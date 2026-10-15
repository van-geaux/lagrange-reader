package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ActiveReaderSession
import java.io.File

internal class KavitaRepository(context: Context) : BookOrbitDataSource, ProfileSessionAware {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("kavita_connection", Context.MODE_PRIVATE)
    private val authModule = KavitaAuthModuleImpl(appContext)
    private val libraryModule = KavitaLibraryModuleImpl(authModule)
    private val catalogModule = KavitaBookCatalogModuleImpl(authModule)
    private val detailModule = KavitaBookDetailModuleImpl(authModule)
    private val seriesModule = KavitaSeriesCatalogModuleImpl(catalogModule, detailModule)
    private val coverModule = KavitaCoverModuleImpl(authModule)
    private val readerModule = KavitaReaderModuleImpl(authModule)
    private val readingProgressModule = KavitaReadingProgressModuleImpl(authModule)
    private val readingStatusModule = KavitaReadingStatusModuleImpl(authModule)
    private val downloadModule = KavitaDownloadModuleImpl(appContext, authModule)

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
        if (password.isNotEmpty()) {
            throw UserFacingException("Kavita requires an Auth Key, not a username and password.")
        }
        authModule.authenticate(getServerUrl().orEmpty(), username)
    }

    override suspend fun loadLibraries(): List<LibrarySummary> = libraryModule.loadLibraries()

    override suspend fun loadBooks(libraryId: String): List<BookSummary> = catalogModule.loadBooks(libraryId)
    override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter) =
        catalogModule.loadBooksPage(libraryId, page, filter)
    override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int) =
        catalogModule.loadRecentBooksPage(libraryId, section, page)
    override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?) =
        catalogModule.refreshLibraryCatalog(libraryId, firstPage)
    override suspend fun searchBooks(query: String) = catalogModule.searchBooks(query)
    override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
        seriesModule.loadSeriesCatalog(filter, page)
    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? =
        seriesModule.loadSeriesDetail(seriesId)
    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo = detailModule.loadBookDetail(book)
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
    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = null
    override suspend fun saveActiveReader(book: BookSummary, launchMode: ReaderLaunchMode) = unavailable<Unit>()
    override suspend fun clearActiveReader() = unavailable<Unit>()
    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = null


    override suspend fun canReachServer(serverUrl: String): Boolean =
        checkServer(serverUrl) == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult =
        authModule.checkServer(serverUrl)

    internal suspend fun authenticate(apiKey: String) {
        authModule.authenticate(getServerUrl().orEmpty(), apiKey)
    }

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
