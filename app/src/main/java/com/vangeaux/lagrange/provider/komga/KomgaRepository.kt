package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

import android.content.Context
import com.vangeaux.lagrange.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun komgaDownloadedLocalFile(record: DownloadRecord?, fallbackPath: String?): File? =
    listOfNotNull(record?.localPath, fallbackPath)
        .asSequence()
        .map(::File)
        .firstOrNull(File::isFile)

internal fun komgaLocalReaderBook(book: BookSummary, record: DownloadRecord?): BookSummary? =
    komgaDownloadedLocalFile(record, book.localPath)?.let { file ->
        book.copy(localPath = file.absolutePath)
    }

internal fun komgaReaderPageIndex(book: BookSummary): Int =
    if (book.mediaKind == MediaKind.EPUB) 0 else book.progressPageIndex ?: 0

internal fun komgaOfflineBrowserState(serverUrl: String, books: List<BookSummary>): BrowserState? =
    books.takeIf { it.isNotEmpty() }?.let { localBooks ->
        BrowserState(
            serverUrl = serverUrl,
            libraries = emptyList(),
            selectedLibraryId = null,
            books = localBooks,
            homeBooks = localBooks,
            isCatalogComplete = true
        )
    }

internal fun komgaMergeDownloadedBooks(cached: List<BookSummary>, local: List<BookSummary>): List<BookSummary> {
    val localByKey = local.flatMap { book -> listOfNotNull(book.fileId, book.id).map { it to book } }.toMap()
    val merged = cached.map { book -> localByKey[book.fileId ?: book.id]?.let { book.copy(localPath = it.localPath) } ?: book }
    val known = merged.flatMap { listOfNotNull(it.fileId, it.id) }.toSet()
    return merged + local.filter { book -> listOfNotNull(book.fileId, book.id).none(known::contains) }
}

internal fun komgaCachedSeriesCatalogFromBooks(
    books: List<BookSummary>,
    filter: SeriesCatalogFilter,
    page: Int
): SeriesCatalogPage {
    val filtered = filterAndSortSeriesCatalog(
        aggregateBooksToSeriesCatalog(filterBooksForSeriesCatalog(books, filter.libraryId)).items,
        filter
    )
    val from = (page * 20).coerceAtMost(filtered.size)
    val to = (from + 20).coerceAtMost(filtered.size)
    return SeriesCatalogPage(filtered.subList(from, to), filtered.size, page, 20)
}

internal fun komgaCachedSeriesDetailFromBooks(
    books: List<BookSummary>,
    seriesId: String
): SeriesDetailInfo? {
    val seriesBooks = books
        .filter { it.seriesId == seriesId || (it.seriesId.isNullOrBlank() && "name:${it.seriesName}" == seriesId) }
        .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
    if (seriesBooks.isEmpty()) return null
    return SeriesDetailInfo(
        id = seriesId,
        name = seriesBooks.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) } ?: "Series",
        bookCount = seriesBooks.size,
        readCount = seriesBooks.count { it.isRead || (it.progressPercent ?: 0f) >= 99.5f },
        authors = seriesBooks.flatMap { it.author.orEmpty().split(",") }.map(String::trim)
            .filter(String::isNotBlank).distinct().sorted(),
        books = seriesBooks
    )
}

internal fun komgaDownloadedEpubFile(record: DownloadRecord?, fallbackPath: String?): File? =
    komgaDownloadedLocalFile(record, fallbackPath)

class KomgaRepository(context: Context) : BookOrbitDataSource, ProfileSessionAware {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("komga_connection", Context.MODE_PRIVATE)
    private val authModule = KomgaAuthModuleImpl(appContext)
    private val libraryModule = KomgaLibraryModuleImpl(authModule)
    private val catalogModule = KomgaBookCatalogModuleImpl(authModule)
    private val seriesModule = KomgaSeriesModuleImpl(authModule, libraryModule, catalogModule)
    private val homeModule = KomgaHomeShelfModuleImpl(authModule)
    private val readingStatusModule = KomgaReadingStatusModuleImpl(authModule) { getServerUrl().orEmpty() }
    private val readingProgressModule = KomgaReadingProgressModuleImpl(authModule) { getServerUrl().orEmpty() }
    private val detailModule = KomgaBookDetailModuleImpl(authModule)
    private val coverModule = KomgaCoverModuleImpl(authModule)
    private val downloadModule = KomgaDownloadModuleImpl(appContext, authModule)
    private val downloadStore = DownloadStore(appContext)
    private val browserSnapshotStore = BrowserSnapshotStore(appContext)

    override suspend fun getServerUrl(): String? = preferences.getString(SERVER_URL_KEY, null)

    override suspend fun setServerUrl(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException("Enter a valid Komga server URL.")
        preferences.edit().putString(SERVER_URL_KEY, normalized).apply()
    }

    override suspend fun clearServer() {
        preferences.edit().remove(SERVER_URL_KEY).remove(SELECTED_LIBRARY_KEY).apply()
        authModule.clearRuntimeSession()
    }

    override suspend fun clearSession() = authModule.clearSession()

    internal suspend fun streamingRequestHeaders(url: String): Map<String, String> {
        val serverUrl = getServerUrl().orEmpty()
        if (!sameHttpOrigin(url, serverUrl)) return emptyMap()
        return authModule.authorizationHeader()?.let { mapOf("Authorization" to it) }.orEmpty()
    }

    internal suspend fun recoverStreamingAuthentication(): Boolean =
        authModule.restoreCurrentProfileSession()

    override suspend fun saveCurrentProfileSession() = authModule.saveCurrentProfileSession()

    override suspend fun restoreCurrentProfileSession(): Boolean = authModule.restoreCurrentProfileSession()

    override suspend fun getSelectedLibraryId(): String? = preferences.getString(SELECTED_LIBRARY_KEY, null)

    override suspend fun setSelectedLibraryId(libraryId: String) {
        preferences.edit().putString(SELECTED_LIBRARY_KEY, libraryId).apply()
    }

    override suspend fun getSessionState(): SessionState = authModule.sessionState(getServerUrl().orEmpty())

    override suspend fun login(username: String, password: String) {
        val url = getServerUrl() ?: throw UserFacingException("Enter a Komga server URL first.")
        authModule.login(url, username, password)
    }

    override suspend fun loadLibraries(): List<LibrarySummary> {
        val serverUrl = getServerUrl().orEmpty()
        return runCatching { libraryModule.loadLibraries(serverUrl) }
            .onSuccess { libraries ->
                browserSnapshotStore.saveLibraries(serverUrl, getSelectedLibraryId(), libraries)
            }
            .getOrElse { error ->
                browserSnapshotStore.read(serverUrl)?.libraries?.takeIf { it.isNotEmpty() } ?: throw error
            }
    }

    suspend fun loadHomeShelves(): HomeShelfData {
        val shelves = homeModule.loadHomeShelves(getServerUrl().orEmpty())
        val downloads = downloadStore.readAll(getServerUrl().orEmpty()).associateBy { it.fileId }
        return shelves.copy(
            booksBySection = shelves.booksBySection.mapValues { (_, books) -> books.withCurrentDownloads(downloads) },
            seriesBySection = shelves.seriesBySection.mapValues { (_, books) -> books.withCurrentDownloads(downloads) }
        )
    }

    override suspend fun loadBooks(libraryId: String): List<BookSummary> =
        loadBooksPage(libraryId, 0).items

    override suspend fun loadBooksPage(libraryId: String, page: Int): LibraryBooksPage {
        val serverUrl = getServerUrl().orEmpty()
        return runCatching {
            val result = catalogModule.loadBooks(serverUrl, libraryId, page)
            val books = withDownloadPaths(serverUrl, result.items)
            val cachedBooks = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.get(libraryId).orEmpty()
            browserSnapshotStore.saveBooks(
                serverUrl = serverUrl,
                selectedLibraryId = getSelectedLibraryId(),
                libraryId = libraryId,
                books = (cachedBooks + books).distinctBy { it.id }
            )
            result.copy(items = books)
        }.getOrElse { error ->
            cachedLibraryPage(serverUrl, libraryId)?.takeIf { it.items.isNotEmpty() } ?: throw error
        }
    }

    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? =
        cachedLibraryPage(getServerUrl().orEmpty(), libraryId)

    override suspend fun refreshLibraryCatalog(
        libraryId: String,
        firstPage: LibraryBooksPage?
    ): LibraryBooksPage = runCatching {
        val first = firstPage ?: loadBooksPage(libraryId, 0)
        var page = first.page ?: 0
        val books = first.items.toMutableList()
        var complete = first.isComplete
        while (!complete && page < 999) {
            page++
            val next = loadBooksPage(libraryId, page)
            books += next.items
            complete = next.isComplete || next.items.isEmpty()
        }
        val completeBooks = books.distinctBy { it.id }
        browserSnapshotStore.saveBooks(
            serverUrl = getServerUrl().orEmpty(),
            selectedLibraryId = getSelectedLibraryId(),
            libraryId = libraryId,
            books = completeBooks
        )
        LibraryBooksPage(
            items = completeBooks,
            total = completeBooks.size,
            page = 0,
            size = completeBooks.size,
            isComplete = true,
            refreshedAtMillis = System.currentTimeMillis()
        )
    }.getOrElse { error ->
        cachedLibraryPage(getServerUrl().orEmpty(), libraryId)?.copy(isComplete = true) ?: throw error
    }

    override suspend fun loadLocalBooks(): List<BookSummary> = withContext(Dispatchers.IO) {
        downloadStore.readAll(getServerUrl().orEmpty())
            .filter { it.status == DownloadRecordStatus.COMPLETE && File(it.localPath).exists() }
            .map { record ->
                BookSummary(
                    libraryId = "",
                    id = record.bookId,
                    fileId = record.fileId,
                    title = record.title,
                    filename = record.filename ?: File(record.localPath).name,
                    format = record.mimeType,
                    mediaKind = record.mediaKind,
                    localPath = record.localPath,
                    updatedAtMillis = record.sourceUpdatedAtMillis
                )
            }
    }

    override suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage = loadBooksPage(libraryId, page)

    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo {
        val record = book.fileId?.let { downloadStore.find(getServerUrl().orEmpty(), it) }
            ?.takeIf { it.status == DownloadRecordStatus.COMPLETE && File(it.localPath).exists() }
        return detailModule.loadBookDetail(getServerUrl().orEmpty(), book.copy(localPath = record?.localPath ?: book.localPath))
    }

    override suspend fun prepareEpubImageLibrarySource(
        book: BookSummary,
        allowRemoteCache: Boolean
    ): EpubImageLibrarySourceResult {
        val record = book.fileId?.let { downloadStore.find(getServerUrl().orEmpty(), it) }
            ?.takeIf { it.status == DownloadRecordStatus.COMPLETE }
        return epubImageLibrarySourceResult(
            localFile = komgaDownloadedEpubFile(record, book.localPath),
            localFileError = null,
            fileId = book.fileId,
            allowRemoteCache = allowRemoteCache
        )
    }

    override suspend fun loadSeriesCatalog(query: String?, page: Int): SeriesCatalogPage =
        loadSeriesCatalog(SeriesCatalogFilter(query = query), page)

    override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage {
        val serverUrl = getServerUrl().orEmpty()
        return runCatching { seriesModule.loadSeriesCatalog(serverUrl, null, filter, page) }
            .getOrElse { error -> cachedSeriesCatalog(serverUrl, filter, page) ?: throw error }
    }

    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? {
        val serverUrl = getServerUrl().orEmpty()
        return runCatching { seriesModule.loadSeriesDetail(serverUrl, seriesId) }
            .getOrElse { cachedSeriesDetail(serverUrl, seriesId) }
    }

    override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) =
        readingStatusModule.setBookReadingStatus(book, status)

    override suspend fun markBookAsRead(book: BookSummary) = readingStatusModule.markBookAsRead(book)

    override suspend fun resetBookReadingState(book: BookSummary) = readingStatusModule.resetBookReadingState(book)

    override suspend fun loadBookCover(book: BookSummary): ByteArray? =
        coverModule.loadBookCover(getServerUrl().orEmpty(), book)

    override suspend fun loadCatalogImage(url: String): ByteArray? =
        coverModule.loadCatalogImage(url)

    override suspend fun canReachServer(serverUrl: String): Boolean =
        checkServer(serverUrl) == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult =
        authModule.checkServer(serverUrl)

    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) {
        readingProgressModule.queueProgress(book, position, pageIndex, progressPercent)
    }

    override suspend fun pendingProgressCount(): Int = 0

    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Unsupported

    override suspend fun loadReaderProgress(
        book: BookSummary,
        availableFiles: List<BookFileOption>
    ): BookSummary = detailModule.loadBookDetail(getServerUrl().orEmpty(), book).book

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState {
        val record = book.fileId?.let { downloadStore.find(getServerUrl().orEmpty(), it) }
            ?.takeIf { it.status == DownloadRecordStatus.COMPLETE }
        val localBook = komgaLocalReaderBook(book, record)
        if (localBook != null) {
            return ReaderState(
                book = localBook,
                localFile = File(localBook.localPath!!),
                pageIndex = komgaReaderPageIndex(localBook),
                progressPercent = localBook.progressPercent,
                serverProgressAuthoritative = !localOnly,
                initialLocatorJson = localBook.readerLocatorJson
            )
        }
        if (localOnly) {
            throw UserFacingException("This Komga book is not downloaded for offline reading.")
        }
        val detail = loadBookDetail(book)
        val resolvedBook = detail.book
        if (resolvedBook.mediaKind == MediaKind.COMIC) {
            return ReaderState(
                book = resolvedBook,
                comicPagesUrl = "${getServerUrl().orEmpty().trimEnd('/')}/api/v1/books/${resolvedBook.id}/pages?zero_based=true",
                pageIndex = komgaReaderPageIndex(resolvedBook),
                progressPercent = resolvedBook.progressPercent,
                serverProgressAuthoritative = true,
                initialLocatorJson = resolvedBook.readerLocatorJson
            )
        }
        val localFile = downloadBook(resolvedBook) { }
        val readerBook = resolvedBook.copy(localPath = localFile.absolutePath)
        return ReaderState(
            book = readerBook,
            localFile = localFile,
            pageIndex = komgaReaderPageIndex(readerBook),
            progressPercent = readerBook.progressPercent,
            serverProgressAuthoritative = !localOnly,
            initialLocatorJson = readerBook.readerLocatorJson
        )
    }

    override suspend fun saveActiveReader(book: BookSummary, launchMode: ReaderLaunchMode) {
        preferences.edit()
            .putString(ACTIVE_READER_BOOK_ID_KEY, book.id)
            .putString(ACTIVE_READER_FILE_ID_KEY, book.fileId)
            .putString(ACTIVE_READER_TITLE_KEY, book.title)
            .putString(ACTIVE_READER_LOCAL_PATH_KEY, book.localPath)
            .putString(ACTIVE_READER_FORMAT_KEY, book.format)
            .putString(ACTIVE_READER_MEDIA_KIND_KEY, book.mediaKind.name)
            .apply()
    }

    override suspend fun clearActiveReader() {
        preferences.edit()
            .remove(ACTIVE_READER_BOOK_ID_KEY)
            .remove(ACTIVE_READER_FILE_ID_KEY)
            .remove(ACTIVE_READER_TITLE_KEY)
            .remove(ACTIVE_READER_LOCAL_PATH_KEY)
            .remove(ACTIVE_READER_FORMAT_KEY)
            .remove(ACTIVE_READER_MEDIA_KIND_KEY)
            .apply()
    }

    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = null

    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val snapshot = browserSnapshotStore.read(serverUrl)
        val localBooks = loadLocalBooks()
        val selectedId = libraryId ?: getSelectedLibraryId() ?: snapshot?.selectedLibraryId
        val cachedBooks = if (selectedId != null) snapshot?.booksByLibraryId?.get(selectedId).orEmpty()
        else snapshot?.booksByLibraryId?.values?.flatten().orEmpty()
        val books = komgaMergeDownloadedBooks(cachedBooks, localBooks)
        if (snapshot == null || books.isEmpty()) komgaOfflineBrowserState(serverUrl, localBooks)
        else BrowserState(
            serverUrl = serverUrl,
            libraries = snapshot.libraries,
            selectedLibraryId = selectedId,
            books = books,
            homeBooks = snapshot.booksByLibraryId.values.flatten().let { komgaMergeDownloadedBooks(it, localBooks) },
            isCatalogComplete = true,
            catalogRefreshedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun loadCachedHomeBooks(): List<BookSummary> = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        val snapshotBooks = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.values?.flatten().orEmpty()
        komgaMergeDownloadedBooks(snapshotBooks, loadLocalBooks())
    }

    private suspend fun withDownloadPaths(serverUrl: String, books: List<BookSummary>): List<BookSummary> {
        val records = downloadStore.readAll(serverUrl)
            .filter { it.status == DownloadRecordStatus.COMPLETE && File(it.localPath).exists() }
            .associateBy { it.fileId }
        return books.withCurrentDownloads(records)
    }

    private suspend fun cachedLibraryPage(serverUrl: String, libraryId: String): LibraryBooksPage? {
        val books = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.get(libraryId).orEmpty()
        if (books.isEmpty()) return null
        return LibraryBooksPage(
            items = withDownloadPaths(serverUrl, books),
            total = books.size,
            page = 0,
            size = books.size,
            isComplete = true
        )
    }


    private suspend fun cachedSeriesCatalog(
        serverUrl: String,
        filter: SeriesCatalogFilter,
        page: Int
    ): SeriesCatalogPage? {
        val books = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.values?.flatten().orEmpty()
        if (books.isEmpty()) return null
        return komgaCachedSeriesCatalogFromBooks(books, filter, page)
    }

    private suspend fun cachedSeriesDetail(serverUrl: String, seriesId: String): SeriesDetailInfo? {
        val books = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.values?.flatten().orEmpty()
        return komgaCachedSeriesDetailFromBooks(books, seriesId)
    }

    override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit): File {
        val serverUrl = getServerUrl().orEmpty()
        val target = downloadModule.downloadBook(serverUrl, book, onProgress)
        downloadStore.save(
            DownloadRecord(
                serverUrl = serverUrl,
                fileId = book.fileId ?: book.id,
                bookId = book.id,
                title = book.title,
                filename = book.filename ?: target.name,
                localPath = target.absolutePath,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                status = DownloadRecordStatus.COMPLETE
            )
        )
        return target
    }

    override suspend fun deleteLocalCopy(book: BookSummary) {
        val serverUrl = getServerUrl().orEmpty()
        book.fileId?.let { downloadStore.delete(serverUrl, it) }
            ?: book.localPath?.let(::File)?.takeIf(File::exists)?.delete()
    }

    private companion object {
        const val SERVER_URL_KEY = "server_url"
        const val SELECTED_LIBRARY_KEY = "selected_library_id"
        const val ACTIVE_READER_BOOK_ID_KEY = "active_reader_book_id"
        const val ACTIVE_READER_FILE_ID_KEY = "active_reader_file_id"
        const val ACTIVE_READER_TITLE_KEY = "active_reader_title"
        const val ACTIVE_READER_LOCAL_PATH_KEY = "active_reader_local_path"
        const val ACTIVE_READER_FORMAT_KEY = "active_reader_format"
        const val ACTIVE_READER_MEDIA_KIND_KEY = "active_reader_media_kind"
    }
}
