package com.vangeaux.lagrange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSpreadReaderAxisTest {
    @Test
    fun `landscape spread reader uses vertical navigation`() {
        assertTrue(pdfSpreadUsesVerticalNavigation(viewportWidthPx = 1920, viewportHeightPx = 1080))
    }

    @Test
    fun `portrait spread reader uses horizontal navigation`() {
        assertFalse(pdfSpreadUsesVerticalNavigation(viewportWidthPx = 1080, viewportHeightPx = 1920))
    }
}
