package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class EpubReaderPosition(
    val serverUrl: String,
    val bookId: String,
    val fileId: String?,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val updatedAtMillis: Long
)

class EpubReaderPositionStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(
        file = File(context.filesDir, "epub_reader_positions.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "epub_reader_positions.json"),
        directFileConstructor = true
    )

    suspend fun read(serverUrl: String, bookId: String, fileId: String?): EpubReaderPosition? = mutex.withLock {
        readUnlocked()[storageKey(serverUrl, bookId, fileId)]
    }

    suspend fun save(position: EpubReaderPosition) = mutex.withLock {
        val current = readUnlocked().toMutableMap()
        current[storageKey(position.serverUrl, position.bookId, position.fileId)] = position
        writeUnlocked(current)
    }

    suspend fun removeForBook(serverUrl: String, bookId: String) = mutex.withLock {
        val remaining = readUnlocked().filterValues { position ->
            position.serverUrl != serverUrl || position.bookId != bookId
        }
        writeUnlocked(remaining)
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    private fun readUnlocked(): Map<String, EpubReaderPosition> {
        if (!file.exists()) return emptyMap()
        val array = JSONArray(file.readText())
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val position = item.toPosition()
                put(storageKey(position.serverUrl, position.bookId, position.fileId), position)
            }
        }
    }

    private fun writeUnlocked(items: Map<String, EpubReaderPosition>) {
        val array = JSONArray()
        items.values.sortedBy { it.updatedAtMillis }.forEach { position ->
            array.put(
                JSONObject().apply {
                    put("serverUrl", position.serverUrl)
                    put("bookId", position.bookId)
                    put("fileId", position.fileId)
                    put("chapterIndex", position.chapterIndex)
                    put("pageIndex", position.pageIndex)
                    put("pageCount", position.pageCount)
                    put("updatedAtMillis", position.updatedAtMillis)
                }
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun JSONObject.toPosition(): EpubReaderPosition {
        return EpubReaderPosition(
            serverUrl = optString("serverUrl"),
            bookId = optString("bookId"),
            fileId = optString("fileId").takeIf { it.isNotBlank() },
            chapterIndex = optInt("chapterIndex").coerceAtLeast(0),
            pageIndex = optInt("pageIndex").coerceAtLeast(0),
            pageCount = optInt("pageCount", 1).coerceAtLeast(1),
            updatedAtMillis = optLong("updatedAtMillis")
        )
    }

    private fun storageKey(serverUrl: String, bookId: String, fileId: String?): String {
        return listOf(serverUrl, bookId, fileId.orEmpty()).joinToString("|")
    }
}
