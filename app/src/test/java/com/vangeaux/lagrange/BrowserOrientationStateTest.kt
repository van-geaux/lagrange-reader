package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserOrientationStateTest {
    private fun book(
        libraryId: String,
        id: String,
        fileId: String?,
        title: String,
        localPath: String? = null
    ) = BookSummary(
        libraryId = libraryId,
        id = id,
        fileId = fileId,
        title = title,
        author = "Author",
        format = "epub",
        mediaKind = MediaKind.EPUB,
        streamUrl = "https://books.example.test/stream/$fileId",
        downloadUrl = "https://books.example.test/download/$fileId",
        coverUrl = "https://books.example.test/cover/$id",
        localPath = localPath,
        downloadedSourceUpdatedAtMillis = 100L,
        progressLabel = "42%",
        progressPercent = 42f,
        progressPositionMs = 4_200L,
        progressPageIndex = 42,
        seriesId = "series-1",
        seriesName = "Series",
        seriesIndex = 2.0,
        readStatus = BookReadStatus.READING,
        addedAtMillis = 10L,
        updatedAtMillis = 200L,
        lastReadAtMillis = 300L,
        readerPageIndex = 42,
        readerPageCount = 100,
        coverAspectRatio = CoverAspectRatio.SQUARE,
        isServerMissing = false
    )

    @Test
    fun `selection identity distinguishes files for the same book`() {
        val epub = book("lib-1", "book-1", "file-epub", "EPUB")
        val pdf = book("lib-1", "book-1", "file-pdf", "PDF")

        val resolved = resolveSelectedBook(
            candidateLists = listOf(listOf(epub, pdf)),
            snapshot = bookSelectionSnapshot(pdf)
        )

        assertEquals(pdf, resolved)
    }

    @Test
    fun `selection resolves against later home and search catalogs`() {
        val selected = book("lib-2", "book-2", "file-2", "Home Book")
        val staleFallback = selected.copy(title = "Stale title")

        val resolved = resolveSelectedBook(
            candidateLists = listOf(emptyList(), listOf(selected), emptyList()),
            snapshot = bookSelectionSnapshot(staleFallback)
        )

        assertEquals(selected, resolved)
    }

    @Test
    fun `missing local or search candidate restores bounded fallback without chapters`() {
        val selected = book(
            libraryId = "",
            id = "local-book",
            fileId = "local-file",
            title = "Local Book",
            localPath = "/downloads/local.epub"
        ).copy(audioChapters = listOf(AudiobookChapter("Large chapter payload", 0L)))
        val snapshot = bookSelectionSnapshot(selected)

        val restoredSnapshot = restoreBookSelection(saveBookSelection(snapshot))
        val restoredBook = resolveSelectedBook(emptyList(), restoredSnapshot)

        assertEquals(selected.copy(audioChapters = emptyList()), restoredBook)
        assertEquals(emptyList<AudiobookChapter>(), restoredBook?.audioChapters)
    }

    @Test
    fun `null book selection round trips to null`() {
        assertNull(restoreBookSelection(saveBookSelection(null)))
        assertNull(resolveSelectedBook(emptyList(), null))
    }

    @Test
    fun `author selection round trips through bounded saveable fields`() {
        val original = AuthorSummary(
            id = "author-1",
            name = "Jane Doe",
            bookCount = 3,
            photoUrl = "https://books.example.test/author.jpg"
        )

        assertEquals(original, restoreAuthorSelection(saveAuthorSelection(original)))
        assertNull(restoreAuthorSelection(saveAuthorSelection(null)))
    }

    @Test
    fun `complete browser drill down route round trips atomically`() {
        val selected = book("lib-1", "book-1", "file-1", "Selected Book")
        val genreSource = book("lib-2", "book-2", "file-2", "Genre Source")
        val route = BrowserRouteSnapshot(
            selectedBook = bookSelectionSnapshot(selected),
            selectedSeriesKey = "series-1",
            selectedAuthor = AuthorSummary(
                id = "author-1",
                name = "Jane Doe",
                bookCount = 3,
                photoUrl = "https://books.example.test/author.jpg"
            ),
            activeBookGenre = "Science fiction",
            activeSeriesGenre = "Space opera",
            genreSourceBook = bookSelectionSnapshot(genreSource),
            genreSourceSeriesKey = "series-2",
            detailReturnDestination = BrowserDestination.LIBRARY
        )

        assertEquals(route, restoreBrowserRoute(saveBrowserRoute(route)))
    }
}
