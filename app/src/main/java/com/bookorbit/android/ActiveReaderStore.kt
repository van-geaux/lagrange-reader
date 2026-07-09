package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                put("localPath", book.localPath)
                put("progressLabel", book.progressLabel)
                put("progressPercent", normalizeStoredProgressPercent(book.progressPercent))
                put("progressPositionMs", book.progressPositionMs)
                put("progressPageIndex", book.progressPageIndex)
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
            localPath = book.optString("localPath").takeIf { it.isNotBlank() },
            progressLabel = book.optString("progressLabel").takeIf { it.isNotBlank() },
            progressPercent = if (book.has("progressPercent") && !book.isNull("progressPercent")) normalizeStoredProgressPercent(book.optDouble("progressPercent").toFloat()) else null,
            progressPositionMs = if (book.has("progressPositionMs") && !book.isNull("progressPositionMs")) book.optLong("progressPositionMs") else null,
            progressPageIndex = if (book.has("progressPageIndex") && !book.isNull("progressPageIndex")) book.optInt("progressPageIndex") else null
        )
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) {
            file.delete()
        }
    }
}
