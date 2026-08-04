package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionTrackerTest {
    @Test
    fun `ends a session with active duration and progress delta`() {
        val tracker = ReadingSessionTracker(
            sessionIdFactory = { "session-1" },
            minSessionMillis = 10_000L
        )

        tracker.start(progressPercent = 10f, atMillis = 1_000L)
        tracker.activity(progressPercent = 25f, atMillis = 16_000L)
        val payload = tracker.end(progressPercent = 40f, atMillis = 31_000L)

        assertEquals("session-1", payload?.sessionId)
        assertEquals(30L, payload?.durationSeconds)
        assertEquals(30.0, payload?.progressDelta ?: 0.0, 0.001)
        assertEquals(40.0, payload?.endProgress ?: 0.0, 0.001)
        assertEquals(1_000L, payload?.startedAtMillis)
        assertEquals(31_000L, payload?.endedAtMillis)
    }

    @Test
    fun `explicit pause finalizes the active session and resume starts a new one`() {
        var nextId = 0
        val tracker = ReadingSessionTracker(
            sessionIdFactory = { "session-${++nextId}" },
            minSessionMillis = 1L
        )

        tracker.start(progressPercent = 5f, atMillis = 1_000L)
        val paused = tracker.pause(atMillis = 21_000L, progressPercent = 10f)
        tracker.resume(progressPercent = 10f, atMillis = 30_000L)
        val resumed = tracker.end(progressPercent = 15f, atMillis = 40_000L)

        assertEquals("session-1", paused?.sessionId)
        assertEquals(20L, paused?.durationSeconds)
        assertEquals(10.0, paused?.endProgress ?: 0.0, 0.001)
        assertEquals("session-2", resumed?.sessionId)
        assertEquals(10L, resumed?.durationSeconds)
    }

    @Test
    fun `idle activity closes old session and starts a new one`() {
        var nextId = 0
        val tracker = ReadingSessionTracker(
            sessionIdFactory = { "session-${++nextId}" },
            idleTimeoutMillis = 300_000L,
            minSessionMillis = 1L
        )

        tracker.start(progressPercent = 0f, atMillis = 0L)
        val rollover = tracker.activity(progressPercent = 50f, atMillis = 360_000L)
        val next = tracker.end(progressPercent = 60f, atMillis = 370_000L)

        assertEquals(1, rollover.size)
        assertEquals("session-1", rollover.single().sessionId)
        assertEquals(300L, rollover.single().durationSeconds)
        assertEquals("session-2", next?.sessionId)
        assertEquals(10L, next?.durationSeconds)
    }

    @Test
    fun `short sessions are discarded and end is idempotent`() {
        val tracker = ReadingSessionTracker(
            sessionIdFactory = { "short" },
            minSessionMillis = 10_000L
        )

        tracker.start(progressPercent = 10f, atMillis = 1_000L)
        assertNull(tracker.end(progressPercent = 10f, atMillis = 9_000L))
        assertNull(tracker.end(progressPercent = 20f, atMillis = 20_000L))
    }

    @Test
    fun `end clamps active duration to the idle cutoff`() {
        val tracker = ReadingSessionTracker(
            sessionIdFactory = { "session-3" },
            idleTimeoutMillis = 300_000L,
            minSessionMillis = 1L
        )

        tracker.start(progressPercent = 20f, atMillis = 1_000L)
        val payload = tracker.end(progressPercent = 30f, atMillis = 600_000L)

        assertTrue(payload != null)
        assertEquals(300L, payload?.durationSeconds)
    }
}
