package com.vangeaux.lagrange

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BrowserSnapshotStore private constructor(
    private val filesDir: File,
    private val profileId: () -> String?
) {
    private val mutex = Mutex()

    constructor(context: Context) : this(
        context.applicationContext.filesDir,
        { ServerProfileStore(context.applicationContext).activeId() }
    )

    internal constructor(filesDir: File, profileId: String? = null) : this(filesDir, { profileId })

    private fun snapshotFile(): File {
        val activeProfileId = profileId()
        return if (activeProfileId == null || !activeProfileId.startsWith("profile-")) {
            File(filesDir, "browser_snapshot.json")
        } else {
            val safeId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(activeProfileId.toByteArray(Charsets.UTF_8))
            File(filesDir, "browser_snapshot_$safeId.json")
        }
    }

    suspend fun saveLibraries(
        serverUrl: String,
        selectedLibraryId: String?,
        libraries: List<LibrarySummary>
    ) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl }
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = selectedLibraryId,
                libraries = libraries,
                booksByLibraryId = current?.booksByLibraryId.orEmpty(),
                collections = current?.collections.orEmpty(),
                collectionBooksPages = current?.collectionBooksPages.orEmpty(),
                hasCollectionSnapshot = current?.hasCollectionSnapshot ?: false
            )
        )
    }

    suspend fun saveBooks(
        serverUrl: String,
        selectedLibraryId: String?,
        libraryId: String,
        books: List<BookSummary>
    ) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl }
        val nextBooks = current?.booksByLibraryId.orEmpty().toMutableMap()
        nextBooks[libraryId] = books
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = selectedLibraryId,
                libraries = current?.libraries.orEmpty(),
                booksByLibraryId = nextBooks,
                collections = current?.collections.orEmpty(),
                collectionBooksPages = current?.collectionBooksPages.orEmpty(),
                hasCollectionSnapshot = current?.hasCollectionSnapshot ?: false
            )
        )
    }

    suspend fun appendBooks(
        serverUrl: String,
        selectedLibraryId: String?,
        libraryId: String,
        books: List<BookSummary>
    ) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl }
        val nextBooks = current?.booksByLibraryId.orEmpty().toMutableMap()
        nextBooks[libraryId] = (nextBooks[libraryId].orEmpty() + books).distinctBy { it.id }
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = selectedLibraryId,
                libraries = current?.libraries.orEmpty(),
                booksByLibraryId = nextBooks,
                collections = current?.collections.orEmpty(),
                collectionBooksPages = current?.collectionBooksPages.orEmpty(),
                hasCollectionSnapshot = current?.hasCollectionSnapshot ?: false
            )
        )
    }

    suspend fun saveCollections(serverUrl: String, collections: List<BookCollection>) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl }
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = current?.selectedLibraryId,
                libraries = current?.libraries.orEmpty(),
                booksByLibraryId = current?.booksByLibraryId.orEmpty(),
                collections = collections,
                collectionBooksPages = current?.collectionBooksPages.orEmpty(),
                hasCollectionSnapshot = true
            )
        )
    }

    suspend fun readCollections(serverUrl: String): List<BookCollection>? = mutex.withLock {
        readUnlocked()?.takeIf { it.serverUrl == serverUrl && it.hasCollectionSnapshot }?.collections
    }

    suspend fun saveCollectionBooksPage(
        serverUrl: String,
        collectionId: String,
        page: Int,
        booksPage: LibraryBooksPage
    ) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl }
        val pagesByCollection = current?.collectionBooksPages.orEmpty().toMutableMap()
        val pages = pagesByCollection[collectionId].orEmpty().toMutableMap()
        pages[page.coerceAtLeast(0)] = booksPage
        pagesByCollection[collectionId] = pages
        writeUnlocked(
            BrowserSnapshot(
                serverUrl = serverUrl,
                selectedLibraryId = current?.selectedLibraryId,
                libraries = current?.libraries.orEmpty(),
                booksByLibraryId = current?.booksByLibraryId.orEmpty(),
                collections = current?.collections.orEmpty(),
                collectionBooksPages = pagesByCollection,
                hasCollectionSnapshot = current?.hasCollectionSnapshot ?: false
            )
        )
    }

    suspend fun readCollectionBooksPage(
        serverUrl: String,
        collectionId: String,
        page: Int
    ): LibraryBooksPage? = mutex.withLock {
        readUnlocked()
            ?.takeIf { it.serverUrl == serverUrl }
            ?.collectionBooksPages
            ?.get(collectionId)
            ?.get(page.coerceAtLeast(0))
    }

    suspend fun read(serverUrl: String): BrowserSnapshot? = mutex.withLock {
        readUnlocked()?.takeIf { it.serverUrl == serverUrl }
    }

    suspend fun resetBookReadingState(serverUrl: String, bookId: String) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl } ?: return@withLock
        writeUnlocked(
            current.copy(
                booksByLibraryId = current.booksByLibraryId.mapValues { (_, books) ->
                    books.map { book ->
                        if (book.id == bookId) book.withReadingStateReset() else book
                    }
                }
            )
        )
    }

    suspend fun setBookReadingStatus(
        serverUrl: String,
        bookId: String,
        status: BookReadStatus,
        isRead: Boolean,
        lastReadAtMillis: Long?
    ) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl } ?: return@withLock
        writeUnlocked(
            current.copy(
                booksByLibraryId = current.booksByLibraryId.mapValues { (_, books) ->
                    books.map { book ->
                        if (book.id == bookId) {
                            book.copy(
                                readStatus = status,
                                isRead = isRead,
                                lastReadAtMillis = lastReadAtMillis
                            )
                        } else {
                            book
                        }
                    }
                }
            )
        )
    }
    suspend fun markBookAsRead(serverUrl: String, bookId: String, markedAtMillis: Long) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl } ?: return@withLock
        writeUnlocked(
            current.copy(
                booksByLibraryId = current.booksByLibraryId.mapValues { (_, books) ->
                    books.map { book ->
                        if (book.id == bookId) {
                            book.copy(
                                readStatus = BookReadStatus.READ,
                                isRead = true,
                                lastReadAtMillis = markedAtMillis
                            )
                        } else {
                            book
                        }
                    }
                }
            )
        )
    }

    suspend fun updateLocalPath(serverUrl: String, bookId: String, localPath: String?) = mutex.withLock {
        val current = readUnlocked()?.takeIf { it.serverUrl == serverUrl } ?: return@withLock
        writeUnlocked(
            current.copy(
                booksByLibraryId = current.booksByLibraryId.mapValues { (_, books) ->
                    books.map { book ->
                        if (book.id == bookId) book.copy(localPath = localPath) else book
                    }
                }
            )
        )
    }

    suspend fun clear() = mutex.withLock {
        val file = snapshotFile()
        if (file.exists()) {
            file.delete()
        }
    }

    private fun readUnlocked(): BrowserSnapshot? {
        val file = snapshotFile()
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
            booksByLibraryId = booksByLibraryId,
            collections = root.optJSONArray("collections").toCollections(),
            collectionBooksPages = root.optJSONObject("collectionBooksPages").toCollectionBooksPages(),
            hasCollectionSnapshot = root.has("collections")
        )
    }

    private fun writeUnlocked(snapshot: BrowserSnapshot) {
        val file = snapshotFile()
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
                            put("coverAspectRatio", library.coverAspectRatio.wireValue)
                        }
                    )
                }
            })
            put("booksByLibraryId", JSONObject().apply {
                snapshot.booksByLibraryId.forEach { (libraryId, books) ->
                    put(libraryId, JSONArray().apply {
                        books.forEach { book ->
                            put(book.toBookCacheJson())
                        }
                    })
                }
            })
            if (snapshot.hasCollectionSnapshot || snapshot.collections.isNotEmpty()) {
                put("collections", JSONArray().apply {
                    snapshot.collections.forEach { collection ->
                        put(
                            JSONObject().apply {
                                put("id", collection.id)
                                put("name", collection.name)
                                put("bookCount", collection.bookCount)
                                put("description", collection.description)
                                put("kind", collection.kind.name)
                            }
                        )
                    }
                })
            }
            put("collectionBooksPages", JSONObject().apply {
                snapshot.collectionBooksPages.forEach { (collectionId, pages) ->
                    put(collectionId, JSONObject().apply {
                        pages.forEach { (pageNumber, booksPage) ->
                            put(
                                pageNumber.toString(),
                                JSONObject().apply {
                                    put("total", booksPage.total)
                                    put("seriesTotal", booksPage.seriesTotal)
                                    put("page", booksPage.page)
                                    put("size", booksPage.size)
                                    put("isComplete", booksPage.isComplete)
                                    put("refreshedAtMillis", booksPage.refreshedAtMillis)
                                    put("items", JSONArray().apply {
                                        booksPage.items.forEach { put(it.toBookCacheJson()) }
                                    })
                                    put("jumpBuckets", JSONArray().apply {
                                        booksPage.jumpBuckets.forEach { bucket ->
                                            put(
                                                JSONObject()
                                                    .put("key", bucket.key)
                                                    .put("label", bucket.label)
                                                    .put("index", bucket.index)
                                            )
                                        }
                                    })
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
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        coverAspectRatio = CoverAspectRatio.fromWireValue(item.optString("coverAspectRatio"))
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
                val book = item.toBookCacheSummary()
                add(if (book.libraryId.isBlank()) book.copy(libraryId = defaultLibraryId) else book)
            }
        }
    }

    private fun JSONArray?.toCollections(): List<BookCollection> {
        this ?: return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(
                    BookCollection(
                        id = id,
                        name = item.optString("name"),
                        bookCount = item.optIntOrNull("bookCount"),
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        kind = runCatching { BookCollectionKind.valueOf(item.optString("kind")) }
                            .getOrDefault(BookCollectionKind.COLLECTION)
                    )
                )
            }
        }
    }

    private fun JSONObject?.toCollectionBooksPages(): Map<String, Map<Int, LibraryBooksPage>> {
        this ?: return emptyMap()
        return buildMap {
            keys().forEach collectionLoop@{ collectionId ->
                val pageObject = optJSONObject(collectionId) ?: return@collectionLoop
                val pages = buildMap {
                    pageObject.keys().forEach pageLoop@{ pageKey ->
                        val pageNumber = pageKey.toIntOrNull() ?: return@pageLoop
                        val value = pageObject.optJSONObject(pageKey) ?: return@pageLoop
                        put(
                            pageNumber,
                            LibraryBooksPage(
                                items = value.optJSONArray("items").toBooks(""),
                                total = value.optIntOrNull("total"),
                                seriesTotal = value.optIntOrNull("seriesTotal"),
                                page = value.optIntOrNull("page"),
                                size = value.optIntOrNull("size"),
                                isComplete = value.optBoolean("isComplete"),
                                refreshedAtMillis = value.optLongOrNull("refreshedAtMillis"),
                                jumpBuckets = value.optJSONArray("jumpBuckets").toJumpBuckets()
                            )
                        )
                    }
                }
                put(collectionId, pages)
            }
        }
    }

    private fun JSONArray?.toJumpBuckets(): List<LibraryJumpBucket> {
        this ?: return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    LibraryJumpBucket(
                        key = item.optString("key"),
                        label = item.optString("label"),
                        index = item.optInt("index")
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
        val booksByLibraryId: Map<String, List<BookSummary>>,
        val collections: List<BookCollection> = emptyList(),
        val collectionBooksPages: Map<String, Map<Int, LibraryBooksPage>> = emptyMap(),
        val hasCollectionSnapshot: Boolean = false
    )
}

private fun JSONArray?.toStringList(): List<String> {
    this ?: return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
