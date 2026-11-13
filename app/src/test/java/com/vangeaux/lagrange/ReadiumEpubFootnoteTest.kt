package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class ReadiumEpubFootnoteTest {
    @Test
    fun `footnote context exposes the sanitized note content`() {
        val context = HyperlinkNavigator.FootnoteContext("<p>Footnote <em>text</em></p>")

        assertEquals("<p>Footnote <em>text</em></p>", epubFootnoteNoteContent(context))
    }

    @Test
    fun `missing link context is not treated as a footnote`() {
        assertNull(epubFootnoteNoteContent(null))
        assertTrue(!isEpubFootnoteContext(null))
    }

    @Test
    fun `blank footnote context still counts as an in-place note`() {
        val context = HyperlinkNavigator.FootnoteContext("   ")

        assertTrue(isEpubFootnoteContext(context))
        assertEquals("   ", epubFootnoteNoteContent(context))
    }
}
