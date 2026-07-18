package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesNavigationTest {
    @Test
    fun `neighbors follow numeric series index regardless of candidate order`() {
        val first = book("first", "First", 1.0)
        val current = book("current", "Current", 2.0)
        val next = book("next", "Next", 3.0)

        val neighbors = seriesBookNeighbors(current, listOf(next, current, first))

        assertEquals(first, neighbors.previous)
        assertEquals(next, neighbors.next)
        assertEquals(3, neighbors.total)
    }

    @Test
    fun `series edges keep one stable disabled direction`() {
        val first = book("first", "First", 1.0)
        val next = book("next", "Next", 2.0)

        val firstNeighbors = seriesBookNeighbors(first, listOf(next))
        val lastNeighbors = seriesBookNeighbors(next, listOf(first))

        assertNull(firstNeighbors.previous)
        assertEquals(next, firstNeighbors.next)
        assertEquals(first, lastNeighbors.previous)
        assertNull(lastNeighbors.next)
    }

    @Test
    fun `unindexed books sort after indexed books and unrelated series are excluded`() {
        val indexed = book("indexed", "Indexed", 1.0)
        val current = book("current", "Current", null)
        val unrelated = book("unrelated", "Unrelated", 2.0).copy(
            seriesId = "other-series",
            seriesName = "Other Series"
        )

        val neighbors = seriesBookNeighbors(current, listOf(unrelated, indexed))

        assertEquals(indexed, neighbors.previous)
        assertNull(neighbors.next)
        assertEquals(2, neighbors.total)
    }

    private fun book(id: String, title: String, index: Double?) = BookSummary(
        libraryId = "library",
        id = id,
        fileId = "file-$id",
        title = title,
        seriesId = "series",
        seriesName = "Test Series",
        seriesIndex = index
    )
}
