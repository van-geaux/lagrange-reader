package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailSessionHistoryActionTest {
    @Test
    fun `history button is shown only for audiobook currently-reading statuses`() {
        assertTrue(showAudiobookSessionHistoryButton(book(BookReadStatus.READING)))
        assertTrue(showAudiobookSessionHistoryButton(book(BookReadStatus.REREADING)))
        assertFalse(showAudiobookSessionHistoryButton(book(BookReadStatus.UNREAD)))
        assertFalse(showAudiobookSessionHistoryButton(book(BookReadStatus.READ)))
    }

    @Test
    fun `history button is hidden for non-audiobook currently-reading books`() {
        assertFalse(
            showAudiobookSessionHistoryButton(
                book(BookReadStatus.READING).copy(mediaKind = MediaKind.EPUB)
            )
        )
    }

    @Test
    fun `action row reserves the leading history button`() {
        val withoutHistory = bookDetailActionRowLayout(
            availableWidth = 320f,
            readWidth = 82f,
            previewWidth = 94f,
            markWidth = 118f,
            hasInlineTransfer = false,
            hasFixedOverflow = false,
            hasSessionHistory = false
        )
        val withHistory = bookDetailActionRowLayout(
            availableWidth = 320f,
            readWidth = 82f,
            previewWidth = 94f,
            markWidth = 118f,
            hasInlineTransfer = false,
            hasFixedOverflow = false,
            hasSessionHistory = true,
            sessionHistoryWidth = 32f
        )

        assertTrue(withoutHistory.showInlineStatusAction)
        assertFalse(withHistory.showInlineStatusAction)
        assertTrue(withHistory.showMore)
    }

    private fun book(status: BookReadStatus) = BookSummary(
        libraryId = "library-1",
        id = "book-1",
        fileId = "file-1",
        title = "Audiobook",
        mediaKind = MediaKind.AUDIO,
        readStatus = status
    )
}
