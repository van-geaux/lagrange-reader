package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShelfTest {
    @Test
    fun `currently reading includes only reading and rereading states`() {
        val reading = seriesBook("book-reading", index = 1.0, status = BookReadStatus.READING)
            .copy(lastReadAtMillis = 200L)
        val rereading = seriesBook("book-rereading", index = 2.0, status = BookReadStatus.REREADING)
            .copy(lastReadAtMillis = 300L)
        val misleadingProgress = seriesBook("book-unread", index = 3.0, status = BookReadStatus.UNREAD)
            .copy(progressPercent = 42f, lastReadAtMillis = 400L)
        val legacyNoStatus = seriesBook("book-legacy", index = 4.0)
            .copy(progressPercent = 55f, lastReadAtMillis = 500L)

        assertEquals(
            listOf(rereading, reading),
            currentlyReadingBooks(listOf(misleadingProgress, reading, legacyNoStatus, rereading))
        )
    }

    @Test
    fun `library series count waits for all books before deriving a partial total`() {
        assertEquals(null, librarySeriesCount(100, 50, null, 12))
        assertEquals(18, librarySeriesCount(100, 100, null, 18))
        assertEquals(24, librarySeriesCount(100, 50, 24, 12))
    }

    @Test
    fun `on deck includes only on hold books including standalone titles`() {
        val older = seriesBook("book-1", index = 1.0, status = BookReadStatus.ON_HOLD)
            .copy(lastReadAtMillis = 100L)
        val newerStandalone = seriesBook("book-2", index = 2.0, status = BookReadStatus.ON_HOLD)
            .copy(seriesId = null, seriesName = null, lastReadAtMillis = 200L)
        val unread = seriesBook("book-3", index = 3.0, status = BookReadStatus.UNREAD)

        assertEquals(listOf(newerStandalone, older), onDeckBooks(listOf(unread, older, newerStandalone)))
    }

    @Test
    fun `want to read includes only the matching server state`() {
        val older = seriesBook("book-1", index = 1.0, status = BookReadStatus.WANT_TO_READ)
            .copy(updatedAtMillis = 100L)
        val newer = seriesBook("book-2", index = 2.0, status = BookReadStatus.WANT_TO_READ)
            .copy(updatedAtMillis = 200L)
        val abandoned = seriesBook("book-3", index = 3.0, status = BookReadStatus.ABANDONED)

        assertEquals(listOf(newer, older), wantToReadBooks(listOf(older, abandoned, newer)))
    }

    @Test
    fun `recently read includes read and skimmed states only`() {
        val read = seriesBook("book-read", index = 1.0, status = BookReadStatus.READ, isRead = true)
            .copy(lastReadAtMillis = 300L)
        val skimmed = seriesBook("book-skimmed", index = 2.0, status = BookReadStatus.SKIMMED, isRead = true)
            .copy(lastReadAtMillis = 400L)
        val misleadingLegacyRead = seriesBook("book-legacy", index = 3.0, isRead = true)
            .copy(lastReadAtMillis = 500L)
        val abandoned = seriesBook("book-abandoned", index = 4.0, status = BookReadStatus.ABANDONED)

        assertEquals(
            listOf(skimmed, read),
            recentlyReadBooks(listOf(read, misleadingLegacyRead, abandoned, skimmed))
        )
    }

    @Test
    fun `unmapped states do not enter any reading state shelf`() {
        val excluded = listOf(
            BookReadStatus.UNREAD,
            BookReadStatus.ABANDONED
        ).mapIndexed { index, status ->
            seriesBook("excluded-$index", index.toDouble(), status = status)
        } + seriesBook("unknown", 3.0)

        assertEquals(emptyList<BookSummary>(), currentlyReadingBooks(excluded))
        assertEquals(emptyList<BookSummary>(), wantToReadBooks(excluded))
        assertEquals(emptyList<BookSummary>(), onDeckBooks(excluded))
        assertEquals(emptyList<BookSummary>(), recentlyReadBooks(excluded))
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
        status: BookReadStatus? = null,
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
        readStatus = status,
        isRead = isRead,
        addedAtMillis = addedAt
    )
}
