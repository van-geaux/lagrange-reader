package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleNarrationPositionCleanupTest {
    private val idle = { false }
    private val narrating = { true }

    @Test
    fun `position is dropped when the service connects after the reader moved`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.serverProgressWon()

        cleanup.readerMoved()
        assertFalse(cleanup.shouldDrop(serviceConnected = false, narrationActive = idle))

        assertTrue(cleanup.shouldDrop(serviceConnected = true, narrationActive = idle))
    }

    @Test
    fun `position is dropped when the reader moves after the service connected`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.serverProgressWon()

        assertFalse(cleanup.shouldDrop(serviceConnected = true, narrationActive = idle))

        cleanup.readerMoved()
        assertTrue(cleanup.shouldDrop(serviceConnected = true, narrationActive = idle))
    }

    @Test
    fun `position of narration still running is kept`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.serverProgressWon()
        cleanup.readerMoved()

        assertFalse(cleanup.shouldDrop(serviceConnected = true, narrationActive = narrating))
    }

    @Test
    fun `narration is only checked once the service is connected`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.serverProgressWon()
        cleanup.readerMoved()
        var checked = false

        cleanup.shouldDrop(serviceConnected = false) { checked = true; false }

        assertFalse(checked)
    }

    @Test
    fun `decision is made once per server resume`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.serverProgressWon()
        cleanup.readerMoved()
        assertFalse(cleanup.shouldDrop(serviceConnected = true, narrationActive = narrating))

        // narration started later on this screen writes a position that must survive reconnects
        cleanup.readerMoved()
        assertFalse(cleanup.shouldDrop(serviceConnected = true, narrationActive = idle))
    }

    @Test
    fun `nothing is dropped without a server resume`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.readerMoved()

        assertFalse(cleanup.shouldDrop(serviceConnected = true, narrationActive = idle))
    }

    @Test
    fun `a reader move before the server resume does not count`() {
        val cleanup = StaleNarrationPositionCleanup()
        cleanup.readerMoved()
        cleanup.serverProgressWon()

        assertFalse(cleanup.shouldDrop(serviceConnected = true, narrationActive = idle))
    }
}
