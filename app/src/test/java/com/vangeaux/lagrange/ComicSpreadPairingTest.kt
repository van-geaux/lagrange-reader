package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicSpreadPairingTest {
    private fun pairs(
        pageCount: Int,
        coverAlone: Boolean = true,
        shift: Boolean = false,
        wide: Set<Int> = emptySet()
    ): List<List<Int>> = comicSpreads(pageCount, coverAlone, shift) { it in wide }.map { it.pages }

    @Test
    fun `cover alone pairs the following pages like a printed book`() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5, 6)),
            pairs(7)
        )
    }

    @Test
    fun `cover alone with an even page count leaves the last page alone`() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)),
            pairs(6)
        )
    }

    @Test
    fun `without cover alone pages pair from the first page`() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), pairs(5, coverAlone = false))
    }

    @Test
    fun `shift moves every pair by one page`() {
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4, 5)),
            pairs(6, coverAlone = true, shift = true)
        )
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
            pairs(5, coverAlone = false, shift = true)
        )
    }

    @Test
    fun `wide pages stand alone and pairing restarts after them`() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5), listOf(6)),
            pairs(7, wide = setOf(3))
        )
    }

    @Test
    fun `a page before a wide page is not paired with it`() {
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)),
            pairs(5, wide = setOf(2))
        )
    }

    @Test
    fun `consecutive wide pages each stand alone`() {
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)),
            pairs(5, wide = setOf(1, 2))
        )
    }

    @Test
    fun `wide cover counts as the lone cover`() {
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), pairs(4, wide = setOf(0)))
    }

    @Test
    fun `tiny and empty books`() {
        assertEquals(emptyList<List<Int>>(), pairs(0))
        assertEquals(emptyList<List<Int>>(), pairs(-3))
        assertEquals(listOf(listOf(0)), pairs(1))
        assertEquals(listOf(listOf(0)), pairs(1, coverAlone = false))
        assertEquals(listOf(listOf(0), listOf(1)), pairs(2))
        assertEquals(listOf(listOf(0, 1)), pairs(2, coverAlone = false))
    }

    @Test
    fun `every page appears exactly once in reading order for many layouts`() {
        for (count in 0..41) {
            for (cover in listOf(true, false)) {
                for (shift in listOf(true, false)) {
                    val wide = (0 until count).filter { it % 7 == 5 || it % 11 == 3 }.toSet()
                    val flattened = pairs(count, cover, shift, wide).flatten()
                    assertEquals("count=$count cover=$cover shift=$shift", (0 until count).toList(), flattened)
                    comicSpreads(count, cover, shift) { it in wide }.forEach { spread ->
                        if (spread.isPair) {
                            assertFalse(spread.firstPage in wide)
                            assertFalse(spread.lastPage in wide)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `spread index lookup finds the spread showing a page`() {
        val spreads = comicSpreads(7, coverAlone = true, shift = false)

        assertEquals(0, comicSpreadIndexForPage(spreads, 0))
        assertEquals(1, comicSpreadIndexForPage(spreads, 1))
        assertEquals(1, comicSpreadIndexForPage(spreads, 2))
        assertEquals(3, comicSpreadIndexForPage(spreads, 6))
        assertEquals(3, comicSpreadIndexForPage(spreads, 99))
        assertEquals(0, comicSpreadIndexForPage(spreads, -4))
        assertEquals(0, comicSpreadIndexForPage(emptyList(), 3))
        for (page in 0 until 7) {
            assertTrue(page in spreads[comicSpreadIndexForPage(spreads, page)])
        }
    }

    @Test
    fun `right to left books show the earlier page on the right`() {
        val spread = ComicSpread(listOf(4, 5))

        assertEquals(listOf(4, 5), comicSpreadScreenOrder(spread, LibraryReadingDirection.LEFT_TO_RIGHT))
        assertEquals(listOf(5, 4), comicSpreadScreenOrder(spread, LibraryReadingDirection.RIGHT_TO_LEFT))
        assertEquals(listOf(7), comicSpreadScreenOrder(ComicSpread(listOf(7)), LibraryReadingDirection.RIGHT_TO_LEFT))
    }

    @Test
    fun `long press position resolves to the page under the finger`() {
        val spread = ComicSpread(listOf(4, 5))
        val ltr = LibraryReadingDirection.LEFT_TO_RIGHT
        val rtl = LibraryReadingDirection.RIGHT_TO_LEFT

        assertEquals(4, comicSpreadPageAt(spread, 100f, 1000f, ltr))
        assertEquals(5, comicSpreadPageAt(spread, 900f, 1000f, ltr))
        assertEquals(5, comicSpreadPageAt(spread, 100f, 1000f, rtl))
        assertEquals(4, comicSpreadPageAt(spread, 900f, 1000f, rtl))
        assertEquals(7, comicSpreadPageAt(ComicSpread(listOf(7)), 900f, 1000f, ltr))
        assertEquals(4, comicSpreadPageAt(spread, 10f, 0f, rtl))
    }

    @Test
    fun `resume uses the first page and progress uses the last visible page`() {
        val pair = ComicSpread(listOf(8, 9))
        val single = ComicSpread(listOf(0))

        assertEquals(8, comicSpreadResumePage(pair))
        assertEquals(9, comicSpreadProgressPage(pair))
        assertEquals(0, comicSpreadResumePage(single))
        assertEquals(0, comicSpreadProgressPage(single))
    }

    @Test
    fun `final spread reports the last page so progress reaches the end`() {
        val spreads = comicSpreads(9, coverAlone = true, shift = false)
        assertEquals(8, comicSpreadProgressPage(spreads.last()))
    }

    @Test
    fun `resume page reopens the same spread`() {
        val spreads = comicSpreads(20, coverAlone = true, shift = false)
        spreads.forEachIndexed { index, spread ->
            assertEquals(index, comicSpreadIndexForPage(spreads, comicSpreadResumePage(spread)))
        }
    }

    @Test
    fun `discovering a wide page keeps the current page on screen`() {
        val before = comicSpreads(10, coverAlone = true, shift = false)
        val current = before[comicSpreadIndexForPage(before, 5)]
        val after = comicSpreads(10, coverAlone = true, shift = false) { it == 4 }

        val index = comicSpreadIndexAfterRepairing(current, after)

        assertTrue(current.firstPage in after[index])
        assertEquals(0, comicSpreadIndexAfterRepairing(null, after))
    }

    @Test
    fun `aspect ratio classification`() {
        assertFalse(isWideComicPage(null))
        assertFalse(isWideComicPage(0.66f))
        assertFalse(isWideComicPage(0.99f))
        assertTrue(isWideComicPage(1.0f))
        assertTrue(isWideComicPage(1.4f))
        assertFalse(isWideComicPage(Float.NaN))
        assertFalse(isWideComicPage(Float.POSITIVE_INFINITY))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `spread rejects non consecutive pages`() {
        ComicSpread(listOf(2, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `spread rejects more than two pages`() {
        ComicSpread(listOf(1, 2, 3))
    }

    @Test
    fun `footer shows a page range for pairs`() {
        assertEquals(ComicFooterPages(2, 3, 40), comicReaderFooterPages(currentPage = 2, pageCount = 40, spreadFirstPage = 1))
        assertEquals(ComicFooterPages(null, 1, 40), comicReaderFooterPages(currentPage = 0, pageCount = 40, spreadFirstPage = 0))
        assertEquals(ComicFooterPages(null, 5, 40), comicReaderFooterPages(currentPage = 4, pageCount = 40))
        assertEquals(ComicFooterPages(null, 1, 1), comicReaderFooterPages(currentPage = 0, pageCount = 0))
    }
}
