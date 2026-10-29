package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import android.util.Log
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ActiveReaderSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class KavitaCleanupAction(
    val name: String,
    val cleanup: suspend () -> Unit
)

internal suspend fun runBestEffortKavitaCleanup(
    actions: List<KavitaCleanupAction>,
    onFailure: (String, Exception) -> Unit
) {
    actions.forEach { action ->
        try {
            action.cleanup()
        } catch (error: Exception) {
            onFailure(action.name, error)
        }
    }
}

private fun HomeShelfData.homeBooks(): List<BookSummary> =
    booksBySection.values.flatten()
        .plus(seriesBySection.values.flatten())
        .distinctBy { it.libraryId to (it.fileId ?: it.id) }

internal fun mergeKavitaCachedBooks(
    snapshotBooksByLibraryId: Map<String, List<BookSummary>>,
    homeShelves: HomeShelfData?,
    localBooks: List<BookSummary>,
    downloads: Map<String, DownloadRecord> = emptyMap()
): List<BookSummary> = snapshotBooksByLibraryId.values.flatten()
    .plus(homeShelves?.homeBooks().orEmpty())
    .plus(localBooks)
    .distinctBy { it.libraryId to (it.fileId ?: it.id) }
    .withCurrentDownloads(downloads)

internal fun kavitaHomeFallback(cached: HomeShelfData?, error: Exception): HomeShelfData =
    cached ?: throw error

internal class KavitaRepository(context: Context) : BookOrbitDataSource, ProfileSessionAware {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("kavita_connection", Context.MODE_PRIVATE)
    private val authModule = KavitaAuthModuleImpl(appContext)
    private val libraryModule = KavitaLibraryModuleImpl(authModule)
    private val homeModule = KavitaHomeShelfModuleImpl(authModule)
    private val homeSnapshotStore = KavitaHomeSnapshotStore(appContext)
    private val seriesSnapshotStore = KavitaSeriesCatalogSnapshotStore(appContext)
    private val catalogModule = KavitaBookCatalogModuleImpl(authModule)
    private val detailModule = KavitaBookDetailModuleImpl(authModule)
    private val seriesModule = KavitaSeriesCatalogModuleImpl(
        catalogModule,
        detailModule,
        seriesSnapshotStore
    )
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

    /** Localizes a provider error message, falling back to the original English text. */
    internal fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String =
        providerErrorMessage(appContext, messageRes, fallback, *formatArgs)

    override suspend fun getServerUrl(): String? = preferences.getString(SERVER_URL_KEY, null)

    override suspend fun setServerUrl(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException(localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        preferences.edit().putString(SERVER_URL_KEY, normalized).apply()
    }

    override suspend fun clearServer() {
        val serverUrl = runCatching { getServerUrl() }.getOrNull()
        val profileId = runCatching { ServerProfileStore(appContext).activeId() }.getOrNull()
        val automaticPolicy = runCatching {
            activeAutomaticDownloadPolicy(profileId, serverUrl)
        }.getOrNull()
        try {
            authModule.clearSession()
        } finally {
            clearAccountLocalState(
                profileId = profileId,
                automaticPolicy = automaticPolicy,
                removeServerUrl = true
            )
        }
    }

    override suspend fun clearSession() {
        val serverUrl = runCatching { getServerUrl() }.getOrNull()
        val profileId = runCatching { ServerProfileStore(appContext).activeId() }.getOrNull()
        val automaticPolicy = runCatching {
            activeAutomaticDownloadPolicy(profileId, serverUrl)
        }.getOrNull()
        try {
            authModule.clearSession()
        } finally {
            withContext(NonCancellable) {
                runBestEffortKavitaCleanup(
                    listOf(
                        KavitaCleanupAction("per-book languages") {
                            // Per-book languages are kept across profile switches; sign-out forgets them.
                            EpubBookLanguageStore(appContext).clearProfile()
                        }
                    ),
                    ::logCleanupFailure
                )
            }
            clearAccountLocalState(
                profileId = profileId,
                automaticPolicy = automaticPolicy,
                removeServerUrl = false
            )
        }
    }

    private suspend fun clearAccountLocalState(
        profileId: String?,
        automaticPolicy: AutomaticDownloadPolicy?,
        removeServerUrl: Boolean
    ) = withContext(NonCancellable) {
        val actions = mutableListOf(
            KavitaCleanupAction("connection selection") {
                val editor = preferences.edit().remove(SELECTED_LIBRARY_KEY)
                if (removeServerUrl) editor.remove(SERVER_URL_KEY)
                check(editor.commit()) { "Unable to clear Kavita connection selection." }
            },
            KavitaCleanupAction("browser snapshot") { browserSnapshotStore.clear() },
            KavitaCleanupAction("home snapshot") { homeSnapshotStore.clear() },
            KavitaCleanupAction("series catalog snapshot") { seriesSnapshotStore.clear() },
            KavitaCleanupAction("reader positions") { EpubReaderPositionStore(appContext).clear() },
            KavitaCleanupAction("locators") { ReadiumEpubLocatorStore(appContext).clear() },
            KavitaCleanupAction("TTS positions") { ReadiumEpubTtsPositionStore(appContext).clear() }
        )
        profileId?.let { activeProfileId ->
            actions += KavitaCleanupAction("active account scope") {
                AuthenticatedAccountScopeStore(appContext).deactivate(activeProfileId)
            }
        }
        automaticPolicy?.let { policy ->
            actions += KavitaCleanupAction("automatic downloads") {
                AutomaticDownloadScheduler.pauseForInactiveProfile(appContext, policy)
            }
        }
        runBestEffortKavitaCleanup(actions, ::logCleanupFailure)
    }

    private fun logCleanupFailure(name: String, error: Exception) =
        Log.w("KavitaRepository", "Unable to clear account-owned $name", error)

    private fun activeAutomaticDownloadPolicy(
        profileId: String?,
        serverUrl: String?
    ): AutomaticDownloadPolicy? {
        val base = serverUrl?.takeIf(String::isNotBlank) ?: return null
        val resolvedProfileId = profileId ?: serverProfileId(base)
        val storageScopeId = downloadStorageScopeId(appContext, base, resolvedProfileId)
            ?: return null
        return AutomaticDownloadPolicyStore(appContext)
            .read(resolvedProfileId, base, storageScopeId)
    }

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
        val books = withDownloadPaths(getServerUrl().orEmpty(), catalogModule.loadBooks(libraryId))
        browserSnapshotStore.saveBooks(getServerUrl().orEmpty(), getSelectedLibraryId(), libraryId, books)
        return books
    }
    override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter): LibraryBooksPage {
        val result = catalogModule.loadBooksPage(libraryId, page, filter)
        val books = withDownloadPaths(getServerUrl().orEmpty(), result.items)
        if (filter == BookBrowseFilter()) {
            browserSnapshotStore.appendBooks(
                getServerUrl().orEmpty(),
                getSelectedLibraryId(),
                libraryId,
                books
            )
        }
        return result.copy(items = books)
    }
    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? {
        val books = browserSnapshotStore.read(getServerUrl().orEmpty())
            ?.booksByLibraryId?.get(libraryId).orEmpty()
        return books.takeIf { it.isNotEmpty() }?.let {
            LibraryBooksPage(
                items = withDownloadPaths(getServerUrl().orEmpty(), it),
                page = 0,
                size = 100,
                total = null,
                isComplete = false
            )
        }
    }
    override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int) =
        catalogModule.loadRecentBooksPage(libraryId, section, page).let { result ->
            result.copy(items = withDownloadPaths(getServerUrl().orEmpty(), result.items))
        }
    override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?) =
        catalogModule.refreshLibraryCatalog(libraryId, firstPage).let { result ->
            result.copy(items = withDownloadPaths(getServerUrl().orEmpty(), result.items))
        }
    override suspend fun searchBooks(query: String) =
        withDownloadPaths(getServerUrl().orEmpty(), catalogModule.searchBooks(query))
    override suspend fun loadCachedHomeBooks(): List<BookSummary> = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl().orEmpty()
        mergeKavitaCachedBooks(
            browserSnapshotStore.read(serverUrl)?.booksByLibraryId.orEmpty(),
            homeSnapshotStore.read(serverUrl),
            loadLocalBooks(),
            currentDownloads(serverUrl)
        )
    }
    override suspend fun loadHomeShelves(): HomeShelfData {
        val serverUrl = getServerUrl().orEmpty()
        val shelves = try {
            homeModule.loadHomeShelves().withDownloadPaths(serverUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return kavitaHomeFallback(homeSnapshotStore.read(serverUrl), error)
                .withDownloadPaths(serverUrl)
        }
        homeSnapshotStore.write(serverUrl, shelves)
        cacheBooks(shelves.homeBooks())
        return shelves
    }
    override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
        seriesModule.loadSeriesCatalog(filter, page)
    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? =
        seriesModule.loadSeriesDetail(seriesId)?.let { detail ->
            val books = withDownloadPaths(getServerUrl().orEmpty(), detail.books)
            detail.copy(books = books).also { cacheBooks(books) }
        }
    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo {
        val detail = detailModule.loadBookDetail(book)
        val serverUrl = getServerUrl().orEmpty()
        val localRecord = currentDownloadFor(serverUrl, detail.book)
            ?: currentDownloadFor(serverUrl, book)
        val resolved = localRecord?.let { record ->
            detail.copy(
                book = detail.book.copy(
                    localPath = record.localPath,
                    downloadedSourceUpdatedAtMillis = record.sourceUpdatedAtMillis,
                    isLocalOnlyOverride = false
                )
            )
        } ?: detail
        cacheBooks(listOf(resolved.book))
        return resolved
    }
    override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? {
        val serverUrl = getServerUrl().orEmpty()
        val cached = browserSnapshotStore.read(serverUrl)
            ?.booksByLibraryId
            ?.values
            ?.flatten()
            ?.firstOrNull { candidate ->
                candidate.id == book.id ||
                    (book.fileId != null && candidate.fileId == book.fileId)
            }
            ?: return null
        val resolved = withDownloadPaths(serverUrl, listOf(cached)).single()
        return kavitaCachedBookDetail(resolved)
    }
    override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
        detailModule.setBookUserRating(book, rating)
    override suspend fun loadBookCover(book: BookSummary): ByteArray? = coverModule.loadBookCover(book)
    override suspend fun loadCatalogImage(url: String): ByteArray? = coverModule.loadCatalogImage(url)
    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState =
        readerModule.buildReaderState(
            currentDownloadFor(getServerUrl().orEmpty(), book)?.let { record ->
                book.copy(
                    localPath = record.localPath,
                    downloadedSourceUpdatedAtMillis = record.sourceUpdatedAtMillis
                )
            } ?: book,
            localOnly
        )
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
    override suspend fun downloadBook(
        book: BookSummary,
        storageScopeId: String?,
        onProgress: (Float?) -> Unit
    ): DownloadedBookFile = downloadModule.downloadBook(book, storageScopeId, onProgress)
    override suspend fun deleteLocalCopy(book: BookSummary) = downloadModule.deleteLocalCopy(book)
    override suspend fun deleteLocalCopies(book: BookSummary): Set<String> = downloadModule.deleteLocalCopies(book)
    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? {
        val serverUrl = getServerUrl().orEmpty()
        val snapshot = browserSnapshotStore.read(serverUrl)
        val cachedShelves = homeSnapshotStore.read(serverUrl)
        val localBooks = loadLocalBooks()
        val downloads = currentDownloads(serverUrl)
        if (snapshot == null && cachedShelves == null && localBooks.isEmpty()) return null
        val selected = libraryId ?: getSelectedLibraryId() ?: snapshot?.selectedLibraryId
        val allBooks = mergeKavitaCachedBooks(
            snapshot?.booksByLibraryId.orEmpty(),
            cachedShelves,
            localBooks,
            downloads
        )
        val books = snapshot?.booksByLibraryId?.get(selected).orEmpty()
            .withCurrentDownloads(downloads)
            .ifEmpty { allBooks.filter { selected == null || it.libraryId == selected } }
        val homeShelves = cachedShelves ?: HomeShelfData(
            isServerProvided = true,
            hiddenSections = setOf(
                HomeSection.WANT_TO_READ,
                HomeSection.RECENTLY_ADDED_BOOKS,
                HomeSection.RECENTLY_RELEASED_BOOKS,
                HomeSection.RECENTLY_READ
            ),
            deferInitialCatalogLoad = true
        )
        return BrowserState(
            serverUrl = serverUrl,
            libraries = snapshot?.libraries.orEmpty(),
            selectedLibraryId = selected,
            books = books,
            homeBooks = allBooks,
            homeShelves = homeShelves,
            booksTotal = books.size,
            booksPageSize = books.size,
            isCatalogComplete = snapshot?.booksByLibraryId?.containsKey(selected) == true,
            catalogRefreshedAtMillis = System.currentTimeMillis()
        )
    }

    private suspend fun cacheBooks(books: List<BookSummary>) {
        val serverUrl = getServerUrl().orEmpty()
        books.filter { it.libraryId.isNotBlank() }
            .groupBy(BookSummary::libraryId)
            .forEach { (libraryId, libraryBooks) ->
                browserSnapshotStore.appendBooks(
                    serverUrl,
                    getSelectedLibraryId(),
                    libraryId,
                    libraryBooks
                )
            }
    }

    private suspend fun currentDownloads(serverUrl: String): Map<String, DownloadRecord> {
        val localAccess = LocalDownloadAccess(appContext)
        return downloadStore.readAll(serverUrl)
            .filter { it.status == DownloadRecordStatus.COMPLETE && localAccess.exists(it.localPath) }
            .associateBy { it.fileId }
    }

    private suspend fun withDownloadPaths(
        serverUrl: String,
        books: List<BookSummary>
    ): List<BookSummary> = books.withCurrentDownloads(currentDownloads(serverUrl))

    private suspend fun HomeShelfData.withDownloadPaths(serverUrl: String): HomeShelfData {
        val downloads = currentDownloads(serverUrl)
        return copy(
            booksBySection = booksBySection.mapValues { (_, books) -> books.withCurrentDownloads(downloads) },
            seriesBySection = seriesBySection.mapValues { (_, books) -> books.withCurrentDownloads(downloads) }
        )
    }

    private suspend fun currentDownloadFor(
        serverUrl: String,
        book: BookSummary
    ): DownloadRecord? {
        val localAccess = LocalDownloadAccess(appContext)
        for (fileId in listOfNotNull(book.fileId, book.id).distinct()) {
            val record = runCatching { downloadStore.find(serverUrl, fileId) }.getOrNull() ?: continue
            if (record.status == DownloadRecordStatus.COMPLETE && localAccess.exists(record.localPath)) {
                return record
            }
        }
        return null
    }

    override suspend fun loadLocalBooks(): List<BookSummary> {
        val serverUrl = getServerUrl().orEmpty()
        val snapshotBooks = browserSnapshotStore.read(serverUrl)?.booksByLibraryId?.values.orEmpty()
            .flatten()
        val metadataByKey = snapshotBooks
            .flatMap { book -> listOfNotNull(book.fileId, book.id).map { it to book } }
            .toMap()
        val localAccess = LocalDownloadAccess(appContext)
        return downloadStore.readAll(serverUrl)
            .filter { it.status == DownloadRecordStatus.COMPLETE && localAccess.exists(it.localPath) }
            .map { record ->
                val fallback = BookSummary(
                    libraryId = "",
                    id = record.bookId,
                    fileId = record.fileId,
                    title = record.title,
                    filename = record.filename ?: localAccess.displayName(record.localPath),
                    format = record.mimeType,
                    mediaKind = record.mediaKind,
                    updatedAtMillis = record.sourceUpdatedAtMillis
                )
                (metadataByKey[record.fileId] ?: metadataByKey[record.bookId] ?: fallback).copy(
                    fileId = record.fileId,
                    filename = record.filename ?: metadataByKey[record.fileId]?.filename
                        ?: localAccess.displayName(record.localPath),
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
        val localAccess = LocalDownloadAccess(appContext)
        offlineCacheJob = offlineScope.launch {
            var processed = 0
            var downloaded = 0
            var skipped = 0
            var failed = 0
            books.forEach { book ->
                currentCoroutineContext().ensureActive()
                try {
                    val existing = downloadStore.find(serverUrl, book.fileId ?: book.id)
                    if (existing?.status == DownloadRecordStatus.COMPLETE &&
                        localAccess.exists(existing.localPath)
                    ) {
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
        throw UserFacingException(localizedMessage(R.string.perror_kavita_not_implemented, "Kavita catalog and reading support are not implemented yet."))

    private companion object {
        const val SERVER_URL_KEY = "server_url"
        const val SELECTED_LIBRARY_KEY = "selected_library_id"
    }
}
