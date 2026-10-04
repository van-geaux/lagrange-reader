package com.vangeaux.lagrange.core

import com.vangeaux.lagrange.*
import java.io.File

interface ServerSelectionModule {
    suspend fun getServerUrl(): String?
    suspend fun setServerUrl(serverUrl: String)
    suspend fun clearServer()
    suspend fun getSelectedLibraryId(): String?
    suspend fun setSelectedLibraryId(libraryId: String)
}

interface ServerDetectionModule {
    suspend fun canReachServer(serverUrl: String): Boolean
    suspend fun checkServer(serverUrl: String): ServerCheckResult
}

interface AuthenticatedMediaModule {
    suspend fun requestHeaders(url: String): Map<String, String>
    suspend fun recoverAuthentication(): Boolean
}

interface ProviderSessionModule {
    suspend fun saveCurrentProfileSession(): Boolean
    suspend fun restoreCurrentProfileSession(): Boolean
}

interface LoginModule {
    suspend fun getSessionState(): SessionState
    suspend fun login(username: String, password: String)
    suspend fun clearSession()
}

interface LibraryModule {
    suspend fun loadLibraries(): List<LibrarySummary>
}

interface BookCatalogModule {
    suspend fun loadBooks(libraryId: String): List<BookSummary>
    suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter = BookBrowseFilter()): LibraryBooksPage
    suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int): LibraryBooksPage
    suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage?
    suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage? = null): LibraryBooksPage
    suspend fun searchBooks(query: String): List<BookSummary>
}

interface SeriesCatalogModule {
    suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int = 0): SeriesCatalogPage
    suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo?
}

interface HomeShelfModule {
    suspend fun loadCachedHomeBooks(): List<BookSummary>
    suspend fun loadHomeShelves(): HomeShelfData = HomeShelfData(
        booksBySection = mapOf(HomeSection.RECENTLY_ADDED_BOOKS to loadCachedHomeBooks())
    )
}

interface SmartScopeModule {
    suspend fun loadSmartScopes(): List<SmartScope>
    suspend fun loadSeriesCatalog(scopeId: Long, filter: SeriesCatalogFilter, page: Int = 0): SeriesCatalogPage
    suspend fun loadSeriesDetail(scopeId: Long, seriesId: String): SeriesDetailInfo?
}

interface AuthorModule {
    suspend fun loadAuthorsCatalog(query: String? = null, page: Int = 0): AuthorCatalogPage
    suspend fun loadAuthorBooks(authorId: String, page: Int = 0): AuthorBooksPage?
}

interface BookDetailModule {
    suspend fun loadBookDetail(book: BookSummary): BookDetailInfo?
    suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo?
    suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo
}

interface BookCoverModule {
    suspend fun loadBookCover(book: BookSummary): ByteArray?
    suspend fun loadCatalogImage(url: String): ByteArray?
}

interface ReaderModule {
    suspend fun loadReaderProgress(book: BookSummary, availableFiles: List<BookFileOption> = emptyList()): BookSummary
    suspend fun buildReaderState(book: BookSummary, localOnly: Boolean = false): ReaderState
    suspend fun prepareEpubImageLibrarySource(book: BookSummary, allowRemoteCache: Boolean): EpubImageLibrarySourceResult
    suspend fun restoreActiveReaderState(session: ActiveReaderSession, localOnly: Boolean = false): ReaderState?
}

interface DownloadModule {
    val handlesDownloadsDirectly: Boolean get() = false
    suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit = {}): File
    suspend fun loadAudiobookDownloadFiles(book: BookSummary): List<BookSummary>
    suspend fun deleteLocalCopy(book: BookSummary)
    suspend fun deleteLocalCopies(book: BookSummary): Set<String>
}

interface LocalBooksModule {
    suspend fun loadLocalBooks(): List<BookSummary>
}

interface ReadingStatusModule {
    suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus)
    suspend fun markBookAsRead(book: BookSummary)
    suspend fun resetBookReadingState(book: BookSummary)
}

interface ReadingProgressModule {
    suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?)
    suspend fun pendingProgressCount(): Int
    suspend fun syncPendingProgress(): SyncAttemptResult
}

interface ReadingSessionModule {
    suspend fun loadBookReadingSessions(bookId: String, page: Int = 1, pageSize: Int = 20): BookReadingSessionsResult
    suspend fun loadBookReadingAttempts(bookId: String, page: Int = 1, pageSize: Int = 20): ReadingAttemptsResult
    suspend fun queueReadingSession(payload: ReadingSessionPayload)
    suspend fun pendingReadingSessionCount(): Int
    suspend fun syncPendingReadingSessions(): SyncAttemptResult
}

interface AnnotationModule {
    suspend fun loadAnnotations(filter: AnnotationsFilter, page: Int = 1, pageSize: Int = ANNOTATIONS_PAGE_SIZE): BookAnnotationsPage
    suspend fun createAnnotation(bookId: String, cfi: String?, bookFileId: String?, text: String, color: String, style: String, note: String? = null, chapterTitle: String? = null): BookAnnotation
    suspend fun updateAnnotation(bookId: String, annotationId: String, note: String? = null, color: String? = null, style: String? = null)
    suspend fun deleteAnnotation(bookId: String, annotationId: String)
    suspend fun restoreAnnotation(annotationId: String)
    suspend fun purgeAnnotation(annotationId: String)
    suspend fun pendingMutationCount(): Int
    suspend fun syncPendingMutations(): SyncAttemptResult
}

interface AchievementModule { suspend fun loadAchievements(): AchievementCatalogue }
interface StatisticsModule { suspend fun loadUserStatistics(): UserStatistics }
interface CacheModule {
    suspend fun loadCachedBrowserState(): BrowserState? = null
    suspend fun loadStorageUsage(): StorageUsage
    suspend fun clearAppCache()
    suspend fun loadOfflineCacheStatus(): OfflineCacheStatus
    suspend fun startOfflineCacheUpdate(): Boolean
    suspend fun cancelOfflineCacheUpdate()
    suspend fun clearOfflineCache()
    suspend fun reconfigureBackgroundRefresh()
}

interface BackgroundCacheModule {
   val isAvailable: Boolean
   suspend fun warmCoverCacheBatch(
       expectedServerUrl: String,
       libraryId: String,
       startIndex: Int,
       maxDownloads: Int
   ): Int?
   suspend fun warmOfflineCacheBatch(
       expectedServerUrl: String,
       libraryId: String,
       startIndex: Int,
       maxItems: Int,
       includeDetails: Boolean,
       includeCovers: Boolean
   ): OfflineCacheBatchResult?
}

data class ProviderFeatureAvailability(
    val achievements: Boolean = true,
    val statistics: Boolean = true,
    val smartScopes: Boolean = true,
    val authors: Boolean = true,
    val userRating: Boolean = true,
    val extendedReadingStatuses: Boolean = true,
    val readingStatusOptions: List<BookReadStatus> = BOOK_READ_STATUS_OPTIONS
)

data class ProviderModules(
    val serverSelection: ServerSelectionModule,
    val serverDetection: ServerDetectionModule,
    val login: LoginModule,
    val libraries: LibraryModule,
    val bookCatalog: BookCatalogModule,
    val seriesCatalog: SeriesCatalogModule,
    val home: HomeShelfModule,
    val smartScopes: SmartScopeModule,
    val authors: AuthorModule,
    val bookDetail: BookDetailModule,
    val covers: BookCoverModule,
    val reader: ReaderModule,
    val downloads: DownloadModule,
    val localBooks: LocalBooksModule,
    val readingStatus: ReadingStatusModule,
    val readingProgress: ReadingProgressModule,
    val readingSessions: ReadingSessionModule,
    val annotations: AnnotationModule,
    val achievements: AchievementModule,
    val statistics: StatisticsModule,
    val cache: CacheModule
)
