package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShelfTest {
    @Test
    fun `recent home sections use server sort filters`() {
        assertEquals(BookSortOption.ADDED, HomeSection.RECENTLY_ADDED_BOOKS.recentBooksFilter().sort)
        assertEquals(BookSortOption.UPDATED, HomeSection.RECENTLY_UPDATED_SERIES.recentBooksFilter().sort)
        assertEquals(BookSortOption.LAST_READ, HomeSection.RECENTLY_READ.recentBooksFilter().sort)
        assertEquals(SortDirection.DESCENDING, HomeSection.RECENTLY_READ.recentBooksFilter().direction)
        assertEquals(SeriesSortOption.LAST_ADDED, SeriesCatalogFilter(sort = SeriesSortOption.LAST_ADDED).sort)
        assertEquals(SortDirection.DESCENDING, SeriesCatalogFilter(
            sort = SeriesSortOption.LAST_ADDED,
            direction = SortDirection.DESCENDING
        ).direction)
    }
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
    fun `on deck selects the next unread book after a series has been read`() {
        val read = seriesBook("book-1", index = 1.0, status = BookReadStatus.READ, isRead = true)
        val next = seriesBook("book-2", index = 2.0, status = BookReadStatus.WANT_TO_READ)
        val later = seriesBook("book-3", index = 3.0, status = BookReadStatus.UNREAD)

        assertEquals(listOf(next), onDeckBooks(listOf(later, next, read)))
    }

    @Test
    fun `on deck omits unstarted standalone and currently reading books`() {
        val unstartedFirst = seriesBook("unstarted-1", index = 1.0, status = BookReadStatus.UNREAD)
        val unstartedSecond = seriesBook("unstarted-2", index = 2.0, status = BookReadStatus.ON_HOLD)
        val read = seriesBook("started-1", index = 1.0, status = BookReadStatus.READ, isRead = true)
            .copy(seriesId = "series-2", seriesName = "Started Saga")
        val reading = seriesBook("started-2", index = 2.0, status = BookReadStatus.READING)
            .copy(seriesId = "series-2", seriesName = "Started Saga")
        val standalone = seriesBook("standalone", index = 1.0, status = BookReadStatus.ON_HOLD)
            .copy(seriesId = null, seriesName = null)

        assertEquals(
            emptyList<BookSummary>(),
            onDeckBooks(listOf(unstartedSecond, standalone, reading, unstartedFirst, read))
        )
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
    fun `home reading shelves expose eight-item previews and complete projections`() {
        val books = (1..10).map { index ->
            seriesBook("reading-$index", index.toDouble(), status = BookReadStatus.READING)
                .copy(lastReadAtMillis = index.toLong())
        }

        assertEquals(8, currentlyReadingBooks(books).size)
        assertEquals(10, currentlyReadingBooks(books, limit = null).size)
    }

    @Test
    fun `home want-to-read preview keeps ordering while full projection remains available`() {
        val books = (1..10).map { index ->
            seriesBook("want-$index", index.toDouble(), status = BookReadStatus.WANT_TO_READ)
                .copy(updatedAtMillis = index.toLong())
        }

        assertEquals(
            wantToReadBooks(books, limit = null).take(8),
            wantToReadBooks(books)
        )
        assertEquals(10, wantToReadBooks(books, limit = null).size)
    }

    @Test
    fun `recent book and series previews expose eight items with complete projections`() {
        val books = (1..10).map { index ->
            seriesBook("recent-$index", index.toDouble(), status = BookReadStatus.READ, isRead = true)
                .copy(
                    seriesId = "series-$index",
                    seriesName = "Series $index",
                    addedAtMillis = index.toLong(),
                    updatedAtMillis = index.toLong()
                )
        }

        assertEquals(8, recentlyReadBooks(books).size)
        assertEquals(10, recentlyReadBooks(books, limit = null).size)
        assertEquals(8, recentSeries(books, useUpdatedAt = false).size)
        assertEquals(10, recentSeries(books, useUpdatedAt = false, limit = null).size)
        assertEquals(10, homeSeriesSummaries(books, useUpdatedAt = true).size)
    }

    @Test
    fun `authoritative home series preview uses series recency and identity`() {
        val newestBookIsOlderSeries = SeriesSummary(
            id = "series-a",
            name = "Series A",
            coverUrl = "https://example.test/a.jpg",
            lastAddedAtMillis = 100L
        )
        val newestSeries = SeriesSummary(
            id = "series-b",
            name = "Series B",
            coverUrl = "https://example.test/b.jpg",
            lastAddedAtMillis = 200L
        )
        val duplicate = newestSeries.copy(name = "Duplicate label", lastAddedAtMillis = 50L)

        assertEquals(
            listOf("series-b", "series-a"),
            homeSeriesPreview(listOf(newestBookIsOlderSeries, duplicate, newestSeries)).map { it.id }
        )
        assertEquals("Series B", homeSeriesPreview(listOf(duplicate, newestSeries)).first().name)
        assertEquals("https://example.test/b.jpg", homeSeriesPreview(listOf(newestSeries)).first().coverUrl)
    }

    @Test
    fun `authoritative home series preview keeps complete results beyond eight`() {
        val series = (1..10).map { index ->
            SeriesSummary(id = "series-$index", name = "Series $index", lastAddedAtMillis = index.toLong())
        }

        assertEquals(8, homeSeriesPreview(series).size)
        assertEquals(10, homeSeriesPreview(series, limit = Int.MAX_VALUE).size)
        assertEquals(listOf("series-10", "series-9"), homeSeriesPreview(series).take(2).map { it.id })
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

    @Test
    fun `home local shelf prefers all downloaded books over the catalog home subset`() {
        val currentlyReading = seriesBook("reading", index = 1.0, status = BookReadStatus.READING)
            .copy(title = "Reading", localPath = "/local/reading.epub")
        val unread = seriesBook("unread", index = 2.0, status = BookReadStatus.UNREAD)
            .copy(title = "Unread", localPath = "/local/unread.epub")
        val finished = seriesBook("finished", index = 3.0, status = BookReadStatus.READ, isRead = true)
            .copy(title = "Finished", localPath = "/local/finished.epub")
        val onHold = seriesBook("on-hold", index = 4.0, status = BookReadStatus.ON_HOLD)
            .copy(title = "On hold", localPath = "/local/on-hold.epub")

        assertEquals(
            listOf("finished", "on-hold", "reading", "unread"),
            homeLocalBooksPreview(
                catalogHomeBooks = listOf(currentlyReading),
                downloadedBooks = listOf(onHold, unread, currentlyReading, finished)
            ).map { it.id }
        )
    }

    @Test
    fun `library download candidates are scoped to the selected library and skip missing file ids`() {
        val withFile = seriesBook("alpha", index = 1.0)
        val withoutFile = seriesBook("beta", index = 2.0).copy(fileId = null)
        val otherLibrary = seriesBook("gamma", index = 3.0).copy(libraryId = "lib-2")

        assertEquals(
            listOf("alpha"),
            booksDownloadableForLibrary(listOf(withFile, withoutFile, otherLibrary), "lib-1").map { it.id }
        )
        assertEquals(emptyList<BookSummary>(), booksDownloadableForLibrary(listOf(withFile), null))
    }

    @Test
    fun `series download candidates skip books without file ids`() {
        val withFile = seriesBook("alpha", index = 1.0)
        val withoutFile = seriesBook("beta", index = 2.0).copy(fileId = null)

        assertEquals(
            listOf("alpha"),
            booksDownloadableForSeries(listOf(withFile, withoutFile)).map { it.id }
        )
    }

    @Test
    fun `bulk selection deduplicates file ids and excludes completed files by default`() {
        val available = seriesBook("available", index = 1.0)
        val duplicate = available.copy(id = "duplicate", title = "Duplicate")
        val downloaded = seriesBook("downloaded", index = 2.0).copy(localPath = "/local/downloaded.epub")

        assertEquals(
            listOf("file-available"),
            defaultSeriesFileSelection(listOf(available, duplicate, downloaded)).toList()
        )
        assertEquals(
            listOf("available"),
            selectedSeriesFiles(
                listOf(available, duplicate, downloaded),
                setOf("file-available", "file-downloaded")
            ).map { it.id }
        )
    }

    @Test
    fun `bulk selection groups by library and format`() {
        val epub = seriesBook("epub", index = 1.0).copy(format = "epub")
        val pdf = seriesBook("pdf", index = 2.0).copy(format = "pdf")
        val otherLibrary = pdf.copy(id = "other", fileId = "file-other", libraryId = "lib-2")

        assertEquals(
            listOf("Main · epub", "Main · pdf", "Secondary · pdf"),
            bulkDownloadGroupKeys(
                listOf(epub, pdf, otherLibrary),
                listOf(
                    LibrarySummary("lib-1", "Main"),
                    LibrarySummary("lib-2", "Secondary")
                )
            )
        )
    }

    @Test
    fun `series bulk downloads dispatch in ascending index order`() {
        val third = seriesBook("third", index = 3.0)
        val secondB = seriesBook("second-b", index = 2.0).copy(title = "Beta")
        val missing = seriesBook("missing", index = 4.0).copy(title = "Epilogue", seriesIndex = null)
        val first = seriesBook("first", index = 1.0)
        val secondA = seriesBook("second-a", index = 2.0).copy(title = "Alpha")

        assertEquals(
            listOf("first", "second-a", "second-b", "third", "missing"),
            seriesDownloadDispatchOrder(listOf(third, secondB, missing, first, secondA)).map { it.id }
        )
    }

    @Test
    fun `bulk download action hides when every file is current`() {
        val downloaded = seriesBook("downloaded", index = 1.0)
            .copy(localPath = "/local/downloaded.epub")
        val update = downloaded.copy(
            id = "update",
            fileId = "file-update",
            downloadedSourceUpdatedAtMillis = 100L,
            updatedAtMillis = 200L
        )

        assertFalse(hasSelectableBulkDownloads(listOf(downloaded)))
        assertTrue(hasSelectableBulkDownloads(listOf(update)))
    }

    @Test
    fun `series download action returns after local copies are deleted`() {
        val downloaded = seriesBook("downloaded", index = 1.0)
            .copy(localPath = "/local/downloaded.epub")

        val afterDelete = booksWithLocalFilePathOverrides(
            books = listOf(downloaded),
            localFilePathOverrides = mapOf("file-downloaded" to null)
        )

        assertTrue(hasSelectableBulkDownloads(afterDelete))
    }

    @Test
    fun `bulk local delete candidates use current overrides and deduplicate files`() {
        val stale = seriesBook("stale", index = 1.0)
        val duplicate = stale.copy(id = "duplicate")
        val alreadyLocal = seriesBook("local", index = 2.0)
            .copy(localPath = "/local/book.epub")
        val remoteOnly = seriesBook("remote", index = 3.0)

        assertEquals(
            listOf("file-stale", "file-local"),
            localCopiesForBulkAction(
                books = listOf(stale, duplicate, alreadyLocal, remoteOnly),
                localFilePathOverrides = mapOf("file-stale" to "/local/stale.epub")
            ).mapNotNull { it.fileId }
        )
    }

    @Test
    fun `collection download progress includes completed and active files`() {
        val progress = collectionDownloadProgress(
            fileIds = listOf("completed", "active"),
            downloadingFileIds = setOf("active"),
            progressByFileId = mapOf("active" to 0.5f),
            completedFileIds = setOf("completed")
        )

        assertEquals(1, progress?.activeCount)
        assertEquals(2, progress?.totalCount)
        assertEquals(0.75f, progress?.progress ?: 0f, 0.001f)
    }

    @Test
    fun `collection download progress is hidden without active files`() {
        assertEquals(
            null,
            collectionDownloadProgress(
                fileIds = listOf("completed"),
                downloadingFileIds = emptySet(),
                progressByFileId = emptyMap(),
                completedFileIds = setOf("completed")
            )
        )
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
