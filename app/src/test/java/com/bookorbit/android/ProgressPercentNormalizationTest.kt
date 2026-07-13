package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressPercentNormalizationTest {
    @Test
    fun `normalizeStoredProgressPercent keeps canonical zero to one hundred values`() {
        assertEquals(0.375f, normalizeStoredProgressPercent(0.375f))
        assertEquals(1f, normalizeStoredProgressPercent(1f))
        assertEquals(50f, normalizeStoredProgressPercent(50f))
        assertEquals(100f, normalizeStoredProgressPercent(120f))
    }

    @Test
    fun `ebook payload does not scale a low percentage twice`() {
        val payload = buildProgressPayload(
            update(
                mediaKind = MediaKind.EPUB,
                fileId = "88",
                progressPercent = 0.375f
            )
        )

        assertEquals(0.375, payload?.getDouble("percentage"))
    }

    @Test
    fun `audio payload uses canonical percentage and seconds`() {
        val payload = buildProgressPayload(
            update(
                mediaKind = MediaKind.AUDIO,
                fileId = "91",
                progressPercent = 1f,
                positionMs = 12_500L
            )
        )

        assertEquals(1.0, payload?.getDouble("percentage"))
        assertEquals(91, payload?.getInt("currentFileId"))
        assertEquals(12.5, payload?.getDouble("positionSeconds"))
    }

    @Test
    fun `unknown percentage is not submitted as zero progress`() {
        val payload = buildProgressPayload(
            update(
                mediaKind = MediaKind.EPUB,
                fileId = "88",
                progressPercent = null
            )
        )

        assertEquals(null, payload)
    }

    @Test
    fun `low epub percentage restores near the beginning instead of the middle`() {
        assertEquals(0, percentToChapterIndex(0.5f, 100))
        assertEquals(1, percentToChapterIndex(1f, 100))
        assertEquals(50, percentToChapterIndex(50f, 100))
    }

    private fun update(
        mediaKind: MediaKind,
        fileId: String,
        progressPercent: Float?,
        positionMs: Long = 0L
    ) = ProgressUpdate(
        id = "update",
        serverUrl = "https://example.test",
        bookId = "41",
        fileId = fileId,
        mediaKind = mediaKind,
        positionMs = positionMs,
        pageIndex = 0,
        progressPercent = progressPercent,
        updatedAtMillis = 1L
    )
}
