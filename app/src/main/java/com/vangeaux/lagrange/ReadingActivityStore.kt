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
data class ReadingActivityValue(val timestamp: Long, val localBaseline: Long? = null)

internal fun nextReadingActivityOrder(
    observedAtMillis: Long,
    books: List<BookSummary>,
    localActivity: Collection<Long>
): Long {
    val latestBook = books.asSequence()
        .filter { it.readStatus == BookReadStatus.READING || it.readStatus == BookReadStatus.REREADING }
        .mapNotNull { it.lastReadAtMillis }
        .maxOrNull()
    val latest = listOfNotNull(latestBook, localActivity.maxOrNull()).maxOrNull() ?: return observedAtMillis
    return maxOf(observedAtMillis, if (latest == Long.MAX_VALUE) latest else latest + 1)
}

internal suspend fun BookOrbitDataSource.recordBackgroundReadingActivity(
    serverUrl: String,
    key: ReadingActivityKey,
    observedAtMillis: Long
) {
    val orderingAt = nextReadingActivityOrder(
        observedAtMillis,
        loadCachedHomeBooks(),
        loadReadingActivity(serverUrl).values
    )
    recordReadingActivity(serverUrl, key, orderingAt)
}

internal class ReadingActivityStore(filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "reading_activity.json")

    suspend fun read(serverUrl: String): Map<ReadingActivityKey, Long> = mutex.withLock {
        readAll()[serverUrl].orEmpty().mapValues { it.value.timestamp }
    }

    suspend fun readValues(serverUrl: String): Map<ReadingActivityKey, ReadingActivityValue> = mutex.withLock {
        readAll()[serverUrl].orEmpty()
    }

    suspend fun record(serverUrl: String, key: ReadingActivityKey, timestamp: Long) = mutex.withLock {
        val all = readAll().mapValues { it.value.toMutableMap() }.toMutableMap()
        val activities = all.getOrPut(serverUrl) { mutableMapOf() }
        val previous = activities[key]
        if (previous == null || timestamp >= previous.timestamp) {
            activities[key] = ReadingActivityValue(timestamp)
        }
        writeAll(all)
    }

    suspend fun recordRemote(
        serverUrl: String,
        key: ReadingActivityKey,
        expectedTimestamp: Long,
        timestamp: Long
    ): ReadingActivityValue? = mutex.withLock {
        val all = readAll().mapValues { it.value.toMutableMap() }.toMutableMap()
        val activities = all[serverUrl] ?: return@withLock null
        val previous = activities[key] ?: return@withLock null
        if (previous.timestamp != expectedTimestamp || timestamp <= previous.timestamp) return@withLock previous
        val updated = ReadingActivityValue(timestamp, previous.localBaseline ?: previous.timestamp)
        activities[key] = updated
        writeAll(all)
        updated
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
        legacy.forEach { (key, activity) ->
            val current = activities[key]
            if (current == null || activity.timestamp > current.timestamp) activities[key] = activity
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

    private fun readAll(): Map<String, Map<ReadingActivityKey, ReadingActivityValue>> {
        if (!file.exists()) return emptyMap()
        val array = JSONArray(file.readText())
        val result = mutableMapOf<String, MutableMap<ReadingActivityKey, ReadingActivityValue>>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val serverUrl = item.getString("serverUrl")
            val key = ReadingActivityKey(item.getString("libraryId"), item.getString("bookId"))
            result.getOrPut(serverUrl) { mutableMapOf() }[key] = ReadingActivityValue(
                item.getLong("timestamp"),
                if (item.has("localBaseline") && !item.isNull("localBaseline")) item.getLong("localBaseline") else null
            )
        }
        return result
    }

    private fun writeAll(all: Map<String, Map<ReadingActivityKey, ReadingActivityValue>>) {
        val array = JSONArray()
        all.forEach { (serverUrl, activities) ->
            activities.forEach { (key, activity) ->
                array.put(JSONObject().apply {
                    put("serverUrl", serverUrl)
                    put("libraryId", key.libraryId)
                    put("bookId", key.bookId)
                    put("timestamp", activity.timestamp)
                    put("localBaseline", activity.localBaseline)
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
