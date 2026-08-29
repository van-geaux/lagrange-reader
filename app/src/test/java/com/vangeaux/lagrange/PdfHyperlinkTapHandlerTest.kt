package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHyperlinkTapHandlerTest {
    @Test
    fun `http and https links open externally`() {
        assertEquals(
            PdfHyperlinkTarget.External("https://example.com/read"),
            classifyPdfHyperlink(destinationPageIndex = null, uri = "https://example.com/read")
        )
        assertEquals(
            PdfHyperlinkTarget.External("http://example.com/read"),
            classifyPdfHyperlink(destinationPageIndex = null, uri = "http://example.com/read")
        )
    }

    @Test
    fun `internal destination takes precedence over uri`() {
        assertEquals(
            PdfHyperlinkTarget.Internal(4),
            classifyPdfHyperlink(destinationPageIndex = 4, uri = "https://example.com/read")
        )
    }

    @Test
    fun `unsupported and malformed targets are ignored`() {
        assertEquals(
            PdfHyperlinkTarget.External("mailto:reader@example.com"),
            classifyPdfHyperlink(destinationPageIndex = null, uri = "mailto:reader@example.com")
        )
        assertNull(classifyPdfHyperlink(destinationPageIndex = null, uri = "javascript:alert(1)"))
        assertNull(classifyPdfHyperlink(destinationPageIndex = null, uri = "   "))
        assertNull(classifyPdfHyperlink(destinationPageIndex = -1, uri = null))
    }

    @Test
    fun `matched link consumes tap without invoking navigation fallback`() {
        var fallbackInvoked = false

        val consumed = routePdfTap(
            target = PdfHyperlinkTarget.External("https://example.com/read"),
            openExternal = { false },
            openInternal = { false },
            fallback = {
                fallbackInvoked = true
                true
            }
        )

        assertTrue(consumed)
        assertFalse(fallbackInvoked)
    }

    @Test
    fun `unmatched tap delegates to navigation fallback`() {
        var fallbackInvoked = false

        val consumed = routePdfTap(
            target = null,
            openExternal = { false },
            openInternal = { false },
            fallback = {
                fallbackInvoked = true
                true
            }
        )

        assertTrue(consumed)
        assertTrue(fallbackInvoked)
    }
}
