package com.vangeaux.lagrange

import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
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

private fun epubPosition(
    href: String,
    total: Double,
    resource: Double = total
): EpubCanonicalPosition = EpubCanonicalPosition(
    href = href,
    totalProgression = total,
    resourceProgression = resource
)

class ReadiumEpubReaderRoutingTest {
    @Test
    fun `closing EPUB reader closes publisher narration and text to speech`() {
        val closed = mutableListOf<String>()

        closeEpubReaderPlayback(
            closeMediaOverlayPlayback = { closed += "publisher" },
            closeTtsPlayback = { closed += "tts" }
        )

        assertEquals(listOf("publisher", "tts"), closed)
    }

    @Test
    fun `EPUB viewport reserves navigation inset only when bar is visible`() {
        assertEquals(0, readerViewportBottomInset(72, hideNavigationBar = true))
        assertEquals(72, readerViewportBottomInset(72, hideNavigationBar = false))
        assertEquals(0, readerViewportBottomInset(0, hideNavigationBar = false))
        assertEquals(0, readerViewportBottomInset(-1, hideNavigationBar = false))
    }

    @Test
    fun `bottom overlay reservation subtracts viewport inset once and uses tallest visible overlay`() {
        assertEquals(348, readerViewportOverlayBottomMargin(listOf(240, 420), viewportBottomInset = 72))
        assertEquals(168, readerViewportOverlayBottomMargin(listOf(240), viewportBottomInset = 72))
        assertEquals(0, readerViewportOverlayBottomMargin(emptyList(), viewportBottomInset = 72))
        assertEquals(0, readerViewportOverlayBottomMargin(listOf(24), viewportBottomInset = 72))
    }

    @Test
    fun previewLabelsUsePreviewModeTerminologyForListening() {
        // The reading preview banner text now lives in string resources; the listening player
        // label is still a constant until the audiobook player is localized.
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
    fun epubImageHrefResolvesRelativeToTheCurrentPublicationLocator() {
        assertEquals(
            "OEBPS/Images/Art_insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "OEBPS/Text/insert001.xhtml",
                imageHrefs = listOf("../Images/Art_insert001.jpg")
            )
        )
    }

    @Test
    fun epubImageHrefNormalizesReadiumPublicationBaseBeforeResolvingRelativeSource() {
        assertEquals(
            "OEBPS/Images/Art_insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "https://readium/publication/OEBPS/Text/insert001.xhtml",
                imageHrefs = listOf("../Images/Art_insert001.jpg")
            )
        )
    }

    @Test
    fun epubImageCurrentSrcRemovesTheReadiumPublicationOrigin() {
        assertEquals(
            "OEBPS/Images/Art_insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "OEBPS/Text/insert001.xhtml",
                imageHrefs = listOf("https://readium/publication/OEBPS/Images/Art_insert001.jpg")
            )
        )
    }

    @Test
    fun epubImageHrefPreservesEncodedPublicationPathSegments() {
        assertEquals(
            "OEBPS/Images/Art%20insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "OEBPS/Text/Chapter%201.xhtml",
                imageHrefs = listOf("../Images/Art%20insert001.jpg")
            )
        )
    }

    @Test
    fun epubImageHrefDropsQueryAndFragmentBeforePublicationLookup() {
        assertEquals(
            "OEBPS/Images/Art_insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "OEBPS/Text/insert001.xhtml?layout=page#position",
                imageHrefs = listOf("../Images/Art_insert001.jpg?width=1200#figure")
            )
        )
        assertEquals(
            "OEBPS/Images/Art_insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "OEBPS/Text/insert001.xhtml",
                imageHrefs = listOf(
                    "https://readium/publication/OEBPS/Images/Art_insert001.jpg?width=1200#figure"
                )
            )
        )
    }

    @Test
    fun epubImageHrefRejectsNonPublicationUrls() {
        val currentLocator = "OEBPS/Text/insert001.xhtml"

        listOf(
            "https://example.com/Art_insert001.jpg",
            "data:image/png;base64,AAAA",
            "blob:https://readium/example-id",
            "file:///android_asset/Art_insert001.jpg",
            "https://readium/assets/Art_insert001.jpg"
        ).forEach { imageHref ->
            assertEquals(
                imageHref,
                null,
                resolveEpubImageHref(currentLocator, listOf(imageHref))
            )
        }
    }

    @Test
    fun epubImageHrefFallsBackFromRejectedHrefToReadiumCurrentSrc() {
        assertEquals(
            "OEBPS/Images/Art_insert001.jpg",
            resolveEpubImageHref(
                currentLocatorHref = "OEBPS/Text/insert001.xhtml",
                imageHrefs = listOf(
                    "https://example.com/Art_insert001.jpg",
                    "https://readium/publication/OEBPS/Images/Art_insert001.jpg"
                )
            )
        )
    }

    @Test
    fun epubImageViewerOpensOnlyForLongPress() {
        assertTrue(shouldOpenEpubImageViewer("long_press"))
        assertFalse(shouldOpenEpubImageViewer("tap"))
        assertFalse(shouldOpenEpubImageViewer(""))
    }

    @Test
    fun noIndexProgressIgnoresUntrustedReadiumTotalAndUsesChapterResourceFallback() {
        assertEquals(
            27.5f,
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
    fun percentageSelectionRejectsAnIncompletePositionTable() {
        assertEquals(null, selectReadiumPositionIndex(0.0, listOf(null, 0.0, 0.4)))
        assertEquals(null, selectReadiumPositionIndex(0.5, listOf(0.0, 0.7, 0.4)))
    }

    @Test
    fun canonicalPercentRoundTripsEveryPositionDespiteUnevenRawTotals() {
        val index = validatedEpubCanonicalPositionIndex(
            positions = listOf(
                epubPosition("chapter.xhtml", 0.0, 0.0),
                epubPosition("chapter.xhtml", 0.1, 0.15),
                epubPosition("chapter.xhtml", 0.35, 0.3),
                epubPosition("chapter.xhtml", 0.62, 0.5),
                epubPosition("chapter.xhtml", 0.82, 0.75),
                epubPosition("chapter.xhtml", 1.0, 0.95)
            ),
            readingOrderHrefs = listOf("chapter.xhtml")
        )!!

        index.positions.indices.forEach { positionIndex ->
            val savedPercent = readiumOverallPercent(
                totalProgression = index.totalProgressions[positionIndex],
                resourceProgression = index.resourceProgressions[positionIndex],
                chapterIndex = 0,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(
                    number = positionIndex + 1,
                    count = index.positions.size
                )
            )
            assertEquals(
                "position ${positionIndex + 1}/${index.positions.size} at $savedPercent%",
                positionIndex,
                selectCanonicalEpubPositionIndex(savedPercent, index)
            )
        }
        assertEquals(index.positions.lastIndex, selectCanonicalEpubPositionIndex(100f, index))
    }

    @Test
    fun generatedFinalPositionDoesNotForceCompletion() {
        assertEquals(
            80f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.8,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 5, count = 5)
            ),
            0.001f
        )
        assertEquals(
            100f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 1.0,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 5, count = 5)
            ),
            0f
        )
    }

    @Test
    fun finalRenderedPageCompletesEvenWhenReadiumReportsItsStartProgression() {
        assertEquals(
            100f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.875,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 8, count = 8),
                navigatorAtEnd = true
            ),
            0f
        )
        assertTrue(
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.75,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 7, count = 8),
                navigatorAtEnd = false
            ) < 100f
        )
    }

    @Test
    fun finalRenderedPageCompletesWhenReadiumOmitsResourceProgression() {
        assertEquals(
            100f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = null,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 8, count = 8),
                navigatorAtEnd = true
            ),
            0f
        )
    }

    @Test
    fun renderedEndRequiresTheLastPageOfTheLastChapterInPaginatedMode() {
        assertTrue(isRenderedEpubPublicationEnd(ReaderLayoutMode.PAGINATED, 7, 8, 1, 2))
        assertFalse(isRenderedEpubPublicationEnd(ReaderLayoutMode.PAGINATED, 6, 8, 1, 2))
        assertFalse(isRenderedEpubPublicationEnd(ReaderLayoutMode.PAGINATED, 7, 8, 0, 2))
        assertFalse(isRenderedEpubPublicationEnd(ReaderLayoutMode.CONTINUOUS, 7, 8, 1, 2))
    }

    @Test
    fun `two resource EPUB produces nine overall pages rather than eight content positions`() {
        val firstChapter = updateEpubRenderedPageLedger(
            current = null,
            layoutKey = "portrait-layout",
            readingOrderCount = 2,
            chapterIndex = 0,
            pageIndex = 0,
            pageCount = 1
        )
        assertNull(
            renderedEpubOverallPosition(
                ledger = firstChapter,
                layoutKey = "portrait-layout",
                readingOrderCount = 2,
                chapterIndex = 0,
                pageIndex = 0,
                pageCount = 1
            )
        )

        val complete = updateEpubRenderedPageLedger(
            current = firstChapter,
            layoutKey = "portrait-layout",
            readingOrderCount = 2,
            chapterIndex = 1,
            pageIndex = 0,
            pageCount = 8
        )

        assertEquals(listOf(1, 8), complete?.pageCounts)
        assertEquals(
            EpubOverallPosition(2, 9),
            renderedEpubOverallPosition(complete, "portrait-layout", 2, 1, 0, 8)
        )
        assertEquals(
            EpubOverallPosition(9, 9),
            renderedEpubOverallPosition(complete, "portrait-layout", 2, 1, 7, 8)
        )
        assertEquals(
            100f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.875,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(8, 8),
                renderedPagePosition = EpubOverallPosition(9, 9),
                navigatorAtEnd = true
            ),
            0f
        )
    }

    @Test
    fun `rendered overall percent follows nine screens and not eight canonical chunks`() {
        assertEquals(
            50f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.5,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(5, 8),
                renderedPagePosition = EpubOverallPosition(5, 9)
            ),
            0.001f
        )
        assertEquals(
            87.5f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.875,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(8, 8),
                renderedPagePosition = EpubOverallPosition(8, 9)
            ),
            0.001f
        )
        assertEquals(
            50f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.625,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(5, 8)
            ),
            0.001f
        )
        assertEquals(
            62.5f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = 0.625,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(5, 8),
                renderedPagePosition = EpubOverallPosition(6, 9)
            ),
            0.001f
        )
    }

    @Test
    fun `layout changes and changed chapter page counts invalidate observed totals`() {
        val complete = EpubRenderedPageLedger("portrait", listOf(1, 8))
        val landscape = updateEpubRenderedPageLedger(
            current = complete,
            layoutKey = "landscape",
            readingOrderCount = 2,
            chapterIndex = 1,
            pageIndex = 0,
            pageCount = 4
        )
        assertEquals(listOf(0, 4), landscape?.pageCounts)
        assertNull(renderedEpubOverallPosition(landscape, "landscape", 2, 1, 0, 4))

        val repaginated = updateEpubRenderedPageLedger(
            current = complete,
            layoutKey = "portrait",
            readingOrderCount = 2,
            chapterIndex = 1,
            pageIndex = 0,
            pageCount = 9
        )
        assertEquals(listOf(0, 9), repaginated?.pageCounts)
        assertNull(renderedEpubOverallPosition(repaginated, "portrait", 2, 1, 0, 9))
    }

    @Test
    fun `invalid pagination callbacks cannot create or corrupt a rendered page ledger`() {
        val complete = EpubRenderedPageLedger("portrait", listOf(1, 8))

        assertEquals(
            complete,
            updateEpubRenderedPageLedger(complete, "portrait", 2, 1, 8, 8)
        )
        assertNull(updateEpubRenderedPageLedger(null, "portrait", 2, 1, -1, 8))
        assertNull(renderedEpubOverallPosition(complete, "portrait", 2, 1, 8, 8))
    }

    @Test
    fun `rendered page layout key changes with viewport and pagination settings`() {
        fun key(
            width: Int = 1080,
            preferences: LibraryReaderPreferences = LibraryReaderPreferences(),
            twoPageSpreadActive: Boolean = false,
            spreadPageMarginCssPx: Double = EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX,
            contentLanguage: EpubContentLanguage = EpubContentLanguage(
                tag = "en",
                source = EpubContentLanguageSource.BOOK
            )
        ) = epubRenderedPageLayoutKey(
            readingOrderHrefs = listOf("one.xhtml", "two.xhtml"),
            viewportWidthPx = width,
            viewportHeightPx = 2000,
            contentPaddingLeftPx = 40,
            contentPaddingTopPx = 60,
            contentPaddingRightPx = 40,
            contentPaddingBottomPx = 90,
            densityDpi = 420,
            systemFontScale = 1f,
            webViewVersion = "123.0",
            twoPageSpreadActive = twoPageSpreadActive,
            spreadPageMarginCssPx = spreadPageMarginCssPx,
            contentLanguage = contentLanguage,
            preferences = preferences
        )

        val portrait = key()
        assertEquals(portrait, key())
        assertFalse(portrait == key(width = 2000))
        assertFalse(portrait == key(preferences = LibraryReaderPreferences(fontScale = 1.25f)))
        assertFalse(
            portrait == key(preferences = LibraryReaderPreferences(textAlignment = EpubTextAlignment.JUSTIFY))
        )
        assertFalse(
            portrait == key(preferences = LibraryReaderPreferences(hyphenation = EpubHyphenation.ON))
        )
        assertFalse(
            portrait == key(
                preferences = LibraryReaderPreferences(
                    padding = EpubPaddingPercentages(left = 25f)
                )
            )
        )
        assertFalse(
            portrait == key(preferences = LibraryReaderPreferences(twoPageSpread = ReaderSpreadMode.OFF))
        )
        assertFalse(portrait == key(twoPageSpreadActive = true))
        assertFalse(portrait == key(spreadPageMarginCssPx = 24.0))
        assertNull(key(spreadPageMarginCssPx = Double.NaN))
        assertFalse(
            portrait == key(
                contentLanguage = EpubContentLanguage("de", EpubContentLanguageSource.OVERRIDE)
            )
        )
        assertFalse(
            portrait == key(
                contentLanguage = EpubContentLanguage("en", EpubContentLanguageSource.OVERRIDE)
            )
        )
        assertNull(key(preferences = LibraryReaderPreferences(epubLayoutMode = ReaderLayoutMode.CONTINUOUS)))
    }

    @Test
    fun renderedEndMarkerOnlySurvivesForTheSameLocator() {
        val end = epubPosition("last chapter.xhtml", 0.875, 0.875)
        assertTrue(sameEpubRenderedLocation(end, epubPosition("last%20chapter.xhtml", 0.4, 0.875)))
        assertFalse(sameEpubRenderedLocation(end, epubPosition("last%20chapter.xhtml", 0.4, 0.75)))
        assertFalse(sameEpubRenderedLocation(end, epubPosition("other.xhtml", 0.4, 0.875)))
        assertFalse(sameEpubRenderedLocation(end, epubPosition("https://example.com/book.xhtml", 0.4, 0.875)))
    }

    @Test
    fun failedContentPositionGenerationDegradesToNoOverallIndex() = runBlocking {
        assertNull(
            loadEpubPositionsSafely<Int>(timeoutMillis = 1_000L) {
                throw IllegalStateException("malformed publication")
            }
        )
    }

    @Test
    fun ordinalFallbackUsesTheStartOfTheContentChunkAndStaysBelowCompletion() {
        assertEquals(
            80f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = null,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 5, count = 5)
            ),
            0.001f
        )
        assertEquals(
            0f,
            readiumOverallPercent(
                totalProgression = null,
                resourceProgression = null,
                chapterIndex = 0,
                chapterCount = 1,
                overallPosition = EpubOverallPosition(number = 1, count = 1)
            ),
            0f
        )
    }

    @Test
    fun invalidProgressionsCannotMasqueradeAsPublicationCompletion() {
        assertTrue(
            readiumOverallPercent(
                totalProgression = 1.5,
                resourceProgression = 2.0,
                chapterIndex = 1,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 5, count = 5)
            ) < 100f
        )
    }

    @Test
    fun stablePositionMappingIgnoresRawLocatorPositionValues() {
        val positions = validatedEpubCanonicalPositionIndex(
            listOf(
                epubPosition("one.xhtml", 0.0, 0.0),
                epubPosition("two.xhtml", 0.25, 0.1),
                epubPosition("two.xhtml", 0.5, 0.5),
                epubPosition("two.xhtml", 0.75, 0.8)
            ),
            readingOrderHrefs = listOf("one.xhtml", "two.xhtml")
        )!!

        assertEquals(EpubOverallPosition(1, 4), readiumOverallPosition(epubPosition("one.xhtml", 0.1), positions))
        assertEquals(EpubOverallPosition(2, 4), readiumOverallPosition(epubPosition("two.xhtml", 0.3), positions))
        assertEquals(EpubOverallPosition(3, 4), readiumOverallPosition(epubPosition("two.xhtml", 0.6), positions))
    }

    @Test
    fun duplicateLiveTotalProgressionStillAdvancesByCanonicalResourcePosition() {
        val index = validatedEpubCanonicalPositionIndex(
            positions = listOf(
                epubPosition("chapter.xhtml", 0.0, 0.0),
                epubPosition("chapter.xhtml", 0.25, 0.25),
                epubPosition("chapter.xhtml", 0.5, 0.5),
                epubPosition("chapter.xhtml", 0.75, 0.75)
            ),
            readingOrderHrefs = listOf("chapter.xhtml")
        )!!
        val earlier = readiumOverallPosition(epubPosition("chapter.xhtml", 0.5, 0.3), index)!!
        val later = readiumOverallPosition(epubPosition("chapter.xhtml", 0.5, 0.7), index)!!

        assertEquals(EpubOverallPosition(2, 4), earlier)
        assertEquals(EpubOverallPosition(3, 4), later)
        assertTrue(
            readiumOverallPercent(0.5, 0.7, 0, 1, later) >
                readiumOverallPercent(0.5, 0.3, 0, 1, earlier)
        )
    }

    @Test
    fun failedHrefMappingNeverFallsBackToLiveTotalProgression() {
        val index = validatedEpubCanonicalPositionIndex(
            positions = listOf(
                epubPosition("one.xhtml", 0.0, 0.0),
                epubPosition("one.xhtml", 0.5, 0.5)
            ),
            readingOrderHrefs = listOf("one.xhtml")
        )!!

        val unmapped = epubPosition("missing.xhtml", 0.9, 0.5)
        assertNull(readiumOverallPosition(unmapped, index))
        assertEquals(
            25f,
            readiumOverallPercent(0.9, 0.5, 0, 2, overallPosition = null),
            0f
        )
    }

    @Test
    fun canonicalMidBookPositionIgnoresPrematureLiveCompletion() {
        assertEquals(
            40f,
            readiumOverallPercent(
                totalProgression = 1.0,
                resourceProgression = 0.5,
                chapterIndex = 0,
                chapterCount = 2,
                overallPosition = EpubOverallPosition(number = 3, count = 5)
            ),
            0f
        )
    }

    @Test
    fun positionGenerationEligibilityAndValidationAreBounded() {
        assertTrue(shouldGenerateEpubContentPositions(1024, 2))
        assertFalse(shouldGenerateEpubContentPositions(MAX_EPUB_POSITION_SOURCE_BYTES + 1, 2))
        assertFalse(shouldGenerateEpubContentPositions(1024, MAX_EPUB_POSITION_READING_ORDER + 1))
        assertEquals(
            null,
            validatedEpubCanonicalPositionIndex(
                positions = listOf(epubPosition("one.xhtml", 0.7), epubPosition("two.xhtml", 0.4)),
                readingOrderHrefs = listOf("one.xhtml", "two.xhtml")
            )
        )
    }

    @Test
    fun readingOrderFingerprintDoesNotReuseJavaListHashCollisions() {
        val first = listOf("Aa")
        val second = listOf("BB")
        assertEquals(first.hashCode(), second.hashCode())
        assertFalse(epubReadingOrderFingerprint(first) == epubReadingOrderFingerprint(second))
    }

    @Test
    fun positionIndexRejectsExternalAndBacktrackingResources() {
        assertEquals(
            null,
            validatedEpubCanonicalPositionIndex(
                positions = listOf(epubPosition("https://outside.example/chapter.xhtml", 0.2)),
                readingOrderHrefs = listOf("one.xhtml")
            )
        )
        assertNull(
            validatedEpubCanonicalPositionIndex(
                positions = listOf(epubPosition("https://outside.example/chapter.xhtml", 0.2)),
                readingOrderHrefs = listOf("https://outside.example/chapter.xhtml")
            )
        )
        assertEquals(
            null,
            validatedEpubCanonicalPositionIndex(
                positions = listOf(
                    epubPosition("two.xhtml", 0.2, 0.1),
                    epubPosition("one.xhtml", 0.4, 0.2)
                ),
                readingOrderHrefs = listOf("one.xhtml", "two.xhtml")
            )
        )
    }

    @Test
    fun compressedEpubWithOversizedExpandedMarkupIsIneligibleForPositionGeneration() {
        val epub = Files.createTempFile("oversized-position-markup", ".epub").toFile()
        ZipOutputStream(epub.outputStream().buffered()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                "<container><rootfiles><rootfile full-path=\"OPS/package\"/></rootfiles></container>"
            )
            entry(
                "OPS/package",
                "<package><manifest><item id=\"c\" href=\"chapter\" media-type=\"application/xhtml+xml\"/></manifest></package>"
            )
            zip.putNextEntry(ZipEntry("OPS/chapter"))
            zip.write(ByteArray((MAX_EPUB_POSITION_MARKUP_ENTRY_BYTES + 1L).toInt()) { 'a'.code.toByte() })
            zip.closeEntry()
        }

        assertTrue(epub.length() < MAX_EPUB_POSITION_SOURCE_BYTES)
        assertFalse(shouldGenerateEpubContentPositions(epub, readingOrderHrefs = listOf("OPS/chapter")))
        epub.delete()
    }

    @Test
    fun extensionlessManifestXhtmlIsIncludedInPositionGenerationBounds() {
        val epub = Files.createTempFile("extensionless-position-markup", ".epub").toFile()
        ZipOutputStream(epub.outputStream().buffered()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                "<container><rootfiles><rootfile full-path=\"OPS/package\"/></rootfiles></container>"
            )
            entry(
                "OPS/package",
                "<package><manifest><item id=\"c\" href=\"chapter\" media-type=\"application/xhtml+xml\"/></manifest></package>"
            )
            entry("OPS/chapter", "<html><body>bounded</body></html>")
        }

        assertTrue(shouldGenerateEpubContentPositions(epub, listOf("OPS/chapter")))
        epub.delete()
    }

    @Test
    fun spaceContainingArchiveAndManifestHrefsRemainEligible() {
        val epub = Files.createTempFile("space-position-markup", ".epub").toFile()
        ZipOutputStream(epub.outputStream().buffered()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                "<container><rootfiles><rootfile full-path=\"content.opf\"/></rootfiles></container>"
            )
            entry(
                "content.opf",
                "<package><manifest><item id=\"c\" href=\"A Short Story.htm\" media-type=\"application/xhtml+xml\"/></manifest></package>"
            )
            entry("A Short Story.htm", "<html><body>bounded</body></html>")
        }

        assertEquals("A Short Story.htm", normalizedLocalEpubResourcePath("A Short Story.htm"))
        assertEquals("A Short Story.htm", normalizedLocalEpubResourcePath("A%20Short%20Story.htm"))
        assertTrue(shouldGenerateEpubContentPositions(epub, listOf("A%20Short%20Story.htm")))
        epub.delete()
    }

    @Test
    fun positionPreflightAcceptsSafeLocalNamesWithoutWeakeningPathChecks() {
        assertEquals("Text/A + B.xhtml", normalizedLocalEpubResourcePath("Text/A%20+%20B.xhtml"))
        assertEquals("Text/日本語.xhtml", normalizedLocalEpubResourcePath("Text/日本語.xhtml"))
        assertEquals("Text/100%.xhtml", normalizedLocalEpubArchivePath("Text/100%.xhtml"))
        assertEquals("Text/100%.xhtml", normalizedLocalEpubResourcePath("Text/100%25.xhtml"))
        assertEquals("Text/chapter.xhtml", normalizedLocalEpubResourcePath("OPS/../Text/chapter.xhtml"))

        listOf(
            "https://outside.example/chapter.xhtml",
            "//outside.example/chapter.xhtml",
            "Text/chapter.xhtml?download=true",
            "../outside.xhtml",
            "%2e%2e/outside.xhtml",
            "Text%5Coutside.xhtml",
            "Text%00outside.xhtml"
        ).forEach { unsafe ->
            assertNull("Expected unsafe path to be rejected: $unsafe", normalizedLocalEpubResourcePath(unsafe))
        }
    }

    @Test
    fun normalizedDuplicateArchiveEntriesRemainIneligible() {
        val epub = Files.createTempFile("duplicate-position-path", ".epub").toFile()
        ZipOutputStream(epub.outputStream().buffered()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                "<container><rootfiles><rootfile full-path=\"content.opf\"/></rootfiles></container>"
            )
            entry(
                "content.opf",
                "<package><manifest><item id=\"c\" href=\"Text/chapter.xhtml\" media-type=\"application/xhtml+xml\"/></manifest></package>"
            )
            entry("Text/chapter.xhtml", "<html><body>first</body></html>")
            entry("Text/./chapter.xhtml", "<html><body>duplicate</body></html>")
        }

        assertFalse(shouldGenerateEpubContentPositions(epub, listOf("Text/chapter.xhtml")))
        epub.delete()
    }

    @Test
    fun ttsProgressUsesCanonicalResourcePositionInsteadOfRepeatedLiveTotal() {
        val index = validatedEpubCanonicalPositionIndex(
            positions = listOf(
                epubPosition("chapter.xhtml", 0.0, 0.0),
                epubPosition("chapter.xhtml", 0.25, 0.25),
                epubPosition("chapter.xhtml", 0.5, 0.5),
                epubPosition("chapter.xhtml", 0.75, 0.75)
            ),
            readingOrderHrefs = listOf("chapter.xhtml")
        )!!

        val earlier = epubTtsServerProgressPercent(
            epubPosition("chapter.xhtml", 0.5, 0.3), 0, 1, index
        )
        val later = epubTtsServerProgressPercent(
            epubPosition("chapter.xhtml", 0.5, 0.7), 0, 1, index
        )

        assertTrue(later > earlier)
    }

    @Test
    fun oversizedTtsLocatorIsOmittedFromServerProgress() {
        val locator = """{"href":"chapter.xhtml","text":{"highlight":"${
            "x".repeat(MAX_SAVED_READER_LOCATOR_BYTES)
        }"}}"""

        assertNull(boundedEpubTtsServerLocatorJson(locator))
    }

    @Test
    fun authenticatedCanonicalReaderDoesNotAdoptOldAuxiliaryLocator() {
        assertFalse(
            shouldRestoreLegacyEpubLocator(
                hasCanonicalIdentity = true,
                staleSavedStateRejected = false
            )
        )
        assertFalse(
            shouldRestoreLegacyEpubLocator(
                hasCanonicalIdentity = true,
                staleSavedStateRejected = true
            )
        )
        assertTrue(
            shouldRestoreLegacyEpubLocator(
                hasCanonicalIdentity = false,
                staleSavedStateRejected = false
            )
        )
    }

    @Test
    fun positionGenerationSingleFlightSharesOneJobAcrossRotation() = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val starts = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        try {
            val singleFlight = EpubPositionSingleFlight<Int>(scope)
            val first = singleFlight.getOrStart("publication") {
                starts.incrementAndGet()
                release.await()
                7
            }
            val rotated = singleFlight.getOrStart("publication") { 8 }

            assertSame(first, rotated)
            release.complete(Unit)
            assertEquals(7, first.await())
            assertEquals(1, starts.get())
        } finally {
            scope.cancel()
            dispatcher.close()
        }
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
