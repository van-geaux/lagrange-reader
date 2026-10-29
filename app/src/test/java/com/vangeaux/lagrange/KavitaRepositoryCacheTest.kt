package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.mergeKavitaCachedBooks
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaRepositoryCacheTest {
    @Test
    fun home_books_fill_an_empty_browser_snapshot() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "chapter:1",
            fileId = null,
            title = "Current book"
        )
        val shelves = HomeShelfData(
            booksBySection = mapOf(HomeSection.CURRENTLY_READING to listOf(book)),
            isServerProvided = true
        )

        val result = mergeKavitaCachedBooks(
            snapshotBooksByLibraryId = mapOf("library-1" to emptyList()),
            homeShelves = shelves,
            localBooks = emptyList()
        )

        assertEquals(listOf(book), result)
    }

    @Test
    fun cached_books_are_deduplicated_in_favor_of_the_browser_snapshot() {
        val snapshotBook = BookSummary(
            libraryId = "library-1",
            id = "chapter:1",
            fileId = null,
            title = "Cached title"
        )
        val homeBook = snapshotBook.copy(title = "Home title")

        val result = mergeKavitaCachedBooks(
            snapshotBooksByLibraryId = mapOf("library-1" to listOf(snapshotBook)),
            homeShelves = HomeShelfData(
                booksBySection = mapOf(HomeSection.CURRENTLY_READING to listOf(homeBook)),
                isServerProvided = true
            ),
            localBooks = emptyList()
        )

        assertEquals(listOf(snapshotBook), result)
    }

    @Test
    fun downloaded_metadata_is_reapplied_after_cached_book_deduplication() {
        val snapshotBook = BookSummary(
            libraryId = "library-1",
            id = "chapter:1",
            fileId = "file-1",
            title = "Cached title",
            format = "epub",
            mediaKind = MediaKind.EPUB
        )
        val download = DownloadRecord(
            serverUrl = "https://kavita.example",
            fileId = "file-1",
            bookId = "chapter:1",
            title = "Cached title",
            localPath = "/downloads/book.epub",
            mediaKind = MediaKind.EPUB,
            mimeType = "application/epub+zip"
        )

        val result = mergeKavitaCachedBooks(
            snapshotBooksByLibraryId = mapOf("library-1" to listOf(snapshotBook)),
            homeShelves = null,
            localBooks = emptyList(),
            downloads = mapOf(download.fileId to download)
        )

        assertEquals("/downloads/book.epub", result.single().localPath)
        assertEquals(listOf("EPUB"), result.single().downloadedFormats)
    }
}
