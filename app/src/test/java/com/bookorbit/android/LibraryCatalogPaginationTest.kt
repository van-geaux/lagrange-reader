package com.bookorbit.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogPaginationTest {
    @Test
    fun `full catalog reuses a displayed first page and loads directly from page two`() = runTest {
        val requestedPages = mutableListOf<Int>()
        val firstPage = page(0, total = 5, ids = listOf("1", "2"))

        val pages = loadCompleteLibraryPages(firstPage) { requestedPage ->
            requestedPages += requestedPage
            when (requestedPage) {
                1 -> page(1, total = 5, ids = listOf("3", "4"))
                2 -> page(2, total = 5, ids = listOf("5"))
                else -> error("unexpected page $requestedPage")
            }
        }

        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(listOf("1", "2", "3", "4", "5"), mergeLibraryBooks(pages).map { it.id })
    }

    @Test
    fun `full catalog stops when the server repeats a page`() = runTest {
        val requestedPages = mutableListOf<Int>()
        val pages = loadCompleteLibraryPages { requestedPage ->
            requestedPages += requestedPage
            when (requestedPage) {
                0 -> page(0, total = 6, ids = listOf("1", "2"))
                1 -> page(1, total = 6, ids = listOf("1", "2"))
                else -> error("pagination should stop after a repeated page")
            }
        }

        assertEquals(listOf(0, 1), requestedPages)
        assertEquals(listOf("1", "2"), mergeLibraryBooks(pages).map { it.id })
    }

    @Test
    fun `catalog merge preserves server order and first copy of duplicate books`() {
        val pages = listOf(
            page(0, total = 3, ids = listOf("a", "b")),
            LibraryBooksPage(
                items = listOf(book("b", "Duplicate"), book("c", "Book c")),
                total = 3,
                page = 1,
                size = 2
            )
        )

        val merged = mergeLibraryBooks(pages)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("Book b", merged[1].title)
    }

    @Test
    fun `catalog stability rejects changed totals and incomplete deduplicated results`() {
        val changedTotals = listOf(
            page(0, total = 4, ids = listOf("1", "2")),
            page(1, total = 3, ids = listOf("3"))
        )
        val duplicateBoundary = listOf(
            page(0, total = 4, ids = listOf("1", "2")),
            page(1, total = 4, ids = listOf("2", "3"))
        )
        val stable = listOf(
            page(0, total = 3, ids = listOf("1", "2")),
            page(1, total = 3, ids = listOf("3"))
        )

        assertFalse(libraryCatalogPagesAreStable(changedTotals, mergeLibraryBooks(changedTotals)))
        assertFalse(libraryCatalogPagesAreStable(duplicateBoundary, mergeLibraryBooks(duplicateBoundary)))
        assertTrue(libraryCatalogPagesAreStable(stable, mergeLibraryBooks(stable)))
    }

    private fun page(page: Int, total: Int, ids: List<String>): LibraryBooksPage = LibraryBooksPage(
        items = ids.map { id -> book(id, "Book $id") },
        total = total,
        page = page,
        size = 2
    )

    private fun book(id: String, title: String): BookSummary = BookSummary(
        libraryId = "library-1",
        id = id,
        fileId = null,
        title = title
    )
}
