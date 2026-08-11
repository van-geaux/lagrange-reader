package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `configuration stop does not pause the reading session`() {
        assertFalse(shouldPauseReadingSession(isChangingConfigurations = true))
        assertTrue(shouldPauseReadingSession(isChangingConfigurations = false))
    }
}
