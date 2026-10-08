package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSpreadReaderTest {
    @Test
    fun `landscape viewport pairs portrait pages and keeps landscape pages alone`() {
        val pages = listOf(
            PdfSpreadPage(0, 0.7f),
            PdfSpreadPage(1, 0.7f),
            PdfSpreadPage(2, 1.4f),
            PdfSpreadPage(3, 0.7f),
            PdfSpreadPage(4, 0.7f)
        )

        val spreads = buildPdfSpreads(pages, viewportWidthPx = 1920, viewportHeightPx = 1080)

        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3, 4)), spreads.map { it.pages.map(PdfSpreadPage::index) })
    }

    @Test
    fun `portrait viewport keeps every page in its own row`() {
        val pages = listOf(PdfSpreadPage(0, 0.7f), PdfSpreadPage(1, 0.7f))

        val spreads = buildPdfSpreads(pages, viewportWidthPx = 1080, viewportHeightPx = 1920)

        assertEquals(listOf(listOf(0), listOf(1)), spreads.map { it.pages.map(PdfSpreadPage::index) })
    }

    @Test
    fun `pairing is rejected when the two pages do not fit the viewport`() {
        val pages = listOf(PdfSpreadPage(0, 0.8f), PdfSpreadPage(1, 0.8f))

        val spreads = buildPdfSpreads(pages, viewportWidthPx = 4, viewportHeightPx = 3)

        assertEquals(listOf(listOf(0), listOf(1)), spreads.map { it.pages.map(PdfSpreadPage::index) })
    }
}
