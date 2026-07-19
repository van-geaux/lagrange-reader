package com.bookorbit.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPaginationTest {
    @Test
    fun `fresh reader padding defaults top to thirty and other edges to fifteen`() {
        assertEquals(
            EpubPaddingPercentages(top = 30f, bottom = 15f, left = 15f, right = 15f),
            EpubPaddingPercentages()
        )
    }

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
        assertTrue(rendered.contains("strip = document.createElement('main')"))
        assertTrue(rendered.contains("left: 3.75vw"))
        assertTrue(rendered.contains("top: 7.50vh"))
        assertTrue(rendered.contains("width: calc(100vw - 7.50vw)"))
        assertTrue(rendered.contains("height: calc(100vh - 11.25vh)"))
        assertTrue(rendered.contains("min-height: calc(100vh - 11.25vh)"))
        assertTrue(rendered.contains("overflow: visible"))
        assertFalse(rendered.contains("#bookorbit-page-viewport"))
        assertFalse(rendered.contains("position: fixed !important"))
        assertFalse(rendered.contains("column-width"))
    }

    @Test
    fun `epub image sizing preserves full page svg dimensions`() {
        val rendered = styleEpubHtml(
            html = """
                <svg width="100%" height="100%" viewBox="0 0 1200 1800">
                  <image width="1200" height="1800" href="/Images/illustration.jpg" />
                </svg>
            """.trimIndent(),
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false
        )

        val svgRules = rendered.substringAfter("svg {").substringBefore('}')
        assertTrue(rendered.contains("img {"))
        assertTrue(rendered.contains("object-fit: contain"))
        assertTrue(rendered.contains("svg {"))
        assertFalse(rendered.contains("img, svg"))
        assertFalse(svgRules.contains("height: auto"))
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
        assertTrue(rendered.contains("left: 1.25vw"))
        assertTrue(rendered.contains("top: 2.50vh"))
        assertTrue(rendered.contains("width: calc(100vw - 7.50vw)"))
        assertTrue(rendered.contains("height: calc(100vh - 7.50vh)"))
        assertTrue(rendered.contains("window.BookOrbitReaderLayout = Object.freeze"))
        assertTrue(rendered.contains("goToPage: jumpToPage"))
    }

    @Test
    fun `epub page jump command stays within the current chapter`() {
        val rendered = styleEpubHtml(
            html = "<p>Readable chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1f,
            startAtEnd = false
        )

        assertTrue(rendered.contains("page = Math.min(Math.max(0, Math.round(target)), pageCount() - 1)"))
        assertTrue(epubPageJumpJavascript(7).contains("goToPage(7)"))
        assertTrue(epubPageJumpJavascript(-4).contains("goToPage(0)"))
    }

    @Test
    fun `vertical padding stays outside the known good WebView renderer`() {
        val contentPadding = EpubPaddingPercentages(
            top = 80f,
            bottom = 60f,
            left = 20f,
            right = 30f
        ).forWebViewContent()

        assertEquals(0f, contentPadding.top, 0f)
        assertEquals(0f, contentPadding.bottom, 0f)
        assertEquals(20f, contentPadding.left, 0f)
        assertEquals(30f, contentPadding.right, 0f)
    }

    @Test
    fun `epub padding updates visible overflow page geometry without reloading`() {
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
        assertTrue(update.contains("BookOrbitReaderLayout.refresh()"))
        assertTrue(rendered.contains("const contentOffset = page * pageHeight()"))
        assertTrue(rendered.contains("applyPageGeometry()"))
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

    @Test
    fun `epub measurement html reports a layout settled chapter page count`() {
        val rendered = styleEpubHtml(
            html = "<p>Measured chapter text</p>",
            theme = EpubReaderTheme.Sepia,
            fontScale = 1.2f,
            startAtEnd = false,
            measurementChapterIndex = 4
        )

        assertTrue(rendered.contains("const measurementChapterIndex = 4"))
        assertTrue(rendered.contains("document.fonts.ready"))
        assertTrue(rendered.contains("image.addEventListener('load'"))
        assertTrue(rendered.contains("bridge.chapterPageCount(measurementChapterIndex, pageCount())"))
    }
}
