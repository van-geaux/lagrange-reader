package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicSpreadLayoutTest {
    @Test
    fun `flat Fold8 spread splits the window in half with no gutter`() {
        val widths = comicSpreadWidths(2448, readerSpreadGutterPx(2448, hinge = null, minimumGapPx = 0))

        assertEquals(ComicSpreadWidths(leftPx = 1224, gutterPx = 0, rightPx = 1224, singlePx = 2448), widths)
        assertEquals(1224, widths.pairDecodePx)
    }

    @Test
    fun `odd width keeps every pixel`() {
        val widths = comicSpreadWidths(2449, readerSpreadGutterPx(2449, hinge = null, minimumGapPx = 0))

        assertEquals(2449, widths.leftPx + widths.gutterPx + widths.rightPx)
        assertEquals(1225, widths.pairDecodePx)
    }

    @Test
    fun `separating hinge is kept clear between the pages`() {
        val hinge = readerHingeFrom(
            isVertical = true,
            isHalfOpened = true,
            isSeparating = true,
            leftPx = 1200,
            rightPx = 1260
        )
        val widths = comicSpreadWidths(2448, readerSpreadGutterPx(2448, hinge, minimumGapPx = 0))

        assertEquals(1200, widths.leftPx)
        assertEquals(60, widths.gutterPx)
        assertEquals(1188, widths.rightPx)
        assertEquals(1200, widths.pairDecodePx)
    }

    @Test
    fun `degenerate widths stay non negative`() {
        assertEquals(ComicSpreadWidths(0, 0, 0, 1), comicSpreadWidths(0, 0 until 0))
        assertEquals(ComicSpreadWidths(0, 0, 0, 1), comicSpreadWidths(-10, 5 until 9))
        assertEquals(1, comicSpreadWidths(0, 0 until 0).pairDecodePx)
        val clamped = comicSpreadWidths(100, 90 until 400)
        assertEquals(ComicSpreadWidths(90, 10, 0, 100), clamped)
    }

    @Test
    fun `pairs decode at half width and lone pages at full width`() {
        val widths = comicSpreadWidths(2000, 1000 until 1000)

        assertEquals(1000, comicSpreadDecodeWidth(ComicSpread(listOf(3, 4)), widths))
        assertEquals(2000, comicSpreadDecodeWidth(ComicSpread(listOf(0)), widths))
    }

    @Test
    fun `prefetch warms nearest spreads first and stays in bounds`() {
        assertEquals(listOf(6, 4, 7, 3), comicSpreadPrefetchIndexes(settled = 5, spreadCount = 20, radius = 2))
        assertEquals(listOf(1, 2), comicSpreadPrefetchIndexes(settled = 0, spreadCount = 20, radius = 2))
        assertEquals(listOf(8, 7), comicSpreadPrefetchIndexes(settled = 9, spreadCount = 10, radius = 2))
        assertEquals(emptyList<Int>(), comicSpreadPrefetchIndexes(settled = 0, spreadCount = 1, radius = 2))
        assertEquals(emptyList<Int>(), comicSpreadPrefetchIndexes(settled = 3, spreadCount = 10, radius = 0))
    }
}
