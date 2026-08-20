package com.vangeaux.lagrange

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudiobookChapterListInsetsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chapterListViewportStopsAboveNavigationControls() {
        composeRule.setContent {
            MaterialTheme {
                FullPlayerChapterListSheet(
                    chapters = List(20) { index ->
                        AudiobookChapter(
                            title = "Chapter ${index + 1}",
                            startMs = index * 60_000L
                        )
                    },
                    activeChapterIndex = 4,
                    onChapterSelected = {},
                    onClose = {}
                )
            }
        }

        composeRule.waitForIdle()
        val sheetBounds = composeRule
            .onNodeWithTag("audiobook-chapter-sheet")
            .fetchSemanticsNode()
            .boundsInRoot
        val contentBounds = composeRule
            .onNodeWithTag("audiobook-chapter-sheet-content")
            .fetchSemanticsNode()
            .boundsInRoot
        val listBounds = composeRule
            .onNodeWithTag("audiobook-chapter-list")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "The chapter list must end inside the sheet content",
            listBounds.bottom <= contentBounds.bottom
        )
        assertTrue(
            "The navigation-padded sheet content must keep the list above the sheet bottom",
            listBounds.bottom < sheetBounds.bottom
        )
    }
}
