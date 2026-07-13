package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressQueuePolicyTest {
    @Test
    fun `first progress sample is always queued`() {
        assertTrue(
            ProgressQueuePolicy.shouldQueue(
                current = snapshot(mediaKind = MediaKind.EPUB, pageIndex = 1),
                lastQueued = null
            )
        )
    }

    @Test
    fun `non-audio progress queues when chapter or page changes`() {
        val previous = snapshot(mediaKind = MediaKind.EPUB, pageIndex = 1, progressPercent = 10f)
        val current = snapshot(mediaKind = MediaKind.EPUB, pageIndex = 2, progressPercent = 12f)

        assertTrue(ProgressQueuePolicy.shouldQueue(current, previous))
    }

    @Test
    fun `audio progress suppresses small updates inside the throttle window`() {
        val previous = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 30_000L,
            progressPercent = 10f,
            observedAtMillis = 1_000L
        )
        val current = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 35_000L,
            progressPercent = 10.5f,
            observedAtMillis = 10_000L
        )

        assertFalse(ProgressQueuePolicy.shouldQueue(current, previous))
    }

    @Test
    fun `audio progress queues after sufficient elapsed time`() {
        val previous = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 30_000L,
            progressPercent = 10f,
            observedAtMillis = 1_000L
        )
        val current = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 35_000L,
            progressPercent = 10.5f,
            observedAtMillis = 16_500L
        )

        assertTrue(ProgressQueuePolicy.shouldQueue(current, previous))
    }

    @Test
    fun `audio progress queues after a large position jump`() {
        val previous = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 30_000L,
            progressPercent = 10f,
            observedAtMillis = 1_000L
        )
        val current = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 46_000L,
            progressPercent = 10.5f,
            observedAtMillis = 10_000L
        )

        assertTrue(ProgressQueuePolicy.shouldQueue(current, previous))
    }

    @Test
    fun `audio progress preserves low canonical percentages when comparing`() {
        val previous = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 30_000L,
            progressPercent = 50f,
            observedAtMillis = 1_000L
        )
        val current = snapshot(
            mediaKind = MediaKind.AUDIO,
            positionMs = 30_500L,
            progressPercent = 0.5f,
            observedAtMillis = 5_000L
        )

        assertTrue(ProgressQueuePolicy.isMeaningfullyDifferent(current, previous))
    }

    private fun snapshot(
        mediaKind: MediaKind,
        positionMs: Long = 0L,
        pageIndex: Int = 0,
        progressPercent: Float? = null,
        observedAtMillis: Long = 0L
    ): ProgressSnapshot {
        return ProgressSnapshot(
            mediaKind = mediaKind,
            positionMs = positionMs,
            pageIndex = pageIndex,
            progressPercent = progressPercent,
            observedAtMillis = observedAtMillis
        )
    }
}
