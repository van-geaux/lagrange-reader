package com.vangeaux.lagrange

import androidx.media3.common.MimeTypes
import androidx.media3.session.CommandButton
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.util.mediatype.MediaType

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReadiumAudioPlaybackTest {

    @Test
    fun audiobookPreviewBannerVisibilityFollowsCurrentEmittedSessionMode() {
        assertEquals(false, shouldShowAudiobookPreviewBanner(ReaderLaunchMode.NORMAL))
        assertEquals(true, shouldShowAudiobookPreviewBanner(ReaderLaunchMode.PREVIEW))
        assertEquals(false, shouldShowAudiobookPreviewBanner(ReaderLaunchMode.NORMAL))
    }

    private fun file(
        id: String,
        streamUrl: String?,
        format: String = "mp3",
        filename: String? = "$id.mp3",
        durationMs: Long? = 100_000L,
        mediaKind: MediaKind = MediaKind.AUDIO
    ): BookFileOption {
        val book = BookSummary(
            libraryId = "lib",
            id = "book-1",
            fileId = id,
            title = "Book Title",
            author = "Book Author",
            format = format,
            mediaKind = mediaKind,
            streamUrl = streamUrl
        )
        return BookFileOption(book = book, filename = filename, durationMs = durationMs)
    }

    @Test
    fun buildAudiobookMediaItemSpecsPreservesServerOrderWithStableIdsAndMetadata() {
        val files = listOf(
            file("f1", "https://server/stream/f1"),
            file("f2", "https://server/stream/f2")
        )

        val specs = buildAudiobookMediaItemSpecs(files)

        assertEquals(2, specs.size)
        assertEquals("f1", specs[0].fileId)
        assertEquals("https://server/stream/f1", specs[0].streamUrl)
        assertEquals(MimeTypes.AUDIO_MPEG, specs[0].mimeType)
        assertEquals("f1.mp3", specs[0].title)
        assertEquals("Book Author", specs[0].artist)
        assertEquals("f2", specs[1].fileId)
        assertEquals("https://server/stream/f2", specs[1].streamUrl)
    }

    @Test
    fun buildAudiobookMediaItemSpecsSkipsEntriesMissingStreamUrlFileIdOrMimeType() {
        val files = listOf(
            file("f1", "https://server/stream/f1"),
            file("f2", null),
            file(id = "f3", streamUrl = "https://server/stream/f3", format = "epub")
        )

        val specs = buildAudiobookMediaItemSpecs(files)

        assertEquals(1, specs.size)
        assertEquals("f1", specs[0].fileId)
    }

    @Test
    fun resolveAbsolutePositionMsMapsFileRelativePositionAcrossTracks() {
        val files = listOf(
            file("f1", "https://server/stream/f1", durationMs = 100_000L),
            file("f2", "https://server/stream/f2", durationMs = 200_000L)
        )

        assertEquals(100_500L, resolveAbsolutePositionMs(files, "f2", 500L))
        assertEquals(500L, resolveAbsolutePositionMs(files, "f1", 500L))
        assertEquals(500L, resolveAbsolutePositionMs(files, null, 500L))
    }

    @Test
    fun resolveTotalDurationMsPrefersKnownFileDurationsOverPlayerFallback() {
        val files = listOf(
            file("f1", "https://server/stream/f1", durationMs = 100_000L),
            file("f2", "https://server/stream/f2", durationMs = 200_000L)
        )

        assertEquals(300_000L, resolveTotalDurationMs(files, fallbackDurationMs = 999L))
        assertEquals(999L, resolveTotalDurationMs(emptyList(), fallbackDurationMs = 999L))
    }

    @Test
    fun resolveSeekTargetMapsAbsolutePositionToTrackAndOffset() {
        val files = listOf(
            file("f1", "https://server/stream/f1", durationMs = 100_000L),
            file("f2", "https://server/stream/f2", durationMs = 200_000L)
        )

        val target = resolveSeekTarget(files, 100_500L)

        assertEquals(1, target?.fileIndex)
        assertEquals("f2", target?.fileId)
        assertEquals(500L, target?.positionMs)
    }

    @Test
    fun resolveSeekTargetReturnsNullForSingleTrackAudiobooks() {
        val files = listOf(file("f1", "https://server/stream/f1", durationMs = 100_000L))

        assertNull(resolveSeekTarget(files, 50L))
    }

    @Test
    fun activeBookSummaryForActiveFileSwitchesFileIdAndStreamButKeepsBookLevelMetadata() {
        val bookLevel = BookSummary(
            libraryId = "lib",
            id = "book-1",
            fileId = "f1",
            title = "Gone Girl",
            author = "Gillian Flynn",
            format = "mp3",
            mediaKind = MediaKind.AUDIO,
            streamUrl = "https://server/stream/f1",
            coverUrl = "https://server/cover.jpg",
            audioChapters = listOf(AudiobookChapter(title = "Part One", startMs = 0L))
        )
        val files = listOf(
            file("f1", "https://server/stream/f1"),
            file("f2", "https://server/stream/f2")
        )

        val active = activeBookSummaryForActiveFile(bookLevel, files, "f2")

        assertEquals("f2", active.fileId)
        assertEquals("https://server/stream/f2", active.streamUrl)
        assertEquals("Gone Girl", active.title)
        assertEquals("Gillian Flynn", active.author)
        assertEquals("https://server/cover.jpg", active.coverUrl)
        assertEquals(bookLevel.audioChapters, active.audioChapters)
    }

    @Test
    fun activeBookSummaryForActiveFileFallsBackToBookWhenFileIdUnknown() {
        val bookLevel = file("f1", "https://server/stream/f1").book
        val files = listOf(file("f1", "https://server/stream/f1"))

        val active = activeBookSummaryForActiveFile(bookLevel, files, "unknown-file")

        assertEquals(bookLevel, active)
    }

    @Test
    fun absoluteProgressPercentDerivesPercentFromAbsolutePositionAndAggregateDuration() {
        assertEquals(50.0f, absoluteProgressPercent(150_000L, 300_000L))
        assertEquals(100.0f, absoluteProgressPercent(999_000L, 300_000L))
        assertEquals(0.0f, absoluteProgressPercent(-10L, 300_000L))
        assertNull(absoluteProgressPercent(100L, 0L))
    }

    @Test
    fun supportedAudioExtensionsMapToReadiumMediaTypes() {
        assertEquals(MediaType.MP4, readiumAudioMediaType("book.m4b"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("BOOK.M4A"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("m4b"))
        assertEquals(MediaType.MP4, readiumAudioMediaType("audio/x-m4b"))
        assertEquals(MediaType.MP3, readiumAudioMediaType("book.mp3"))
        assertEquals(MediaType.MP3, readiumAudioMediaType("audio/mpeg"))
        assertEquals(MediaType.AAC, readiumAudioMediaType("book.aac"))
        assertEquals(MediaType.AIFF, readiumAudioMediaType("book.aiff"))
        assertEquals(MediaType.FLAC, readiumAudioMediaType("book.flac"))
        assertEquals(MediaType.OGG, readiumAudioMediaType("book.ogg"))
        assertEquals(MediaType.OPUS, readiumAudioMediaType("book.opus"))
        assertEquals(MediaType.WAV, readiumAudioMediaType("book.wav"))
        assertEquals(MediaType.WEBM_AUDIO, readiumAudioMediaType("book.webm"))
    }

    @Test
    fun nonAudioExtensionsAreRejected() {
        assertNull(readiumAudioMediaType("book.epub"))
        assertNull(readiumAudioMediaType("book.cbz"))
        assertNull(readiumAudioMediaType("book.pdf"))
        assertNull(readiumAudioMediaType("book"))
    }

    @Test
    fun remoteAudioExtensionsMapToMedia3MimeTypes() {
        assertEquals(MimeTypes.AUDIO_MP4, media3AudioMimeType("book.m4b"))
        assertEquals(MimeTypes.AUDIO_MP4, media3AudioMimeType("BOOK.M4A"))
        assertEquals(MimeTypes.AUDIO_MPEG, media3AudioMimeType("book.mp3"))
        assertEquals(MimeTypes.AUDIO_FLAC, media3AudioMimeType("book.flac"))
        assertEquals(MimeTypes.AUDIO_OGG, media3AudioMimeType("book.ogg"))
        assertEquals(MimeTypes.AUDIO_OPUS, media3AudioMimeType("book.opus"))
        assertNull(media3AudioMimeType("book.epub"))
    }

    @Test
    fun audiobookCustomLayoutExposesTenSecondBackAndThirtySecondForwardSeeking() {
        val preferences = audiobookMediaButtonPreferences()

        assertEquals(2, preferences.size)
        assertEquals(CommandButton.ICON_SKIP_BACK_10, preferences[0].icon)
        assertEquals(AUDIO_SEEK_BACK_SESSION_ACTION, preferences[0].sessionCommand?.customAction)
        assertEquals(CommandButton.ICON_SKIP_FORWARD_30, preferences[1].icon)
        assertEquals(AUDIO_SEEK_FORWARD_SESSION_ACTION, preferences[1].sessionCommand?.customAction)
        assertEquals(10_000L, AUDIO_SEEK_BACK_INCREMENT_MS)
        assertEquals(30_000L, AUDIO_SEEK_FORWARD_INCREMENT_MS)
    }

    @Test
    fun readiumEngineUsesTheSameTimedSeekIncrements() {
        val configuration = audiobookReadiumEngineConfiguration()

        assertEquals(10.seconds, configuration.seekBackwardIncrement)
        assertEquals(30.seconds, configuration.seekForwardIncrement)
    }

    @Test
    fun audiobookChapterBoundsUseTheNextChapterOrTotalDuration() {
        val chapters = listOf(
            AudiobookChapter("Opening", 0L),
            AudiobookChapter("Middle", 100_000L),
            AudiobookChapter("Ending", 250_000L)
        )

        assertEquals(100_000L to 250_000L, audiobookChapterBounds(chapters, 150_000L, 400_000L))
        assertEquals(250_000L to 400_000L, audiobookChapterBounds(chapters, 300_000L, 400_000L))
        assertEquals(250_000L, audiobookChapterEndMs(chapters, 150_000L))
        assertEquals(null, audiobookChapterEndMs(chapters, 300_000L))
    }

    @Test
    fun audiobookChapterBoundsRejectUnknownDuration() {
        assertEquals(
            null,
            audiobookChapterBounds(
                listOf(AudiobookChapter("Opening", 0L)),
                positionMs = 0L,
                totalDurationMs = 0L
            )
        )
    }
}
