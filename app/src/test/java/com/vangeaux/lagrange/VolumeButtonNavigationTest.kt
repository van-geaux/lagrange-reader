package com.vangeaux.lagrange

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeButtonNavigationTest {
    @Test
    fun `volume keys map to logical next and previous by default`() {
        assertEquals(
            VolumeButtonNavigationAction.NEXT,
            volumeButtonNavigationAction(KeyEvent.KEYCODE_VOLUME_DOWN)
        )
        assertEquals(
            VolumeButtonNavigationAction.PREVIOUS,
            volumeButtonNavigationAction(KeyEvent.KEYCODE_VOLUME_UP)
        )
        assertNull(volumeButtonNavigationAction(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `reversing volume buttons swaps logical actions`() {
        assertEquals(
            VolumeButtonNavigationAction.PREVIOUS,
            volumeButtonNavigationAction(KeyEvent.KEYCODE_VOLUME_DOWN, reverse = true)
        )
        assertEquals(
            VolumeButtonNavigationAction.NEXT,
            volumeButtonNavigationAction(KeyEvent.KEYCODE_VOLUME_UP, reverse = true)
        )
    }

    @Test
    fun `active audiobook session disables volume page navigation`() {
        assertFalse(volumeButtonNavigationEnabled(readerEnabled = true, audiobookSessionActive = true))
        assertTrue(volumeButtonNavigationEnabled(readerEnabled = true, audiobookSessionActive = false))
        assertFalse(volumeButtonNavigationEnabled(readerEnabled = false, audiobookSessionActive = false))
    }

    @Test
    fun `navigation target clamps at reader boundaries`() {
        assertNull(
            volumeButtonNavigationTarget(
                VolumeButtonNavigationAction.PREVIOUS,
                currentIndex = 0,
                pageCount = 3
            )
        )
        assertEquals(
            1,
            volumeButtonNavigationTarget(
                VolumeButtonNavigationAction.NEXT,
                currentIndex = 0,
                pageCount = 3
            )
        )
        assertNull(
            volumeButtonNavigationTarget(
                VolumeButtonNavigationAction.NEXT,
                currentIndex = 2,
                pageCount = 3
            )
        )
        assertNull(
            volumeButtonNavigationTarget(
                VolumeButtonNavigationAction.NEXT,
                currentIndex = 0,
                pageCount = 0
            )
        )
    }
}
