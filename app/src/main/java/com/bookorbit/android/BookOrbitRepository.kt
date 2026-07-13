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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
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
import javax.net.ssl.SSLException

private val Context.dataStore by preferencesDataStore(name = "bookorbit_prefs")
private const val LIBRARY_PAGE_SIZE = 50

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
    suspend fun loadLocalBooks(): List<BookSummary> = emptyList()
    suspend fun loadSeriesCatalog(query: String? = null, page: Int = 0): SeriesCatalogPage = SeriesCatalogPage()
    suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int = 0): SeriesCatalogPage =
        loadSeriesCatalog(filter.query, page)
    suspend fun loadAuthorsCatalog(query: String? = null, page: Int = 0): AuthorCatalogPage = AuthorCatalogPage()
    suspend fun loadAuthorBooks(authorId: String, page: Int = 0): AuthorBooksPage? = null
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
    suspend fun downloadBook(book: BookSummary): File
    suspend fun deleteLocalCopy(book: BookSummary)
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
                snapshotBook?.copy(
                    fileId = snapshotBook.fileId ?: record.fileId,
                    localPath = record.localPath
                ) ?: BookSummary(
                    libraryId = "",
                    id = record.bookId,
                    fileId = record.fileId,
                    title = record.title,
                    format = record.mimeType,
                    mediaKind = record.mediaKind,
                    localPath = record.localPath
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
        val url = book.coverUrl?.let(::coverThumbnailUrl) ?: return@withContext null
        synchronized(coverCache) { coverCache[url] }?.let { return@withContext it }
        val serverUrl = getServerUrl().orEmpty()
        coverCacheStore.read(serverUrl, book.id, url)?.let { bytes ->
            synchronized(coverCache) { coverCache[url] = bytes }
            return@withContext bytes
        }
        val bytes = requestBytes(url)
        coverCacheStore.save(serverUrl, book.id, url, bytes)
        synchronized(coverCache) {
            coverCache[url] = bytes
            while (coverCache.size > 32) {
                coverCache.remove(coverCache.entries.first().key)
            }
        }
        bytes
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
        if (book.isDownloaded) {
            bookDetailCacheStore.read(serverUrl, book.id, book.fileId)?.let { cached ->
                return@withContext cached.copy(
                    book = cached.book.copy(localPath = book.localPath ?: cached.book.localPath)
                )
            }
        }
        val detail = BookOrbitPayloadParser.parseBookDetail(
            fallback = book,
            payload = request("/api/v1/books/${book.id}", "GET", null),
            downloads = downloadStore.readAll(serverUrl).associateBy { it.fileId },
            serverBase = serverBase()
        )
        if (book.isDownloaded) {
            bookDetailCacheStore.save(serverUrl, book.id, book.fileId, detail)
        }
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
            books = mergeSeriesBooks(pages)
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
        BrowserState(
            serverUrl = serverUrl,
            libraries = snapshot.libraries,
            selectedLibraryId = selectedLibraryId,
            books = books.withCurrentDownloads(downloads),
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
        ensureReaderCanOpen(
            book = book,
            localFile = localFile,
            streamUrl = streamUrl
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
        runCatching {
            ensureReaderCanOpen(
                book = savedBook,
                localFile = localFile,
                streamUrl = streamUrl
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
            lastKnownPosition = restoredProgress.positionMs,
            pageIndex = epubPosition?.chapterIndex ?: restoredProgress.pageIndex,
            readerPageIndex = epubPosition?.pageIndex ?: savedBook.readerPageIndex ?: 0,
            progressPercent = restoredProgress.progressPercent
        )
    }

    override suspend fun downloadBook(book: BookSummary): File = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: throw UserFacingException("This title is missing a downloadable file.")
        val target = downloadStore.downloadTarget(fileId, book.title, book.mediaKind, book.format)
        if (!target.exists() || target.length() <= 0L) {
            if (target.exists() && target.length() <= 0L) {
                target.delete()
            }
            val parent = target.parentFile
            parent?.mkdirs()
            if (parent != null && !parent.exists()) {
                throw UserFacingException("Unable to prepare local storage for this download.")
            }
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
                val input = response.body?.byteStream()
                    ?: throw UserFacingException("The server returned an empty download.")
                try {
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                } catch (error: IOException) {
                    target.delete()
                    if (error.isLikelyLocalStorageFailure()) {
                        throw UserFacingException("Local storage could not save this download. Check free space and file access, then try again.")
                    }
                    throw error
                }
                ensureNonEmptyFile(
                    target = target,
                    message = "The server returned an empty download."
                )
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
                mimeType = book.format
            )
        )
        val downloadedBook = book.copy(localPath = target.absolutePath)
        val detail = runCatching { loadBookDetail(downloadedBook) }
            .getOrElse { BookDetailInfo(downloadedBook) }
        bookDetailCacheStore.save(getServerUrl().orEmpty(), book.id, fileId, detail)
        target
    }

    override suspend fun deleteLocalCopy(book: BookSummary) = withContext(Dispatchers.IO) {
        val fileId = book.fileId ?: throw UserFacingException("This title does not have a removable local file.")
        val deleted = downloadStore.delete(getServerUrl().orEmpty(), fileId)
        if (!deleted) {
            throw UserFacingException("Unable to remove the local copy for this title.")
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
            progressPercent = normalizeStoredProgressPercent(progressPercent),
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
        val pending = queueStore.readAll()
        if (pending.isEmpty()) {
            return@withContext SyncAttemptResult.Success
        }
        val currentServerUrl = getServerUrl().orEmpty()
        val survivors = mutableListOf<ProgressUpdate>()
        val orderedPending = pending.sortedBy { it.updatedAtMillis }
        var authBlocked = false
        var transientFailure = false
        orderedPending.forEach { item ->
            if (authBlocked) {
                survivors += item
                return@forEach
            }
            if (!item.targetsServer(currentServerUrl)) {
                survivors += item
                return@forEach
            }
            runCatching { postProgress(item) }
                .onSuccess { submitted ->
                    if (submitted) {
                        lastSyncedProgressStore.save(item)
                    } else {
                        survivors += item
                    }
                }
                .onFailure { error ->
                    if (error is AuthenticationRequiredException) {
                        authBlocked = true
                        survivors += item
                    } else {
                        transientFailure = true
                        survivors += item
                    }
                }
                .getOrNull()
        }
        queueStore.replaceAll(survivors)
        when {
            authBlocked -> SyncAttemptResult.AuthenticationBlocked
            transientFailure && survivors.isNotEmpty() -> SyncAttemptResult.TransientFailure
            else -> SyncAttemptResult.Success
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

    private suspend fun postProgress(item: ProgressUpdate): Boolean = withContext(Dispatchers.IO) {
        if (item.serverUrl.isBlank() || item.serverUrl != getServerUrl().orEmpty()) {
            return@withContext false
        }
        val lastSynced = lastSyncedProgressStore.read(item.progressKey())
        if (lastSynced != null && item.isStaleComparedTo(lastSynced)) {
            return@withContext false
        }

        val path = if (item.mediaKind == MediaKind.AUDIO) {
            "/api/v1/books/${item.bookId}/audio-progress"
        } else {
            val fileId = item.fileId ?: return@withContext false
            "/api/v1/books/files/$fileId/progress"
        }

        val payload = buildProgressPayload(item) ?: return@withContext false

        request(path, if (item.mediaKind == MediaKind.AUDIO) "PATCH" else "POST", payload.toString().toRequestBody(JSON))
        true
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
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bookorbit-progress-sync",
            ExistingWorkPolicy.KEEP,
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
        return when (book.mediaKind) {
            MediaKind.EPUB,
            MediaKind.PDF,
            MediaKind.COMIC -> if (allowRemoteCache) cacheReadableCopy(book) else null
            else -> null
        }
    }

    private fun ensureReaderCanOpen(
        book: BookSummary,
        localFile: File?,
        streamUrl: String?
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
                if (localFile == null) {
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
        val extension = when (book.mediaKind) {
            MediaKind.EPUB -> "epub"
            MediaKind.PDF -> "pdf"
            MediaKind.AUDIO -> "bin"
            MediaKind.COMIC -> "cbz"
            MediaKind.UNKNOWN -> "bin"
        }
        val targetDir = File(context.cacheDir, "reader-cache").apply { mkdirs() }
        val target = File(targetDir, ReaderCacheKey.build(serverUrl, fileId, extension))
        if (target.exists() && target.length() > 0L && ReaderFileValidator.isReadable(book.mediaKind, target)) {
            return target
        }
        if (target.exists()) {
            target.delete()
        }

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
            val input = response.body?.byteStream() ?: return@executeAuthenticated false
            target.outputStream().use { output -> input.copyTo(output) }
            true
        }
        if (!fetched) {
            return null
        }
        return runCatching {
            ensureNonEmptyFile(target, "The server returned an empty reader file.")
            target
        }.getOrNull()
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

    private fun requestBytes(url: String): ByteArray {
        return executeAuthenticated(
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
            path.contains("/books") && method == "POST" -> "load books"
            path.contains("/progress") -> "sync reading progress"
            path.contains("/audio-progress") -> "sync listening progress"
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

    private suspend fun clearCookies() {
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
                val progressLabel = progressPercent
                    ?.let { "${formatProgressValue(it)}%" }
                    ?: readingProgress.progressLabel()
                val readStatusValue = readStatus?.stringValue("status", "state")
                    ?.trim()
                    ?.lowercase(Locale.US)
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
                        progressLabel = progressLabel,
                        progressPercent = progressPercent,
                        progressPositionMs = readingProgress.progressPositionMs(),
                        progressPageIndex = readingProgress.progressPageIndex(),
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
                        lastReadAtMillis = readingProgress.timestampValue(
                            "updatedAt", "lastReadAt", "finishedAt", "completedAt"
                        ) ?: readStatus.timestampValue("finishedAt", "startedAt", "updatedAt")
                            ?: obj.timestampValue("lastReadAt", "lastReadDate")
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
        return BookDetailInfo(
            book = book,
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
                ?: obj.optJSONObject("audioMetadata")?.numberValue("durationSeconds", "duration")?.toLong()
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
        serverBase: String
    ): SeriesBooksPage {
        val root = extractObject(payload, "load series details")
        val books = parseBooks(
            libraryId = "",
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
                            fallbackPath = "/api/v1/series/$id/cover",
                            keys = arrayOf("coverUrl", "cover", "coverImage")
                        ) ?: "$serverBase/api/v1/series/$id/cover"
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
        val token = listOfNotNull(format, title).joinToString(" ").lowercase(Locale.US)
        return when {
            token.endsWith(".mp3") || token.endsWith(".m4b") || token.contains("audio") -> MediaKind.AUDIO
            token.endsWith(".pdf") || token.contains("pdf") -> MediaKind.PDF
            token.endsWith(".cbz") || token.endsWith(".cbr") || token.contains("comic") -> MediaKind.COMIC
            token.endsWith(".epub") || token.contains("epub") || token.contains("mobi") || token.contains("azw3") -> MediaKind.EPUB
            else -> MediaKind.UNKNOWN
        }
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
        fallbackPath: String,
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
        return if (hasImageMetadata) "$serverBase$fallbackPath" else null
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

internal suspend fun loadCompleteLibraryPages(
    firstPage: LibraryBooksPage? = null,
    loadPage: suspend (Int) -> LibraryBooksPage
): List<LibraryBooksPage> {
    val pages = mutableListOf<LibraryBooksPage>()
    val accumulatedIds = linkedSetOf<String>()
    var targetTotal: Int? = null
    var pageNumber = 0

    while (true) {
        val page = if (pageNumber == 0 && firstPage != null) firstPage else loadPage(pageNumber)
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
    val localPath = book.fileId?.let { downloads[it]?.localPath }
    if (localPath == book.localPath) book else book.copy(localPath = localPath)
}

internal fun mergeSeriesBooks(pages: List<SeriesBooksPage>): List<BookSummary> {
    return pages
        .flatMap { it.books }
        .distinctBy { it.id }
        .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
}

internal fun coverThumbnailUrl(coverUrl: String): String {
    return if (coverUrl.matches(Regex(".*/api/v1/books/[^/]+/cover$"))) {
        coverUrl.removeSuffix("/cover") + "/thumbnail"
    } else {
        coverUrl
    }
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
    return JSONObject().apply {
        put("percentage", (normalizeStoredProgressPercent(item.progressPercent) ?: 0f).toDouble())
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
