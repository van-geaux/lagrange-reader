package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSpreadPolicyTest {
    // Representative window sizes (dp). Fold8 inner display is 4:3 and landscape when unfolded.
    private val fold8InnerLandscape = ReaderWindowState(widthDp = 933, heightDp = 680)
    private val fold8InnerLandscapeLowDensity = ReaderWindowState(widthDp = 1224, heightDp = 900)
    private val fold8InnerPortrait = ReaderWindowState(widthDp = 704, heightDp = 909)
    private val fold8CoverPortrait = ReaderWindowState(widthDp = 384, heightDp = 640)
    private val fold8CoverLandscape = ReaderWindowState(widthDp = 712, heightDp = 360)
    private val fold8InnerSplitHalf = ReaderWindowState(widthDp = 462, heightDp = 680)
    private val fold8UltraLandscape = ReaderWindowState(widthDp = 960, heightDp = 840)
    private val phoneLandscape = ReaderWindowState(widthDp = 915, heightDp = 388)
    private val tabletLandscape = ReaderWindowState(widthDp = 1280, heightDp = 776)

    private fun spread(
        window: ReaderWindowState,
        mode: ReaderSpreadMode = ReaderSpreadMode.AUTO,
        layout: ReaderLayoutMode = ReaderLayoutMode.PAGINATED
    ) = shouldUseTwoPageSpread(mode, window, layout)

    @Test
    fun `unfolded Fold8 in its natural landscape posture shows a spread`() {
        assertTrue(spread(fold8InnerLandscape))
        assertTrue(spread(fold8InnerLandscapeLowDensity))
    }

    @Test
    fun `unfolded Fold8 rotated to portrait keeps a single page`() {
        assertFalse(spread(fold8InnerPortrait))
    }

    @Test
    fun `cover screen never shows a spread in either orientation`() {
        assertFalse(spread(fold8CoverPortrait))
        assertFalse(spread(fold8CoverLandscape))
    }

    @Test
    fun `phone landscape is wide but too short for a spread`() {
        assertFalse(spread(phoneLandscape))
    }

    @Test
    fun `split screen half of the inner display keeps a single page`() {
        assertFalse(spread(fold8InnerSplitHalf))
    }

    @Test
    fun `other large horizontal windows also spread`() {
        assertTrue(spread(fold8UltraLandscape))
        assertTrue(spread(tabletLandscape))
    }

    @Test
    fun `square window does not spread`() {
        assertFalse(spread(ReaderWindowState(widthDp = 800, heightDp = 800)))
    }

    @Test
    fun `short side boundary is inclusive at 600dp`() {
        assertTrue(spread(ReaderWindowState(widthDp = 900, heightDp = READER_SPREAD_MIN_SHORT_SIDE_DP)))
        assertFalse(spread(ReaderWindowState(widthDp = 900, heightDp = READER_SPREAD_MIN_SHORT_SIDE_DP - 1)))
    }

    @Test
    fun `off preference always keeps a single page`() {
        assertFalse(spread(fold8InnerLandscape, mode = ReaderSpreadMode.OFF))
        assertFalse(spread(tabletLandscape, mode = ReaderSpreadMode.OFF))
    }

    @Test
    fun `continuous layout never spreads`() {
        assertFalse(spread(fold8InnerLandscape, layout = ReaderLayoutMode.CONTINUOUS))
    }

    @Test
    fun `unknown or zero window size never spreads`() {
        assertFalse(spread(ReaderWindowState(widthDp = 0, heightDp = 0)))
        assertFalse(spread(ReaderWindowState(widthDp = 1000, heightDp = 0)))
    }

    @Test
    fun `flat vertical hinge keeps the spread`() {
        val window = fold8InnerLandscape.copy(
            hinge = readerHingeFrom(
                isVertical = true,
                isHalfOpened = false,
                isSeparating = false,
                leftPx = 1224,
                rightPx = 1224
            )
        )
        assertTrue(spread(window))
    }

    @Test
    fun `half opened book posture keeps the spread`() {
        val window = fold8InnerLandscape.copy(
            hinge = readerHingeFrom(
                isVertical = true,
                isHalfOpened = true,
                isSeparating = true,
                leftPx = 1224,
                rightPx = 1224
            )
        )
        assertTrue(spread(window))
    }

    @Test
    fun `half opened tabletop posture keeps a single page`() {
        val window = tabletLandscape.copy(
            hinge = readerHingeFrom(
                isVertical = false,
                isHalfOpened = true,
                isSeparating = true,
                leftPx = 0,
                rightPx = 2560
            )
        )
        assertFalse(spread(window))
    }

    @Test
    fun `flat horizontal hinge does not block a spread`() {
        val window = tabletLandscape.copy(
            hinge = readerHingeFrom(
                isVertical = false,
                isHalfOpened = false,
                isSeparating = false,
                leftPx = 0,
                rightPx = 2560
            )
        )
        assertTrue(spread(window))
    }

    @Test
    fun `gutter is centred when there is no separating hinge`() {
        assertEquals(1214 until 1234, readerSpreadGutterPx(2448, hinge = null, minimumGapPx = 20))
        val flatHinge = readerHingeFrom(true, false, false, 1224, 1224)
        assertEquals(1214 until 1234, readerSpreadGutterPx(2448, flatHinge, minimumGapPx = 20))
    }

    @Test
    fun `gutter covers a wide separating vertical hinge exactly`() {
        val hinge = readerHingeFrom(true, true, true, 1300, 1380)
        assertEquals(1300 until 1380, readerSpreadGutterPx(2700, hinge, minimumGapPx = 20))
    }

    @Test
    fun `gutter is centred on a narrow separating hinge with the minimum gap`() {
        val hinge = readerHingeFrom(true, true, true, 1220, 1228)
        assertEquals(1214 until 1234, readerSpreadGutterPx(2448, hinge, minimumGapPx = 20))
    }

    @Test
    fun `horizontal or out of bounds hinges are ignored for the gutter`() {
        val horizontal = readerHingeFrom(false, true, true, 0, 2448)
        assertEquals(1214 until 1234, readerSpreadGutterPx(2448, horizontal, minimumGapPx = 20))
        val outside = readerHingeFrom(true, true, true, 2500, 2600)
        assertEquals(1214 until 1234, readerSpreadGutterPx(2448, outside, minimumGapPx = 20))
    }

    @Test
    fun `gutter follows the hinge when the reader is inset from the window edge`() {
        // Hinge at 1300–1380 in the window; the reader starts 100px in, so the hinge is at 1200–1280.
        val hinge = readerHingeFrom(true, true, true, 1300, 1380).inReaderCoordinates(100)
        assertEquals(1200 until 1280, readerSpreadGutterPx(2600, hinge, minimumGapPx = 20))
        val same = readerHingeFrom(true, true, true, 1300, 1380)
        assertEquals(same, same.inReaderCoordinates(0))
    }

    @Test
    fun `zero gap gutter is empty and gap never exceeds window`() {
        assertTrue(readerSpreadGutterPx(2448, null, minimumGapPx = 0).isEmpty())
        assertEquals(0 until 10, readerSpreadGutterPx(10, null, minimumGapPx = 50))
        assertTrue(readerSpreadGutterPx(-5, null, minimumGapPx = 50).isEmpty())
    }

    @Test
    fun `spread mode storage round trips and defaults to automatic`() {
        ReaderSpreadMode.entries.forEach { mode ->
            assertEquals(mode, readerSpreadModeFromStorage(readerSpreadModeStorageValue(mode)))
        }
        assertEquals(ReaderSpreadMode.AUTO, readerSpreadModeFromStorage(null))
        assertEquals(ReaderSpreadMode.AUTO, readerSpreadModeFromStorage("garbage"))
        assertEquals(ReaderSpreadMode.OFF, readerSpreadModeFromStorage(" OFF "))
    }
}
