package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubReaderProgressStatusTest {
    @Test
    fun `reader footer reports completion chapter page and normalized book location`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 2,
            chapterCount = 10,
            pageIndex = 3,
            pageCount = 12
        )

        assertEquals(23.333f, status.completionPercent, 0.001f)
        assertEquals(233, status.bookLocation)
        assertEquals("23% · Chapter 3/10 · Page 4/12 · Book 233/1000", status.displayText())
        assertEquals(
            "Book completion 23 percent; chapter 3 of 10; chapter page 4 of 12; book location 233 of 1000",
            status.accessibilityText()
        )
    }

    @Test
    fun `reader footer reaches the final location on the final page`() {
        val status = epubReaderProgressStatus(
            chapterIndex = 4,
            chapterCount = 5,
            pageIndex = 7,
            pageCount = 8
        )

        assertEquals(100f, status.completionPercent, 0f)
        assertEquals(EPUB_BOOK_LOCATION_TOTAL, status.bookLocation)
    }
}
