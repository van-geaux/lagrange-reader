package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShelfTest {
    @Test
    fun `currently reading includes any active progress and excludes completed books`() {
        val current = seriesBook("book-current", index = 2.0).copy(
            progressPercent = 25f,
            lastReadAtMillis = 200L
        )
        val pageProgress = seriesBook("book-page", index = 3.0).copy(progressLabel = "Page 9")
        val completed = seriesBook("book-complete", index = 1.0, isRead = true).copy(progressPercent = 100f)
        val untouched = seriesBook("book-new", index = 4.0)

        assertEquals(
            listOf(current, pageProgress),
            currentlyReadingBooks(listOf(untouched, completed, pageProgress, current))
        )
    }

    @Test
    fun `on deck selects first unread book after a series has started`() {
        val first = seriesBook("book-1", index = 1.0, isRead = true)
        val next = seriesBook("book-2", index = 2.0)
        val later = seriesBook("book-3", index = 3.0)

        assertEquals(listOf(next), onDeckBooks(listOf(later, next, first)))
    }

    @Test
    fun `on deck omits series that have not started`() {
        val first = seriesBook("book-1", index = 1.0)
        val second = seriesBook("book-2", index = 2.0)

        assertEquals(emptyList<BookSummary>(), onDeckBooks(listOf(first, second)))
    }

    @Test
    fun `recent series keeps one representative ordered by activity`() {
        val older = seriesBook("book-1", index = 1.0, addedAt = 100L)
        val newer = seriesBook("book-2", index = 2.0, addedAt = 200L)

        assertEquals(listOf("Orbit Saga" to newer), recentSeries(listOf(older, newer), useUpdatedAt = false))
    }

    private fun seriesBook(
        id: String,
        index: Double,
        isRead: Boolean = false,
        addedAt: Long? = null
    ) = BookSummary(
        libraryId = "lib-1",
        id = id,
        fileId = "file-$id",
        title = "Book $index",
        seriesId = "series-1",
        seriesName = "Orbit Saga",
        seriesIndex = index,
        isRead = isRead,
        addedAtMillis = addedAt
    )
}
