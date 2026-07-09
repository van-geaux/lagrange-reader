package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressUpdateConflictTest {
    @Test
    fun `stale comparison treats identical progress as stale`() {
        val baseline = update(pageIndex = 4, positionMs = 30_000L, progressPercent = 50f)
        val identical = update(pageIndex = 4, positionMs = 30_000L, progressPercent = 50f)

        assertTrue(identical.isStaleComparedTo(baseline))
    }

    @Test
    fun `stale comparison normalizes fractional percentages`() {
        val baseline = update(pageIndex = 10, positionMs = 90_000L, progressPercent = 50f)
        val fractional = update(pageIndex = 10, positionMs = 90_000L, progressPercent = 0.5f)

        assertEqualsWithTolerance(50f, fractional.normalizedProgressPercent())
        assertTrue(fractional.isStaleComparedTo(baseline))
    }

    @Test
    fun `stale comparison keeps newer progress when any dimension moves forward`() {
        val baseline = update(pageIndex = 4, positionMs = 30_000L, progressPercent = 50f)
        val ahead = update(pageIndex = 5, positionMs = 30_000L, progressPercent = 50f)
        val regressed = update(pageIndex = 3, positionMs = 25_000L, progressPercent = 45f)

        assertFalse(ahead.isStaleComparedTo(baseline))
        assertTrue(regressed.isStaleComparedTo(baseline))
    }

    private fun update(
        pageIndex: Int,
        positionMs: Long,
        progressPercent: Float?
    ): ProgressUpdate {
        return ProgressUpdate(
            id = "id",
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            mediaKind = MediaKind.EPUB,
            positionMs = positionMs,
            pageIndex = pageIndex,
            progressPercent = progressPercent,
            updatedAtMillis = 1L
        )
    }

    private fun assertEqualsWithTolerance(expected: Float, actual: Float) {
        assertTrue(kotlin.math.abs(expected - actual) < 0.0001f)
    }
}
