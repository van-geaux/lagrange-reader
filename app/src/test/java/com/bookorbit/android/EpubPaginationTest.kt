package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        assertTrue(rendered.contains("maximum-scale=1.0"))
        assertTrue(rendered.contains("strip.scrollHeight / pageHeight()"))
        assertTrue(rendered.contains("touchstart"))
        assertTrue(rendered.contains("touchend"))
        assertTrue(rendered.contains("deltaX < 0 ? page + 1 : page - 1"))
        assertTrue(rendered.contains("suppressClick"))
        assertTrue(rendered.contains("const initialPage = 0"))
        assertTrue(rendered.contains("#bookorbit-page-strip"))
        assertTrue(rendered.contains("const viewport = () => document.body"))
        assertTrue(rendered.contains("strip = document.createElement('div')"))
        assertTrue(rendered.contains("left: var(--bookorbit-reader-left)"))
        assertTrue(rendered.contains("top: var(--bookorbit-reader-top)"))
        assertTrue(rendered.contains("width: calc(100vw - 7.50vw)"))
        assertTrue(rendered.contains("height: calc(100vh - 7.50vh)"))
        assertTrue(rendered.contains("overflow: hidden !important"))
        assertTrue(rendered.contains("overflow: visible !important"))
        assertTrue(rendered.contains("body.getBoundingClientRect().height"))
        assertFalse(rendered.contains("column-width"))
    }

    @Test
    fun `epub padding percentages map to a quarter of the viewport`() {
        val rendered = styleEpubHtml(
            html = "<p>Readable chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false,
            topPaddingPercent = 10f,
            bottomPaddingPercent = 20f,
            leftPaddingPercent = 5f,
            rightPaddingPercent = 25f
        )

        assertEquals(25f, epubPaddingViewportPercent(100f), 0f)
        assertEquals(2.5f, epubPaddingViewportPercent(10f), 0f)
        assertTrue(rendered.contains("--bookorbit-reader-left: 1.25vw"))
        assertTrue(rendered.contains("--bookorbit-reader-top: 2.50vh"))
        assertTrue(rendered.contains("--bookorbit-reader-bottom: 5.00vh"))
        assertTrue(rendered.contains("--bookorbit-reader-right: 6.25vw"))
        assertTrue(rendered.contains("width: calc(100vw - 7.50vw)"))
        assertTrue(rendered.contains("height: calc(100vh - 7.50vh)"))
        assertTrue(rendered.contains("window.BookOrbitReaderLayout = Object.freeze"))
    }

    @Test
    fun `epub padding updates the clipped viewport and repaginates without reloading`() {
        val update = epubPaddingUpdateJavascript(
            EpubPaddingPercentages(
                top = 10f,
                bottom = 20f,
                left = 5f,
                right = 25f
            )
        )
        val rendered = styleEpubHtml(
            html = "<p>Readable chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false
        )

        assertTrue(update.contains("setInsets(2.50, 5.00, 1.25, 6.25)"))
        assertTrue(rendered.contains("const contentOffset = page * pageHeight()"))
        assertTrue(rendered.contains("applyViewportInsets()"))
        assertTrue(rendered.contains("repaginateFromOffset(contentOffset)"))
    }

    @Test
    fun `epub html starts at the requested page`() {
        val rendered = styleEpubHtml(
            html = "<p>Readable chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false,
            initialPage = 7
        )

        assertTrue(rendered.contains("const initialPage = 7"))
        assertTrue(rendered.contains("Math.min(initialPage, pageCount() - 1)"))
    }
}
