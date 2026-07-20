package com.bookorbit.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun serverSetupAcceptsExplicitRemoteHttpUrl() {
        val dataSource = InstrumentedFakeDataSource()
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.ServerSetup(),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Server URL").performTextInput("http://books.example.test:8080")
        composeRule.onNodeWithText("Continue").performClick()

        composeRule.waitUntil { dataSource.savedServerUrls == listOf("http://books.example.test:8080") }
    }

    @Test
    fun startupLoadingStateUsesBrandedMarkInsteadOfSpinner() {
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Loading,
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Lagrange logo").assertIsDisplayed()
        composeRule.onNodeWithText("Loading your library…").assertIsDisplayed()
    }

    @Test
    fun offlineBrowserShowsProfileSignInAndLibraryPicker() {
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

        composeRule.onNodeWithContentDescription("User profile").performClick()
        composeRule.onNodeWithText("Sign in").assertIsEnabled()
        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Main").assertIsDisplayed()
        composeRule.onNodeWithText("Recommended").assertIsDisplayed()
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
    fun profileChangeServerPrefillsWarnsAndContinuesAboveLogout() {
        val currentServer = "https://books.example.test"
        val replacement = "https://replacement.example.test"
        val dataSource = InstrumentedFakeDataSource().apply { serverUrl = currentServer }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = currentServer,
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = emptyList()
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("User profile").performClick()
        val changeServerBounds = composeRule.onNodeWithText("Change server")
            .fetchSemanticsNode().boundsInRoot
        val logoutBounds = composeRule.onNodeWithText("Log out")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(changeServerBounds.top < logoutBounds.top)
        composeRule.onNodeWithText("Change server").performClick()
        composeRule.onNodeWithText(currentServer).assertIsDisplayed()

        composeRule.onNodeWithTag("submit-server-change").performClick()
        composeRule.onAllNodesWithText("Change server?").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(0, dataSource.clearServerCalls)
            assertTrue(dataSource.savedServerUrls.isEmpty())
        }
        composeRule.onNodeWithContentDescription("User profile").performClick()
        composeRule.onNodeWithText("Change server").performClick()

        composeRule.onNodeWithText("Server URL").performTextReplacement(replacement)
        composeRule.onNodeWithTag("submit-server-change").performClick()
        composeRule.onNodeWithText(
            "Changing to $replacement will log you out of the current server and cancel active downloads."
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(replacement).assertIsDisplayed()
        composeRule.onNodeWithTag("submit-server-change").performClick()
        composeRule.onNodeWithTag("confirm-server-change").performClick()

        composeRule.waitUntil {
            dataSource.clearServerCalls == 1 && dataSource.savedServerUrls == listOf(replacement)
        }
    }

    @Test
    fun profileAchievementsShowsUnlockedAndLockedCardsWithServerProgress() {
        val dataSource = InstrumentedFakeDataSource().apply {
            achievementsResult = AchievementCatalogue(
                items = listOf(
                    AchievementItem(
                        key = "first-finish",
                        category = "reading",
                        categoryLabel = "Reading",
                        name = "First Finish",
                        description = "Finish a book",
                        iconName = "book-open",
                        rarity = "common",
                        threshold = 1,
                        earned = true,
                        awardedAt = "2026-07-17T12:30:00.000Z",
                        currentProgress = 1
                    ),
                    AchievementItem(
                        key = "page-turner",
                        category = "reading",
                        categoryLabel = "Reading",
                        name = "Page Turner",
                        description = "Finish ten books",
                        iconName = "library",
                        rarity = "rare",
                        threshold = 10,
                        currentProgress = 4
                    )
                ),
                totalEarned = 1,
                totalAvailable = 2
            )
        }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = emptyList(),
                            selectedLibraryId = null,
                            books = emptyList()
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("User profile").performClick()
        composeRule.onNodeWithText("Achievements").performClick()
        composeRule.waitUntil { composeRule.onAllNodesWithText("First Finish").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("1 of 2 unlocked").assertIsDisplayed()
        composeRule.onNodeWithText("Unlocked").assertIsDisplayed()
        composeRule.onNodeWithText("First Finish").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("First Finish achievement symbol").assertIsDisplayed()
        composeRule.onNodeWithText("Page Turner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page Turner achievement symbol").assertIsDisplayed()
        composeRule.onNodeWithText("4 / 10").assertIsDisplayed()
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
        val dataSource = InstrumentedFakeDataSource().apply {
            loadBooksResult = listOf(book)
        }

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
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Recently added books").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Lagrange logo").assertIsDisplayed()
        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Main").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open library selector").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Refresh").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Open library selector").performClick()
        composeRule.onNodeWithContentDescription("Open library selector").performClick()
        composeRule.onNodeWithText("Browse").performClick()
        composeRule.onNodeWithContentDescription("Jump to T").assertIsDisplayed()
        composeRule.onNodeWithText("The Test Book").assertIsDisplayed()
        composeRule.onNodeWithText("Test Author").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("The Test Book").performClick()
        composeRule.onNodeWithContentDescription("Read").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Download").assertIsEnabled()
        composeRule.onNodeWithContentDescription("User profile").performClick()
        composeRule.onNodeWithText("Log out").assertIsEnabled()
    }

    @Test
    fun bookDetailsExposeCompactActionsCoverViewerMetadataAndSeriesNavigation() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-series",
            fileId = "file-series",
            title = "Orbit Rising",
            author = "Test Author",
            seriesId = "series-orbit",
            seriesName = "Orbit Saga",
            seriesIndex = 2.0,
            format = "epub",
            mediaKind = MediaKind.EPUB
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            loadBooksResult = listOf(book)
            bookDetailResult = BookDetailInfo(
                book = book,
                libraryName = "Main",
                publisher = "Test Press",
                publishedDate = "2026",
                language = "English",
                pageCount = 320,
                isbn13 = "9781234567890",
                genres = listOf("Science fiction"),
                tags = listOf("Space opera")
            )
        }

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
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Orbit Rising").performClick()
        composeRule.onNodeWithTag("book-detail-actions").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Read").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Preview").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Download").assertIsEnabled()
        composeRule.onNodeWithText("Read").assertIsDisplayed()
        composeRule.onNodeWithText("Preview").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Mark as read").assertIsDisplayed().performClick()
        composeRule.waitUntil { dataSource.markedReadBooks.any { it.id == book.id } }
        composeRule.onNodeWithText("Genres").assertIsDisplayed()
        composeRule.onNodeWithText("Science fiction").assertIsDisplayed()
        composeRule.onNodeWithText("Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Space opera").assertIsDisplayed().assertHasNoClickAction()

        composeRule.onNodeWithContentDescription("Open full-screen cover for Orbit Rising").performClick()
        composeRule.onNodeWithContentDescription("Full-screen cover for Orbit Rising. Tap anywhere to close").assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithContentDescription("Full-screen cover for Orbit Rising. Tap anywhere to close").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Filter Genres by Science fiction").performClick()
        composeRule.onNodeWithText("Books · Science fiction").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithContentDescription("Open series Orbit Saga").performClick()
        composeRule.onNodeWithText("Orbit Saga").assertIsDisplayed()
        composeRule.onNodeWithText("Orbit Rising").assertIsDisplayed()
    }

    @Test
    fun bookDetailsNavigateToSeriesNeighborsInIndexOrder() {
        fun seriesBook(id: String, title: String, index: Double) = BookSummary(
            libraryId = "lib-1",
            id = id,
            fileId = "file-$id",
            title = title,
            seriesId = "series-1",
            seriesName = "Test Series",
            seriesIndex = index,
            mediaKind = MediaKind.EPUB
        )
        val first = seriesBook("first", "First Book", 1.0)
        val current = seriesBook("current", "Current Book", 2.0)
        val next = seriesBook("next", "Next Book", 3.0)
        val dataSource = InstrumentedFakeDataSource().apply {
            seriesDetailResult = SeriesDetailInfo(
                id = "series-1",
                name = "Test Series",
                bookCount = 3,
                readCount = 0,
                books = listOf(next, first, current)
            )
        }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(current)
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Current Book").performClick()
        composeRule.onNodeWithTag("series-neighbor-navigation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Previous book in Test Series: #1 \u00B7 First Book")
            .assertIsEnabled()
        composeRule.onNodeWithContentDescription("Next book in Test Series: #3 \u00B7 Next Book")
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithContentDescription("Open full-screen cover for Next Book").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("No next book in Test Series").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Previous book in Test Series: #2 \u00B7 Current Book")
            .assertIsEnabled()
        composeRule.runOnIdle { assertEquals(1, dataSource.seriesDetailLoadCalls) }
    }

    @Test
    fun downloadedBookDetailsKeepDeleteLocalInMore() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-downloaded",
            fileId = "file-downloaded",
            title = "Downloaded Book",
            format = "epub",
            mediaKind = MediaKind.EPUB,
            localPath = "/downloads/downloaded.epub"
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            loadBooksResult = listOf(book)
            bookDetailResult = BookDetailInfo(book)
        }

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
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Downloaded Book").performClick()
        composeRule.onAllNodesWithText("Delete local").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("More book actions").performClick()
        composeRule.onNodeWithText("Delete local").assertIsDisplayed()
    }

    @Test
    fun outdatedDownloadedBookDetailsKeepUpdateAndDeleteInMore() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-outdated-download",
            fileId = "file-outdated-download",
            title = "Updated Book",
            format = "epub",
            mediaKind = MediaKind.EPUB,
            localPath = "/downloads/updated-book.epub",
            downloadedSourceUpdatedAtMillis = 100L,
            updatedAtMillis = 200L
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            loadBooksResult = listOf(book)
            bookDetailResult = BookDetailInfo(book)
        }

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
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Updated Book").performClick()
        composeRule.onAllNodesWithText("Update local").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("More book actions").performClick()
        composeRule.onNodeWithText("Update local").assertIsDisplayed()
        composeRule.onNodeWithText("Delete local").assertIsDisplayed()
        composeRule.onNodeWithText("Update local").performClick()
        composeRule.waitUntil { dataSource.downloadedBooks == listOf(book) }
    }

    @Test
    fun currentlyReadingCardExposesRemoveAction() {
        val dataSource = InstrumentedFakeDataSource()
        val current = BookSummary(
            libraryId = "lib-1",
            id = "book-current",
            fileId = "file-current",
            title = "Current Book",
            mediaKind = MediaKind.EPUB,
            progressPercent = 35f,
            lastReadAtMillis = 100L
        )

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(current)
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Currently reading").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("More options for Current Book")[0].performClick()
        composeRule.onNodeWithText("Mark as read").assertIsDisplayed()
        composeRule.onNodeWithText("Mark as unread").assertIsDisplayed()
        composeRule.onNodeWithText("Remove from Currently reading").assertIsDisplayed()

        composeRule.onNodeWithText("Mark as read").performClick()
        composeRule.waitUntil { dataSource.markedReadBooks == listOf(current) }

        composeRule.onAllNodesWithContentDescription("More options for Current Book")[0].performClick()
        composeRule.onNodeWithText("Mark as unread").performClick()
        composeRule.waitUntil { dataSource.resetReadingStateBooks == listOf(current) }

        composeRule.onAllNodesWithTag("book_card_${current.id}")[0].performTouchInput { longClick() }
        composeRule.onNodeWithText("Mark as read").assertIsDisplayed()
    }

    @Test
    fun homePullDownTriggersBrowserRefresh() {
        val dataSource = InstrumentedFakeDataSource()

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = emptyList()
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithTag("home_pull_to_refresh").performTouchInput { swipeDown() }

        composeRule.waitUntil { dataSource.loadLibrariesCalls == 1 }
    }

    @Test
    fun homeLocalShelfAggregatesServerAndLibraryShelfScopesDownloads() {
        val mainLocal = BookSummary(
            libraryId = "lib-1",
            id = "local-main",
            fileId = "file-main",
            title = "Main Local",
            localPath = "/local/main.epub",
            mediaKind = MediaKind.EPUB
        )
        val mangaLocal = BookSummary(
            libraryId = "lib-2",
            id = "local-manga",
            fileId = "file-manga",
            title = "Manga Local",
            localPath = "/local/manga.cbz",
            mediaKind = MediaKind.COMIC
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            localBooksResult = listOf(mainLocal, mangaLocal)
        }
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(
                                LibrarySummary(id = "lib-1", name = "Main"),
                                LibrarySummary(id = "lib-2", name = "Manga")
                            ),
                            selectedLibraryId = "lib-1",
                            books = listOf(mainLocal),
                            homeBooks = listOf(mainLocal, mangaLocal)
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Local books").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cover for Main Local").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cover for Manga Local").assertIsDisplayed()
        composeRule.onNodeWithText("See all").performClick()
        composeRule.onNodeWithContentDescription("Main Local").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Manga Local").assertIsDisplayed()

        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Local books").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cover for Main Local").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Cover for Manga Local").assertCountEquals(0)
    }

    @Test
    fun dismissibleMessageSupportsCloseButtonAndHorizontalSwipe() {
        val showCloseMessage = mutableStateOf(true)
        val showSwipeMessage = mutableStateOf(true)
        composeRule.setContent {
            BookOrbitTheme {
                Column {
                    if (showCloseMessage.value) {
                        OrbitMessage("Close this message", onDismiss = { showCloseMessage.value = false })
                    }
                    if (showSwipeMessage.value) {
                        OrbitMessage("Swipe this message", onDismiss = { showSwipeMessage.value = false })
                    }
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("Dismiss message")[0].performClick()
        composeRule.onAllNodesWithText("Close this message").assertCountEquals(0)
        composeRule.onNodeWithText("Swipe this message").performTouchInput { swipeLeft() }
        composeRule.waitUntil { !showSwipeMessage.value }
        composeRule.onAllNodesWithText("Swipe this message").assertCountEquals(0)
    }

    @Test
    fun homeSearchAndMoreActionsOpenNativeLayers() {
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = emptyList()
                        )
                    ),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Search your library").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onAllNodesWithText("a BookOrbit reader").assertCountEquals(0)
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Series").assertIsDisplayed()
        composeRule.onNodeWithText("Authors").assertIsDisplayed()
        composeRule.onNodeWithText("Local books").assertIsDisplayed()
        composeRule.onAllNodesWithText("Options").assertCountEquals(0)
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithContentDescription("User profile").performClick()
        composeRule.onNodeWithText("Options").assertIsDisplayed()
    }

    @Test
    fun optionsExposePersistableInterfaceControls() {
        val preferences = mutableStateOf(AppPreferences())
        composeRule.setContent {
            BookOrbitTheme(themeMode = preferences.value.themeMode) {
                OptionsScreen(
                    preferences = preferences.value,
                    onPreferencesChange = { preferences.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Interface").assertIsDisplayed()
        composeRule.onAllNodesWithText("Haptic feedback").assertCountEquals(0)
        composeRule.onNodeWithTag("options-lock-orientation").performClick()
        composeRule.runOnIdle { assertTrue(preferences.value.lockOrientation) }

        composeRule.onNodeWithTag("options-theme").performClick()
        composeRule.onNodeWithText("Follow system").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Charcoal").assertIsDisplayed()
        composeRule.onNodeWithText("Warm black").assertIsDisplayed()
        composeRule.onNodeWithText("OLED black").performClick()
        composeRule.runOnIdle { assertEquals(AppThemeMode.OLED_BLACK, preferences.value.themeMode) }

        composeRule.onNodeWithTag("options-opening-screen").performClick()
        composeRule.onNodeWithText("Local books").performClick()
        composeRule.runOnIdle {
            assertEquals(DefaultOpeningScreen.LOCAL_BOOKS, preferences.value.defaultOpeningScreen)
        }

        composeRule.onNodeWithTag("options-reduce-motion").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(preferences.value.reduceMotion) }
    }

    @Test
    fun optionsExposeDataPoliciesAndSafeCacheClearing() {
        val preferences = mutableStateOf(AppPreferences())
        var clearCount = 0
        composeRule.setContent {
            BookOrbitTheme {
                OptionsScreen(
                    preferences = preferences.value,
                    onPreferencesChange = { preferences.value = it },
                    storageUsageLoader = {
                        StorageUsage(downloadedBytes = 10L * 1024L, cacheBytes = 2L * 1024L)
                    },
                    onClearCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("options-cellular-downloads").performScrollTo().performClick()
        composeRule.onNodeWithText("Never").performClick()
        composeRule.runOnIdle {
            assertEquals(CellularDownloadPolicy.NEVER, preferences.value.cellularDownloadPolicy)
        }

        composeRule.onNodeWithTag("options-storage").performScrollTo()
        composeRule.onNodeWithText("Downloads 10 KB · Cache 2.0 KB").assertIsDisplayed()
        composeRule.onNodeWithTag("options-clear-cache").performClick()
        composeRule.onNodeWithText("Downloaded books are kept.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-clear-cache").performClick()
        composeRule.waitUntil { clearCount == 1 }

        composeRule.onNodeWithTag("options-background-refresh").performScrollTo().performClick()
        composeRule.onNodeWithText("Disabled").performClick()
        composeRule.runOnIdle {
            assertEquals(
                BackgroundRefreshNetworkPolicy.DISABLED,
                preferences.value.backgroundRefreshNetworkPolicy
            )
        }

        composeRule.onNodeWithTag("options-confirm-local-delete").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(false, preferences.value.confirmDeleteLocalCopy) }
    }

    @Test
    fun browserConfirmsBeforeDeletingDownloadedLocalCopy() {
        val downloadedBook = BookSummary(
            libraryId = "lib-1",
            id = "book-delete-confirm",
            fileId = "file-delete-confirm",
            title = "Keep My Download",
            mediaKind = MediaKind.EPUB,
            localPath = "/downloads/keep-my-download.epub"
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            bookDetailResult = BookDetailInfo(downloadedBook)
        }
        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(downloadedBook)
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main),
                    appPreferences = AppPreferences(confirmDeleteLocalCopy = true)
                )
            }
        }

        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Browse").performClick()
        composeRule.onNodeWithText("Keep My Download").performClick()
        composeRule.onNodeWithContentDescription("Delete local").performClick()
        composeRule.onNodeWithText("Delete local copy?").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(dataSource.deletedLocalBooks.isEmpty()) }
        composeRule.onNodeWithTag("confirm-delete-local-copy").performClick()
        composeRule.waitUntil { dataSource.deletedLocalBooks == listOf(downloadedBook) }
        composeRule.onNodeWithText("Download").assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete local").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("User profile").performClick()
        composeRule.onNodeWithText("Options").performClick()
        composeRule.onNodeWithText("Interface").assertIsDisplayed()
    }

    @Test
    fun searchResultListRowExposesBookActionsFromOverflowAndLongPress() {
        val searchBook = BookSummary(
            libraryId = "lib-1",
            id = "book-search",
            fileId = "file-search",
            title = "Search Result Book",
            mediaKind = MediaKind.EPUB
        )
        val dataSource = InstrumentedFakeDataSource().apply {
            searchBooksResult = listOf(searchBook)
        }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = emptyList()
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Search your library").performTextInput("Search Result")
        composeRule.waitUntil { dataSource.searchQueries == listOf("Search Result") }

        composeRule.onNodeWithText("Search Result Book").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options for Search Result Book").performClick()
        composeRule.onNodeWithText("Mark as read").performClick()
        composeRule.waitUntil { dataSource.markedReadBooks == listOf(searchBook) }

        composeRule.onNodeWithTag("search_result_book_${searchBook.id}").performTouchInput { longClick() }
        composeRule.onNodeWithText("Mark as unread").performClick()
        composeRule.waitUntil { dataSource.resetReadingStateBooks == listOf(searchBook) }
    }

    @Test
    fun seriesCatalogUsesJumpRailWithoutLoadMore() {
        val dataSource = InstrumentedFakeDataSource().apply {
            seriesCatalogPages[0] = SeriesCatalogPage(
                items = listOf(
                    SeriesSummary(id = "series-a", name = "Alpha Series"),
                    SeriesSummary(id = "series-z", name = "Zulu Series")
                ),
                total = 2,
                page = 0,
                size = 100
            )
        }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = emptyList()
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Series").performClick()

        composeRule.onNodeWithContentDescription("Jump to A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("B unavailable").assertIsDisplayed().assertHasNoClickAction()
        val seriesCardBounds = composeRule.onNodeWithTag("series_card_series-a")
            .fetchSemanticsNode().boundsInRoot
        val jumpRailBounds = composeRule.onNodeWithTag("catalog_jump_rail")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(seriesCardBounds.right <= jumpRailBounds.left)
        composeRule.onAllNodesWithText("Load more").assertCountEquals(0)
    }

    @Test
    fun libraryGridCardsEndBeforeTheVisibleJumpRail() {
        val book = BookSummary(
            libraryId = "lib-1",
            id = "book-alpha",
            fileId = "file-alpha",
            title = "Alpha Book",
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
                            books = listOf(book),
                            isCatalogComplete = true
                        )
                    ),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Browse").performClick()

        composeRule.onNodeWithContentDescription("Jump to A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("B unavailable").assertIsDisplayed().assertHasNoClickAction()

        val bookCardBounds = composeRule.onNodeWithContentDescription("Alpha Book")
            .fetchSemanticsNode().boundsInRoot
        val jumpRailBounds = composeRule.onNodeWithTag("catalog_jump_rail")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(bookCardBounds.right <= jumpRailBounds.left)
    }

    @Test
    fun collapsedLibrarySeriesCardShowsItsBookCount() {
        val books = listOf(
            BookSummary(
                libraryId = "lib-1",
                id = "book-1",
                fileId = "file-1",
                title = "Volume One",
                seriesId = "series-1",
                seriesName = "Test Series",
                seriesIndex = 1.0
            ),
            BookSummary(
                libraryId = "lib-1",
                id = "book-2",
                fileId = "file-2",
                title = "Volume Two",
                seriesId = "series-1",
                seriesName = "Test Series",
                seriesIndex = 2.0
            )
        )

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = books,
                            isCatalogComplete = true
                        )
                    ),
                    coordinator = AppCoordinator(InstrumentedFakeDataSource(), Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Browse").performClick()
        composeRule.onNodeWithContentDescription("Collapse series").performClick()

        composeRule.onNodeWithText("2 books").assertIsDisplayed()
    }

    @Test
    fun librarySeriesCanCollapseAndExpand() {
        val first = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Series Book One",
            seriesId = "series-1",
            seriesName = "Test Series",
            seriesIndex = 1.0,
            mediaKind = MediaKind.EPUB
        )
        val second = first.copy(id = "book-2", fileId = "file-2", title = "Series Book Two", seriesIndex = 2.0)
        val dataSource = InstrumentedFakeDataSource().apply { loadBooksResult = listOf(first, second) }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(first, second)
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Lagrange logo").assertIsDisplayed()
        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Main").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Lagrange logo").assertCountEquals(0)
        composeRule.onNodeWithText("Browse").performClick()
        composeRule.onNodeWithText("Collapse series").performClick()
        composeRule.onNodeWithText("Test Series").assertIsDisplayed()
        composeRule.onNodeWithText("Expand series").performClick()
        composeRule.onNodeWithText("Series Book Two").assertIsDisplayed()
    }

    @Test
    fun libraryBrowseCanLoadMorePages() {
        val first = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Page One",
            mediaKind = MediaKind.EPUB
        )
        val second = first.copy(id = "book-2", fileId = "file-2", title = "Page Two")
        val dataSource = InstrumentedFakeDataSource().apply {
            loadBooksResult = listOf(first)
            libraryPageResults[1] = LibraryBooksPage(
                items = listOf(second),
                total = 2,
                page = 1,
                size = 1
            )
        }

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Browser(
                        BrowserState(
                            serverUrl = "https://books.example.test",
                            libraries = listOf(LibrarySummary(id = "lib-1", name = "Main")),
                            selectedLibraryId = "lib-1",
                            books = listOf(first),
                            booksTotal = 2,
                            booksPage = 0,
                            booksPageSize = 1
                        )
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Browse").performClick()
        composeRule.onNodeWithText("Page Two").assertIsDisplayed()
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

        composeRule.onNodeWithText("Loading books...").assertIsDisplayed()
        composeRule.onNodeWithText("Libraries").performClick()
        composeRule.onNodeWithText("Recommended").assertIsDisplayed()
    }

    @Test
    fun descriptionsExpandAndCollapseFromTextTap() {
        val description = """
            This is a deliberately long description that should exceed four rendered lines on a phone-sized layout. It should expose the inline expand action only after measured overflow, expand when the description itself is tapped, and collapse again when tapped a second time.
        """.trimIndent()

        composeRule.setContent {
            BookOrbitTheme {
                ExpandableDescription(title = "Synopsis", body = description)
            }
        }

        composeRule.onNodeWithContentDescription("Expand Synopsis").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Synopsis description").performClick()
        composeRule.onNodeWithContentDescription("Collapse Synopsis").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Synopsis description").performClick()
        composeRule.onNodeWithContentDescription("Expand Synopsis").assertIsDisplayed()
    }

    @Test
    fun remoteComicUsesBookOrbitPageEndpoints() {
        val pagesUrl = "https://books.example.test/api/v1/cbz/files/42/pages"
        val pageBytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, Color.BLACK)
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
            output.toByteArray()
        }
        val dataSource = InstrumentedFakeDataSource().apply {
            catalogImageResults[pagesUrl] = "{\"pageCount\":2}".toByteArray()
            catalogImageResults["$pagesUrl/0"] = pageBytes
            catalogImageResults["$pagesUrl/1"] = pageBytes
        }
        val comic = BookSummary(
            libraryId = "lib-manga",
            id = "book-comic",
            fileId = "42",
            title = "Remote Comic",
            format = "cbr",
            mediaKind = MediaKind.COMIC
        )

        composeRule.setContent {
            BookOrbitTheme {
                BookOrbitApp(
                    screen = AppScreen.Reader(
                        ReaderState(book = comic, comicPagesUrl = pagesUrl)
                    ),
                    coordinator = AppCoordinator(dataSource, Dispatchers.Main)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Comic page 1 of 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next comic page").performClick()
        composeRule.onNodeWithContentDescription("Comic page 2 of 2").assertIsDisplayed()
        composeRule.waitUntil { "$pagesUrl/1" in dataSource.loadedCatalogImageUrls }

        composeRule.onNodeWithTag("comic_reader_surface").performTouchInput { swipeRight() }
        composeRule.onNodeWithContentDescription("Comic page 1 of 2").assertIsDisplayed()
        composeRule.onNodeWithTag("comic_reader_surface").performTouchInput { swipeLeft() }
        composeRule.onNodeWithContentDescription("Comic page 2 of 2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open comic reader options").performClick()
        composeRule.onNodeWithText("Reader options").assertIsDisplayed()
        composeRule.onNodeWithText("Continue reading").performClick()
        composeRule.onAllNodesWithText("Reader options").assertCountEquals(0)
    }

}

private class InstrumentedFakeDataSource : BookOrbitDataSource {
    var serverUrl: String? = null
    var clearServerCalls = 0
    var loadLibrariesCalls = 0
    val savedServerUrls = mutableListOf<String>()
    val markedReadBooks = mutableListOf<BookSummary>()
    val resetReadingStateBooks = mutableListOf<BookSummary>()
    val deletedLocalBooks = mutableListOf<BookSummary>()
    val downloadedBooks = mutableListOf<BookSummary>()
    var loadBooksResult: List<BookSummary> = emptyList()
    var localBooksResult: List<BookSummary> = emptyList()
    var searchBooksResult: List<BookSummary> = emptyList()
    var bookDetailResult: BookDetailInfo? = null
    var seriesDetailResult: SeriesDetailInfo? = null
    var seriesDetailLoadCalls = 0
    var achievementsResult: AchievementCatalogue = AchievementCatalogue(
        status = AchievementCatalogueStatus.UNSUPPORTED
    )
    val searchQueries = mutableListOf<String>()
    val libraryPageResults = mutableMapOf<Int, LibraryBooksPage>()
    val seriesCatalogPages = mutableMapOf<Int, SeriesCatalogPage>()
    val catalogImageResults = mutableMapOf<String, ByteArray>()
    val loadedCatalogImageUrls = mutableListOf<String>()

    override suspend fun getServerUrl(): String? = serverUrl
    override suspend fun setServerUrl(serverUrl: String) {
        this.serverUrl = serverUrl
        savedServerUrls += serverUrl
    }
    override suspend fun clearServer() {
        serverUrl = null
        clearServerCalls += 1
    }
    override suspend fun clearSession() = Unit
    override suspend fun getSelectedLibraryId(): String? = null
    override suspend fun setSelectedLibraryId(libraryId: String) = Unit
    override suspend fun getSessionState(): SessionState = SessionState.Unauthenticated
    override suspend fun login(username: String, password: String) = Unit
    override suspend fun loadLibraries(): List<LibrarySummary> {
        loadLibrariesCalls += 1
        return emptyList()
    }
    override suspend fun loadBooks(libraryId: String): List<BookSummary> = loadBooksResult
    override suspend fun searchBooks(query: String): List<BookSummary> {
        searchQueries += query
        return searchBooksResult
    }
    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? = bookDetailResult
    override suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? {
        seriesDetailLoadCalls += 1
        return seriesDetailResult
    }
    override suspend fun loadAchievements(): AchievementCatalogue = achievementsResult
    override suspend fun loadCatalogImage(url: String): ByteArray? {
        loadedCatalogImageUrls += url
        return catalogImageResults[url]
    }
    override suspend fun loadBooksPage(libraryId: String, page: Int): LibraryBooksPage {
        if (page == 0) return LibraryBooksPage(items = loadBooksResult, total = loadBooksResult.size, page = 0, size = loadBooksResult.size)
        return libraryPageResults[page] ?: LibraryBooksPage(page = page)
    }
    override suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
        seriesCatalogPages[page] ?: SeriesCatalogPage(page = page)
    override suspend fun loadLocalBooks(): List<BookSummary> = localBooksResult
    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = null
    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState = ReaderState(book)
    override suspend fun saveActiveReader(book: BookSummary) = Unit
    override suspend fun clearActiveReader() = Unit
    override suspend fun markBookAsRead(book: BookSummary) {
        markedReadBooks += book
    }
    override suspend fun resetBookReadingState(book: BookSummary) {
        resetReadingStateBooks += book
    }
    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = null
    override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit): File {
        downloadedBooks += book
        return File("unused")
    }
    override suspend fun deleteLocalCopy(book: BookSummary) {
        deletedLocalBooks += book
    }
    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = Unit
    override suspend fun pendingProgressCount(): Int = 0
    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Success
    override suspend fun canReachServer(serverUrl: String): Boolean = false
    override suspend fun checkServer(serverUrl: String): ServerCheckResult = ServerCheckResult.Reachable
}
