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
    fun `stale comparison preserves low canonical percentages`() {
        val baseline = update(pageIndex = 10, positionMs = 90_000L, progressPercent = 50f)
        val lowPercent = update(pageIndex = 10, positionMs = 90_000L, progressPercent = 0.5f)

        assertEqualsWithTolerance(0.5f, lowPercent.normalizedProgressPercent())
        assertFalse(lowPercent.isStaleComparedTo(baseline))
    }

    @Test
    fun `stale comparison only suppresses equivalent progress`() {
        val baseline = update(pageIndex = 4, positionMs = 30_000L, progressPercent = 50f)
        val ahead = update(pageIndex = 5, positionMs = 30_000L, progressPercent = 50f)
        val regressed = update(pageIndex = 3, positionMs = 25_000L, progressPercent = 45f)

        assertFalse(ahead.isStaleComparedTo(baseline))
        assertFalse(regressed.isStaleComparedTo(baseline))
    }

    @Test
    fun `lower reread progress can repair an inflated last synced marker`() {
        val inflated = update(pageIndex = 0, positionMs = 0L, progressPercent = 100f)
        val reread = update(pageIndex = 0, positionMs = 0L, progressPercent = 2.5f)

        assertFalse(reread.isStaleComparedTo(inflated))
    }

    @Test
    fun `server targeting requires a non-blank exact server match`() {
        val matching = update(pageIndex = 1, positionMs = 1_000L, progressPercent = 5f)
        val mismatched = matching.copy(serverUrl = "https://other.example")
        val blank = matching.copy(serverUrl = "")

        assertTrue(matching.targetsServer("https://example.test"))
        assertFalse(mismatched.targetsServer("https://example.test"))
        assertFalse(blank.targetsServer("https://example.test"))
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
