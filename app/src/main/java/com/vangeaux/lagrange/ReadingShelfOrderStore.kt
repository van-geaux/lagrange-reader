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

internal class ReadingShelfOrderStore(filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "reading_shelf_order.json")

    suspend fun read(scope: String): List<ReadingActivityKey> = mutex.withLock {
        readAll()[scope].orEmpty()
    }

    suspend fun write(scope: String, order: List<ReadingActivityKey>) = mutex.withLock {
        val all = readAll().toMutableMap()
        all[scope] = order.distinct()
        writeAll(all)
    }

    suspend fun clearScope(scope: String) = mutex.withLock {
        val all = readAll().toMutableMap()
        if (all.remove(scope) != null) writeAll(all)
    }

    private fun readAll(): Map<String, List<ReadingActivityKey>> {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText())
        return root.keys().asSequence().associateWith { scope ->
            val array = root.getJSONArray(scope)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ReadingActivityKey(item.getString("libraryId"), item.getString("bookId"))
            }
        }
    }

    private fun writeAll(all: Map<String, List<ReadingActivityKey>>) {
        val root = JSONObject()
        all.forEach { (scope, order) ->
            root.put(scope, JSONArray().apply {
                order.forEach { key ->
                    put(JSONObject().put("libraryId", key.libraryId).put("bookId", key.bookId))
                }
            })
        }
        val directory = requireNotNull(file.parentFile)
        Files.createDirectories(directory.toPath())
        val temporary = Files.createTempFile(directory.toPath(), "reading-shelf-order-", ".tmp")
        try {
            Files.write(temporary, root.toString().toByteArray(Charsets.UTF_8))
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
