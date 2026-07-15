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
    fun `unfinished progress explicitly marks the BookOrbit book as reading`() {
        val payload = buildReadStatusPayload(
            update(
                mediaKind = MediaKind.EPUB,
                fileId = "88",
                progressPercent = 42.5f
            )
        )

        assertEquals("reading", payload?.getString("status"))
    }

    @Test
    fun `zero progress still marks an opened BookOrbit book as reading`() {
        val payload = buildReadStatusPayload(
            update(
                mediaKind = MediaKind.EPUB,
                fileId = "88",
                progressPercent = 0f
            )
        )

        assertEquals("reading", payload?.getString("status"))
    }

    @Test
    fun `completion threshold explicitly marks the BookOrbit book as read`() {
        val payload = buildReadStatusPayload(
            update(
                mediaKind = MediaKind.EPUB,
                fileId = "88",
                progressPercent = 99.5f
            )
        )

        assertEquals("read", payload?.getString("status"))
    }

    @Test
    fun `unknown progress does not create a reading status payload`() {
        val payload = buildReadStatusPayload(
            update(
                mediaKind = MediaKind.EPUB,
                fileId = "88",
                progressPercent = null
            )
        )

        assertEquals(null, payload)
    }

    @Test
    fun `ebook reset deletes the primary file progress`() {
        val request = buildProgressResetRequest(book(mediaKind = MediaKind.EPUB, fileId = "88"))

        assertEquals("/api/v1/books/files/88/progress", request?.path)
        assertEquals("DELETE", request?.method)
        assertEquals(null, request?.payload)
    }

    @Test
    fun `audio reset saves explicit zero progress`() {
        val request = buildProgressResetRequest(book(mediaKind = MediaKind.AUDIO, fileId = "91"))

        assertEquals("/api/v1/books/41/audio-progress", request?.path)
        assertEquals("PATCH", request?.method)
        assertEquals(0.0, request?.payload?.getDouble("percentage"))
        assertEquals(91, request?.payload?.getInt("currentFileId"))
        assertEquals(0.0, request?.payload?.getDouble("positionSeconds"))
    }

    @Test
    fun `reset requires a valid file id`() {
        assertEquals(null, buildProgressResetRequest(book(mediaKind = MediaKind.EPUB, fileId = null)))
        assertEquals(null, buildProgressResetRequest(book(mediaKind = MediaKind.AUDIO, fileId = "not-a-number")))
    }

    @Test
    fun `reset marks the book unread and clears lifecycle dates`() {
        val payload = buildUnreadStatusPayload()

        assertEquals("unread", payload.getString("status"))
        assertEquals(true, payload.isNull("startedAt"))
        assertEquals(true, payload.isNull("finishedAt"))
    }

    @Test
    fun `explicit read action marks the book read`() {
        val payload = buildMarkedReadStatusPayload()

        assertEquals("read", payload.getString("status"))
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

    private fun book(mediaKind: MediaKind, fileId: String?) = BookSummary(
        libraryId = "7",
        id = "41",
        fileId = fileId,
        title = "Test book",
        mediaKind = mediaKind
    )
}
