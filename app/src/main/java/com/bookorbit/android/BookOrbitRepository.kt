package com.bookorbit.android

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "bookorbit_prefs")

class BookOrbitRepository(private val context: Context) {
    private val queueStore = ProgressQueueStore(context)
    private val downloadStore = DownloadStore(context)
    private val browserSnapshotStore = BrowserSnapshotStore(context)
    private val lastSyncedProgressStore = LastSyncedProgressStore(context)
    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getServerUrl(): String? = context.dataStore.data.first()[Keys.SERVER_URL]

    suspend fun setServerUrl(serverUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl.trimEnd('/')
        }
    }

    suspend fun clearServer() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.SELECTED_LIBRARY_ID)
        }
        queueStore.clear()
        downloadStore.clear()
        browserSnapshotStore.clear()
        lastSyncedProgressStore.clear()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    suspend fun getSelectedLibraryId(): String? = context.dataStore.data.first()[Keys.SELECTED_LIBRARY_ID]

    suspend fun setSelectedLibraryId(libraryId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_LIBRARY_ID] = libraryId
        }
    }

    suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            loadLibraries()
            true
        }.getOrDefault(false)
    }

    suspend fun loadLibraries(): List<LibrarySummary> = withContext(Dispatchers.IO) {
        parseLibraries(request("/api/v1/libraries", "GET", null)).also { libraries ->
            browserSnapshotStore.saveLibraries(
                serverUrl = getServerUrl().orEmpty(),
                selectedLibraryId = getSelectedLibraryId(),
                libraries = libraries
            )
        }
    }

    suspend fun loadBooks(libraryId: String): List<BookSummary> = withContext(Dispatchers.IO) {
        val body = JSONObject().toString().toRequestBody(JSON)
        val downloads = downloadStore.readAll().associateBy { it.fileId }
        parseBooks(
            libraryId = libraryId,
            payload = request("/api/v1/libraries/$libraryId/books", "POST", body),
            downloads = downloads
        ).also { books ->
            browserSnapshotStore.saveBooks(
                serverUrl = getServerUrl().orEmpty(),
                selectedLibraryId = libraryId,
                libraryId = libraryId,
                books = books
            )
        }
    }

    suspend fun loadCachedBrowserState(libraryId: String? = null): BrowserState? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val snapshot = browserSnapshotStore.read(serverUrl) ?: return@withContext null
        val downloads = downloadStore.readAll().associateBy { it.fileId }
        val selectedLibraryId = libraryId
            ?: getSelectedLibraryId()
            ?: snapshot.selectedLibraryId
            ?: snapshot.libraries.firstOrNull()?.id
        BrowserState(
            serverUrl = serverUrl,
            libraries = snapshot.libraries,
            selectedLibraryId = selectedLibraryId,
            books = selectedLibraryId
                ?.let { snapshot.booksByLibraryId[it] }
                .orEmpty()
                .map { book ->
                    val fileId = book.fileId
                    val localPath = fileId?.let { downloads[it]?.localPath }
                    if (localPath == book.localPath) book else book.copy(localPath = localPath)
                },
            debugPendingProgressCount = pendingProgressCount()
        )
    }

    suspend fun buildReaderState(book: BookSummary): ReaderState = withContext(Dispatchers.IO) {
        val localFile = resolveReadableFile(book)
        val progress = queueStore.latestFor(book.id, book.fileId)
        ReaderState(
            book = if (localFile != null) book.copy(localPath = localFile.absolutePath) else book,
            localFile = localFile,
            streamUrl = book.fileId?.let(::buildStreamUrl),
            lastKnownPosition = progress?.positionMs ?: book.progressPositionMs ?: 0L,
            pageIndex = progress?.pageIndex ?: book.progressPageIndex ?: 0,
            progressPercent = progress?.progressPercent ?: book.progressPercent
        )
    }

    suspend fun downloadBook(book: BookSummary): File = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: throw UserFacingException("This title is missing a downloadable file.")
        val target = downloadStore.downloadTarget(fileId, book.title, book.mediaKind, book.format)
        if (!target.exists() || target.length() <= 0L) {
            if (target.exists() && target.length() <= 0L) {
                target.delete()
            }
            val parent = target.parentFile
            parent?.mkdirs()
            if (parent != null && !parent.exists()) {
                throw UserFacingException("Unable to prepare local storage for this download.")
            }
            val request = Request.Builder()
                .url(buildDownloadUrl(fileId))
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpRequestException(
                        code = response.code,
                        action = "download this title"
                    )
                }
                val input = response.body?.byteStream()
                    ?: throw UserFacingException("The server returned an empty download.")
                try {
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                } catch (error: IOException) {
                    target.delete()
                    if (error.isLikelyLocalStorageFailure()) {
                        throw UserFacingException("Local storage could not save this download. Check free space and file access, then try again.")
                    }
                    throw error
                }
                ensureNonEmptyFile(
                    target = target,
                    message = "The server returned an empty download."
                )
            }
        }
        downloadStore.save(
            DownloadRecord(
                fileId = fileId,
                bookId = book.id,
                title = book.title,
                localPath = target.absolutePath,
                mediaKind = book.mediaKind,
                mimeType = book.format
            )
        )
        target
    }

    suspend fun deleteLocalCopy(book: BookSummary) = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: throw UserFacingException("This title does not have a removable local file.")
        val deleted = downloadStore.delete(fileId)
        if (!deleted) {
            throw UserFacingException("Unable to remove the local copy for this title.")
        }
    }

    suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?
    ) = withContext(Dispatchers.IO) {
        val update = ProgressUpdate(
            id = UUID.randomUUID().toString(),
            serverUrl = getServerUrl().orEmpty(),
            bookId = book.id,
            fileId = book.fileId,
            mediaKind = book.mediaKind,
            positionMs = position,
            pageIndex = pageIndex,
            progressPercent = progressPercent,
            updatedAtMillis = System.currentTimeMillis()
        )
        val lastSynced = lastSyncedProgressStore.read(update.progressKey())
        if (lastSynced != null && update.isStaleComparedTo(lastSynced)) {
            return@withContext
        }
        queueStore.enqueue(update)
        enqueueSyncWorker()
    }

    suspend fun pendingProgressCount(): Int = withContext(Dispatchers.IO) {
        queueStore.readAll().size
    }

    suspend fun syncPendingProgress(): SyncAttemptResult = withContext(Dispatchers.IO) {
        val pending = queueStore.readAll()
        if (pending.isEmpty()) {
            return@withContext SyncAttemptResult.Success
        }
        val survivors = mutableListOf<ProgressUpdate>()
        val orderedPending = pending.sortedBy { it.updatedAtMillis }
        var authBlocked = false
        var transientFailure = false
        orderedPending.forEachIndexed { index, item ->
            if (authBlocked) {
                survivors += item
                return@forEachIndexed
            }
            runCatching { postProgress(item) }
                .onSuccess { submitted ->
                    if (submitted) {
                        lastSyncedProgressStore.save(item)
                    }
                }
                .onFailure { error ->
                    if (error is AuthenticationRequiredException) {
                        authBlocked = true
                        survivors += item
                        survivors += orderedPending.drop(index + 1)
                    } else {
                        transientFailure = true
                        survivors += item
                    }
                }
                .getOrNull()
        }
        queueStore.replaceAll(survivors)
        when {
            authBlocked -> SyncAttemptResult.AuthenticationBlocked
            transientFailure && survivors.isNotEmpty() -> SyncAttemptResult.TransientFailure
            else -> SyncAttemptResult.Success
        }
    }

    suspend fun canReachServer(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    private suspend fun postProgress(item: ProgressUpdate): Boolean = withContext(Dispatchers.IO) {
        if (item.serverUrl.isBlank() || item.serverUrl != getServerUrl().orEmpty()) {
            return@withContext false
        }
        val lastSynced = lastSyncedProgressStore.read(item.progressKey())
        if (lastSynced != null && item.isStaleComparedTo(lastSynced)) {
            return@withContext false
        }

        val path = if (item.mediaKind == MediaKind.AUDIO) {
            "/api/v1/books/${item.bookId}/audio-progress"
        } else {
            val fileId = item.fileId ?: return@withContext false
            "/api/v1/books/files/$fileId/progress"
        }

        val payload = JSONObject().apply {
            put("percentage", normalizePercentage(item.progressPercent))
            if (item.mediaKind == MediaKind.AUDIO) {
                put("currentFileId", item.fileId?.toIntOrNull() ?: return@withContext false)
                put("positionSeconds", item.positionMs / 1000.0)
            } else {
                if (item.pageIndex > 0) {
                    put("pageNumber", item.pageIndex + 1)
                }
                if (item.positionMs > 0L) {
                    put("positionSeconds", item.positionMs / 1000.0)
                }
            }
        }

        request(path, if (item.mediaKind == MediaKind.AUDIO) "PATCH" else "POST", payload.toString().toRequestBody(JSON))
        true
    }

    private fun enqueueSyncWorker() {
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bookorbit-progress-sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun resolveReadableFile(book: BookSummary): File? {
        val direct = book.localPath?.let(::File)?.takeIf(File::exists)
        if (direct != null) {
            return direct
        }
        val fileId = book.fileId ?: return null
        val downloaded = downloadStore.find(fileId)?.localPath?.let(::File)?.takeIf(File::exists)
        if (downloaded != null) {
            return downloaded
        }
        return when (book.mediaKind) {
            MediaKind.EPUB,
            MediaKind.PDF -> cacheReadableCopy(book)
            else -> null
        }
    }

    private fun cacheReadableCopy(book: BookSummary): File? {
        val fileId = book.fileId ?: return null
        val extension = when (book.mediaKind) {
            MediaKind.EPUB -> "epub"
            MediaKind.PDF -> "pdf"
            MediaKind.AUDIO -> "bin"
            MediaKind.COMIC -> "cbz"
            MediaKind.UNKNOWN -> "bin"
        }
        val targetDir = File(context.cacheDir, "reader-cache").apply { mkdirs() }
        val target = File(targetDir, "$fileId.$extension")
        if (target.exists() && target.length() > 0L) {
            return target
        }
        if (target.exists() && target.length() <= 0L) {
            target.delete()
        }

        val request = Request.Builder()
            .url(buildDownloadUrl(fileId))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw AuthenticationRequiredException()
            }
            if (!response.isSuccessful) {
                return null
            }
            val input = response.body?.byteStream() ?: return null
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return runCatching {
            ensureNonEmptyFile(target, "The server returned an empty reader file.")
            target
        }.getOrNull()
    }

    private fun buildStreamUrl(fileId: String): String = "${serverBase()}/api/v1/books/files/$fileId/serve"

    private fun buildDownloadUrl(fileId: String): String = "${serverBase()}/api/v1/books/files/$fileId/download"

    private fun serverBase(): String = runBlocking { getServerUrl() }.orEmpty()

    private fun request(path: String, method: String, body: RequestBody?): String {
        val base = serverBase().ifBlank { throw UserFacingException("No BookOrbit server is configured.") }
        val request = Request.Builder()
            .url(base.trimEnd('/') + path)
            .method(method, body)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw AuthenticationRequiredException()
            }
            if (!response.isSuccessful) {
                throw HttpRequestException(
                    code = response.code,
                    action = requestAction(path, method)
                )
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseLibraries(payload: String): List<LibrarySummary> {
        val array = extractArray(payload, "load libraries")
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(
                    LibrarySummary(
                        id = obj.stringValue("id", "_id", "libraryId")
                            ?: "library-$index",
                        name = obj.stringValue("name", "title", "label")
                            ?: "Library ${index + 1}",
                        description = obj.stringValue("description", "summary")
                    )
                )
            }
        }
    }

    private fun parseBooks(
        libraryId: String,
        payload: String,
        downloads: Map<String, DownloadRecord>
    ): List<BookSummary> {
        val array = extractArray(payload, "load books")
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val primaryFile = obj.optJSONArray("files").selectPrimaryFile()
                val fileId = primaryFile?.stringValue("id", "_id", "fileId")
                    ?: obj.stringValue("fileId", "file_id")
                    ?: obj.optJSONObject("file")?.stringValue("id", "_id")
                    ?: obj.optJSONObject("bookFile")?.stringValue("id", "_id")
                val format = primaryFile?.stringValue("format", "mimeType", "mime_type", "extension")
                    ?: obj.stringValue("format", "mimeType", "mime_type", "extension")
                val mediaKind = inferMediaKind(format, obj.stringValue("title", "name"))
                val readingProgress = obj.optJSONObject("readingProgress")
                add(
                    BookSummary(
                        libraryId = libraryId,
                        id = obj.stringValue("id", "_id", "bookId") ?: "book-$index",
                        fileId = fileId,
                        title = obj.stringValue("title", "name", "displayName") ?: "Untitled",
                        author = obj.authorDisplayName(),
                        format = format,
                        mediaKind = mediaKind,
                        streamUrl = fileId?.let(::buildStreamUrl),
                        downloadUrl = fileId?.let(::buildDownloadUrl),
                        coverUrl = if (obj.booleanValue("hasCover", "has_cover")) {
                            "${serverBase()}/api/v1/books/${obj.stringValue("id", "_id", "bookId") ?: "book-$index"}/cover"
                        } else {
                            obj.stringValue("coverUrl", "cover", "coverImage")
                        },
                        localPath = fileId?.let { downloads[it]?.localPath },
                        progressLabel = readingProgress.progressLabel(),
                        progressPercent = readingProgress.progressPercent(),
                        progressPositionMs = readingProgress.progressPositionMs(),
                        progressPageIndex = readingProgress.progressPageIndex()
                    )
                )
            }
        }
    }

    private fun inferMediaKind(format: String?, title: String?): MediaKind {
        val token = (format ?: title ?: "").lowercase(Locale.US)
        return when {
            token.endsWith(".mp3") || token.endsWith(".m4b") || token.contains("audio") -> MediaKind.AUDIO
            token.endsWith(".pdf") || token.contains("pdf") -> MediaKind.PDF
            token.endsWith(".cbz") || token.endsWith(".cbr") || token.contains("comic") -> MediaKind.COMIC
            token.endsWith(".epub") || token.contains("epub") || token.contains("mobi") || token.contains("azw3") -> MediaKind.EPUB
            else -> MediaKind.UNKNOWN
        }
    }

    private fun extractArray(payload: String, action: String): JSONArray {
        return runCatching {
            when (val root = JSONTokener(payload).nextValue()) {
                is JSONArray -> root
                is JSONObject -> root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("libraries")
                    ?: root.optJSONArray("books")
                    ?: root.optJSONArray("results")
                    ?: JSONArray()
                else -> JSONArray()
            }
        }.getOrElse {
            throw UserFacingException("The server returned malformed data while trying to $action.")
        }
    }

    private fun JSONObject?.progressLabel(): String? {
        this ?: return null
        opt("percentage")?.toString()?.takeIf { it.isNotBlank() }?.let { return "$it%" }
        opt("pageNumber")?.toString()?.takeIf { it.isNotBlank() }?.let { return "page $it" }
        opt("positionSeconds")?.toString()?.takeIf { it.isNotBlank() }?.let { return "${it}s" }
        opt("currentFileId")?.toString()?.takeIf { it.isNotBlank() }?.let { return "file $it" }
        return null
    }

    private fun JSONObject?.progressPercent(): Float? {
        this ?: return null
        return when (val value = opt("percentage")) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }

    private fun JSONObject?.progressPositionMs(): Long? {
        this ?: return null
        val seconds = when (val value = opt("positionSeconds")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: return null
        return (seconds * 1000.0).toLong().coerceAtLeast(0L)
    }

    private fun JSONObject?.progressPageIndex(): Int? {
        this ?: return null
        val pageNumber = when (val value = opt("pageNumber")) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: return null
        return (pageNumber - 1).coerceAtLeast(0)
    }

    private fun JSONObject.stringValue(vararg keys: String): String? {
        keys.forEach { key ->
            when (val value = opt(key)) {
                is String -> if (value.isNotBlank()) return value
                is Number -> return value.toString()
                is JSONObject -> {
                    val nested = value.stringValue("id", "_id", "value", "name", "title")
                    if (!nested.isNullOrBlank()) {
                        return nested
                    }
                }
            }
        }
        return null
    }

    private fun JSONObject.booleanValue(vararg keys: String): Boolean {
        keys.forEach { key ->
            when (val value = opt(key)) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
                is String -> {
                    when (value.trim().lowercase(Locale.US)) {
                        "true", "1", "yes" -> return true
                        "false", "0", "no" -> return false
                    }
                }
            }
        }
        return false
    }

    private fun JSONObject.authorDisplayName(): String? {
        optJSONArray("authors")?.let { authors ->
            val values = buildList {
                for (authorIndex in 0 until authors.length()) {
                    when (val author = authors.opt(authorIndex)) {
                        is String -> author.takeIf { it.isNotBlank() }?.let(::add)
                        is Number -> add(author.toString())
                        is JSONObject -> author.stringValue("name", "author", "displayName", "fullName", "title")
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }
            if (values.isNotEmpty()) {
                return values.joinToString(", ")
            }
        }
        return stringValue("author", "authorName", "creator")
    }

    private fun JSONArray?.selectPrimaryFile(): JSONObject? {
        this ?: return null
        for (index in 0 until length()) {
            val candidate = optJSONObject(index) ?: continue
            if (candidate.optString("role").equals("primary", ignoreCase = true)) {
                return candidate
            }
        }
        for (index in 0 until length()) {
            val candidate = optJSONObject(index) ?: continue
            if (candidate.stringValue("id", "_id", "fileId") != null) {
                return candidate
            }
        }
        return null
    }

    private fun normalizePercentage(value: Float?): Double {
        val raw = value ?: 0f
        val scaled = if (raw in 0f..1f) raw * 100f else raw
        return scaled.coerceIn(0f, 100f).toDouble()
    }

    private fun requestAction(path: String, method: String): String {
        return when {
            path == "/api/v1/libraries" -> "load libraries"
            path.contains("/books") && method == "POST" -> "load books"
            path.contains("/progress") -> "sync reading progress"
            path.contains("/audio-progress") -> "sync listening progress"
            else -> "complete this request"
        }
    }

    private fun ensureNonEmptyFile(target: File, message: String) {
        if (target.length() > 0L) {
            return
        }
        target.delete()
        throw UserFacingException(message)
    }

    private fun IOException.isLikelyLocalStorageFailure(): Boolean {
        return this is FileNotFoundException ||
            this is FileSystemException ||
            this is AccessDeniedException ||
            this is NoSuchFileException ||
            message.orEmpty().contains("no space", ignoreCase = true) ||
            message.orEmpty().contains("not enough space", ignoreCase = true) ||
            message.orEmpty().contains("enospc", ignoreCase = true)
    }

    private fun ProgressUpdate.isStaleComparedTo(other: ProgressUpdate): Boolean {
        return sameProgressAs(other) || isNotAheadOf(other)
    }

    private fun ProgressUpdate.sameProgressAs(other: ProgressUpdate): Boolean {
        return pageIndex == other.pageIndex &&
            positionMs == other.positionMs &&
            normalizedProgressPercent() == other.normalizedProgressPercent()
    }

    private fun ProgressUpdate.isNotAheadOf(other: ProgressUpdate): Boolean {
        return pageIndex <= other.pageIndex &&
            positionMs <= other.positionMs &&
            normalizedProgressPercent() <= other.normalizedProgressPercent()
    }

    private fun ProgressUpdate.normalizedProgressPercent(): Float {
        val value = progressPercent ?: 0f
        return if (value in 0f..1f) value * 100f else value
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SELECTED_LIBRARY_ID = stringPreferencesKey("selected_library_id")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

enum class SyncAttemptResult {
    Success,
    AuthenticationBlocked,
    TransientFailure
}

class AuthenticationRequiredException : IllegalStateException("Authentication required.")
class UserFacingException(message: String) : IllegalStateException(message)
class HttpRequestException(
    val code: Int,
    val action: String
) : IOException("HTTP $code while trying to $action.")

private class WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
}
