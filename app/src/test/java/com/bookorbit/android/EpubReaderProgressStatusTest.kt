package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubReaderProgressStatusTest {
    @Test
    fun `reader footer reports layout derived whole book pages`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 2,
            chapterCount = 10,
            pageIndex = 3,
            pageCount = 12,
            chapterPageCounts = List(10) { 12 }
        )

        assertEquals(23.333f, status.completionPercent, 0.001f)
        assertEquals(28, status.bookPageNumber)
        assertEquals(120, status.bookPageCount)
        assertEquals("23% · Chapter 3/10 · Page 4/12 · Book 28/120", status.displayText())
        assertEquals(
            "Book completion 23 percent; chapter 3 of 10; chapter page 4 of 12; book page 28 of 120",
            status.accessibilityText()
        )
    }

    @Test
    fun `reader footer reaches the actual final page`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 4,
            chapterCount = 5,
            pageIndex = 7,
            pageCount = 8,
            chapterPageCounts = listOf(3, 4, 2, 6, 8)
        )

        assertEquals(100f, status.completionPercent, 0f)
        assertEquals(23, status.bookPageNumber)
        assertEquals(23, status.bookPageCount)
    }

    @Test
    fun `whole book completion weights chapters by measured page count`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 1,
            chapterCount = 3,
            pageIndex = 0,
            pageCount = 5,
            chapterPageCounts = listOf(2, 5, 3)
        )

        assertEquals(30f, status.completionPercent, 0.001f)
        assertEquals(3, status.bookPageNumber)
        assertEquals(10, status.bookPageCount)
    }

    @Test
    fun `reader footer avoids a fake total while layout pages are calculating`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 0,
            chapterCount = 3,
            pageIndex = 0,
            pageCount = 2
        )

        assertEquals(null, status.bookPageNumber)
        assertEquals(null, status.bookPageCount)
        assertEquals("17% · Chapter 1/3 · Page 1/2 · Book pages calculating…", status.displayText())
    }
}
