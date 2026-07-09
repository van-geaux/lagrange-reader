package com.bookorbit.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppCoordinator(private val repository: BookOrbitRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()
    private var lastBrowserState: BrowserState? = null
    private val latestProgressByTarget = mutableMapOf<BookProgressKey, PendingProgress>()
    private val queuedProgressByTarget = mutableMapOf<BookProgressKey, PendingProgress>()

    fun bootstrap() {
        scope.launch {
            _screen.value = AppScreen.Loading
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isNullOrBlank()) {
                _screen.value = AppScreen.ServerSetup()
                return@launch
            }

            if (repository.isAuthenticated()) {
                loadBrowser()
            } else {
                _screen.value = AppScreen.Login(serverUrl = serverUrl, message = "Sign in to access your libraries.")
            }
        }
    }

    fun saveServer(serverUrl: String) {
        scope.launch {
            if (!repository.canReachServer(serverUrl)) {
                _screen.value = AppScreen.ServerSetup(
                    message = "Unable to reach that server. Check the URL and try again."
                )
                return@launch
            }
            repository.setServerUrl(serverUrl)
            _screen.value = AppScreen.Login(serverUrl = serverUrl, message = "Connect to the server and complete sign in.")
        }
    }

    fun clearServer() {
        scope.launch {
            latestProgressByTarget.clear()
            queuedProgressByTarget.clear()
            repository.clearServer()
            _screen.value = AppScreen.ServerSetup()
        }
    }

    fun refreshLoginState() {
        scope.launch {
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isNullOrBlank()) {
                _screen.value = AppScreen.ServerSetup()
                return@launch
            }
            if (repository.isAuthenticated()) {
                loadBrowser()
            } else {
                _screen.value = AppScreen.Login(serverUrl = serverUrl, message = "Waiting for an authenticated session.")
            }
        }
    }

    fun loadBrowser() {
        scope.launch {
            val serverUrl = repository.getServerUrl().orEmpty()
            val previous = lastBrowserState
            if (previous != null) {
                showBrowser(
                    previous.copy(
                        isRefreshing = true,
                        isLoadingLibraries = true,
                        isLoadingBooks = true,
                        message = null
                    )
                )
            }
            runCatching {
                val libraries = repository.loadLibraries()
                val selectedLibrary = repository.getSelectedLibraryId() ?: libraries.firstOrNull()?.id
                val books = selectedLibrary?.let { repository.loadBooks(it) }.orEmpty()
                showBrowser(
                    BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = selectedLibrary,
                        books = books,
                        isRefreshing = false,
                        isLoadingLibraries = false,
                        isLoadingBooks = false
                    )
                )
                if (selectedLibrary != null) {
                    repository.setSelectedLibraryId(selectedLibrary)
                }
                repository.syncPendingProgress()
            }.onFailure { error ->
                if (previous != null) {
                    showBrowser(
                        previous.copy(
                            isRefreshing = false,
                            isLoadingLibraries = false,
                            isLoadingBooks = false,
                            message = error.message ?: "Unable to refresh libraries."
                        )
                    )
                } else {
                    _screen.value = AppScreen.Login(
                        serverUrl = serverUrl,
                        message = "Session expired or the server is unavailable. Sign in again."
                    )
                }
            }
        }
    }

    fun selectLibrary(libraryId: String) {
        scope.launch {
            val serverUrl = repository.getServerUrl().orEmpty()
            val currentBrowser = _screen.value as? AppScreen.Browser
            val loadingState = currentBrowser?.browserState?.copy(
                selectedLibraryId = libraryId,
                books = emptyList(),
                isRefreshing = false,
                isLoadingLibraries = false,
                isLoadingBooks = true,
                message = null
            )
            if (loadingState != null) {
                showBrowser(loadingState)
            }
            runCatching {
                repository.setSelectedLibraryId(libraryId)
                val libraries = currentBrowser?.browserState?.libraries ?: repository.loadLibraries()
                val books = repository.loadBooks(libraryId)
                showBrowser(
                    BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = libraryId,
                        books = books,
                        isRefreshing = false,
                        isLoadingLibraries = false,
                        isLoadingBooks = false
                    )
                )
            }.onFailure { error ->
                showBrowser(
                    (loadingState ?: BrowserState(
                        serverUrl = serverUrl,
                        libraries = emptyList(),
                        selectedLibraryId = libraryId,
                        books = emptyList()
                    )).copy(
                        isLoadingBooks = false,
                        message = error.message ?: "Unable to load the selected library."
                    )
                )
            }
        }
    }

    fun openBook(book: BookSummary) {
        scope.launch {
            _screen.value = AppScreen.ReaderLoading(book)
            runCatching {
                val readerState = repository.buildReaderState(book)
                _screen.value = AppScreen.Reader(readerState)
            }.onFailure { error ->
                val fallback = lastBrowserState
                if (fallback != null) {
                    showBrowser(fallback.copy(message = error.message ?: "Unable to open ${book.title}."))
                } else {
                    loadBrowser()
                }
            }
        }
    }

    fun downloadBook(book: BookSummary) {
        scope.launch {
            runCatching {
                repository.downloadBook(book)
            }
            loadBrowser()
        }
    }

    fun onProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) {
        scope.launch {
            val key = book.progressKey()
            val progress = PendingProgress(
                book = book,
                position = position,
                pageIndex = pageIndex,
                progressPercent = progressPercent,
                observedAtMillis = System.currentTimeMillis()
            )
            latestProgressByTarget[key] = progress
            if (shouldQueueProgress(progress, queuedProgressByTarget[key])) {
                queueProgress(key, progress)
            }
        }
    }

    fun closeReader() {
        scope.launch {
            flushCurrentReaderProgress()
            loadBrowser()
        }
    }

    private fun showBrowser(state: BrowserState) {
        lastBrowserState = state
        _screen.value = AppScreen.Browser(browserState = state)
    }

    private suspend fun flushCurrentReaderProgress() {
        val reader = _screen.value as? AppScreen.Reader ?: return
        val key = reader.readerState.book.progressKey()
        val progress = latestProgressByTarget[key] ?: return
        if (progress.isMeaningfullyDifferentFrom(queuedProgressByTarget[key])) {
            queueProgress(key, progress)
        }
    }

    private suspend fun queueProgress(key: BookProgressKey, progress: PendingProgress) {
        repository.queueProgress(
            book = progress.book,
            position = progress.position,
            pageIndex = progress.pageIndex,
            progressPercent = progress.progressPercent
        )
        queuedProgressByTarget[key] = progress
    }

    private fun shouldQueueProgress(progress: PendingProgress, lastQueued: PendingProgress?): Boolean {
        if (lastQueued == null) {
            return true
        }
        if (!progress.isMeaningfullyDifferentFrom(lastQueued)) {
            return false
        }
        if (progress.book.mediaKind != MediaKind.AUDIO) {
            return true
        }
        val elapsedMillis = progress.observedAtMillis - lastQueued.observedAtMillis
        val positionDeltaMillis = kotlin.math.abs(progress.position - lastQueued.position)
        return elapsedMillis >= MIN_AUDIO_PROGRESS_QUEUE_INTERVAL_MS ||
            positionDeltaMillis >= MIN_AUDIO_POSITION_DELTA_MS ||
            progress.percentDeltaFrom(lastQueued) >= MIN_PERCENT_DELTA
    }

    private fun PendingProgress.isMeaningfullyDifferentFrom(other: PendingProgress?): Boolean {
        other ?: return true
        return pageIndex != other.pageIndex ||
            kotlin.math.abs(position - other.position) >= MIN_POSITION_DELTA_MS ||
            percentDeltaFrom(other) >= MIN_PERCENT_DELTA
    }

    private fun PendingProgress.percentDeltaFrom(other: PendingProgress): Float {
        val current = progressPercent ?: return 0f
        val previous = other.progressPercent ?: return 0f
        return kotlin.math.abs(normalizeProgressPercent(current) - normalizeProgressPercent(previous))
    }

    private fun normalizeProgressPercent(value: Float): Float {
        return if (value in 0f..1f) value * 100f else value
    }

    private fun BookSummary.progressKey(): BookProgressKey {
        return BookProgressKey(
            bookId = id,
            fileId = fileId,
            mediaKind = mediaKind
        )
    }

    private data class BookProgressKey(
        val bookId: String,
        val fileId: String?,
        val mediaKind: MediaKind
    )

    private data class PendingProgress(
        val book: BookSummary,
        val position: Long,
        val pageIndex: Int,
        val progressPercent: Float?,
        val observedAtMillis: Long
    )

    private companion object {
        const val MIN_AUDIO_PROGRESS_QUEUE_INTERVAL_MS = 15_000L
        const val MIN_AUDIO_POSITION_DELTA_MS = 15_000L
        const val MIN_POSITION_DELTA_MS = 1_000L
        const val MIN_PERCENT_DELTA = 1f
    }
}
