package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaHomeReadingShelvesTest {
    @Test
    fun activeBookIsCurrentlyReadingAndNextUnreadBookIsOnDeck() {
        val books = listOf(
            book("read", 1.0, BookReadStatus.READ, isRead = true),
            book("current", 2.0, BookReadStatus.READING),
            book("next", 3.0, BookReadStatus.UNREAD)
        )

        val shelves = com.vangeaux.lagrange.provider.kavita.kavitaHomeReadingShelves(books)

        assertEquals(listOf("current"), shelves.currentlyReading.map { it.id })
        assertTrue(shelves.onDeck.isEmpty())
    }

    @Test
    fun nextUnreadBookIsOnDeckWhenSeriesHasNoActiveBook() {
        val books = listOf(
            book("read", 1.0, BookReadStatus.READ, isRead = true),
            book("next", 2.0, BookReadStatus.UNREAD)
        )

        val shelves = com.vangeaux.lagrange.provider.kavita.kavitaHomeReadingShelves(books)

        assertTrue(shelves.currentlyReading.isEmpty())
        assertEquals(listOf("next"), shelves.onDeck.map { it.id })
    }

    @Test
    fun currentlyReadingBookIsExcludedFromOnDeck() {
        val shelves = com.vangeaux.lagrange.provider.kavita.kavitaHomeReadingShelves(
            listOf(book("current", 1.0, BookReadStatus.READING))
        )

        assertEquals(listOf("current"), shelves.currentlyReading.map { it.id })
        assertTrue(shelves.onDeck.isEmpty())
    }

    @Test
    fun completedSeriesHasNoOnDeckBook() {
        val shelves = com.vangeaux.lagrange.provider.kavita.kavitaHomeReadingShelves(
            listOf(
                book("first", 1.0, BookReadStatus.READ, isRead = true),
                book("last", 2.0, BookReadStatus.READ, isRead = true)
            )
        )

        assertTrue(shelves.currentlyReading.isEmpty())
        assertTrue(shelves.onDeck.isEmpty())
    }

    private fun book(
        id: String,
        seriesIndex: Double,
        status: BookReadStatus,
        isRead: Boolean = false
    ) = BookSummary(
        libraryId = "1",
        id = id,
        fileId = id,
        title = id,
        seriesId = "series-1",
        seriesName = "Series",
        seriesIndex = seriesIndex,
        readStatus = status,
        isRead = isRead
    )
}
