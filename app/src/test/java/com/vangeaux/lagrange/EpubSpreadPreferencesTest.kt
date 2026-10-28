package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class EpubSpreadPreferencesTest {
    @Test
    fun `spread css properties force two columns independent of the 60em media query`() {
        val css = epubSpreadRsProperties(twoPageSpread = true).toCssProperties()
            .filterValues { it != null }

        assertEquals("2", css["--RS__colCount"])
        assertEquals("auto", css["--RS__colWidth"])
        // 1px unit: pageMargins then carries the page margin in CSS px.
        assertEquals("1px", css["--RS__pageGutter"])
        // A column gap would break Readium's viewport-width pagination.
        assertFalse(css.containsKey("--RS__colGap"))
        assertEquals(setOf("--RS__colCount", "--RS__colWidth", "--RS__pageGutter"), css.keys)
    }

    @Test
    fun `single page css properties are Readium defaults`() {
        assertTrue(
            epubSpreadRsProperties(twoPageSpread = false).toCssProperties()
                .filterValues { it != null }
                .isEmpty()
        )
    }

    @Test
    fun `spread is active only for paginated layout`() {
        assertTrue(epubSpreadActive(true, ReaderLayoutMode.PAGINATED))
        assertFalse(epubSpreadActive(true, ReaderLayoutMode.CONTINUOUS))
        assertFalse(epubSpreadActive(false, ReaderLayoutMode.PAGINATED))
        assertFalse(epubSpreadActive(false, ReaderLayoutMode.CONTINUOUS))
    }

    @Test
    fun `spread moves horizontal margins from the container into each page`() {
        assertEquals(10f to 30f, epubHorizontalPaddingPercents(10f, 30f, twoPageSpread = false))
        assertEquals(0f to 100f, epubHorizontalPaddingPercents(-5f, 150f, twoPageSpread = false))
        assertEquals(0f to 0f, epubHorizontalPaddingPercents(10f, 30f, twoPageSpread = true))
        assertEquals(10f to 30f, epubHorizontalPaddingPercents(10f, 30f, twoPageSpread = true, fixedLayout = true))
    }

    @Test
    fun `spread page margin matches the single page margin for the same setting`() {
        // Fold8-sized window, 841dp wide: 15% gives 841 * 15 / 400 dp on every page edge.
        assertEquals(841 * 15 / 400.0, epubSpreadPageMarginCssPx(841, 15f, 15f), 0.0001)
        // Uneven left/right settings use their mean so both pages stay symmetric.
        assertEquals(841 * 20 / 400.0, epubSpreadPageMarginCssPx(841, 10f, 30f), 0.0001)
        // Larger setting, larger margins.
        assertTrue(epubSpreadPageMarginCssPx(841, 40f, 40f) > epubSpreadPageMarginCssPx(841, 15f, 15f))
    }

    @Test
    fun `spread page margin never lets the two pages touch`() {
        assertEquals(EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX, epubSpreadPageMarginCssPx(841, 0f, 0f), 0.0)
        assertEquals(EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX, epubSpreadPageMarginCssPx(841, -5f, -5f), 0.0)
        // Capped so each page keeps at least half its width for text.
        assertEquals(841 / 8.0, epubSpreadPageMarginCssPx(841, 150f, 150f), 0.0001)
        assertEquals(841 / 8.0, epubSpreadPageMarginCssPx(841, 100f, 100f), 0.0001)
        assertEquals(841 / 8.0, epubSpreadPageMarginCssPx(841, 50f, 50f), 0.0001)
    }
}
