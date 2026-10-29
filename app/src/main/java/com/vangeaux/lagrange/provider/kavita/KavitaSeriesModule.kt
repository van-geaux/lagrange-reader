package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.SeriesCatalogModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class KavitaSeriesCatalogModuleImpl(
    private val catalog: KavitaBookCatalogModuleImpl,
    private val details: KavitaBookDetailModuleImpl,
    private val snapshotStore: KavitaSeriesCatalogSnapshotStore
) : SeriesCatalogModule {
    override suspend fun loadSeriesCatalog(
        filter: SeriesCatalogFilter,
        page: Int
    ): SeriesCatalogPage {
        if (filter.homeSection == HomeSection.RECENTLY_ADDED_SERIES ||
            filter.homeSection == HomeSection.RECENTLY_UPDATED_SERIES
        ) {
            val homePage = catalog.loadHomeSeriesPage(filter.homeSection, filter.libraryId, page)
            if (!filter.includeCounts) return homePage
            val enriched = coroutineScope {
                homePage.items.map { summary ->
                    async {
                        runCatching { catalog.loadSelectedSeriesBooks(summary.id) }
                            .getOrElse { emptyList() }
                            .let { books ->
                                if (books.isEmpty()) summary else summary.copy(
                                    authors = books.mapNotNull { it.author?.takeIf(String::isNotBlank) }.distinct().sorted(),
                                    bookCount = books.size,
                                    readCount = books.count { it.isRead || (it.progressPercent ?: 0f) >= 99.5f },
                                    availableFormats = books.flatMap { book ->
                                        book.availableFormats.ifEmpty { listOfNotNull(book.format) }
                                    }.distinct().sorted(),
                                    genres = books.flatMap { it.genres }.distinct().sorted()
                                )
                            }
                    }
                }.awaitAll()
            }
            return homePage.copy(items = enriched)
        }
        val libraryId = filter.libraryId
        val serverUrl = catalog.serverUrl()
            ?: throw UserFacingException("Enter a valid Kavita server URL.")
        val (result, jumpIndex) = coroutineScope {
            val pageRequest = async {
                runCatching { catalog.loadSeriesSummaryPage(libraryId, page) }
                    .onSuccess { snapshotStore.write(serverUrl, libraryId, page, it) }
                    .getOrElse { snapshotStore.read(serverUrl, libraryId, page) ?: throw it }
                    .let { result ->
                        if (result.items.isEmpty()) snapshotStore.read(serverUrl, libraryId, page) ?: result else result
                    }
            }
            val jumpRequest = if (page == 0 && filter.includeCounts) {
                async {
                    runCatching { catalog.loadSeriesSummaryPage(libraryId, -1) }
                        .onSuccess { snapshotStore.write(serverUrl, libraryId, -1, it) }
                        .getOrNull()
                }
            } else {
                null
            }
            pageRequest.await() to jumpRequest?.await()?.items
        }
        val filtered = result.items.filter { summary ->
            filter.query.isNullOrBlank() || summary.name.contains(filter.query, ignoreCase = true)
        }
        if (!filter.includeCounts) {
            return SeriesCatalogPage(
                items = filtered,
                total = result.total,
                page = page,
                size = result.pageSize,
                jumpIndex = jumpIndex
            )
        }
        val enriched = coroutineScope {
            filtered.map { summary ->
                async {
                    runCatching { catalog.loadSelectedSeriesBooks(summary.id) }
                        .getOrElse { emptyList() }
                        .let { books ->
                            if (books.isEmpty()) summary else summary.copy(
                                authors = books.mapNotNull { it.author?.takeIf(String::isNotBlank) }.distinct().sorted(),
                                bookCount = books.size,
                                readCount = books.count { it.isRead || (it.progressPercent ?: 0f) >= 99.5f },
                                availableFormats = books.flatMap { book -> book.availableFormats.ifEmpty { listOfNotNull(book.format) } }.distinct().sorted(),
                                genres = books.flatMap { it.genres }.distinct().sorted()
                            )
                        }
                }
            }.awaitAll()
        }
        return SeriesCatalogPage(
            items = enriched,
            total = result.total,
            page = page,
            size = result.pageSize,
            jumpIndex = jumpIndex
        )
    }

    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? {
        val books = catalog.loadSelectedSeriesBooks(seriesId)
            .sortedWith(compareBy<com.vangeaux.lagrange.BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
        if (books.isEmpty()) return null
        val seriesName = runCatching { catalog.loadSeriesDisplayName(seriesId) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: books.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) }
            ?: "Series"
        val namedBooks = books.map { book ->
            if (book.seriesName.isNullOrBlank() || book.seriesName == "Series" || book.seriesName == seriesId) {
                book.copy(seriesName = seriesName)
            } else {
                book
            }
        }
        return SeriesDetailInfo(
            id = seriesId,
            name = seriesName,
            bookCount = namedBooks.size,
            readCount = namedBooks.count { it.isRead },
            authors = namedBooks.mapNotNull { it.author?.takeIf(String::isNotBlank) }.distinct().sorted(),
            books = namedBooks,
            firstBook = namedBooks.firstOrNull()?.let { details.loadBookDetail(it) }
        )
    }
}
