package com.vangeaux.lagrange

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookSeekConfirmationTest {
    @Test
    fun acceptingSeekKeepsRequestedAbsolutePosition() {
        val transaction = AudiobookSeekTransaction(
            previousPositionMs = 10_000L,
            requestedPositionMs = 90_000L
        )

        assertEquals(
            90_000L,
            resolveAudiobookSeekConfirmation(
                transaction,
                AudiobookSeekResolution.KEEP_REQUESTED
            )
        )
    }

    @Test
    fun rejectingSeekRestoresPreviousAbsolutePosition() {
        val transaction = AudiobookSeekTransaction(
            previousPositionMs = 10_000L,
            requestedPositionMs = 90_000L
        )

        assertEquals(
            10_000L,
            resolveAudiobookSeekConfirmation(
                transaction,
                AudiobookSeekResolution.RETURN_TO_PREVIOUS
            )
        )
    }

    @Test
    fun dismissingConfirmationKeepsRequestedAbsolutePosition() {
        val transaction = AudiobookSeekTransaction(
            previousPositionMs = 10_000L,
            requestedPositionMs = 90_000L
        )

        assertEquals(
            90_000L,
            resolveAudiobookSeekConfirmation(
                transaction,
                AudiobookSeekResolution.DISMISS
            )
        )
    }

    @Test
    fun chapterSeekTransactionStoresResolvedAbsoluteTarget() {
        assertEquals(
            AudiobookSeekTransaction(120_000L, 150_000L),
            audiobookChapterSeekTransaction(
                previousPositionMs = 120_000L,
                chapterStartMs = 120_000L,
                requestedChapterPositionMs = 30_000L
            )
        )
    }

    @Test
    fun disabledOrNoOpSeekDoesNotCreatePendingTransaction() {
        assertEquals(
            null,
            audiobookSeekTransactionOrNull(10_000L, 90_000L, confirmationEnabled = false)
        )
        assertEquals(
            null,
            audiobookSeekTransactionOrNull(10_000L, 10_000L, confirmationEnabled = true)
        )
    }

    @Test
    fun confirmationPopupIsPlacedAboveAnchorWhenSpaceAllows() {
        assertEquals(
            IntOffset(100, 188),
            audiobookSeekConfirmationPosition(
                anchorBounds = IntRect(80, 500, 400, 700),
                windowSize = IntSize(480, 800),
                popupContentSize = IntSize(280, 300),
                marginPx = 12,
                placement = AudiobookSeekConfirmationPlacement.ABOVE_ANCHOR
            )
        )
    }

    @Test
    fun confirmationPopupIsClampedToWindowAndFallsBelowAnchorWhenNeeded() {
        assertEquals(
            IntOffset(0, 360),
            audiobookSeekConfirmationPosition(
                anchorBounds = IntRect(-40, 100, 120, 400),
                windowSize = IntSize(320, 600),
                popupContentSize = IntSize(400, 240),
                marginPx = 12,
                placement = AudiobookSeekConfirmationPlacement.ABOVE_ANCHOR
            )
        )
    }

    @Test
    fun fullPlayerConfirmationIsCenteredOverCoverInsteadOfControls() {
        assertEquals(
            IntOffset(80, 340),
            audiobookSeekConfirmationPosition(
                anchorBounds = IntRect(40, 200, 400, 800),
                windowSize = IntSize(480, 900),
                popupContentSize = IntSize(280, 320),
                marginPx = 12,
                placement = AudiobookSeekConfirmationPlacement.OVER_ANCHOR
            )
        )
    }
}