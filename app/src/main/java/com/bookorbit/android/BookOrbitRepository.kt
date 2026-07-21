package com.bookorbit.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.text.DecimalFormat
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.net.ssl.SSLException

private val Context.dataStore by preferencesDataStore(name = "bookorbit_prefs")
private const val LIBRARY_PAGE_SIZE = 100
private const val LIBRARY_MAX_CONCURRENT_PAGE_REQUESTS = 4
private const val LIBRARY_PARALLEL_PAGE_THRESHOLD = 4
private const val COMPLETED_PROGRESS_PERCENT = 99.5f
private val progressSyncMutex = Mutex()

private enum class ProgressPostResult {
    ACCEPTED,
    ALREADY_SYNCED,
    INVALID,
    DEFERRED
}

internal fun extractAccessToken(payload: String): String? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null

    fun tokenFrom(obj: JSONObject?): String? {
        obj ?: return null
        return listOf("accessToken", "access_token", "token")
            .firstNotNullOfOrNull { key ->
                when (val value = obj.opt(key)) {
                    is JSONObject -> value.optString("token").takeIf { it.isNotBlank() }
                        ?: value.optString("value").takeIf { it.isNotBlank() }
                        ?: value.optString("accessToken").takeIf { it.isNotBlank() }
                    else -> obj.optString(key).takeIf { it.isNotBlank() }
                }
            }
    }

    return tokenFrom(root)
        ?: tokenFrom(root.optJSONObject("data"))
        ?: tokenFrom(root.optJSONObject("result"))
}

interface BookOrbitDataSource {
    suspend fun getServerUrl(): String?
    suspend fun setServerUrl(serverUrl: String)
    suspend fun clearServer()
    suspend fun clearSession()
    suspend fun getSelectedLibraryId(): String?
    suspend fun setSelectedLibraryId(libraryId: String)
    suspend fun getSessionState(): SessionState
    suspend fun login(username: String, password: String)
    suspend fun loadLibraries(): List<LibrarySummary>
    suspend fun loadBooks(libraryId: String): List<BookSummary>
    suspend fun loadBooksPage(libraryId: String, page: Int): LibraryBooksPage {
        if (page != 0) return LibraryBooksPage(page = page)
        val books = loadBooks(libraryId)
        return LibraryBooksPage(items = books, total = books.size, page = 0, size = books.size)
    }
    suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage = loadBooksPage(libraryId, page)
    suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = null
    suspend fun refreshLibraryCatalog(
        libraryId: String,
        firstPage: LibraryBooksPage? = null
    ): LibraryBooksPage = (firstPage ?: loadBooksPage(libraryId, 0)).copy(isComplete = true)
    suspend fun loadCachedHomeBooks(): List<BookSummary> = emptyList()
    suspend fun loadLocalBooks(): List<BookSummary> = emptyList()
    suspend fun loadSeriesCatalog(query: String? = null, page: Int = 0): SeriesCatalogPage = SeriesCatalogPage()
    suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int = 0): SeriesCatalogPage =
        loadSeriesCatalog(filter.query, page)
    suspend fun loadAuthorsCatalog(query: String? = null, page: Int = 0): AuthorCatalogPage = AuthorCatalogPage()
    suspend fun loadAuthorBooks(authorId: String, page: Int = 0): AuthorBooksPage? = null
    suspend fun loadAchievements(): AchievementCatalogue = AchievementCatalogue(
        status = AchievementCatalogueStatus.UNSUPPORTED
    )
    suspend fun searchBooks(query: String): List<BookSummary> = emptyList()
    suspend fun loadBookCover(book: BookSummary): ByteArray? = null
    suspend fun loadCatalogImage(url: String): ByteArray? = null
    suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? = null
    suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? = null
    suspend fun loadCachedBrowserState(libraryId: String? = null): BrowserState?
    suspend fun buildReaderState(book: BookSummary, localOnly: Boolean = false): ReaderState
    suspend fun saveActiveReader(book: BookSummary)
    suspend fun clearActiveReader()
    suspend fun restoreActiveReaderState(localOnly: Boolean = false): ReaderState?
    suspend fun saveEpubReaderPosition(book: BookSummary) = Unit
    suspend fun markBookAsRead(book: BookSummary) = Unit
    suspend fun resetBookReadingState(book: BookSummary) = Unit
    suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit = {}): File
    suspend fun deleteLocalCopy(book: BookSummary)
    suspend fun loadStorageUsage(): StorageUsage = StorageUsage()
    suspend fun clearAppCache() = Unit
    suspend fun reconfigureBackgroundRefresh() = Unit
    suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?)
    suspend fun pendingProgressCount(): Int
    suspend fun syncPendingProgress(): SyncAttemptResult
    suspend fun canReachServer(serverUrl: String): Boolean
    suspend fun checkServer(serverUrl: String): ServerCheckResult
}

class BookOrbitRepository(private val context: Context) : BookOrbitDataSource {
    private val queueStore = ProgressQueueStore(context)
    private val downloadStore = DownloadStore(context)
    private val browserSnapshotStore = BrowserSnapshotStore(context)
    private val libraryCatalogStore = LibraryCatalogStore(context)
    private val catalogSnapshotStore = CatalogSnapshotStore(context)
    private val coverCacheStore = CoverCacheStore(context)
    private val bookDetailCacheStore = BookDetailCacheStore(context)
    private val activeReaderStore = ActiveReaderStore(context)
    private val epubReaderPositionStore = EpubReaderPositionStore(context)
    private val lastSyncedProgressStore = LastSyncedProgressStore(context)
    private val appStorageManager = AppStorageManager(context)
    private val coverCache = LinkedHashMap<String, ByteArray>(32, 0.75f, true)
    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val sessionRefreshLock = Any()

    override suspend fun getServerUrl(): String? = context.dataStore.data.first()[Keys.SERVER_URL]

    override suspend fun setServerUrl(serverUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = normalizeStoredServerUrl(serverUrl)
        }
    }

    override suspend fun clearServer() {
        CoverCacheWarmWorker.cancelAll(context)
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.SELECTED_LIBRARY_ID)
        }
        coverCacheStore.clear()
        bookDetailCacheStore.clear()
        clearSession()
    }

    override suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
        }
        activeReaderStore.clear()
        epubReaderPositionStore.clear()
        clearCookies()
    }

    override suspend fun getSelectedLibraryId(): String? = context.dataStore.data.first()[Keys.SELECTED_LIBRARY_ID]

    override suspend fun setSelectedLibraryId(libraryId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_LIBRARY_ID] = libraryId
        }
    }

    override suspend fun getSessionState(): SessionState = withContext(Dispatchers.IO) {
        runCatching {
            request("/api/v1/auth/me", "GET", null)
            SessionState.Authenticated
        }.getOrElse { error ->
            when (error) {
                is AuthenticationRequiredException -> SessionState.Unauthenticated
                else -> SessionState.Unavailable
            }
        }
    }

    override suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            throw InvalidCredentialsException()
        }
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
            .toRequestBody(JSON)
        requestLogin(body)
    }

    override suspend fun loadAchievements(): AchievementCatalogue = withContext(Dispatchers.IO) {
        try {
            BookOrbitPayloadParser.parseAchievements(
                request("/api/v1/achievements", "GET", null)
            )
        } catch (error: HttpRequestException) {
            if (error.code == 404) {
                AchievementCatalogue(status = AchievementCatalogueStatus.UNSUPPORTED)
            } else {
                throw error
            }
        }
    }

    override suspend fun loadLibraries(): List<LibrarySummary> = withContext(Dispatchers.IO) {
        BookOrbitPayloadParser.parseLibraries(request("/api/v1/libraries", "GET", null)).also { libraries ->
            browserSnapshotStore.saveLibraries(
                serverUrl = getServerUrl().orEmpty(),
                selectedLibraryId = getSelectedLibraryId(),
                libraries = libraries
            )
        }
    }

    override suspend fun loadBooks(libraryId: String): List<BookSummary> {
        return loadBooksPage(libraryId, 0).items
    }

    override suspend fun loadBooksPage(libraryId: String, page: Int): LibraryBooksPage = withContext(Dispatchers.IO) {
        loadBooksPage(libraryId, page, BookBrowseFilter())
    }

    override suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        requestLibraryBooksPage(libraryId, page, filter, downloads)
    }

    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? =
        withContext(Dispatchers.IO) {
            val serverUrl = getServerUrl().orEmpty()
            val cached = libraryCatalogStore.read(serverUrl, libraryId) ?: return@withContext null
            val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
            LibraryBooksPage(
                items = cached.books.withCurrentDownloads(downloads),
                total = cached.total,
                seriesTotal = cached.seriesTotal,
                page = 0,
                size = cached.pageSize,
                isComplete = true,
                refreshedAtMillis = cached.refreshedAtMillis,
                jumpBuckets = cached.jumpBuckets
            )
        }

    override suspend fun loadCachedHomeBooks(): List<BookSummary> = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        val catalogBooks = libraryCatalogStore.readAllBooks(serverUrl)
        val books = catalogBooks.ifEmpty {
            browserSnapshotStore.read(serverUrl)
                ?.booksByLibraryId
                ?.values
                ?.flatten()
                .orEmpty()
        }
        books.withCurrentDownloads(downloads)
    }

    override suspend fun refreshLibraryCatalog(
        libraryId: String,
        firstPage: LibraryBooksPage?
    ): LibraryBooksPage = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        suspend fun loadPages(seed: LibraryBooksPage?): List<LibraryBooksPage> = loadCompleteLibraryPages(seed) { page ->
            requestLibraryBooksPage(
                libraryId = libraryId,
                page = page,
                filter = BookBrowseFilter(),
                downloads = downloads
            )
        }

        var pages = loadPages(firstPage)
        var books = mergeLibraryBooks(pages)
        if (!libraryCatalogPagesAreStable(pages, books)) {
            // The library changed while page offsets were being traversed. Retry once
            // from page zero so a shifted deletion/addition cannot create a partial cache.
            pages = loadPages(null)
            books = mergeLibraryBooks(pages)
        }
        if (!libraryCatalogPagesAreStable(pages, books)) {
            throw UserFacingException(
                "The library changed while it was refreshing. Pull to refresh again."
            )
        }
        val reportedTotal = pages.mapNotNull { it.total }.lastOrNull()
        val total = reportedTotal ?: books.size
        val seriesTotal = pages.mapNotNull { it.seriesTotal }.maxOrNull()
        val pageSize = pages.firstOrNull()?.size?.takeIf { it > 0 } ?: LIBRARY_PAGE_SIZE
        val jumpResponse = runCatching { requestLibraryJumpBuckets(libraryId, BookBrowseFilter()) }
            .getOrElse { error ->
                if (error is AuthenticationRequiredException) throw error
                LibraryJumpBucketsResponse(emptyList(), total)
            }
        val jumpBuckets = jumpResponse.buckets
            .filter { bucket -> bucket.index in books.indices }
            .takeIf { jumpResponse.total == total }
            .orEmpty()
        val refreshedAtMillis = System.currentTimeMillis()
        libraryCatalogStore.replace(
            serverUrl = serverUrl,
            libraryId = libraryId,
            pageSize = pageSize,
            total = total,
            seriesTotal = seriesTotal,
            books = books,
            jumpBuckets = jumpBuckets,
            refreshedAtMillis = refreshedAtMillis
        )
        CoverCacheWarmWorker.enqueue(context, serverUrl, libraryId)
        LibraryBooksPage(
            items = books,
            total = total,
            seriesTotal = seriesTotal,
            page = 0,
            size = pageSize,
            isComplete = true,
            refreshedAtMillis = refreshedAtMillis,
            jumpBuckets = jumpBuckets
        )
    }

    private fun requestLibraryBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter,
        downloads: Map<String, DownloadRecord>
    ): LibraryBooksPage {
        return BookOrbitPayloadParser.parseLibraryBooksPage(
            libraryId = libraryId,
            payload = request(
                path = "/api/v1/libraries/$libraryId/books",
                method = "POST",
                body = libraryQueryPayload(filter, page).toRequestBody(JSON)
            ),
            downloads = downloads,
            serverBase = serverBase()
        )
    }

    private fun requestLibraryJumpBuckets(
        libraryId: String,
        filter: BookBrowseFilter
    ): LibraryJumpBucketsResponse {
        return BookOrbitPayloadParser.parseLibraryJumpBuckets(
            request(
                path = "/api/v1/libraries/$libraryId/books/jump-buckets",
                method = "POST",
                body = libraryQueryPayload(filter, 0).toRequestBody(JSON)
            )
        )
    }

    private fun libraryQueryPayload(filter: BookBrowseFilter, page: Int): String {
        return JSONObject().apply {
            put("sort", JSONArray().apply {
                filter.sort.serverField?.let { field ->
                    put(JSONObject().put("field", field).put("dir", filter.direction.serverValue))
                }
            })
            filter.toServerFilter()?.let { put("filter", it) }
            put("pagination", JSONObject().apply {
                put("page", page.coerceAtLeast(0))
                put("size", LIBRARY_PAGE_SIZE)
            })
        }.toString()
    }

    override suspend fun loadLocalBooks(): List<BookSummary> = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val downloads = downloadStore.readAll(serverUrl)
        val catalogBooks = libraryCatalogStore.readAllBooks(serverUrl)
        val snapshotBooks = catalogBooks.ifEmpty {
            browserSnapshotStore.read(serverUrl)
                ?.booksByLibraryId
                ?.values
                ?.flatten()
                .orEmpty()
        }
        downloads
            .sortedByDescending { it.downloadedAtMillis }
            .map { record ->
                val snapshotBook = snapshotBooks.firstOrNull { book ->
                    book.id == record.bookId || book.fileId == record.fileId
                }
                val cachedDetailBook = bookDetailCacheStore.readLatest(
                    serverUrl = serverUrl,
                    bookId = record.bookId,
                    fileId = record.fileId
                )?.book
                val metadataBook = snapshotBook?.copy(
                    coverUrl = snapshotBook.coverUrl ?: cachedDetailBook?.coverUrl,
                    updatedAtMillis = snapshotBook.updatedAtMillis ?: cachedDetailBook?.updatedAtMillis,
                    author = snapshotBook.author ?: cachedDetailBook?.author,
                    format = snapshotBook.format ?: cachedDetailBook?.format,
                    seriesId = snapshotBook.seriesId ?: cachedDetailBook?.seriesId,
                    seriesName = snapshotBook.seriesName ?: cachedDetailBook?.seriesName,
                    seriesIndex = snapshotBook.seriesIndex ?: cachedDetailBook?.seriesIndex
                ) ?: cachedDetailBook
                metadataBook?.copy(
                    fileId = metadataBook.fileId ?: record.fileId,
                    localPath = record.localPath,
                    downloadedSourceUpdatedAtMillis = record.sourceUpdatedAtMillis
                ) ?: BookSummary(
                    libraryId = "",
                    id = record.bookId,
                    fileId = record.fileId,
                    title = record.title,
                    format = record.mimeType,
                    mediaKind = record.mediaKind,
                    localPath = record.localPath,
                    downloadedSourceUpdatedAtMillis = record.sourceUpdatedAtMillis
                )
            }
    }

    override suspend fun loadSeriesCatalog(query: String?, page: Int): SeriesCatalogPage =
        loadSeriesCatalog(SeriesCatalogFilter(query = query), page)

    override suspend fun loadSeriesCatalog(
        filter: SeriesCatalogFilter,
        page: Int
    ): SeriesCatalogPage = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val serverBase = serverBase()
        val path = catalogPath(
            path = "/api/v1/series",
            query = filter.query,
            page = page,
            sort = filter.sort.serverField,
            order = filter.direction.serverValue,
            extra = buildList {
                filter.author?.trim()?.takeIf { it.isNotBlank() }?.let { add("author" to it) }
                filter.genre?.trim()?.takeIf { it.isNotBlank() }?.let { add("genre" to it) }
                filter.libraryId?.trim()?.takeIf { it.isNotBlank() }?.let { add("libraryId" to it) }
                filter.completion.serverValue?.let { add("completionStatus" to it) }
            }
        )
        try {
            val payload = request(path, "GET", null)
            catalogSnapshotStore.saveSeries(serverUrl, filter.query, page, payload)
            BookOrbitPayloadParser.parseSeriesCatalogPage(payload, serverBase)
        } catch (error: Throwable) {
            if (error is AuthenticationRequiredException) throw error
            if (!filter.isActive) catalogSnapshotStore.readSeries(serverUrl, filter.query, page)?.let { cached ->
                return@withContext BookOrbitPayloadParser.parseSeriesCatalogPage(cached, serverBase)
            }
            throw error
        }
    }

    override suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val serverBase = serverBase()
        val path = catalogPath("/api/v1/authors", query, page, "name")
        try {
            val payload = request(path, "GET", null)
            catalogSnapshotStore.saveAuthors(serverUrl, query, page, payload)
            BookOrbitPayloadParser.parseAuthorCatalogPage(payload, serverBase)
        } catch (error: Throwable) {
            if (error is AuthenticationRequiredException) throw error
            catalogSnapshotStore.readAuthors(serverUrl, query, page)?.let { cached ->
                return@withContext BookOrbitPayloadParser.parseAuthorCatalogPage(cached, serverBase)
            }
            throw error
        }
    }

    override suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val serverBase = serverBase()
        val encodedAuthorId = java.net.URLEncoder.encode(authorId, Charsets.UTF_8.name())
        val path = catalogPath("/api/v1/authors/$encodedAuthorId/books", null, page, "title")
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        try {
            val payload = request(path, "GET", null)
            catalogSnapshotStore.saveAuthorBooks(serverUrl, authorId, page, payload)
            BookOrbitPayloadParser.parseAuthorBooksPage(authorId, payload, downloads, serverBase)
        } catch (error: Throwable) {
            if (error is AuthenticationRequiredException) throw error
            catalogSnapshotStore.readAuthorBooks(serverUrl, authorId, page)?.let { cached ->
                return@withContext BookOrbitPayloadParser.parseAuthorBooksPage(
                    authorId,
                    cached,
                    downloads,
                    serverBase
                )
            }
            throw error
        }
    }

    override suspend fun searchBooks(query: String): List<BookSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val serverUrl = getServerUrl().orEmpty()
        val body = JSONObject().apply {
            put("q", query.trim())
            put("sort", JSONArray())
            put("pagination", JSONObject().apply {
                put("page", 0)
                put("size", 100)
            })
        }.toString().toRequestBody(JSON)
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        BookOrbitPayloadParser.parseBooks(
            libraryId = getSelectedLibraryId().orEmpty(),
            payload = request("/api/v1/books/query", "POST", body),
            downloads = downloads,
            serverBase = serverBase()
        )
    }

    override suspend fun loadBookCover(book: BookSummary): ByteArray? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val candidates = bookCoverCandidateUrls(book, serverUrl)
        var lastFailure: Throwable? = null
        candidates.forEach { url ->
            val cacheIdentity = coverCacheIdentity(url, book.updatedAtMillis)
            synchronized(coverCache) { coverCache[cacheIdentity] }?.let { return@withContext it }
            coverCacheStore.read(serverUrl, book.id, cacheIdentity)?.let { bytes ->
                if (bytes.isNotEmpty()) {
                    synchronized(coverCache) { coverCache[cacheIdentity] = bytes }
                    return@withContext bytes
                }
            }
            val bytes = try {
                requestBytes(url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastFailure = error
                return@forEach
            }
            if (bytes.isEmpty()) return@forEach
            coverCacheStore.save(serverUrl, book.id, cacheIdentity, bytes)
            synchronized(coverCache) {
                coverCache[cacheIdentity] = bytes
                while (coverCache.size > 32) {
                    coverCache.remove(coverCache.entries.first().key)
                }
            }
            return@withContext bytes
        }
        lastFailure?.let { throw it }
        null
    }

    internal suspend fun warmCoverCacheBatch(
        expectedServerUrl: String,
        libraryId: String,
        startIndex: Int,
        maxDownloads: Int
    ): Int? = withContext(Dispatchers.IO) {
        if (getServerUrl().orEmpty() != expectedServerUrl) return@withContext null
        val books = libraryCatalogStore.read(expectedServerUrl, libraryId)?.books
            ?: return@withContext null
        var index = startIndex.coerceIn(0, books.size)
        var downloaded = 0
        while (index < books.size && downloaded < maxDownloads.coerceAtLeast(1)) {
            if (getServerUrl().orEmpty() != expectedServerUrl) return@withContext null
            val book = books[index]
            val url = book.coverUrl?.let(::coverThumbnailUrl)
            if (url != null) {
                val cacheIdentity = coverCacheIdentity(url, book.updatedAtMillis)
                if (!coverCacheStore.contains(expectedServerUrl, book.id, cacheIdentity)) {
                    try {
                        val bytes = requestBytes(url)
                        if (bytes.isNotEmpty() && getServerUrl().orEmpty() == expectedServerUrl) {
                            coverCacheStore.save(expectedServerUrl, book.id, cacheIdentity, bytes)
                            downloaded += 1
                        }
                    } catch (error: HttpRequestException) {
                        if (error.code != 404) throw error
                    }
                }
            }
            index += 1
        }
        index.takeIf { it < books.size }
    }

    override suspend fun loadCatalogImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { requestBytes(url) }.getOrElse { error ->
            if (error is HttpRequestException && error.code == 404 && url.endsWith("/cover")) {
                requestBytes(url.removeSuffix("/cover") + "/thumbnail")
            } else {
                throw error
            }
        }
    }

    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        bookDetailCacheStore.read(serverUrl, book.id, book.fileId, book.updatedAtMillis)?.let { cached ->
            return@withContext cached.copy(
                book = cached.book.copy(
                    localPath = book.localPath ?: cached.book.localPath,
                    progressLabel = book.progressLabel ?: cached.book.progressLabel,
                    progressPercent = book.progressPercent ?: cached.book.progressPercent,
                    progressPositionMs = book.progressPositionMs ?: cached.book.progressPositionMs,
                    progressPageIndex = book.progressPageIndex ?: cached.book.progressPageIndex,
                    lastReadAtMillis = book.lastReadAtMillis ?: cached.book.lastReadAtMillis,
                    isRead = book.isRead
                )
            )
        }
        val detail = BookOrbitPayloadParser.parseBookDetail(
            fallback = book,
            payload = request("/api/v1/books/${book.id}", "GET", null),
            downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId },
            serverBase = serverBase()
        )
        bookDetailCacheStore.save(serverUrl, book.id, book.fileId, detail, book.updatedAtMillis)
        detail
    }

    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        val pages = loadCompleteSeriesPages { page ->
            BookOrbitPayloadParser.parseSeriesBooksPage(
                seriesId = seriesId,
                payload = request(
                    "/api/v1/series/$seriesId/books?page=$page&size=100&sort=seriesIndex&order=asc",
                    "GET",
                    null
                ),
                downloads = downloads,
                serverBase = serverBase()
            )
        }
        val libraries = BookOrbitPayloadParser.parseLibraries(
            request("/api/v1/libraries", "GET", null)
        )
        val libraryBooks = libraries.flatMap { library ->
            val encodedLibraryId = java.net.URLEncoder.encode(library.id, Charsets.UTF_8.name())
            loadCompleteSeriesPages { page ->
                BookOrbitPayloadParser.parseSeriesBooksPage(
                    seriesId = seriesId,
                    payload = request(
                        "/api/v1/series/$seriesId/books?page=$page&size=100&sort=seriesIndex&order=asc&libraryId=$encodedLibraryId",
                        "GET",
                        null
                    ),
                    downloads = downloads,
                    serverBase = serverBase(),
                    libraryId = library.id
                )
            }.flatMap { it.books }
        }
        val firstPage = requireNotNull(pages.firstOrNull())
        val responseTotal = pages.mapNotNull { it.total }.maxOrNull()
        val metadataBookCount = firstPage.seriesInfo.metadataBookCount
        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable && responseTotal != null && metadataBookCount != null && responseTotal != metadataBookCount) {
            Log.d(
                "BookOrbit",
                "Series $seriesId reports total=$responseTotal but seriesInfo.bookCount=$metadataBookCount"
            )
        }
        val completeDetail = firstPage.seriesInfo.copy(
            bookCount = responseTotal ?: firstPage.seriesInfo.bookCount,
            responseTotal = responseTotal ?: firstPage.seriesInfo.responseTotal,
            books = mergeSeriesBooksWithLibraryOwnership(
                unscopedBooks = mergeSeriesBooks(pages),
                libraryBooks = libraryBooks
            )
        )
        completeDetail.copy(
            firstBook = completeDetail.books.firstOrNull()?.let { first ->
                runCatching {
                    BookOrbitPayloadParser.parseBookDetail(
                        fallback = first,
                        payload = request("/api/v1/books/${first.id}", "GET", null),
                        downloads = downloads,
                        serverBase = serverBase()
                    )
                }.getOrNull()
            }
        )
    }

    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val snapshot = browserSnapshotStore.read(serverUrl) ?: return@withContext null
        val downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId }
        val selectedLibraryId = resolveSelectedLibraryId(
            preferredId = libraryId
                ?: getSelectedLibraryId()
                ?: snapshot.selectedLibraryId,
            libraries = snapshot.libraries
        )
        val cachedCatalog = selectedLibraryId?.let { libraryCatalogStore.read(serverUrl, it) }
        val books = cachedCatalog?.books
            ?: selectedLibraryId?.let { snapshot.booksByLibraryId[it] }
            .orEmpty()
        val homeBooks = libraryCatalogStore.readAllBooks(serverUrl)
            .ifEmpty { snapshot.booksByLibraryId.values.flatten() }
        BrowserState(
            serverUrl = serverUrl,
            libraries = snapshot.libraries,
            selectedLibraryId = selectedLibraryId,
            books = books.withCurrentDownloads(downloads),
            homeBooks = homeBooks.withCurrentDownloads(downloads),
            booksTotal = cachedCatalog?.total ?: books.size,
            booksSeriesTotal = cachedCatalog?.seriesTotal,
            booksPageSize = cachedCatalog?.pageSize,
            isCatalogComplete = cachedCatalog != null,
            catalogRefreshedAtMillis = cachedCatalog?.refreshedAtMillis,
            libraryJumpBuckets = cachedCatalog?.jumpBuckets.orEmpty(),
            debugPendingProgressCount = pendingProgressCount()
        )
    }

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val localFile = resolveReadableFile(book, allowRemoteCache = !localOnly)
        val streamUrl = buildReaderStreamUrl(
            fileId = book.fileId,
            serverBase = serverBase(),
            localOnly = localOnly
        )
        val comicPagesUrl = buildComicPagesUrl(
            fileId = book.fileId,
            serverBase = serverBase(),
            localOnly = localOnly,
            mediaKind = book.mediaKind
        )
        ensureReaderCanOpen(
            book = book,
            localFile = localFile,
            streamUrl = streamUrl,
            comicPagesUrl = comicPagesUrl
        )
        val restoredProgress = resolveRestoredReaderProgress(
            book = book,
            latestProgress = latestKnownProgress(serverUrl, book.id, book.fileId, book.mediaKind)
        )
        val epubPosition = if (book.mediaKind == MediaKind.EPUB) {
            epubReaderPositionStore.read(serverUrl, book.id, book.fileId)
        } else {
            null
        }
        ReaderState(
            book = if (localFile != null) book.copy(localPath = localFile.absolutePath) else book,
            localFile = localFile,
            streamUrl = streamUrl,
            comicPagesUrl = comicPagesUrl,
            lastKnownPosition = restoredProgress.positionMs,
            pageIndex = epubPosition?.chapterIndex ?: restoredProgress.pageIndex,
            readerPageIndex = epubPosition?.pageIndex ?: 0,
            progressPercent = restoredProgress.progressPercent
        )
    }

    override suspend fun saveActiveReader(book: BookSummary) = withContext(Dispatchers.IO) {
        activeReaderStore.save(
            serverUrl = getServerUrl().orEmpty(),
            book = book
        )
    }

    override suspend fun clearActiveReader() = withContext(Dispatchers.IO) {
        activeReaderStore.clear()
    }

    override suspend fun saveEpubReaderPosition(book: BookSummary) = withContext(Dispatchers.IO) {
        if (book.mediaKind != MediaKind.EPUB || book.readerPageIndex == null) return@withContext
        epubReaderPositionStore.save(
            EpubReaderPosition(
                serverUrl = getServerUrl().orEmpty(),
                bookId = book.id,
                fileId = book.fileId,
                chapterIndex = book.progressPageIndex ?: 0,
                pageIndex = book.readerPageIndex,
                pageCount = book.readerPageCount ?: 1,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun markBookAsRead(book: BookSummary) = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        if (serverUrl.isBlank()) throw AuthenticationRequiredException()

        // A queued progress update posts a status of "reading". Let any active replay
        // finish, send the explicit "read" status, then discard older updates before
        // releasing the lock so a background worker cannot undo the user's choice.
        progressSyncMutex.withLock {
            request(
                "/api/v1/books/${book.id}/status",
                "PATCH",
                buildMarkedReadStatusPayload().toString().toRequestBody(JSON)
            )
            queueStore.removeForBook(serverUrl, book.id)
        }

        val markedAtMillis = System.currentTimeMillis()
        bookDetailCacheStore.remove(serverUrl, book.id, book.fileId)
        libraryCatalogStore.markBookAsRead(serverUrl, book.id, markedAtMillis)
        browserSnapshotStore.markBookAsRead(serverUrl, book.id, markedAtMillis)
    }

    override suspend fun resetBookReadingState(book: BookSummary) = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        if (serverUrl.isBlank()) throw AuthenticationRequiredException()
        val progressResetRequest = buildProgressResetRequest(book)
            ?: throw UserFacingException("Unable to reset progress because this book has no valid file ID.")

        // A worker may already be replaying an older progress event. Serialize the clear
        // behind that replay, then remove every queued/acknowledged marker before another
        // worker can put the title back into Reading. These endpoints are available to
        // ordinary users; BookOrbit's full reset-reading-state endpoint is metadata-admin only.
        progressSyncMutex.withLock {
            request(
                progressResetRequest.path,
                progressResetRequest.method,
                progressResetRequest.payload?.toString()?.toRequestBody(JSON)
            )
            request(
                "/api/v1/books/${book.id}/status",
                "PATCH",
                buildUnreadStatusPayload().toString().toRequestBody(JSON)
            )
            queueStore.removeForBook(serverUrl, book.id)
            lastSyncedProgressStore.removeForBook(serverUrl, book.id)
        }

        epubReaderPositionStore.removeForBook(serverUrl, book.id)
        activeReaderStore.clearIfMatches(serverUrl, book.id)
        bookDetailCacheStore.remove(serverUrl, book.id, book.fileId)
        libraryCatalogStore.resetBookReadingState(serverUrl, book.id)
        browserSnapshotStore.resetBookReadingState(serverUrl, book.id)
    }

    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        if (serverUrl.isBlank()) {
            return@withContext null
        }
        val savedBook = activeReaderStore.read(serverUrl) ?: return@withContext null
        val localFile = resolveReadableFile(savedBook, allowRemoteCache = !localOnly)
        if (localOnly && localFile == null) {
            return@withContext null
        }
        val streamUrl = buildReaderStreamUrl(
            fileId = savedBook.fileId,
            serverBase = serverBase(),
            localOnly = localOnly
        )
        val comicPagesUrl = buildComicPagesUrl(
            fileId = savedBook.fileId,
            serverBase = serverBase(),
            localOnly = localOnly,
            mediaKind = savedBook.mediaKind
        )
        if (localOnly && savedBook.mediaKind == MediaKind.COMIC && localFile?.let(ReaderFileValidator::canRenderComicLocally) != true) {
            return@withContext null
        }
        runCatching {
            ensureReaderCanOpen(
                book = savedBook,
                localFile = localFile,
                streamUrl = streamUrl,
                comicPagesUrl = comicPagesUrl
            )
        }.getOrElse {
            return@withContext null
        }
        val restoredProgress = resolveRestoredReaderProgress(
            book = savedBook,
            latestProgress = latestKnownProgress(serverUrl, savedBook.id, savedBook.fileId, savedBook.mediaKind)
        )
        val epubPosition = if (savedBook.mediaKind == MediaKind.EPUB) {
            epubReaderPositionStore.read(serverUrl, savedBook.id, savedBook.fileId)
        } else {
            null
        }
        ReaderState(
            book = if (localFile != null) savedBook.copy(localPath = localFile.absolutePath) else savedBook,
            localFile = localFile,
            streamUrl = streamUrl,
            comicPagesUrl = comicPagesUrl,
            lastKnownPosition = restoredProgress.positionMs,
            pageIndex = epubPosition?.chapterIndex ?: restoredProgress.pageIndex,
            readerPageIndex = epubPosition?.pageIndex ?: savedBook.readerPageIndex ?: 0,
            progressPercent = restoredProgress.progressPercent
        )
    }

    override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: throw UserFacingException("This title is missing a downloadable file.")
        val serverUrl = getServerUrl().orEmpty()
        val existingRecord = downloadStore.find(serverUrl, fileId)
        val target = existingRecord
            ?.localPath
            ?.let(::File)
            ?: downloadStore.downloadTarget(fileId, book.title, book.mediaKind, book.format)
        val hadUsableExisting = existingRecord != null &&
            downloadedFilePassesIntegrity(book, target)
        val canReuseExisting = existingRecord != null &&
            !downloadUpdateAvailable(book, existingRecord) &&
            hadUsableExisting
        if (!canReuseExisting) {
            val parent = target.parentFile
            parent?.mkdirs()
            if (parent != null && !parent.exists()) {
                throw UserFacingException("Unable to prepare local storage for this download.")
            }
            val staged = File(parent, ".${target.name}.$fileId.part")
            if (staged.exists() && !staged.delete()) {
                throw UserFacingException("Unable to clear an interrupted download before retrying.")
            }
            val downloadContext = currentCoroutineContext()
            try {
                executeAuthenticated(
                    requestFactory = {
                        Request.Builder()
                            .url(buildDownloadUrl(fileId))
                            .get()
                            .addSessionAccessToken()
                            .build()
                    },
                    action = "download this title"
                ) { response ->
                    val body = response.body
                        ?: throw UserFacingException("The server returned an empty download.")
                    val input = body.byteStream()
                    val totalBytes = body.contentLength().takeIf { it > 0L }
                    onProgress(0f.takeIf { totalBytes != null })
                    try {
                        FileOutputStream(staged).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copiedBytes = 0L
                            var lastReportedPercent = -1
                            while (true) {
                                downloadContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copiedBytes += count
                                val percent = totalBytes?.let { total ->
                                    ((copiedBytes * 100L) / total).coerceIn(0L, 100L).toInt()
                                }
                                if (percent != null && percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    } catch (error: IOException) {
                        if (error.isLikelyLocalStorageFailure()) {
                            throw UserFacingException("Local storage could not save this download. Check free space and file access, then try again.")
                        }
                        throw error
                    }
                    ensureNonEmptyFile(
                        target = staged,
                        message = "The server returned an empty download."
                    )
                    if (!downloadedFilePassesIntegrity(book, staged)) {
                        val suffix = if (hadUsableExisting) " The previous local copy was kept." else ""
                        throw UserFacingException("The downloaded file did not pass integrity checks.$suffix")
                    }
                    onProgress(1f)
                }
                atomicReplaceDownloadedFile(staged, target)
            } finally {
                if (staged.exists()) staged.delete()
            }
        }
        downloadStore.save(
            DownloadRecord(
                serverUrl = getServerUrl().orEmpty(),
                fileId = fileId,
                bookId = book.id,
                title = book.title,
                localPath = target.absolutePath,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = listOfNotNull(
                    existingRecord?.sourceUpdatedAtMillis,
                    book.updatedAtMillis
                ).maxOrNull()
            )
        )
        libraryCatalogStore.updateLocalPath(serverUrl, book.id, target.absolutePath)
        browserSnapshotStore.updateLocalPath(serverUrl, book.id, target.absolutePath)
        val downloadedBook = book.copy(localPath = target.absolutePath)
        val detail = runCatching { loadBookDetail(downloadedBook) }
            .getOrElse { BookDetailInfo(downloadedBook) }
        bookDetailCacheStore.save(serverUrl, book.id, fileId, detail)
        target
    }

    override suspend fun deleteLocalCopy(book: BookSummary) = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: throw UserFacingException("This title does not have a removable local file.")
        val serverUrl = getServerUrl().orEmpty()
        val deleted = downloadStore.delete(serverUrl, fileId)
        if (!deleted) {
            throw UserFacingException("Unable to remove the local copy for this title.")
        }
        libraryCatalogStore.updateLocalPath(serverUrl, book.id, null)
        browserSnapshotStore.updateLocalPath(serverUrl, book.id, null)
        bookDetailCacheStore.remove(serverUrl, book.id, fileId)
    }

    override suspend fun loadStorageUsage(): StorageUsage = appStorageManager.usage()

    override suspend fun clearAppCache() = withContext(Dispatchers.IO) {
        synchronized(coverCache) { coverCache.clear() }
        coverCacheStore.clear()
        appStorageManager.clearDisposableCache()
    }

    override suspend fun reconfigureBackgroundRefresh() {
        CoverCacheWarmWorker.cancelAll(context)
        val serverUrl = getServerUrl().orEmpty()
        val libraryId = getSelectedLibraryId().orEmpty()
        if (serverUrl.isNotBlank() && libraryId.isNotBlank()) {
            CoverCacheWarmWorker.enqueue(context, serverUrl, libraryId)
        }
    }

    override suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?
    ) = withContext(Dispatchers.IO) {
        val update = ProgressUpdate(
            id = UUID.randomUUID().toString(),
            serverUrl = getServerUrl().orEmpty(),
            bookId = book.id,
            fileId = book.fileId,
            mediaKind = book.mediaKind,
            positionMs = position,
            pageIndex = pageIndex,
            progressPercent = normalizeStoredProgressPercent(progressPercent ?: book.progressPercent),
            updatedAtMillis = System.currentTimeMillis()
        )
        val lastSynced = lastSyncedProgressStore.read(update.progressKey())
        if (lastSynced != null && update.isStaleComparedTo(lastSynced)) {
            return@withContext
        }
        queueStore.enqueue(update)
        enqueueSyncWorker()
    }

    override suspend fun pendingProgressCount(): Int = withContext(Dispatchers.IO) {
        queueStore.countFor(getServerUrl().orEmpty())
    }

    override suspend fun syncPendingProgress(): SyncAttemptResult = withContext(Dispatchers.IO) {
        // The foreground coordinator and WorkManager can both ask for a replay. Serialize
        // submissions across repository instances, while still allowing the reader to enqueue
        // a newer update during the network request.
        progressSyncMutex.withLock {
            val pending = queueStore.readAll()
            if (pending.isEmpty()) {
                return@withLock SyncAttemptResult.Success
            }
            val currentServerUrl = getServerUrl().orEmpty()
            val acknowledgedIds = mutableSetOf<String>()
            val orderedPending = pending.sortedBy { it.updatedAtMillis }
            var authBlocked = false
            var transientFailure = false
            orderedPending.forEach { item ->
                if (authBlocked || !item.targetsServer(currentServerUrl)) {
                    return@forEach
                }
                runCatching { postProgress(item) }
                    .onSuccess { result ->
                        when (result) {
                            ProgressPostResult.ACCEPTED -> {
                                lastSyncedProgressStore.save(item)
                                acknowledgedIds += item.id
                            }
                            ProgressPostResult.ALREADY_SYNCED,
                            ProgressPostResult.INVALID -> acknowledgedIds += item.id
                            ProgressPostResult.DEFERRED -> Unit
                        }
                    }
                    .onFailure { error ->
                        if (error is AuthenticationRequiredException) {
                            authBlocked = true
                        } else {
                            transientFailure = true
                        }
                    }
                    .getOrNull()
            }
            // Remove only the posted snapshot IDs. A newer update for the same book/file may
            // have replaced one of them while the request was in flight and must remain queued.
            queueStore.acknowledge(acknowledgedIds)
            when {
                authBlocked -> SyncAttemptResult.AuthenticationBlocked
                transientFailure -> SyncAttemptResult.TransientFailure
                else -> SyncAttemptResult.Success
            }
        }
    }

    override suspend fun canReachServer(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        checkServer(serverUrl) == ServerCheckResult.Reachable
    }

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/")
                .get()
                .build()
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                if (response.isRedirect) {
                    ServerCheckResult.Redirected
                } else if (response.isSuccessful) {
                    ServerCheckResult.Reachable
                } else {
                    ServerCheckResult.HttpFailure
                }
            }
        }.getOrElse { error ->
            when (error) {
                is IllegalArgumentException -> ServerCheckResult.MalformedUrl
                is UnknownHostException -> ServerCheckResult.UnreachableHost
                is SocketTimeoutException -> ServerCheckResult.Timeout
                is SSLException -> ServerCheckResult.TlsFailure
                is IOException -> ServerCheckResult.NetworkFailure
                else -> ServerCheckResult.NetworkFailure
            }
        }
    }

    private suspend fun postProgress(item: ProgressUpdate): ProgressPostResult = withContext(Dispatchers.IO) {
        if (item.serverUrl.isBlank() || item.serverUrl != getServerUrl().orEmpty()) {
            return@withContext ProgressPostResult.DEFERRED
        }
        val lastSynced = lastSyncedProgressStore.read(item.progressKey())
        if (lastSynced != null && item.isStaleComparedTo(lastSynced)) {
            return@withContext ProgressPostResult.ALREADY_SYNCED
        }

        val path = if (item.mediaKind == MediaKind.AUDIO) {
            "/api/v1/books/${item.bookId}/audio-progress"
        } else {
            val fileId = item.fileId ?: return@withContext ProgressPostResult.INVALID
            "/api/v1/books/files/$fileId/progress"
        }

        val payload = buildProgressPayload(item) ?: return@withContext ProgressPostResult.INVALID
        val readStatusPayload = buildReadStatusPayload(item) ?: return@withContext ProgressPostResult.INVALID

        if (item.mediaKind == MediaKind.AUDIO) {
            request(path, "PATCH", payload.toString().toRequestBody(JSON))
        } else {
            val progressResult = postFileProgressWithRemap(item, payload)
            if (progressResult != ProgressPostResult.ACCEPTED) {
                return@withContext progressResult
            }
        }
        // BookOrbit's dashboard widget is status-backed, and some server versions can
        // accept file progress even when their automatic status update fails. Treat the
        // progress and status writes as one queued operation so a failed status write is
        // retried instead of silently leaving Currently Reading empty.
        request(
            "/api/v1/books/${item.bookId}/status",
            "PATCH",
            readStatusPayload.toString().toRequestBody(JSON)
        )
        ProgressPostResult.ACCEPTED
    }

    private fun postFileProgressWithRemap(
        item: ProgressUpdate,
        payload: JSONObject
    ): ProgressPostResult {
        val originalFileId = item.fileId ?: return ProgressPostResult.INVALID
        val body = payload.toString().toRequestBody(JSON)
        try {
            request("/api/v1/books/files/$originalFileId/progress", "POST", body)
            return ProgressPostResult.ACCEPTED
        } catch (error: HttpRequestException) {
            if (error.code != 404) throw error
        }

        val currentFileId = try {
            BookOrbitPayloadParser.parsePrimaryFileId(
                request("/api/v1/books/${item.bookId}", "GET", null)
            )
        } catch (error: HttpRequestException) {
            if (error.code == 404) return ProgressPostResult.INVALID
            throw error
        }
        if (currentFileId.isNullOrBlank() || currentFileId == originalFileId) {
            return ProgressPostResult.INVALID
        }

        return try {
            request(
                "/api/v1/books/files/$currentFileId/progress",
                "POST",
                payload.toString().toRequestBody(JSON)
            )
            ProgressPostResult.ACCEPTED
        } catch (error: HttpRequestException) {
            if (error.code == 404) ProgressPostResult.INVALID else throw error
        }
    }

    private fun enqueueSyncWorker() {
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS
            )
            // Debounce rapid page callbacks into one replay of the latest compacted update.
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bookorbit-progress-sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private suspend fun resolveReadableFile(book: BookSummary, allowRemoteCache: Boolean = true): File? {
        val direct = book.localPath
            ?.let(::File)
            ?.takeIf(File::exists)
            ?.takeIf { it.isReadableLocalReaderFile(book) }
        if (direct != null) {
            return direct
        }
        val fileId = book.fileId ?: return null
        val downloaded = downloadStore.find(getServerUrl().orEmpty(), fileId)
            ?.localPath
            ?.let(::File)
            ?.takeIf(File::exists)
            ?.takeIf { it.isReadableLocalReaderFile(book) }
        if (downloaded != null) {
            return downloaded
        }
        return if (shouldCacheReadableCopy(book, allowRemoteCache)) {
            cacheReadableCopy(book)
        } else {
            null
        }
    }

    private fun ensureReaderCanOpen(
        book: BookSummary,
        localFile: File?,
        streamUrl: String?,
        comicPagesUrl: String? = null
    ) {
        when (book.mediaKind) {
            MediaKind.AUDIO -> {
                if (localFile == null && streamUrl.isNullOrBlank()) {
                    throw UserFacingException("This audiobook is missing both a local file and a playable stream.")
                }
            }
            MediaKind.PDF -> {
                if (localFile == null) {
                    throw UserFacingException("This PDF could not be prepared for reading. Download it first or reconnect to the server.")
                }
            }
            MediaKind.EPUB -> {
                if (localFile == null) {
                    throw UserFacingException("This EPUB could not be prepared for reading. Download it first or reconnect to the server.")
                }
            }
            MediaKind.COMIC -> {
                val localComicReady = localFile?.let(ReaderFileValidator::canRenderComicLocally) == true
                if (!localComicReady && comicPagesUrl.isNullOrBlank()) {
                    throw UserFacingException("This comic could not be prepared for reading. Download it first or reconnect to the server.")
                }
            }
            MediaKind.UNKNOWN -> {
                if (localFile == null && streamUrl.isNullOrBlank()) {
                    throw UserFacingException("This title does not expose a readable local file or stream.")
                }
            }
        }
    }

    private suspend fun cacheReadableCopy(book: BookSummary): File? {
        val serverUrl = getServerUrl().orEmpty()
        val fileId = book.fileId ?: return null
        val extension = readerCacheExtension(book)
        val targetDir = File(context.cacheDir, "reader-cache").apply { mkdirs() }
        val target = File(targetDir, ReaderCacheKey.build(serverUrl, fileId, extension))
        if (target.exists() && target.length() > 0L && ReaderFileValidator.isReadable(book.mediaKind, target)) {
            return target
        }
        if (target.exists()) {
            target.delete()
        }

        val staged = File(targetDir, ".${target.name}.${UUID.randomUUID()}.part")
        return try {
            val fetched = executeAuthenticated(
                requestFactory = {
                    Request.Builder()
                        .url(buildDownloadUrl(fileId))
                        .get()
                        .addSessionAccessToken()
                        .build()
                },
                action = "prepare a reader file"
            ) { response ->
                val body = response.body ?: return@executeAuthenticated false
                val expectedLength = body.contentLength().takeIf { it >= 0L }
                val copiedLength = body.byteStream().use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                if (expectedLength != null && copiedLength != expectedLength) {
                    throw IOException("The reader file download ended before it was complete.")
                }
                true
            }
            if (!fetched) return null
            ensureNonEmptyFile(staged, "The server returned an empty reader file.")
            if (!ReaderFileValidator.isReadable(book.mediaKind, staged)) {
                throw UserFacingException("The reader file did not pass integrity checks.")
            }
            atomicReplaceDownloadedFile(staged, target)
            target
        } finally {
            if (staged.exists()) staged.delete()
        }
    }

    private fun buildStreamUrl(fileId: String): String = "${serverBase()}/api/v1/books/files/$fileId/serve"

    private fun buildDownloadUrl(fileId: String): String = "${serverBase()}/api/v1/books/files/$fileId/download"

    private suspend fun latestKnownProgress(
        serverUrl: String,
        bookId: String,
        fileId: String?,
        mediaKind: MediaKind
    ): ProgressUpdate? {
        val key = ProgressKey(
            serverUrl = serverUrl,
            bookId = bookId,
            fileId = fileId,
            mediaKind = mediaKind
        )
        return queueStore.latestFor(serverUrl, bookId, fileId, mediaKind)
            ?: lastSyncedProgressStore.read(key)
    }

    private fun serverBase(): String = runBlocking { getServerUrl() }.orEmpty()

    private fun catalogPath(
        path: String,
        query: String?,
        page: Int,
        sort: String,
        order: String = "asc",
        extra: List<Pair<String, String>> = emptyList()
    ): String {
        val encodedQuery = query?.trim()?.takeIf { it.isNotBlank() }
            ?.let { java.net.URLEncoder.encode(it, Charsets.UTF_8.name()) }
        return buildString {
            append(path)
            append("?page=")
            append(page.coerceAtLeast(0))
            append("&size=100&sort=")
            append(sort)
            append("&order=")
            append(order)
            if (encodedQuery != null) {
                append("&q=")
                append(encodedQuery)
            }
            extra.forEach { (key, value) ->
                append('&')
                append(key)
                append('=')
                append(java.net.URLEncoder.encode(value, Charsets.UTF_8.name()))
            }
        }
    }

    private fun request(path: String, method: String, body: RequestBody?): String {
        val base = serverBase().ifBlank { throw UserFacingException("No BookOrbit server is configured.") }
        return executeAuthenticated(
            requestFactory = {
                Request.Builder()
                    .url(base.trimEnd('/') + path)
                    .method(method, body)
                    .header("Accept", "application/json")
                    .addSessionAccessToken()
                    .build()
            },
            action = requestAction(path, method)
        ) { response ->
            response.body?.string().orEmpty()
        }
    }

    private suspend fun requestLogin(body: RequestBody) {
        val base = serverBase().ifBlank { throw UserFacingException("No BookOrbit server is configured.") }
        val request = Request.Builder()
            .url(base.trimEnd('/') + "/api/v1/auth/login")
            .post(body)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            when {
                response.code == 401 || response.code == 403 -> throw InvalidCredentialsException()
                response.code == 429 -> throw LoginRateLimitedException()
                !response.isSuccessful -> throw HttpRequestException(response.code, "sign in")
                else -> {
                    val accessToken = extractAccessToken(responseBody)
                    context.dataStore.edit { prefs ->
                        if (accessToken.isNullOrBlank()) {
                            prefs.remove(Keys.ACCESS_TOKEN)
                        } else {
                            prefs[Keys.ACCESS_TOKEN] = accessToken
                        }
                    }
                }
            }
        }
    }

    private suspend fun requestBytes(url: String): ByteArray {
        return executeAuthenticatedCancellable(
            requestFactory = {
                Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "image/*")
                    .addSessionAccessToken()
                    .build()
            },
            action = "load a book cover"
        ) { response ->
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun <T> executeAuthenticatedCancellable(
        requestFactory: () -> Request,
        action: String,
        onSuccess: (Response) -> T
    ): T {
        val tokenBeforeRequest = sessionAccessToken()
        var response = executeCancellable(requestFactory())
        if (response.code == 401 || response.code == 403) {
            response.close()
            if (refreshSession(tokenBeforeRequest)) {
                response = executeCancellable(requestFactory())
            }
        }
        response.use {
            if (it.code == 401 || it.code == 403) {
                throw AuthenticationRequiredException()
            }
            if (!it.isSuccessful) {
                throw HttpRequestException(code = it.code, action = action)
            }
            return onSuccess(it)
        }
    }

    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    private fun <T> executeAuthenticated(
        requestFactory: () -> Request,
        action: String,
        onSuccess: (Response) -> T
    ): T {
        val tokenBeforeRequest = sessionAccessToken()
        var response = client.newCall(requestFactory()).execute()
        if (response.code == 401 || response.code == 403) {
            response.close()
            if (refreshSession(tokenBeforeRequest)) {
                response = client.newCall(requestFactory()).execute()
            }
        }
        response.use {
            if (it.code == 401 || it.code == 403) {
                throw AuthenticationRequiredException()
            }
            if (!it.isSuccessful) {
                throw HttpRequestException(code = it.code, action = action)
            }
            return onSuccess(it)
        }
    }

    private fun refreshSession(expectedToken: String?): Boolean {
        return runCatching {
            synchronized(sessionRefreshLock) {
                val currentToken = sessionAccessToken()
                if (currentToken != expectedToken && !currentToken.isNullOrBlank()) {
                    // Another request completed the shared refresh while this request
                    // was waiting for the lock. Reuse its token for the retry.
                    return@synchronized true
                }
                val base = serverBase().ifBlank { return@synchronized false }
                val refreshPaths = listOf(
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/token/renew"
                )
                for (path in refreshPaths) {
                    val request = Request.Builder()
                        .url(base.trimEnd('/') + path)
                        .post("{}".toRequestBody(JSON))
                        .header("Accept", "application/json")
                        .addSessionAccessToken()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404 || response.code == 405) {
                            return@use
                        }
                        if (!response.isSuccessful) {
                            return@synchronized false
                        }
                        val accessToken = extractAccessToken(response.body?.string().orEmpty())
                        runBlocking {
                            context.dataStore.edit { prefs ->
                                if (accessToken.isNullOrBlank()) {
                                    prefs.remove(Keys.ACCESS_TOKEN)
                                } else {
                                    prefs[Keys.ACCESS_TOKEN] = accessToken
                                }
                            }
                        }
                        return@synchronized true
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    private fun requestAction(path: String, method: String): String {
        return when {
            path == "/api/v1/libraries" -> "load libraries"
            path.contains("/audio-progress") -> "sync listening progress"
            path.contains("/progress") && method == "DELETE" -> "clear reading progress"
            path.contains("/progress") -> "sync reading progress"
            path.endsWith("/status") && method == "PATCH" -> "sync reading status"
            path.contains("/books") && method == "POST" -> "load books"
            else -> "complete this request"
        }
    }

    private fun ensureNonEmptyFile(target: File, message: String) {
        if (target.length() > 0L) {
            return
        }
        target.delete()
        throw UserFacingException(message)
    }

    private suspend fun File.isReadableLocalReaderFile(book: BookSummary): Boolean {
        val readable = ReaderFileValidator.isReadable(book.mediaKind, this)
        if (!readable) {
            book.fileId?.let { fileId ->
                downloadStore.delete(getServerUrl().orEmpty(), fileId)
            }
        }
        return readable
    }

    private suspend fun clearCookies() = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val manager = CookieManager.getInstance()
            manager.removeSessionCookies(null)
            manager.removeAllCookies {
                manager.flush()
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun IOException.isLikelyLocalStorageFailure(): Boolean {
        return this is FileNotFoundException ||
            this is FileSystemException ||
            this is AccessDeniedException ||
            this is NoSuchFileException ||
            message.orEmpty().contains("no space", ignoreCase = true) ||
            message.orEmpty().contains("not enough space", ignoreCase = true) ||
            message.orEmpty().contains("enospc", ignoreCase = true)
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SELECTED_LIBRARY_ID = stringPreferencesKey("selected_library_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }

    private fun sessionAccessToken(): String? = runBlocking {
        context.dataStore.data.first()[Keys.ACCESS_TOKEN]
    }

    private fun Request.Builder.addSessionAccessToken(): Request.Builder {
        sessionAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { header("Authorization", "Bearer $it") }
        return this
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

enum class SyncAttemptResult {
    Success,
    AuthenticationBlocked,
    TransientFailure
}

enum class SessionState {
    Authenticated,
    Unauthenticated,
    Unavailable
}

enum class ServerCheckResult {
    Reachable,
    MalformedUrl,
    UnreachableHost,
    Timeout,
    TlsFailure,
    Redirected,
    HttpFailure,
    NetworkFailure
}

class AuthenticationRequiredException : IllegalStateException("Authentication required.")
class UserFacingException(message: String) : IllegalStateException(message)
class InvalidCredentialsException : IOException("Invalid credentials.")
class LoginRateLimitedException : IOException("Login rate limited.")
class LoginVerificationException : IOException("The server did not confirm the new session.")
class HttpRequestException(
    val code: Int,
    val action: String
) : IOException("HTTP $code while trying to $action.")

internal data class LibraryJumpBucketsResponse(
    val buckets: List<LibraryJumpBucket>,
    val total: Int
)

private class WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
}

internal object BookOrbitPayloadParser {
    fun parsePrimaryFileId(payload: String): String? {
        return extractObject(payload, "resolve the current book file")
            .optJSONArray("files")
            .selectPrimaryFile()
            ?.stringValue("id", "_id", "fileId")
    }

    fun parseAchievements(payload: String): AchievementCatalogue {
        val root = extractObject(payload, "load achievements")
        val categories = root.optJSONArray("categories") ?: JSONArray()
        val items = buildList {
            for (categoryIndex in 0 until categories.length()) {
                val category = categories.optJSONObject(categoryIndex) ?: continue
                val categoryKey = category.stringValue("key") ?: "other"
                val categoryLabel = category.stringValue("label") ?: categoryKey
                val achievements = category.optJSONArray("achievements") ?: continue
                for (achievementIndex in 0 until achievements.length()) {
                    val achievement = achievements.optJSONObject(achievementIndex) ?: continue
                    val key = achievement.stringValue("key") ?: continue
                    add(
                        AchievementItem(
                            key = key,
                            category = achievement.stringValue("category") ?: categoryKey,
                            categoryLabel = categoryLabel,
                            name = achievement.stringValue("name") ?: "Achievement",
                            description = achievement.stringValue("description").orEmpty(),
                            iconName = achievement.stringValue("iconName") ?: "award",
                            rarity = achievement.stringValue("rarity") ?: "common",
                            threshold = achievement.numberValue("threshold")?.toInt(),
                            hidden = achievement.booleanValue("hidden"),
                            earned = achievement.booleanValue("earned"),
                            awardedAt = achievement.stringValue("awardedAt"),
                            currentProgress = achievement.numberValue("currentProgress")?.toInt(),
                            sortOrder = achievement.numberValue("sortOrder")?.toInt() ?: 0
                        )
                    )
                }
            }
        }

        return AchievementCatalogue(
            items = items,
            totalEarned = root.numberValue("totalEarned")?.toInt()?.coerceAtLeast(0)
                ?: items.count { it.earned },
            totalAvailable = root.numberValue("totalAvailable")?.toInt()?.coerceAtLeast(0)
                ?: items.size
        )
    }

    fun parseLibraries(payload: String): List<LibrarySummary> {
        val array = extractArray(payload, "load libraries")
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(
                    LibrarySummary(
                        id = obj.stringValue("id", "_id", "libraryId")
                            ?: "library-$index",
                        name = obj.stringValue("name", "title", "label")
                            ?: "Library ${index + 1}",
                        description = obj.stringValue("description", "summary")
                    )
                )
            }
        }
    }

    fun parseBooks(
        libraryId: String,
        payload: String,
        downloads: Map<String, DownloadRecord>,
        serverBase: String
    ): List<BookSummary> {
        val array = extractArray(payload, "load books")
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val primaryFile = obj.optJSONArray("files").selectPrimaryFile()
                val fileId = primaryFile?.stringValue("id", "_id", "fileId")
                    ?: obj.stringValue("fileId", "file_id")
                    ?: obj.optJSONObject("file")?.stringValue("id", "_id")
                    ?: obj.optJSONObject("bookFile")?.stringValue("id", "_id")
                val format = primaryFile?.stringValue("format", "mimeType", "mime_type", "extension")
                    ?: obj.stringValue("format", "mimeType", "mime_type", "extension")
                val mediaKind = inferMediaKind(
                    format = format,
                    title = primaryFile?.stringValue("name", "title", "path", "filename")
                        ?: obj.stringValue("title", "name")
                )
                val bookId = obj.stringValue("id", "_id", "bookId") ?: "book-$index"
                val readingProgress = obj.optJSONObject("readingProgress")
                    ?: obj.optJSONObject("readProgress")
                    ?: obj.optJSONObject("userProgress")
                    ?: obj.optJSONObject("progress")
                val readStatus = obj.optJSONObject("readStatus")
                    ?: obj.optJSONObject("readingStatus")
                    ?: obj.optJSONObject("userBookStatus")
                val series = obj.optJSONObject("series")
                    ?: obj.optJSONArray("series")?.optJSONObject(0)
                val progressPercent = normalizeStoredProgressPercent(
                    readingProgress.progressPercent()
                        ?: obj.numberValue(
                            "readingProgress",
                            "readProgress",
                            "userProgress",
                            "progressPercent"
                        )?.toFloat()
                )
                val readStatusValue = readStatus?.stringValue("status", "state")
                    ?.trim()
                    ?.lowercase(Locale.US)
                val suppressUnreadReadingActivity = readStatusValue == "unread" &&
                    (progressPercent == null || progressPercent <= 0f)
                val progressLabel = if (suppressUnreadReadingActivity) {
                    null
                } else {
                    progressPercent
                        ?.takeIf { it > 0f }
                        ?.let { "${formatProgressValue(it)}%" }
                        ?: readingProgress.progressLabel()
                }
                val progressPositionMs = readingProgress.progressPositionMs()
                    .takeUnless { suppressUnreadReadingActivity }
                val progressPageIndex = readingProgress.progressPageIndex()
                    .takeUnless { suppressUnreadReadingActivity }
                val lastReadAtMillis = if (suppressUnreadReadingActivity) {
                    null
                } else {
                    readingProgress.timestampValue(
                        "updatedAt", "lastReadAt", "finishedAt", "completedAt"
                    ) ?: readStatus.timestampValue("finishedAt", "startedAt", "updatedAt")
                        ?: obj.timestampValue("lastReadAt", "lastReadDate")
                }
                add(
                    BookSummary(
                        libraryId = obj.stringValue("libraryId", "library_id") ?: libraryId,
                        id = bookId,
                        fileId = fileId,
                        title = obj.stringValue("title", "name", "displayName") ?: "Untitled",
                        author = obj.authorDisplayName(),
                        format = format,
                        mediaKind = mediaKind,
                        streamUrl = fileId?.let { "$serverBase/api/v1/books/files/$it/serve" },
                        downloadUrl = fileId?.let { "$serverBase/api/v1/books/files/$it/download" },
                        coverUrl = obj.resolveCoverUrl(serverBase = serverBase, bookId = bookId),
                        localPath = fileId?.let { downloads[it]?.localPath },
                        downloadedSourceUpdatedAtMillis = fileId?.let {
                            downloads[it]?.sourceUpdatedAtMillis
                        },
                        progressLabel = progressLabel,
                        progressPercent = progressPercent,
                        progressPositionMs = progressPositionMs,
                        progressPageIndex = progressPageIndex,
                        seriesId = series?.stringValue("id", "_id", "seriesId")
                            ?: obj.stringValue("seriesId", "series_id"),
                        seriesName = series?.stringValue("name", "title")
                            ?: obj.stringValue("seriesName", "series_name")
                            ?: obj.optString("series").takeIf { it.isNotBlank() },
                        seriesIndex = obj.numberValue("seriesIndex", "seriesNumber", "sequence")
                            ?: series?.numberValue("index", "number", "position"),
                        isRead = progressPercent?.let { it >= 99.5f } == true ||
                            readingProgress.booleanValue("completed", "isRead", "read") ||
                            readStatusValue == "read" ||
                            readStatusValue == "skimmed" ||
                            obj.booleanValue("isRead", "read"),
                        addedAtMillis = obj.timestampValue("createdAt", "addedAt", "dateAdded"),
                        updatedAtMillis = obj.timestampValue("updatedAt", "modifiedAt", "dateModified"),
                        lastReadAtMillis = lastReadAtMillis
                    )
                )
            }
        }
    }

    fun parseLibraryBooksPage(
        libraryId: String,
        payload: String,
        downloads: Map<String, DownloadRecord>,
        serverBase: String
    ): LibraryBooksPage {
        val root = extractObject(payload, "load books")
        return LibraryBooksPage(
            items = parseBooks(libraryId, payload, downloads, serverBase),
            total = root.numberValue("total")?.toInt()?.takeIf { it >= 0 },
            seriesTotal = root.numberValue("seriesTotal", "totalSeries", "seriesCount")?.toInt()?.takeIf { it >= 0 },
            page = root.numberValue("page")?.toInt(),
            size = root.numberValue("size")?.toInt()
        )
    }

    fun parseLibraryJumpBuckets(payload: String): LibraryJumpBucketsResponse {
        val root = extractObject(payload, "load library jump targets")
        val array = root.optJSONArray("buckets") ?: JSONArray()
        val buckets = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val key = item.stringValue("key") ?: continue
                val targetIndex = item.numberValue("index")?.toInt()?.takeIf { it >= 0 } ?: continue
                add(
                    LibraryJumpBucket(
                        key = key,
                        label = item.stringValue("label") ?: key,
                        index = targetIndex
                    )
                )
            }
        }
        return LibraryJumpBucketsResponse(
            buckets = buckets,
            total = root.numberValue("total")?.toInt()?.coerceAtLeast(0) ?: 0
        )
    }

    fun parseBookDetail(
        fallback: BookSummary,
        payload: String,
        downloads: Map<String, DownloadRecord>,
        serverBase: String
    ): BookDetailInfo {
        val obj = extractObject(payload, "load book details")
        val parsedBook = parseBooks(
            libraryId = fallback.libraryId,
            payload = JSONObject().put("items", JSONArray().put(obj)).toString(),
            downloads = downloads,
            serverBase = serverBase
        ).singleOrNull()
        val book = parsedBook?.copy(
            fileId = parsedBook.fileId ?: fallback.fileId,
            author = parsedBook.author ?: fallback.author,
            format = parsedBook.format ?: fallback.format,
            mediaKind = parsedBook.mediaKind.takeUnless { it == MediaKind.UNKNOWN } ?: fallback.mediaKind,
            streamUrl = parsedBook.streamUrl ?: fallback.streamUrl,
            downloadUrl = parsedBook.downloadUrl ?: fallback.downloadUrl,
            coverUrl = parsedBook.coverUrl ?: fallback.coverUrl,
            localPath = parsedBook.localPath ?: fallback.localPath,
            progressLabel = parsedBook.progressLabel ?: fallback.progressLabel,
            progressPercent = parsedBook.progressPercent ?: fallback.progressPercent,
            progressPositionMs = parsedBook.progressPositionMs ?: fallback.progressPositionMs,
            progressPageIndex = parsedBook.progressPageIndex ?: fallback.progressPageIndex,
            seriesId = parsedBook.seriesId ?: fallback.seriesId,
            seriesName = parsedBook.seriesName ?: fallback.seriesName,
            seriesIndex = parsedBook.seriesIndex ?: fallback.seriesIndex,
            isRead = parsedBook.isRead || fallback.isRead
        ) ?: fallback
        val files = obj.optJSONArray("files")
        val publishedDate = obj.stringValue("publishedDate", "publicationDate")
            ?: obj.numberValue("publishedYear", "publicationYear")?.toInt()?.toString()
        val audioMetadata = obj.optJSONObject("audioMetadata")
        val audioChapters = audioMetadata.audioChapters()
        return BookDetailInfo(
            book = book.copy(audioChapters = audioChapters),
            libraryName = obj.stringValue("libraryName"),
            subtitle = obj.stringValue("subtitle"),
            synopsis = obj.stringValue("description", "synopsis", "summary"),
            publisher = obj.stringValue("publisher"),
            publishedDate = publishedDate,
            language = obj.stringValue("language"),
            pageCount = obj.numberValue("pageCount", "pages")?.toInt(),
            isbn10 = obj.stringValue("isbn10"),
            isbn13 = obj.stringValue("isbn13", "isbn"),
            genres = obj.stringList("genres"),
            tags = obj.stringList("tags"),
            rating = obj.numberValue("rating"),
            narrators = obj.stringList("narrators"),
            fileCount = files?.length() ?: 0,
            totalSizeBytes = files.sumLong("sizeBytes", "size"),
            durationSeconds = files.maxLong("durationSeconds", "duration")
                ?: audioMetadata?.numberValue("durationSeconds", "duration")?.toLong(),
            audioChapters = audioChapters
        )
    }

    fun parseSeriesDetail(
        seriesId: String,
        payload: String,
        downloads: Map<String, DownloadRecord>,
        serverBase: String
    ): SeriesDetailInfo = parseSeriesBooksPage(
        seriesId = seriesId,
        payload = payload,
        downloads = downloads,
        serverBase = serverBase
    ).seriesInfo

    fun parseSeriesBooksPage(
        seriesId: String,
        payload: String,
        downloads: Map<String, DownloadRecord>,
        serverBase: String,
        libraryId: String = ""
    ): SeriesBooksPage {
        val root = extractObject(payload, "load series details")
        val books = parseBooks(
            libraryId = libraryId,
            payload = root.toString(),
            downloads = downloads,
            serverBase = serverBase
        ).sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
        val info = root.optJSONObject("seriesInfo") ?: JSONObject()
        val responseTotal = root.numberValue("total")?.toInt()?.takeIf { it >= 0 }
        val metadataBookCount = info.numberValue("bookCount")?.toInt()?.takeIf { it >= 0 }
        val seriesInfo = SeriesDetailInfo(
            id = info.stringValue("id") ?: seriesId,
            name = info.stringValue("name", "title") ?: books.firstOrNull()?.seriesName ?: "Series",
            bookCount = responseTotal ?: metadataBookCount ?: books.size,
            readCount = info.numberValue("readCount")?.toInt() ?: books.count { it.isRead },
            authors = info.stringList("authors"),
            possibleGaps = info.numberList("possibleGaps"),
            books = books,
            responseTotal = responseTotal,
            metadataBookCount = metadataBookCount
        )
        return SeriesBooksPage(
            books = books,
            total = responseTotal,
            page = root.numberValue("page")?.toInt(),
            size = root.numberValue("size")?.toInt(),
            seriesInfo = seriesInfo
        )
    }

    fun parseSeriesCatalogPage(
        payload: String,
        serverBase: String
    ): SeriesCatalogPage {
        val root = extractObject(payload, "load series")
        val array = root.catalogArray("items", "series", "results")
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val info = item.optJSONObject("seriesInfo") ?: item
                val id = info.stringValue("id", "_id", "seriesId") ?: "series-$index"
                val coverBookId = info.stringList("coverBookIds").firstOrNull()
                    ?: item.stringList("coverBookIds").firstOrNull()
                val representativeThumbnailPath = coverBookId?.let { "/api/v1/books/$it/thumbnail" }
                add(
                    SeriesSummary(
                        id = id,
                        name = info.stringValue("name", "title", "displayName") ?: "Series ${index + 1}",
                        authors = info.stringList("authors").ifEmpty {
                            info.authorDisplayName()?.split(",")?.map(String::trim).orEmpty()
                        },
                        bookCount = info.numberValue("bookCount", "totalBooks", "booksCount")?.toInt() ?: 0,
                        readCount = info.numberValue("readCount", "booksRead")?.toInt() ?: 0,
                        coverUrl = info.resolveCatalogImageUrl(
                            serverBase = serverBase,
                            fallbackPath = representativeThumbnailPath,
                            keys = arrayOf("coverUrl", "cover", "coverImage")
                        ) ?: representativeThumbnailPath?.let { "$serverBase$it" }
                    )
                )
            }
        }
        return SeriesCatalogPage(
            items = items,
            total = root.numberValue("total")?.toInt()?.takeIf { it >= 0 },
            page = root.numberValue("page")?.toInt(),
            size = root.numberValue("size")?.toInt()
        )
    }

    fun parseAuthorCatalogPage(
        payload: String,
        serverBase: String
    ): AuthorCatalogPage {
        val root = extractObject(payload, "load authors")
        val array = root.catalogArray("items", "authors", "results")
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.stringValue("id", "_id", "authorId") ?: "author-$index"
                add(
                    AuthorSummary(
                        id = id,
                        name = item.stringValue("name", "displayName", "fullName", "title") ?: "Author ${index + 1}",
                        bookCount = item.numberValue("bookCount", "booksCount", "totalBooks")?.toInt() ?: 0,
                        photoUrl = item.resolveCatalogImageUrl(
                            serverBase = serverBase,
                            fallbackPath = "/api/v1/authors/$id/photo",
                            keys = arrayOf("photoUrl", "photo", "avatarUrl", "avatar", "imageUrl")
                        )
                    )
                )
            }
        }
        return AuthorCatalogPage(
            items = items,
            total = root.numberValue("total")?.toInt()?.takeIf { it >= 0 },
            page = root.numberValue("page")?.toInt(),
            size = root.numberValue("size")?.toInt()
        )
    }

    fun parseAuthorBooksPage(
        authorId: String,
        payload: String,
        downloads: Map<String, DownloadRecord>,
        serverBase: String
    ): AuthorBooksPage {
        val root = extractObject(payload, "load author books")
        val authorObject = root.optJSONObject("author") ?: root.optJSONObject("authorInfo") ?: JSONObject()
        val author = AuthorSummary(
            id = authorObject.stringValue("id", "_id", "authorId") ?: authorId,
            name = authorObject.stringValue("name", "displayName", "fullName", "title") ?: "Author",
            bookCount = authorObject.numberValue("bookCount", "booksCount", "totalBooks")?.toInt()
                ?: root.numberValue("total")?.toInt()
                ?: 0,
            photoUrl = authorObject.resolveCatalogImageUrl(
                serverBase = serverBase,
                fallbackPath = "/api/v1/authors/$authorId/photo",
                keys = arrayOf("photoUrl", "photo", "avatarUrl", "avatar", "imageUrl")
            )
        )
        return AuthorBooksPage(
            author = author,
            items = parseBooks(
                libraryId = "",
                payload = root.toString(),
                downloads = downloads,
                serverBase = serverBase
            ),
            total = root.numberValue("total")?.toInt()?.takeIf { it >= 0 },
            page = root.numberValue("page")?.toInt(),
            size = root.numberValue("size")?.toInt()
        )
    }

    fun inferMediaKind(format: String?, title: String?): MediaKind {
        return readiumPublicationRoute(format, title).mediaKind()
    }

    private fun extractArray(payload: String, action: String): JSONArray {
        return runCatching {
            when (val root = JSONTokener(payload).nextValue()) {
                is JSONArray -> root
                is JSONObject -> root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("libraries")
                    ?: root.optJSONArray("books")
                    ?: root.optJSONArray("results")
                    ?: JSONArray()
                else -> JSONArray()
            }
        }.getOrElse {
            throw UserFacingException("The server returned malformed data while trying to $action.")
        }
    }

    private fun extractObject(payload: String, action: String): JSONObject {
        return runCatching {
            when (val root = JSONTokener(payload).nextValue()) {
                is JSONObject -> root.optJSONObject("data") ?: root
                else -> JSONObject()
            }
        }.getOrElse {
            throw UserFacingException("The server returned malformed data while trying to $action.")
        }
    }

    private fun JSONObject.catalogArray(vararg keys: String): JSONArray {
        return keys.asSequence()
            .mapNotNull { key -> optJSONArray(key) }
            .firstOrNull()
            ?: JSONArray()
    }

    private fun JSONObject?.progressLabel(): String? {
        this ?: return null
        progressPercent()?.let { return "${formatProgressValue(normalizeProgressValue(it))}%" }
        progressPageIndex()?.let { return "Page ${it + 1}" }
        progressPositionMs()?.let { return formatDurationLabel(it) }
        opt("currentFileId")?.toString()?.takeIf { it.isNotBlank() }?.let { return "File $it" }
        return null
    }

    private fun JSONObject?.progressPercent(): Float? {
        this ?: return null
        val keys = listOf("percentage", "percent", "progressPercent", "progress", "completion")
        return keys.firstNotNullOfOrNull { key ->
            when (val value = opt(key)) {
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull()
                else -> null
            }
        }
    }

    private fun JSONObject?.progressPositionMs(): Long? {
        this ?: return null
        val seconds = when (val value = opt("positionSeconds")) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: return null
        return (seconds * 1000.0).toLong().coerceAtLeast(0L)
    }

    private fun JSONObject?.progressPageIndex(): Int? {
        this ?: return null
        val pageNumber = when (val value = opt("pageNumber")) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: return null
        return (pageNumber - 1).coerceAtLeast(0)
    }

    private fun JSONObject.stringValue(vararg keys: String): String? {
        keys.forEach { key ->
            when (val value = opt(key)) {
                is String -> if (value.isNotBlank()) return value
                is Number -> return value.toString()
                is JSONObject -> {
                    val nested = value.stringValue("id", "_id", "value", "name", "title")
                    if (!nested.isNullOrBlank()) {
                        return nested
                    }
                }
            }
        }
        return null
    }

    private fun JSONObject?.booleanValue(vararg keys: String): Boolean {
        this ?: return false
        keys.forEach { key ->
            when (val value = opt(key)) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
                is String -> {
                    when (value.trim().lowercase(Locale.US)) {
                        "true", "1", "yes" -> return true
                        "false", "0", "no" -> return false
                    }
                }
            }
        }
        return false
    }

    private fun JSONObject.numberValue(vararg keys: String): Double? {
        keys.forEach { key ->
            when (val value = opt(key)) {
                is Number -> return value.toDouble()
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                when (val value = array.opt(index)) {
                    is String -> value.takeIf { it.isNotBlank() }?.let(::add)
                    is Number -> add(value.toString())
                    is JSONObject -> value.stringValue("name", "title", "value", "label")?.let(::add)
                }
            }
        }.distinct()
    }

    private fun JSONObject?.audioChapters(): List<AudiobookChapter> {
        val array = this?.optJSONArray("chapters") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val chapter = array.optJSONObject(index) ?: continue
                val startMs = chapter.numberValue("startMs")?.toLong()
                    ?: chapter.numberValue("start")?.times(1_000.0)?.toLong()
                    ?: continue
                add(
                    AudiobookChapter(
                        title = chapter.stringValue("title", "name") ?: "Chapter ${index + 1}",
                        startMs = startMs.coerceAtLeast(0L)
                    )
                )
            }
        }.sortedBy(AudiobookChapter::startMs)
    }

    private fun JSONObject.numberList(key: String): List<Double> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                when (val value = array.opt(index)) {
                    is Number -> add(value.toDouble())
                    is String -> value.toDoubleOrNull()?.let(::add)
                }
            }
        }
    }

    private fun JSONArray?.sumLong(vararg keys: String): Long? {
        this ?: return null
        var found = false
        var total = 0L
        for (index in 0 until length()) {
            val value = optJSONObject(index)?.numberValue(*keys)?.toLong() ?: continue
            found = true
            total += value
        }
        return total.takeIf { found }
    }

    private fun JSONArray?.maxLong(vararg keys: String): Long? {
        this ?: return null
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.numberValue(*keys)?.toLong()
        }.maxOrNull()
    }

    private fun JSONObject?.timestampValue(vararg keys: String): Long? {
        this ?: return null
        keys.forEach { key ->
            val parsed = when (val value = opt(key)) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                    ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                else -> null
            }
            if (parsed != null) {
                return if (parsed in 1..9_999_999_999L) parsed * 1000L else parsed
            }
        }
        return null
    }

    private fun JSONObject.authorDisplayName(): String? {
        optJSONArray("authors")?.let { authors ->
            val values = buildList {
                for (authorIndex in 0 until authors.length()) {
                    when (val author = authors.opt(authorIndex)) {
                        is String -> author.takeIf { it.isNotBlank() }?.let(::add)
                        is Number -> add(author.toString())
                        is JSONObject -> author.stringValue("name", "author", "displayName", "fullName", "title")
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }
            if (values.isNotEmpty()) {
                return values.joinToString(", ")
            }
        }
        return stringValue("author", "authorName", "creator")
    }

    private fun JSONObject.resolveCoverUrl(serverBase: String, bookId: String): String? {
        val explicit = stringValue("coverUrl", "cover", "coverImage")
            ?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("/")
            }
            ?: optJSONObject("cover")?.stringValue("url", "href", "path")
            ?: optJSONObject("coverImage")?.stringValue("url", "href", "path")
        if (!explicit.isNullOrBlank()) {
            if (explicit.startsWith("http://", ignoreCase = true) || explicit.startsWith("https://", ignoreCase = true)) {
                return explicit
            }
            if (explicit.startsWith("/")) {
                return "$serverBase$explicit"
            }
            return "$serverBase/${explicit.trimStart('/')}"
        }

        val hasResolvableCover = booleanValue("hasCover", "has_cover") ||
            stringValue("coverSource", "cover_source") != null ||
            optJSONObject("cover") != null ||
            optJSONObject("coverImage") != null ||
            opt("coverFileId") != null ||
            opt("cover_file_id") != null
        return if (hasResolvableCover) {
            "$serverBase/api/v1/books/$bookId/cover"
        } else {
            null
        }
    }

    private fun JSONObject.resolveCatalogImageUrl(
        serverBase: String,
        fallbackPath: String?,
        keys: Array<String>
    ): String? {
        val explicit = keys.asSequence()
            .mapNotNull { key ->
                when (val value = opt(key)) {
                    is String -> value.takeIf { it.isNotBlank() }
                    is JSONObject -> value.stringValue("url", "href", "path")
                    else -> null
                }
            }
            .firstOrNull()
        if (!explicit.isNullOrBlank()) {
            return if (explicit.startsWith("http://", ignoreCase = true) || explicit.startsWith("https://", ignoreCase = true)) {
                explicit
            } else {
                "$serverBase/${explicit.trimStart('/')}"
            }
        }
        val hasImageMetadata = keys.any { key -> has(key) && !isNull(key) }
        return if (hasImageMetadata && !fallbackPath.isNullOrBlank()) "$serverBase$fallbackPath" else null
    }

    private fun JSONArray?.selectPrimaryFile(): JSONObject? {
        this ?: return null
        for (index in 0 until length()) {
            val candidate = optJSONObject(index) ?: continue
            if (candidate.optString("role").equals("primary", ignoreCase = true)) {
                return candidate
            }
        }
        return (0 until length())
            .asSequence()
            .mapNotNull { index -> optJSONObject(index) }
            .filter { it.stringValue("id", "_id", "fileId") != null }
            .maxByOrNull { candidate ->
                val mediaKind = inferMediaKind(
                    format = candidate.stringValue("format", "mimeType", "mime_type", "extension"),
                    title = candidate.stringValue("name", "title", "path", "filename")
                )
                when (mediaKind) {
                    MediaKind.EPUB -> 4
                    MediaKind.PDF -> 3
                    MediaKind.AUDIO -> 2
                    MediaKind.COMIC -> 1
                    MediaKind.UNKNOWN -> 0
                }
            }
    }

    private fun normalizeProgressValue(value: Float): Float {
        return value.coerceIn(0f, 100f)
    }

    private fun formatProgressValue(value: Float): String {
        return DecimalFormat("0.##").format(value.coerceIn(0f, 100f).toDouble())
    }

    private fun formatDurationLabel(positionMs: Long): String {
        val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}

internal fun normalizeStoredServerUrl(serverUrl: String): String {
    return serverUrl.trim().trimEnd('/')
}

internal suspend fun loadCompleteSeriesPages(
    loadPage: suspend (Int) -> SeriesBooksPage
): List<SeriesBooksPage> {
    val pages = mutableListOf<SeriesBooksPage>()
    val accumulatedIds = linkedSetOf<String>()
    var targetTotal: Int? = null
    var fallbackTotal: Int? = null
    var pageNumber = 0

    while (true) {
        val page = loadPage(pageNumber)
        pages += page
        targetTotal = targetTotal ?: page.total?.takeIf { it >= 0 }
        fallbackTotal = fallbackTotal ?: page.seriesInfo.metadataBookCount ?: page.seriesInfo.bookCount

        val idsBeforePage = accumulatedIds.size
        page.books.forEach { book -> accumulatedIds += book.id }
        val addedIds = accumulatedIds.size - idsBeforePage

        if (page.books.isEmpty() || addedIds == 0) {
            break
        }

        val expectedTotal = targetTotal ?: fallbackTotal
        if (accumulatedIds.size >= expectedTotal) {
            break
        }
        pageNumber += 1
    }

    return pages
}

internal suspend fun loadCompleteSeriesCatalog(
    loadPage: suspend (Int) -> SeriesCatalogPage
): SeriesCatalogPage {
    val accumulated = linkedMapOf<String, SeriesSummary>()
    var targetTotal: Int? = null
    var firstPageSize: Int? = null
    var pageNumber = 0

    while (true) {
        val page = loadPage(pageNumber)
        targetTotal = listOfNotNull(targetTotal, page.total?.takeIf { it >= 0 }).maxOrNull()
        firstPageSize = firstPageSize ?: page.size?.takeIf { it > 0 }

        val countBeforePage = accumulated.size
        page.items.forEach { series -> accumulated.putIfAbsent(series.id, series) }
        val addedItems = accumulated.size - countBeforePage

        if (page.items.isEmpty() || addedItems == 0) break
        if (targetTotal != null && accumulated.size >= targetTotal) break
        if (targetTotal == null && firstPageSize != null && page.items.size < firstPageSize) break
        pageNumber += 1
    }

    return SeriesCatalogPage(
        items = accumulated.values.toList(),
        total = targetTotal ?: accumulated.size,
        page = 0,
        size = firstPageSize
    )
}

internal suspend fun loadCompleteLibraryPages(
    firstPage: LibraryBooksPage? = null,
    loadPage: suspend (Int) -> LibraryBooksPage
): List<LibraryBooksPage> {
    val initialPage = firstPage ?: loadPage(0)
    val initialPageSize = initialPage.size?.takeIf { it > 0 } ?: LIBRARY_PAGE_SIZE
    val initialTotal = initialPage.total?.takeIf { it >= 0 }
    val expectedPageCount = initialTotal?.let { total ->
        if (total == 0) 1 else ((total - 1) / initialPageSize) + 1
    }
    if (
        expectedPageCount != null &&
        expectedPageCount >= LIBRARY_PARALLEL_PAGE_THRESHOLD &&
        initialPage.items.isNotEmpty()
    ) {
        val remainingPages = (1 until expectedPageCount)
            .chunked(LIBRARY_MAX_CONCURRENT_PAGE_REQUESTS)
            .flatMap { pageBatch ->
                coroutineScope {
                    pageBatch
                        .map { pageNumber -> async { pageNumber to loadPage(pageNumber) } }
                        .awaitAll()
                        .sortedBy { (pageNumber, _) -> pageNumber }
                        .map { (_, page) -> page }
                }
            }
        return listOf(initialPage) + remainingPages
    }

    val pages = mutableListOf(initialPage)
    val accumulatedIds = linkedSetOf<String>()
    initialPage.items.forEach { book -> accumulatedIds += book.id }
    var targetTotal: Int? = initialTotal
    var pageNumber = 1

    if (
        initialPage.items.isEmpty() ||
        targetTotal?.let { accumulatedIds.size >= it } == true ||
        initialPage.items.size < initialPageSize
    ) {
        return pages
    }

    while (true) {
        val page = loadPage(pageNumber)
        pages += page
        targetTotal = listOfNotNull(targetTotal, page.total?.takeIf { it >= 0 }).maxOrNull()

        val idsBeforePage = accumulatedIds.size
        page.items.forEach { book -> accumulatedIds += book.id }
        val addedIds = accumulatedIds.size - idsBeforePage
        val pageSize = page.size?.takeIf { it > 0 } ?: LIBRARY_PAGE_SIZE

        if (
            page.items.isEmpty() ||
            addedIds == 0 ||
            targetTotal?.let { accumulatedIds.size >= it } == true ||
            page.items.size < pageSize
        ) {
            break
        }
        pageNumber += 1
    }

    return pages
}

internal fun mergeLibraryBooks(pages: List<LibraryBooksPage>): List<BookSummary> {
    val seenIds = mutableSetOf<String>()
    return pages.flatMap { page -> page.items }.filter { book -> seenIds.add(book.id) }
}

internal fun libraryCatalogPagesAreStable(
    pages: List<LibraryBooksPage>,
    mergedBooks: List<BookSummary>
): Boolean {
    val totals = pages.mapNotNull { it.total }
    return totals.distinct().size <= 1 && totals.lastOrNull()?.let { it == mergedBooks.size } != false
}

private fun List<BookSummary>.withCurrentDownloads(
    downloads: Map<String, DownloadRecord>
): List<BookSummary> = map { book ->
    val download = book.fileId?.let(downloads::get)
    val localPath = download?.localPath
    if (
        localPath == book.localPath &&
        download?.sourceUpdatedAtMillis == book.downloadedSourceUpdatedAtMillis
    ) {
        book
    } else {
        book.copy(
            localPath = localPath,
            downloadedSourceUpdatedAtMillis = download?.sourceUpdatedAtMillis
        )
    }
}

internal fun downloadUpdateAvailable(book: BookSummary, record: DownloadRecord): Boolean {
    val currentSourceVersion = book.updatedAtMillis ?: return false
    val downloadedSourceVersion = record.sourceUpdatedAtMillis
    return downloadedSourceVersion == null || currentSourceVersion > downloadedSourceVersion
}

internal fun downloadedFilePassesIntegrity(book: BookSummary, file: File): Boolean {
    if (!file.exists() || file.length() <= 0L) return false
    return ReaderFileValidator.isReadable(book.mediaKind, file)
}

internal fun atomicReplaceDownloadedFile(staged: File, target: File) {
    require(staged.exists()) { "Staged download does not exist." }
    target.parentFile?.mkdirs()
    try {
        Files.move(
            staged.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun mergeSeriesBooks(pages: List<SeriesBooksPage>): List<BookSummary> {
    return pages
        .flatMap { it.books }
        .distinctBy { it.id }
        .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
}

internal fun mergeSeriesBooksWithLibraryOwnership(
    unscopedBooks: List<BookSummary>,
    libraryBooks: List<BookSummary>
): List<BookSummary> {
    val libraryIdByBookId = libraryBooks.associate { it.id to it.libraryId }
    return unscopedBooks.map { book ->
        libraryIdByBookId[book.id]?.let { libraryId -> book.copy(libraryId = libraryId) } ?: book
    }
}

internal fun coverThumbnailUrl(coverUrl: String): String {
    return if (coverUrl.matches(Regex(".*/api/v1/books/[^/]+/cover$"))) {
        coverUrl.removeSuffix("/cover") + "/thumbnail"
    } else {
        coverUrl
    }
}

internal fun bookCoverCandidateUrls(book: BookSummary, serverBase: String): List<String> {
    return buildList {
        book.coverUrl
            ?.takeIf { it.isNotBlank() }
            ?.let(::coverThumbnailUrl)
            ?.let(::add)
        serverBase
            .trim()
            .trimEnd('/')
            .takeIf { it.isNotBlank() }
            ?.let { add("$it/api/v1/books/${book.id}/thumbnail") }
    }.distinct()
}

internal fun coverCacheIdentity(url: String, updatedAtMillis: Long?): String {
    return updatedAtMillis?.let { "$url#updated=$it" } ?: url
}

internal fun resolveSelectedLibraryId(
    preferredId: String?,
    libraries: List<LibrarySummary>
): String? {
    val validPreferred = preferredId?.takeIf { candidate ->
        libraries.any { it.id == candidate }
    }
    return validPreferred ?: libraries.firstOrNull()?.id
}

internal fun normalizeStoredProgressPercent(value: Float?): Float? {
    value ?: return null
    return value.coerceIn(0f, 100f)
}

internal fun ProgressUpdate.isStaleComparedTo(other: ProgressUpdate): Boolean {
    return sameProgressAs(other)
}

private fun ProgressUpdate.sameProgressAs(other: ProgressUpdate): Boolean {
    return pageIndex == other.pageIndex &&
        positionMs == other.positionMs &&
        normalizedProgressPercent() == other.normalizedProgressPercent()
}

internal fun ProgressUpdate.normalizedProgressPercent(): Float {
    return normalizeStoredProgressPercent(progressPercent) ?: 0f
}

internal fun buildProgressPayload(item: ProgressUpdate): JSONObject? {
    val percentage = normalizeStoredProgressPercent(item.progressPercent) ?: return null
    return JSONObject().apply {
        put("percentage", percentage.toDouble())
        if (item.mediaKind == MediaKind.AUDIO) {
            put("currentFileId", item.fileId?.toIntOrNull() ?: return null)
            put("positionSeconds", item.positionMs / 1000.0)
        } else {
            if (item.pageIndex > 0) {
                put("pageNumber", item.pageIndex + 1)
            }
            if (item.positionMs > 0L) {
                put("positionSeconds", item.positionMs / 1000.0)
            }
        }
    }
}

internal fun buildReadStatusPayload(item: ProgressUpdate): JSONObject? {
    val percentage = normalizeStoredProgressPercent(item.progressPercent) ?: return null
    return JSONObject().apply {
        put("status", if (percentage >= COMPLETED_PROGRESS_PERCENT) "read" else "reading")
    }
}

internal data class ProgressResetRequest(
    val path: String,
    val method: String,
    val payload: JSONObject? = null
)

internal fun buildProgressResetRequest(book: BookSummary): ProgressResetRequest? {
    val fileId = book.fileId ?: return null
    if (book.mediaKind != MediaKind.AUDIO) {
        return ProgressResetRequest(
            path = "/api/v1/books/files/$fileId/progress",
            method = "DELETE"
        )
    }

    val numericFileId = fileId.toIntOrNull() ?: return null
    return ProgressResetRequest(
        path = "/api/v1/books/${book.id}/audio-progress",
        method = "PATCH",
        payload = JSONObject().apply {
            put("percentage", 0.0)
            put("currentFileId", numericFileId)
            put("positionSeconds", 0.0)
        }
    )
}

internal fun buildUnreadStatusPayload(): JSONObject = JSONObject().apply {
    put("status", "unread")
    put("startedAt", JSONObject.NULL)
    put("finishedAt", JSONObject.NULL)
}

internal fun buildMarkedReadStatusPayload(): JSONObject = JSONObject().apply {
    put("status", "read")
}

internal fun ProgressUpdate.targetsServer(currentServerUrl: String): Boolean {
    return serverUrl.isNotBlank() && serverUrl == currentServerUrl
}

internal fun buildReaderStreamUrl(
    fileId: String?,
    serverBase: String,
    localOnly: Boolean
): String? {
    if (localOnly || fileId.isNullOrBlank()) {
        return null
    }
    return "${serverBase.trimEnd('/')}/api/v1/books/files/$fileId/serve"
}

internal fun buildComicPagesUrl(
    fileId: String?,
    serverBase: String,
    localOnly: Boolean,
    mediaKind: MediaKind
): String? {
    if (localOnly || mediaKind != MediaKind.COMIC || fileId.isNullOrBlank()) {
        return null
    }
    return "${serverBase.trimEnd('/')}/api/v1/cbz/files/$fileId/pages"
}

private fun BookSummary.hasZipComicHint(): Boolean = comicArchiveExtension() == "cbz"

internal fun shouldCacheReadableCopy(book: BookSummary, allowRemoteCache: Boolean): Boolean {
    if (!allowRemoteCache || book.fileId.isNullOrBlank()) {
        return false
    }
    return when (book.mediaKind) {
        MediaKind.EPUB,
        MediaKind.PDF,
        MediaKind.AUDIO -> true
        MediaKind.COMIC -> book.hasZipComicHint()
        MediaKind.UNKNOWN -> false
    }
}

internal fun readerCacheExtension(book: BookSummary): String = when (book.mediaKind) {
    MediaKind.EPUB -> "epub"
    MediaKind.PDF -> "pdf"
    MediaKind.AUDIO -> "audio-v2.${audiobookFileExtension(book) ?: "bin"}"
    MediaKind.COMIC -> book.comicArchiveExtension() ?: "cbz"
    MediaKind.UNKNOWN -> "bin"
}

private fun audiobookFileExtension(book: BookSummary): String? {
    val suffixes = listOfNotNull(book.format, book.title).map { value ->
        value.trim().lowercase(Locale.US).substringAfterLast('/').substringAfterLast('.')
    }
    return suffixes.firstNotNullOfOrNull { suffix ->
        when (suffix) {
            "m4a", "m4b", "mp4", "aac", "aif", "aiff", "flac", "ogg", "oga", "opus", "wav", "webm" -> suffix
            "mp3", "mpeg" -> "mp3"
            "x-m4b" -> "m4b"
            else -> null
        }
    }
}

internal fun BookSummary.comicArchiveExtension(): String? {
    val token = listOfNotNull(format, title).joinToString(" ").lowercase(Locale.US)
    return when {
        token.contains("cb7") || token.contains("7z") -> "cb7"
        token.contains("cbr") || token.contains("rar") -> "cbr"
        token.contains("cbz") || token.contains("zip") -> "cbz"
        else -> null
    }
}

internal fun resolveRestoredReaderProgress(
    book: BookSummary,
    latestProgress: ProgressUpdate?
): ReaderProgressState {
    val savedProgress = ReaderProgressState(
        positionMs = book.progressPositionMs ?: 0L,
        pageIndex = book.progressPageIndex ?: 0,
        progressPercent = normalizeStoredProgressPercent(book.progressPercent)
    )
    val queuedOrSyncedProgress = latestProgress?.let {
        ReaderProgressState(
            positionMs = it.positionMs,
            pageIndex = it.pageIndex,
            progressPercent = normalizeStoredProgressPercent(it.progressPercent)
        )
    }
    return when {
        queuedOrSyncedProgress == null -> savedProgress
        queuedOrSyncedProgress.isAheadOf(savedProgress) -> queuedOrSyncedProgress
        else -> savedProgress
    }
}

internal data class ReaderProgressState(
    val positionMs: Long,
    val pageIndex: Int,
    val progressPercent: Float?
)

internal fun ReaderProgressState.isAheadOf(other: ReaderProgressState): Boolean {
    if (pageIndex != other.pageIndex) {
        return pageIndex > other.pageIndex
    }
    if (positionMs != other.positionMs) {
        return positionMs > other.positionMs
    }
    return normalizedProgressPercent() > other.normalizedProgressPercent()
}

private fun ReaderProgressState.normalizedProgressPercent(): Float {
    return normalizeStoredProgressPercent(progressPercent) ?: 0f
}
