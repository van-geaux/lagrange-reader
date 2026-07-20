package com.bookorbit.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EpubReaderOptionsOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomSheetSeparatesContinueReadingFromClosingTheBook() {
        val dismissCount = mutableIntStateOf(0)
        val continueCount = mutableIntStateOf(0)
        val closeBookCount = mutableIntStateOf(0)

        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                EpubReaderDismissScrim(onDismiss = { dismissCount.intValue++ })
                EpubReaderOptionsBottomSheet(
                    title = "Test Book",
                    status = "Chapter 1/2 · Page 3/4",
                    theme = EpubReaderTheme.Sepia,
                    padding = EpubPaddingPercentages(),
                    fontScale = 1f,
                    onContinueReading = { continueCount.intValue++ },
                    onCloseBook = { closeBookCount.intValue++ },
                    onThemeSelected = {},
                    onPaddingChange = {},
                    onPaddingChangeFinished = {},
                    onDecreaseFont = {},
                    onIncreaseFont = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Back").assertCountEquals(0)
        composeRule.onAllNodesWithText("Continue reading").assertCountEquals(1)
        composeRule.onAllNodesWithText("Close book").assertCountEquals(1)
        composeRule.onNodeWithText("Reader options").assertIsDisplayed()
        composeRule.onAllNodesWithText("Reading position").assertCountEquals(0)
        composeRule.onAllNodesWithText("Choose chapter").assertCountEquals(0)
        composeRule.onAllNodesWithText("Page 3 of 4").assertCountEquals(0)
        composeRule.onNodeWithText("Continue reading").performClick()
        composeRule.onNodeWithText("Close book").performClick()
        composeRule
            .onNodeWithContentDescription("Dismiss reader options and continue reading")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, continueCount.intValue)
            assertEquals(1, closeBookCount.intValue)
            assertEquals(1, dismissCount.intValue)
        }
    }

    @Test
    fun comicOptionsDoNotDuplicateTheOuterPageSlider() {
        composeRule.setContent {
            ComicReaderOptionsBottomSheet(
                title = "Test Comic",
                currentPage = 2,
                pageCount = 5,
                onContinueReading = {},
                onCloseBook = {}
            )
        }

        composeRule.onNodeWithText("Reader options").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3 of 5").assertIsDisplayed()
        composeRule.onAllNodesWithText("Reading position").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Comic reading position").assertCountEquals(0)
    }
}
