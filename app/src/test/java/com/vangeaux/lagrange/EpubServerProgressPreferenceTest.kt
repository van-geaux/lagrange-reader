package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubServerProgressPreferenceTest {
    @Test
    fun `server progress well ahead of the saved locator wins`() {
        assertTrue(isServerProgressAheadOfLocator(serverPercent = 62f, locatorPercent = 40f))
    }

    @Test
    fun `saved locator is kept when server is behind or equal`() {
        assertFalse(isServerProgressAheadOfLocator(serverPercent = 40f, locatorPercent = 62f))
        assertFalse(isServerProgressAheadOfLocator(serverPercent = 40f, locatorPercent = 40f))
    }

    @Test
    fun `saved locator is kept within renderer drift`() {
        assertFalse(isServerProgressAheadOfLocator(serverPercent = 40.8f, locatorPercent = 40f))
        assertTrue(isServerProgressAheadOfLocator(serverPercent = 41.5f, locatorPercent = 40f))
    }

    @Test
    fun `saved locator is kept when no server progress is known`() {
        assertFalse(isServerProgressAheadOfLocator(serverPercent = null, locatorPercent = 40f))
    }
}
