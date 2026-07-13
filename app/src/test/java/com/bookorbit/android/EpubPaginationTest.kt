package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPaginationTest {
    @Test
    fun `epub pagination uses full viewport strip and swipe navigation`() {
        val rendered = styleEpubHtml(
            html = "<p>Readable chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false
        )

        assertTrue(rendered.contains("const pageHeight = ()"))
        assertTrue(rendered.contains("strip.scrollHeight / pageHeight()"))
        assertTrue(rendered.contains("touchstart"))
        assertTrue(rendered.contains("touchend"))
        assertTrue(rendered.contains("deltaX < 0 ? page + 1 : page - 1"))
        assertTrue(rendered.contains("suppressClick"))
        assertTrue(rendered.contains("left: 36px"))
        assertTrue(rendered.contains("top: 40px"))
        assertFalse(rendered.contains("column-width"))
    }

    @Test
    fun `epub padding presets change the rendered page inset`() {
        val rendered = styleEpubHtml(
            html = "<p>Readable chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false,
            padding = EpubReaderPadding.Wide
        )

        assertTrue(rendered.contains("left: 48px"))
        assertTrue(rendered.contains("top: 52px"))
        assertTrue(rendered.contains("window.innerHeight - 104"))
    }
}
