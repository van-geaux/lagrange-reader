package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubServerProgressPreferenceTest {
    private val knownStamp = 1_000L
    private val newStamp = 2_000L

    private fun prefer(
        serverPercent: Float? = 62f,
        serverUpdatedAt: Long? = newStamp,
        locatorPercent: Float? = 40f,
        knownUpdatedAt: Long? = knownStamp
    ) = shouldPreferServerProgress(serverPercent, serverUpdatedAt, locatorPercent, knownUpdatedAt)

    @Test
    fun `changed and clearly different server progress wins`() {
        assertTrue(prefer())
    }

    @Test
    fun `changed server progress that went backwards also wins`() {
        assertTrue(prefer(serverPercent = 20f, locatorPercent = 40f))
    }

    @Test
    fun `locator is kept when nobody wrote since this device last looked`() {
        assertFalse(prefer(serverUpdatedAt = knownStamp, serverPercent = 90f))
    }

    @Test
    fun `locator is kept when this device's own write points at the same place`() {
        assertFalse(prefer(serverPercent = 40.5f, locatorPercent = 40f))
    }

    @Test
    fun `locator is kept within renderer drift`() {
        assertFalse(prefer(serverPercent = 41.9f, locatorPercent = 40f))
        assertTrue(prefer(serverPercent = 42.1f, locatorPercent = 40f))
    }

    @Test
    fun `without a remembered time only server progress ahead of the locator wins`() {
        assertTrue(prefer(knownUpdatedAt = null, serverPercent = 62f, locatorPercent = 40f))
        assertFalse(prefer(knownUpdatedAt = null, serverPercent = 40f, locatorPercent = 55f))
        assertFalse(prefer(knownUpdatedAt = null, serverPercent = 41f, locatorPercent = 40f))
    }

    @Test
    fun `locator is kept when it cannot be compared`() {
        assertFalse(prefer(locatorPercent = null))
    }

    @Test
    fun `locator is kept when no fresh server progress is known`() {
        assertFalse(prefer(serverPercent = null))
        assertFalse(prefer(serverUpdatedAt = null))
    }

    @Test
    fun `locator total progression is used when present`() {
        assertEquals(
            0.4,
            locatorTotalProgression(0.4, 0.5, listOf(0.0 to 0.1, 0.9 to 0.2))!!,
            0.0001
        )
    }

    @Test
    fun `missing total progression is read from the positions of the same resource`() {
        val positions = listOf(0.0 to 0.30, 0.25 to 0.33, 0.5 to 0.36, 0.75 to 0.39)
        assertEquals(0.36, locatorTotalProgression(null, 0.6, positions)!!, 0.0001)
        assertEquals(0.30, locatorTotalProgression(null, null, positions)!!, 0.0001)
    }

    @Test
    fun `missing total progression with no usable positions cannot be compared`() {
        assertNull(locatorTotalProgression(null, 0.5, emptyList()))
        assertNull(locatorTotalProgression(null, 0.5, listOf(0.0 to null)))
    }
}
