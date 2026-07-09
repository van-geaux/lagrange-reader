package com.bookorbit.android

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "bookorbit_prefs")

class BookOrbitRepository(private val context: Context) {
    private val queueStore = ProgressQueueStore(context)
    private val downloadStore = DownloadStore(context)
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
        parseLibraries(request("/api/v1/libraries", "GET", null))
    }

    suspend fun loadBooks(libraryId: String): List<BookSummary> = withContext(Dispatchers.IO) {
        val body = JSONObject().toString().toRequestBody(JSON)
        val downloads = downloadStore.readAll().associateBy { it.fileId }
        parseBooks(
            libraryId = libraryId,
            payload = request("/api/v1/libraries/$libraryId/books", "POST", body),
            downloads = downloads
        )
    }

    suspend fun buildReaderState(book: BookSummary): ReaderState = withContext(Dispatchers.IO) {
        val localFile = resolveReadableFile(book)
        val progress = queueStore.latestFor(book.id, book.fileId)
        ReaderState(
            book = if (localFile != null) book.copy(localPath = localFile.absolutePath) else book,
            localFile = localFile,
            streamUrl = book.fileId?.let(::buildStreamUrl),
            lastKnownPosition = progress?.positionMs ?: 0L,
            pageIndex = progress?.pageIndex ?: 0,
            progressPercent = progress?.progressPercent ?: book.progressPercent
        )
    }

    suspend fun downloadBook(book: BookSummary): File = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: error("Book does not expose a file id.")
        val target = downloadStore.downloadTarget(fileId, book.title, book.mediaKind)
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            val request = Request.Builder()
                .url(buildDownloadUrl(fileId))
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed with HTTP ${response.code}.")
                }
                val input = response.body?.byteStream() ?: error("No download body returned.")
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        }
        downloadStore.save(
            DownloadRecord(
                fileId = fileId,
                bookId = book.id,
                title = book.title,
                localPath = target.absolutePath,
                mediaKind = book.mediaKind
            )
        )
        target
    }

    suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?
    ) = withContext(Dispatchers.IO) {
        queueStore.enqueue(
            ProgressUpdate(
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
        )
        enqueueSyncWorker()
    }

    suspend fun syncPendingProgress() = withContext(Dispatchers.IO) {
        val pending = queueStore.readAll()
        if (pending.isEmpty()) {
            return@withContext
        }
        val survivors = mutableListOf<ProgressUpdate>()
        val orderedPending = pending.sortedBy { it.updatedAtMillis }
        var authBlocked = false
        orderedPending.forEachIndexed { index, item ->
            if (authBlocked) {
                survivors += item
                return@forEachIndexed
            }
            runCatching { postProgress(item) }
                .onFailure { error ->
                    if (error is AuthenticationRequiredException) {
                        authBlocked = true
                        survivors += item
                        survivors += orderedPending.drop(index + 1)
                    } else {
                        survivors += item
                    }
                }
                .getOrNull()
        }
        queueStore.replaceAll(survivors)
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
            MediaKind.EPUB -> cacheReadableCopy(book)
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
        return target.takeIf(File::exists)
    }

    private fun buildStreamUrl(fileId: String): String = "${serverBase()}/api/v1/books/files/$fileId/serve"

    private fun buildDownloadUrl(fileId: String): String = "${serverBase()}/api/v1/books/files/$fileId/download"

    private fun serverBase(): String = runBlocking { getServerUrl() }.orEmpty()

    private fun request(path: String, method: String, body: RequestBody?): String {
        val base = serverBase().ifBlank { error("No BookOrbit server configured.") }
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
                error("Request failed with HTTP ${response.code}.")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseLibraries(payload: String): List<LibrarySummary> {
        val array = extractArray(payload)
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
        val array = extractArray(payload)
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
                val authors = obj.optJSONArray("authors")
                val readingProgress = obj.optJSONObject("readingProgress")
                add(
                    BookSummary(
                        libraryId = libraryId,
                        id = obj.stringValue("id", "_id", "bookId") ?: "book-$index",
                        fileId = fileId,
                        title = obj.stringValue("title", "name", "displayName") ?: "Untitled",
                        author = when {
                            authors != null && authors.length() > 0 -> buildList {
                                for (authorIndex in 0 until authors.length()) {
                                    authors.optString(authorIndex).takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }.joinToString(", ").ifBlank { null }
                            else -> obj.stringValue("author", "authorName", "creator")
                        },
                        format = format,
                        mediaKind = mediaKind,
                        streamUrl = fileId?.let(::buildStreamUrl),
                        downloadUrl = fileId?.let(::buildDownloadUrl),
                        coverUrl = if (obj.optBoolean("hasCover")) {
                            "${serverBase()}/api/v1/books/${obj.stringValue("id", "_id", "bookId") ?: "book-$index"}/cover"
                        } else {
                            obj.stringValue("coverUrl", "cover", "coverImage")
                        },
                        localPath = fileId?.let { downloads[it]?.localPath },
                        progressLabel = readingProgress.progressLabel(),
                        progressPercent = readingProgress.progressPercent()
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

    private fun extractArray(payload: String): JSONArray {
        return when (val root = JSONTokener(payload).nextValue()) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("items")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("libraries")
                ?: root.optJSONArray("books")
                ?: root.optJSONArray("results")
                ?: JSONArray()
            else -> JSONArray()
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

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SELECTED_LIBRARY_ID = stringPreferencesKey("selected_library_id")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private class AuthenticationRequiredException : IllegalStateException("Authentication required.")

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
