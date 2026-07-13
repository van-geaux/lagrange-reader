package com.bookorbit.android

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppCoordinatorTest {
    private val serverUrl = "https://books.example.test"
    private val library = LibrarySummary(id = "lib-1", name = "Main")
    private val book = BookSummary(
        libraryId = library.id,
        id = "book-1",
        fileId = "file-1",
        title = "Sample Book",
        mediaKind = MediaKind.EPUB
    )

    @Test
    fun `bootstrap shows server setup when no server is saved`() = runTest {
        val repository = FakeBookOrbitDataSource(serverUrl = null)
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()

        assertEquals(AppScreen.ServerSetup(), coordinator.screen.value)
    }

    @Test
    fun `bootstrap prefers local only active reader restore before session checks`() = runTest {
        val readerState = ReaderState(book = book, localFile = File("offline.epub"))
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            restoreActiveReaderLocalOnlyResult = readerState
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()

        assertEquals(AppScreen.Reader(readerState), coordinator.screen.value)
        assertEquals(listOf(true), repository.restoreActiveReaderCalls)
        assertFalse(repository.sessionStateRequested)
    }

    @Test
    fun `bootstrap restores authenticated reader state after relaunch when offline only restore misses`() = runTest {
        val readerState = ReaderState(book = book, streamUrl = "$serverUrl/stream")
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Authenticated,
            restoreActiveReaderResult = readerState
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()

        assertEquals(AppScreen.Reader(readerState), coordinator.screen.value)
        assertEquals(listOf(true, false), repository.restoreActiveReaderCalls)
    }

    @Test
    fun `bootstrap loads browser for authenticated session when no reader state can be restored`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Authenticated
        ).apply {
            loadLibrariesResult = listOf(library)
            loadBooksResult = listOf(book)
        }
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Browser
        assertEquals(listOf(true, false), repository.restoreActiveReaderCalls)
        assertEquals(listOf(library), screen.browserState.libraries)
        assertEquals(library.id, screen.browserState.selectedLibraryId)
        assertEquals(listOf(book), screen.browserState.books)
        assertTrue(screen.browserState.isCatalogComplete)
        assertEquals(listOf(listOf(book)), repository.refreshFirstPages.map { it?.items })
    }

    @Test
    fun `load browser shows cached catalog before reconciling to the complete server result`() = runTest {
        val cachedBook = book.copy(title = "Cached title")
        val refreshedBook = book.copy(title = "Server title")
        val refreshGate = CompletableDeferred<Unit>()
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            loadLibrariesResult = listOf(library),
            cachedLibraryCatalog = LibraryBooksPage(
                items = listOf(cachedBook),
                total = 1,
                page = 0,
                size = 50,
                isComplete = true
            ),
            refreshLibraryCatalogResult = LibraryBooksPage(
                items = listOf(refreshedBook),
                total = 1,
                page = 0,
                size = 50,
                isComplete = true
            ),
            refreshLibraryCatalogGate = refreshGate
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.loadBrowser()
        runCurrent()

        val cachedScreen = coordinator.screen.value as AppScreen.Browser
        assertEquals(listOf(cachedBook), cachedScreen.browserState.books)
        assertTrue(cachedScreen.browserState.isCatalogComplete)
        assertTrue(cachedScreen.browserState.isCatalogSyncing)

        refreshGate.complete(Unit)
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Browser
        assertEquals(listOf(refreshedBook), screen.browserState.books)
        assertTrue(screen.browserState.isCatalogComplete)
        assertFalse(screen.browserState.isCatalogSyncing)
        assertEquals(listOf(null), repository.refreshFirstPages)
    }

    @Test
    fun `failed reconciliation keeps the complete cached catalog usable`() = runTest {
        val cachedBook = book.copy(title = "Cached title")
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            loadLibrariesResult = listOf(library),
            cachedLibraryCatalog = LibraryBooksPage(
                items = listOf(cachedBook),
                total = 1,
                page = 0,
                size = 50,
                isComplete = true
            ),
            refreshLibraryCatalogError = java.io.IOException("offline")
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.loadBrowser()
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Browser
        assertEquals(listOf(cachedBook), screen.browserState.books)
        assertTrue(screen.browserState.isCatalogComplete)
        assertFalse(screen.browserState.isCatalogSyncing)
        assertFalse(screen.browserState.isRefreshing)
        assertTrue(screen.browserState.message.orEmpty().contains("network error"))
    }

    @Test
    fun `bootstrap opens login instead of cached browser when server rejects saved session`() = runTest {
        val cachedState = BrowserState(
            serverUrl = serverUrl,
            libraries = listOf(library),
            selectedLibraryId = library.id,
            books = listOf(book)
        )
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Unauthenticated,
            cachedBrowserState = cachedState
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Login
        assertEquals(serverUrl, screen.serverUrl)
        assertTrue(screen.message.orEmpty().contains("no longer authenticated"))
    }

    @Test
    fun `refresh login resumes opening the requested book after authentication recovers`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            buildReaderError = AuthenticationRequiredException()
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.openBook(book)
        advanceUntilIdle()

        val loginScreen = coordinator.screen.value as AppScreen.Login
        assertTrue(loginScreen.message.orEmpty().contains("reopen ${book.title}"))

        repository.buildReaderError = null
        repository.buildReaderResult = ReaderState(book = book, streamUrl = "$serverUrl/stream")
        repository.sessionState = SessionState.Authenticated

        coordinator.refreshLoginState()
        advanceUntilIdle()

        val readerScreen = coordinator.screen.value as AppScreen.Reader
        assertEquals(book, readerScreen.readerState.book)
        assertEquals(listOf(false, false), repository.buildReaderLocalOnlyCalls)
        assertEquals(listOf(book), repository.savedActiveReaders)
    }

    @Test
    fun `load browser falls back to cached snapshot on network failure`() = runTest {
        val cachedState = BrowserState(
            serverUrl = serverUrl,
            libraries = listOf(library),
            selectedLibraryId = library.id,
            books = listOf(book)
        )
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            cachedBrowserState = cachedState,
            loadLibrariesError = java.io.IOException("offline")
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.loadBrowser()
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Browser
        assertTrue(screen.browserState.isOfflineSnapshot)
        assertTrue(screen.browserState.message.orEmpty().contains("Showing the last cached library snapshot."))
        assertTrue(screen.browserState.message.orEmpty().contains("network error"))
    }

    @Test
    fun `background authenticated request sends the user to login when session expires`() = runTest {
        val repository = FakeBookOrbitDataSource().apply {
            searchBooksError = AuthenticationRequiredException()
        }
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))
        coordinator.bootstrapIntoBrowser(
            BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book)
            )
        )

        assertTrue(coordinator.searchBooks("sample").isEmpty())

        val login = coordinator.screen.value as AppScreen.Login
        assertEquals(serverUrl, login.serverUrl)
        assertTrue(login.message.orEmpty().contains("session expired"))
    }

    @Test
    fun `load browser shows recoverable empty browser state when initial load fails without cache`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            loadLibrariesError = java.io.IOException("offline")
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.loadBrowser()
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Browser
        assertEquals(serverUrl, screen.browserState.serverUrl)
        assertTrue(screen.browserState.libraries.isEmpty())
        assertTrue(screen.browserState.books.isEmpty())
        assertFalse(screen.browserState.isOfflineSnapshot)
        assertEquals("A network error interrupted the request.", screen.browserState.message)
    }

    @Test
    fun `load browser can recover from empty error state on retry`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            loadLibrariesError = java.io.IOException("offline")
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.loadBrowser()
        advanceUntilIdle()

        repository.loadLibrariesError = null
        repository.loadLibrariesResult = listOf(library)
        repository.loadBooksResult = listOf(book)

        coordinator.loadBrowser()
        advanceUntilIdle()

        val screen = coordinator.screen.value as AppScreen.Browser
        assertEquals(listOf(library), screen.browserState.libraries)
        assertEquals(library.id, screen.browserState.selectedLibraryId)
        assertEquals(listOf(book), screen.browserState.books)
        assertNull(screen.browserState.message)
    }

    @Test
    fun `save server surfaces url validation failures without persisting the server`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = null,
            checkServerResult = ServerCheckResult.Redirected
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.saveServer("https://redirected.example.test")
        advanceUntilIdle()

        assertEquals(
            AppScreen.ServerSetup(
                serverUrl = "https://redirected.example.test",
                message = "The server redirected this URL. Enter the final base URL directly."
            ),
            coordinator.screen.value
        )
        assertNull(repository.serverUrl)
    }

    @Test
    fun `select library resumes after authentication recovers`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            loadBooksError = AuthenticationRequiredException()
        ).apply {
            loadLibrariesResult = listOf(library)
            loadBooksResult = listOf(book)
        }
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.selectLibrary(library.id)
        advanceUntilIdle()

        val loginScreen = coordinator.screen.value as AppScreen.Login
        assertTrue(loginScreen.message.orEmpty().contains("continue in this library"))

        repository.loadBooksError = null
        repository.sessionState = SessionState.Authenticated

        coordinator.refreshLoginState()
        advanceUntilIdle()

        val browserScreen = coordinator.screen.value as AppScreen.Browser
        assertEquals(library.id, browserScreen.browserState.selectedLibraryId)
        assertEquals(listOf(book), browserScreen.browserState.books)
    }

    @Test
    fun `download resumes after authentication recovers`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            downloadError = AuthenticationRequiredException()
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.downloadBook(book)
        advanceUntilIdle()

        val loginScreen = coordinator.screen.value as AppScreen.Login
        assertTrue(loginScreen.message.orEmpty().contains("continue downloading ${book.title}"))

        repository.downloadError = null
        repository.sessionState = SessionState.Authenticated

        coordinator.refreshLoginState()
        advanceUntilIdle()

        assertEquals(listOf(book, book), repository.downloadedBooks)
    }

    @Test
    fun `load browser redirects to login when browsing session expires`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            loadLibrariesError = AuthenticationRequiredException()
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.loadBrowser()
        advanceUntilIdle()

        val loginScreen = coordinator.screen.value as AppScreen.Login
        assertEquals(serverUrl, loginScreen.serverUrl)
        assertTrue(loginScreen.message.orEmpty().contains("continue browsing"))
    }

    @Test
    fun `offline browser session action routes back to sign in instead of clearing the server`() = runTest {
        val repository = FakeBookOrbitDataSource(serverUrl = serverUrl)
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrapIntoBrowser(
            BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book),
                isOfflineSnapshot = true
            )
        )

        coordinator.onBrowserSessionAction()
        advanceUntilIdle()

        val loginScreen = coordinator.screen.value as AppScreen.Login
        assertTrue(loginScreen.message.orEmpty().contains("Sign in again"))
        assertEquals(0, repository.clearSessionCalls)
    }

    @Test
    fun `explicit sign in from cached browser stays on login until authentication succeeds`() = runTest {
        val cachedState = BrowserState(
            serverUrl = serverUrl,
            libraries = listOf(library),
            selectedLibraryId = library.id,
            books = listOf(book),
            isOfflineSnapshot = true
        )
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Unauthenticated,
            cachedBrowserState = cachedState
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))
        coordinator.bootstrapIntoBrowser(cachedState)

        coordinator.beginSignIn()
        advanceUntilIdle()
        assertTrue(coordinator.screen.value is AppScreen.Login)

        coordinator.refreshLoginState()
        advanceUntilIdle()

        val login = coordinator.screen.value as AppScreen.Login
        assertTrue(login.message.orEmpty().contains("Waiting for an authenticated session"))
        assertEquals(0, repository.clearSessionCalls)
    }

    @Test
    fun `submit login verifies the session and resumes the browser`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Unauthenticated,
            loadLibrariesResult = listOf(library),
            loadBooksResult = listOf(book)
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()
        coordinator.submitLogin("reader", "secret")
        advanceUntilIdle()

        assertEquals(listOf("reader" to "secret"), repository.loginCalls)
        val browser = coordinator.screen.value as AppScreen.Browser
        assertEquals(listOf(library), browser.browserState.libraries)
        assertEquals(listOf(book), browser.browserState.books)
        assertTrue(repository.sessionStateRequested)
    }

    @Test
    fun `submit login keeps credentials failure on login`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Unauthenticated,
            loginError = InvalidCredentialsException()
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrap()
        advanceUntilIdle()
        coordinator.submitLogin("reader", "wrong")
        advanceUntilIdle()

        val login = coordinator.screen.value as AppScreen.Login
        assertTrue(login.message.orEmpty().contains("username or password"))
        assertFalse(login.isSubmitting)
    }

    @Test
    fun `preview starts at the beginning and does not persist reader state or progress`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            buildReaderResult = ReaderState(
                book = book,
                lastKnownPosition = 84_000L,
                pageIndex = 8,
                progressPercent = 42f
            )
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))
        coordinator.bootstrapIntoBrowser(
            BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book)
            )
        )

        coordinator.previewBook(book)
        advanceUntilIdle()

        val preview = coordinator.screen.value as AppScreen.Reader
        assertEquals(ReaderLaunchMode.PREVIEW, preview.readerState.launchMode)
        assertEquals(0L, preview.readerState.lastKnownPosition)
        assertEquals(0, preview.readerState.pageIndex)
        assertNull(preview.readerState.progressPercent)
        assertTrue(repository.savedActiveReaders.isEmpty())

        coordinator.onProgress(book, position = 90_000L, pageIndex = 9, progressPercent = 50f)
        advanceUntilIdle()
        coordinator.closeReader()
        advanceUntilIdle()

        assertTrue(repository.savedActiveReaders.isEmpty())
        assertTrue(repository.queuedProgress.isEmpty())
        assertEquals(0, repository.clearActiveReaderCalls)
    }

    @Test
    fun `reader progress is captured before close and synced before browser refresh`() = runTest {
        val repository = FakeBookOrbitDataSource()
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))
        coordinator.bootstrapIntoBrowser(
            BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book)
            )
        )
        coordinator.setScreenForTest(AppScreen.Reader(ReaderState(book = book)))

        coordinator.onProgress(book, position = 90_000L, pageIndex = 9, progressPercent = 50f)
        coordinator.closeReader()
        advanceUntilIdle()

        assertEquals(listOf(book), repository.queuedProgress)
        assertTrue(repository.syncPendingProgressCalls >= 1)
        assertEquals(1, repository.clearActiveReaderCalls)
    }

    @Test
    fun `successful sync lets refreshed server progress replace the local overlay`() = runTest {
        val serverBook = book.copy(progressPercent = 72f, progressLabel = "72%")
        val repository = FakeBookOrbitDataSource(
            loadLibrariesResult = listOf(library),
            loadBooksResult = listOf(serverBook)
        ).apply {
            selectedLibraryId = library.id
        }
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))
        coordinator.bootstrapIntoBrowser(
            BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book)
            )
        )
        coordinator.setScreenForTest(AppScreen.Reader(ReaderState(book = book)))

        coordinator.onProgress(book, position = 0L, pageIndex = 2, progressPercent = 25f)
        coordinator.closeReader()
        advanceUntilIdle()

        val refreshed = coordinator.screen.value as AppScreen.Browser
        assertEquals(72f, refreshed.browserState.books.single().progressPercent)
        assertEquals("72%", refreshed.browserState.books.single().progressLabel)
    }

    @Test
    fun `live browser sign out clears session and returns to login`() = runTest {
        val repository = FakeBookOrbitDataSource(
            serverUrl = serverUrl,
            sessionState = SessionState.Unauthenticated,
            cachedBrowserState = BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book)
            )
        )
        val coordinator = AppCoordinator(repository, StandardTestDispatcher(testScheduler))

        coordinator.bootstrapIntoBrowser(
            BrowserState(
                serverUrl = serverUrl,
                libraries = listOf(library),
                selectedLibraryId = library.id,
                books = listOf(book),
                isOfflineSnapshot = false
            )
        )

        coordinator.onBrowserSessionAction()
        advanceUntilIdle()

        val loginScreen = coordinator.screen.value as AppScreen.Login
        assertTrue(loginScreen.message.orEmpty().contains("Signed out"))
        assertEquals(1, repository.clearSessionCalls)

        coordinator.refreshLoginState()
        advanceUntilIdle()

        val refreshedLogin = coordinator.screen.value as AppScreen.Login
        assertTrue(refreshedLogin.message.orEmpty().contains("Waiting for an authenticated session."))
    }
}

private class FakeBookOrbitDataSource(
    var serverUrl: String? = "https://books.example.test",
    var sessionState: SessionState = SessionState.Authenticated,
    var cachedBrowserState: BrowserState? = null,
    var restoreActiveReaderLocalOnlyResult: ReaderState? = null,
    var restoreActiveReaderResult: ReaderState? = null,
    var buildReaderResult: ReaderState = ReaderState(
        book = BookSummary(
            libraryId = "lib-1",
            id = "book-1",
            fileId = "file-1",
            title = "Sample Book"
        )
    ),
    var buildReaderError: Throwable? = null,
    var downloadError: Throwable? = null,
    var loadLibrariesResult: List<LibrarySummary> = emptyList(),
    var loadBooksResult: List<BookSummary> = emptyList(),
    var cachedLibraryCatalog: LibraryBooksPage? = null,
    var refreshLibraryCatalogResult: LibraryBooksPage? = null,
    var refreshLibraryCatalogError: Throwable? = null,
    var refreshLibraryCatalogGate: CompletableDeferred<Unit>? = null,
    var loadLibrariesError: Throwable? = null,
    var loadBooksError: Throwable? = null,
    var searchBooksError: Throwable? = null,
    var loginError: Throwable? = null,
    var checkServerResult: ServerCheckResult = ServerCheckResult.Reachable,
    var pendingProgressCountResult: Int = 0,
    var syncPendingProgressResult: SyncAttemptResult = SyncAttemptResult.Success
) : BookOrbitDataSource {
    val restoreActiveReaderCalls = mutableListOf<Boolean>()
    val buildReaderLocalOnlyCalls = mutableListOf<Boolean>()
    val savedActiveReaders = mutableListOf<BookSummary>()
    val downloadedBooks = mutableListOf<BookSummary>()
    val queuedProgress = mutableListOf<BookSummary>()
    var clearSessionCalls = 0
    var clearActiveReaderCalls = 0
    var syncPendingProgressCalls = 0
    var sessionStateRequested = false
    var selectedLibraryId: String? = null
    val loginCalls = mutableListOf<Pair<String, String>>()
    val refreshFirstPages = mutableListOf<LibraryBooksPage?>()

    override suspend fun getServerUrl(): String? = serverUrl

    override suspend fun setServerUrl(serverUrl: String) {
        this.serverUrl = serverUrl
    }

    override suspend fun clearServer() {
        serverUrl = null
        selectedLibraryId = null
    }

    override suspend fun clearSession() {
        clearSessionCalls += 1
    }

    override suspend fun getSelectedLibraryId(): String? = selectedLibraryId

    override suspend fun setSelectedLibraryId(libraryId: String) {
        selectedLibraryId = libraryId
    }

    override suspend fun getSessionState(): SessionState {
        sessionStateRequested = true
        return sessionState
    }

    override suspend fun login(username: String, password: String) {
        loginCalls += username to password
        loginError?.let { throw it }
        sessionState = SessionState.Authenticated
    }

    override suspend fun loadLibraries(): List<LibrarySummary> {
        loadLibrariesError?.let { throw it }
        return loadLibrariesResult
    }

    override suspend fun loadBooks(libraryId: String): List<BookSummary> {
        loadBooksError?.let { throw it }
        return loadBooksResult
    }

    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = cachedLibraryCatalog

    override suspend fun refreshLibraryCatalog(
        libraryId: String,
        firstPage: LibraryBooksPage?
    ): LibraryBooksPage {
        refreshFirstPages += firstPage
        refreshLibraryCatalogGate?.await()
        refreshLibraryCatalogError?.let { throw it }
        return refreshLibraryCatalogResult
            ?: firstPage?.copy(isComplete = true)
            ?: cachedLibraryCatalog?.copy(isComplete = true)
            ?: LibraryBooksPage(isComplete = true)
    }

    override suspend fun searchBooks(query: String): List<BookSummary> {
        searchBooksError?.let { throw it }
        return emptyList()
    }

    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = cachedBrowserState

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState {
        buildReaderLocalOnlyCalls += localOnly
        buildReaderError?.let { throw it }
        return buildReaderResult
    }

    override suspend fun saveActiveReader(book: BookSummary) {
        savedActiveReaders += book
    }

    override suspend fun clearActiveReader() {
        clearActiveReaderCalls += 1
    }

    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? {
        restoreActiveReaderCalls += localOnly
        return if (localOnly) restoreActiveReaderLocalOnlyResult else restoreActiveReaderResult
    }

    override suspend fun downloadBook(book: BookSummary): File {
        downloadedBooks += book
        downloadError?.let { throw it }
        return File("downloaded.bin")
    }

    override suspend fun deleteLocalCopy(book: BookSummary) = Unit

    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) {
        queuedProgress += book
    }

    override suspend fun pendingProgressCount(): Int = pendingProgressCountResult

    override suspend fun syncPendingProgress(): SyncAttemptResult {
        syncPendingProgressCalls += 1
        return syncPendingProgressResult
    }

    override suspend fun canReachServer(serverUrl: String): Boolean = checkServerResult == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = checkServerResult
}

private fun AppCoordinator.setScreenForTest(screen: AppScreen) {
    val field = AppCoordinator::class.java.getDeclaredField("_screen")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val state = field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<AppScreen>
    state.value = screen
}

private fun AppCoordinator.bootstrapIntoBrowser(state: BrowserState) {
    val field = AppCoordinator::class.java.getDeclaredField("lastBrowserState")
    field.isAccessible = true
    field.set(this, state)

    val screenField = AppCoordinator::class.java.getDeclaredField("_screen")
    screenField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val screen = screenField.get(this) as kotlinx.coroutines.flow.MutableStateFlow<AppScreen>
    screen.value = AppScreen.Browser(state)
}
