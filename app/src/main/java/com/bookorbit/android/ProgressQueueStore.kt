package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProgressQueueStore(context: Context) {
    private val mutex = Mutex()
    private val file = File(context.filesDir, "pending_progress.json")

    suspend fun readAll(): List<ProgressUpdate> = mutex.withLock {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(obj.toUpdate())
            }
        }
    }

    suspend fun enqueue(update: ProgressUpdate) = mutex.withLock {
        val current = readUnlocked().toMutableList()
        current += update
        writeUnlocked(current)
    }

    suspend fun replaceAll(items: List<ProgressUpdate>) = mutex.withLock {
        writeUnlocked(items)
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    suspend fun latestFor(bookId: String, fileId: String?): ProgressUpdate? = mutex.withLock {
        readUnlocked()
            .filter { it.bookId == bookId || (fileId != null && it.fileId == fileId) }
            .maxByOrNull { it.updatedAtMillis }
    }

    private fun readUnlocked(): List<ProgressUpdate> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(obj.toUpdate())
            }
        }
    }

    private fun writeUnlocked(items: List<ProgressUpdate>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("serverUrl", item.serverUrl)
                    put("bookId", item.bookId)
                    put("fileId", item.fileId)
                    put("mediaKind", item.mediaKind.name)
                    put("positionMs", item.positionMs)
                    put("pageIndex", item.pageIndex)
                    put("progressPercent", item.progressPercent)
                    put("updatedAtMillis", item.updatedAtMillis)
                }
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun JSONObject.toUpdate(): ProgressUpdate {
        return ProgressUpdate(
            id = optString("id"),
            serverUrl = optString("serverUrl"),
            bookId = optString("bookId"),
            fileId = optString("fileId").takeIf { it.isNotBlank() },
            mediaKind = runCatching { MediaKind.valueOf(optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
            positionMs = optLong("positionMs"),
            pageIndex = optInt("pageIndex"),
            progressPercent = if (has("progressPercent") && !isNull("progressPercent")) optDouble("progressPercent").toFloat() else null,
            updatedAtMillis = optLong("updatedAtMillis")
        )
    }
}
