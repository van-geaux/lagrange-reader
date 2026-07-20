package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailActionRowTest {
    @Test
    fun nonlocalTransferSlotMapsIdleRetryAndCancelStates() {
        assertEquals(
            BookDetailInlineTransfer.DOWNLOAD,
            state(isDownloaded = false).inlineTransfer
        )
        assertEquals(
            BookDetailInlineTransfer.RETRY_DOWNLOAD,
            state(isDownloaded = false, downloadFailed = true).inlineTransfer
        )
        assertEquals(
            BookDetailInlineTransfer.CANCEL_DOWNLOAD,
            state(isDownloaded = false, isDownloading = true).inlineTransfer
        )
    }

    @Test
    fun localUpdateDeleteAndCancelStayInMore() {
        val update = state(isDownloaded = true, hasDownloadUpdate = true)
        assertNull(update.inlineTransfer)
        assertEquals("Update local", update.overflowTransferLabel)
        assertTrue(update.showDeleteLocal)
        assertTrue(update.hasFixedOverflow)

        val active = state(
            isDownloaded = true,
            isDownloading = true,
            hasDownloadUpdate = true
        )
        assertEquals("Cancel update", active.overflowTransferLabel)
        assertTrue(active.showDeleteLocal)

        val offline = state(
            isDownloaded = true,
            hasDownloadUpdate = true,
            isOfflineSnapshot = true
        )
        assertNull(offline.overflowTransferLabel)
        assertTrue(offline.showDeleteLocal)
    }

    @Test
    fun wideNonlocalRowKeepsStatusInlineWithoutMore() {
        val layout = layout(
            availableWidth = 400f,
            hasInlineTransfer = true,
            hasFixedOverflow = false
        )

        assertTrue(layout.showInlineStatusAction)
        assertFalse(layout.showMore)
        assertFalse(layout.compactRequiredActions)
    }

    @Test
    fun narrowNonlocalRowMovesStatusIntoMoreWithoutWrapping() {
        val layout = layout(
            availableWidth = 300f,
            hasInlineTransfer = true,
            hasFixedOverflow = false
        )

        assertFalse(layout.showInlineStatusAction)
        assertTrue(layout.showMore)
        assertFalse(layout.compactRequiredActions)
    }

    @Test
    fun extremelyNarrowRowCompactsOnlyRequiredVisibleActions() {
        val layout = layout(
            availableWidth = 260f,
            hasInlineTransfer = true,
            hasFixedOverflow = false
        )

        assertFalse(layout.showInlineStatusAction)
        assertTrue(layout.showMore)
        assertTrue(layout.compactRequiredActions)
    }

    @Test
    fun largeTextMeasurementsStillMoveOptionalStatusBeforeCompactingRequiredActions() {
        val layout = bookDetailActionRowLayout(
            availableWidth = 360f,
            readWidth = 105f,
            previewWidth = 130f,
            markWidth = 180f,
            hasInlineTransfer = true,
            hasFixedOverflow = false
        )

        assertFalse(layout.showInlineStatusAction)
        assertTrue(layout.showMore)
        assertFalse(layout.compactRequiredActions)
    }

    @Test
    fun localRowAlwaysShowsMoreAndUsesRemainingWidthForStatus() {
        val wide = layout(
            availableWidth = 400f,
            hasInlineTransfer = false,
            hasFixedOverflow = true
        )
        assertTrue(wide.showInlineStatusAction)
        assertTrue(wide.showMore)

        val narrow = layout(
            availableWidth = 260f,
            hasInlineTransfer = false,
            hasFixedOverflow = true
        )
        assertFalse(narrow.showInlineStatusAction)
        assertTrue(narrow.showMore)
    }

    private fun state(
        isDownloaded: Boolean,
        isDownloading: Boolean = false,
        downloadFailed: Boolean = false,
        hasDownloadUpdate: Boolean = false,
        isOfflineSnapshot: Boolean = false
    ) = bookDetailActionState(
        isDownloaded = isDownloaded,
        isDownloading = isDownloading,
        downloadFailed = downloadFailed,
        hasDownloadUpdate = hasDownloadUpdate,
        isOfflineSnapshot = isOfflineSnapshot
    )

    private fun layout(
        availableWidth: Float,
        hasInlineTransfer: Boolean,
        hasFixedOverflow: Boolean
    ) = bookDetailActionRowLayout(
        availableWidth = availableWidth,
        readWidth = 82f,
        previewWidth = 94f,
        markWidth = 118f,
        hasInlineTransfer = hasInlineTransfer,
        hasFixedOverflow = hasFixedOverflow
    )
}
