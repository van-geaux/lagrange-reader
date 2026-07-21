package com.bookorbit.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesPaginationTest {
    @Test
    fun `series catalog pagination loads every page to the response total`() = runTest {
        val requests = mutableListOf<Int>()

        val catalog = loadCompleteSeriesCatalog { requestedPage ->
            requests += requestedPage
            when (requestedPage) {
                0 -> catalogPage(
                    page = 0,
                    total = 3,
                    size = 2,
                    items = listOf(series("alpha", "Alpha"), series("middle", "Middle"))
                )
                1 -> catalogPage(
                    page = 1,
                    total = 3,
                    size = 2,
                    items = listOf(series("zulu", "Zulu"))
                )
                else -> error("pagination should stop at the response total")
            }
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(listOf("alpha", "middle", "zulu"), catalog.items.map { it.id })
        assertEquals(3, catalog.total)
    }

    @Test
    fun `series catalog pagination stops when a page adds no distinct series`() = runTest {
        val requests = mutableListOf<Int>()

        val catalog = loadCompleteSeriesCatalog { requestedPage ->
            requests += requestedPage
            if (requestedPage == 0) {
                catalogPage(0, total = 4, size = 2, items = listOf(series("alpha", "Alpha"), series("middle", "Middle")))
            } else {
                catalogPage(1, total = 4, size = 2, items = listOf(series("middle", "Duplicate Middle")))
            }
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(listOf("alpha", "middle"), catalog.items.map { it.id })
    }

    @Test
    fun `series jump targets follow ascending and descending name order`() {
        val ascending = buildSeriesJumpTargets(
            listOf(series("numeric", "123 Series"), series("alpha", "Alpha"), series("middle", "Middle"), series("zulu", "Zulu"))
        ).toMap()
        val descending = buildSeriesJumpTargets(
            listOf(series("zulu", "Zulu"), series("middle", "Middle"), series("alpha", "Alpha"), series("numeric", "123 Series")),
            SortDirection.DESCENDING
        )

        assertEquals(0, ascending['#'])
        assertEquals(1, ascending['A'])
        assertEquals(setOf('#', 'A', 'M', 'Z'), ascending.keys)
        assertEquals(3, ascending['Z'])
        assertEquals('Z', descending.first().first)
        assertEquals(0, descending.first().second)
        assertEquals(1, descending.first { it.first == 'M' }.second)
        assertEquals(3, descending.last().second)
    }

    @Test
    fun `pagination reaches response total when series metadata count is smaller`() = runTest {
        val requests = mutableListOf<Int>()
        val pages = listOf(
            page(
                page = 0,
                total = 27,
                metadataCount = 22,
                books = (1..22).map { book("book-$it", "Book $it", it.toDouble()) }
            ),
            page(
                page = 1,
                total = 27,
                metadataCount = 22,
                books = (23..27).map { book("book-$it", "Book $it", it.toDouble()) }
            )
        )

        val loaded = loadCompleteSeriesPages { requestedPage ->
            requests += requestedPage
            pages[requestedPage]
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(27, loaded.flatMap { it.books }.distinctBy { it.id }.size)
    }

    @Test
    fun `pagination stops after an empty terminal page`() = runTest {
        val requests = mutableListOf<Int>()
        val loaded = loadCompleteSeriesPages { requestedPage ->
            requests += requestedPage
            if (requestedPage == 0) {
                page(
                    page = 0,
                    total = 5,
                    books = (1..3).map { book("book-$it", "Book $it", it.toDouble()) }
                )
            } else {
                page(page = requestedPage, total = 5, books = emptyList())
            }
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(2, loaded.size)
        assertEquals(3, loaded.flatMap { it.books }.size)
    }

    @Test
    fun `pagination stops when a page adds no distinct ids`() = runTest {
        val requests = mutableListOf<Int>()
        val loaded = loadCompleteSeriesPages { requestedPage ->
            requests += requestedPage
            when (requestedPage) {
                0 -> page(
                    page = 0,
                    total = 6,
                    books = (1..3).map { book("book-$it", "Book $it", it.toDouble()) }
                )
                1 -> page(
                    page = 1,
                    total = 6,
                    books = listOf(book("book-2", "Book 2 duplicate", 2.0))
                )
                else -> error("pagination should stop after a page with no new ids")
            }
        }

        assertEquals(listOf(0, 1), requests)
        assertEquals(3, loaded.flatMap { it.books }.distinctBy { it.id }.size)
    }

    @Test
    fun `merge deduplicates and orders fractional and missing series indexes`() {
        val pages = listOf(
            page(
                page = 0,
                total = 4,
                books = listOf(
                    book("book-2", "Second", 2.5),
                    book("book-1", "First", 1.0),
                    book("book-missing", "Unnumbered", null)
                )
            ),
            page(
                page = 1,
                total = 4,
                books = listOf(
                    book("book-2", "Second duplicate", 2.5),
                    book("book-3", "Third", 3.0)
                )
            )
        )

        val merged = mergeSeriesBooks(pages)

        assertEquals(listOf("book-1", "book-2", "book-3", "book-missing"), merged.map { it.id })
        assertEquals("Second", merged[1].title)
    }

    @Test
    fun `library ownership from scoped pages is applied to unscoped series books`() {
        val unscoped = listOf(
            book("book-1", "First", 1.0).copy(libraryId = ""),
            book("book-2", "Second", 2.0).copy(libraryId = "")
        )
        val scoped = listOf(
            book("book-1", "First", 1.0).copy(libraryId = "library-1"),
            book("book-2", "Second", 2.0).copy(
                libraryId = "library-2",
                coverAspectRatio = CoverAspectRatio.SQUARE
            )
        )

        val merged = mergeSeriesBooksWithLibraryOwnership(unscoped, scoped)

        assertEquals(listOf("library-1", "library-2"), merged.map { it.libraryId })
        assertEquals(
            listOf(CoverAspectRatio.PORTRAIT, CoverAspectRatio.SQUARE),
            merged.map { it.coverAspectRatio }
        )
    }

    private fun page(
        page: Int,
        total: Int,
        metadataCount: Int? = null,
        books: List<BookSummary>
    ): SeriesBooksPage {
        val seriesInfo = SeriesDetailInfo(
            id = "series-1",
            name = "Test Series",
            bookCount = metadataCount ?: total,
            readCount = 0,
            metadataBookCount = metadataCount,
            responseTotal = total,
            books = books
        )
        return SeriesBooksPage(
            books = books,
            total = total,
            page = page,
            size = 100,
            seriesInfo = seriesInfo
        )
    }

    private fun catalogPage(
        page: Int,
        total: Int?,
        size: Int,
        items: List<SeriesSummary>
    ) = SeriesCatalogPage(
        items = items,
        total = total,
        page = page,
        size = size
    )

    private fun series(id: String, name: String) = SeriesSummary(id = id, name = name)

    private fun book(id: String, title: String, seriesIndex: Double?): BookSummary {
        return BookSummary(
            libraryId = "library-1",
            id = id,
            fileId = null,
            title = title,
            seriesId = "series-1",
            seriesName = "Test Series",
            seriesIndex = seriesIndex
        )
    }
}
