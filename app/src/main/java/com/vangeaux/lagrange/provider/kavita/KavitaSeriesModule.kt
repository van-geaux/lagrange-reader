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
        val libraryId = filter.libraryId
        val result = catalog.loadSeriesSummaryPage(libraryId, page)
        val filtered = result.items.filter { summary ->
            filter.query.isNullOrBlank() || summary.name.contains(filter.query, ignoreCase = true)
        }
        return SeriesCatalogPage(
            items = filtered,
            total = result.total,
            page = page,
            size = result.pageSize
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
