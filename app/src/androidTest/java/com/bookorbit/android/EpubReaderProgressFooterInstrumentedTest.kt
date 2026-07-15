package com.bookorbit.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class EpubReaderProgressFooterInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun footerShowsCompletionChapterPageAndNormalizedBookLocation() {
        val status = epubReaderProgressStatus(
            chapterIndex = 2,
            chapterCount = 10,
            pageIndex = 3,
            pageCount = 12
        )

        composeRule.setContent {
            BookOrbitTheme {
                EpubReaderProgressFooter(
                    status = status,
                    theme = EpubReaderTheme.Sepia
                )
            }
        }

        composeRule
            .onNodeWithText("23% · Chapter 3/10 · Page 4/12 · Book 233/1000")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                "Book completion 23 percent; chapter 3 of 10; chapter page 4 of 12; " +
                    "book location 233 of 1000"
            )
            .assertIsDisplayed()
    }
}
