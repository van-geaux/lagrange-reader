package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFiltersTest {
    @Test
    fun `book filter produces server group rules and official sort fields`() {
        val filter = BookBrowseFilter(
            title = "Dune",
            readStatus = BookReadFilter.IN_PROGRESS,
            format = BookFormatFilter.EPUB,
            sort = BookSortOption.LAST_READ,
            direction = SortDirection.DESCENDING
        )

        val json = filter.toServerFilter() ?: error("expected filter rules")
        assertEquals("group", json.getString("type"))
        assertEquals("AND", json.getString("join"))
        val rules = json.getJSONArray("rules")
        assertEquals(3, rules.length())
        assertEquals("title", rules.getJSONObject(0).getString("field"))
        assertEquals("contains", rules.getJSONObject(0).getString("operator"))
        assertEquals("isInProgress", rules.getJSONObject(1).getString("operator"))
        assertEquals("includesAny", rules.getJSONObject(2).getString("operator"))
        assertEquals("lastReadAt", BookSortOption.LAST_READ.serverField)
        assertEquals("desc", SortDirection.DESCENDING.serverValue)
        assertTrue("cb7" in BookFormatFilter.COMIC.serverValues)
    }

    @Test
    fun `default book filter does not send a filter group`() {
        assertFalse(BookBrowseFilter().isActive)
        assertTrue(BookBrowseFilter().toServerFilter() == null)
    }

    @Test
    fun `genre book filter uses the official relation rule contract`() {
        val rules = BookBrowseFilter(genre = "Science fiction")
            .toServerFilter()
            ?.getJSONArray("rules")
            ?: error("expected genre rule")

        assertEquals(1, rules.length())
        val rule = rules.getJSONObject(0)
        assertEquals("genre", rule.getString("field"))
        assertEquals("includesAny", rule.getString("operator"))
        assertEquals("Science fiction", rule.getJSONArray("value").getString(0))
    }

    @Test
    fun `author book filter uses the official relation rule contract`() {
        val rule = BookBrowseFilter(author = "Terry Pratchett")
            .toServerFilter()
            ?.getJSONArray("rules")
            ?.getJSONObject(0)
            ?: error("expected author rule")

        assertEquals("author", rule.getString("field"))
        assertEquals("includesAny", rule.getString("operator"))
        assertEquals("Terry Pratchett", rule.getJSONArray("value").getString(0))
    }

    @Test
    fun `local books apply title status format and sort filters`() {
        val books = listOf(
            BookSummary("library", "1", "file-1", "Zeta", author = "Author", mediaKind = MediaKind.EPUB, progressPercent = 40f),
            BookSummary("library", "2", "file-2", "Alpha", author = "Author", mediaKind = MediaKind.PDF, isRead = true),
            BookSummary("library", "3", "file-3", "Dune", author = "Other", mediaKind = MediaKind.EPUB)
        )

        val result = filterAndSortLocalBooks(
            books,
            BookBrowseFilter(
                title = "dune",
                format = BookFormatFilter.EPUB,
                sort = BookSortOption.TITLE
            )
        )

        assertEquals(listOf("3"), result.map { it.id })
        assertTrue(filterAndSortLocalBooks(books, BookBrowseFilter(readStatus = BookReadFilter.FINISHED)).map { it.id }.contains("2"))
    }
}
