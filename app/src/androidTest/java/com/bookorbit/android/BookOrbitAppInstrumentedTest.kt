package com.bookorbit.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
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
        composeRule.onNodeWithText("Read").assertIsEnabled()
        composeRule.onNodeWithText("Download").assertIsEnabled()
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
        composeRule.onNodeWithText("Read").assertIsEnabled()
        composeRule.onNodeWithText("Preview").assertIsEnabled()
        composeRule.onNodeWithText("Download").assertIsEnabled()
        composeRule.onNodeWithText("Genres").assertIsDisplayed()
        composeRule.onNodeWithText("Science fiction").assertIsDisplayed()
        composeRule.onNodeWithText("Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Space opera").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open full-screen cover for Orbit Rising").performClick()
        composeRule.onNodeWithContentDescription("Full-screen cover for Orbit Rising. Tap to close").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Full-screen cover for Orbit Rising. Tap to close").performClick()
        composeRule.onAllNodesWithContentDescription("Full-screen cover for Orbit Rising. Tap to close").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Open series Orbit Saga").performClick()
        composeRule.onNodeWithText("Orbit Saga").assertIsDisplayed()
        composeRule.onNodeWithText("Orbit Rising").assertIsDisplayed()
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
        composeRule.onAllNodesWithText("Load more").assertCountEquals(0)
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

}

private class InstrumentedFakeDataSource : BookOrbitDataSource {
    var clearServerCalls = 0
    var loadLibrariesCalls = 0
    val savedServerUrls = mutableListOf<String>()
    val markedReadBooks = mutableListOf<BookSummary>()
    val resetReadingStateBooks = mutableListOf<BookSummary>()
    var loadBooksResult: List<BookSummary> = emptyList()
    var localBooksResult: List<BookSummary> = emptyList()
    var searchBooksResult: List<BookSummary> = emptyList()
    var bookDetailResult: BookDetailInfo? = null
    val searchQueries = mutableListOf<String>()
    val libraryPageResults = mutableMapOf<Int, LibraryBooksPage>()
    val seriesCatalogPages = mutableMapOf<Int, SeriesCatalogPage>()

    override suspend fun getServerUrl(): String? = null
    override suspend fun setServerUrl(serverUrl: String) {
        savedServerUrls += serverUrl
    }
    override suspend fun clearServer() {
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
    override suspend fun downloadBook(book: BookSummary): File = File("unused")
    override suspend fun deleteLocalCopy(book: BookSummary) = Unit
    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = Unit
    override suspend fun pendingProgressCount(): Int = 0
    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Success
    override suspend fun canReachServer(serverUrl: String): Boolean = false
    override suspend fun checkServer(serverUrl: String): ServerCheckResult = ServerCheckResult.Reachable
}
