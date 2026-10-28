package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.SeriesCatalogModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class KavitaSeriesCatalogModuleImpl(
    private val catalog: KavitaBookCatalogModuleImpl,
    private val details: KavitaBookDetailModuleImpl
) : SeriesCatalogModule {
    override suspend fun loadSeriesCatalog(
        filter: SeriesCatalogFilter,
        page: Int
    ): SeriesCatalogPage {
        val libraryId = filter.libraryId
        val (result, jumpIndex) = coroutineScope {
            val pageRequest = async { catalog.loadSeriesSummaryPage(libraryId, page) }
            val jumpRequest = if (page == 0 && filter.includeCounts) {
                async { catalog.loadSeriesSummaryPage(libraryId, -1) }
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
                            summary.copy(
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
        val seriesName = books.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) }
            ?: "Series"
        return SeriesDetailInfo(
            id = seriesId,
            name = seriesName,
            bookCount = books.size,
            readCount = books.count { it.isRead },
            authors = books.mapNotNull { it.author?.takeIf(String::isNotBlank) }.distinct().sorted(),
            books = books,
            firstBook = books.firstOrNull()?.let { details.loadBookDetail(it) }
        )
    }
}
