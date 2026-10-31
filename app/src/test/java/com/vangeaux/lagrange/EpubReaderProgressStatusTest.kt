package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubReaderProgressStatusTest {
    @Test
    fun `footer keeps canonical completion separate from rendered pages`() {
        val narrow = epubReaderProgressStatus(
            chapterIndex = 2,
            chapterCount = 10,
            pageIndex = 3,
            pageCount = 12,
            completionPercent = 22.689f,
            overallPositionNumber = 28,
            overallPositionCount = 120
        )
        val wide = epubReaderProgressStatus(
            chapterIndex = 2,
            chapterCount = 10,
            pageIndex = 1,
            pageCount = 5,
            completionPercent = 22.689f,
            overallPositionNumber = 28,
            overallPositionCount = 120
        )

        assertEquals(narrow.completionPercent, wide.completionPercent, 0f)
        assertEquals(narrow.overallPositionNumber, wide.overallPositionNumber)
        assertEquals(narrow.overallPositionCount, wide.overallPositionCount)
        assertEquals("23% · Chapter 3/10 · Page 4/12 · Overall 28/120", narrow.displayText())
        assertEquals(
            "Book completion 23 percent; chapter 3 of 10; chapter page 4 of 12; " +
                "overall page 28 of 120",
            narrow.accessibilityText()
        )
    }

    @Test
    fun `continuous footer omits layout page`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 1,
            chapterCount = 3,
            pageIndex = 8,
            pageCount = 20,
            completionPercent = 40f,
            overallPositionNumber = 4,
            overallPositionCount = 10,
            layoutMode = ReaderLayoutMode.CONTINUOUS
        )

        assertEquals("40% · Chapter 2/3 · Overall 4/10", status.displayText())
        assertEquals(
            "Book completion 40 percent; chapter 2 of 3; overall page 4 of 10",
            status.accessibilityText()
        )
    }

    @Test
    fun `footer omits unavailable overall position`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 0,
            chapterCount = 3,
            pageIndex = 0,
            pageCount = 2,
            completionPercent = 0f
        )

        assertNull(status.overallPositionNumber)
        assertNull(status.overallPositionCount)
        assertEquals("0% · Chapter 1/3 · Page 1/2", status.displayText())
    }

    @Test
    fun `overall page is clamped without changing completion`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 0,
            chapterCount = 1,
            pageIndex = 0,
            pageCount = 1,
            completionPercent = 37f,
            overallPositionNumber = 99,
            overallPositionCount = 8
        )

        assertEquals(37f, status.completionPercent, 0f)
        assertEquals(8, status.overallPositionNumber)
        assertEquals(8, status.overallPositionCount)
    }
}
