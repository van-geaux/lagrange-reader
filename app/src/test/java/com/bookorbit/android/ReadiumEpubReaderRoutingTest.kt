package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumEpubReaderRoutingTest {
    @Test
    fun everyEpubLaunchUsesReadium() {
        assertTrue(shouldUseReadiumEpubReader(MediaKind.EPUB))
        assertFalse(shouldUseReadiumEpubReader(MediaKind.COMIC))
        assertFalse(shouldUseReadiumEpubReader(MediaKind.AUDIO))
        assertFalse(shouldUseReadiumEpubReader(MediaKind.PDF))
        assertFalse(shouldUseReadiumEpubReader(MediaKind.UNKNOWN))
    }

    @Test
    fun overallProgressUsesReadiumTotalProgressionWhenAvailable() {
        assertEquals(
            62.5f,
            readiumOverallPercent(
                totalProgression = 0.625,
                resourceProgression = 0.1,
                chapterIndex = 1,
                chapterCount = 4
            ),
            0.001f
        )
    }

    @Test
    fun overallProgressFallsBackToChapterAndResourceProgression() {
        assertEquals(
            37.5f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.5,
                chapterIndex = 1,
                chapterCount = 4
            ),
            0.001f
        )
    }

    @Test
    fun percentageSelectionUsesTheFloorReadiumPositionForUnevenResources() {
        assertEquals(
            3,
            selectReadiumPositionIndex(
                targetProgression = 0.635,
                totalProgressions = listOf(0.0, 0.1, 0.35, 0.62, 0.82, 1.0)
            )
        )
    }

    @Test
    fun percentageSelectionUsesTheFirstUsablePositionAtTheLowerBoundary() {
        assertEquals(
            1,
            selectReadiumPositionIndex(
                targetProgression = 0.0,
                totalProgressions = listOf(null, 0.0, 0.4)
            )
        )
    }
}
