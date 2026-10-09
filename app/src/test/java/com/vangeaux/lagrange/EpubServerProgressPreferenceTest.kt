package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubServerProgressPreferenceTest {
    private val savedAt = 1_000_000_000L
    private val hourLater = savedAt + 3_600_000L

    private fun prefer(
        serverPercent: Float? = 62f,
        serverUpdatedAt: Long? = hourLater,
        locatorPercent: Float? = 40f,
        locatorSavedAt: Long? = savedAt
    ) = shouldPreferServerProgress(serverPercent, serverUpdatedAt, locatorPercent, locatorSavedAt)

    @Test
    fun `newer and clearly different server progress wins`() {
        assertTrue(prefer())
    }

    @Test
    fun `newer server progress that went backwards also wins`() {
        assertTrue(prefer(serverPercent = 20f, locatorPercent = 40f))
    }

    @Test
    fun `locator is kept when the server progress is older than the locator`() {
        assertFalse(prefer(serverUpdatedAt = savedAt - 3_600_000L))
    }

    @Test
    fun `locator is kept when the server stamp is within clock tolerance`() {
        assertFalse(prefer(serverUpdatedAt = savedAt + SERVER_PROGRESS_CLOCK_TOLERANCE_MS))
        assertTrue(prefer(serverUpdatedAt = savedAt + SERVER_PROGRESS_CLOCK_TOLERANCE_MS + 1))
    }

    @Test
    fun `locator is kept when a clock skewed echo of its own sync points at the same place`() {
        assertFalse(prefer(serverPercent = 40.5f, serverUpdatedAt = hourLater))
    }

    @Test
    fun `locator is kept within renderer drift`() {
        assertFalse(prefer(serverPercent = 41.9f, locatorPercent = 40f))
        assertTrue(prefer(serverPercent = 42.1f, locatorPercent = 40f))
    }

    @Test
    fun `locator without a save time is older than any server progress`() {
        assertTrue(prefer(locatorSavedAt = null))
        assertFalse(prefer(locatorSavedAt = null, serverPercent = 40.5f))
    }

    @Test
    fun `locator is kept when it cannot be compared fairly`() {
        assertFalse(prefer(locatorPercent = null))
    }

    @Test
    fun `locator is kept when no fresh server progress is known`() {
        assertFalse(prefer(serverPercent = null))
        assertFalse(prefer(serverUpdatedAt = null))
    }
}
