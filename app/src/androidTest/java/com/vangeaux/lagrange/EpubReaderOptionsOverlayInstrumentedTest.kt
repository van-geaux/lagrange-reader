package com.vangeaux.lagrange

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EpubReaderOptionsOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readiumPreferencesEnableUserCssOverridesForLineSpacing() {
        val preferences = readiumPreferences(
            theme = EpubReaderTheme.Light,
            fontScale = 1.0f,
            lineSpacing = 1.4f,
            wordSpacing = 0.4f
        )

        assertEquals(1.4, preferences.lineHeight)
        assertEquals(0.4, preferences.wordSpacing)
        assertFalse(preferences.publisherStyles == true)
    }

    @Test
    fun readerOptionsSheetDefaultsToTwoThirdsAndCanExpand() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 600.dp)) {
                EpubReaderOptionsBottomSheet(
                    title = "Resize test",
                    status = "Chapter 1/1 · Page 1/1",
                    preferences = LibraryReaderPreferences(),
                    onContinueReading = {},
                    onCloseBook = {},
                    onPreferencesChange = {},
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        composeRule.onNodeWithTag("reader-options-sheet").assertIsDisplayed()

        composeRule.onNodeWithText("Reading configuration")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("reader-options-resize-handle")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(READER_OPTIONS_RESIZE_HANDLE_MIN_HEIGHT_DP.dp)

        composeRule
            .onNodeWithTag("reader-options-resize-handle")
            .performTouchInput { swipeUp() }

        composeRule.onNodeWithTag("reader-options-sheet").assertIsDisplayed()
    }

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
                    preferences = LibraryReaderPreferences(),
                    onContinueReading = { continueCount.intValue++ },
                    onCloseBook = { closeBookCount.intValue++ },
                    onPreferencesChange = {}
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
                preferences = LibraryReaderPreferences(),
                onContinueReading = {},
                onCloseBook = {},
                onPreferencesChange = {}
            )
        }

        composeRule.onNodeWithText("Reader options").assertIsDisplayed()
        composeRule.onNodeWithText("Page 3 of 5").assertIsDisplayed()
        composeRule.onAllNodesWithText("Reading position").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Comic reading position").assertCountEquals(0)
        composeRule.onNodeWithText("Reading configuration").assertIsDisplayed()
        composeRule.onAllNodesWithText("Library reader profile").assertCountEquals(0)
        composeRule.onNodeWithText("CBR/CBZ layout").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reader-options-reading-comic-layout-continuous")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Fonts").assertCountEquals(0)
        composeRule.onAllNodesWithText("Accessibility fonts").assertCountEquals(0)
    }

    @Test
    fun epubFontChoicesAreSeparatedAndPreserveOtherReaderSettings() {
        val profile = mutableStateOf(
            LibraryReaderPreferences(
                theme = EpubReaderTheme.Dark,
                fontScale = 1.3f,
                lineSpacing = 1.2f,
                wordSpacing = 0.2f,
                readingDirection = LibraryReadingDirection.RIGHT_TO_LEFT,
                epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
                padding = EpubPaddingPercentages(11f, 22f, 33f, 44f)
            )
        )
        composeRule.setContent {
            EpubReaderOptionsBottomSheet(
                title = "Font test",
                status = "Chapter 1/1 · Page 1/1",
                preferences = profile.value,
                onContinueReading = {},
                onCloseBook = {},
                onPreferencesChange = { profile.value = it }
            )
        }

        composeRule.onNodeWithText("Fonts").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-options-reading-font-family-dropdown")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Normal fonts").assertIsDisplayed()
        composeRule.onNodeWithText("Accessibility fonts").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-options-reading-font-family-system_sans_serif")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithTag("reader-options-reading-line-spacing")
            .performScrollTo()
            .performTouchInput { swipeRight() }
        composeRule
            .onNodeWithTag("reader-options-reading-word-spacing")
            .performScrollTo()
            .performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertEquals(EpubReaderFontFamily.SYSTEM_SANS_SERIF, profile.value.fontFamily)
            assertEquals(EpubReaderTheme.Dark, profile.value.theme)
            assertEquals(1.3f, profile.value.fontScale)
            assertTrue(profile.value.lineSpacing > 1.2f)
            assertTrue(profile.value.wordSpacing > 0.2f)
            assertEquals(LibraryReadingDirection.RIGHT_TO_LEFT, profile.value.readingDirection)
            assertEquals(ReaderLayoutMode.CONTINUOUS, profile.value.epubLayoutMode)
            assertEquals(EpubPaddingPercentages(11f, 22f, 33f, 44f), profile.value.padding)
        }
    }

    @Test
    fun readerConfigurationEditsPreserveAllFormatLayoutsAndGaps() {
        val profile = mutableStateOf(
            LibraryReaderPreferences(
                epubLayoutMode = ReaderLayoutMode.CONTINUOUS,
                pdfLayoutMode = ReaderLayoutMode.PAGINATED,
                pdfPageGapDp = 8f,
                comicLayoutMode = ReaderLayoutMode.CONTINUOUS,
                comicPageGapDp = 24f
            )
        )
        composeRule.setContent {
            ComicReaderOptionsBottomSheet(
                title = "Profile test",
                currentPage = 0,
                pageCount = 1,
                preferences = profile.value,
                onContinueReading = {},
                onCloseBook = {},
                onPreferencesChange = { profile.value = it }
            )
        }

        composeRule.onNodeWithTag("reader-options-reading-theme-dark")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(EpubReaderTheme.Dark, profile.value.theme)
            assertEquals(ReaderLayoutMode.CONTINUOUS, profile.value.epubLayoutMode)
            assertEquals(ReaderLayoutMode.PAGINATED, profile.value.pdfLayoutMode)
            assertEquals(8f, profile.value.pdfPageGapDp)
            assertEquals(ReaderLayoutMode.CONTINUOUS, profile.value.comicLayoutMode)
            assertEquals(24f, profile.value.comicPageGapDp)
        }
    }
}
