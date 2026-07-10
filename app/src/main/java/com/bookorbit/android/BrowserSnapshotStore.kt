package com.bookorbit.android

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BrowserSnapshotStore(context: Context) {
    private val mutex = Mutex()
    private val file = File(context.filesDir, "browser_snapshot.json")

    suspend fun saveLibraries(
        serverUrl: String,
        selectedLibraryId: String?,
        libraries: List<LibrarySummary>
    ) = mutex.withLock {
        val current = readUnlocked()
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = selectedLibraryId,
                libraries = libraries,
                booksByLibraryId = current?.booksByLibraryId.orEmpty()
            )
        )
    }

    suspend fun saveBooks(
        serverUrl: String,
        selectedLibraryId: String?,
        libraryId: String,
        books: List<BookSummary>
    ) = mutex.withLock {
        val current = readUnlocked()
        val nextBooks = current?.booksByLibraryId.orEmpty().toMutableMap()
        nextBooks[libraryId] = books
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = selectedLibraryId,
                libraries = current?.libraries.orEmpty(),
                booksByLibraryId = nextBooks
            )
        )
    }

    suspend fun read(serverUrl: String): BrowserSnapshot? = mutex.withLock {
        readUnlocked()?.takeIf { it.serverUrl == serverUrl }
    }

    suspend fun clear() = mutex.withLock {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun readUnlocked(): BrowserSnapshot? {
        if (!file.exists()) {
            return null
        }
        val root = JSONObject(file.readText())
        val libraries = root.optJSONArray("libraries").toLibraries()
        val booksByLibraryId = buildMap {
            val booksObject = root.optJSONObject("booksByLibraryId") ?: JSONObject()
            booksObject.keys().forEach { libraryId ->
                put(libraryId, booksObject.optJSONArray(libraryId).toBooks(libraryId))
            }
        }
        return BrowserSnapshot(
            serverUrl = root.optString("serverUrl"),
            selectedLibraryId = root.optString("selectedLibraryId").takeIf { it.isNotBlank() },
            libraries = libraries,
            booksByLibraryId = booksByLibraryId
        )
    }

    private fun writeUnlocked(snapshot: BrowserSnapshot) {
        val root = JSONObject().apply {
            put("serverUrl", snapshot.serverUrl)
            put("selectedLibraryId", snapshot.selectedLibraryId)
            put("libraries", JSONArray().apply {
                snapshot.libraries.forEach { library ->
                    put(
                        JSONObject().apply {
                            put("id", library.id)
                            put("name", library.name)
                            put("description", library.description)
                        }
                    )
                }
            })
            put("booksByLibraryId", JSONObject().apply {
                snapshot.booksByLibraryId.forEach { (libraryId, books) ->
                    put(libraryId, JSONArray().apply {
                        books.forEach { book ->
                            put(
                                JSONObject().apply {
                                    put("libraryId", book.libraryId)
                                    put("id", book.id)
                                    put("fileId", book.fileId)
                                    put("title", book.title)
                                    put("author", book.author)
                                    put("format", book.format)
                                    put("mediaKind", book.mediaKind.name)
                                    put("streamUrl", book.streamUrl)
                                    put("downloadUrl", book.downloadUrl)
                                    put("coverUrl", book.coverUrl)
                                    put("localPath", book.localPath)
                                    put("progressLabel", book.progressLabel)
                                    put("progressPercent", normalizeStoredProgressPercent(book.progressPercent))
                                    put("progressPositionMs", book.progressPositionMs)
                                    put("progressPageIndex", book.progressPageIndex)
                                    put("seriesId", book.seriesId)
                                    put("seriesName", book.seriesName)
                                    put("seriesIndex", book.seriesIndex)
                                    put("isRead", book.isRead)
                                    put("addedAtMillis", book.addedAtMillis)
                                    put("updatedAtMillis", book.updatedAtMillis)
                                    put("lastReadAtMillis", book.lastReadAtMillis)
                                }
                            )
                        }
                    })
                }
            })
        }
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    private fun JSONArray?.toLibraries(): List<LibrarySummary> {
        this ?: return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    LibrarySummary(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        description = item.optString("description").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun JSONArray?.toBooks(defaultLibraryId: String): List<BookSummary> {
        this ?: return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    BookSummary(
                        libraryId = item.optString("libraryId").ifBlank { defaultLibraryId },
                        id = item.optString("id"),
                        fileId = item.optString("fileId").takeIf { it.isNotBlank() },
                        title = item.optString("title"),
                        author = item.optString("author").takeIf { it.isNotBlank() },
                        format = item.optString("format").takeIf { it.isNotBlank() },
                        mediaKind = runCatching {
                            MediaKind.valueOf(item.optString("mediaKind"))
                        }.getOrDefault(MediaKind.UNKNOWN),
                        streamUrl = item.optString("streamUrl").takeIf { it.isNotBlank() },
                        downloadUrl = item.optString("downloadUrl").takeIf { it.isNotBlank() },
                        coverUrl = item.optString("coverUrl").takeIf { it.isNotBlank() },
                        localPath = item.optString("localPath").takeIf { it.isNotBlank() },
                        progressLabel = item.optString("progressLabel").takeIf { it.isNotBlank() },
                        progressPercent = normalizeStoredProgressPercent(item.optFloat("progressPercent")),
                        progressPositionMs = item.optLongOrNull("progressPositionMs"),
                        progressPageIndex = item.optIntOrNull("progressPageIndex"),
                        seriesId = item.optString("seriesId").takeIf { it.isNotBlank() },
                        seriesName = item.optString("seriesName").takeIf { it.isNotBlank() },
                        seriesIndex = item.optDoubleOrNull("seriesIndex"),
                        isRead = item.optBoolean("isRead"),
                        addedAtMillis = item.optLongOrNull("addedAtMillis"),
                        updatedAtMillis = item.optLongOrNull("updatedAtMillis"),
                        lastReadAtMillis = item.optLongOrNull("lastReadAtMillis")
                    )
                )
            }
        }
    }

    private fun JSONObject.optFloat(key: String): Float? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return optDouble(key).toFloat()
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return optLong(key)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return optInt(key)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return optDouble(key)
    }

    data class BrowserSnapshot(
        val serverUrl: String,
        val selectedLibraryId: String?,
        val libraries: List<LibrarySummary>,
        val booksByLibraryId: Map<String, List<BookSummary>>
    )
}
