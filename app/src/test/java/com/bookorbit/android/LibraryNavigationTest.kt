package com.bookorbit.android

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryNavigationTest {
    @Test
    fun `catalog grid reserves trailing space only while jump rail is visible`() {
        assertEquals(16.dp, catalogGridEndPadding(hasJumpRail = false))
        assertEquals(32.dp, catalogGridEndPadding(hasJumpRail = true))
    }

    @Test
    fun `jump rail exposes only labels represented by books`() {
        val targets = listOf(
            BookSummary("library", "numeric", null, "123 Stories"),
            BookSummary("library", "alpha", null, "Alpha Book"),
            BookSummary("library", "m", null, "Monogatari"),
            BookSummary("library", "zulu", null, "Zulu Book")
        ).map { it to null }

        val rail = buildLibraryJumpTargets(targets)

        assertEquals(listOf('#', 'A', 'M', 'Z'), rail.map { it.first })
        assertEquals(listOf(0, 1, 2, 3), rail.map { it.second })
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
    fun `collapsed series counts every matching book and uses singular grammar`() {
        val books = listOf(
            BookSummary("library", "one", null, "One", seriesId = "series-a", seriesName = "Series A"),
            BookSummary("library", "two", null, "Two", seriesId = "series-a", seriesName = "Series A"),
            BookSummary("library", "single", null, "Single", seriesId = "series-b", seriesName = "Series B"),
            BookSummary("library", "standalone", null, "Standalone")
        )

        val counts = collapsedSeriesBookCounts(books)

        assertEquals(mapOf("series-a" to 2, "series-b" to 1), counts)
        assertEquals("2 books", seriesBookCountLabel(counts.getValue("series-a")))
        assertEquals("1 book", seriesBookCountLabel(counts.getValue("series-b")))
    }

    @Test
    fun `server jump buckets keep only exact represented labels and absolute indexes`() {
        val rail = buildServerLibraryJumpTargets(
            buckets = listOf(
                LibraryJumpBucket(key = "#", label = "#", index = 0),
                LibraryJumpBucket(key = "A", label = "A", index = 3),
                LibraryJumpBucket(key = "M", label = "M", index = 40),
                LibraryJumpBucket(key = "Z", label = "Z", index = 99)
            ),
            itemCount = 100
        ).toMap()

        assertEquals(setOf('#', 'A', 'M', 'Z'), rail.keys)
        assertEquals(3, rail['A'])
        assertEquals(40, rail['M'])
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
        assertEquals(listOf('Z', 'M', 'A'), rail.map { it.first })
        assertEquals(0, rail.first().second)
        assertEquals(1, rail.first { it.first == 'M' }.second)
        assertEquals('A', rail.last().first)
    }
}
