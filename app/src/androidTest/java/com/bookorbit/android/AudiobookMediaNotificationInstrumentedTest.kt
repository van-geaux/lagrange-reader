package com.bookorbit.android

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AudiobookMediaNotificationInstrumentedTest {
    @Test
    fun externalCommandsReplaceChapterSkipWithTimedSeeking() {
        val commands = audiobookExternalPlayerCommands(
            Player.Commands.Builder().addAllCommands().build()
        )

        assertTrue(commands.contains(Player.COMMAND_SEEK_BACK))
        assertTrue(commands.contains(Player.COMMAND_SEEK_FORWARD))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }

    @Test
    fun notificationUsesContiguousTimedSeekAndPlayPauseSlots() {
        val playPause = CommandButton.Builder(CommandButton.ICON_PLAY)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()
        val buttons = audiobookNotificationMediaButtons(
            playerCommands = audiobookExternalPlayerCommands(
                Player.Commands.Builder().addAllCommands().build()
            ),
            defaultButtons = listOf(playPause)
        )

        assertEquals(
            listOf(
                audiobookSeekBackSessionCommand,
                null,
                audiobookSeekForwardSessionCommand
            ),
            buttons.map(CommandButton::sessionCommand)
        )
        assertEquals(listOf(0, 1, 2), buttons.map { button ->
            button.extras.getInt(
                DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX
            )
        })
    }

    @Test
    fun sessionAuthorizesTimedSeekCommands() {
        val commands = audiobookAvailableSessionCommands()

        assertTrue(commands.contains(audiobookSeekBackSessionCommand))
        assertTrue(commands.contains(audiobookSeekForwardSessionCommand))
    }
}
