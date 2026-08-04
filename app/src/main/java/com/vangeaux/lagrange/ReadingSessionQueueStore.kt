package com.vangeaux.lagrange

import android.content.Context
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal class ReadingSessionQueueStore private constructor(
    private val file: File,
    @Suppress("UNUSED_PARAMETER") private val directFileConstructor: Boolean
) {
    constructor(context: Context) : this(
        file = File(context.filesDir, "pending_reading_sessions.json"),
        directFileConstructor = true
    )

    internal constructor(filesDir: File) : this(
        file = File(filesDir, "pending_reading_sessions.json"),
        directFileConstructor = true
    )

    suspend fun readAll(): List<ReadingSessionPayload> = mutex.withLock {
        readUnlocked()
    }

    suspend fun enqueue(payload: ReadingSessionPayload) = mutex.withLock {
        writeUnlocked(readUnlocked() + payload)
    }

    suspend fun acknowledge(sessionIds: Set<String>) = mutex.withLock {
        if (sessionIds.isEmpty()) return@withLock
        writeUnlocked(readUnlocked().filterNot { it.sessionId in sessionIds })
    }

    suspend fun countFor(serverUrl: String): Int = mutex.withLock {
        readUnlocked().count { it.serverUrl == serverUrl }
    }

    suspend fun clearServer(serverUrl: String) = mutex.withLock {
        writeUnlocked(readUnlocked().filterNot { it.serverUrl == serverUrl })
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    private fun readUnlocked(): List<ReadingSessionPayload> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toPayload()?.let(::add)
                }
            }.sortedBy { it.updatedAtMillis }
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(items: List<ReadingSessionPayload>) {
        val array = JSONArray()
        items.sortedBy { it.updatedAtMillis }.forEach { payload ->
            array.put(
                JSONObject().apply {
                    put("sessionId", payload.sessionId)
                    put("serverUrl", payload.serverUrl)
                    put("fileId", payload.fileId)
                    put("startedAtMillis", payload.startedAtMillis)
                    put("endedAtMillis", payload.endedAtMillis)
                    put("durationSeconds", payload.durationSeconds)
                    put("progressDelta", payload.progressDelta ?: JSONObject.NULL)
                    put("endProgress", payload.endProgress ?: JSONObject.NULL)
                    put("updatedAtMillis", payload.updatedAtMillis)
                }
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun JSONObject.toPayload(): ReadingSessionPayload? {
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        val serverUrl = optString("serverUrl")
        val fileId = optString("fileId")
        if (serverUrl.isBlank() || fileId.isBlank()) return null
        return ReadingSessionPayload(
            sessionId = sessionId,
            serverUrl = serverUrl,
            fileId = fileId,
            startedAtMillis = optLong("startedAtMillis"),
            endedAtMillis = optLong("endedAtMillis"),
            durationSeconds = optLong("durationSeconds"),
            progressDelta = optNullableDouble("progressDelta"),
            endProgress = optNullableDouble("endProgress"),
            updatedAtMillis = optLong("updatedAtMillis")
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private companion object {
        val mutex = Mutex()
    }
}
