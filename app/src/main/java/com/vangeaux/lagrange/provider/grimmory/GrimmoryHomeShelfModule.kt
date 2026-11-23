package com.vangeaux.lagrange.provider.grimmory

import android.util.Log
import com.vangeaux.lagrange.*
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Native, bounded Home projections from Grimmory's app books and series routes. */
internal class GrimmoryHomeShelfModule(
    private val serverUrlProvider: suspend () -> String,
    private val nativeJson: suspend (String, Map<String, String>) -> JSONObject,
    private val nativeJsonArray: suspend (String, Map<String, String>) -> JSONArray,
    private val logShelfFailure: (HomeSection, String) -> Unit = { section, reason ->
        Log.w("GrimmoryHomeShelf", "${section.name} shelf unavailable ($reason)")
    },
    private val enrichBooks: suspend (List<BookSummary>, String) -> List<BookSummary> = { books, _ -> books }
) {
    constructor(repository: GrimmoryRepository) : this(
        serverUrlProvider = { repository.getServerUrl().orEmpty() },
        nativeJson = { path, query -> repository.nativeJson(path, query) },
        nativeJsonArray = { path, query -> repository.nativeJsonArray(path, query) }
    )

    suspend fun loadHomeShelves(): HomeShelfData {
        val base = serverUrlProvider()
        val booksBySection = linkedMapOf<HomeSection, List<BookSummary>>()

        val continueReading = safelyLoad(HomeSection.CURRENTLY_READING) {
            loadContinueBooks("/api/v1/app/books/continue-reading", base)
        }
        val continueListening = safelyLoad(HomeSection.CURRENTLY_READING) {
            loadContinueBooks("/api/v1/app/books/continue-listening", base)
        }
        val nativeInProgressBooks = continueReading.orEmpty() + continueListening.orEmpty()
        val catalogProgressBooks = safelyLoad(HomeSection.CURRENTLY_READING) {
            loadUnfinishedProgressBooks(libraryId = "", base = base)
        }.orEmpty()
        val inProgressBooks = (nativeInProgressBooks + catalogProgressBooks)
            .distinctBy { it.libraryId to it.id }
            .sortedWith(
                compareByDescending<BookSummary> { it.lastReadAtMillis != null }
                    .thenByDescending { it.lastReadAtMillis ?: 0L }
            )
        if (continueReading != null || continueListening != null || inProgressBooks.isNotEmpty()) {
            booksBySection[HomeSection.CURRENTLY_READING] = inProgressBooks
        }
        val recentReadPage = safelyLoad(HomeSection.RECENTLY_READ) {
            loadStatusBooks("", 0, BookReadFilter.FINISHED, base)
        }
        val recentlyAddedSeries = safelyLoad(HomeSection.RECENTLY_ADDED_SERIES) {
            loadRecentlyAddedSeriesPage(page = 0).items.map(::seriesCardBook)
        }
        recentReadPage?.let { booksBySection[HomeSection.RECENTLY_READ] = it.items }
        safelyLoad(HomeSection.ON_DECK) {
            loadOnDeckBooks(limit = HOME_ON_DECK_LIMIT, firstReadPage = recentReadPage)
        }?.let { booksBySection[HomeSection.ON_DECK] = it }
        safelyLoad(HomeSection.RECENTLY_ADDED_BOOKS) { loadRecentlyAddedBooks(base) }
            ?.let { booksBySection[HomeSection.RECENTLY_ADDED_BOOKS] = it }

        return HomeShelfData(
            booksBySection = booksBySection,
            seriesBySection = recentlyAddedSeries?.takeIf { it.isNotEmpty() }
                ?.let { mapOf(HomeSection.RECENTLY_ADDED_SERIES to it) }.orEmpty(),
            hiddenSections = setOf(HomeSection.WANT_TO_READ, HomeSection.RECENTLY_RELEASED_BOOKS),
            isServerProvided = true,
            isAggregatedAcrossLibraries = true
        )
    }

    suspend fun loadRecentlyAddedSeriesPage(page: Int): SeriesCatalogPage {
        val safePage = page.coerceAtLeast(0)
        val payload = nativeJson(
            "/api/v1/app/series",
            mapOf(
                "page" to safePage.toString(),
                "size" to RECENT_SERIES_PAGE_SIZE.toString(),
                "sort" to "recentlyAdded",
                "dir" to "desc"
            )
        )
        return parseGrimmoryRecentlyAddedSeriesPage(payload, safePage, serverUrlProvider())
    }

    suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? {
        if (seriesId.isBlank()) return null
        val base = serverUrlProvider()
        val encodedName = URLEncoder.encode(seriesId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val books = mutableListOf<BookSummary>()
        var page = 0
        var total: Int? = null
        while (page < MAX_SERIES_BOOK_PAGES) {
            val result = parseGrimmoryAppBookPage(
                nativeJson(
                    "/api/v1/app/series/$encodedName/books",
                    mapOf("page" to page.toString(), "size" to SERIES_PAGE_SIZE.toString(), "sort" to "seriesNumber", "dir" to "asc")
                ).toString(),
                base
            )
            if (result.page != page && result.items.isEmpty()) break
            total = result.total
            books += result.items
            if (result.isComplete || result.items.isEmpty()) break
            page++
        }
        if (books.isEmpty()) return null
        val enrichedBooks = enrichBooks(books, base)
        return SeriesDetailInfo(
            id = seriesId,
            name = enrichedBooks.firstNotNullOfOrNull { it.seriesName } ?: seriesId,
            bookCount = total ?: enrichedBooks.size,
            readCount = enrichedBooks.count { it.readStatus == BookReadStatus.READ },
            authors = enrichedBooks.mapNotNull { it.author?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted(),
            books = enrichedBooks,
            responseTotal = total
        )
    }

    private fun seriesCardBook(series: SeriesSummary): BookSummary = BookSummary(
        libraryId = "",
        id = series.id,
        fileId = null,
        title = series.name,
        author = series.authors.joinToString().ifBlank { null },
        coverUrl = series.coverUrl,
        seriesId = series.id,
        seriesName = series.name,
        addedAtMillis = series.lastAddedAtMillis
    )

    suspend fun loadRecentBooksPage(
        libraryId: String,
        section: HomeSection,
        page: Int
    ): LibraryBooksPage {
        val base = serverUrlProvider()
        val safePage = page.coerceAtLeast(0)
        return when (section) {
            HomeSection.CURRENTLY_READING -> pageOf(
                loadUnfinishedProgressBooks(libraryId, base),
                safePage
            )
            HomeSection.RECENTLY_READ -> loadStatusBooks(libraryId, safePage, BookReadFilter.FINISHED, base)
            HomeSection.RECENTLY_ADDED_BOOKS -> loadBooksPage(
                libraryId = libraryId,
                page = safePage,
                filter = BookBrowseFilter(
                    sort = BookSortOption.ADDED,
                    direction = SortDirection.DESCENDING
                ),
                base = base
            )
            HomeSection.ON_DECK -> {
                val items = loadOnDeckBooks(
                    limit = MAX_ON_DECK_BOOKS,
                    libraryId = libraryId
                )
                pageOf(items, safePage)
            }
            else -> grimmoryUnavailable("${section.title} shelf browsing")
        }
    }

    private suspend fun loadStatusBooks(
        libraryId: String,
        page: Int,
        status: BookReadFilter,
        base: String
    ): LibraryBooksPage = loadBooksPage(
        libraryId = libraryId,
        page = page,
        filter = BookBrowseFilter(
            readStatus = status,
            sort = BookSortOption.LAST_READ,
            direction = SortDirection.DESCENDING
        ),
        base = base
    )

    private suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter,
        base: String
    ): LibraryBooksPage {
        val payload = nativeJson(
            "/api/v1/app/books",
            grimmoryBookCatalogQuery(libraryId, page, filter)
        )
        val parsed = parseGrimmoryAppBookPage(payload.toString(), base)
        return parsed.copy(items = enrichBooks(parsed.items, base))
    }

    private suspend fun loadUnfinishedProgressBooks(
        libraryId: String,
        base: String
    ): List<BookSummary> {
        val filter = BookBrowseFilter(
            sort = BookSortOption.LAST_READ,
            direction = SortDirection.DESCENDING
        )
        val unfinished = mutableListOf<BookSummary>()
        val activeSeriesBooks = mutableListOf<BookSummary>()
        var page = 0
        while (page < MAX_UNFINISHED_PROGRESS_PAGES) {
            val parsed = parseGrimmoryAppBookPage(
                nativeJson("/api/v1/app/books", grimmoryBookCatalogQuery(libraryId, page, filter)).toString(),
                base
            )
            if (parsed.page != page) throw IllegalStateException("Unexpected Grimmory catalog page metadata.")
            parsed.items.forEach { book ->
                if (book.readStatus == BookReadStatus.READING || book.readStatus == BookReadStatus.REREADING) {
                    activeSeriesBooks += book
                }
                if (book.progressPercent?.let { progress ->
                        progress.isFinite() && progress > 0f && progress < COMPLETED_PROGRESS_PERCENT
                    } == true
                ) {
                    unfinished += book
                }
            }
            if (parsed.isComplete || parsed.items.isEmpty()) {
                return enrichBooks(
                    unfinished.filterNot { candidate -> hasEarlierActiveSeriesBook(candidate, activeSeriesBooks) },
                    base
                )
            }
            page++
        }
        throw IllegalStateException("Grimmory unfinished-progress catalog exceeded the page limit.")
    }

    private fun hasEarlierActiveSeriesBook(
        candidate: BookSummary,
        activeSeriesBooks: List<BookSummary>
    ): Boolean {
        if (candidate.readStatus != BookReadStatus.UNREAD) return false
        val candidateNumber = candidate.seriesIndex?.takeIf(Double::isFinite) ?: return false
        val candidateSeriesName = candidate.seriesName?.trim()?.takeIf(String::isNotBlank)
        val candidateSeriesId = candidate.seriesId?.trim()?.takeIf(String::isNotBlank)
        if (candidateSeriesName == null && candidateSeriesId == null) return false

        return activeSeriesBooks.any { active ->
            if (active.libraryId != candidate.libraryId || active.readStatus !in setOf(
                    BookReadStatus.READING,
                    BookReadStatus.REREADING
                )
            ) {
                return@any false
            }
            val activeNumber = active.seriesIndex?.takeIf(Double::isFinite) ?: return@any false
            val activeSeriesId = active.seriesId?.trim()?.takeIf(String::isNotBlank)
            val activeSeriesName = active.seriesName?.trim()?.takeIf(String::isNotBlank)
            val sameSeries = if (candidateSeriesId != null && activeSeriesId != null) {
                candidateSeriesId == activeSeriesId
            } else {
                candidateSeriesName != null && candidateSeriesName == activeSeriesName
            }
            sameSeries && activeNumber < candidateNumber
        }
    }

    private suspend fun loadContinueBooks(path: String, base: String): List<BookSummary> {
        val books = parseGrimmoryBookArray(
            nativeJsonArray(path, mapOf("limit" to CONTINUE_SHELF_LIMIT.toString())),
            base
        )
        return enrichBooks(books, base)
    }

    private suspend fun loadRecentlyAddedBooks(base: String): List<BookSummary> {
        val books = parseGrimmoryBookArray(
            nativeJsonArray(
                "/api/v1/app/books/recently-added",
                mapOf("limit" to PAGE_SIZE.toString())
            ),
            base
        )
        return enrichBooks(books, base)
    }

    private suspend fun loadOnDeckBooks(
        limit: Int,
        libraryId: String = "",
        firstReadPage: LibraryBooksPage? = null
    ): List<BookSummary> {
        if (limit <= 0) return emptyList()
        val base = serverUrlProvider()
        val results = mutableListOf<BookSummary>()
        val seenSeries = mutableSetOf<String>()
        val seenBookIds = mutableSetOf<String>()
        var detailRequests = 0
        val maxDetailRequests = (limit * 2).coerceAtLeast(HOME_ON_DECK_LIMIT * 2)
        var page = firstReadPage?.page ?: 0
        var pagesScanned = 0
        var readPage = firstReadPage ?: loadStatusBooks(libraryId, page, BookReadFilter.FINISHED, base)

        while (pagesScanned < MAX_RECENT_READ_PAGES && results.size < limit && detailRequests < maxDetailRequests) {
            for (readBook in readPage.items) {
                val seriesName = readBook.seriesName?.trim()?.takeIf(String::isNotBlank) ?: continue
                if (!seenSeries.add(seriesName) || detailRequests >= maxDetailRequests) continue
                detailRequests++
                val nextBook = safelyLoad(HomeSection.ON_DECK) {
                    loadNextUnreadAfterLastRead(seriesName, libraryId, base)
                }
                if (nextBook != null && seenBookIds.add(nextBook.id)) results += nextBook
                if (results.size >= limit) break
            }

            pagesScanned++
            if (results.size >= limit || readPage.isComplete || readPage.items.isEmpty() ||
                pagesScanned >= MAX_RECENT_READ_PAGES || detailRequests >= maxDetailRequests
            ) break
            page++
            readPage = loadStatusBooks(libraryId, page, BookReadFilter.FINISHED, base)
        }
        return results
    }

    private suspend fun loadNextUnreadAfterLastRead(
        seriesName: String,
        libraryId: String,
        base: String
    ): BookSummary? {
        val encodedName = URLEncoder.encode(seriesName, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val path = "/api/v1/app/series/$encodedName/books"
        val books = mutableListOf<BookSummary>()
        var page = 0
        while (page < MAX_SERIES_BOOK_PAGES) {
            val query = buildMap {
                put("page", page.toString())
                put("size", SERIES_PAGE_SIZE.toString())
                put("sort", "seriesNumber")
                put("dir", "asc")
                if (libraryId.isNotBlank()) {
                    val numericLibraryId = libraryId.toLongOrNull()
                        ?: throw UserFacingException("Grimmory library identity is unavailable.")
                    put("libraryId", numericLibraryId.toString())
                }
            }
            val parsed = parseGrimmoryAppBookPage(nativeJson(path, query).toString(), base)
            books += parsed.items
            if (parsed.isComplete || parsed.items.isEmpty()) break
            page++
        }
        return nextUnreadAfterLastRead(books)?.let { next ->
            enrichBooks(listOf(next), base).singleOrNull()
        }
    }

    private fun pageOf(items: List<BookSummary>, page: Int): LibraryBooksPage {
        val offset = page * PAGE_SIZE
        val pageItems = items.drop(offset).take(PAGE_SIZE)
        return LibraryBooksPage(
            items = pageItems,
            total = items.size,
            page = page,
            size = PAGE_SIZE,
            isComplete = offset + pageItems.size >= items.size
        )
    }

    private suspend fun <T> safelyLoad(section: HomeSection, block: suspend () -> T): T? = try {
        block()
    } catch (error: AuthenticationRequiredException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val reason = if (error is GrimmoryHttpException) "HTTP ${error.statusCode}" else error.javaClass.simpleName
        logShelfFailure(section, reason)
        null
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val SERIES_PAGE_SIZE = 50
        const val RECENT_SERIES_PAGE_SIZE = 20
        const val CONTINUE_SHELF_LIMIT = 20
        const val HOME_ON_DECK_LIMIT = 8
        const val MAX_ON_DECK_BOOKS = 50
        const val MAX_RECENT_READ_PAGES = 10
        const val MAX_SERIES_BOOK_PAGES = 20
        const val MAX_UNFINISHED_PROGRESS_PAGES = 100
        const val COMPLETED_PROGRESS_PERCENT = 99.5f
    }
}

/** Picks the first unread book after the last completed book, unless an earlier volume is active. */
internal fun nextUnreadAfterLastRead(booksInSeriesOrder: List<BookSummary>): BookSummary? {
    val lastReadIndex = booksInSeriesOrder.indexOfLast { it.readStatus == BookReadStatus.READ }
    if (lastReadIndex < 0) return null
    val afterLastRead = booksInSeriesOrder.drop(lastReadIndex + 1)
    val nextUnreadIndex = afterLastRead.indexOfFirst { it.readStatus == BookReadStatus.UNREAD }
    if (nextUnreadIndex < 0) return null
    if (afterLastRead.take(nextUnreadIndex).any {
            it.readStatus == BookReadStatus.READING || it.readStatus == BookReadStatus.REREADING
        }
    ) return null
    return afterLastRead[nextUnreadIndex]
}
