package com.vangeaux.lagrange.provider.opds

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

/** OPDS-only unsupported capabilities; prevents compatibility fallbacks from calling BookOrbit APIs. */
internal class OpdsModuleSet(private val repository: OpdsRepository) {
    private fun unavailable(capability: String): Nothing = throw UserFacingException(
        "$capability is not available for this OPDS feed.",
        UserFacingReason.CAPABILITY_UNAVAILABLE
    )

    val seriesCatalog = object : SeriesCatalogModule {
        override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int) = unavailable("Series catalogs")
        override suspend fun loadSeriesDetail(seriesId: String) = unavailable("Series details")
    }

    val home = object : HomeShelfModule {
        override val deferInitialCatalogLoad: Boolean = repository.legacyCwa
        override suspend fun loadCachedHomeBooks() = repository.loadCachedHomeBooks()
        override suspend fun loadHomeShelves() = repository.loadHomeShelves()
    }

    val smartScopes = object : SmartScopeModule {
        override suspend fun loadSmartScopes(): List<SmartScope> = unavailable("Smart scopes")
        override suspend fun loadBooksPage(scopeId: Long, page: Int): LibraryBooksPage = unavailable("Smart scopes")
    }

    val collections = object : CollectionCatalogModule {
        override suspend fun loadCollections(): List<BookCollection> = unavailable("Collections")
        override suspend fun loadBooksPage(collectionId: String, page: Int): LibraryBooksPage = unavailable("Collections")
    }

    val authors = object : AuthorModule {
        override suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage = unavailable("Authors")
        override suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage? = unavailable("Author books")
    }

    val readingSessions = object : ReadingSessionModule {
        override suspend fun loadBookReadingSessions(bookId: String, page: Int, pageSize: Int): BookReadingSessionsResult = unavailable("Reading sessions")
        override suspend fun loadBookReadingAttempts(bookId: String, page: Int, pageSize: Int): ReadingAttemptsResult = unavailable("Reading attempts")
        override suspend fun queueReadingSession(payload: ReadingSessionPayload) = unavailable("Reading sessions")
        override suspend fun pendingReadingSessionCount() = 0
        override suspend fun syncPendingReadingSessions() = SyncAttemptResult.Unsupported
    }

    val annotations = object : AnnotationModule {
        override suspend fun loadAnnotations(filter: AnnotationsFilter, page: Int, pageSize: Int): BookAnnotationsPage = unavailable("Annotations")
        override suspend fun createAnnotation(bookId: String, cfi: String?, bookFileId: String?, text: String, color: String, style: String, note: String?, chapterTitle: String?): BookAnnotation = unavailable("Annotations")
        override suspend fun updateAnnotation(bookId: String, annotationId: String, note: String?, color: String?, style: String?) = unavailable("Annotations")
        override suspend fun deleteAnnotation(bookId: String, annotationId: String) = unavailable("Annotations")
        override suspend fun restoreAnnotation(annotationId: String) = unavailable("Annotations")
        override suspend fun purgeAnnotation(annotationId: String) = unavailable("Annotations")
        override suspend fun pendingMutationCount() = 0
        override suspend fun syncPendingMutations() = SyncAttemptResult.Unsupported
    }

    val epubBookmarks = object : EpubBookmarkModule {
        override val isAvailable = false
        override val supportsEditing = false
        override suspend fun loadBookmarks(bookId: String, readerFileId: String): List<EpubBookmark> = unavailable("EPUB bookmarks")
        override suspend fun createBookmark(bookId: String, bookFileId: String, position: EpubBookmarkPosition, title: String): EpubBookmark = unavailable("EPUB bookmarks")
        override suspend fun updateBookmark(bookId: String, bookmarkId: String, title: String?, note: String?) = unavailable("EPUB bookmarks")
        override suspend fun deleteBookmark(bookId: String, bookmarkId: String) = unavailable("EPUB bookmarks")
    }

    val achievements = object : AchievementModule {
        override suspend fun loadAchievements() = AchievementCatalogue(status = AchievementCatalogueStatus.UNSUPPORTED)
    }

    val statistics = object : StatisticsModule {
        override suspend fun loadUserStatistics() = UserStatistics(status = UserStatisticsStatus.UNSUPPORTED)
    }

    val cache = object : CacheModule {
        override val offlineCacheAvailable = false
        override suspend fun loadCachedBrowserState(): BrowserState? = null
        override suspend fun loadStorageUsage() = repository.loadStorageUsage()
        override suspend fun clearAppCache() = repository.clearAppCache()
        override suspend fun loadOfflineCacheStatus() = repository.loadOfflineCacheStatus()
        override suspend fun startOfflineCacheUpdate() = false
        override suspend fun cancelOfflineCacheUpdate() = Unit
        override suspend fun clearOfflineCache() = repository.clearOfflineCache()
        override suspend fun reconfigureBackgroundRefresh() = Unit
    }

    val session = object : ProviderSessionModule {
        override suspend fun saveCurrentProfileSession() = repository.saveCurrentProfileSession()
        override suspend fun restoreCurrentProfileSession() = repository.restoreCurrentProfileSession()
    }

    val backgroundCache = object : BackgroundCacheModule {
        override val isAvailable = false
        override suspend fun warmCoverCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxDownloads: Int): Int? = null
        override suspend fun warmOfflineCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxItems: Int, includeDetails: Boolean, includeCovers: Boolean): OfflineCacheBatchResult? = null
    }
}
