package com.vangeaux.lagrange

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeButtonNavigationTest {
    @Test
    fun `volume keys map to logical previous and next`() {
        assertEquals(
            VolumeButtonNavigationAction.PREVIOUS,
            volumeButtonNavigationAction(KeyEvent.KEYCODE_VOLUME_DOWN)
        )
        assertEquals(
            VolumeButtonNavigationAction.NEXT,
            volumeButtonNavigationAction(KeyEvent.KEYCODE_VOLUME_UP)
        )
        assertNull(volumeButtonNavigationAction(KeyEvent.KEYCODE_BACK))
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
