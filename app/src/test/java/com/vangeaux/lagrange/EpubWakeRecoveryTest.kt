package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubWakeRecoveryTest {
    @Test
    fun `validated canonical position supplies display-only progress during renderer recovery`() {
        val locator = locator("OEBPS/Text/chapter-3.xhtml", 0.42)
        val status = restoredEpubDisplayProgress(
            position = position(
                locator = locator,
                chapterIndex = 2,
                pageIndex = 12,
                pageCount = 71,
                percent = 23.5f,
                overallNumber = 13,
                overallCount = 90,
                renderedPageCounts = listOf(1, 1, 71) + List(19) { 1 }
            ),
            savedPosition = locator,
            openingPosition = locator,
            openingChapterIndex = 2,
            chapterCount = 22,
            layoutMode = ReaderLayoutMode.PAGINATED,
            renderedLayoutKey = RENDERED_LAYOUT_KEY
        )

        assertEquals(
            EpubReaderProgressStatus(
                completionPercent = 23.5f,
                chapterNumber = 3,
                chapterCount = 22,
                chapterPageNumber = 13,
                chapterPageCount = 71,
                overallPositionNumber = 15,
                overallPositionCount = 92
            ),
            status
        )
    }

    @Test
    fun `restored display keeps exact completed overall position`() {
        val locator = locator("OEBPS/Text/final.xhtml", 1.0)
        val status = restoredEpubDisplayProgress(
            position = position(
                locator = locator,
                chapterIndex = 1,
                pageIndex = 7,
                pageCount = 8,
                percent = 100f,
                overallNumber = 8,
                overallCount = 8,
                renderedPageCounts = listOf(1, 8)
            ),
            savedPosition = locator,
            openingPosition = locator,
            openingChapterIndex = 1,
            chapterCount = 2,
            layoutMode = ReaderLayoutMode.PAGINATED,
            renderedLayoutKey = RENDERED_LAYOUT_KEY
        )

        assertEquals(100f, status?.completionPercent)
        assertEquals(9, status?.overallPositionNumber)
        assertEquals(9, status?.overallPositionCount)
    }

    @Test
    fun `restored display rejects a different opening locator`() {
        val saved = locator("OEBPS/Text/chapter-3.xhtml", 0.42)
        val opening = locator("OEBPS/Text/chapter-4.xhtml", 0.1)

        assertNull(
            restoredEpubDisplayProgress(
                position = position(saved, 2, 12, 71, 23.5f),
                savedPosition = saved,
                openingPosition = opening,
                openingChapterIndex = 3,
                chapterCount = 22,
                layoutMode = ReaderLayoutMode.PAGINATED,
                renderedLayoutKey = RENDERED_LAYOUT_KEY
            )
        )
    }

    @Test
    fun `restored display rejects placeholder stale and malformed values`() {
        val locator = locator("OEBPS/Text/chapter-3.xhtml", 0.42)
        val valid = position(locator, 2, 12, 71, 23.5f)

        fun restore(candidate: EpubReaderPosition, saved: EpubCanonicalPosition? = locator) =
            restoredEpubDisplayProgress(
                position = candidate,
                savedPosition = saved,
                openingPosition = locator,
                openingChapterIndex = 2,
                chapterCount = 22,
                layoutMode = ReaderLayoutMode.PAGINATED,
                renderedLayoutKey = RENDERED_LAYOUT_KEY
            )

        assertNull(restore(valid.copy(updatedAtMillis = 0L)))
        assertNull(restore(valid.copy(locatorJson = null)))
        assertNull(restore(valid, saved = null))
        assertNull(restore(valid.copy(chapterIndex = 22)))
        assertNull(restore(valid.copy(pageIndex = 71)))
        assertNull(restore(valid.copy(progressPercent = Float.NaN)))
    }

    @Test
    fun `restored display rejects a chapter tuple that disagrees with its opening resource`() {
        val locator = locator("OEBPS/Text/chapter-3.xhtml", 0.42)

        assertNull(
            restoredEpubDisplayProgress(
                position = position(locator, 2, 12, 71, 23.5f),
                savedPosition = locator,
                openingPosition = locator,
                openingChapterIndex = 3,
                chapterCount = 22,
                layoutMode = ReaderLayoutMode.PAGINATED,
                renderedLayoutKey = RENDERED_LAYOUT_KEY
            )
        )
    }

    @Test
    fun `paginated restored display rejects stale page geometry`() {
        val locator = locator("OEBPS/Text/final.xhtml", 1.0)
        assertNull(
            restoredEpubDisplayProgress(
                position = position(
                    locator = locator,
                    chapterIndex = 1,
                    pageIndex = 7,
                    pageCount = 8,
                    percent = 100f,
                    overallNumber = 8,
                    overallCount = 8,
                    renderedPageCounts = listOf(1, 8)
                ),
                savedPosition = locator,
                openingPosition = locator,
                openingChapterIndex = 1,
                chapterCount = 2,
                layoutMode = ReaderLayoutMode.PAGINATED,
                renderedLayoutKey = "different-layout"
            )
        )
    }

    @Test
    fun `continuous restored display does not label content positions as overall pages`() {
        val locator = locator("OEBPS/Text/final.xhtml", 1.0)
        val status = restoredEpubDisplayProgress(
            position = position(
                locator = locator,
                chapterIndex = 1,
                pageIndex = 7,
                pageCount = 8,
                percent = 100f,
                overallNumber = 8,
                overallCount = 8,
                renderedPageCounts = listOf(1, 8)
            ),
            savedPosition = locator,
            openingPosition = locator,
            openingChapterIndex = 1,
            chapterCount = 2,
            layoutMode = ReaderLayoutMode.CONTINUOUS,
            renderedLayoutKey = null
        )

        assertEquals(100f, status?.completionPercent)
        assertNull(status?.overallPositionNumber)
        assertNull(status?.overallPositionCount)
    }

    @Test
    fun `save before publication attachment preserves the claimed canonical tuple`() {
        val locator = locator("OEBPS/Text/chapter-3.xhtml", 0.42)
        val claimed = position(locator, 2, 12, 71, 23.5f)

        assertEquals(
            claimed,
            epubPositionToPreserveBeforeRendererConfirmation(
                validatedDisplayPosition = null,
                pendingClaimedPosition = claimed
            )
        )
        assertEquals(
            claimed.copy(pageIndex = 13),
            epubPositionToPreserveBeforeRendererConfirmation(
                validatedDisplayPosition = claimed.copy(pageIndex = 13),
                pendingClaimedPosition = claimed
            )
        )
        assertTrue(
            epubProgressPercentForSavedState(
                preservedPosition = claimed.copy(progressPercent = null),
                currentPercent = 0f
            ).isNaN()
        )
        assertEquals(
            42f,
            epubProgressPercentForSavedState(
                preservedPosition = null,
                currentPercent = 42f
            )
        )
    }

    @Test
    fun `passive wake reflow cannot replace the observed canonical tuple`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val wakeReflow = waldenPosition(
            progression = 0.1791071724117178,
            pageCount = 67,
            percent = 4.887218f,
            overallNumber = 14
        )
        val guard = EpubPassiveRecoveryGuard()

        guard.begin(stored)

        assertTrue(guard.isProtectingCanonicalProgress)
        assertFalse(guard.shouldCommitVisualUpdate(2, 12, wakeReflow.toEpubCanonicalPosition()!!))
        assertEquals(stored, guard.positionToPreserve)
        // A second and third renderer callback remain passive; this is not a one-event guard.
        assertFalse(guard.shouldCommitVisualUpdate(2, 12, wakeReflow.toEpubCanonicalPosition()!!))
        assertFalse(guard.shouldCommitVisualUpdate(2, 12, wakeReflow.toEpubCanonicalPosition()!!))
        assertEquals(stored, guard.positionToPreserve)
        assertFalse(stored == wakeReflow)
    }

    @Test
    fun `no-op page request cannot release passive recovery but a changed page can`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)

        guard.expectPageOrChapterMovement()

        assertFalse(guard.shouldCommitVisualUpdate(2, 12, stored.toEpubCanonicalPosition()!!))
        guard.cancelExpectedMovement()
        assertFalse(guard.shouldCommitVisualUpdate(2, 13, stored.toEpubCanonicalPosition()!!))
        guard.expectPageOrChapterMovement()
        assertTrue(guard.shouldCommitVisualUpdate(2, 13, stored.toEpubCanonicalPosition()!!))
        // Until the navigator reports a valid move, recreation/Close still preserves the old tuple.
        assertEquals(stored, guard.positionToPreserve)
        val moved = stored.copy(pageIndex = 13, updatedAtMillis = stored.updatedAtMillis + 1)
        guard.committed(moved)
        assertEquals(moved, guard.positionToPreserve)
        assertTrue(guard.isProtectingCanonicalProgress)
        assertFalse(guard.shouldCommitVisualUpdate(2, 13, moved.toEpubCanonicalPosition()!!))
    }

    @Test
    fun `settled no-op swipe clears its movement authorization`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)
        guard.expectPageOrChapterMovement()
        guard.finishPageOrChapterMovement()

        assertFalse(guard.shouldCommitVisualUpdate(2, 12, stored.toEpubCanonicalPosition()!!))
        assertFalse(guard.isMovementExpected())
        assertFalse(guard.shouldCommitVisualUpdate(2, 13, stored.toEpubCanonicalPosition()!!))
    }

    @Test
    fun `finished swipe still accepts its changed rendered page`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)
        guard.expectPageOrChapterMovement()
        guard.finishPageOrChapterMovement()

        assertTrue(guard.shouldCommitVisualUpdate(2, 13, stored.toEpubCanonicalPosition()!!))
    }

    @Test
    fun `same-page callback during an active swipe does not cancel a later real move`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)
        guard.expectPageOrChapterMovement()

        assertFalse(guard.shouldCommitVisualUpdate(2, 12, stored.toEpubCanonicalPosition()!!))
        assertTrue(guard.isMovementExpected())
        assertTrue(guard.shouldCommitVisualUpdate(2, 13, stored.toEpubCanonicalPosition()!!))
    }

    @Test
    fun `finished swipe cancellation identifies unstarted and publication-bound gestures`() {
        fun cancelled(
            horizontalOffset: Float,
            pageIndex: Int = 3,
            pageCount: Int = 8,
            chapterIndex: Int = 1,
            chapterCount: Int = 3,
            layoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED
        ) = shouldCancelFinishedEpubSwipe(
            layoutMode = layoutMode,
            horizontalOffset = horizontalOffset,
            pagingTouchSlop = 16,
            pageIndex = pageIndex,
            pageCount = pageCount,
            chapterIndex = chapterIndex,
            chapterCount = chapterCount
        )

        assertTrue(cancelled(horizontalOffset = 16f))
        assertTrue(cancelled(horizontalOffset = -8f))
        assertTrue(cancelled(horizontalOffset = 30f, pageIndex = 0, chapterIndex = 0))
        assertTrue(cancelled(horizontalOffset = -30f, pageIndex = 7, chapterIndex = 2))
        assertFalse(cancelled(horizontalOffset = -30f))
        assertFalse(cancelled(horizontalOffset = -30f, pageIndex = 7, chapterIndex = 1))
        assertFalse(cancelled(horizontalOffset = 8f, layoutMode = ReaderLayoutMode.CONTINUOUS))
    }

    @Test
    fun `semantic drift is not accepted without a deliberate navigation signal`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val alteredSemanticPosition = EpubCanonicalPosition(
            href = "epub/text/economy.xhtml",
            totalProgression = 0.052,
            resourceProgression = 0.18,
            contentPosition = 15
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)

        assertFalse(guard.shouldCommitVisualUpdate(2, 13, alteredSemanticPosition))
    }

    @Test
    fun `stale page movement authorization expires before later renderer drift`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)
        guard.expectPageOrChapterMovement(untilMillis = 10L)

        assertFalse(
            guard.shouldCommitVisualUpdate(
                chapterIndex = 2,
                pageIndex = 13,
                candidate = stored.toEpubCanonicalPosition()!!,
                nowMillis = 11L
            )
        )
    }

    @Test
    fun `fresh and placeholder positions do not block first progress`() {
        val guard = EpubPassiveRecoveryGuard()

        guard.begin(null)
        assertTrue(guard.shouldCommitVisualUpdate(0, 0, locator("chapter.xhtml", 0.0)))

        guard.begin(
            waldenPosition(
                progression = 0.0,
                pageCount = 1,
                percent = 0f,
                overallNumber = 1
            ).copy(updatedAtMillis = 0L)
        )
        assertTrue(guard.shouldCommitVisualUpdate(0, 0, locator("chapter.xhtml", 0.0)))
        assertNull(guard.positionToPreserve)
    }

    @Test
    fun `semantic target must arrive before a protected position can be replaced`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val target = EpubCanonicalPosition(
            href = "epub/text/economy.xhtml",
            totalProgression = 0.06,
            resourceProgression = 0.25,
            contentPosition = 16
        )
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)
        guard.expectSemanticMovement(target)

        val passiveDrift = EpubCanonicalPosition(
            href = "epub/text/economy.xhtml",
            totalProgression = 0.04887218045112782,
            resourceProgression = 0.1791071724117178,
            contentPosition = 14
        )
        val arrived = target.copy(resourceProgression = 0.251)

        assertFalse(guard.shouldCommitVisualUpdate(2, 12, passiveDrift))
        assertTrue(guard.shouldCommitVisualUpdate(2, 15, arrived))
    }

    @Test
    fun `semantic navigation accepts page snapping but same-page fallback requires exact target`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val sameAnchorTarget = EpubCanonicalPosition(
            href = "epub/text/economy.xhtml",
            totalProgression = 0.04887218045112782,
            resourceProgression = 0.25,
            contentPosition = 14
        )
        val snappedPageTarget = sameAnchorTarget.copy(resourceProgression = 0.24)
        val guard = EpubPassiveRecoveryGuard()
        guard.begin(stored)
        guard.expectSemanticMovement(sameAnchorTarget)

        assertTrue(guard.shouldCommitVisualUpdate(2, 13, snappedPageTarget))

        guard.begin(stored)
        guard.expectSemanticMovement(sameAnchorTarget)
        assertFalse(guard.shouldCommitVisualUpdate(2, 12, snappedPageTarget))
        assertTrue(guard.shouldCommitVisualUpdate(2, 12, sameAnchorTarget))
    }

    @Test
    fun `passive display adopts current geometry without adopting renderer drift`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )

        val status = passiveEpubDisplayProgress(
            position = stored,
            chapterIndex = 2,
            chapterCount = 22,
            pageIndex = 12,
            pageCount = 67,
            renderedOverallPosition = EpubOverallPosition(14, 250),
            layoutMode = ReaderLayoutMode.PAGINATED
        )

        assertEquals(4.511278f, status?.completionPercent)
        assertEquals(13, status?.chapterPageNumber)
        assertEquals(67, status?.chapterPageCount)
        assertEquals(14, status?.overallPositionNumber)
        assertEquals(250, status?.overallPositionCount)
    }

    @Test
    fun `recovery result preserves nullable percent and the complete canonical tuple`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        ).copy(progressPercent = null)

        val result = epubProgressResultDuringRecovery(
            preservedPosition = stored,
            currentChapterIndex = 2,
            currentPageIndex = 12,
            currentPageCount = 67,
            currentPercent = 4.887218f,
            currentLocatorJson = waldenPosition(
                progression = 0.1791071724117178,
                pageCount = 67,
                percent = 4.887218f,
                overallNumber = 14
            ).locatorJson
        )

        assertEquals(2, result.chapterIndex)
        assertEquals(12, result.pageIndex)
        assertEquals(71, result.pageCount)
        assertNull(result.percent)
        assertEquals(stored.locatorJson, result.locatorJson)
    }

    @Test
    fun `passive recovery wins saved state selection after renderer health`() {
        val passive = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val driftedDisplay = waldenPosition(
            progression = 0.1791071724117178,
            pageCount = 67,
            percent = 4.887218f,
            overallNumber = 14
        )

        assertEquals(
            passive,
            epubPositionToPreserveBeforeRendererConfirmation(
                passiveRecoveryPosition = passive,
                validatedDisplayPosition = driftedDisplay,
                pendingClaimedPosition = driftedDisplay
            )
        )
    }

    @Test
    fun `canonical opening match is exact while later resource progression drift is not`() {
        val stored = waldenPosition(
            progression = 0.1690164842185969,
            pageCount = 71,
            percent = 4.511278f,
            overallNumber = 13
        )
        val exact = EpubCanonicalPosition(
            href = "epub/text/economy.xhtml",
            totalProgression = 0.04887218045112782,
            resourceProgression = 0.1690164842185969,
            contentPosition = 14
        )
        val drifted = EpubCanonicalPosition(
            href = "epub/text/economy.xhtml",
            totalProgression = 0.04887218045112782,
            resourceProgression = 0.1791071724117178,
            contentPosition = 14
        )

        assertTrue(epubPositionMatchesOpeningPosition(stored, exact))
        assertFalse(epubPositionMatchesOpeningPosition(stored, drifted))
        assertTrue(sameEpubSemanticAnchor(exact, drifted))
    }

    @Test
    fun `stopped or noninteractive readers never run renderer probes`() {
        assertTrue(shouldRunEpubRendererProbe(lifecycleStarted = true, deviceInteractive = true))
        assertFalse(shouldRunEpubRendererProbe(lifecycleStarted = false, deviceInteractive = true))
        assertFalse(shouldRunEpubRendererProbe(lifecycleStarted = true, deviceInteractive = false))
        assertFalse(shouldRunEpubRendererProbe(lifecycleStarted = false, deviceInteractive = false))
    }

    @Test
    fun `suppressed locator reports cannot apply pagination or clear trusted display`() {
        assertFalse(
            shouldApplyEpubPagination(
                validPagination = true,
                visualLocatorAccepted = false
            )
        )
        assertFalse(
            shouldConfirmEpubProgressDisplay(
                layoutMode = ReaderLayoutMode.PAGINATED,
                paginationApplied = false,
                visualLocatorApplied = false
            )
        )
    }

    @Test
    fun `locator only recovery confirms continuous but not paginated display`() {
        assertTrue(
            shouldConfirmEpubProgressDisplay(
                layoutMode = ReaderLayoutMode.CONTINUOUS,
                paginationApplied = false,
                visualLocatorApplied = true
            )
        )
        assertFalse(
            shouldConfirmEpubProgressDisplay(
                layoutMode = ReaderLayoutMode.PAGINATED,
                paginationApplied = false,
                visualLocatorApplied = true
            )
        )
        assertFalse(
            shouldConfirmEpubProgressDisplay(
                layoutMode = ReaderLayoutMode.CONTINUOUS,
                paginationApplied = false,
                visualLocatorApplied = false
            )
        )
    }

    @Test
    fun `paginated recovery never applies a locator without matching pagination`() {
        assertFalse(
            shouldApplyEpubLocatorSeparately(
                layoutMode = ReaderLayoutMode.PAGINATED,
                hasLocator = true,
                paginationApplied = false,
                paginationMatchesLocator = false
            )
        )
        assertTrue(
            shouldApplyEpubLocatorSeparately(
                layoutMode = ReaderLayoutMode.CONTINUOUS,
                hasLocator = true,
                paginationApplied = false,
                paginationMatchesLocator = false
            )
        )
        assertFalse(
            shouldApplyEpubLocatorSeparately(
                layoutMode = ReaderLayoutMode.CONTINUOUS,
                hasLocator = true,
                paginationApplied = true,
                paginationMatchesLocator = true
            )
        )
    }

    private fun position(
        locator: EpubCanonicalPosition,
        chapterIndex: Int,
        pageIndex: Int,
        pageCount: Int,
        percent: Float,
        overallNumber: Int? = null,
        overallCount: Int? = null,
        renderedPageCounts: List<Int> = List(22) { 1 }
    ) = EpubReaderPosition(
        serverUrl = "",
        bookId = "",
        fileId = null,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        pageCount = pageCount,
        updatedAtMillis = 1L,
        locatorJson = """{"href":"${locator.href}","locations":{"progression":${locator.resourceProgression}}}""",
        progressPercent = percent,
        overallPositionNumber = overallNumber,
        overallPositionCount = overallCount,
        renderedPageLayoutKey = RENDERED_LAYOUT_KEY,
        renderedPageCounts = renderedPageCounts
    )

    private fun locator(href: String, progression: Double): EpubCanonicalPosition = EpubCanonicalPosition(
        href = href,
        totalProgression = null,
        resourceProgression = progression
    )

    private fun waldenPosition(
        progression: Double,
        pageCount: Int,
        percent: Float,
        overallNumber: Int
    ) = EpubReaderPosition(
        serverUrl = "https://read.example",
        bookId = "3093",
        fileId = "3926",
        chapterIndex = 2,
        pageIndex = 12,
        pageCount = pageCount,
        updatedAtMillis = 1_793_337_624_167L,
        locatorJson =
            """{"href":"epub/text/economy.xhtml","type":"application/xhtml+xml","locations":{"progression":$progression,"position":14,"totalProgression":0.04887218045112782}}""",
        progressPercent = percent,
        overallPositionNumber = overallNumber,
        overallPositionCount = 266,
        renderedPageLayoutKey = RENDERED_LAYOUT_KEY,
        renderedPageCounts = List(22) { index -> if (index == 2) pageCount else 0 },
        profileId = "profile",
        providerId = "bookorbit",
        accountScope = "account",
        publicationFingerprint = "sha256:fixture"
    )

    private companion object {
        const val RENDERED_LAYOUT_KEY = "test-rendered-layout"
    }
}
