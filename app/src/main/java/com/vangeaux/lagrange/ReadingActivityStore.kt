package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class ReadingActivityKey(val libraryId: String, val bookId: String)

internal class ReadingActivityStore(filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "reading_activity.json")

    suspend fun read(serverUrl: String): Map<ReadingActivityKey, Long> = mutex.withLock {
        readAll()[serverUrl].orEmpty()
    }

    suspend fun record(serverUrl: String, key: ReadingActivityKey, timestamp: Long) = mutex.withLock {
        val all = readAll().mapValues { it.value.toMutableMap() }.toMutableMap()
        val activities = all.getOrPut(serverUrl) { mutableMapOf() }
        activities[key] = maxOf(activities[key] ?: timestamp, timestamp)
        writeAll(all)
    }

    suspend fun remove(serverUrl: String, key: ReadingActivityKey) = mutex.withLock {
        val all = readAll().mapValues { it.value.toMutableMap() }.toMutableMap()
        all[serverUrl]?.remove(key)
        writeAll(all)
    }

    suspend fun migrateLegacy(serverUrl: String, scope: String) = mutex.withLock {
        require(serverUrl != scope)
        val all = readAll().mapValues { it.value.toMutableMap() }.toMutableMap()
        val legacy = all.remove(serverUrl) ?: return@withLock
        val activities = all.getOrPut(scope) { mutableMapOf() }
        legacy.forEach { (key, timestamp) ->
            activities[key] = maxOf(activities[key] ?: timestamp, timestamp)
        }
        writeAll(all)
    }

    suspend fun clearScope(scope: String) = mutex.withLock {
        val all = readAll().mapValues { it.value.toMutableMap() }.toMutableMap()
        if (all.remove(scope) != null) writeAll(all)
    }

    suspend fun preserveLegacy(serverUrl: String, scope: String?) {
        if (serverUrl.isBlank()) return
        if (scope == null) clearScope(serverUrl) else migrateLegacy(serverUrl, scope)
    }

    suspend fun clear() = mutex.withLock {
        Files.deleteIfExists(file.toPath())
    }

    private fun readAll(): Map<String, Map<ReadingActivityKey, Long>> {
        if (!file.exists()) return emptyMap()
        val array = JSONArray(file.readText())
        val result = mutableMapOf<String, MutableMap<ReadingActivityKey, Long>>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val serverUrl = item.getString("serverUrl")
            val key = ReadingActivityKey(item.getString("libraryId"), item.getString("bookId"))
            result.getOrPut(serverUrl) { mutableMapOf() }[key] = item.getLong("timestamp")
        }
        return result
    }

    private fun writeAll(all: Map<String, Map<ReadingActivityKey, Long>>) {
        val array = JSONArray()
        all.forEach { (serverUrl, activities) ->
            activities.forEach { (key, timestamp) ->
                array.put(JSONObject().apply {
                    put("serverUrl", serverUrl)
                    put("libraryId", key.libraryId)
                    put("bookId", key.bookId)
                    put("timestamp", timestamp)
                })
            }
        }
        val directory = requireNotNull(file.parentFile)
        Files.createDirectories(directory.toPath())
        val temporary = Files.createTempFile(directory.toPath(), "reading-activity-", ".tmp")
        try {
            Files.write(temporary, array.toString().toByteArray(Charsets.UTF_8))
            try {
                Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        val mutex = Mutex()
    }
}
