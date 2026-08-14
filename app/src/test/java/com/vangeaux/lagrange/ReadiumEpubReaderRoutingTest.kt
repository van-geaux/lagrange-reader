package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class AnnotationReanchorState(
    val current: String,
    val pending: String?
)

private fun settleAnnotationReanchor(state: AnnotationReanchorState): AnnotationReanchorState {
    val target = state.pending ?: return state
    return AnnotationReanchorState(current = target, pending = null)
}

class ReadiumEpubReaderRoutingTest {
    @Test
    fun previewLabelsUsePreviewModeTerminologyForReadingAndListening() {
        assertEquals("Preview mode · Tap to enable reading progress", BOOK_PREVIEW_MODE_LABEL)
        assertEquals("Preview mode · Tap to enable listening progress", AUDIOBOOK_PREVIEW_MODE_LABEL)
    }

    @Test
    fun previewTopSpaceUsesTheLargerOfReaderPaddingAndBannerInsteadOfAddingThem() {
        assertEquals(80, effectiveReaderTopSpace(normalTopPadding = 80, bannerHeight = 48, isPreview = true))
        assertEquals(80, effectiveReaderTopSpace(normalTopPadding = 32, bannerHeight = 80, isPreview = true))
    }

    @Test
    fun readingModeRecoversBannerSpaceAndKeepsOnlyReaderPadding() {
        assertEquals(32, effectiveReaderTopSpace(normalTopPadding = 32, bannerHeight = 80, isPreview = false))
    }

    @Test
    fun previewBannerHostStartsBelowStatusBarWithoutChangingBannerHeight() {
        assertEquals(104, occupiedPreviewBannerBottom(statusBarTop = 24, bannerHeight = 80))
        assertEquals(104, effectiveReaderTopSpace(normalTopPadding = 32, bannerHeight = 104, isPreview = true))
    }

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

    @Test
    fun promotionSettlesOnTheExactPendingAnnotationLocator() {
        val settled = settleAnnotationReanchor(
            AnnotationReanchorState(current = "chapter-2#old-page", pending = "chapter-2#annotation-cfi")
        )

        assertEquals("chapter-2#annotation-cfi", settled.current)
        assertEquals(null, settled.pending)
    }

    @Test
    fun ordinaryPreviewHasNoAnnotationLocatorToReanchor() {
        val state = AnnotationReanchorState(current = "chapter-1#start", pending = null)

        assertEquals(state, settleAnnotationReanchor(state))
    }
}
