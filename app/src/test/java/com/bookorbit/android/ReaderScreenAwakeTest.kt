package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenAwakeTest {
    @Test
    fun `visual readers stay awake while audio and unsupported media may sleep`() {
        assertTrue(readerKeepsScreenAwake(MediaKind.EPUB))
        assertTrue(readerKeepsScreenAwake(MediaKind.PDF))
        assertTrue(readerKeepsScreenAwake(MediaKind.COMIC))
        assertFalse(readerKeepsScreenAwake(MediaKind.AUDIO))
        assertFalse(readerKeepsScreenAwake(MediaKind.UNKNOWN))
    }
}
