package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookTimelineTest {

    private fun file(
        id: String,
        durationMs: Long?,
        mediaKind: MediaKind = MediaKind.AUDIO,
        role: String? = null,
        format: String? = null,
        filename: String = "$id.mp3"
    ): BookFileOption {
        val book = BookSummary(
            libraryId = "lib",
            id = "book-1",
            fileId = id,
            title = "Book",
            format = format,
            mediaKind = mediaKind
        )
        return BookFileOption(book = book, filename = filename, durationMs = durationMs, role = role)
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
    fun `downloadableAudioFiles preserves server order and excludes supplements`() {
        val files = listOf(
            file("chapter-2", 2_000L, role = "content"),
            file("metadata", null, mediaKind = MediaKind.UNKNOWN, role = "supplement"),
            file("chapter-1", 1_000L, role = "content")
        )

        assertEquals(
            listOf("chapter-2", "chapter-1"),
            AudiobookTimeline.downloadableAudioFiles(files).map { it.fileId }
        )
    }

    @Test
    fun `downloadableAudioFiles drops blank and duplicate identities`() {
        val files = listOf(
            file("first", 1_000L),
            file("first", 2_000L),
            file("", 3_000L),
            file("m4b", 4_000L)
        )

        assertEquals(
            listOf("first", "m4b"),
            AudiobookTimeline.downloadableAudioFiles(files).map { it.fileId }
        )
    }

    @Test
    fun `availableFileGroups combines same-format audio files by book and grouping path`() {
        val grouped = listOf(
            file("chapter-1", 1_000L).copy(groupingPath = "books/test/story"),
            file("chapter-2", 1_000L).copy(groupingPath = "books/test/story"),
            file("alternate", 1_000L).copy(groupingPath = "books/test/alternate")
        )

        val groups = availableFileGroups(grouped)

        assertEquals(2, groups.size)
        assertEquals(listOf("chapter-1", "chapter-2"), groups[0].options.map { it.fileId })
        assertEquals("books/test/story", groups[0].groupingPath)
        assertEquals(listOf("alternate"), groups[1].options.map { it.fileId })
    }

    @Test
    fun `availableFileGroups separates mixed media and audio formats in one server folder`() {
        val options = listOf(
            file("epub", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book.epub")
                .copy(groupingPath = "books/test/Book"),
            file("pdf", null, mediaKind = MediaKind.PDF, format = "pdf", filename = "Book.pdf")
                .copy(groupingPath = "books/test/Book"),
            file("mp3", 1_000L, format = "mp3", filename = "Chapter 01.mp3")
                .copy(groupingPath = "books/test/Book"),
            file("mp3-2", 1_000L, format = "mp3", filename = "Chapter 02.mp3")
                .copy(groupingPath = "books/test/Book"),
            file("m4a", 1_000L, format = "m4a", filename = "Book.m4a")
                .copy(groupingPath = "books/test/Book"),
            file("m4a-2", 1_000L, format = "m4a", filename = "Book part 2.m4a")
                .copy(groupingPath = "books/test/Book"),
            file("m4b", 1_000L, format = "m4b", filename = "Book.m4b")
                .copy(groupingPath = "books/test/Book")
        )

        val groups = availableFileGroups(options)

        assertEquals(5, groups.size)
        assertEquals(
            listOf(MediaKind.EPUB, MediaKind.PDF, MediaKind.AUDIO, MediaKind.AUDIO, MediaKind.AUDIO),
            groups.map { it.options.first().mediaKind }
        )
        assertEquals(listOf(1, 1, 2, 2, 1), groups.map { it.options.size })
        assertEquals(listOf("epub", "pdf", "mp3", "m4a", "m4b"), groups.map { it.options.first().format })
    }

    @Test
    fun `availableFileGroups separates ebook units with different stems in one folder`() {
        val options = listOf(
            file("one", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book One.epub")
                .copy(groupingPath = "books/test/Pack"),
            file("two", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book Two.epub")
                .copy(groupingPath = "books/test/Pack")
        )

        val groups = availableFileGroups(options)

        assertEquals(2, groups.size)
        assertEquals(listOf("one", "two"), groups.map { it.options.single().fileId })
    }

    @Test
    fun `availableFileGroups follows upstream ebook release stem normalization`() {
        val options = listOf(
            file("retail", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book (retail).epub")
                .copy(groupingPath = "books/test/Pack"),
            file("v2", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book v2.epub")
                .copy(groupingPath = "books/test/Pack"),
            file("brackets", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book [fixed].epub")
                .copy(groupingPath = "books/test/Pack"),
            file("braces", null, mediaKind = MediaKind.EPUB, format = "epub", filename = "Book {proper}.epub")
                .copy(groupingPath = "books/test/Pack")
        )

        val groups = availableFileGroups(options)

        assertEquals(1, groups.size)
        assertEquals(listOf("retail", "v2", "brackets", "braces"), groups.single().options.map { it.fileId })
    }

    @Test
    fun `availableFileGroupDisplayLabel uses group name and total size`() {
        val group = AvailableFileGroup(
            key = "group",
            options = listOf(
                file("chapter-1", 1_000L).copy(
                    filename = "Chapter 01.mp3",
                    sizeBytes = 40L * 1024 * 1024,
                    groupingPath = "books/test/Complete Audiobook"
                ),
                file("chapter-2", 1_000L).copy(
                    filename = "Chapter 02.mp3",
                    sizeBytes = 560L * 1024 * 1024,
                    groupingPath = "books/test/Complete Audiobook"
                )
            )
        )

        val label = availableFileGroupDisplayLabel(group)

        assertEquals("MP3 · Complete Audiobook", label.title)
        assertEquals("2 files · 600 MB", label.metadata)
    }

    @Test
    fun `closed grouped file summary uses group name instead of primary filename`() {
        val option = file("chapter-1", 1_000L).copy(
            filename = "Chapter 01.mp3",
            groupingPath = "books/test/Complete Audiobook"
        )
        val group = AvailableFileGroup(
            key = "group",
            options = listOf(option, option.copy(book = option.book.copy(fileId = "chapter-2")))
        )

        assertEquals(
            "Complete Audiobook",
            availableFileSummaryName(option, availableFileDisplayLabels(listOf(option)).values.single(), group)
        )
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
