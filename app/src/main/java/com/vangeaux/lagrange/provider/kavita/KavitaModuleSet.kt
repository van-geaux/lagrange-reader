package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

internal class KavitaModuleSet(private val repository: KavitaRepository) {
    private fun unavailable(): Nothing =
        throw UserFacingException("Kavita catalog and reading support are not implemented yet.")

    val modules: ProviderModules = ProviderModules(
        serverSelection = object : ServerSelectionModule {
            override suspend fun getServerUrl() = repository.getServerUrl()
            override suspend fun setServerUrl(serverUrl: String) = repository.setServerUrl(serverUrl)
            override suspend fun clearServer() = repository.clearServer()
            override suspend fun getSelectedLibraryId() = repository.getSelectedLibraryId()
            override suspend fun setSelectedLibraryId(libraryId: String) = repository.setSelectedLibraryId(libraryId)
        },
        serverDetection = object : ServerDetectionModule {
            override suspend fun canReachServer(serverUrl: String) = repository.canReachServer(serverUrl)
            override suspend fun checkServer(serverUrl: String) = repository.checkServer(serverUrl)
        },
        login = object : LoginModule {
            override suspend fun getSessionState() = repository.getSessionState()
            override suspend fun login(username: String, password: String) = repository.login(username, password)
            override suspend fun clearSession() = repository.clearSession()
        },
        libraries = object : LibraryModule {
            override suspend fun loadLibraries() = repository.loadLibraries()
        },
        bookCatalog = object : BookCatalogModule {
            override suspend fun loadBooks(libraryId: String): List<BookSummary> = repository.loadBooks(libraryId)
            override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter): LibraryBooksPage =
                repository.loadBooksPage(libraryId, page, filter)
            override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int): LibraryBooksPage =
                repository.loadRecentBooksPage(libraryId, section, page)
            override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = null
            override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?): LibraryBooksPage =
                repository.refreshLibraryCatalog(libraryId, firstPage)
            override suspend fun searchBooks(query: String): List<BookSummary> = repository.searchBooks(query)
        },
        seriesCatalog = object : SeriesCatalogModule {
            override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage = unavailable()
            override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? = unavailable()
        },
        home = object : HomeShelfModule {
            override suspend fun loadCachedHomeBooks(): List<BookSummary> = unavailable()
            override suspend fun loadHomeShelves(): HomeShelfData = unavailable()
        },
        smartScopes = object : SmartScopeModule {
            override suspend fun loadSmartScopes(): List<SmartScope> = unavailable()
            override suspend fun loadSeriesCatalog(scopeId: Long, filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage = unavailable()
            override suspend fun loadSeriesDetail(scopeId: Long, seriesId: String): SeriesDetailInfo? = unavailable()
        },
        authors = object : AuthorModule {
            override suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage = unavailable()
            override suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage? = unavailable()
        },
        bookDetail = object : BookDetailModule {
            override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? = repository.loadBookDetail(book)
            override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? = repository.loadCachedBookDetail(book)
            override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
                repository.setBookUserRating(book, rating)
        },
        covers = object : BookCoverModule {
            override suspend fun loadBookCover(book: BookSummary): ByteArray? = repository.loadBookCover(book)
            override suspend fun loadCatalogImage(url: String): ByteArray? = repository.loadCatalogImage(url)
        },
        reader = object : ReaderModule {
            override suspend fun loadReaderProgress(book: BookSummary, availableFiles: List<BookFileOption>): BookSummary =
                repository.loadReaderProgress(book, availableFiles)
            override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState =
                repository.buildReaderState(book, localOnly)
            override suspend fun prepareEpubImageLibrarySource(book: BookSummary, allowRemoteCache: Boolean): EpubImageLibrarySourceResult =
                repository.prepareEpubImageLibrarySource(book, allowRemoteCache)
            override suspend fun restoreActiveReaderState(session: ActiveReaderSession, localOnly: Boolean): ReaderState? =
                repository.restoreActiveReaderState(session, localOnly)
        },
        downloads = object : DownloadModule {
            override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit) = unavailable()
            override suspend fun loadAudiobookDownloadFiles(book: BookSummary): List<BookSummary> = unavailable()
            override suspend fun deleteLocalCopy(book: BookSummary) = unavailable()
            override suspend fun deleteLocalCopies(book: BookSummary): Set<String> = unavailable()
        },
        localBooks = object : LocalBooksModule {
            override suspend fun loadLocalBooks(): List<BookSummary> = unavailable()
        },
        readingStatus = object : ReadingStatusModule {
            override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) = unavailable()
            override suspend fun markBookAsRead(book: BookSummary) = unavailable()
            override suspend fun resetBookReadingState(book: BookSummary) = unavailable()
        },
        readingProgress = object : ReadingProgressModule {
            override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = unavailable()
            override suspend fun pendingProgressCount(): Int = 0
            override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Unsupported
        },
        readingSessions = object : ReadingSessionModule {
            override suspend fun loadBookReadingSessions(bookId: String, page: Int, pageSize: Int): BookReadingSessionsResult = unavailable()
            override suspend fun loadBookReadingAttempts(bookId: String, page: Int, pageSize: Int): ReadingAttemptsResult = unavailable()
            override suspend fun queueReadingSession(payload: ReadingSessionPayload) = unavailable()
            override suspend fun pendingReadingSessionCount(): Int = 0
            override suspend fun syncPendingReadingSessions(): SyncAttemptResult = SyncAttemptResult.Unsupported
        },
        annotations = object : AnnotationModule {
            override suspend fun loadAnnotations(filter: AnnotationsFilter, page: Int, pageSize: Int): BookAnnotationsPage = unavailable()
            override suspend fun createAnnotation(bookId: String, cfi: String?, bookFileId: String?, text: String, color: String, style: String, note: String?, chapterTitle: String?): BookAnnotation = unavailable()
            override suspend fun updateAnnotation(bookId: String, annotationId: String, note: String?, color: String?, style: String?) = unavailable()
            override suspend fun deleteAnnotation(bookId: String, annotationId: String) = unavailable()
            override suspend fun restoreAnnotation(annotationId: String) = unavailable()
            override suspend fun purgeAnnotation(annotationId: String) = unavailable()
            override suspend fun pendingMutationCount(): Int = 0
            override suspend fun syncPendingMutations(): SyncAttemptResult = SyncAttemptResult.Unsupported
        },
        achievements = object : AchievementModule {
            override suspend fun loadAchievements() = AchievementCatalogue(status = AchievementCatalogueStatus.UNSUPPORTED)
        },
        statistics = object : StatisticsModule {
            override suspend fun loadUserStatistics() = UserStatistics(status = UserStatisticsStatus.UNSUPPORTED)
        },
        cache = object : CacheModule {
            override suspend fun loadCachedBrowserState(): BrowserState? = null
            override suspend fun loadStorageUsage(): StorageUsage = unavailable()
            override suspend fun clearAppCache() = unavailable()
            override suspend fun loadOfflineCacheStatus(): OfflineCacheStatus = unavailable()
            override suspend fun startOfflineCacheUpdate(): Boolean = unavailable()
            override suspend fun cancelOfflineCacheUpdate() = unavailable()
            override suspend fun clearOfflineCache() = unavailable()
            override suspend fun reconfigureBackgroundRefresh() = unavailable()
        }
    )

    val featureAvailability = ProviderFeatureAvailability(
        achievements = false,
        statistics = false,
        smartScopes = false,
        authors = false,
        userRating = false,
        extendedReadingStatuses = false,
        readingStatusOptions = emptyList()
    )

    val session: ProviderSessionModule = object : ProviderSessionModule {
        override suspend fun saveCurrentProfileSession(): Boolean {
            repository.saveCurrentProfileSession()
            return true
        }

        override suspend fun restoreCurrentProfileSession(): Boolean =
            repository.restoreCurrentProfileSession()
    }

    val authenticatedMedia: AuthenticatedMediaModule = object : AuthenticatedMediaModule {
        override suspend fun requestHeaders(url: String) = repository.requestHeaders()
        override suspend fun recoverAuthentication() = repository.restoreCurrentProfileSession()
    }

    val backgroundCache: BackgroundCacheModule = object : BackgroundCacheModule {
        override val isAvailable: Boolean = false
        override suspend fun warmCoverCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxDownloads: Int): Int? = null
        override suspend fun warmOfflineCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxItems: Int, includeDetails: Boolean, includeCovers: Boolean): OfflineCacheBatchResult? = null
    }
}
