package com.bookorbit.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class BookOrbitAppInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun serverSetupRejectsUnsupportedScheme() {
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.ServerSetup(),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Server URL").performTextInput("httpx://books.example.test")
        composeRule.onNodeWithText("Continue").performClick()

        composeRule.onNodeWithText(invalidServerUrlMessage()).assertIsDisplayed()
    }

    @Test
    fun offlineBrowserDisablesUnavailableBookActions() {
        val unavailableBook = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Remote Only",
            mediaKind = MediaKind.EPUB
        )

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(unavailableBook),
                            isOfflineSnapshot = true
                        )
                    ),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Sign in").assertIsEnabled()
        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Unavailable offline").assertIsDisplayed()
    }

    @Test
    fun loginScreenShowsRecoveryMessageAndCanChangeServer() {
        val dataSource = InstrumentedFakeDataSource()

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Login(
                        serverUrl = "about:blank",
                        message = "Sign in again to continue browsing."
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in again to continue browsing.").assertIsDisplayed()
        composeRule.onNodeWithText("Change server").performClick()
        composeRule.waitUntil { dataSource.clearServerCalls == 1 }
    }

    @Test
    fun liveBrowserShowsLibrariesBooksAndAvailableActions() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "The Test Book",
            author = "Test Author",
            mediaKind = MediaKind.EPUB
        )

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(book)
                        )
                    ),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Recently added books").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Log out").assertIsEnabled()
        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Main").assertIsDisplayed()
        composeRule.onNodeWithText("The Test Book").assertIsDisplayed()
        composeRule.onNodeWithText("Test Author").assertIsDisplayed()
        composeRule.onNodeWithText("Details").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Read").assertIsEnabled()
        composeRule.onNodeWithText("Download").assertIsEnabled()
    }

    @Test
    fun browserLoadingStateDisablesRefreshAndLibrarySelection() {
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = emptyList(),
                            isRefreshing = true,
                            isLoadingBooks = true
                        )
                    ),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Main").assertIsNotEnabled()
        composeRule.onNodeWithText("Loading books...").assertIsDisplayed()
    }
}

private class InstrumentedFakeDataSource : BookOrbitDataSource {
    var clearServerCalls = 0

    override suspend fun getServerUrl(): String? = null
    override suspend fun setServerUrl(serverUrl: String) = Unit
    override suspend fun clearServer() {
        clearServerCalls += 1
    }
    override suspend fun clearSession() = Unit
    override suspend fun getSelectedLibraryId(): String? = null
    override suspend fun setSelectedLibraryId(libraryId: String) = Unit
    override suspend fun getSessionState(): SessionState = SessionState.Unauthenticated
    override suspend fun login(username: String, password: String) = Unit
    override suspend fun loadLibraries(): List<LibrarySummary> = emptyList()
    override suspend fun loadBooks(libraryId: String): List<BookSummary> = emptyList()
    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = null
    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState = ReaderState(book)
    override suspend fun saveActiveReader(book: BookSummary) = Unit
    override suspend fun clearActiveReader() = Unit
    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = null
    override suspend fun downloadBook(book: BookSummary): File = File("unused")
    override suspend fun deleteLocalCopy(book: BookSummary) = Unit
    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = Unit
    override suspend fun pendingProgressCount(): Int = 0
    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Success
    override suspend fun canReachServer(serverUrl: String): Boolean = false
    override suspend fun checkServer(serverUrl: String): ServerCheckResult = ServerCheckResult.Reachable
}
