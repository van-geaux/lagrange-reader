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
}
