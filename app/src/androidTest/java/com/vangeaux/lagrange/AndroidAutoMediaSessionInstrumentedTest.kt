package com.vangeaux.lagrange

import androidx.media3.session.CommandButton
import androidx.media3.session.MediaConstants
import androidx.media3.session.SessionCommand
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaSessionInstrumentedTest {

    @Test
    fun sessionCommandsAuthorizeLibraryBrowseSearchAndChapterNavigation() {
        val commands = audiobookAvailableSessionCommands()

        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
        assertTrue(commands.contains(audiobookSeekBackSessionCommand))
        assertTrue(commands.contains(audiobookSeekForwardSessionCommand))
        assertTrue(commands.contains(audiobookPreviousChapterSessionCommand))
        assertTrue(commands.contains(audiobookNextChapterSessionCommand))
    }

    @Test
    fun androidAutoCustomLayoutAppendsChapterButtonsWhenChaptersExist() {
        val withoutChapters = audiobookAutoMediaButtonPreferences(hasChapters = false)
        val withChapters = audiobookAutoMediaButtonPreferences(hasChapters = true)

        assertEquals(3, withoutChapters.size)
        assertEquals(5, withChapters.size)
        assertEquals(CommandButton.ICON_SKIP_BACK_30, withChapters[0].icon)
        assertEquals(audiobookSeekBackSessionCommand, withChapters[0].sessionCommand)
        assertEquals(CommandButton.ICON_SKIP_FORWARD_30, withChapters[1].icon)
        assertEquals(audiobookSeekForwardSessionCommand, withChapters[1].sessionCommand)
        assertEquals(CommandButton.ICON_PREVIOUS, withChapters[2].icon)
        assertEquals(audiobookPreviousChapterSessionCommand, withChapters[2].sessionCommand)
        assertEquals(CommandButton.ICON_NEXT, withChapters[3].icon)
        assertEquals(audiobookNextChapterSessionCommand, withChapters[3].sessionCommand)
    }

    @Test
    fun nodeSpecConversionPopulatesCompletionDownloadAndContentStyleExtras() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-42",
            fileId = "file-1",
            title = "Neuromancer",
            author = "William Gibson",
            format = "m4b",
            mediaKind = MediaKind.AUDIO,
            localPath = "/data/user/0/com.vangeaux.lagrange/files/downloads/neuromancer.m4b",
            progressPercent = 50f,
            progressPositionMs = 600_000L,
            readStatus = BookReadStatus.READING
        )

        val item = bookToAndroidAutoNodeSpec(book, groupTitle = "In Progress")
            .toMediaItem("com.vangeaux.lagrange")
        val extras = requireNotNull(item.mediaMetadata.extras)

        assertEquals("Neuromancer", item.mediaMetadata.title.toString())
        assertEquals("William Gibson", item.mediaMetadata.artist.toString())
        assertEquals(
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
            extras.getInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS)
        )
        assertEquals(
            0.5,
            extras.getDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE),
            0.01
        )
        assertEquals(
            MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED,
            extras.getLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS)
        )
        assertEquals(
            "In Progress",
            extras.getString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE)
        )
        assertEquals(
            "content://com.vangeaux.lagrange.auto.covers/book/book-42?libraryId=lib-1",
            item.mediaMetadata.artworkUri.toString()
        )
    }
}
