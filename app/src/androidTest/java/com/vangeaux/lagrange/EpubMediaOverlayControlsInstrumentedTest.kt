package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EpubMediaOverlayControlsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playerShowsSpeedAndRightmostCloseWithoutClipCounter() {
        val closeCount = mutableIntStateOf(0)
        val speed = mutableFloatStateOf(1f)
        composeRule.setContent {
            BookOrbitTheme {
                Box(Modifier.fillMaxSize().testTag("readalong-controls-root")) {
                    EpubMediaOverlayControls(
                        speed = speed.floatValue,
                        isPlaying = true,
                        canGoPrevious = true,
                        canGoNext = true,
                        onPlayPause = {},
                        onPrevious = {},
                        onNext = {},
                        onSpeedChange = { speed.floatValue = it },
                        onClose = { closeCount.intValue++ }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Read-along").assertIsDisplayed()
        composeRule.onNodeWithText("1.00×").assertIsDisplayed()
        composeRule.onNodeWithText("63/5245").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Close read-along").assertIsDisplayed()
        val closeBounds = composeRule.onNodeWithContentDescription("Close read-along")
            .fetchSemanticsNode().boundsInRoot
        val nextBounds = composeRule.onNodeWithContentDescription("Next narration sentence")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(closeBounds.right > nextBounds.right)

        composeRule.onNodeWithContentDescription("Close read-along").performClick()
        composeRule.runOnIdle { assertEquals(1, closeCount.intValue) }
    }

    @Test
    fun speedButtonUsesAudiobookSpeedOverlayAndAdjustmentSteps() {
        val speed = mutableFloatStateOf(1f)
        composeRule.setContent {
            BookOrbitTheme {
                EpubMediaOverlayControls(
                    speed = speed.floatValue,
                    isPlaying = false,
                    canGoPrevious = false,
                    canGoNext = true,
                    onPlayPause = {},
                    onPrevious = {},
                    onNext = {},
                    onSpeedChange = { speed.floatValue = it },
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Select read-along speed").performClick()
        composeRule.onNodeWithText("Playback speed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Increase playback speed by 0.05").performClick()
        composeRule.runOnIdle { assertEquals(1.05f, speed.floatValue, 0.0001f) }
    }

    @Test
    fun progressFooterKeepsItsCompactStatusRowHeight() {
        val status = epubReaderProgressStatus(
            chapterIndex = 1,
            chapterCount = 3,
            pageIndex = 0,
            pageCount = 5
        )
        composeRule.setContent {
            BookOrbitTheme {
                Box(Modifier.width(320.dp).height(30.dp).testTag("epub-progress-footer-test-root")) {
                    EpubReaderProgressFooter(
                        status = status,
                        theme = EpubReaderTheme.Sepia,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        val progressBounds = composeRule.onNodeWithText(status.displayText())
            .fetchSemanticsNode().boundsInRoot
        val footerBounds = composeRule.onNodeWithTag("epub-progress-footer-test-root")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(progressBounds.top >= footerBounds.top)
        assertTrue(progressBounds.bottom <= footerBounds.bottom)
    }
}
