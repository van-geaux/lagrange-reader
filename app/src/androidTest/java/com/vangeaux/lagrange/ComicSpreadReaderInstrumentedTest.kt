package com.vangeaux.lagrange

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicSpreadReaderInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val portrait = Bitmap.createBitmap(66, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private val wide = Bitmap.createBitmap(140, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }

    private class Recorder {
        val spreads = mutableListOf<ComicSpread>()
        val taps = mutableListOf<ContinuousComicTapAction>()
        var longPressedPage: Int? = null
        var navigator: ComicSpreadNavigator? = null
        val loadRequests = mutableListOf<Pair<Int, Int>>()
    }

    private fun setReader(
        recorder: Recorder,
        pageCount: Int = 9,
        initialPage: Int = 0,
        readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
        widePages: () -> Set<Int> = { emptySet() },
        bitmapFor: (Int) -> Bitmap = { portrait }
    ) {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 900.dp, height = 600.dp)) {
                ComicSpreadReader(
                    pageCount = pageCount,
                    initialPage = initialPage,
                    coverAlone = true,
                    shift = false,
                    widePages = widePages(),
                    readingDirection = readingDirection,
                    tapZoneLayout = ReaderTapZoneLayout.CURRENT_EDGES,
                    tapZoneInvertMode = ReaderTapZoneInvertMode.NONE,
                    gutterForWidth = { width -> readerSpreadGutterPx(width, null, 0) },
                    animateNavigation = { false },
                    cachedPage = { _, _ -> null },
                    loadPage = { page, width ->
                        synchronized(recorder.loadRequests) { recorder.loadRequests += page to width }
                        bitmapFor(page)
                    },
                    onSpreadChanged = { recorder.spreads += it },
                    onTap = { recorder.taps += it },
                    onLongPress = { page, _ -> recorder.longPressedPage = page },
                    onNavigatorAvailable = { recorder.navigator = it }
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun opensOnTheSpreadContainingTheInitialPageAndReportsIt() {
        val recorder = Recorder()
        setReader(recorder, initialPage = 4)

        composeRule.onNodeWithContentDescription("Pages 4 and 5").assertIsDisplayed()
        assertEquals(ComicSpread(listOf(3, 4)), recorder.spreads.last())
    }

    @Test
    fun coverIsShownAloneThenPagesPair() {
        val recorder = Recorder()
        setReader(recorder, initialPage = 0)

        composeRule.onNodeWithContentDescription("Page 1").assertIsDisplayed()
        composeRule.runOnIdle { requireNotNull(recorder.navigator).step(1) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pages 2 and 3").assertIsDisplayed()
        assertEquals(ComicSpread(listOf(1, 2)), recorder.spreads.last())
    }

    @Test
    fun navigatorGoToPageAndStepStayWithinTheBook() {
        val recorder = Recorder()
        setReader(recorder, pageCount = 9)
        val navigator = requireNotNull(recorder.navigator)

        composeRule.runOnIdle { navigator.goToPage(8) }
        composeRule.waitForIdle()
        assertEquals(ComicSpread(listOf(7, 8)), recorder.spreads.last())

        composeRule.runOnIdle { navigator.step(5) }
        composeRule.waitForIdle()
        assertEquals(ComicSpread(listOf(7, 8)), recorder.spreads.last())

        composeRule.runOnIdle { navigator.step(-1) }
        composeRule.waitForIdle()
        assertEquals(ComicSpread(listOf(5, 6)), recorder.spreads.last())

        composeRule.runOnIdle { navigator.goToPage(-3) }
        composeRule.waitForIdle()
        assertEquals(ComicSpread(listOf(0)), recorder.spreads.last())
    }

    @Test
    fun edgeTapsMapToTapZoneActionsAcrossTheWholeSpread() {
        val recorder = Recorder()
        setReader(recorder, initialPage = 2)

        val reader = composeRule.onNodeWithContentDescription("Pages 2 and 3")
        reader.performTouchInput { click(centerRight.copy(x = width * 0.95f)) }
        composeRule.waitForIdle()
        reader.performTouchInput { click(centerLeft.copy(x = width * 0.05f)) }
        composeRule.waitForIdle()
        reader.performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                ContinuousComicTapAction.NEXT,
                ContinuousComicTapAction.PREVIOUS,
                ContinuousComicTapAction.MENU
            ),
            recorder.taps
        )
    }

    @Test
    fun longPressExportsThePageUnderTheFingerInRightToLeftBooks() {
        val recorder = Recorder()
        setReader(recorder, initialPage = 2, readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT)

        val reader = composeRule.onNodeWithContentDescription("Pages 2 and 3")
        // Right-to-left: the earlier page (index 1) sits on the right half.
        reader.performTouchInput { longClick(centerRight.copy(x = width * 0.75f)) }
        composeRule.waitForIdle()
        assertEquals(1, recorder.longPressedPage)

        reader.performTouchInput { longClick(centerLeft.copy(x = width * 0.25f)) }
        composeRule.waitForIdle()
        assertEquals(2, recorder.longPressedPage)
    }

    @Test
    fun pairedPagesDecodeAtHalfWidthAndLonePagesAtFullWidth() {
        val recorder = Recorder()
        setReader(recorder, initialPage = 0)
        composeRule.waitUntil(5_000) {
            synchronized(recorder.loadRequests) {
                recorder.loadRequests.any { it.first == 1 } && recorder.loadRequests.any { it.first == 0 }
            }
        }

        val requests = synchronized(recorder.loadRequests) { recorder.loadRequests.toList() }
        val coverWidth = requests.first { it.first == 0 }.second
        val pairedWidth = requests.first { it.first == 1 }.second
        assertTrue("cover $coverWidth vs paired $pairedWidth", coverWidth >= pairedWidth * 2 - 1)
    }

    @Test
    fun pinchZoomLocksPageSwipesUntilZoomedBackOut() {
        val recorder = Recorder()
        setReader(recorder, initialPage = 2)
        val reader = composeRule.onNodeWithContentDescription("Pages 2 and 3")

        reader.performTouchInput {
            pinch(
                start0 = center - Offset(20f, 0f),
                end0 = center - Offset(200f, 0f),
                start1 = center + Offset(20f, 0f),
                end1 = center + Offset(200f, 0f)
            )
        }
        composeRule.waitForIdle()
        reader.performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals("zoomed spread must not page", ComicSpread(listOf(1, 2)), recorder.spreads.last())

        reader.performTouchInput {
            pinch(
                start0 = center - Offset(250f, 0f),
                end0 = center - Offset(5f, 0f),
                start1 = center + Offset(250f, 0f),
                end1 = center + Offset(5f, 0f)
            )
        }
        composeRule.waitForIdle()
        reader.performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(ComicSpread(listOf(3, 4)), recorder.spreads.last())
    }

    @Test
    fun discoveringAWidePageRepairsWithoutLosingTheCurrentPage() {
        val recorder = Recorder()
        var widePages by mutableStateOf(emptySet<Int>())
        setReader(recorder, initialPage = 5, widePages = { widePages }, bitmapFor = { page ->
            if (page == 4) wide else portrait
        })
        assertEquals(ComicSpread(listOf(5, 6)), recorder.spreads.last())

        composeRule.runOnIdle { widePages = setOf(4) }
        composeRule.waitForIdle()

        val current = recorder.spreads.last()
        assertTrue("page 5 must stay visible, was $current", 5 in current)
        assertNotNull(composeRule.onNodeWithTag(COMIC_SPREAD_READER_TEST_TAG).assertIsDisplayed())
    }
}
