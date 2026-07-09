package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LastSyncedProgressStore(context: Context) {
    private val mutex = Mutex()
    private val file = File(context.filesDir, "last_synced_progress.json")

    suspend fun read(key: ProgressKey): ProgressUpdate? = mutex.withLock {
        readUnlocked()[key.storageKey()]
    }

    suspend fun save(update: ProgressUpdate) = mutex.withLock {
        val current = readUnlocked().toMutableMap()
        current[update.progressKey().storageKey()] = update
        writeUnlocked(current)
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun readUnlocked(): Map<String, ProgressUpdate> {
        if (!file.exists()) {
            return emptyMap()
        }
        val array = JSONArray(file.readText())
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val update = item.toUpdate()
                put(update.progressKey().storageKey(), update)
            }
        }
    }

    private fun writeUnlocked(items: Map<String, ProgressUpdate>) {
        val array = JSONArray()
        items.values.sortedBy { it.updatedAtMillis }.forEach { item ->
            array.put(
                JSONObject().apply {
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
            id = "",
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

data class ProgressKey(
    val serverUrl: String,
    val bookId: String,
    val fileId: String?,
    val mediaKind: MediaKind
)

fun ProgressUpdate.progressKey(): ProgressKey {
    return ProgressKey(
        serverUrl = serverUrl,
        bookId = bookId,
        fileId = fileId,
        mediaKind = mediaKind
    )
}

fun ProgressKey.storageKey(): String {
    return listOf(serverUrl, mediaKind.name, bookId, fileId.orEmpty()).joinToString("|")
}
