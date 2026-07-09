package com.bookorbit.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class AppCoordinator(private val repository: BookOrbitRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()
    private var lastBrowserState: BrowserState? = null
    private val activeDownloads = mutableMapOf<String, Job>()
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

            repository.restoreActiveReaderState(localOnly = true)?.let { readerState ->
                _screen.value = AppScreen.Reader(readerState)
                return@launch
            }

            if (repository.isAuthenticated()) {
                repository.restoreActiveReaderState()?.let { readerState ->
                    _screen.value = AppScreen.Reader(readerState)
                    return@launch
                }
                loadBrowser()
            } else {
                val cached = repository.loadCachedBrowserState()
                if (cached != null) {
                    showBrowser(
                        cached.copy(
                            isOfflineSnapshot = true,
                            message = "Showing the last cached library snapshot. Sign in again when the server is available."
                        )
                    )
                } else {
                    _screen.value = AppScreen.Login(serverUrl = serverUrl, message = "Sign in to access your libraries.")
                }
            }
        }
    }

    fun saveServer(serverUrl: String) {
        scope.launch {
            when (repository.checkServer(serverUrl)) {
                ServerCheckResult.Reachable -> Unit
                ServerCheckResult.MalformedUrl -> {
                    _screen.value = AppScreen.ServerSetup(
                        message = "Enter a valid server URL."
                    )
                    return@launch
                }
                ServerCheckResult.UnreachableHost -> {
                    _screen.value = AppScreen.ServerSetup(
                        message = "The server host could not be resolved."
                    )
                    return@launch
                }
                ServerCheckResult.Timeout -> {
                    _screen.value = AppScreen.ServerSetup(
                        message = "The server took too long to respond. Retry or check the network."
                    )
                    return@launch
                }
                ServerCheckResult.TlsFailure -> {
                    _screen.value = AppScreen.ServerSetup(
                        message = "The server TLS certificate could not be validated."
                    )
                    return@launch
                }
                ServerCheckResult.HttpFailure -> {
                    _screen.value = AppScreen.ServerSetup(
                        message = "The server responded, but the base URL did not open correctly."
                    )
                    return@launch
                }
                ServerCheckResult.NetworkFailure -> {
                    _screen.value = AppScreen.ServerSetup(
                        message = "Unable to reach that server. Check the URL and try again."
                    )
                    return@launch
                }
            }
            repository.setServerUrl(serverUrl)
            _screen.value = AppScreen.Login(serverUrl = serverUrl, message = "Connect to the server and complete sign in.")
        }
    }

    fun clearServer() {
        scope.launch {
            activeDownloads.values.forEach { it.cancel() }
            activeDownloads.clear()
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
                val cached = repository.loadCachedBrowserState()
                if (cached != null) {
                    showBrowser(
                        cached.copy(
                            isOfflineSnapshot = true,
                            message = "Showing the last cached library snapshot while waiting for an authenticated session."
                        )
                    )
                } else {
                    _screen.value = AppScreen.Login(serverUrl = serverUrl, message = "Waiting for an authenticated session.")
                }
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
                val pendingProgressCount = repository.pendingProgressCount()
                showBrowser(
                    BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = selectedLibrary,
                        books = books,
                        isRefreshing = false,
                        isLoadingLibraries = false,
                        isLoadingBooks = false,
                        debugPendingProgressCount = pendingProgressCount,
                        isOfflineSnapshot = false
                    )
                )
                if (selectedLibrary != null) {
                    repository.setSelectedLibraryId(selectedLibrary)
                }
                repository.syncPendingProgress()
                val refreshed = lastBrowserState ?: return@runCatching
                showBrowser(
                    refreshed.copy(
                        debugPendingProgressCount = repository.pendingProgressCount()
                    )
                )
            }.onFailure { error ->
                val cached = repository.loadCachedBrowserState()
                if (cached != null) {
                    showBrowser(
                        cached.copy(
                            isOfflineSnapshot = true,
                            message = "Showing the last cached library snapshot. ${userMessage(error, "Refresh failed.")}"
                        )
                    )
                } else if (previous != null) {
                    showBrowser(
                        previous.copy(
                            isRefreshing = false,
                            isLoadingLibraries = false,
                            isLoadingBooks = false,
                            message = userMessage(error, "Unable to refresh libraries.")
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
                val pendingProgressCount = repository.pendingProgressCount()
                showBrowser(
                    BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = libraryId,
                        books = books,
                        isRefreshing = false,
                        isLoadingLibraries = false,
                        isLoadingBooks = false,
                        debugPendingProgressCount = pendingProgressCount,
                        isOfflineSnapshot = false
                    )
                )
            }.onFailure { error ->
                val cachedForLibrary = repository.loadCachedBrowserState(libraryId)
                showBrowser(
                    (cachedForLibrary ?: loadingState ?: BrowserState(
                        serverUrl = serverUrl,
                        libraries = emptyList(),
                        selectedLibraryId = libraryId,
                        books = emptyList()
                    )).copy(
                        selectedLibraryId = libraryId,
                        isLoadingBooks = false,
                        isOfflineSnapshot = cachedForLibrary != null,
                        message = if (cachedForLibrary != null) {
                            "Showing cached books for this library. ${userMessage(error, "Unable to load the selected library.")}"
                        } else {
                            userMessage(error, "Unable to load the selected library.")
                        }
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
                repository.saveActiveReader(readerState.book)
                _screen.value = AppScreen.Reader(readerState)
            }.onFailure { error ->
                val fallback = lastBrowserState
                if (fallback != null) {
                    showBrowser(
                        fallback.copy(
                            message = userMessage(error, "Unable to open ${book.title}.")
                        )
                    )
                } else {
                    loadBrowser()
                }
            }
        }
    }

    fun downloadBook(book: BookSummary) {
        val fileId = book.fileId ?: run {
            showBrowserMessage("This title cannot be downloaded because it does not expose a file.")
            return
        }
        if (activeDownloads.containsKey(fileId)) {
            return
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            updateDownloadState(
                fileId = fileId,
                isDownloading = true,
                failed = false,
                message = null
            )
            val result = runCatching {
                repository.downloadBook(book)
            }
            activeDownloads.remove(fileId)
            result
                .onSuccess {
                    loadBrowser()
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        updateDownloadState(
                            fileId = fileId,
                            isDownloading = false,
                            failed = false,
                            message = "Download canceled."
                        )
                    } else {
                        updateDownloadState(
                            fileId = fileId,
                            isDownloading = false,
                            failed = true,
                            message = userMessage(error, "Download failed for ${book.title}.")
                        )
                    }
                }
        }
        activeDownloads[fileId] = job
        job.start()
    }

    fun cancelDownload(book: BookSummary) {
        val fileId = book.fileId ?: return
        activeDownloads.remove(fileId)?.cancel()
        updateDownloadState(
            fileId = fileId,
            isDownloading = false,
            failed = false,
            message = "Download canceled."
        )
    }

    fun deleteLocalCopy(book: BookSummary) {
        scope.launch {
            runCatching {
                repository.deleteLocalCopy(book)
            }.onSuccess {
                loadBrowser()
            }.onFailure { error ->
                showBrowserMessage(userMessage(error, "Unable to remove the local copy."))
            }
        }
    }

    private fun updateDownloadState(
        fileId: String,
        isDownloading: Boolean,
        failed: Boolean,
        message: String?
    ) {
        val current = lastBrowserState ?: return
        val downloading = current.downloadingFileIds.toMutableSet()
        val failedDownloads = current.failedDownloadFileIds.toMutableSet()
        if (isDownloading) {
            downloading += fileId
        } else {
            downloading -= fileId
        }
        if (failed) {
            failedDownloads += fileId
        } else {
            failedDownloads -= fileId
        }
        showBrowser(
            current.copy(
                downloadingFileIds = downloading,
                failedDownloadFileIds = failedDownloads,
                message = message
            )
        )
    }

    private fun showBrowserMessage(message: String) {
        val current = lastBrowserState ?: return
        showBrowser(current.copy(message = message))
    }

    private fun userMessage(error: Throwable, fallback: String): String {
        return when (error) {
            is AuthenticationRequiredException -> "Your session expired. Sign in again."
            is UserFacingException -> error.message ?: fallback
            is HttpRequestException -> when {
                error.code == 404 -> "The server could not find the requested content."
                error.code == 429 -> "The server is rate limiting requests. Try again shortly."
                error.code in 500..599 -> "The server failed while trying to ${error.action}."
                else -> "The server could not ${error.action}."
            }
            is UnknownHostException -> "The server host could not be resolved."
            is SocketTimeoutException -> "The server took too long to respond."
            is SSLException -> "The server's TLS configuration could not be validated."
            is IOException -> "A network error interrupted the request."
            else -> fallback
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
            repository.saveActiveReader(
                book.copy(
                    progressPositionMs = position.takeIf { it > 0L } ?: book.progressPositionMs,
                    progressPageIndex = pageIndex.takeIf { it > 0 } ?: book.progressPageIndex,
                    progressPercent = progressPercent ?: book.progressPercent
                )
            )
            if (shouldQueueProgress(progress, queuedProgressByTarget[key])) {
                queueProgress(key, progress)
            }
        }
    }

    fun closeReader() {
        scope.launch {
            flushCurrentReaderProgress()
            repository.clearActiveReader()
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
