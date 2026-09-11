package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailActionRowTest {
    @Test
    fun groupedFileActionReflectsPhysicalTransferState() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Chamber of Secrets",
            mediaKind = MediaKind.AUDIO
        )
        val option = BookFileOption(book = book, filename = "Chapter 01.mp3")

        assertEquals(
            GroupedFileAction.DOWNLOAD,
            groupedFileAction(option, emptySet(), emptySet())
        )
        assertEquals(
            GroupedFileAction.DOWNLOADING,
            groupedFileAction(option, setOf("file-1"), emptySet())
        )
        assertEquals(
            GroupedFileAction.DOWNLOADING,
            groupedFileAction(option, emptySet(), setOf("file-1"))
        )
        assertEquals(
            GroupedFileAction.DELETE,
            groupedFileAction(option.copy(book = book.copy(localPath = "/downloads/part-1.mp3")), emptySet(), emptySet())
        )
    }

    @Test
    fun groupedFileDownloadPayloadRetainsPhysicalFilename() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-4",
            title = "Chamber of Secrets",
            mediaKind = MediaKind.AUDIO
        )
        val option = BookFileOption(book = book, filename = "Chapter 04.mp3")

        assertEquals("Chapter 04.mp3", singleFileDownloadBook(option).filename)
    }

    @Test
    fun pendingCellularSingleFileConfirmationRoutesOnlyToSingleFileDownload() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Chamber of Secrets",
            mediaKind = MediaKind.AUDIO
        )
        val normalDownloads = mutableListOf<BookSummary>()
        val singleFileDownloads = mutableListOf<BookSummary>()

        routePendingCellularDownload(
            pending = PendingCellularDownload(book, PendingCellularDownloadScope.SINGLE_FILE),
            onDownload = normalDownloads::add,
            onDownloadSingleFile = singleFileDownloads::add
        )

        assertTrue(normalDownloads.isEmpty())
        assertEquals(listOf(book), singleFileDownloads)
    }

    @Test
    fun pendingCellularGroupConfirmationRoutesOnlyToGroupDownload() {
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Chamber of Secrets",
            mediaKind = MediaKind.AUDIO
        )
        val normalDownloads = mutableListOf<BookSummary>()
        val singleFileDownloads = mutableListOf<BookSummary>()

        routePendingCellularDownload(
            pending = PendingCellularDownload(book, PendingCellularDownloadScope.GROUP),
            onDownload = normalDownloads::add,
            onDownloadSingleFile = singleFileDownloads::add
        )

        assertEquals(listOf(book), normalDownloads)
        assertTrue(singleFileDownloads.isEmpty())
    }

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
    fun failedLocalUpdateOffersUpdateLocalAndKeepsDeleteLocal() {
        val failedUpdate = state(isDownloaded = true, downloadFailed = true)

        assertNull(failedUpdate.inlineTransfer)
        assertEquals("Update local", failedUpdate.overflowTransferLabel)
        assertTrue(failedUpdate.showDeleteLocal)
    }

    @Test
    fun permissionDeniedDownloadDoesNotOfferRetry() {
        val denied = state(isDownloaded = false, permissionDenied = true)

        assertNull(denied.inlineTransfer)
        assertNull(denied.overflowTransferLabel)
    }

    @Test
    fun partialGroupOffersDownloadRemaining() {
        val partial = state(isDownloaded = false, isPartial = true)

        assertEquals(BookDetailInlineTransfer.DOWNLOAD_REMAINING, partial.inlineTransfer)
    }
    @Test
    fun serverMissingBookHasNoFileActionsButCanCancelAnExistingTransfer() {
        val missing = state(isDownloaded = false, isServerMissing = true)
        assertNull(missing.inlineTransfer)
        assertNull(missing.overflowTransferLabel)
        assertFalse(missing.showDeleteLocal)

        val active = state(isDownloaded = true, isDownloading = true, isServerMissing = true)
        assertEquals(BookDetailInlineTransfer.CANCEL_DOWNLOAD, active.inlineTransfer)
        assertNull(active.overflowTransferLabel)
        assertTrue(active.showDeleteLocal)
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
        assertTrue(layout.showInlineTransferAction)
    }

    @Test
    fun extremelyNarrowRowMovesOptionalTransferIntoMoreWithoutStretchingRequiredActions() {
        val layout = layout(
            availableWidth = 260f,
            hasInlineTransfer = true,
            hasFixedOverflow = false
        )

        assertFalse(layout.showInlineStatusAction)
        assertTrue(layout.showMore)
        assertFalse(layout.showInlineTransferAction)
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
        assertEquals(40f, wide.moreSlotWidth, 0.01f)

        val narrow = layout(
            availableWidth = 260f,
            hasInlineTransfer = false,
            hasFixedOverflow = true
        )
        assertFalse(narrow.showInlineStatusAction)
        assertTrue(narrow.showMore)
    }

    @Test
    fun rowWithoutMoreDoesNotReserveTrailingSlot() {
        val layout = bookDetailActionRowLayout(
            availableWidth = 400f,
            readWidth = 72f,
            previewWidth = 84f,
            markWidth = 100f,
            hasInlineTransfer = false,
            hasFixedOverflow = false
        )

        assertFalse(layout.showMore)
        assertEquals(0f, layout.moreSlotWidth, 0.01f)
    }

    private fun state(
        isDownloaded: Boolean,
        isDownloading: Boolean = false,
        downloadFailed: Boolean = false,
        isPartial: Boolean = false,
        permissionDenied: Boolean = false,
        hasDownloadUpdate: Boolean = false,
        isOfflineSnapshot: Boolean = false,
        isServerMissing: Boolean = false
    ) = bookDetailActionState(
        isDownloaded = isDownloaded,
        isDownloading = isDownloading,
        downloadFailed = downloadFailed,
        isPartial = isPartial,
        permissionDenied = permissionDenied,
        hasDownloadUpdate = hasDownloadUpdate,
        isOfflineSnapshot = isOfflineSnapshot,
        isServerMissing = isServerMissing
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
