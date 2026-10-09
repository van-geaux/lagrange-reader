package com.vangeaux.lagrange

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTtsPlaybackServiceTest {
    @Test
    fun `inactive service exposes no stale media actions`() {
        val state = EpubTtsServiceState()

        assertEquals(PlaybackStateCompat.STATE_NONE, epubTtsMediaPlaybackState(state))
        assertEquals(0L, epubTtsMediaPlaybackActions(state))
    }

    @Test
    fun `preparing service exposes close without premature transport controls`() {
        val state = EpubTtsServiceState(readerKey = "book", isPreparing = true)
        val actions = epubTtsMediaPlaybackActions(state)

        assertEquals(PlaybackStateCompat.STATE_BUFFERING, epubTtsMediaPlaybackState(state))
        assertTrue(actions hasAction PlaybackStateCompat.ACTION_STOP)
        assertFalse(actions hasAction PlaybackStateCompat.ACTION_PLAY)
        assertFalse(actions hasAction PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
    }

    @Test
    fun `paused session exposes resumable bounded travel and close controls`() {
        val state = EpubTtsServiceState(
            readerKey = "book",
            isPlaying = false,
            canGoPrevious = true,
            canGoNext = false
        )
        val actions = epubTtsMediaPlaybackActions(state)

        assertEquals(PlaybackStateCompat.STATE_PAUSED, epubTtsMediaPlaybackState(state))
        assertTrue(actions hasAction PlaybackStateCompat.ACTION_PLAY)
        assertTrue(actions hasAction PlaybackStateCompat.ACTION_PAUSE)
        assertTrue(actions hasAction PlaybackStateCompat.ACTION_PLAY_PAUSE)
        assertTrue(actions hasAction PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        assertFalse(actions hasAction PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        assertTrue(actions hasAction PlaybackStateCompat.ACTION_STOP)
    }

    @Test
    fun `playing and failed sessions publish accurate media states`() {
        assertEquals(
            PlaybackStateCompat.STATE_PLAYING,
            epubTtsMediaPlaybackState(
                EpubTtsServiceState(readerKey = "book", isPlaying = true)
            )
        )
        val failed = EpubTtsServiceState(
            readerKey = "book",
            failure = EpubTtsFailureKind.GENERIC
        )
        assertEquals(PlaybackStateCompat.STATE_ERROR, epubTtsMediaPlaybackState(failed))
        assertEquals(PlaybackStateCompat.ACTION_STOP, epubTtsMediaPlaybackActions(failed))
    }

    @Test
    fun `reader commands are scoped to their owning reader session`() {
        val state = EpubTtsServiceState(ownerToken = "new-reader", readerKey = "book")

        assertTrue(epubTtsOwnerMatches(state, "new-reader"))
        assertFalse(epubTtsOwnerMatches(state, "old-reader"))
        assertTrue(epubTtsOwnerMatches(state, null))
    }

    @Test
    fun `lock screen title setting hides book metadata`() {
        val visible = EpubTtsServiceState(title = "Private book")
        val hidden = visible.copy(
            settings = visible.settings.copy(showBookTitleOnLockScreen = false)
        )

        assertEquals("Private book", epubTtsExposedTitle(visible))
        assertEquals("Text to speech", epubTtsExposedTitle(hidden))
    }

    @Test
    fun `notification shows the current utterance while TTS is active`() {
        val state = EpubTtsServiceState(
            readerKey = "book",
            isPlaying = true,
            utterance = "The sentence currently being read."
        )

        assertEquals(
            "The sentence currently being read.",
            epubTtsNotificationDetail(state, isPlaying = true)
        )
    }

    @Test
    fun `notification keeps status text when no utterance is available`() {
        assertEquals(
            "Reading aloud",
            epubTtsNotificationDetail(
                EpubTtsServiceState(readerKey = "book", isPlaying = true),
                isPlaying = true
            )
        )
    }

    @Test
    fun `new start and cancellation invalidate every older start request`() {
        val first = EpubTtsRequestSession.begin()
        val second = EpubTtsRequestSession.begin()

        assertFalse(EpubTtsRequestSession.isCurrent(first))
        assertTrue(EpubTtsRequestSession.isCurrent(second))

        EpubTtsRequestSession.cancel(first)
        assertTrue(EpubTtsRequestSession.isCurrent(second))

        EpubTtsRequestSession.cancel(second)
        assertFalse(EpubTtsRequestSession.isCurrent(second))
    }

    @Test
    fun `account epoch captured before suspension becomes stale after logout`() {
        val beforeLogout = EpubTtsAccountSession.currentEpoch()

        EpubTtsAccountSession.invalidate()

        assertFalse(EpubTtsAccountSession.isCurrent(beforeLogout))
        assertTrue(EpubTtsAccountSession.isCurrent(EpubTtsAccountSession.currentEpoch()))
    }

    private infix fun Long.hasAction(action: Long): Boolean = this and action != 0L
}
