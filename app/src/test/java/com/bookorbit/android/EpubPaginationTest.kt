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
        assertFalse(rendered.contains("column-width"))
    }
}
