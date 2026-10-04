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

    override suspend fun loadLibraries(): List<LibrarySummary> =
        libraryModule.loadLibraries(getServerUrl().orEmpty())

    suspend fun loadHomeShelves(): HomeShelfData =
        homeModule.loadHomeShelves(getServerUrl().orEmpty())

    override suspend fun loadBooks(libraryId: String): List<BookSummary> =
        loadBooksPage(libraryId, 0).items

    override suspend fun loadBooksPage(libraryId: String, page: Int): LibraryBooksPage {
        val result = catalogModule.loadBooks(getServerUrl().orEmpty(), libraryId, page)
        val records = downloadStore.readAll(getServerUrl().orEmpty())
            .filter { it.status == DownloadRecordStatus.COMPLETE && File(it.localPath).exists() }
            .associateBy { it.fileId }
        return result.copy(items = result.items.map { book ->
            records[book.fileId]?.let { record -> book.copy(localPath = record.localPath) } ?: book
        })
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

    override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
        seriesModule.loadSeriesCatalog(getServerUrl().orEmpty(), null, filter, page)

    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? =
        seriesModule.loadSeriesDetail(getServerUrl().orEmpty(), seriesId)

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

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState {
        val record = book.fileId?.let { downloadStore.find(getServerUrl().orEmpty(), it) }
            ?.takeIf { it.status == DownloadRecordStatus.COMPLETE }
        val localBook = komgaLocalReaderBook(book, record)
        if (localBook != null) {
            return ReaderState(
                book = localBook,
                localFile = File(localBook.localPath!!)
            )
        }
        if (localOnly) {
            throw UserFacingException("This Komga book is not downloaded for offline reading.")
        }
        val detail = loadBookDetail(book)
        val resolvedBook = detail.book
        val localFile = downloadBook(resolvedBook) { }
        return ReaderState(book = resolvedBook.copy(localPath = localFile.absolutePath), localFile = localFile)
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
        komgaOfflineBrowserState(getServerUrl().orEmpty(), loadLocalBooks())
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
