package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Stores raw catalog pages so new fields can be added without invalidating old snapshots. */
class CatalogSnapshotStore(context: Context) {
    private val mutex = Mutex()
    private val file = File(context.filesDir, "catalog_snapshots.json")

    suspend fun saveSeries(serverUrl: String, query: String?, page: Int, payload: String) = mutex.withLock {
        savePage(serverUrl, "series", pageKey(query, page), payload)
    }

    suspend fun readSeries(serverUrl: String, query: String?, page: Int): String? = mutex.withLock {
        readPage(serverUrl, "series", pageKey(query, page))
    }

    suspend fun saveAuthors(serverUrl: String, query: String?, page: Int, payload: String) = mutex.withLock {
        savePage(serverUrl, "authors", pageKey(query, page), payload)
    }

    suspend fun readAuthors(serverUrl: String, query: String?, page: Int): String? = mutex.withLock {
        readPage(serverUrl, "authors", pageKey(query, page))
    }

    suspend fun saveAuthorBooks(serverUrl: String, authorId: String, page: Int, payload: String) = mutex.withLock {
        savePage(serverUrl, "authorBooks", pageKey(authorId, page), payload)
    }

    suspend fun readAuthorBooks(serverUrl: String, authorId: String, page: Int): String? = mutex.withLock {
        readPage(serverUrl, "authorBooks", pageKey(authorId, page))
    }

    private fun savePage(serverUrl: String, catalog: String, key: String, payload: String) {
        val root = readRoot()
        val servers = root.optJSONObject("servers") ?: JSONObject().also { root.put("servers", it) }
        val server = servers.optJSONObject(serverUrl) ?: JSONObject().also { servers.put(serverUrl, it) }
        val pages = server.optJSONObject(catalog) ?: JSONObject().also { server.put(catalog, it) }
        pages.put(key, payload)
        root.put("version", SNAPSHOT_VERSION)
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    private fun readPage(serverUrl: String, catalog: String, key: String): String? {
        val server = readRoot().optJSONObject("servers")?.optJSONObject(serverUrl) ?: return null
        return server.optJSONObject(catalog)?.optString(key)?.takeIf { it.isNotBlank() }
    }

    private fun readRoot(): JSONObject {
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }
            .getOrElse { JSONObject() }
    }

    private fun pageKey(scope: String?, page: Int): String = "${scope.orEmpty()}|${page.coerceAtLeast(0)}"

    private companion object {
        const val SNAPSHOT_VERSION = 1
    }
}
