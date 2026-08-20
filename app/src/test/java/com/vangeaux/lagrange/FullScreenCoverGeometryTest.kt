package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenCoverGeometryTest {
    @Test
    fun `root already centered on the physical screen needs no correction`() {
        // 2340px tall screen, root fills it exactly starting at y=0.
        assertEquals(
            0f,
            coverCenterCorrectionPx(
                rootScreenY = 0f,
                rootHeightPx = 2340f,
                physicalScreenHeightPx = 2340f
            ),
            0.001f
        )
    }

    @Test
    fun `root shifted down by a status bar is pulled back up`() {
        // Root height equals the physical screen height minus the status bar,
        // but its origin starts below y=0 (as if pushed down by the status bar).
        val statusBarPx = 84f
        val physicalHeightPx = 2340f
        val rootHeightPx = physicalHeightPx - statusBarPx
        assertEquals(
            -statusBarPx / 2f,
            coverCenterCorrectionPx(
                rootScreenY = statusBarPx,
                rootHeightPx = rootHeightPx,
                physicalScreenHeightPx = physicalHeightPx
            ),
            0.001f
        )
    }

    @Test
    fun `asymmetric top and bottom frames are corrected to the physical center`() {
        // Root starts at y=60 and is shorter than the screen by 60+30=90px total,
        // i.e. its bottom sits 30px above the physical bottom edge.
        val physicalHeightPx = 2000f
        val rootScreenY = 60f
        val rootHeightPx = 1910f // ends at 1970, leaving 30px below
        val expectedCorrection = (physicalHeightPx / 2f) - (rootScreenY + rootHeightPx / 2f)
        assertEquals(
            expectedCorrection,
            coverCenterCorrectionPx(
                rootScreenY = rootScreenY,
                rootHeightPx = rootHeightPx,
                physicalScreenHeightPx = physicalHeightPx
            ),
            0.001f
        )
    }

    @Test
    fun `portrait dimensions with a shifted root`() {
        // physicalCenter=1250, rootCenter=100+1100=1200 -> pull down by 50px.
        assertEquals(
            50f,
            coverCenterCorrectionPx(
                rootScreenY = 100f,
                rootHeightPx = 2200f,
                physicalScreenHeightPx = 2500f
            ),
            0.001f
        )
    }

    @Test
    fun `landscape dimensions with a shifted root`() {
        // Wide-but-short physical screen (landscape).
        // physicalCenter=540, rootCenter=48+516=564 -> pull up by 24px.
        assertEquals(
            -24f,
            coverCenterCorrectionPx(
                rootScreenY = 48f,
                rootHeightPx = 1032f,
                physicalScreenHeightPx = 1080f
            ),
            0.001f
        )
    }

    @Test
    fun `point inside untransformed cover is included and outside point is excluded`() {
        assertTrue(
            isPointInsideTransformedCover(
                pointX = 300f,
                pointY = 500f,
                coverLeft = 100f,
                coverTop = 200f,
                coverWidth = 400f,
                coverHeight = 600f,
                scale = 1f,
                panX = 0f,
                panY = 0f
            )
        )
        assertFalse(
            isPointInsideTransformedCover(
                pointX = 99f,
                pointY = 500f,
                coverLeft = 100f,
                coverTop = 200f,
                coverWidth = 400f,
                coverHeight = 600f,
                scale = 1f,
                panX = 0f,
                panY = 0f
            )
        )
    }

    @Test
    fun `zoom expands visible cover bounds around its center`() {
        assertTrue(
            isPointInsideTransformedCover(
                pointX = 50f,
                pointY = 100f,
                coverLeft = 100f,
                coverTop = 200f,
                coverWidth = 400f,
                coverHeight = 600f,
                scale = 2f,
                panX = 0f,
                panY = 0f
            )
        )
    }

    @Test
    fun `pan translates visible cover bounds`() {
        assertTrue(
            isPointInsideTransformedCover(
                pointX = 650f,
                pointY = 500f,
                coverLeft = 100f,
                coverTop = 200f,
                coverWidth = 400f,
                coverHeight = 600f,
                scale = 1f,
                panX = 200f,
                panY = 0f
            )
        )
        assertFalse(
            isPointInsideTransformedCover(
                pointX = 150f,
                pointY = 500f,
                coverLeft = 100f,
                coverTop = 200f,
                coverWidth = 400f,
                coverHeight = 600f,
                scale = 1f,
                panX = 200f,
                panY = 0f
            )
        )
    }

    @Test
    fun `invalid cover geometry never captures a tap`() {
        assertFalse(
            isPointInsideTransformedCover(
                pointX = 0f,
                pointY = 0f,
                coverLeft = 0f,
                coverTop = 0f,
                coverWidth = 0f,
                coverHeight = 600f,
                scale = 1f,
                panX = 0f,
                panY = 0f
            )
        )
    }
}
