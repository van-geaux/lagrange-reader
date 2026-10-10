package com.vangeaux.lagrange

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal data class LocalBookReaderLease(
    val ownerId: String,
    val serverUrl: String,
    val storageScopeId: String?,
    val fileId: String?,
    val localPath: String?
)

internal class LocalBookReaderLeaseStore(context: Context) {
    private companion object {
        const val LEASES_KEY = "leases"
        val processInstanceId: String = java.util.UUID.randomUUID().toString()
        val monitor = Any()
    }

    private val preferences = context.applicationContext.getSharedPreferences(
        "local_book_reader_leases",
        Context.MODE_PRIVATE
    )

    fun acquire(
        ownerId: String,
        serverUrl: String,
        fileId: String?,
        localPath: String?,
        storageScopeId: String? = null
    ) = runBlocking {
        localCopyDeletionGuard.withLock {
            synchronized(monitor) {
                val leases = readUnlocked()
                    .filterNot { it.optString("ownerId") == ownerId }
                    .toMutableList()
                leases += JSONObject()
                    .put("processInstanceId", processInstanceId)
                    .put("ownerId", ownerId)
                    .put("serverUrl", serverUrl)
                    .put("storageScopeId", storageScopeId)
                    .put("fileId", fileId)
                    .put("localPath", localPath)
                writeUnlocked(leases)
            }
        }
    }

    fun release(ownerId: String) {
        synchronized(monitor) {
            writeUnlocked(readUnlocked().filterNot { it.optString("ownerId") == ownerId })
        }
    }

    fun readLive(): List<LocalBookReaderLease> = synchronized(monitor) {
        val all = readUnlocked()
        val current = all.filter { it.optString("processInstanceId") == processInstanceId }
        if (current.size != all.size) writeUnlocked(current)
        current.mapNotNull { value ->
            value.optString("ownerId").takeIf(String::isNotBlank)?.let { ownerId ->
                LocalBookReaderLease(
                    ownerId = ownerId,
                    serverUrl = value.optString("serverUrl"),
                    storageScopeId = value.optString("storageScopeId").takeIf(String::isNotBlank),
                    fileId = value.optString("fileId").takeIf(String::isNotBlank),
                    localPath = value.optString("localPath").takeIf(String::isNotBlank)
                )
            }
        }
    }

    private fun readUnlocked(): List<JSONObject> {
        val raw = preferences.getString(LEASES_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(values: List<JSONObject>) {
        preferences.edit().putString(LEASES_KEY, JSONArray(values).toString()).apply()
    }
}
