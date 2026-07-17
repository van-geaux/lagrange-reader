package com.bookorbit.android

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
    private val file: File
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(File(context.filesDir, "book_detail_cache.json"))

    @Suppress("UNUSED_PARAMETER")
    internal constructor(filesDir: File, marker: Unit = Unit) : this(File(filesDir, "book_detail_cache.json"))

    suspend fun read(
        serverUrl: String,
        bookId: String,
        fileId: String?,
        sourceUpdatedAtMillis: Long? = null
    ): BookDetailInfo? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.isFile) return@withLock null
            val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withLock null
            val entry = root.optJSONObject(cacheKey(serverUrl, bookId, fileId)) ?: return@withLock null
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
        mutex.withLock {
            if (!file.isFile) return@withLock null
            val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withLock null
            val entry = root.optJSONObject(cacheKey(serverUrl, bookId, fileId)) ?: return@withLock null
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
        mutex.withLock {
            val root = if (file.isFile) {
                runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
            } else {
                JSONObject()
            }
            root.put(
                cacheKey(serverUrl, bookId, fileId),
                JSONObject().apply {
                    putNullable("sourceUpdatedAtMillis", sourceUpdatedAtMillis)
                    put("detail", detail.toJson())
                }
            )
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }
    }

    suspend fun remove(serverUrl: String, bookId: String, fileId: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.isFile) return@withLock
            val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withLock
            root.remove(cacheKey(serverUrl, bookId, fileId))
            file.writeText(root.toString())
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.delete()
        }
    }

    private fun cacheKey(serverUrl: String, bookId: String, fileId: String?): String {
        val token = "$serverUrl\u0000$bookId\u0000${fileId.orEmpty()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
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
    putNullable("rating", rating)
    put("narrators", JSONArray(narrators))
    put("fileCount", fileCount)
    putNullable("totalSizeBytes", totalSizeBytes)
    putNullable("durationSeconds", durationSeconds)
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
    putNullable("localPath", localPath)
    putNullable("progressLabel", progressLabel)
    putNullable("progressPercent", progressPercent)
    putNullable("progressPositionMs", progressPositionMs)
    putNullable("progressPageIndex", progressPageIndex)
    putNullable("seriesId", seriesId)
    putNullable("seriesName", seriesName)
    putNullable("seriesIndex", seriesIndex)
    put("isRead", isRead)
    putNullable("addedAtMillis", addedAtMillis)
    putNullable("updatedAtMillis", updatedAtMillis)
    putNullable("lastReadAtMillis", lastReadAtMillis)
    putNullable("readerPageIndex", readerPageIndex)
    putNullable("readerPageCount", readerPageCount)
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
        rating = optionalDouble("rating"),
        narrators = stringList("narrators"),
        fileCount = optInt("fileCount"),
        totalSizeBytes = optionalLong("totalSizeBytes"),
        durationSeconds = optionalLong("durationSeconds")
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
    localPath = optionalString("localPath"),
    progressLabel = optionalString("progressLabel"),
    progressPercent = optionalFloat("progressPercent"),
    progressPositionMs = optionalLong("progressPositionMs"),
    progressPageIndex = optionalInt("progressPageIndex"),
    seriesId = optionalString("seriesId"),
    seriesName = optionalString("seriesName"),
    seriesIndex = optionalDouble("seriesIndex"),
    isRead = optBoolean("isRead"),
    addedAtMillis = optionalLong("addedAtMillis"),
    updatedAtMillis = optionalLong("updatedAtMillis"),
    lastReadAtMillis = optionalLong("lastReadAtMillis"),
    readerPageIndex = optionalInt("readerPageIndex"),
    readerPageCount = optionalInt("readerPageCount")
)

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

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
