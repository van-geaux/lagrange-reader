package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadiumPdfProgressTest {
    @Test
    fun `corrects the PDFium page index reported one page high`() {
        assertEquals(0, pdfiumVisiblePageIndex(reportedPageIndex = 1, pageCount = 3))
        assertEquals(1, pdfiumVisiblePageIndex(reportedPageIndex = 2, pageCount = 3))
        assertEquals(2, pdfiumVisiblePageIndex(reportedPageIndex = 3, pageCount = 3))
    }

    @Test
    fun `clamps PDFium page index correction to the document bounds`() {
        assertEquals(0, pdfiumVisiblePageIndex(reportedPageIndex = 0, pageCount = 3))
        assertEquals(2, pdfiumVisiblePageIndex(reportedPageIndex = 4, pageCount = 3))
    }
}
