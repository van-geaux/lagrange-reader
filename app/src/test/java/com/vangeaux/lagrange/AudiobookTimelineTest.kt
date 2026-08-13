package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookTimelineTest {

    private fun file(id: String, durationMs: Long?, mediaKind: MediaKind = MediaKind.AUDIO): BookFileOption {
        val book = BookSummary(
            libraryId = "lib",
            id = "book-1",
            fileId = id,
            title = "Book",
            mediaKind = mediaKind
        )
        return BookFileOption(book = book, filename = "$id.mp3", durationMs = durationMs)
    }

    private val fiveTrackFiles = listOf(
        file("f1", 100_000L),
        file("f2", 200_000L),
        file("f3", 50_000L),
        file("f4", 300_000L),
        file("f5", 150_000L)
    )

    @Test
    fun `playableAudioFiles filters non-audio without reordering`() {
        val nonAudio = file("cover", 10_000L, MediaKind.EPUB)
        val files = listOf(nonAudio, fiveTrackFiles[0], fiveTrackFiles[1])

        val result = AudiobookTimeline.playableAudioFiles(files)

        assertEquals(listOf(fiveTrackFiles[0], fiveTrackFiles[1]), result)
    }

    @Test
    fun `totalDurationMs sums known durations`() {
        val total = AudiobookTimeline.totalDurationMs(fiveTrackFiles)
        assertEquals(800_000L, total)
    }

    @Test
    fun `totalDurationMs is null when any duration unknown`() {
        val files = fiveTrackFiles.toMutableList()
        files[2] = file("f3", null)
        assertNull(AudiobookTimeline.totalDurationMs(files))
    }

    @Test
    fun `cumulativeStartsMs computes track boundaries`() {
        val starts = AudiobookTimeline.cumulativeStartsMs(fiveTrackFiles)
        assertEquals(listOf(0L, 100_000L, 300_000L, 350_000L, 650_000L), starts)
    }

    @Test
    fun `cumulativeStartsMs preserves fractional-second boundaries parsed as milliseconds`() {
        val files = listOf(file("f1", 100_765L), file("f2", 200_500L))

        assertEquals(listOf(0L, 100_765L), AudiobookTimeline.cumulativeStartsMs(files))
        assertEquals(301_265L, AudiobookTimeline.totalDurationMs(files))
    }

    @Test
    fun `absoluteToPosition resolves exact boundary to the starting track`() {
        val position = AudiobookTimeline.absoluteToPosition(fiveTrackFiles, 300_000L)
        requireNotNull(position)
        assertEquals(2, position.fileIndex)
        assertEquals("f3", position.fileId)
        assertEquals(0L, position.positionMs)
        assertEquals(300_000L, position.absolutePositionMs)
    }

    @Test
    fun `absoluteToPosition one millisecond before boundary stays in previous track`() {
        val position = AudiobookTimeline.absoluteToPosition(fiveTrackFiles, 299_999L)
        requireNotNull(position)
        assertEquals(1, position.fileIndex)
        assertEquals("f2", position.fileId)
        assertEquals(199_999L, position.positionMs)
    }

    @Test
    fun `absoluteToPosition one millisecond after boundary enters next track`() {
        val position = AudiobookTimeline.absoluteToPosition(fiveTrackFiles, 300_001L)
        requireNotNull(position)
        assertEquals(2, position.fileIndex)
        assertEquals("f3", position.fileId)
        assertEquals(1L, position.positionMs)
    }

    @Test
    fun `absoluteToPosition clamps negative and overflowing positions`() {
        val negative = AudiobookTimeline.absoluteToPosition(fiveTrackFiles, -50L)
        requireNotNull(negative)
        assertEquals(0L, negative.absolutePositionMs)

        val overflow = AudiobookTimeline.absoluteToPosition(fiveTrackFiles, 999_999_999L)
        requireNotNull(overflow)
        assertEquals(4, overflow.fileIndex)
        assertEquals("f5", overflow.fileId)
        assertEquals(150_000L, overflow.positionMs)
        assertEquals(800_000L, overflow.absolutePositionMs)
    }

    @Test
    fun `absoluteToPosition falls back safely when durations are unknown`() {
        val files = listOf(file("only", null))
        val position = AudiobookTimeline.absoluteToPosition(files, 5_000L)
        requireNotNull(position)
        assertEquals(0, position.fileIndex)
        assertEquals("only", position.fileId)
        assertEquals(5_000L, position.positionMs)
    }

    @Test
    fun `absoluteToPosition returns null for empty file list`() {
        assertNull(AudiobookTimeline.absoluteToPosition(emptyList(), 0L))
    }

    @Test
    fun `fileRelativeToAbsolute maps unknown file id back to null`() {
        assertNull(AudiobookTimeline.fileRelativeToAbsolute(fiveTrackFiles, "unknown", 0L))
    }

    @Test
    fun `fileRelativeToAbsolute maps in-file position back to absolute time`() {
        val absolute = AudiobookTimeline.fileRelativeToAbsolute(fiveTrackFiles, "f3", 20_000L)
        assertEquals(320_000L, absolute)
    }

    @Test
    fun `totalDurationMs and cumulativeStartsMs are overflow-safe for extreme durations`() {
        val hugeDuration = Long.MAX_VALUE / 2 + 1
        val files = listOf(file("huge-1", hugeDuration), file("huge-2", hugeDuration))

        val total = AudiobookTimeline.totalDurationMs(files)
        requireNotNull(total)
        assertEquals(Long.MAX_VALUE, total)

        val starts = AudiobookTimeline.cumulativeStartsMs(files)
        assertEquals(0L, starts[0])
        assertEquals(hugeDuration, starts[1])
    }

    @Test
    fun `fileRelativeToAbsolute single-track M4B identity mapping`() {
        val singleTrack = listOf(file("m4b", 3_600_000L))
        val absolute = AudiobookTimeline.fileRelativeToAbsolute(singleTrack, "m4b", 1_800_000L)
        assertEquals(1_800_000L, absolute)

        val position = AudiobookTimeline.absoluteToPosition(singleTrack, 1_800_000L)
        requireNotNull(position)
        assertEquals(0, position.fileIndex)
        assertEquals("m4b", position.fileId)
        assertEquals(1_800_000L, position.positionMs)
    }
}
