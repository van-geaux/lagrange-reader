package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BookDetailCacheStore private constructor(
    private val directory: File,
    private val legacyFile: File
) {
    constructor(context: Context) : this(
        File(context.filesDir, "book_detail_cache"),
        File(context.filesDir, "book_detail_cache.json")
    )

    @Suppress("UNUSED_PARAMETER")
    internal constructor(filesDir: File, marker: Unit = Unit) : this(
        File(filesDir, "book_detail_cache"),
        File(filesDir, "book_detail_cache.json")
    )

    suspend fun read(
        serverUrl: String,
        bookId: String,
        fileId: String?,
        sourceUpdatedAtMillis: Long? = null
    ): BookDetailInfo? = withContext(Dispatchers.IO) {
        val key = cacheKey(serverUrl, bookId, fileId)
        val target = entryFile(key)
        lockFor(target).withLock {
            val entry = readEntry(target, key) ?: return@withLock null
            if (!entry.has("detail")) {
                val legacy = entry.toBookDetail() ?: return@withLock null
                return@withLock legacy.takeIf {
                    sourceUpdatedAtMillis == null || legacy.book.updatedAtMillis == sourceUpdatedAtMillis
                }
            }
            if (entry.optionalLong("sourceUpdatedAtMillis") != sourceUpdatedAtMillis) {
                return@withLock null
            }
            entry.optJSONObject("detail")?.toBookDetail()
        }
    }

    suspend fun readLatest(
        serverUrl: String,
        bookId: String,
        fileId: String?
    ): BookDetailInfo? = withContext(Dispatchers.IO) {
        val key = cacheKey(serverUrl, bookId, fileId)
        val target = entryFile(key)
        lockFor(target).withLock {
            val entry = readEntry(target, key) ?: return@withLock null
            if (!entry.has("detail")) entry.toBookDetail() else entry.optJSONObject("detail")?.toBookDetail()
        }
    }

    suspend fun save(
        serverUrl: String,
        bookId: String,
        fileId: String?,
        detail: BookDetailInfo,
        sourceUpdatedAtMillis: Long? = detail.book.updatedAtMillis
    ) = withContext(Dispatchers.IO) {
        val target = entryFile(cacheKey(serverUrl, bookId, fileId))
        lockFor(target).withLock {
            directory.mkdirs()
            val temporary = File(directory, "${target.name}.tmp")
            temporary.writeText(
                JSONObject().apply {
                    putNullable("sourceUpdatedAtMillis", sourceUpdatedAtMillis)
                    put("detail", detail.toJson())
                }.toString()
            )
            runCatching {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrElse {
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    target.writeText(temporary.readText())
                    temporary.delete()
                }
            }
        }
    }

    suspend fun remove(serverUrl: String, bookId: String, fileId: String?) = withContext(Dispatchers.IO) {
        val key = cacheKey(serverUrl, bookId, fileId)
        val target = entryFile(key)
        lockFor(target).withLock {
            if (target.exists()) target.delete()
        }
        legacyMutex.withLock {
            if (!legacyFile.isFile) return@withLock
            val root = runCatching { JSONObject(legacyFile.readText()) }.getOrNull() ?: return@withLock
            root.remove(key)
            legacyFile.writeText(root.toString())
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        clearMutex.withLock {
            if (directory.exists()) directory.deleteRecursively()
            legacyMutex.withLock {
                if (legacyFile.exists()) legacyFile.delete()
            }
        }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter(File::isFile)?.sumOf(File::length).orEmptyBytes() +
            legacyFile.takeIf(File::isFile)?.length().orEmptyBytes()
    }

    private fun readEntry(target: File, key: String): JSONObject? {
        if (target.isFile) return runCatching { JSONObject(target.readText()) }.getOrNull()
        return runCatching { JSONObject(legacyFile.readText()) }
            .getOrNull()
            ?.optJSONObject(key)
    }

    private fun entryFile(key: String): File = File(directory, "$key.json")

    private fun lockFor(file: File): Mutex = entryLocks.getOrPut(file.absolutePath) { Mutex() }

    private fun cacheKey(serverUrl: String, bookId: String, fileId: String?): String {
        val token = "$serverUrl\u0000$bookId\u0000${fileId.orEmpty()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun Long?.orEmptyBytes(): Long = this ?: 0L

    private companion object {
        val entryLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
        val legacyMutex = Mutex()
        val clearMutex = Mutex()
    }
}

private fun BookDetailInfo.toJson(): JSONObject = JSONObject().apply {
    put("book", book.toJson())
    putNullable("libraryName", libraryName)
    putNullable("subtitle", subtitle)
    putNullable("synopsis", synopsis)
    putNullable("publisher", publisher)
    putNullable("publishedDate", publishedDate)
    putNullable("language", language)
    putNullable("pageCount", pageCount)
    putNullable("isbn10", isbn10)
    putNullable("isbn13", isbn13)
    put("genres", JSONArray(genres))
    put("tags", JSONArray(tags))
    putNullable("userRating", userRating)
    put("narrators", JSONArray(narrators))
    put("fileCount", fileCount)
    put("availableFiles", JSONArray(availableFiles.map { it.toJson() }))
    putNullable("totalSizeBytes", totalSizeBytes)
    putNullable("durationSeconds", durationSeconds)
    put("audioChapters", audioChapters.toJson())
    put("providerIds", providerIds.toProviderIdsJson())
}

private fun BookFileOption.toJson(): JSONObject = JSONObject().apply {
    put("book", book.toJson())
    putNullable("filename", filename)
    putNullable("sizeBytes", sizeBytes)
    putNullable("role", role)
    putNullable("updatedAtMillis", updatedAtMillis)
}

private fun BookSummary.toJson(): JSONObject = JSONObject().apply {
    put("libraryId", libraryId)
    put("id", id)
    putNullable("fileId", fileId)
    put("title", title)
    putNullable("author", author)
    putNullable("format", format)
    put("mediaKind", mediaKind.name)
    putNullable("streamUrl", streamUrl)
    putNullable("downloadUrl", downloadUrl)
    putNullable("coverUrl", coverUrl)
    put("coverAspectRatio", coverAspectRatio.wireValue)
    putNullable("localPath", localPath)
    putNullable("progressLabel", progressLabel)
    putNullable("progressPercent", progressPercent)
    putNullable("progressPositionMs", progressPositionMs)
    putNullable("progressPageIndex", progressPageIndex)
    putNullable("seriesId", seriesId)
    putNullable("seriesName", seriesName)
    putNullable("seriesIndex", seriesIndex)
    putNullable("readStatus", readStatus?.wireValue)
    put("isRead", isRead)
    putNullable("addedAtMillis", addedAtMillis)
    putNullable("updatedAtMillis", updatedAtMillis)
    putNullable("lastReadAtMillis", lastReadAtMillis)
    put("isServerMissing", isServerMissing)
    putNullable("readerPageIndex", readerPageIndex)
    putNullable("readerPageCount", readerPageCount)
    put("audioChapters", audioChapters.toJson())
}

private fun JSONObject.toBookDetail(): BookDetailInfo? {
    val book = optJSONObject("book")?.toBookSummary() ?: return null
    return BookDetailInfo(
        book = book,
        libraryName = optionalString("libraryName"),
        subtitle = optionalString("subtitle"),
        synopsis = optionalString("synopsis"),
        publisher = optionalString("publisher"),
        publishedDate = optionalString("publishedDate"),
        language = optionalString("language"),
        pageCount = optionalInt("pageCount"),
        isbn10 = optionalString("isbn10"),
        isbn13 = optionalString("isbn13"),
        genres = stringList("genres"),
        tags = stringList("tags"),
        userRating = optionalUserRating(),
        narrators = stringList("narrators"),
        fileCount = optInt("fileCount"),
        availableFiles = optJSONArray("availableFiles").toBookFileOptions(),
        totalSizeBytes = optionalLong("totalSizeBytes"),
        durationSeconds = optionalLong("durationSeconds"),
        audioChapters = audiobookChapters("audioChapters"),
        providerIds = providerIds("providerIds")
    )
}

private fun JSONObject.toBookSummary(): BookSummary = BookSummary(
    libraryId = optString("libraryId"),
    id = optString("id"),
    fileId = optionalString("fileId"),
    title = optString("title"),
    author = optionalString("author"),
    format = optionalString("format"),
    mediaKind = runCatching { MediaKind.valueOf(optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
    streamUrl = optionalString("streamUrl"),
    downloadUrl = optionalString("downloadUrl"),
    coverUrl = optionalString("coverUrl"),
    coverAspectRatio = CoverAspectRatio.fromWireValue(optionalString("coverAspectRatio")),
    localPath = optionalString("localPath"),
    progressLabel = optionalString("progressLabel"),
    progressPercent = optionalFloat("progressPercent"),
    progressPositionMs = optionalLong("progressPositionMs"),
    progressPageIndex = optionalInt("progressPageIndex"),
    seriesId = optionalString("seriesId"),
    seriesName = optionalString("seriesName"),
    seriesIndex = optionalDouble("seriesIndex"),
    readStatus = BookReadStatus.fromWireValue(optionalString("readStatus")),
    isRead = optBoolean("isRead"),
    addedAtMillis = optionalLong("addedAtMillis"),
    updatedAtMillis = optionalLong("updatedAtMillis"),
    lastReadAtMillis = optionalLong("lastReadAtMillis"),
    readerPageIndex = optionalInt("readerPageIndex"),
    readerPageCount = optionalInt("readerPageCount"),
    audioChapters = audiobookChapters("audioChapters"),
    isServerMissing = optBoolean("isServerMissing")
)

private fun JSONArray?.toBookSummaries(): List<BookSummary> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { add(it.toBookSummary()) }
        }
    }
}

private fun JSONArray?.toBookFileOptions(): List<BookFileOption> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val book = item.optJSONObject("book")?.toBookSummary() ?: continue
            add(
                BookFileOption(
                    book = book,
                    filename = item.optionalString("filename"),
                    sizeBytes = item.optionalLong("sizeBytes"),
                    role = item.optionalString("role"),
                    updatedAtMillis = item.optionalLong("updatedAtMillis")
                )
            )
        }
    }
}

private fun List<BookProviderId>.toProviderIdsJson(): JSONArray = JSONArray(
    map { providerId ->
        JSONObject()
            .put("provider", providerId.provider)
            .put("id", providerId.id)
    }
)

private fun JSONObject.providerIds(key: String): List<BookProviderId> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val provider = entry.optString("provider").takeIf { it.isNotBlank() } ?: continue
            val id = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
            add(BookProviderId(provider, id))
        }
    }
}

private fun List<AudiobookChapter>.toJson(): JSONArray = JSONArray(
    map { chapter ->
        JSONObject()
            .put("title", chapter.title)
            .put("startMs", chapter.startMs)
    }
)

private fun JSONObject.audiobookChapters(key: String): List<AudiobookChapter> {
    val chapters = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(index) ?: continue
            add(
                AudiobookChapter(
                    title = chapter.optString("title", "Chapter ${index + 1}"),
                    startMs = chapter.optLong("startMs").coerceAtLeast(0L)
                )
            )
        }
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONObject.optionalInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

private fun JSONObject.optionalLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key)
}

private fun JSONObject.optionalFloat(key: String): Float? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key).toFloat()
}

private fun JSONObject.optionalDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key)
}

private fun JSONObject.optionalUserRating(): Int? {
    val key = when {
        has("userRating") -> "userRating"
        has("rating") -> "rating"
        else -> return null
    }
    if (isNull(key)) return null
    val value = optDouble(key)
    return value.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()?.takeIf { it in 1..5 }
}

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
