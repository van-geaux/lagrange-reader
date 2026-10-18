package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ActiveReaderSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class KavitaRepository(context: Context) : BookOrbitDataSource, ProfileSessionAware {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("kavita_connection", Context.MODE_PRIVATE)
    private val authModule = KavitaAuthModuleImpl(appContext)
    private val libraryModule = KavitaLibraryModuleImpl(authModule)
    private val catalogModule = KavitaBookCatalogModuleImpl(authModule)
    private val detailModule = KavitaBookDetailModuleImpl(authModule)
    private val seriesModule = KavitaSeriesCatalogModuleImpl(catalogModule, detailModule)
    private val coverModule = KavitaCoverModuleImpl(authModule)
    private val readerModule = KavitaReaderModuleImpl(authModule, appContext)
    private val readingProgressModule = KavitaReadingProgressModuleImpl(authModule)
    private val readingStatusModule = KavitaReadingStatusModuleImpl(authModule)
    private val downloadModule = KavitaDownloadModuleImpl(appContext, authModule)
    private val downloadStore = DownloadStore(appContext)
    private val browserSnapshotStore = BrowserSnapshotStore(appContext)
    private val storageManager = AppStorageManager(appContext)
    private val offlineCacheStatusStore = OfflineCacheStatusStore(appContext)
    private val offlineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var offlineCacheJob: Job? = null

    override suspend fun getServerUrl(): String? = preferences.getString(SERVER_URL_KEY, null)

    override suspend fun setServerUrl(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException("Enter a valid Kavita server URL.")
        preferences.edit().putString(SERVER_URL_KEY, normalized).apply()
    }

    override suspend fun clearServer() {
        preferences.edit().remove(SERVER_URL_KEY).remove(SELECTED_LIBRARY_KEY).apply()
        authModule.clearSession()
    }

    override suspend fun clearSession() = authModule.clearSession()

    override suspend fun getSelectedLibraryId(): String? =
        preferences.getString(SELECTED_LIBRARY_KEY, null)

    override suspend fun setSelectedLibraryId(libraryId: String) {
        preferences.edit().putString(SELECTED_LIBRARY_KEY, libraryId).apply()
    }

    override suspend fun getSessionState(): SessionState =
        authModule.sessionState(getServerUrl().orEmpty())

    override suspend fun login(username: String, password: String): Unit {
        authModule.authenticate(getServerUrl().orEmpty(), username, password)
    }

    override suspend fun loadLibraries(): List<LibrarySummary> {
        val libraries = libraryModule.loadLibraries()
        browserSnapshotStore.saveLibraries(getServerUrl().orEmpty(), getSelectedLibraryId(), libraries)
        return libraries
    }

    override suspend fun loadBooks(libraryId: String): List<BookSummary> {
        val books = catalogModule.loadBooks(libraryId)
        browserSnapshotStore.saveBooks(getServerUrl().orEmpty(), getSelectedLibraryId(), libraryId, books)
        return books
    }
    override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter) =
        catalogModule.loadBooksPage(libraryId, page, filter)
    override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int) =
        catalogModule.loadRecentBooksPage(libraryId, section, page)
    override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?) =
        catalogModule.refreshLibraryCatalog(libraryId, firstPage)
    override suspend fun searchBooks(query: String) = catalogModule.searchBooks(query)
    override suspend fun loadCachedHomeBooks(): List<BookSummary> = runCatching {
        loadLibraries()
            .flatMap { library -> loadBooks(library.id) }
            .plus(loadLocalBooks())
            .distinctBy { it.libraryId to (it.fileId ?: it.id) }
    }.getOrElse {
        browserSnapshotStore.read(getServerUrl().orEmpty())?.booksByLibraryId?.values.orEmpty()
            .flatten()
            .plus(loadLocalBooks())
            .distinctBy { it.libraryId to (it.fileId ?: it.id) }
    }
    override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
        seriesModule.loadSeriesCatalog(filter, page)
    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? =
        seriesModule.loadSeriesDetail(seriesId)
    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo {
        val detail = detailModule.loadBookDetail(book)
        val localRecord = downloadStore.find(getServerUrl().orEmpty(), book.fileId ?: book.id)
            ?.takeIf { it.status == DownloadRecordStatus.COMPLETE && File(it.localPath).isFile }
        return localRecord?.let { record ->
            detail.copy(
                book = detail.book.copy(
                    localPath = record.localPath,
                    isLocalOnlyOverride = false
                )
            )
        } ?: detail
    }
    override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? = detailModule.loadCachedBookDetail(book)
    override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
        detailModule.setBookUserRating(book, rating)
    override suspend fun loadBookCover(book: BookSummary): ByteArray? = coverModule.loadBookCover(book)
    override suspend fun loadCatalogImage(url: String): ByteArray? = coverModule.loadCatalogImage(url)
    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState =
        readerModule.buildReaderState(book, localOnly)
    override suspend fun prepareEpubImageLibrarySource(
        book: BookSummary,
        allowRemoteCache: Boolean
    ): EpubImageLibrarySourceResult = readerModule.prepareEpubImageLibrarySource(book, allowRemoteCache)
    override suspend fun restoreActiveReaderState(
        session: ActiveReaderSession,
        localOnly: Boolean
    ): ReaderState? = readerModule.restoreActiveReaderState(session, localOnly)
    override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) =
        readingStatusModule.setBookReadingStatus(book, status)
    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) =
        readingProgressModule.queueProgress(book, position, pageIndex, progressPercent)
    override suspend fun pendingProgressCount(): Int = readingProgressModule.pendingProgressCount()
    override suspend fun syncPendingProgress(): SyncAttemptResult = readingProgressModule.syncPendingProgress()
    override suspend fun loadReaderProgress(book: BookSummary, availableFiles: List<BookFileOption>): BookSummary =
        readingProgressModule.loadReaderProgress(book)
    override suspend fun markBookAsRead(book: BookSummary) = readingStatusModule.markBookAsRead(book)
    override suspend fun resetBookReadingState(book: BookSummary) = readingStatusModule.resetBookReadingState(book)
    override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit): File =
        downloadModule.downloadBook(book, onProgress)
    override suspend fun deleteLocalCopy(book: BookSummary) = downloadModule.deleteLocalCopy(book)
    override suspend fun deleteLocalCopies(book: BookSummary): Set<String> = downloadModule.deleteLocalCopies(book)
    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? {
        val serverUrl = getServerUrl().orEmpty()
        val snapshot = browserSnapshotStore.read(serverUrl) ?: return null
        val selected = libraryId ?: snapshot.selectedLibraryId
        val books = snapshot.booksByLibraryId[selected].orEmpty()
        return BrowserState(
            serverUrl = serverUrl,
            libraries = snapshot.libraries,
            selectedLibraryId = selected,
            books = books,
            homeBooks = snapshot.booksByLibraryId.values.flatten().plus(loadLocalBooks())
                .distinctBy { it.libraryId to (it.fileId ?: it.id) },
            booksTotal = books.size,
            booksPageSize = books.size,
            isCatalogComplete = true,
            catalogRefreshedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun loadLocalBooks(): List<BookSummary> {
        val serverUrl = getServerUrl().orEmpty()
        val snapshotBooks = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.values.orEmpty()
            .flatten()
        val metadataByKey = snapshotBooks
            .flatMap { book -> listOfNotNull(book.fileId, book.id).map { it to book } }
            .toMap()
        return downloadStore.readAll(serverUrl)
            .filter { it.status == DownloadRecordStatus.COMPLETE && File(it.localPath).isFile }
            .map { record ->
                val fallback = BookSummary(
                    libraryId = "",
                    id = record.bookId,
                    fileId = record.fileId,
                    title = record.title,
                    filename = record.filename ?: File(record.localPath).name,
                    format = record.mimeType,
                    mediaKind = record.mediaKind,
                    updatedAtMillis = record.sourceUpdatedAtMillis
                )
                (metadataByKey[record.fileId] ?: metadataByKey[record.bookId] ?: fallback).copy(
                    fileId = record.fileId,
                    filename = record.filename ?: metadataByKey[record.fileId]?.filename ?: File(record.localPath).name,
                    localPath = record.localPath,
                    mediaKind = record.mediaKind,
                    format = record.mimeType ?: metadataByKey[record.fileId]?.format,
                    isLocalOnlyOverride = true
                )
            }
    }

    override suspend fun loadStorageUsage(): StorageUsage = storageManager.usage()

    override suspend fun clearAppCache() = storageManager.clearDisposableCache()

    override suspend fun loadOfflineCacheStatus(): OfflineCacheStatus = offlineCacheStatusStore.read()

    override suspend fun startOfflineCacheUpdate(): Boolean {
        if (offlineCacheJob?.isActive == true) return false
        val serverUrl = getServerUrl().orEmpty()
        val libraries = runCatching { loadLibraries() }.getOrElse { return false }
        val books = runCatching {
            libraries.flatMap { loadBooks(it.id) }
                .distinctBy { it.libraryId to (it.fileId ?: it.id) }
        }.getOrElse { return false }
        offlineCacheStatusStore.write(OfflineCacheStatus(state = OfflineCacheRunState.RUNNING, total = books.size))
        offlineCacheJob = offlineScope.launch {
            var processed = 0
            var downloaded = 0
            var skipped = 0
            var failed = 0
            books.forEach { book ->
                currentCoroutineContext().ensureActive()
                try {
                    val existing = downloadStore.find(serverUrl, book.fileId ?: book.id)
                    if (existing?.status == DownloadRecordStatus.COMPLETE && File(existing.localPath).isFile) {
                        skipped++
                    } else {
                        downloadModule.downloadBook(book) { }
                        downloaded++
                    }
                } catch (_: Throwable) {
                    failed++
                } finally {
                    processed++
                    offlineCacheStatusStore.update {
                        it.copy(processed = processed, downloaded = downloaded, skipped = skipped, failed = failed)
                    }
                }
            }
            offlineCacheStatusStore.update {
                it.copy(
                    state = if (failed == 0) OfflineCacheRunState.SUCCEEDED else OfflineCacheRunState.PARTIAL,
                    lastSuccessAtMillis = System.currentTimeMillis()
                )
            }
        }
        return true
    }

    override suspend fun cancelOfflineCacheUpdate() {
        offlineCacheJob?.cancel()
        offlineCacheJob = null
        offlineCacheStatusStore.update { it.copy(state = OfflineCacheRunState.CANCELLED) }
    }

    override suspend fun clearOfflineCache() {
        val serverUrl = getServerUrl().orEmpty()
        downloadStore.readAll(serverUrl).forEach { record ->
            downloadStore.delete(serverUrl, record.fileId)
        }
        offlineCacheStatusStore.write(OfflineCacheStatus())
    }
    override suspend fun saveActiveReader(book: BookSummary, launchMode: ReaderLaunchMode) = unavailable<Unit>()
    override suspend fun clearActiveReader() = unavailable<Unit>()
    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = null


    override suspend fun canReachServer(serverUrl: String): Boolean =
        checkServer(serverUrl) == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult =
        authModule.checkServer(serverUrl)

    internal fun requestHeaders(): Map<String, String> = authModule.requestHeaders()

    override suspend fun saveCurrentProfileSession() {
        authModule.saveCurrentProfileSession()
    }

    override suspend fun restoreCurrentProfileSession(): Boolean =
        authModule.restoreCurrentProfileSession()

    private fun <T> unavailable(): T =
        throw UserFacingException("Kavita catalog and reading support are not implemented yet.")

    private companion object {
        const val SERVER_URL_KEY = "server_url"
        const val SELECTED_LIBRARY_KEY = "selected_library_id"
    }
}
