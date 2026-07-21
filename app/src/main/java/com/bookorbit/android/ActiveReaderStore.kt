package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ActiveReaderStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(
        file = File(context.filesDir, "active_reader.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "active_reader.json"),
        directFileConstructor = true
    )

    suspend fun save(serverUrl: String, book: BookSummary) = mutex.withLock {
        val root = JSONObject().apply {
            put("serverUrl", serverUrl)
            put("book", JSONObject().apply {
                put("libraryId", book.libraryId)
                put("id", book.id)
                put("fileId", book.fileId)
                put("title", book.title)
                put("author", book.author)
                put("format", book.format)
                put("mediaKind", book.mediaKind.name)
                put("streamUrl", book.streamUrl)
                put("downloadUrl", book.downloadUrl)
                put("coverUrl", book.coverUrl)
                put("coverAspectRatio", book.coverAspectRatio.wireValue)
                put("localPath", book.localPath)
                put("progressLabel", book.progressLabel)
                put("progressPercent", normalizeStoredProgressPercent(book.progressPercent))
                put("progressPositionMs", book.progressPositionMs)
                put("progressPageIndex", book.progressPageIndex)
                put("seriesId", book.seriesId)
                put("seriesName", book.seriesName)
                put("seriesIndex", book.seriesIndex)
                put("isRead", book.isRead)
                put("addedAtMillis", book.addedAtMillis)
                put("updatedAtMillis", book.updatedAtMillis)
                put("lastReadAtMillis", book.lastReadAtMillis)
                put("readerPageIndex", book.readerPageIndex)
                put("readerPageCount", book.readerPageCount)
                put("audioChapters", JSONArray(book.audioChapters.map { chapter ->
                    JSONObject()
                        .put("title", chapter.title)
                        .put("startMs", chapter.startMs)
                }))
            })
        }
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    suspend fun read(serverUrl: String): BookSummary? = mutex.withLock {
        if (!file.exists()) {
            return@withLock null
        }
        val root = JSONObject(file.readText())
        if (root.optString("serverUrl") != serverUrl) {
            return@withLock null
        }
        val book = root.optJSONObject("book") ?: return@withLock null
        BookSummary(
            libraryId = book.optString("libraryId"),
            id = book.optString("id"),
            fileId = book.optString("fileId").takeIf { it.isNotBlank() },
            title = book.optString("title"),
            author = book.optString("author").takeIf { it.isNotBlank() },
            format = book.optString("format").takeIf { it.isNotBlank() },
            mediaKind = runCatching { MediaKind.valueOf(book.optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
            streamUrl = book.optString("streamUrl").takeIf { it.isNotBlank() },
            downloadUrl = book.optString("downloadUrl").takeIf { it.isNotBlank() },
            coverUrl = book.optString("coverUrl").takeIf { it.isNotBlank() },
            coverAspectRatio = CoverAspectRatio.fromWireValue(book.optString("coverAspectRatio")),
            localPath = book.optString("localPath").takeIf { it.isNotBlank() },
            progressLabel = book.optString("progressLabel").takeIf { it.isNotBlank() },
            progressPercent = if (book.has("progressPercent") && !book.isNull("progressPercent")) normalizeStoredProgressPercent(book.optDouble("progressPercent").toFloat()) else null,
            progressPositionMs = if (book.has("progressPositionMs") && !book.isNull("progressPositionMs")) book.optLong("progressPositionMs") else null,
            progressPageIndex = if (book.has("progressPageIndex") && !book.isNull("progressPageIndex")) book.optInt("progressPageIndex") else null,
            seriesId = book.optString("seriesId").takeIf { it.isNotBlank() },
            seriesName = book.optString("seriesName").takeIf { it.isNotBlank() },
            seriesIndex = if (book.has("seriesIndex") && !book.isNull("seriesIndex")) book.optDouble("seriesIndex") else null,
            isRead = book.optBoolean("isRead"),
            addedAtMillis = if (book.has("addedAtMillis") && !book.isNull("addedAtMillis")) book.optLong("addedAtMillis") else null,
            updatedAtMillis = if (book.has("updatedAtMillis") && !book.isNull("updatedAtMillis")) book.optLong("updatedAtMillis") else null,
            lastReadAtMillis = if (book.has("lastReadAtMillis") && !book.isNull("lastReadAtMillis")) book.optLong("lastReadAtMillis") else null,
            readerPageIndex = if (book.has("readerPageIndex") && !book.isNull("readerPageIndex")) book.optInt("readerPageIndex") else null,
            readerPageCount = if (book.has("readerPageCount") && !book.isNull("readerPageCount")) book.optInt("readerPageCount") else null,
            audioChapters = book.optJSONArray("audioChapters")?.let { chapters ->
                buildList {
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
            }.orEmpty()
        )
    }

    suspend fun clearIfMatches(serverUrl: String, bookId: String) = mutex.withLock {
        if (!file.exists()) return@withLock
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withLock
        val savedBookId = root.optJSONObject("book")?.optString("id")
        if (root.optString("serverUrl") == serverUrl && savedBookId == bookId) {
            file.delete()
        }
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) {
            file.delete()
        }
    }
}
