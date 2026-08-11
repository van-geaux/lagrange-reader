package com.vangeaux.lagrange

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityBrowserRecreationInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @After
    fun clearGraphOverride() {
        MainActivityGraphProvider.testFactory = null
    }

    @Test
    fun bookDetailsSurviveTwoMainActivityRecreationsAndBootstraps() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-main-activity-recreation",
            fileId = "file-main-activity-recreation",
            title = "Main Activity Orientation Book",
            format = "epub",
            mediaKind = MediaKind.EPUB
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            serverUrl = "https://books.example.test"
            sessionState = SessionState.Authenticated
            selectedLibraryId = "lib-1"
            librariesResult = listOf(LibrarySummary(id = "lib-1", name = "Main"))
            loadBooksResult = listOf(book)
            bookDetailResult = BookDetailInfo(book = book, libraryName = "Main")
        }
        MainActivityGraphProvider.testFactory = {
            AppGraph(AppCoordinator(dataSource, Dispatchers.Main))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("Main Activity Orientation Book")
            composeRule.onNodeWithContentDescription("Main Activity Orientation Book").performClick()
            waitForText("Book details")

            repeat(2) {
                scenario.recreate()
                waitForText("Book details")
                composeRule.onNodeWithText("Book details").assertIsDisplayed()
                composeRule.onNodeWithText("Main Activity Orientation Book").assertIsDisplayed()
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
