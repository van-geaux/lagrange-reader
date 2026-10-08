package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSpreadSwipeTest {
    @Test
    fun `vertical swipe up advances and swipe down goes back`() {
        assertEquals(
            PdfSpreadSwipeDirection.NEXT,
            pdfSpreadSwipeDirection(deltaX = 2f, deltaY = -80f, vertical = true, rightToLeft = false)
        )
        assertEquals(
            PdfSpreadSwipeDirection.PREVIOUS,
            pdfSpreadSwipeDirection(deltaX = 2f, deltaY = 80f, vertical = true, rightToLeft = false)
        )
    }

    @Test
    fun `horizontal swipe follows reading direction`() {
        assertEquals(
            PdfSpreadSwipeDirection.NEXT,
            pdfSpreadSwipeDirection(deltaX = -80f, deltaY = 2f, vertical = false, rightToLeft = false)
        )
        assertEquals(
            PdfSpreadSwipeDirection.NEXT,
            pdfSpreadSwipeDirection(deltaX = 80f, deltaY = 2f, vertical = false, rightToLeft = true)
        )
    }

    @Test
    fun `cross axis swipe is ignored`() {
        assertEquals(
            PdfSpreadSwipeDirection.IGNORE,
            pdfSpreadSwipeDirection(deltaX = 80f, deltaY = 2f, vertical = true, rightToLeft = false)
        )
    }
}
