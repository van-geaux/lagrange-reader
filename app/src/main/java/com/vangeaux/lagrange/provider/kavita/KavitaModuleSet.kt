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
            override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
                repository.loadSeriesCatalog(filter, page)
            override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? =
                repository.loadSeriesDetail(seriesId)
        },
        home = object : HomeShelfModule {
            override suspend fun loadCachedHomeBooks(): List<BookSummary> = repository.loadCachedHomeBooks()
            override suspend fun loadHomeShelves(): HomeShelfData {
                val books = repository.loadCachedHomeBooks()
                val libraryId = repository.getSelectedLibraryId()
                val currentlyReading = books.filter {
                    !it.isRead && (it.progressPercent ?: 0f) > 0f ||
                        (!it.isRead && it.progressPageIndex != null)
                }
                val recentlyRead = books
                    .filter { it.isRead || it.readStatus == BookReadStatus.READ }
                    .sortedByDescending { it.lastReadAtMillis ?: it.updatedAtMillis ?: 0L }
                val onDeck = onDeckBooks(books, limit = null)
                val recentlyAdded = books.sortedByDescending { it.addedAtMillis ?: 0L }
                val bookShelves = buildMap {
                    if (currentlyReading.isNotEmpty()) put(HomeSection.CURRENTLY_READING, currentlyReading)
                    if (onDeck.isNotEmpty()) put(HomeSection.ON_DECK, onDeck)
                    if (recentlyRead.isNotEmpty()) put(HomeSection.RECENTLY_READ, recentlyRead)
                    if (recentlyAdded.isNotEmpty()) put(HomeSection.RECENTLY_ADDED_BOOKS, recentlyAdded)
                }
                val series = aggregateBooksToSeriesCatalog(books).items
                    .sortedByDescending { it.lastAddedAtMillis ?: 0L }
                    .map { series ->
                        BookSummary(
                            libraryId = libraryId.orEmpty(),
                            id = "series:${series.id}",
                            fileId = null,
                            title = series.name,
                            coverUrl = series.coverUrl,
                            seriesId = series.id,
                            seriesName = series.name,
                            genres = series.genres,
                            availableFormats = series.availableFormats,
                            downloadedFormats = series.downloadedFormats,
                            addedAtMillis = series.lastAddedAtMillis ?: 0L,
                            isRead = series.bookCount > 0 && series.readCount >= series.bookCount,
                            readStatus = if (series.bookCount > 0 && series.readCount >= series.bookCount) {
                                BookReadStatus.READ
                            } else {
                                BookReadStatus.UNREAD
                            }
                        )
                    }
                return HomeShelfData(
                    booksBySection = bookShelves,
                    seriesBySection = mapOf(HomeSection.RECENTLY_ADDED_SERIES to series),
                    isServerProvided = true,
                    isAggregatedAcrossLibraries = true
                )
            }
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
            override val handlesDownloadsDirectly: Boolean = true
            override suspend fun downloadBook(
                book: BookSummary,
                storageScopeId: String?,
                onProgress: (Float?) -> Unit
            ) = repository.downloadBook(book, storageScopeId, onProgress)
            override suspend fun loadAudiobookDownloadFiles(book: BookSummary): List<BookSummary> = listOf(book)
            override suspend fun deleteLocalCopy(book: BookSummary) = repository.deleteLocalCopy(book)
            override suspend fun deleteLocalCopies(book: BookSummary): Set<String> = repository.deleteLocalCopies(book)
        },
        localBooks = object : LocalBooksModule {
            override suspend fun loadLocalBooks(): List<BookSummary> = repository.loadLocalBooks()
        },
        readingStatus = object : ReadingStatusModule {
            override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) =
                repository.setBookReadingStatus(book, status)
            override suspend fun markBookAsRead(book: BookSummary) = repository.markBookAsRead(book)
            override suspend fun resetBookReadingState(book: BookSummary) = repository.resetBookReadingState(book)
        },
        readingProgress = object : ReadingProgressModule {
            override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) =
                repository.queueProgress(book, position, pageIndex, progressPercent)
            override suspend fun pendingProgressCount(): Int = repository.pendingProgressCount()
            override suspend fun syncPendingProgress(): SyncAttemptResult = repository.syncPendingProgress()
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
            override suspend fun loadCachedBrowserState(): BrowserState? = repository.loadCachedBrowserState()
            override suspend fun loadStorageUsage(): StorageUsage = repository.loadStorageUsage()
            override suspend fun clearAppCache() = repository.clearAppCache()
            override suspend fun loadOfflineCacheStatus(): OfflineCacheStatus = repository.loadOfflineCacheStatus()
            override suspend fun startOfflineCacheUpdate(): Boolean = repository.startOfflineCacheUpdate()
            override suspend fun cancelOfflineCacheUpdate() = repository.cancelOfflineCacheUpdate()
            override suspend fun clearOfflineCache() = repository.clearOfflineCache()
            override suspend fun reconfigureBackgroundRefresh() = Unit
        }
    )

    val featureAvailability = ProviderFeatureAvailability(
        achievements = false,
        statistics = false,
        smartScopes = false,
        authors = false,
        userRating = false,
        extendedReadingStatuses = false,
        readingStatusOptions = listOf(BookReadStatus.UNREAD, BookReadStatus.READ)
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
        override val isAvailable: Boolean = true

        override suspend fun warmCoverCacheBatch(
            expectedServerUrl: String,
            libraryId: String,
            startIndex: Int,
            maxDownloads: Int
        ): Int? {
            if (repository.getServerUrl() != expectedServerUrl) return null
            val books = repository.loadBooks(libraryId).drop(startIndex).take(maxDownloads)
            books.forEach { repository.loadBookCover(it) }
            return if (startIndex + books.size < repository.loadBooks(libraryId).size) {
                startIndex + books.size
            } else {
                null
            }
        }

        override suspend fun warmOfflineCacheBatch(
            expectedServerUrl: String,
            libraryId: String,
            startIndex: Int,
            maxItems: Int,
            includeDetails: Boolean,
            includeCovers: Boolean
        ): OfflineCacheBatchResult? {
            if (repository.getServerUrl() != expectedServerUrl) return null
            val allBooks = repository.loadBooks(libraryId)
            val books = allBooks.drop(startIndex).take(maxItems)
            var downloaded = 0
            var skipped = 0
            var failed = 0
            books.forEach { book ->
                try {
                    if (includeDetails) repository.loadBookDetail(book)
                    if (includeCovers) repository.loadBookCover(book)
                    repository.downloadBook(book) { }
                    downloaded++
                } catch (_: Throwable) {
                    failed++
                }
            }
            return OfflineCacheBatchResult(
                nextIndex = (startIndex + books.size).takeIf { it < allBooks.size },
                processed = books.size,
                downloaded = downloaded,
                skipped = skipped,
                unavailable = 0,
                failed = failed
            )
        }
    }
}
