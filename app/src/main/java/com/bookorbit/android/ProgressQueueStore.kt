package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProgressQueueStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(
        file = File(context.filesDir, "pending_progress.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "pending_progress.json"),
        directFileConstructor = true
    )

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
        val current = readUnlocked()
            .filterNot { it.matchesTarget(update) }
            .toMutableList()
        current += update
        writeUnlocked(current)
    }

    suspend fun replaceAll(items: List<ProgressUpdate>) = mutex.withLock {
        writeUnlocked(compact(items))
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    suspend fun latestFor(
        serverUrl: String,
        bookId: String,
        fileId: String?,
        mediaKind: MediaKind
    ): ProgressUpdate? = mutex.withLock {
        readUnlocked()
            .filter {
                it.serverUrl == serverUrl &&
                    it.mediaKind == mediaKind &&
                    it.bookId == bookId &&
                    it.fileId == fileId
            }
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
        compact(items).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("serverUrl", item.serverUrl)
                    put("bookId", item.bookId)
                    put("fileId", item.fileId)
                    put("mediaKind", item.mediaKind.name)
                    put("positionMs", item.positionMs)
                    put("pageIndex", item.pageIndex)
                    put("progressPercent", normalizeStoredProgressPercent(item.progressPercent))
                    put("updatedAtMillis", item.updatedAtMillis)
                }
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun compact(items: List<ProgressUpdate>): List<ProgressUpdate> {
        val latestByTarget = LinkedHashMap<String, ProgressUpdate>()
        items.sortedBy { it.updatedAtMillis }.forEach { item ->
            latestByTarget[item.targetKey()] = item
        }
        return latestByTarget.values.sortedBy { it.updatedAtMillis }
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
            progressPercent = if (has("progressPercent") && !isNull("progressPercent")) normalizeStoredProgressPercent(optDouble("progressPercent").toFloat()) else null,
            updatedAtMillis = optLong("updatedAtMillis")
        )
    }

    private fun ProgressUpdate.matchesTarget(other: ProgressUpdate): Boolean {
        return targetKey() == other.targetKey()
    }

    private fun ProgressUpdate.targetKey(): String {
        return listOf(serverUrl, mediaKind.name, bookId, fileId.orEmpty()).joinToString("|")
    }
}
