package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubServerProgressPreferenceTest {
    private fun prefer(
        serverPercent: Float? = 62f,
        lastReported: Float? = 40f,
        locatorPercent: Float? = 40f
    ) = shouldPreferServerProgress(serverPercent, lastReported, locatorPercent)

    @Test
    fun `server progress written elsewhere and ahead of the locator wins`() {
        assertTrue(prefer(serverPercent = 62f, lastReported = 40f, locatorPercent = 40f))
    }

    @Test
    fun `server progress that went backwards never pulls the reader back`() {
        assertFalse(prefer(serverPercent = 20f, lastReported = 40f, locatorPercent = 40f))
        assertFalse(prefer(serverPercent = 0f, lastReported = 40f, locatorPercent = 40f))
    }

    @Test
    fun `server progress equal to what this device reported is its own write`() {
        // e.g. narration reported an equal-chapter estimate that differs from the exact locator
        assertFalse(prefer(serverPercent = 25f, lastReported = 25f, locatorPercent = 12f))
        // the server may round or truncate what it stores
        assertFalse(prefer(serverPercent = 25f, lastReported = 25.6f, locatorPercent = 12f))
        assertTrue(prefer(serverPercent = 25f, lastReported = 26.1f, locatorPercent = 12f))
    }

    @Test
    fun `a lead within renderer drift keeps the locator`() {
        assertFalse(prefer(serverPercent = 61.9f, lastReported = 40f, locatorPercent = 60f))
        assertTrue(prefer(serverPercent = 62.1f, lastReported = 40f, locatorPercent = 60f))
    }

    @Test
    fun `with nothing reported by this device server progress ahead of the locator wins`() {
        assertTrue(prefer(serverPercent = 62f, lastReported = null, locatorPercent = 40f))
        assertFalse(prefer(serverPercent = 40f, lastReported = null, locatorPercent = 55f))
        assertFalse(prefer(serverPercent = 41f, lastReported = null, locatorPercent = 40f))
    }

    @Test
    fun `locator is kept when it cannot be compared`() {
        assertFalse(prefer(locatorPercent = null))
    }

    @Test
    fun `locator is kept when no fresh server progress is known`() {
        assertFalse(prefer(serverPercent = null))
    }

    @Test
    fun `progression is read from the positions of the same resource`() {
        val positions = listOf(0.0 to 0.30, 0.25 to 0.33, 0.5 to 0.36, 0.75 to 0.39)
        assertEquals(0.36, progressionFromPositions(0.6, positions)!!, 0.0001)
        assertEquals(0.30, progressionFromPositions(null, positions)!!, 0.0001)
    }

    @Test
    fun `progression with no usable positions cannot be compared`() {
        assertNull(progressionFromPositions(0.5, emptyList()))
        assertNull(progressionFromPositions(0.5, listOf(0.0 to null)))
    }
}
