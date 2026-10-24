package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

internal class KomgaModuleSet(private val repository: KomgaRepository) {
    val downloads: DownloadModule = object : DownloadModule {
        override val handlesDownloadsDirectly: Boolean = false
        override suspend fun downloadBook(
            book: BookSummary,
            storageScopeId: String?,
            onProgress: (Float?) -> Unit
        ) = repository.downloadBook(book, storageScopeId, onProgress)
        override suspend fun loadAudiobookDownloadFiles(book: BookSummary) = repository.loadAudiobookDownloadFiles(book)
        override suspend fun deleteLocalCopy(book: BookSummary) = repository.deleteLocalCopy(book)
        override suspend fun deleteLocalCopies(book: BookSummary) = repository.deleteLocalCopies(book)
    }

    val localBooks: LocalBooksModule = object : LocalBooksModule {
        override suspend fun loadLocalBooks() = repository.loadLocalBooks()
    }

    val readingStatus: ReadingStatusModule = object : ReadingStatusModule {
        override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) = repository.setBookReadingStatus(book, status)
        override suspend fun markBookAsRead(book: BookSummary) = repository.markBookAsRead(book)
        override suspend fun resetBookReadingState(book: BookSummary) = repository.resetBookReadingState(book)
    }

    val readingProgress: ReadingProgressModule = object : ReadingProgressModule {
        override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) =
            repository.queueProgress(book, position, pageIndex, progressPercent)
        override suspend fun pendingProgressCount(): Int = repository.pendingProgressCount()
        override suspend fun syncPendingProgress(): SyncAttemptResult = repository.syncPendingProgress()
    }

    val bookCatalog: BookCatalogModule = object : BookCatalogModule {
        override suspend fun loadBooks(libraryId: String) = repository.loadBooks(libraryId)
        override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter) = repository.loadBooksPage(libraryId, page, filter)
        override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int) = repository.loadRecentBooksPage(libraryId, section, page)
        override suspend fun loadCachedLibraryCatalog(libraryId: String) = repository.loadCachedLibraryCatalog(libraryId)
        override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?) = repository.refreshLibraryCatalog(libraryId, firstPage)
        override suspend fun searchBooks(query: String) = repository.searchBooks(query)
    }

    val seriesCatalog: SeriesCatalogModule = object : SeriesCatalogModule {
        override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int) = repository.loadSeriesCatalog(filter, page)
        override suspend fun loadSeriesDetail(seriesId: String) = repository.loadSeriesDetail(seriesId)
    }

    val reader: ReaderModule = object : ReaderModule {
        override suspend fun loadReaderProgress(book: BookSummary, availableFiles: List<BookFileOption>) =
            repository.loadReaderProgress(book, availableFiles)
        override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean) = repository.buildReaderState(book, localOnly)
        override suspend fun prepareEpubImageLibrarySource(book: BookSummary, allowRemoteCache: Boolean) =
            repository.prepareEpubImageLibrarySource(book, allowRemoteCache)
        override suspend fun restoreActiveReaderState(session: ActiveReaderSession, localOnly: Boolean) =
            repository.restoreActiveReaderState(session, localOnly)
    }

    val home: HomeShelfModule = object : HomeShelfModule {
        override suspend fun loadCachedHomeBooks() = repository.loadCachedHomeBooks()
        override suspend fun loadHomeShelves() = repository.loadHomeShelves()
    }

    val libraries: LibraryModule = object : LibraryModule {
        override suspend fun loadLibraries() = repository.loadLibraries()
    }

    val covers: BookCoverModule = object : BookCoverModule {
        override suspend fun loadBookCover(book: BookSummary) = repository.loadBookCover(book)
        override suspend fun loadCatalogImage(url: String) = repository.loadCatalogImage(url)
    }

    val bookDetail: BookDetailModule = object : BookDetailModule {
        override suspend fun loadBookDetail(book: BookSummary) = repository.loadBookDetail(book)
        override suspend fun loadCachedBookDetail(book: BookSummary) = repository.loadCachedBookDetail(book)
        override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
            throw UserFacingException("Book ratings are not available for Komga.")
    }

    val readingSessions: ReadingSessionModule = object : ReadingSessionModule {
        override suspend fun loadBookReadingSessions(bookId: String, page: Int, pageSize: Int) = unavailableReadingSessions()
        override suspend fun loadBookReadingAttempts(bookId: String, page: Int, pageSize: Int) = unavailableReadingSessions()
        override suspend fun queueReadingSession(payload: ReadingSessionPayload) = unavailableReadingSessions()
        override suspend fun pendingReadingSessionCount(): Int = 0
        override suspend fun syncPendingReadingSessions(): SyncAttemptResult = unavailableReadingSessions()
    }

    val annotations: AnnotationModule = object : AnnotationModule {
        override suspend fun loadAnnotations(filter: AnnotationsFilter, page: Int, pageSize: Int) = unavailableAnnotations()
        override suspend fun createAnnotation(bookId: String, cfi: String?, bookFileId: String?, text: String, color: String, style: String, note: String?, chapterTitle: String?) = unavailableAnnotations()
        override suspend fun updateAnnotation(bookId: String, annotationId: String, note: String?, color: String?, style: String?) = unavailableAnnotations()
        override suspend fun deleteAnnotation(bookId: String, annotationId: String) = unavailableAnnotations()
        override suspend fun restoreAnnotation(annotationId: String) = unavailableAnnotations()
        override suspend fun purgeAnnotation(annotationId: String) = unavailableAnnotations()
        override suspend fun pendingMutationCount(): Int = 0
        override suspend fun syncPendingMutations(): SyncAttemptResult = unavailableAnnotations()
    }

    val achievements: AchievementModule = object : AchievementModule {
        override suspend fun loadAchievements() = AchievementCatalogue(status = AchievementCatalogueStatus.UNSUPPORTED)
    }

    val statistics: StatisticsModule = object : StatisticsModule {
        override suspend fun loadUserStatistics() = UserStatistics(status = UserStatisticsStatus.UNSUPPORTED)
    }

    val smartScopes: SmartScopeModule = object : SmartScopeModule {
        override suspend fun loadSmartScopes(): List<SmartScope> = unavailableSmartScopes()
        override suspend fun loadSeriesCatalog(scopeId: Long, filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage = unavailableSmartScopes()
        override suspend fun loadSeriesDetail(scopeId: Long, seriesId: String): SeriesDetailInfo? = unavailableSmartScopes()
    }

    val authors: AuthorModule = object : AuthorModule {
        override suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage = unavailableAuthors()
        override suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage? = unavailableAuthors()
    }

    val login: LoginModule = object : LoginModule {
        override suspend fun getSessionState() = repository.getSessionState()
        override suspend fun login(username: String, password: String) = repository.login(username, password)
        override suspend fun clearSession() = repository.clearSession()
    }

    val serverDetection: ServerDetectionModule = object : ServerDetectionModule {
        override suspend fun canReachServer(serverUrl: String) = repository.canReachServer(serverUrl)
        override suspend fun checkServer(serverUrl: String) = repository.checkServer(serverUrl)
    }

    val serverSelection: ServerSelectionModule = object : ServerSelectionModule {
        override suspend fun getServerUrl() = repository.getServerUrl()
        override suspend fun setServerUrl(serverUrl: String) = repository.setServerUrl(serverUrl)
        override suspend fun clearServer() = repository.clearServer()
        override suspend fun getSelectedLibraryId() = repository.getSelectedLibraryId()
        override suspend fun setSelectedLibraryId(libraryId: String) = repository.setSelectedLibraryId(libraryId)
    }

    val cache: CacheModule = object : CacheModule {
        override suspend fun loadCachedBrowserState() = repository.loadCachedBrowserState()
        override suspend fun loadStorageUsage() = repository.loadStorageUsage()
        override suspend fun clearAppCache() = repository.clearAppCache()
        override suspend fun loadOfflineCacheStatus() = repository.loadOfflineCacheStatus()
        override suspend fun startOfflineCacheUpdate() = repository.startOfflineCacheUpdate()
        override suspend fun cancelOfflineCacheUpdate() = repository.cancelOfflineCacheUpdate()
        override suspend fun clearOfflineCache() = repository.clearOfflineCache()
        override suspend fun reconfigureBackgroundRefresh() = repository.reconfigureBackgroundRefresh()
    }

    val featureAvailability = ProviderFeatureAvailability(
        achievements = false,
        statistics = false,
        smartScopes = false,
        authors = false,
        userRating = false,
        extendedReadingStatuses = false,
        readingStatusOptions = KOMGA_READ_STATUS_OPTIONS
    )

    val session: ProviderSessionModule = object : ProviderSessionModule {
        override suspend fun saveCurrentProfileSession(): Boolean {
            repository.saveCurrentProfileSession()
            return true
        }
        override suspend fun restoreCurrentProfileSession(): Boolean = repository.restoreCurrentProfileSession()
    }

    val authenticatedMedia: AuthenticatedMediaModule = object : AuthenticatedMediaModule {
        override suspend fun requestHeaders(url: String) = repository.streamingRequestHeaders(url)
        override suspend fun recoverAuthentication() = repository.recoverStreamingAuthentication()
    }

    val backgroundCache: BackgroundCacheModule = object : BackgroundCacheModule {
        override val isAvailable: Boolean = false
        override suspend fun warmCoverCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxDownloads: Int): Int? = null
        override suspend fun warmOfflineCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxItems: Int, includeDetails: Boolean, includeCovers: Boolean): OfflineCacheBatchResult? = null
    }

    private fun unavailableReadingSessions(): Nothing = throw UserFacingException("Komga reading-session synchronization is not available.")
    private fun unavailableAnnotations(): Nothing = throw UserFacingException("Komga annotations are not available.")
    private fun unavailableSmartScopes(): Nothing = throw UserFacingException("Smart scopes are not available for Komga.")
    private fun unavailableAuthors(): Nothing = throw UserFacingException("Authors are not available for Komga.")
}
