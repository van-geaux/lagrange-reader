package com.bookorbit.android

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUpdateTest {
    @Test
    fun `newer catalog timestamp exposes a local update`() {
        val book = downloadedBook(updatedAtMillis = 200L, downloadedSourceUpdatedAtMillis = 100L)
        val record = downloadRecord(sourceUpdatedAtMillis = 100L)

        assertTrue(book.hasDownloadUpdate)
        assertTrue(downloadUpdateAvailable(book, record))
    }

    @Test
    fun `equal or older catalog timestamp keeps the local copy current`() {
        assertFalse(
            downloadedBook(updatedAtMillis = 100L, downloadedSourceUpdatedAtMillis = 100L)
                .hasDownloadUpdate
        )
        assertFalse(
            downloadUpdateAvailable(
                downloadedBook(updatedAtMillis = 99L, downloadedSourceUpdatedAtMillis = 100L),
                downloadRecord(sourceUpdatedAtMillis = 100L)
            )
        )
    }

    @Test
    fun `legacy download gets one update opportunity when catalog has a version`() {
        val book = downloadedBook(updatedAtMillis = 100L, downloadedSourceUpdatedAtMillis = null)

        assertTrue(book.hasDownloadUpdate)
        assertTrue(downloadUpdateAvailable(book, downloadRecord(sourceUpdatedAtMillis = null)))
    }

    @Test
    fun `missing catalog timestamp does not invent an update`() {
        val book = downloadedBook(updatedAtMillis = null, downloadedSourceUpdatedAtMillis = null)

        assertFalse(book.hasDownloadUpdate)
        assertFalse(downloadUpdateAvailable(book, downloadRecord(sourceUpdatedAtMillis = null)))
    }

    @Test
    fun `staged replacement atomically replaces the previous bytes`() {
        val dir = Files.createTempDirectory("download-update-replace").toFile()
        val target = File(dir, "book.epub").apply { writeText("old") }
        val staged = File(dir, ".book.epub.part").apply { writeText("new") }

        atomicReplaceDownloadedFile(staged, target)

        assertEquals("new", target.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun `unsupported ebook containers remain downloadable without being treated as epub`() {
        val dir = Files.createTempDirectory("download-update-ebook-formats").toFile()
        val mobi = File(dir, "book.mobi").apply { writeText("mobi-bytes") }
        val azw = File(dir, "book.azw3").apply { writeText("azw-bytes") }

        assertTrue(
            downloadedFilePassesIntegrity(
                downloadedBook(updatedAtMillis = 1L, downloadedSourceUpdatedAtMillis = null)
                    .copy(format = "mobi", mediaKind = MediaKind.UNKNOWN),
                mobi
            )
        )
        assertTrue(
            downloadedFilePassesIntegrity(
                downloadedBook(updatedAtMillis = 1L, downloadedSourceUpdatedAtMillis = null)
                    .copy(format = "azw3", mediaKind = MediaKind.UNKNOWN),
                azw
            )
        )
    }

    private fun downloadedBook(
        updatedAtMillis: Long?,
        downloadedSourceUpdatedAtMillis: Long?
    ) = BookSummary(
        libraryId = "library-1",
        id = "book-1",
        fileId = "file-1",
        title = "Book",
        mediaKind = MediaKind.EPUB,
        localPath = "/downloads/book.epub",
        downloadedSourceUpdatedAtMillis = downloadedSourceUpdatedAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    private fun downloadRecord(sourceUpdatedAtMillis: Long?) = DownloadRecord(
        serverUrl = "https://example.test",
        fileId = "file-1",
        bookId = "book-1",
        title = "Book",
        localPath = "/downloads/book.epub",
        mediaKind = MediaKind.EPUB,
        sourceUpdatedAtMillis = sourceUpdatedAtMillis
    )
}
