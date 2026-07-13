package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryNavigationTest {
    @Test
    fun `jump rail always exposes hash and every alphabet letter`() {
        val targets = listOf(
            BookSummary("library", "numeric", null, "123 Stories"),
            BookSummary("library", "alpha", null, "Alpha Book"),
            BookSummary("library", "m", null, "Monogatari"),
            BookSummary("library", "zulu", null, "Zulu Book")
        ).map { it to null }

        val rail = buildLibraryJumpTargets(targets)

        assertEquals(27, rail.size)
        assertEquals('#', rail.first().first)
        assertEquals(('A'..'Z').toList(), rail.drop(1).map { it.first })
        assertEquals(0, rail.first().second)
    }

    @Test
    fun `jump rail groups non alphabetic titles under hash`() {
        assertEquals('#', libraryJumpLabel("Éclair"))
        assertEquals('#', libraryJumpLabel("123 Stories"))
        assertEquals('M', libraryJumpLabel("monogatari"))
    }

    @Test
    fun `collapsed series are ordered by series name instead of representative title`() {
        val collapsed = collapsedLibraryBooks(
            listOf(
                BookSummary(
                    libraryId = "library",
                    id = "bakemonogatari",
                    fileId = null,
                    title = "Bakemonogatari",
                    seriesId = "monogatari",
                    seriesName = "Monogatari",
                    seriesIndex = 1.0
                ),
                BookSummary(
                    libraryId = "library",
                    id = "alpha",
                    fileId = null,
                    title = "Alpha standalone"
                )
            )
        )

        assertEquals(listOf("Alpha standalone", "Bakemonogatari"), collapsed.map { it.first.title })
        assertEquals("Monogatari", collapsed[1].first.seriesName)
        assertEquals('M', libraryJumpLabel(collapsed[1].first.seriesName))
    }

    @Test
    fun `server jump buckets keep exact absolute indexes and fall forward for missing letters`() {
        val rail = buildServerLibraryJumpTargets(
            buckets = listOf(
                LibraryJumpBucket(key = "#", label = "#", index = 0),
                LibraryJumpBucket(key = "A", label = "A", index = 3),
                LibraryJumpBucket(key = "M", label = "M", index = 40),
                LibraryJumpBucket(key = "Z", label = "Z", index = 99)
            ),
            itemCount = 100
        ).toMap()

        assertEquals(3, rail['A'])
        assertEquals(40, rail['B'])
        assertEquals(99, rail['Y'])
        assertEquals(99, rail['Z'])
    }

    @Test
    fun `author jump targets use author labels in descending display order`() {
        val targets = listOf(
            BookSummary("library", "z", null, "Book Z", author = "Zulu"),
            BookSummary("library", "m", null, "Book M", author = "Mori"),
            BookSummary("library", "a", null, "Book A", author = "Adams")
        ).map { it to null }

        val rail = buildLibraryJumpTargets(
            displayedBooks = targets,
            sort = BookSortOption.AUTHOR,
            direction = SortDirection.DESCENDING
        )

        assertEquals('Z', rail.first().first)
        assertEquals(0, rail.first().second)
        assertEquals(1, rail.first { it.first == 'M' }.second)
        assertEquals('#', rail.last().first)
    }
}
