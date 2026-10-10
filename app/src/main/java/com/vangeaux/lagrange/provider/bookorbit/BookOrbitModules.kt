package com.vangeaux.lagrange.provider.bookorbit

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

class BookOrbitModuleSet(private val source: BookOrbitDataSource) {
    val modules: ProviderModules = ProviderModules(
        serverSelection = object : ServerSelectionModule {
            override suspend fun getServerUrl() = source.getServerUrl()
            override suspend fun setServerUrl(serverUrl: String) = source.setServerUrl(serverUrl)
            override suspend fun clearServer() = source.clearServer()
            override suspend fun getSelectedLibraryId() = source.getSelectedLibraryId()
            override suspend fun setSelectedLibraryId(libraryId: String) = source.setSelectedLibraryId(libraryId)
        },
        serverDetection = object : ServerDetectionModule {
            override suspend fun canReachServer(serverUrl: String) = source.canReachServer(serverUrl)
            override suspend fun checkServer(serverUrl: String) = source.checkServer(serverUrl)
        },
        login = object : LoginModule {
            override suspend fun getSessionState() = source.getSessionState()
            override suspend fun login(username: String, password: String) = source.login(username, password)
            override suspend fun clearSession() = source.clearSession()
        },
        libraries = object : LibraryModule {
            override suspend fun loadLibraries() = source.loadLibraries()
        },
        bookCatalog = object : BookCatalogModule {
            override suspend fun loadBooks(libraryId: String) = source.loadBooks(libraryId)
            override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter) = source.loadBooksPage(libraryId, page, filter)
            override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int) = source.loadRecentBooksPage(libraryId, section, page)
            override suspend fun loadCachedLibraryCatalog(libraryId: String) = source.loadCachedLibraryCatalog(libraryId)
            override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?) = source.refreshLibraryCatalog(libraryId, firstPage)
            override suspend fun searchBooks(query: String) = source.searchBooks(query)
        },
        seriesCatalog = object : SeriesCatalogModule {
            override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int) = source.loadSeriesCatalog(filter, page)
            override suspend fun loadSeriesDetail(seriesId: String) = source.loadSeriesDetail(seriesId)
        },
        home = object : HomeShelfModule {
            override suspend fun loadCachedHomeBooks() = source.loadCachedHomeBooks()
        },
        smartScopes = object : SmartScopeModule {
            override suspend fun loadSmartScopes() = source.loadSmartScopes()
            override suspend fun loadSeriesCatalog(scopeId: Long, filter: SeriesCatalogFilter, page: Int) = source.loadSmartScopeSeriesCatalog(scopeId, filter, page)
            override suspend fun loadSeriesDetail(scopeId: Long, seriesId: String) = source.loadSmartScopeSeriesDetail(scopeId, seriesId)
        },
        authors = object : AuthorModule {
            override suspend fun loadAuthorsCatalog(query: String?, page: Int) = source.loadAuthorsCatalog(query, page)
            override suspend fun loadAuthorBooks(authorId: String, page: Int) = source.loadAuthorBooks(authorId, page)
        },
        bookDetail = object : BookDetailModule {
            override suspend fun loadBookDetail(book: BookSummary) = source.loadBookDetail(book)
            override suspend fun loadCachedBookDetail(book: BookSummary) = source.loadCachedBookDetail(book)
            override suspend fun setBookUserRating(book: BookSummary, rating: Int?) = source.setBookUserRating(book, rating)
        },
        covers = object : BookCoverModule {
            override suspend fun loadBookCover(book: BookSummary) = source.loadBookCover(book)
            override suspend fun loadCatalogImage(url: String) = source.loadCatalogImage(url)
        },
        reader = object : ReaderModule {
            override suspend fun loadReaderProgress(book: BookSummary, availableFiles: List<BookFileOption>) = source.loadReaderProgress(book, availableFiles)
            override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean) = source.buildReaderState(book, localOnly)
            override suspend fun prepareEpubImageLibrarySource(book: BookSummary, allowRemoteCache: Boolean) = source.prepareEpubImageLibrarySource(book, allowRemoteCache)
            override suspend fun restoreActiveReaderState(session: ActiveReaderSession, localOnly: Boolean) = source.restoreActiveReaderState(session, localOnly)
        },
        downloads = object : DownloadModule {
            override suspend fun downloadBook(
                book: BookSummary,
                storageScopeId: String?,
                onProgress: (Float?) -> Unit
            ) = source.downloadBook(book, storageScopeId, onProgress)
            override suspend fun loadAudiobookDownloadFiles(book: BookSummary) = source.loadAudiobookDownloadFiles(book)
            override suspend fun deleteLocalCopy(book: BookSummary) = source.deleteLocalCopy(book)
            override suspend fun deleteLocalCopies(book: BookSummary) = source.deleteLocalCopies(book)
        },
        localBooks = object : LocalBooksModule {
            override suspend fun loadLocalBooks() = source.loadLocalBooks()
        },
        readingStatus = object : ReadingStatusModule {
            override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) = source.setBookReadingStatus(book, status)
            override suspend fun markBookAsRead(book: BookSummary) = source.markBookAsRead(book)
            override suspend fun resetBookReadingState(book: BookSummary) = source.resetBookReadingState(book)
        },
        readingProgress = object : ReadingProgressModule {
            override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = source.queueProgress(book, position, pageIndex, progressPercent)
            override suspend fun pendingProgressCount() = source.pendingProgressCount()
            override suspend fun syncPendingProgress() = source.syncPendingProgress()
        },
        readingSessions = object : ReadingSessionModule {
            override suspend fun loadBookReadingSessions(bookId: String, page: Int, pageSize: Int) = source.loadBookReadingSessions(bookId, page, pageSize)
            override suspend fun loadBookReadingAttempts(bookId: String, page: Int, pageSize: Int) = source.loadBookReadingAttempts(bookId, page, pageSize)
            override suspend fun queueReadingSession(payload: ReadingSessionPayload) = source.queueReadingSession(payload)
            override suspend fun pendingReadingSessionCount() = source.pendingReadingSessionCount()
            override suspend fun syncPendingReadingSessions() = source.syncPendingReadingSessions()
        },
        annotations = object : AnnotationModule {
            override suspend fun loadAnnotations(filter: AnnotationsFilter, page: Int, pageSize: Int) = source.loadAnnotations(filter, page, pageSize)
            override suspend fun createAnnotation(bookId: String, cfi: String?, bookFileId: String?, text: String, color: String, style: String, note: String?, chapterTitle: String?) = source.createAnnotation(bookId, cfi, bookFileId, text, color, style, note, chapterTitle)
            override suspend fun updateAnnotation(bookId: String, annotationId: String, note: String?, color: String?, style: String?) = source.updateAnnotation(bookId, annotationId, note, color, style)
            override suspend fun deleteAnnotation(bookId: String, annotationId: String) = source.deleteAnnotation(bookId, annotationId)
            override suspend fun restoreAnnotation(annotationId: String) = source.restoreAnnotation(annotationId)
            override suspend fun purgeAnnotation(annotationId: String) = source.purgeAnnotation(annotationId)
            override suspend fun pendingMutationCount() = source.pendingAnnotationMutationCount()
            override suspend fun syncPendingMutations() = source.syncPendingAnnotationMutations()
        },
        achievements = object : AchievementModule { override suspend fun loadAchievements() = source.loadAchievements() },
        statistics = object : StatisticsModule { override suspend fun loadUserStatistics() = source.loadUserStatistics() },
        cache = object : CacheModule {
            override suspend fun loadStorageUsage() = source.loadStorageUsage()
            override suspend fun clearAppCache() = source.clearAppCache()
            override suspend fun loadOfflineCacheStatus() = source.loadOfflineCacheStatus()
            override suspend fun startOfflineCacheUpdate() = source.startOfflineCacheUpdate()
            override suspend fun cancelOfflineCacheUpdate() = source.cancelOfflineCacheUpdate()
            override suspend fun clearOfflineCache() = source.clearOfflineCache()
            override suspend fun reconfigureBackgroundRefresh() = source.reconfigureBackgroundRefresh()
        }
    )
}
