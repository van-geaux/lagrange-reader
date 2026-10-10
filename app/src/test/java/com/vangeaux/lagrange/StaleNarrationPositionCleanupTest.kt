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
    fun `a cleanup pending at recreation completes in the recreated reader`() {
        val before = StaleNarrationPositionCleanup()
        before.serverProgressWon()
        before.readerMoved()
        assertFalse(before.shouldDrop(serviceConnected = false, narrationActive = idle))
        assertTrue(before.isPending)

        // the recreated reader restores the pending flag, then shows the reader and binds again
        val after = StaleNarrationPositionCleanup()
        if (before.isPending) after.serverProgressWon()
        assertFalse(after.shouldDrop(serviceConnected = true, narrationActive = idle))
        after.readerMoved()

        assertTrue(after.shouldDrop(serviceConnected = true, narrationActive = idle))
        assertFalse(after.isPending)
    }

    @Test
    fun `nothing is pending without a server resume or once decided`() {
        val cleanup = StaleNarrationPositionCleanup()
        assertFalse(cleanup.isPending)

        cleanup.serverProgressWon()
        cleanup.readerMoved()
        cleanup.shouldDrop(serviceConnected = true, narrationActive = narrating)

        assertFalse(cleanup.isPending)
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
