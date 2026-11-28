package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.navigator.media.tts.android.AndroidTtsEngine

@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
class AndroidTtsVoiceCompatibilityTest {
    @Test
    fun `playback is blocked while the metadata warning is pending and allowed after acceptance`() {
        val warningPending = EpubTtsServiceState(
            readerKey = "book",
            voiceCompatibilityConfirmationRequired = true
        )
        val accepted = warningPending.copy(voiceCompatibilityConfirmationRequired = false)

        assertFalse(epubTtsMayStartPlayback(warningPending))
        assertEquals(
            android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP,
            epubTtsMediaPlaybackActions(warningPending)
        )
        assertTrue(epubTtsMayStartPlayback(accepted))
        assertTrue(
            (epubTtsMediaPlaybackActions(accepted) and
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY) != 0L
        )
    }

    @Test
    fun `recognized Android qualities retain their Readium quality without warning`() {
        val cases = listOf(
            500 to AndroidTtsEngine.Voice.Quality.Highest,
            400 to AndroidTtsEngine.Voice.Quality.High,
            300 to AndroidTtsEngine.Voice.Quality.Normal,
            200 to AndroidTtsEngine.Voice.Quality.Low,
            100 to AndroidTtsEngine.Voice.Quality.Lowest
        )

        cases.forEach { (rawQuality, expected) ->
            val result = compatibleAndroidTtsVoiceQuality(rawQuality)
            assertEquals(expected, result.quality)
            assertFalse(result.requiresConfirmation)
        }
    }

    @Test
    fun `unknown Android qualities use normal metadata and require confirmation`() {
        listOf(-1, 0, 501).forEach { rawQuality ->
            val result = compatibleAndroidTtsVoiceQuality(rawQuality)
            assertEquals(AndroidTtsEngine.Voice.Quality.Normal, result.quality)
            assertTrue(result.requiresConfirmation)
        }
    }
}
