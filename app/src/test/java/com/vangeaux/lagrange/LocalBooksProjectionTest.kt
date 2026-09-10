package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalBooksProjectionTest {
    private val first = BookSummary(
        libraryId = "library-1",
        id = "book-1",
        fileId = "file-1",
        title = "Multipart Audiobook",
        filename = "Chapter 01.mp3",
        mediaKind = MediaKind.AUDIO,
        localPath = "/downloads/chapter-01.mp3",
        downloadedSourceUpdatedAtMillis = 100L
    )

    @Test
    fun `multipart audio files project to one logical local book`() {
        val projected = logicalLocalBooks(
            listOf(
                first,
                first.copy(
                    fileId = "file-2",
                    filename = "Chapter 02.mp3",
                    localPath = "/downloads/chapter-02.mp3",
                    downloadedSourceUpdatedAtMillis = 200L
                )
            )
        )

        assertEquals(1, projected.size)
        assertEquals("book-1", projected.single().id)
        assertEquals("file-1", projected.single().fileId)
    }

    @Test
    fun `single file local books remain separate`() {
        val projected = logicalLocalBooks(
            listOf(
                first.copy(id = "epub-1", fileId = "epub-file", mediaKind = MediaKind.EPUB),
                first.copy(id = "pdf-1", fileId = "pdf-file", mediaKind = MediaKind.PDF)
            )
        )

        assertEquals(listOf("epub-file", "pdf-file"), projected.map { it.fileId })
    }

    @Test
    fun `local book item keys distinguish ungrouped sibling files defensively`() {
        val firstKey = localBookItemKey(first, null)
        val secondKey = localBookItemKey(first.copy(fileId = "file-2"), null)

        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun `download transfer keys use physical file identity`() {
        val firstRow = DownloadTransferRow(first, "file-1", true, false, null)
        val secondRow = DownloadTransferRow(first.copy(fileId = "file-2"), "file-2", true, false, null)

        assertNotEquals(downloadTransferItemKey(firstRow), downloadTransferItemKey(secondRow))
    }

    @Test
    fun `download progress labels distinguish queued files`() {
        assertEquals("Downloading · 25%", downloadProgressLabel(false, 0.25f))
        assertEquals("Waiting · 25%", downloadProgressLabel(true, 0.25f))
        assertEquals("Waiting…", downloadProgressLabel(true, null))
    }

    @Test
    fun `download transfer rows include every queued physical file`() {
        val state = BrowserState(
            serverUrl = "https://example.test",
            libraries = emptyList(),
            selectedLibraryId = null,
            books = emptyList(),
            downloadingFileIds = setOf("file-1"),
            queuedDownloadFileIds = setOf("file-2", "file-3"),
            downloadBooksByFileId = mapOf(
                "file-1" to first,
                "file-2" to first.copy(fileId = "file-2", filename = "Chapter 02.mp3"),
                "file-3" to first.copy(fileId = "file-3", filename = "Chapter 03.mp3")
            )
        )

        val rows = downloadTransferRows(state)

        assertEquals(listOf("file-1", "file-2", "file-3"), rows.map { it.fileId })
        assertEquals(listOf(false, true, true), rows.map { it.isQueued })
    }
}
