package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.SeriesCatalogModule

internal class KavitaSeriesCatalogModuleImpl(
    private val catalog: KavitaBookCatalogModuleImpl,
    private val details: KavitaBookDetailModuleImpl
) : SeriesCatalogModule {
    override suspend fun loadSeriesCatalog(
        filter: SeriesCatalogFilter,
        page: Int
    ): SeriesCatalogPage {
        val books = catalog.loadAllBooksForSeriesCatalog(filter.libraryId)
        val filtered = filterAndSortSeriesCatalog(
            aggregateBooksToSeriesCatalog(books).items,
            filter
        )
        val from = (page * KAVITA_SERIES_PAGE_SIZE).coerceAtMost(filtered.size)
        val to = (from + KAVITA_SERIES_PAGE_SIZE).coerceAtMost(filtered.size)
        return SeriesCatalogPage(
            items = filtered.subList(from, to),
            total = filtered.size,
            page = page,
            size = KAVITA_SERIES_PAGE_SIZE
        )
    }

    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? {
        val books = catalog.loadAllBooksForSeriesCatalog(null)
            .filter { it.seriesId == seriesId }
            .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
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

    private companion object {
        const val KAVITA_SERIES_PAGE_SIZE = 100
    }
}
