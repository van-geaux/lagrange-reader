package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCoverSelectionTest {
    @Test
    fun audiobookCoverIsPreferredBeforeGenericCover() {
        val book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = "audio-file",
            title = "Audio book",
            mediaKind = MediaKind.AUDIO,
            coverUrl = "https://server/book-cover",
            audiobookCoverUrl = "https://server/audio-cover"
        )

        assertEquals(
            listOf(
                "https://server/api/v1/books/book/cover?medium=audio",
                "https://server/book-cover",
                "https://server/api/v1/books/book/thumbnail"
            ),
            bookCoverCandidateUrls(book, "https://server")
        )
    }

    @Test
    fun nonAudioBooksDoNotUseAudiobookCoverCandidate() {
        val book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = "ebook-file",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            coverUrl = "https://server/book-cover",
            audiobookCoverUrl = "https://server/audio-cover"
        )

        assertNull(bookCoverCandidateUrls(book, "https://server").firstOrNull { it.endsWith("audio-cover") })
    }

    @Test
    fun downloadedAudiobookIsNotCopiedToInternalStorageForCoverFallback() {
        assertFalse(shouldMaterializeDownloadedFileForCover(MediaKind.AUDIO))
        assertTrue(shouldMaterializeDownloadedFileForCover(MediaKind.EPUB))
        assertTrue(shouldMaterializeDownloadedFileForCover(MediaKind.PDF))
        assertTrue(shouldMaterializeDownloadedFileForCover(MediaKind.COMIC))
    }
}
