package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChromePositionStateTest {
    @Test
    fun `continuous progression is clamped to normalized resource range`() {
        assertEquals(0f, normalizedReaderProgression(null))
        assertEquals(0f, normalizedReaderProgression(-0.2f))
        assertEquals(0.42f, normalizedReaderProgression(0.42f))
        assertEquals(1f, normalizedReaderProgression(1.4f))
        assertEquals(0f, normalizedReaderProgression(Float.NaN))
    }

    @Test
    fun `continuous rail stays enabled with a single reported item`() {
        assertTrue(readerChromeProgressionState(0f, hasSelectionCallback = true).enabled)
        assertFalse(readerChromeProgressionState(null, hasSelectionCallback = true).enabled)
        assertFalse(readerChromeProgressionState(0.5f, hasSelectionCallback = false).enabled)
    }

    @Test
    fun `position rail targets three quarters of the reader height`() {
        assertEquals(0.75f, READER_POSITION_CONTROL_HEIGHT_FRACTION)
    }

    @Test
    fun `first position enables only next`() {
        val state = readerChromePositionState(currentIndex = 0, itemCount = 4)

        assertEquals(0, state.currentIndex)
        assertEquals(4, state.itemCount)
        assertFalse(state.canGoPrevious)
        assertTrue(state.canGoNext)
    }

    @Test
    fun `middle position enables both directions`() {
        val state = readerChromePositionState(currentIndex = 2, itemCount = 4)

        assertEquals(2, state.currentIndex)
        assertTrue(state.canGoPrevious)
        assertTrue(state.canGoNext)
    }

    @Test
    fun `last and invalid positions are clamped safely`() {
        val last = readerChromePositionState(currentIndex = 99, itemCount = 4)
        assertEquals(3, last.currentIndex)
        assertTrue(last.canGoPrevious)
        assertFalse(last.canGoNext)

        val empty = readerChromePositionState(currentIndex = -5, itemCount = 0)
        assertEquals(0, empty.currentIndex)
        assertEquals(1, empty.itemCount)
        assertFalse(empty.canGoPrevious)
        assertFalse(empty.canGoNext)
    }
}
