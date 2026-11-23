package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File

internal class GrimmoryBookCoverModule(
    private val serverUrlProvider: suspend () -> String?,
    private val imageLoader: suspend (String) -> ByteArray?
) : BookCoverModule {
    private suspend fun loadNativeImage(path: String): ByteArray? = try {
        imageLoader(path)
    } catch (error: GrimmoryHttpException) {
        if (error.statusCode == 404) null else throw error
    }

    override suspend fun loadBookCover(book: BookSummary): ByteArray? {
        val base = serverUrlProvider().orEmpty()
        if (book.mediaKind == MediaKind.AUDIO) {
            loadNativeImage(grimmoryAudiobookCoverPath(book.id))?.let { return it }
        }
        val url = book.coverUrl ?: grimmoryApiUrl(base, grimmoryBookCoverPath(book.id))
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!sameHttpOrigin(url, base)) return null
        val path = parsed.encodedPath + parsed.encodedQuery?.let { "?$it" }.orEmpty()
        return loadNativeImage(path)
    }

    override suspend fun loadCatalogImage(url: String): ByteArray? {
        val base = serverUrlProvider().orEmpty()
        if (!sameHttpOrigin(url, base)) return null
        val path = url.toHttpUrlOrNull()?.encodedPath ?: return null
        return loadNativeImage(path)
    }
}

/** Grimmory-native modules. Unverified functions fail explicitly rather than falling back to Komga. */
internal class GrimmoryModuleSet(private val repository: GrimmoryRepository) {
    private fun unavailable(feature: String): Nothing = grimmoryUnavailable(feature)
    private val auth = repository.auth
    private val readingProgressModule = GrimmoryProgressModule(
        client = OkHttpClient(),
        auth = auth,
        serverUrlProvider = { repository.getServerUrl() }
    )
    private val formatMetadataEnricher = GrimmoryFormatMetadataEnricher { path -> repository.nativeJson(path) }
    private val homeShelfModule = GrimmoryHomeShelfModule(
        serverUrlProvider = { repository.getServerUrl().orEmpty() },
        nativeJson = { path, query -> repository.nativeJson(path, query) },
        nativeJsonArray = { path, query -> repository.nativeJsonArray(path, query) },
        enrichBooks = { books, base -> enrichBookFormats(books, base) }
    )

    private suspend fun enrichBookFormats(books: List<BookSummary>, base: String): List<BookSummary> =
        formatMetadataEnricher.enrich(
            books = books,
            serverUrl = base,
            profileId = ServerProfileStore(repository.appContext).activeId().orEmpty()
        )

    val serverSelection = object : ServerSelectionModule {
        override suspend fun getServerUrl() = repository.getServerUrl()
        override suspend fun setServerUrl(serverUrl: String) = repository.setServerUrl(serverUrl)
        override suspend fun clearServer() = repository.clearServer()
        override suspend fun getSelectedLibraryId() = repository.getSelectedLibraryId()
        override suspend fun setSelectedLibraryId(libraryId: String) = repository.setSelectedLibraryId(libraryId)
    }
    val serverDetection = object : ServerDetectionModule {
        override suspend fun canReachServer(serverUrl: String) = repository.canReachServer(serverUrl)
        override suspend fun checkServer(serverUrl: String) = repository.checkServer(serverUrl)
    }
    val login: LoginModule = auth
    val session: ProviderSessionModule = auth
    val authenticatedMedia: AuthenticatedMediaModule = auth

    val libraries = object : LibraryModule {
        override suspend fun loadLibraries(): List<LibrarySummary> {
            val items = repository.nativeJsonArray("/api/v1/libraries")
            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    add(LibrarySummary(id, item.optString("name").ifBlank { id }))
                }
            }
        }
    }

    val bookCatalog = object : BookCatalogModule {
        override suspend fun loadBooks(libraryId: String): List<BookSummary> = loadBooksPage(libraryId, 0).items
        override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter): LibraryBooksPage {
            val base = repository.getServerUrl().orEmpty()
            val parsed = parseGrimmoryAppBookPage(
                repository.nativeJson("/api/v1/app/books", grimmoryBookCatalogQuery(libraryId, page, filter)).toString(),
                base
            )
            return parsed.copy(items = enrichBookFormats(parsed.items, base))
        }
        override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int): LibraryBooksPage =
            homeShelfModule.loadRecentBooksPage(libraryId, section, page)
        override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = null
        override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?): LibraryBooksPage =
            firstPage ?: loadBooksPage(libraryId, 0)
        override suspend fun searchBooks(query: String): List<BookSummary> {
            val payload = repository.nativeJson(
                "/api/v1/app/books/search", mapOf("q" to query, "page" to "0", "size" to PAGE_SIZE.toString())
            )
            return enrichBookFormats(
                parseGrimmoryAppBookPage(payload.toString(), repository.getServerUrl().orEmpty()).items,
                repository.getServerUrl().orEmpty()
            )
        }
    }

    val home = object : HomeShelfModule {
        override suspend fun loadCachedHomeBooks(): List<BookSummary> = emptyList()
        override suspend fun loadHomeShelves(): HomeShelfData = homeShelfModule.loadHomeShelves()
    }

    val bookDetail = object : BookDetailModule {
        override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? =
            loadGrimmoryBookDetail(book, repository.getServerUrl().orEmpty()) { path ->
                repository.nativeJson(path)
            }

        override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? = null
        override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
            unavailable("User ratings")
    }

    val covers: BookCoverModule = GrimmoryBookCoverModule(
        serverUrlProvider = { repository.getServerUrl() },
        imageLoader = { path -> repository.nativeImageBytes(path) }
    )

    val reader: ReaderModule = GrimmoryReaderModule(repository, readingProgressModule)

    val downloads: DownloadModule = GrimmoryDownloadModule(repository.appContext, repository)
    val localBooks = object : LocalBooksModule { override suspend fun loadLocalBooks() = repository.loadLocalBooks() }
    val readingStatus: ReadingStatusModule = GrimmoryReadingStatusModule(
        client = OkHttpClient(),
        auth = auth,
        serverUrlProvider = { repository.getServerUrl() }
    )
    val readingProgress: ReadingProgressModule = readingProgressModule
    val readingSessions = object : ReadingSessionModule {
        override suspend fun loadBookReadingSessions(bookId: String, page: Int, pageSize: Int) = unavailable("Reading sessions")
        override suspend fun loadBookReadingAttempts(bookId: String, page: Int, pageSize: Int) = unavailable("Reading attempts")
        override suspend fun queueReadingSession(payload: ReadingSessionPayload) = unavailable("Reading sessions")
        override suspend fun pendingReadingSessionCount(): Int = 0
        override suspend fun syncPendingReadingSessions(): SyncAttemptResult = SyncAttemptResult.Unsupported
    }
    val annotations = object : AnnotationModule {
        override suspend fun loadAnnotations(filter: AnnotationsFilter, page: Int, pageSize: Int) = unavailable("Annotations")
        override suspend fun createAnnotation(bookId: String, cfi: String?, bookFileId: String?, text: String, color: String, style: String, note: String?, chapterTitle: String?) = unavailable("Annotations")
        override suspend fun updateAnnotation(bookId: String, annotationId: String, note: String?, color: String?, style: String?) = unavailable("Annotations")
        override suspend fun deleteAnnotation(bookId: String, annotationId: String) = unavailable("Annotations")
        override suspend fun restoreAnnotation(annotationId: String) = unavailable("Annotations")
        override suspend fun purgeAnnotation(annotationId: String) = unavailable("Annotations")
        override suspend fun pendingMutationCount(): Int = 0
        override suspend fun syncPendingMutations(): SyncAttemptResult = SyncAttemptResult.Unsupported
    }
    val epubBookmarks = object : EpubBookmarkModule {
        override val isAvailable = false
        override val supportsEditing = false
        override suspend fun loadBookmarks(bookId: String, readerFileId: String): List<EpubBookmark> = unavailable("EPUB bookmarks")
        override suspend fun createBookmark(bookId: String, bookFileId: String, position: EpubBookmarkPosition, title: String): EpubBookmark = unavailable("EPUB bookmarks")
        override suspend fun updateBookmark(bookId: String, bookmarkId: String, title: String?, note: String?) = unavailable("EPUB bookmarks")
        override suspend fun deleteBookmark(bookId: String, bookmarkId: String) = unavailable("EPUB bookmarks")
    }
    val seriesCatalog = object : SeriesCatalogModule {
        override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage {
            if (filter.homeSection != HomeSection.RECENTLY_ADDED_SERIES ||
                !filter.query.isNullOrBlank() || !filter.author.isNullOrBlank() ||
                !filter.genre.isNullOrBlank() || !filter.libraryId.isNullOrBlank() ||
                filter.completion != SeriesCompletionFilter.ALL ||
                filter.sort != SeriesSortOption.LAST_ADDED ||
                filter.direction != SortDirection.DESCENDING
            ) unavailable("Filtered series browsing")
            return homeShelfModule.loadRecentlyAddedSeriesPage(page)
        }
        override suspend fun loadSeriesDetail(seriesId: String) = homeShelfModule.loadSeriesDetail(seriesId)
    }
    val collections = object : CollectionCatalogModule {
        override suspend fun loadCollections() = unavailable("Collections")
        override suspend fun loadBooksPage(collectionId: String, page: Int) = unavailable("Collection books")
    }
    val smartScopes = object : SmartScopeModule { override suspend fun loadSmartScopes(): List<SmartScope> = unavailable("Smart Scopes") }
    val authors = object : AuthorModule {
        override suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage = unavailable("Author browsing")
        override suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage? = unavailable("Author browsing")
    }
    val achievements = object : AchievementModule { override suspend fun loadAchievements() = AchievementCatalogue(status = AchievementCatalogueStatus.UNSUPPORTED) }
    val statistics = object : StatisticsModule { override suspend fun loadUserStatistics() = UserStatistics(status = UserStatisticsStatus.UNSUPPORTED) }
    val cache = object : CacheModule {
        override val offlineCacheAvailable = false
        override suspend fun loadStorageUsage() = repository.loadStorageUsage()
        override suspend fun clearAppCache() = repository.clearAppCache()
        override suspend fun loadOfflineCacheStatus() = repository.loadOfflineCacheStatus()
        override suspend fun startOfflineCacheUpdate(): Boolean = false
        override suspend fun cancelOfflineCacheUpdate() = Unit
        override suspend fun clearOfflineCache() = repository.clearOfflineCache()
        override suspend fun reconfigureBackgroundRefresh() = Unit
    }
    val featureAvailability = grimmoryFeatureAvailability()
    val backgroundCache = object : BackgroundCacheModule {
        override val isAvailable = false
        override suspend fun warmCoverCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxDownloads: Int): Int? = null
        override suspend fun warmOfflineCacheBatch(expectedServerUrl: String, libraryId: String, startIndex: Int, maxItems: Int, includeDetails: Boolean, includeCovers: Boolean): OfflineCacheBatchResult? = null
    }

    private companion object { const val PAGE_SIZE = 50 }
}

internal suspend fun loadGrimmoryBookDetail(
    book: BookSummary,
    serverUrl: String,
    nativeJson: suspend (String) -> JSONObject
): BookDetailInfo? {
    val item = nativeJson("/api/v1/app/books/${book.id}")
    val mapped = parseGrimmoryAppBookSummary(item, serverUrl) ?: book
    val availableFormats = grimmoryAvailableFormats(item, listOf(mapped.format to mapped.mediaKind))
    val primaryFileId = item.optLong("primaryFileId", -1L).takeIf { it >= 0L }?.toString()
    val rawFiles = item.optJSONArray("files")?.let { array ->
        (0 until array.length()).mapNotNull(array::optJSONObject)
    }.orEmpty()
    val fileOptions = rawFiles.mapNotNull { file ->
        val fileId = file.optLong("id", -1L).takeIf { it >= 0L }?.toString() ?: return@mapNotNull null
        val name = file.optString("fileName").takeIf(String::isNotBlank)
        val projection = grimmoryFormatProjection(
            file.optString("bookType"),
            name,
            file.optString("extension")
        )
        val isPrimary = fileId == primaryFileId || file.optBoolean("primary", file.optBoolean("isPrimary"))
        val bookType = file.optString("bookType").takeIf(String::isNotBlank)
        val streamUrl = if (projection.mediaKind == MediaKind.AUDIO) {
            grimmoryApiUrl(
                serverUrl,
                grimmoryAudiobookStreamPath(
                    mapped.id,
                    bookType = bookType.takeUnless { isPrimary }
                )
            )
        } else mapped.streamUrl
        val fileBook = mapped.copy(
            fileId = fileId,
            filename = name ?: mapped.filename,
            format = projection.format ?: mapped.format,
            mediaKind = projection.mediaKind,
            streamUrl = streamUrl,
            downloadUrl = grimmoryDownloadEndpoint(
                serverUrl,
                mapped.id,
                fileId.takeUnless { isPrimary }
            ),
            availableFormats = availableFormats
        )
        BookFileOption(
            book = fileBook,
            filename = name,
            sizeBytes = file.optLong("fileSizeKb", -1L).takeIf { it >= 0L }?.times(1024L),
            role = if (isPrimary) "primary" else null,
            updatedAtMillis = parseGrimmoryInstant(file.optString("addedOn"))
        )
    }.ifEmpty {
        listOf(BookFileOption(mapped.copy(availableFormats = availableFormats), mapped.filename))
    }
    val selectedFile = fileOptions.firstOrNull { it.fileId == book.fileId }
        ?: fileOptions.firstOrNull { it.role == "primary" }
        ?: fileOptions.first()
    val selectedRawFile = rawFiles.firstOrNull {
        it.optLong("id", -1L).takeIf { id -> id >= 0L }?.toString() == selectedFile.fileId
    }
    val selectedBookType = selectedRawFile?.optString("bookType")
        ?.takeIf(String::isNotBlank)
        .takeUnless { selectedFile.role == "primary" }
    val selectedBook = selectedFile.book
    val audiobookInfo = if (selectedBook.mediaKind == MediaKind.AUDIO) {
        nativeJson(grimmoryAudiobookInfoPath(mapped.id, selectedBookType))
    } else null
    val folderBased = audiobookInfo?.optBoolean("folderBased") == true
    val audioChapters = audiobookInfo?.let(::parseGrimmoryAudiobookChapters).orEmpty()
    val trackFiles = audiobookInfo?.takeIf { folderBased }
        ?.let { parseGrimmoryAudiobookTracks(selectedBook, it, serverUrl) }
        .orEmpty()
    if (folderBased && trackFiles.isEmpty()) {
        throw UserFacingException("Grimmory returned incomplete folder-audiobook track metadata.")
    }
    val files = if (folderBased) trackFiles else fileOptions
    val durationSeconds = audiobookInfo?.optLong("durationMs", -1L)
        ?.takeIf { it >= 0L }?.div(1000L)
    val detailBook = (trackFiles.firstOrNull()?.book ?: selectedBook).copy(audioChapters = audioChapters)
    return BookDetailInfo(
        book = detailBook,
        libraryName = item.optString("libraryName").takeIf(String::isNotBlank),
        subtitle = item.optString("subtitle").takeIf(String::isNotBlank),
        synopsis = item.optString("description").takeIf(String::isNotBlank),
        publisher = item.optString("publisher").takeIf(String::isNotBlank),
        publishedDate = item.optString("publishedDate").takeIf(String::isNotBlank),
        language = item.optString("language").takeIf(String::isNotBlank),
        pageCount = item.optInt("pageCount", -1).takeIf { it >= 0 },
        isbn13 = item.optString("isbn13").takeIf(String::isNotBlank),
        genres = item.optJSONArray("categories").toStringList(),
        fileCount = files.size,
        availableFiles = files,
        totalSizeBytes = files.mapNotNull { it.sizeBytes }.takeIf { it.size == files.size }?.sum(),
        durationSeconds = durationSeconds,
        audioChapters = audioChapters
    )
}

internal fun parseGrimmoryBookArray(items: JSONArray, base: String): List<BookSummary> = buildList {
    for (index in 0 until items.length()) {
        items.optJSONObject(index)?.let { parseGrimmoryAppBookSummary(it, base) }?.let(::add)
    }
}

private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
    (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
}.orEmpty()

private fun parseGrimmoryInstant(value: String?): Long? =
    value?.takeIf(String::isNotBlank)?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
