package com.bookorbit.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
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

class AppCoordinator(
    private val repository: BookOrbitDataSource,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    suspend fun searchBooks(query: String): List<BookSummary> = loadWithSessionRecovery(emptyList()) {
        repository.searchBooks(query)
    }

    suspend fun loadBookCover(book: BookSummary): ByteArray? = loadWithSessionRecovery(null) {
        repository.loadBookCover(book)
    }

    suspend fun loadLocalBooks(): List<BookSummary> = loadWithSessionRecovery(emptyList()) {
        repository.loadLocalBooks()
    }

    suspend fun loadLibraryBooksPage(libraryId: String, page: Int): LibraryBooksPage =
        loadWithSessionRecovery(LibraryBooksPage(page = page)) {
            repository.loadBooksPage(libraryId, page)
        }

    suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? = loadWithSessionRecovery(null) {
        repository.loadBookDetail(book)
    }

    suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? = loadWithSessionRecovery(null) {
        repository.loadSeriesDetail(seriesId)
    }

    suspend fun loadSeriesCatalog(query: String?, page: Int): SeriesCatalogPage =
        loadWithSessionRecovery(SeriesCatalogPage()) {
            repository.loadSeriesCatalog(query, page)
        }

    suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage =
        loadWithSessionRecovery(AuthorCatalogPage()) {
            repository.loadAuthorsCatalog(query, page)
        }

    suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage? =
        loadWithSessionRecovery(null) {
            repository.loadAuthorBooks(authorId, page)
        }

    suspend fun loadCatalogImage(url: String): ByteArray? = loadWithSessionRecovery(null) {
        repository.loadCatalogImage(url)
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()
    private var lastBrowserState: BrowserState? = null
    private var loginRefreshInFlight = false
    private var loginSubmitInFlight = false
    private val activeDownloads = mutableMapOf<String, Job>()
    private val latestProgressByTarget = mutableMapOf<BookProgressKey, PendingProgress>()
    private val queuedProgressByTarget = mutableMapOf<BookProgressKey, PendingProgress>()
    private var pendingPostLoginDestination: PostLoginDestination? = null
    private var allowCachedLoginFallback = true

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

            when (repository.getSessionState()) {
                SessionState.Authenticated -> {
                    allowCachedLoginFallback = true
                    repository.restoreActiveReaderState()?.let { readerState ->
                        _screen.value = AppScreen.Reader(readerState)
                        return@launch
                    }
                    loadBrowser()
                }
                SessionState.Unauthenticated -> {
                    allowCachedLoginFallback = false
                    showLogin(
                        message = "Your saved session is no longer authenticated. Sign in to continue.",
                        destination = PostLoginDestination.Browser
                    )
                }
                SessionState.Unavailable -> {
                    val cached = repository.loadCachedBrowserState().takeIf { allowCachedLoginFallback }
                    if (cached != null) {
                        showBrowser(
                            cached.copy(
                                isOfflineSnapshot = true,
                                message = "Showing the last cached library snapshot while the server is unavailable."
                            )
                        )
                    } else {
                        showLogin(
                            message = "The server is unavailable. Keep the login page open or retry when the connection recovers.",
                            destination = PostLoginDestination.Browser
                        )
                    }
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
                        serverUrl = serverUrl,
                        message = invalidServerUrlMessage()
                    )
                    return@launch
                }
                ServerCheckResult.UnreachableHost -> {
                    _screen.value = AppScreen.ServerSetup(
                        serverUrl = serverUrl,
                        message = "The server host could not be resolved."
                    )
                    return@launch
                }
                ServerCheckResult.Timeout -> {
                    _screen.value = AppScreen.ServerSetup(
                        serverUrl = serverUrl,
                        message = "The server took too long to respond. Retry or check the network."
                    )
                    return@launch
                }
                ServerCheckResult.TlsFailure -> {
                    _screen.value = AppScreen.ServerSetup(
                        serverUrl = serverUrl,
                        message = "The server TLS certificate could not be validated."
                    )
                    return@launch
                }
                ServerCheckResult.Redirected -> {
                    _screen.value = AppScreen.ServerSetup(
                        serverUrl = serverUrl,
                        message = "The server redirected this URL. Enter the final base URL directly."
                    )
                    return@launch
                }
                ServerCheckResult.HttpFailure -> {
                    _screen.value = AppScreen.ServerSetup(
                        serverUrl = serverUrl,
                        message = "The server responded, but the base URL did not open correctly."
                    )
                    return@launch
                }
                ServerCheckResult.NetworkFailure -> {
                    _screen.value = AppScreen.ServerSetup(
                        serverUrl = serverUrl,
                        message = "Unable to reach that server. Check the URL and try again."
                    )
                    return@launch
                }
            }
            repository.setServerUrl(serverUrl)
            showLogin(
                message = "Connect to the server and complete sign in.",
                destination = PostLoginDestination.Browser
            )
        }
    }

    fun clearServer() {
        scope.launch {
            resetTransientState(clearBrowserState = true)
            repository.clearServer()
            _screen.value = AppScreen.ServerSetup()
        }
    }

    fun refreshLoginState() {
        scope.launch {
            if (loginRefreshInFlight || _screen.value !is AppScreen.Login) {
                return@launch
            }
            loginRefreshInFlight = true
            try {
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isNullOrBlank()) {
                _screen.value = AppScreen.ServerSetup()
                return@launch
            }
            when (repository.getSessionState()) {
                SessionState.Authenticated -> {
                    allowCachedLoginFallback = true
                    resumeAfterLogin()
                }
                SessionState.Unauthenticated -> {
                    val cached = repository.loadCachedBrowserState().takeIf { allowCachedLoginFallback }
                    if (cached != null) {
                        showBrowser(
                            cached.copy(
                                isOfflineSnapshot = true,
                                message = "Showing the last cached library snapshot while waiting for an authenticated session."
                            )
                        )
                    } else {
                        showLogin(
                            message = "Waiting for an authenticated session.",
                            destination = pendingPostLoginDestination ?: PostLoginDestination.Browser
                        )
                    }
                }
                SessionState.Unavailable -> {
                    val cached = repository.loadCachedBrowserState().takeIf { allowCachedLoginFallback }
                    if (cached != null) {
                        showBrowser(
                            cached.copy(
                                isOfflineSnapshot = true,
                                message = "Showing the last cached library snapshot while the server is unavailable."
                            )
                        )
                    } else {
                        showLogin(
                            message = "Waiting for the server connection to recover.",
                            destination = pendingPostLoginDestination ?: PostLoginDestination.Browser
                        )
                    }
                }
            }
            } finally {
                loginRefreshInFlight = false
            }
        }
    }

    fun submitLogin(username: String, password: String) {
        scope.launch {
            val current = _screen.value as? AppScreen.Login ?: return@launch
            if (loginSubmitInFlight) {
                return@launch
            }
            loginSubmitInFlight = true
            _screen.value = current.copy(isSubmitting = true, message = null)
            try {
                repository.login(username = username, password = password)
                when (repository.getSessionState()) {
                    SessionState.Authenticated -> {
                        allowCachedLoginFallback = true
                        resumeAfterLogin()
                    }
                    SessionState.Unauthenticated,
                    SessionState.Unavailable -> throw LoginVerificationException()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showLogin(
                    message = userMessage(error, "Unable to sign in."),
                    destination = pendingPostLoginDestination ?: PostLoginDestination.Browser
                )
            } finally {
                loginSubmitInFlight = false
            }
        }
    }

    fun onBrowserSessionAction() {
        scope.launch {
            val current = lastBrowserState
            if (current?.isOfflineSnapshot == true) {
                allowCachedLoginFallback = false
                showLogin(
                    message = "Sign in again to refresh libraries or open online content.",
                    destination = PostLoginDestination.Browser
                )
                return@launch
            }
            signOut()
        }
    }

    fun beginSignIn() {
        scope.launch {
            allowCachedLoginFallback = false
            showLogin(
                message = "Sign in to refresh your libraries and online content.",
                destination = PostLoginDestination.Browser
            )
        }
    }

    fun signOut() {
        scope.launch {
            resetTransientState(clearBrowserState = true)
            allowCachedLoginFallback = false
            repository.clearSession()
            showLogin(
                message = "Signed out. Sign in to access your libraries.",
                destination = PostLoginDestination.Browser
            )
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
                val selectedLibrary = resolveSelectedLibraryId(
                    preferredId = repository.getSelectedLibraryId(),
                    libraries = libraries
                )
                val booksPage = selectedLibrary?.let { repository.loadBooksPage(it, 0) }
                    ?: LibraryBooksPage()
                val pendingProgressCount = repository.pendingProgressCount()
                showBrowser(
                    BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = selectedLibrary,
                        books = mergeKnownProgress(booksPage.items, selectedLibrary),
                        booksTotal = booksPage.total,
                        booksSeriesTotal = booksPage.seriesTotal,
                        booksPage = booksPage.page ?: 0,
                        booksPageSize = booksPage.size,
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
                if (error is AuthenticationRequiredException) {
                    showLogin(
                        message = "Your session expired. Sign in again to continue browsing.",
                        destination = PostLoginDestination.Browser
                    )
                    return@onFailure
                }
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
                    showBrowser(
                        BrowserState(
                            serverUrl = serverUrl,
                            libraries = emptyList(),
                            selectedLibraryId = null,
                            books = emptyList(),
                            isRefreshing = false,
                            isLoadingLibraries = false,
                            isLoadingBooks = false,
                            message = userMessage(error, "Unable to load libraries.")
                        )
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
                val booksPage = repository.loadBooksPage(libraryId, 0)
                val pendingProgressCount = repository.pendingProgressCount()
                showBrowser(
                    BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = libraryId,
                        books = mergeKnownProgress(booksPage.items, libraryId),
                        booksTotal = booksPage.total,
                        booksSeriesTotal = booksPage.seriesTotal,
                        booksPage = booksPage.page ?: 0,
                        booksPageSize = booksPage.size,
                        isRefreshing = false,
                        isLoadingLibraries = false,
                        isLoadingBooks = false,
                        debugPendingProgressCount = pendingProgressCount,
                        isOfflineSnapshot = false
                    )
                )
            }.onFailure { error ->
                if (error is AuthenticationRequiredException) {
                    showLogin(
                        message = "Your session expired. Sign in again to continue in this library.",
                        destination = PostLoginDestination.SelectLibrary(libraryId)
                    )
                    return@onFailure
                }
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
        openBook(book, ReaderLaunchMode.NORMAL)
    }

    fun previewBook(book: BookSummary) {
        openBook(book, ReaderLaunchMode.PREVIEW)
    }

    private fun openBook(book: BookSummary, launchMode: ReaderLaunchMode) {
        scope.launch {
            _screen.value = AppScreen.ReaderLoading(book, launchMode)
            runCatching {
                val preparedState = repository.buildReaderState(
                    book = book,
                    localOnly = lastBrowserState?.isOfflineSnapshot == true
                )
                val readerState = if (launchMode == ReaderLaunchMode.PREVIEW) {
                    preparedState.copy(
                        lastKnownPosition = 0L,
                        pageIndex = 0,
                        progressPercent = null,
                        launchMode = ReaderLaunchMode.PREVIEW
                    )
                } else {
                    preparedState.copy(launchMode = ReaderLaunchMode.NORMAL)
                }
                if (launchMode == ReaderLaunchMode.NORMAL) {
                    repository.saveActiveReader(readerState.book)
                }
                _screen.value = AppScreen.Reader(readerState)
            }.onFailure { error ->
                if (error is AuthenticationRequiredException) {
                    showLogin(
                        message = "Your session expired. Sign in again to reopen ${book.title}.",
                        destination = PostLoginDestination.OpenBook(book, launchMode)
                    )
                    return@onFailure
                }
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
                    } else if (error is AuthenticationRequiredException) {
                        showLogin(
                            message = "Your session expired. Sign in again to continue downloading ${book.title}.",
                            destination = PostLoginDestination.DownloadBook(book)
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

    private suspend fun <T> loadWithSessionRecovery(
        fallback: T,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: AuthenticationRequiredException) {
            recoverExpiredSession()
            fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    private suspend fun recoverExpiredSession() {
        if (_screen.value is AppScreen.Login) {
            return
        }
        allowCachedLoginFallback = false
        showLogin(
            message = "Your session expired. Sign in again to continue.",
            destination = PostLoginDestination.Browser
        )
    }

    private fun userMessage(error: Throwable, fallback: String): String {
        return when (error) {
            is AuthenticationRequiredException -> "Your session expired. Sign in again."
            is InvalidCredentialsException -> "The username or password was not accepted."
            is LoginRateLimitedException -> "The server is rate limiting sign-in attempts. Try again shortly."
            is LoginVerificationException -> "The server did not confirm the new sign-in. Check the server and try again."
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
            if ((_screen.value as? AppScreen.Reader)?.readerState?.launchMode == ReaderLaunchMode.PREVIEW) {
                return@launch
            }
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
                    progressPageIndex = if (book.mediaKind == MediaKind.EPUB) pageIndex else {
                        pageIndex.takeIf { it > 0 } ?: book.progressPageIndex
                    },
                    progressPercent = progressPercent ?: book.progressPercent
                )
            )
            lastBrowserState?.let { browser ->
                lastBrowserState = browser.copy(
                    books = mergeKnownProgress(browser.books, browser.selectedLibraryId)
                )
            }
            if (book.mediaKind == MediaKind.EPUB && book.readerPageIndex != null) {
                repository.saveEpubReaderPosition(
                    book.copy(
                        progressPageIndex = pageIndex,
                        readerPageIndex = book.readerPageIndex,
                        readerPageCount = book.readerPageCount
                    )
                )
            }
            if (ProgressQueuePolicy.shouldQueue(progress.toSnapshot(), queuedProgressByTarget[key]?.toSnapshot())) {
                queueProgress(key, progress)
            }
        }
    }

    fun closeReader() {
        scope.launch {
            val reader = _screen.value as? AppScreen.Reader
            if (reader?.readerState?.launchMode != ReaderLaunchMode.PREVIEW) {
                flushCurrentReaderProgress()
                repository.clearActiveReader()
            }
            loadBrowser()
        }
    }

    private fun showBrowser(state: BrowserState) {
        lastBrowserState = state
        _screen.value = AppScreen.Browser(browserState = state)
    }

    private fun mergeKnownProgress(books: List<BookSummary>, libraryId: String?): List<BookSummary> {
        val merged = books.map { book ->
            val progress = latestProgressByTarget[book.progressKey()]
                ?: latestProgressByTarget.values.firstOrNull { it.book.id == book.id }
                ?: return@map book
            book.copy(
                progressPositionMs = progress.position.takeIf { it > 0L } ?: book.progressPositionMs,
                progressPageIndex = progress.pageIndex.takeIf { it > 0 } ?: book.progressPageIndex,
                progressPercent = progress.progressPercent ?: book.progressPercent,
                progressLabel = progress.progressPercent?.let { "${it}%" } ?: book.progressLabel,
                lastReadAtMillis = progress.observedAtMillis,
                isRead = progress.progressPercent?.let { it >= 99.5f } ?: book.isRead
            )
        }
        val existingIds = merged.mapTo(mutableSetOf()) { it.id }
        val recentBooks = latestProgressByTarget.values
            .asSequence()
            .filter { progress ->
                progress.book.id !in existingIds &&
                    (libraryId == null || progress.book.libraryId == libraryId)
            }
            .sortedByDescending { it.observedAtMillis }
            .map { progress ->
                progress.book.copy(
                    progressPositionMs = progress.position.takeIf { it > 0L } ?: progress.book.progressPositionMs,
                    progressPageIndex = progress.pageIndex.takeIf { it > 0 } ?: progress.book.progressPageIndex,
                    progressPercent = progress.progressPercent ?: progress.book.progressPercent,
                    progressLabel = progress.progressPercent?.let { "${it}%" } ?: progress.book.progressLabel,
                    lastReadAtMillis = progress.observedAtMillis,
                    isRead = progress.progressPercent?.let { it >= 99.5f } ?: progress.book.isRead
                )
            }
            .toList()
        return merged + recentBooks
    }

    private fun resetTransientState(clearBrowserState: Boolean) {
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        latestProgressByTarget.clear()
        queuedProgressByTarget.clear()
        pendingPostLoginDestination = null
        loginRefreshInFlight = false
        loginSubmitInFlight = false
        allowCachedLoginFallback = true
        if (clearBrowserState) {
            lastBrowserState = null
        }
    }

    private suspend fun showLogin(message: String, destination: PostLoginDestination) {
        pendingPostLoginDestination = destination
        _screen.value = AppScreen.Login(
            serverUrl = repository.getServerUrl().orEmpty(),
            message = message
        )
    }

    private fun resumeAfterLogin() {
        val destination = pendingPostLoginDestination
        pendingPostLoginDestination = null
        when (destination) {
            PostLoginDestination.Browser, null -> loadBrowser()
            is PostLoginDestination.SelectLibrary -> selectLibrary(destination.libraryId)
            is PostLoginDestination.OpenBook -> openBook(destination.book, destination.launchMode)
            is PostLoginDestination.DownloadBook -> downloadBook(destination.book)
        }
    }

    private suspend fun flushCurrentReaderProgress() {
        val reader = _screen.value as? AppScreen.Reader ?: return
        val key = reader.readerState.book.progressKey()
        val progress = latestProgressByTarget[key] ?: return
        if (ProgressQueuePolicy.isMeaningfullyDifferent(progress.toSnapshot(), queuedProgressByTarget[key]?.toSnapshot())) {
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

    private fun PendingProgress.toSnapshot(): ProgressSnapshot {
        return ProgressSnapshot(
            mediaKind = book.mediaKind,
            positionMs = position,
            pageIndex = pageIndex,
            progressPercent = progressPercent,
            observedAtMillis = observedAtMillis
        )
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

    private sealed interface PostLoginDestination {
        data object Browser : PostLoginDestination
        data class SelectLibrary(val libraryId: String) : PostLoginDestination
        data class OpenBook(
            val book: BookSummary,
            val launchMode: ReaderLaunchMode = ReaderLaunchMode.NORMAL
        ) : PostLoginDestination
        data class DownloadBook(val book: BookSummary) : PostLoginDestination
    }

}
