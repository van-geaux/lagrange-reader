package com.bookorbit.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesPaginationTest {
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
