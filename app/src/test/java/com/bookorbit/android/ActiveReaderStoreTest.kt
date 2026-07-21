package com.bookorbit.android

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveReaderStoreTest {
    @Test
    fun `read returns null for different server`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-mismatch").toFile())
        store.save(
            serverUrl = "https://one.example",
            book = sampleBook()
        )

        val restored = store.read("https://two.example")

        assertNull(restored)
    }

    @Test
    fun `save and read preserve the active book`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-roundtrip").toFile())
        val book = sampleBook().copy(
            localPath = "/tmp/book.epub",
            progressPercent = 35f,
            progressPositionMs = 12_000L,
            progressPageIndex = 4,
            readStatus = BookReadStatus.REREADING,
            audioChapters = listOf(AudiobookChapter("Opening", 0L)),
            coverAspectRatio = CoverAspectRatio.SQUARE
        )

        store.save("https://example.test", book)

        val restored = store.read("https://example.test")

        assertEquals(book, restored)
    }

    @Test
    fun `clearIfMatches removes only the requested active book`() = runBlocking {
        val store = ActiveReaderStore(Files.createTempDirectory("active-reader-store-clear-match").toFile())
        store.save("https://example.test", sampleBook())

        store.clearIfMatches("https://example.test", "other-book")
        assertEquals(sampleBook(), store.read("https://example.test"))

        store.clearIfMatches("https://example.test", "book-1")
        assertNull(store.read("https://example.test"))
    }

    private fun sampleBook(): BookSummary {
        return BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Example",
            author = "Author",
            format = "application/epub+zip",
            mediaKind = MediaKind.EPUB,
            streamUrl = "https://example.test/stream",
            downloadUrl = "https://example.test/download",
            coverUrl = "https://example.test/cover"
        )
    }
}
