package com.bookorbit.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderLightweightChromeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun epubChromeSeparatesNavigationListsAndSettings() {
        val backCount = mutableIntStateOf(0)
        val closeCount = mutableIntStateOf(0)
        val settingsCount = mutableIntStateOf(0)
        val selectedPage = mutableIntStateOf(-1)
        val selectedChapter = mutableIntStateOf(-1)

        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().testTag("reader-root")) {
                ReaderLightweightChrome(
                    title = "Test Book",
                    theme = EpubReaderTheme.Sepia,
                    positionKind = "Page",
                    positionTitles = listOf("Page 1", "Page 2", "Page 3", "Page 4"),
                    currentPosition = 1,
                    onBackToReading = { backCount.intValue++ },
                    onCloseBook = { closeCount.intValue++ },
                    onOpenSettings = { settingsCount.intValue++ },
                    onPositionSelected = { selectedPage.intValue = it },
                    listPositionKind = "Chapter",
                    listPositionTitles = listOf("Opening", "Second Chapter", "Finale"),
                    currentListPosition = 0,
                    onListPositionSelected = { selectedChapter.intValue = it },
                    secondaryPositionKind = "Chapter",
                    secondaryCurrentPosition = 0,
                    secondaryPositionCount = 3,
                    onSecondaryPositionSelected = { selectedChapter.intValue = it }
                )
            }
        }

        composeRule.onNodeWithTag("reader-lightweight-top-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-lightweight-position-control").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-lightweight-bottom-bar").assertIsDisplayed()
        composeRule.onNodeWithText("Test Book").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Exit").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous chapter").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next chapter").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Previous page").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Next page").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("Page jump bar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to reading").performClick()
        composeRule.onNodeWithContentDescription("Close book").performClick()
        composeRule.onNodeWithContentDescription("Reader settings").performClick()
        composeRule.onNodeWithText("Chapters").performClick()
        composeRule.onNodeWithContentDescription("Select chapter 2: Second Chapter").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backCount.intValue)
            assertEquals(1, closeCount.intValue)
            assertEquals(1, settingsCount.intValue)
            assertEquals(2, selectedPage.intValue)
            assertEquals(1, selectedChapter.intValue)
        }

        val rootBounds = composeRule.onNodeWithTag("reader-root").fetchSemanticsNode().boundsInRoot
        val controlBounds = composeRule
            .onNodeWithTag("reader-lightweight-position-control")
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(rootBounds.right, controlBounds.right, 1f)
        assertEquals(
            rootBounds.height * READER_POSITION_CONTROL_HEIGHT_FRACTION,
            controlBounds.height,
            2f
        )
    }

    @Test
    fun comicChromeUsesPageVocabulary() {
        composeRule.setContent {
            ReaderLightweightChrome(
                title = "Test Comic",
                theme = EpubReaderTheme.Dark,
                positionKind = "Page",
                positionTitles = listOf("Page 1", "Page 2"),
                currentPosition = 1,
                onBackToReading = {},
                onCloseBook = {},
                onOpenSettings = {},
                onPositionSelected = {}
            )
        }

        composeRule.onNodeWithText("Pages").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous page").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Next page").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Page jump bar").assertIsDisplayed()
    }

    @Test
    fun tapZoneTutorialShowsThreeEqualLabeledRegions() {
        composeRule.setContent {
            ReaderTapZoneTutorial()
        }

        composeRule.onNodeWithTag("reader-tap-zone-tutorial").assertIsDisplayed()
        val regionTags = listOf(
            "reader-tap-zone-previous",
            "reader-tap-zone-menu",
            "reader-tap-zone-next"
        )
        regionTags.forEach { tag -> composeRule.onNodeWithTag(tag).assertIsDisplayed() }
        composeRule.onNodeWithText("Previous").assertIsDisplayed()
        composeRule.onNodeWithText("Menu").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()

        val bounds = regionTags.map { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].width, bounds[1].width, 1f)
        assertEquals(bounds[1].width, bounds[2].width, 1f)
        assertTrue(bounds[0].left < bounds[1].left)
        assertTrue(bounds[1].left < bounds[2].left)
    }
}
