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
        val serverMarkedReadButInProgress = seriesBook("book-server-progress", index = 5.0, isRead = true).copy(
            progressPercent = 42f,
            lastReadAtMillis = 300L
        )
        val pageProgress = seriesBook("book-page", index = 3.0).copy(progressLabel = "Page 9")
        val completed = seriesBook("book-complete", index = 1.0, isRead = true).copy(progressPercent = 100f)
        val untouched = seriesBook("book-new", index = 4.0)

        assertEquals(
            listOf(serverMarkedReadButInProgress, current, pageProgress),
            currentlyReadingBooks(listOf(untouched, completed, pageProgress, current, serverMarkedReadButInProgress))
        )
    }

    @Test
    fun `library series count waits for all books before deriving a partial total`() {
        assertEquals(null, librarySeriesCount(100, 50, null, 12))
        assertEquals(18, librarySeriesCount(100, 100, null, 18))
        assertEquals(24, librarySeriesCount(100, 50, 24, 12))
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
    fun `on deck requires a completed book rather than progress alone`() {
        val current = seriesBook("book-1", index = 1.0).copy(progressPercent = 40f)
        val next = seriesBook("book-2", index = 2.0)

        assertEquals(emptyList<BookSummary>(), onDeckBooks(listOf(current, next)))
    }

    @Test
    fun `on deck hides the next volume while it is currently reading`() {
        val completed = seriesBook("book-1", index = 1.0, isRead = true)
        val current = seriesBook("book-2", index = 2.0).copy(progressPercent = 25f)
        val later = seriesBook("book-3", index = 3.0)

        assertEquals(emptyList<BookSummary>(), onDeckBooks(listOf(completed, current, later)))
    }

    @Test
    fun `recently read includes completed books and excludes unfinished activity`() {
        val finished = seriesBook("book-finished", index = 1.0, isRead = true).copy(lastReadAtMillis = 300L)
        val olderFinished = seriesBook("book-older", index = 2.0, isRead = true).copy(lastReadAtMillis = 100L)
        val stillReading = seriesBook("book-current", index = 3.0, isRead = true).copy(
            progressPercent = 42f,
            lastReadAtMillis = 400L
        )
        val recentlyOpened = seriesBook("book-opened", index = 4.0).copy(lastReadAtMillis = 500L)

        assertEquals(
            listOf(finished, olderFinished),
            recentlyReadBooks(listOf(recentlyOpened, olderFinished, stillReading, finished))
        )
    }

    @Test
    fun `recent series keeps one representative ordered by activity`() {
        val older = seriesBook("book-1", index = 1.0, addedAt = 100L)
        val newer = seriesBook("book-2", index = 2.0, addedAt = 200L)

        assertEquals(listOf("Orbit Saga" to newer), recentSeries(listOf(older, newer), useUpdatedAt = false))
    }

    @Test
    fun `local shelf aggregates or scopes downloads and applies its limit`() {
        val alpha = seriesBook("alpha", index = 1.0).copy(title = "Alpha", localPath = "/local/alpha.epub")
        val duplicateAlpha = alpha.copy(fileId = "duplicate-file")
        val beta = seriesBook("beta", index = 2.0).copy(
            libraryId = "lib-2",
            title = "Beta",
            localPath = "/local/beta.epub"
        )
        val remote = seriesBook("remote", index = 3.0).copy(title = "Remote")

        assertEquals(
            listOf("alpha", "beta"),
            localBooksShelf(listOf(beta, remote, duplicateAlpha, alpha)).map { it.id }
        )
        assertEquals(listOf("alpha"), localBooksShelf(listOf(beta, alpha), libraryId = "lib-1").map { it.id })
        assertEquals(listOf("alpha"), localBooksShelf(listOf(beta, alpha), limit = 1).map { it.id })
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
