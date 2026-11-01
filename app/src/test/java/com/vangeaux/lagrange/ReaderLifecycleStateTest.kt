package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLifecycleStateTest {
    @Test
    fun `restored launch token does not launch a duplicate reader`() {
        val first = claimReaderLaunch(ReaderLaunchState(), "book-1|file-1|normal")
        val restored = claimReaderLaunch(first.state, "book-1|file-1|normal")

        assertTrue(first.shouldLaunch)
        assertFalse(restored.shouldLaunch)
        assertEquals(first.state, restored.state)
    }

    @Test
    fun `new launch token can open a different reader`() {
        val first = claimReaderLaunch(ReaderLaunchState(), "book-1|file-1|normal")
        val second = claimReaderLaunch(first.state, "book-2|file-2|normal")

        assertTrue(second.shouldLaunch)
        assertEquals("book-2|file-2|normal", second.state.token)
    }

    @Test
    fun `only explicit user closure closes coordinator reader state`() {
        assertTrue(shouldCloseReader(ReaderCompletionReason.USER_CLOSED))
        assertFalse(shouldCloseReader(ReaderCompletionReason.LIFECYCLE_RECOVERY))
        assertFalse(shouldCloseReader(ReaderCompletionReason.OPEN_FAILED))
    }

    @Test
    fun `saved reader instance reopens instead of finishing`() {
        assertEquals(ReaderRestoreAction.OPEN, readerRestoreAction(hasSavedInstanceState = false))
        assertEquals(ReaderRestoreAction.REOPEN, readerRestoreAction(hasSavedInstanceState = true))
    }

    @Test
    fun `saved locator JSON is capped before entering instance state`() {
        assertEquals("{}", boundedReaderLocatorJson("{}"))
        assertEquals(null, boundedReaderLocatorJson("x".repeat(MAX_SAVED_READER_LOCATOR_BYTES + 1)))
    }

    @Test
    fun `configuration stop does not pause the reading session`() {
        assertFalse(shouldPauseReadingSession(isChangingConfigurations = true))
        assertTrue(shouldPauseReadingSession(isChangingConfigurations = false))
    }

    @Test
    fun `canonical position with null locator clears stale active locator`() {
        val book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = null,
            title = "Book",
            mediaKind = MediaKind.EPUB,
            progressPercent = 25f,
            progressPageIndex = 1,
            readerPageIndex = 2,
            readerPageCount = 8,
            readerLocatorJson = "{\"href\":\"stale.xhtml\"}"
        )
        val state = ReaderState(
            book = book,
            pageIndex = 1,
            readerPageIndex = 2,
            initialLocatorJson = "{\"href\":\"older.xhtml\"}"
        )
        val canonical = EpubReaderPosition(
            serverUrl = "https://example.test",
            bookId = book.id,
            fileId = null,
            chapterIndex = 4,
            pageIndex = 5,
            pageCount = 9,
            updatedAtMillis = 1L,
            locatorJson = null,
            progressPercent = 60f
        )

        val restored = applyCanonicalEpubReaderPosition(state, canonical)

        assertNull(restored.initialLocatorJson)
        assertNull(restored.book.readerLocatorJson)
        assertEquals(4, restored.pageIndex)
        assertEquals(4, restored.book.progressPageIndex)
        assertEquals(5, restored.readerPageIndex)
        assertEquals(5, restored.book.readerPageIndex)
        assertEquals(9, restored.book.readerPageCount)
        assertEquals(60f, restored.progressPercent)
        assertEquals(60f, restored.book.progressPercent)
    }

    @Test
    fun `reading session stays active only while foreground TTS is actually playing`() {
        assertFalse(
            shouldPauseEpubReadingSessionOnStop(
                isChangingConfigurations = false,
                ttsPlayingInForeground = true
            )
        )
        assertTrue(
            shouldPauseEpubReadingSessionOnStop(
                isChangingConfigurations = false,
                ttsPlayingInForeground = false
            )
        )
        assertFalse(
            shouldPauseEpubReadingSessionOnStop(
                isChangingConfigurations = true,
                ttsPlayingInForeground = false
            )
        )
    }
}
