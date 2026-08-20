package com.vangeaux.lagrange

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailAvailableFilePickerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactSelectorShowsDetailsAndSelectsFromSheet() {
        val selectedFileId = mutableStateOf<String?>("file-1")
        val options = listOf(
            fileOption(
                fileId = "file-1",
                format = "application/epub+zip",
                filename = "primary-book.epub",
                sizeBytes = 12L * 1024L * 1024L,
                role = "primary",
                localPath = "/local/primary-book.epub"
            ),
            fileOption(
                fileId = "file-2",
                format = "audio/m4b",
                filename = "audiobook.m4b",
                sizeBytes = 684L * 1024L * 1024L,
                role = "alternate"
            ),
            fileOption(
                fileId = "file-3",
                format = "application/vnd.comicbook+zip",
                filename = "illustrated-edition.cbz",
                sizeBytes = 240L * 1024L * 1024L,
                role = "alternate"
            )
        )

        composeRule.setContent {
            var sheetVisible by remember { mutableStateOf(false) }
            BookDetailAvailableFileSummary(
                options = options,
                selectedFileId = selectedFileId.value,
                onOpenSheet = { sheetVisible = true }
            )
            if (sheetVisible) {
                BookDetailAvailableFileSheet(
                    options = options,
                    selectedFileId = selectedFileId.value,
                    onFileSelected = { selectedFileId.value = it },
                    onDismissRequest = { sheetVisible = false }
                )
            }
        }

        composeRule.onNodeWithTag("book-detail-available-file")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("book-detail-available-file-format")
            .assertTextContains("EPUB")
        composeRule.onNodeWithTag("book-detail-available-file-name")
            .assertTextContains("primary-book.epub")
        composeRule.onNodeWithTag("book-detail-available-file-metadata")
            .assertTextContains("Primary")
            .assertTextContains("Available offline")

        composeRule.onNodeWithTag("book-detail-available-file").performClick()
        composeRule.onNodeWithTag("book-detail-available-file-sheet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
        composeRule.onNodeWithTag("book-detail-available-file-option-name-file-1")
            .assertTextContains("primary-book.epub")
        composeRule.onNodeWithTag("book-detail-available-file-option-name-file-3")
            .assertTextContains("illustrated-edition.cbz")
        composeRule.onNodeWithTag("book-detail-available-file-option-file-3")
            .performClick()

        assertEquals("file-3", selectedFileId.value)
        composeRule.onNodeWithTag("book-detail-available-file-sheet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
        composeRule.onNodeWithTag("book-detail-available-file-option-name-file-3")
            .assertTextContains("illustrated-edition.cbz")
        composeRule.onNodeWithTag("book-detail-available-file-name")
            .assertTextContains("illustrated-edition.cbz")
        pressBack()
        composeRule.onNodeWithTag("book-detail-available-file-sheet").assertDoesNotExist()
    }

    @Test
    fun singleFileShowsCompactNonInteractiveSummary() {
        val option = fileOption(
            fileId = "file-1",
            format = "application/epub+zip",
            filename = "only-file.epub",
            sizeBytes = 8L * 1024L * 1024L,
            role = "primary"
        )

        composeRule.setContent {
            BookDetailAvailableFileSummary(
                options = listOf(option),
                selectedFileId = option.fileId,
                onOpenSheet = {}
            )
        }

        composeRule.onNodeWithTag("book-detail-available-file")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onNodeWithText("EPUB").assertIsDisplayed()
        composeRule.onNodeWithText("only-file.epub").assertIsDisplayed()
        composeRule.onNodeWithTag("book-detail-available-file-metadata")
            .assertTextContains("Primary")
        composeRule.onNodeWithContentDescription("Choose available file").assertDoesNotExist()
    }

    private fun fileOption(
        fileId: String,
        format: String,
        filename: String,
        sizeBytes: Long,
        role: String,
        localPath: String? = null
    ): BookFileOption = BookFileOption(
        book = BookSummary(
            libraryId = "library",
            id = "book",
            fileId = fileId,
            title = "Test book",
            format = format,
            localPath = localPath
        ),
        filename = filename,
        sizeBytes = sizeBytes,
        role = role
    )
}
