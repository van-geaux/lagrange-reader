package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTtsProgressTest {
    @Test
    fun `progress throttle queues first meaningful and periodic updates`() {
        assertTrue(shouldQueueEpubTtsProgress(0L, null, -1, 1L, 4f, 2))
        assertTrue(shouldQueueEpubTtsProgress(1_000L, 4f, 2, 1_001L, 4f, 3))
        assertTrue(shouldQueueEpubTtsProgress(1_000L, 4f, 2, 1_001L, 4.21f, 2))
        assertTrue(shouldQueueEpubTtsProgress(1_000L, 4f, 2, 16_000L, 4.1f, 2))
    }

    @Test
    fun `progress throttle suppresses utterance level queue flooding`() {
        assertFalse(shouldQueueEpubTtsProgress(1_000L, 4f, 2, 15_999L, 4.19f, 2))
    }
}
