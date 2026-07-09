package com.bookorbit.android

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `bootstrap shows cached offline browser snapshot when unauthenticated`() = runTest {
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

        val screen = coordinator.screen.value as AppScreen.Browser
        assertTrue(screen.browserState.isOfflineSnapshot)
        assertEquals(cachedState.books, screen.browserState.books)
        assertEquals(
            "Showing the last cached library snapshot. Sign in again when the server is available.",
            screen.browserState.message
        )
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
    var loadLibrariesResult: List<LibrarySummary> = emptyList(),
    var loadBooksResult: List<BookSummary> = emptyList(),
    var loadLibrariesError: Throwable? = null,
    var loadBooksError: Throwable? = null,
    var checkServerResult: ServerCheckResult = ServerCheckResult.Reachable,
    var pendingProgressCountResult: Int = 0,
    var syncPendingProgressResult: SyncAttemptResult = SyncAttemptResult.Success
) : BookOrbitDataSource {
    val restoreActiveReaderCalls = mutableListOf<Boolean>()
    val buildReaderLocalOnlyCalls = mutableListOf<Boolean>()
    val savedActiveReaders = mutableListOf<BookSummary>()
    var sessionStateRequested = false
    var selectedLibraryId: String? = null

    override suspend fun getServerUrl(): String? = serverUrl

    override suspend fun setServerUrl(serverUrl: String) {
        this.serverUrl = serverUrl
    }

    override suspend fun clearServer() {
        serverUrl = null
        selectedLibraryId = null
    }

    override suspend fun clearSession() = Unit

    override suspend fun getSelectedLibraryId(): String? = selectedLibraryId

    override suspend fun setSelectedLibraryId(libraryId: String) {
        selectedLibraryId = libraryId
    }

    override suspend fun getSessionState(): SessionState {
        sessionStateRequested = true
        return sessionState
    }

    override suspend fun loadLibraries(): List<LibrarySummary> {
        loadLibrariesError?.let { throw it }
        return loadLibrariesResult
    }

    override suspend fun loadBooks(libraryId: String): List<BookSummary> {
        loadBooksError?.let { throw it }
        return loadBooksResult
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

    override suspend fun clearActiveReader() = Unit

    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? {
        restoreActiveReaderCalls += localOnly
        return if (localOnly) restoreActiveReaderLocalOnlyResult else restoreActiveReaderResult
    }

    override suspend fun downloadBook(book: BookSummary): File = error("not needed")

    override suspend fun deleteLocalCopy(book: BookSummary) = Unit

    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = Unit

    override suspend fun pendingProgressCount(): Int = pendingProgressCountResult

    override suspend fun syncPendingProgress(): SyncAttemptResult = syncPendingProgressResult

    override suspend fun canReachServer(serverUrl: String): Boolean = checkServerResult == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = checkServerResult
}
