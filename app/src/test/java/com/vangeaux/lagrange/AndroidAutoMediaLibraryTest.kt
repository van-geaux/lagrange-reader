package com.vangeaux.lagrange

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoMediaLibraryTest {

    private fun audioBook(
        id: String,
        title: String,
        author: String? = "Author",
        libraryId: String = "lib-1",
        fileId: String? = "file-$id",
        localPath: String? = null,
        progressPercent: Float? = null,
        progressPositionMs: Long? = null,
        readStatus: BookReadStatus? = null,
        isRead: Boolean = false,
        seriesId: String? = null,
        seriesName: String? = null,
        seriesIndex: Double? = null,
        addedAtMillis: Long? = 1_000L,
        lastReadAtMillis: Long? = null,
        chapters: List<AudiobookChapter> = emptyList()
    ): BookSummary = BookSummary(
        libraryId = libraryId,
        id = id,
        fileId = fileId,
        title = title,
        author = author,
        format = "m4b",
        mediaKind = MediaKind.AUDIO,
        streamUrl = "https://server.example/api/v1/books/$id/stream",
        localPath = localPath,
        progressPercent = progressPercent,
        progressPositionMs = progressPositionMs,
        readStatus = readStatus,
        isRead = isRead,
        seriesId = seriesId,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        addedAtMillis = addedAtMillis,
        lastReadAtMillis = lastReadAtMillis,
        audioChapters = chapters,
        availableFormats = listOf("AUDIO"),
        downloadedFormats = if (localPath != null) listOf("AUDIO") else emptyList()
    )

    @Test
    fun mediaIdCodecRoundTripsAllBrowseAndPlaybackTargets() {
        val ids = listOf(
            AndroidAutoMediaId.Root,
            AndroidAutoMediaId.TabContinue,
            AndroidAutoMediaId.TabDownloaded,
            AndroidAutoMediaId.TabRecent,
            AndroidAutoMediaId.TabLibraries,
            AndroidAutoMediaId.ActiveChapters,
            AndroidAutoMediaId.SeriesRoot,
            AndroidAutoMediaId.Library("lib:sci-fi/1"),
            AndroidAutoMediaId.Series("series:expanse/1"),
            AndroidAutoMediaId.BookChapters("book:42", "file:99"),
            AndroidAutoMediaId.Book("book:42", "file:99"),
            AndroidAutoMediaId.Book("book:42", null),
            AndroidAutoMediaId.Chapter("book:42", "file:99", chapterIndex = 3, startMs = 185_000L)
        )

        ids.forEach { expected ->
            val encoded = expected.encode()
            assertEquals(expected, AndroidAutoMediaId.parse(encoded))
        }
        assertNull(AndroidAutoMediaId.parse(null))
        assertNull(AndroidAutoMediaId.parse(""))
        assertNull(AndroidAutoMediaId.parse("unknown:123"))
    }

    @Test
    fun rootTabsExposeFourPrimaryAndroidAutoDestinations() {
        val tabs = buildAndroidAutoRootTabSpecs()

        assertEquals(
            listOf(
                ANDROID_AUTO_TAB_CONTINUE_ID,
                ANDROID_AUTO_TAB_DOWNLOADED_ID,
                ANDROID_AUTO_TAB_RECENT_ID,
                ANDROID_AUTO_TAB_LIBRARIES_ID
            ),
            tabs.map(AndroidAutoNodeSpec::mediaId)
        )
        assertTrue(tabs.all(AndroidAutoNodeSpec::isBrowsable))
        assertTrue(tabs.none(AndroidAutoNodeSpec::isPlayable))
    }

    @Test
    fun audiobookCandidateRecognizesStandaloneAudioAndMultiformatAudioBooks() {
        val standaloneAudio = audioBook(id = "1", title = "Standalone Audio")
        val epubWithAudio = BookSummary(
            libraryId = "lib-1",
            id = "2",
            fileId = "epub-file",
            title = "Hybrid Book",
            mediaKind = MediaKind.EPUB,
            availableFormats = listOf("EPUB", "AUDIO")
        )
        val epubOnly = BookSummary(
            libraryId = "lib-1",
            id = "3",
            fileId = "epub-only",
            title = "Text Only",
            mediaKind = MediaKind.EPUB,
            availableFormats = listOf("EPUB")
        )

        assertTrue(isAudiobookCandidate(standaloneAudio))
        assertTrue(isAudiobookCandidate(epubWithAudio))
        assertFalse(isAudiobookCandidate(epubOnly))
    }

    @Test
    fun continueListeningPlacesActiveBookAndItsChaptersFirstThenInProgressByRecency() {
        val active = audioBook(
            id = "b1",
            title = "Project Hail Mary",
            progressPercent = 42f,
            progressPositionMs = 120_000L,
            chapters = listOf(
                AudiobookChapter("Chapter 1", 0L),
                AudiobookChapter("Chapter 2", 60_000L)
            )
        )
        val olderInProgress = audioBook(
            id = "b2",
            title = "Children of Time",
            progressPercent = 20f,
            lastReadAtMillis = 1_000L
        )
        val newerInProgress = audioBook(
            id = "b3",
            title = "Leviathan Wakes",
            progressPercent = 65f,
            lastReadAtMillis = 5_000L
        )
        val untouched = audioBook(
            id = "b4",
            title = "Unread Book",
            progressPercent = null
        )

        val nodes = buildAndroidAutoContinueListeningSpecs(
            activeBook = active,
            catalogBooks = listOf(olderInProgress, newerInProgress, untouched),
            localBooks = emptyList()
        )

        assertEquals(4, nodes.size)
        assertEquals("Project Hail Mary", nodes[0].title)
        assertTrue(nodes[0].isPlayable)
        assertEquals("Chapters · Project Hail Mary", nodes[1].title)
        assertTrue(nodes[1].isBrowsable)
        assertFalse(nodes[1].isPlayable)
        assertEquals("Leviathan Wakes", nodes[2].title)
        assertEquals("Children of Time", nodes[3].title)
    }

    @Test
    fun downloadedTabDeduplicatesMultipartLocalAudioAndMarksItemsDownloaded() {
        val part1 = audioBook(
            id = "b1",
            title = "Multipart Book",
            fileId = "part-1",
            localPath = "/tmp/part1.mp3"
        )
        val part2 = audioBook(
            id = "b1",
            title = "Multipart Book",
            fileId = "part-2",
            localPath = "/tmp/part2.mp3"
        )

        val nodes = buildAndroidAutoDownloadedSpecs(
            localBooks = listOf(part1, part2)
        )

        assertEquals(1, nodes.size)
        assertEquals("Multipart Book", nodes.single().title)
        assertTrue(nodes.single().isDownloaded)
    }

    @Test
    fun seriesAndLibraryBuildersOrderSeriesBooksByIndex() {
        val book2 = audioBook(
            id = "b2",
            title = "Caliban's War",
            seriesId = "s1",
            seriesName = "The Expanse",
            seriesIndex = 2.0
        )
        val book1 = audioBook(
            id = "b1",
            title = "Leviathan Wakes",
            seriesId = "s1",
            seriesName = "The Expanse",
            seriesIndex = 1.0
        )

        val seriesFolders = buildAndroidAutoSeriesFolderSpecs(
            catalogBooks = listOf(book2, book1),
            localBooks = emptyList()
        )
        val seriesBooks = buildAndroidAutoSeriesBookSpecs(
            seriesId = "s1",
            catalogBooks = listOf(book2, book1),
            localBooks = emptyList()
        )

        assertEquals(1, seriesFolders.size)
        assertEquals("The Expanse", seriesFolders.single().title)
        assertEquals(listOf("Leviathan Wakes", "Caliban's War"), seriesBooks.map { it.title })
    }

    @Test
    fun chapterSpecsReflectCompletedActiveAndUpcomingChapterProgress() {
        val book = audioBook(
            id = "b1",
            title = "Dune",
            progressPositionMs = 150_000L,
            chapters = listOf(
                AudiobookChapter("Prologue", 0L),
                AudiobookChapter("Arrakis", 100_000L),
                AudiobookChapter("Spice", 200_000L)
            )
        )

        val chapters = buildAndroidAutoChapterSpecs(
            book = book,
            currentPositionMs = 150_000L,
            totalDurationMs = 300_000L
        )

        assertEquals(3, chapters.size)
        assertEquals(
            AndroidAutoCompletionStatus.FULLY_PLAYED,
            chapters[0].completionState.status
        )
        assertEquals(
            AndroidAutoCompletionStatus.PARTIALLY_PLAYED,
            chapters[1].completionState.status
        )
        assertEquals(0.5, chapters[1].completionState.completionFraction, 0.01)
        assertEquals(
            AndroidAutoCompletionStatus.NOT_PLAYED,
            chapters[2].completionState.status
        )
        assertEquals(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER, chapters[1].mediaType)
    }

    @Test
    fun chapterSkipRestartsCurrentChapterAfterThresholdOrMovesToAdjacentChapter() {
        val chapters = listOf(
            AudiobookChapter("Ch 1", 0L),
            AudiobookChapter("Ch 2", 60_000L),
            AudiobookChapter("Ch 3", 120_000L)
        )

        assertEquals(
            60_000L,
            resolveAndroidAutoChapterSkip(
                chapters,
                currentPositionMs = 75_000L,
                totalDurationMs = 180_000L,
                direction = -1
            )
        )
        assertEquals(
            0L,
            resolveAndroidAutoChapterSkip(
                chapters,
                currentPositionMs = 61_500L,
                totalDurationMs = 180_000L,
                direction = -1
            )
        )
        assertEquals(
            120_000L,
            resolveAndroidAutoChapterSkip(
                chapters,
                currentPositionMs = 75_000L,
                totalDurationMs = 180_000L,
                direction = 1
            )
        )
        assertNull(
            resolveAndroidAutoChapterSkip(
                chapters,
                currentPositionMs = 130_000L,
                totalDurationMs = 180_000L,
                direction = 1
            )
        )
    }

    @Test
    fun voiceSearchRanksExactTitleAndAuthorMatchesAbovePartialMatches() {
        val exact = audioBook(id = "1", title = "Dune", author = "Frank Herbert")
        val sequel = audioBook(id = "2", title = "Dune Messiah", author = "Frank Herbert")
        val unrelated = audioBook(id = "3", title = "Foundation", author = "Isaac Asimov")

        val results = searchAndroidAutoBooks(
            query = "Dune",
            catalogBooks = listOf(unrelated, sequel, exact),
            localBooks = emptyList()
        )

        assertEquals(listOf("1", "2"), results.map { it.id })
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun singleItemPlayer_preservesPreviousNextOutsideAuto_andUses30sControlsInAuto() {
        var autoConnected = false
        var posMs = 100_000L
        val stubPlayer = java.lang.reflect.Proxy.newProxyInstance(
            androidx.media3.common.Player::class.java.classLoader,
            arrayOf(androidx.media3.common.Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "isCommandAvailable" -> false // Simulate single-item player that omits prev/next
                "getCurrentPosition", "getContentPosition", "getContentBufferedPosition", "getBufferedPosition" -> posMs
                "getDuration", "getContentDuration" -> 600_000L
                "getCurrentMediaItemIndex", "getCurrentPeriodIndex" -> 0
                "getMediaItemCount" -> 1
                "getCurrentTimeline" -> androidx.media3.common.Timeline.EMPTY
                "getMediaMetadata", "getPlaylistMetadata" -> androidx.media3.common.MediaMetadata.EMPTY
                "getPlaybackParameters" -> androidx.media3.common.PlaybackParameters.DEFAULT
                "seekTo" -> {
                    posMs = if (args != null && args.size == 1 && args[0] is Long) {
                        args[0] as Long
                    } else if (args != null && args.size >= 2 && args[1] is Long) {
                        args[1] as Long
                    } else {
                        posMs
                    }
                    null
                }
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 1.0f
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            }
        } as androidx.media3.common.Player

        val wrapper = LagrangeLibrarySessionPlayer(
            delegate = stubPlayer,
            isAutoConnectedProvider = { autoConnected }
        )

        // 1. Outside Auto (Phone / Bluetooth on single-item book):
        //    Previous/Next commands are preserved even when the underlying player omits them,
        //    and transport skip actions jump -10s / +30s.
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_BACK))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_FORWARD))
        assertTrue(wrapper.hasPreviousMediaItem())
        assertTrue(wrapper.hasNextMediaItem())

        wrapper.seekToPrevious()
        assertEquals(90_000L, posMs) // -10s on phone/Bluetooth
        wrapper.seekToNext()
        assertEquals(120_000L, posMs) // +30s on phone/Bluetooth

        // 2. Connected to Android Auto:
        //    Previous/Next commands are stripped so circular -30s/+30s buttons take the primary slots,
        //    while hardware vehicle skip buttons jump -30s / +30s.
        autoConnected = true
        assertFalse(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_BACK))
        assertTrue(wrapper.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_FORWARD))

        wrapper.seekToPrevious()
        assertEquals(90_000L, posMs) // -30s on vehicle skip back
        wrapper.seekToNext()
        assertEquals(120_000L, posMs) // +30s on vehicle skip forward
        wrapper.seekToPreviousMediaItem()
        assertEquals(90_000L, posMs) // -30s on vehicle media-item skip back
        wrapper.seekToNextMediaItem()
        assertEquals(120_000L, posMs) // +30s on vehicle media-item skip forward
        wrapper.seekBackAuto()
        assertEquals(90_000L, posMs) // -30s on explicit Auto seek-back command

        // 3. Confirm distinct session commands and 10s vs 30s increments for Phone vs Android Auto
        assertEquals(10_000L, AUDIO_SEEK_BACK_INCREMENT_MS)
        assertEquals(30_000L, ANDROID_AUTO_SEEK_BACK_INCREMENT_MS)
        assertEquals(30_000L, AUDIO_SEEK_FORWARD_INCREMENT_MS)
        assertEquals(AUDIO_SEEK_BACK_SESSION_ACTION, audiobookSeekBackSessionCommand.customAction)
        assertEquals(AUDIO_SEEK_BACK_30_SESSION_ACTION, audiobookAutoSeekBackSessionCommand.customAction)
        assertEquals(AUDIO_SEEK_FORWARD_SESSION_ACTION, audiobookSeekForwardSessionCommand.customAction)
    }
}
