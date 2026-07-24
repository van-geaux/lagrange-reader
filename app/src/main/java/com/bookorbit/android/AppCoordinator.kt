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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val HOME_LIBRARY_REFRESH_CONCURRENCY = 3

class AppCoordinator(
    private val repository: BookOrbitDataSource,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val releaseChecker: suspend (String) -> ReleaseUpdate? = { null },
    private val readIgnoredReleaseTag: () -> String? = { null },
    private val saveIgnoredReleaseTag: (String) -> Unit = {}
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

    suspend fun loadLibraryBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage = loadWithSessionRecovery(LibraryBooksPage(page = page)) {
        repository.loadBooksPage(libraryId, page, filter)
    }

    suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? = loadWithSessionRecovery(null) {
        repository.loadBookDetail(book)
    }

    internal suspend fun loadAudiobookSessionHistory(book: BookSummary): List<AudiobookSessionEvent> {
        val serverUrl = repository.getServerUrl().orEmpty()
        return sessionHistoryStore?.read(serverUrl, book).orEmpty()
    }

    internal fun clearAudiobookSessionHistory(book: BookSummary) {
        scope.launch {
            val serverUrl = repository.getServerUrl().orEmpty()
            sessionHistoryStore?.clearBook(serverUrl, book)
        }
    }

    suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo? {
        return try {
            repository.setBookUserRating(book, rating)
        } catch (error: CancellationException) {
            throw error
        } catch (_: AuthenticationRequiredException) {
            recoverExpiredSession()
            null
        } catch (_: Throwable) {
            showBrowserMessage("Unable to update the rating for ${book.title}.")
            null
        }
    }

    suspend fun loadSeriesDetail(seriesId: String): SeriesDetailInfo? = loadWithSessionRecovery(null) {
        repository.loadSeriesDetail(seriesId)
    }

    suspend fun loadSeriesCatalog(query: String?, page: Int): SeriesCatalogPage =
        loadWithSessionRecovery(SeriesCatalogPage()) {
            repository.loadSeriesCatalog(query, page)
        }

    suspend fun loadSeriesCatalog(filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage =
        loadWithSessionRecovery(SeriesCatalogPage()) {
            repository.loadSeriesCatalog(filter, page)
        }

    suspend fun loadAuthorsCatalog(query: String?, page: Int): AuthorCatalogPage =
        loadWithSessionRecovery(AuthorCatalogPage()) {
            repository.loadAuthorsCatalog(query, page)
        }

    suspend fun loadAuthorBooks(authorId: String, page: Int): AuthorBooksPage? =
        loadWithSessionRecovery(null) {
            repository.loadAuthorBooks(authorId, page)
        }

    suspend fun loadAchievements(): AchievementCatalogue = loadWithSessionRecovery(
        AchievementCatalogue(status = AchievementCatalogueStatus.ERROR)
    ) {
        repository.loadAchievements()
    }

    suspend fun loadCatalogImage(url: String): ByteArray? = loadWithSessionRecovery(null) {
        repository.loadCatalogImage(url)
    }

    suspend fun loadStorageUsage(): StorageUsage = repository.loadStorageUsage()

    suspend fun clearAppCache() {
        repository.clearAppCache()
    }

    fun reconfigureBackgroundRefresh() {
        scope.launch { repository.reconfigureBackgroundRefresh() }
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()
    private val _releaseUpdate = MutableStateFlow<ReleaseUpdate?>(null)
    val releaseUpdate: StateFlow<ReleaseUpdate?> = _releaseUpdate.asStateFlow()
    private var lastBrowserState: BrowserState? = null
    private var loginRefreshInFlight = false
    private var loginSubmitInFlight = false
    private var catalogLoadJob: Job? = null
    private val activeDownloads = mutableMapOf<String, Job>()
    private val latestProgressByTarget = mutableMapOf<BookProgressKey, PendingProgress>()
    private val queuedProgressByTarget = mutableMapOf<BookProgressKey, PendingProgress>()
    private var pendingPostLoginDestination: PostLoginDestination? = null
    private var allowCachedLoginFallback = true
    private var audioPlaybackOpener: (suspend (ReaderState, Boolean) -> Boolean)? = null
    private var audioSessionHistoryOpener: ((BookSummary, Long) -> Unit)? = null
    private var releaseCheckInFlight = false
    private var dismissedReleaseTag: String? = null
    private var sessionHistoryStore: AudiobookSessionHistoryStore? = null

    fun setAudioPlaybackOpener(opener: suspend (ReaderState, Boolean) -> Boolean) {
        audioPlaybackOpener = opener
    }

    internal fun setSessionHistoryStore(store: AudiobookSessionHistoryStore) {
        sessionHistoryStore = store
    }

    fun setAudioSessionHistoryOpener(opener: (BookSummary, Long) -> Unit) {
        audioSessionHistoryOpener = opener
    }

    fun openAudiobookSessionHistory(book: BookSummary, positionMs: Long) {
        audioSessionHistoryOpener?.invoke(book, positionMs)
    }

    fun checkForAppUpdate() {
        if (releaseCheckInFlight) return
        releaseCheckInFlight = true
        scope.launch {
            try {
                val update = releaseChecker(BuildConfig.VERSION_NAME)
                val ignoredTag = readIgnoredReleaseTag()
                if (
                    update != null &&
                    update.tagName != dismissedReleaseTag &&
                    update.tagName != ignoredTag
                ) {
                    _releaseUpdate.value = update
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Release checks are optional and must not interrupt app startup.
            } finally {
                releaseCheckInFlight = false
            }
        }
    }

    fun dismissReleaseUpdate() {
        _releaseUpdate.value?.let { update -> dismissedReleaseTag = update.tagName }
        _releaseUpdate.value = null
    }

    fun ignoreReleaseUpdate() {
        _releaseUpdate.value?.let { update ->
            dismissedReleaseTag = update.tagName
            saveIgnoredReleaseTag(update.tagName)
        }
        _releaseUpdate.value = null
    }

    fun bootstrap() {
        checkForAppUpdate()
        scope.launch {
            _screen.value = AppScreen.Loading
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isNullOrBlank()) {
                _screen.value = AppScreen.ServerSetup()
                return@launch
            }

            val localAudioState = repository.restoreActiveReaderState(localOnly = true)?.let { readerState ->
                if (readerState.book.mediaKind != MediaKind.AUDIO) {
                    _screen.value = AppScreen.Reader(readerState)
                    return@launch
                }
                readerState
            }

            val startupCache = repository.loadCachedBrowserState().takeIf { allowCachedLoginFallback }
            if (startupCache != null) {
                showBrowser(
                    startupCache.copy(
                        isRefreshing = true,
                        isLoadingLibraries = false,
                        isLoadingBooks = false,
                        isCatalogSyncing = false,
                        isOfflineSnapshot = true,
                        message = null
                    )
                )
            }

            val sessionState = repository.getSessionState()
            if (startupCache != null) {
                val currentBrowser = (_screen.value as? AppScreen.Browser)?.browserState
                    ?: return@launch
                if (currentBrowser.selectedLibraryId != startupCache.selectedLibraryId) {
                    // A user selection made while the session check was running owns
                    // the screen now; do not restart the old cached destination.
                    return@launch
                }
            }
            when (sessionState) {
                SessionState.Authenticated -> {
                    allowCachedLoginFallback = true
                    repository.restoreActiveReaderState()?.let { readerState ->
                        if (readerState.book.mediaKind != MediaKind.AUDIO) {
                            _screen.value = AppScreen.Reader(readerState)
                            return@launch
                        }
                        restoreAudioInBackground(readerState)
                    } ?: localAudioState?.let(::restoreAudioInBackground)
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
                    localAudioState?.let(::restoreAudioInBackground)
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
            serverSetupFailure(serverUrl, repository.checkServer(serverUrl))?.let { failure ->
                _screen.value = failure
                return@launch
            }
            repository.setServerUrl(serverUrl)
            showLogin(
                message = "Connect to the server and complete sign in.",
                destination = PostLoginDestination.Browser
            )
        }
    }

    fun changeServer(serverUrl: String) {
        scope.launch {
            val oldServerUrl = repository.getServerUrl().orEmpty()
            resetTransientState(clearBrowserState = true)
            allowCachedLoginFallback = false
            sessionHistoryStore?.clearServer(oldServerUrl)
            repository.clearServer()
            serverSetupFailure(serverUrl, repository.checkServer(serverUrl))?.let { failure ->
                _screen.value = failure
                return@launch
            }
            repository.setServerUrl(serverUrl)
            showLogin(
                message = "Server changed. Sign in to access your libraries.",
                destination = PostLoginDestination.Browser
            )
        }
    }

    fun clearServer() {
        scope.launch {
            val oldServerUrl = repository.getServerUrl().orEmpty()
            resetTransientState(clearBrowserState = true)
            sessionHistoryStore?.clearServer(oldServerUrl)
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
        catalogLoadJob?.cancel()
        catalogLoadJob = scope.launch {
            val serverUrl = repository.getServerUrl().orEmpty()
            var previous = lastBrowserState
            if (previous == null) {
                repository.loadCachedBrowserState()?.let { cached ->
                    previous = cached
                }
            }
            previous?.let { previousState ->
                showBrowser(
                    previousState.copy(
                        isRefreshing = true,
                        isLoadingLibraries = true,
                        isLoadingBooks = true,
                        isOfflineSnapshot = false,
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
                // Flush queued reader progress before loading the first page. This makes
                // the first Home render reflect progress that was created in an earlier
                // session instead of showing Continue reading only after a second open.
                val progressSyncResult = repository.syncPendingProgress()
                val pendingProgressCount = repository.pendingProgressCount()
                if (selectedLibrary == null) {
                    showBrowser(
                        BrowserState(
                            serverUrl = serverUrl,
                            libraries = libraries,
                            selectedLibraryId = null,
                            books = emptyList(),
                            isRefreshing = false,
                            isLoadingLibraries = false,
                            isLoadingBooks = false,
                            debugPendingProgressCount = pendingProgressCount,
                            isOfflineSnapshot = false
                        )
                    )
                    return@runCatching
                }

                repository.setSelectedLibraryId(selectedLibrary)
                var homeBooks = repository.loadCachedHomeBooks()
                    .onlyFrom(libraries)
                val cachedCatalog = repository.loadCachedLibraryCatalog(selectedLibrary)
                val firstPage = cachedCatalog ?: repository.loadBooksPage(selectedLibrary, 0)
                homeBooks = homeBooks.replaceLibrary(selectedLibrary, firstPage.items)
                showBrowser(
                    catalogBrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        libraryId = selectedLibrary,
                        page = firstPage,
                        homeBooks = homeBooks,
                        pendingProgressCount = pendingProgressCount,
                        isRefreshing = true,
                        isCatalogSyncing = true
                    )
                )

                val refreshedCatalog = repository.refreshLibraryCatalog(
                    libraryId = selectedLibrary,
                    firstPage = firstPage.takeUnless { it.isComplete }
                )
                if (progressSyncResult == SyncAttemptResult.Success) {
                    // Once the server has accepted every current-server update and the
                    // complete fresh catalog has loaded, its progress is authoritative.
                    latestProgressByTarget.clear()
                    queuedProgressByTarget.clear()
                }
                homeBooks = homeBooks.replaceLibrary(selectedLibrary, refreshedCatalog.items)
                showBrowser(
                    catalogBrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        libraryId = selectedLibrary,
                        page = refreshedCatalog,
                        homeBooks = homeBooks,
                        pendingProgressCount = pendingProgressCount,
                        isRefreshing = libraries.size > 1,
                        isCatalogSyncing = false
                    )
                )
                val failedLibraries = mutableListOf<String>()
                val remainingLibraries = libraries.filterNot { it.id == selectedLibrary }
                remainingLibraries.chunked(HOME_LIBRARY_REFRESH_CONCURRENCY).forEach { batch ->
                    val outcomes = coroutineScope {
                        batch.map { library ->
                            async {
                                try {
                                    library to repository.refreshLibraryCatalog(library.id)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: AuthenticationRequiredException) {
                                    throw error
                                } catch (_: Throwable) {
                                    library to null
                                }
                            }
                        }.awaitAll()
                    }
                    outcomes.forEach { (library, refreshed) ->
                        if (refreshed == null) {
                            failedLibraries += library.name
                        } else {
                            homeBooks = homeBooks.replaceLibrary(library.id, refreshed.items)
                            lastBrowserState?.let { current ->
                                showBrowser(
                                    current.copy(
                                        homeBooks = mergeKnownProgress(homeBooks, null)
                                    )
                                )
                            }
                        }
                    }
                }
                val current = lastBrowserState
                if (current != null) {
                    showBrowser(
                        current.copy(
                            isRefreshing = false,
                            message = failedLibraries.takeIf { it.isNotEmpty() }?.let { failed ->
                                "Home is showing cached results for ${failed.joinToString()}."
                            }
                        )
                    )
                }
            }.onFailure { error ->
                if (error is AuthenticationRequiredException) {
                    showLogin(
                        message = "Your session expired. Sign in again to continue browsing.",
                        destination = PostLoginDestination.Browser
                    )
                    return@onFailure
                }
                val interruptedCatalog = lastBrowserState?.takeIf { it.isCatalogSyncing }
                if (interruptedCatalog != null) {
                    showBrowser(
                        interruptedCatalog.copy(
                            isRefreshing = false,
                            isLoadingLibraries = false,
                            isLoadingBooks = false,
                            isCatalogSyncing = false,
                            message = userMessage(error, "Unable to finish refreshing the library catalog.")
                        )
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
                    val previousState = requireNotNull(previous)
                    showBrowser(
                        previousState.copy(
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
        catalogLoadJob?.cancel()
        catalogLoadJob = scope.launch {
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
                val pendingProgressCount = repository.pendingProgressCount()
                val cachedCatalog = repository.loadCachedLibraryCatalog(libraryId)
                val firstPage = cachedCatalog ?: repository.loadBooksPage(libraryId, 0)
                val initialHomeBooks = currentBrowser?.browserState?.homeBooks
                    .orEmpty()
                    .replaceLibrary(libraryId, firstPage.items)
                showBrowser(
                    catalogBrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        libraryId = libraryId,
                        page = firstPage,
                        homeBooks = initialHomeBooks,
                        pendingProgressCount = pendingProgressCount,
                        isRefreshing = true,
                        isCatalogSyncing = true
                    )
                )
                val refreshedCatalog = repository.refreshLibraryCatalog(
                    libraryId = libraryId,
                    firstPage = firstPage.takeUnless { it.isComplete }
                )
                showBrowser(
                    catalogBrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        libraryId = libraryId,
                        page = refreshedCatalog,
                        homeBooks = initialHomeBooks.replaceLibrary(libraryId, refreshedCatalog.items),
                        pendingProgressCount = pendingProgressCount,
                        isRefreshing = false,
                        isCatalogSyncing = false
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
                val interruptedCatalog = lastBrowserState?.takeIf { state ->
                    state.selectedLibraryId == libraryId && state.isCatalogSyncing
                }
                if (interruptedCatalog != null) {
                    showBrowser(
                        interruptedCatalog.copy(
                            isRefreshing = false,
                            isLoadingBooks = false,
                            isCatalogSyncing = false,
                            message = userMessage(error, "Unable to finish refreshing the selected library.")
                        )
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
            if (book.mediaKind != MediaKind.AUDIO) {
                _screen.value = AppScreen.ReaderLoading(book, launchMode)
            }
            runCatching {
                val offlineOpen = lastBrowserState?.isOfflineSnapshot == true
                val syncResult = if (launchMode == ReaderLaunchMode.NORMAL && !offlineOpen) {
                    repository.syncPendingProgress()
                } else {
                    null
                }
                val detailForOpen: BookDetailInfo? = when {
                    launchMode == ReaderLaunchMode.NORMAL && !offlineOpen -> {
                        runCatching { repository.loadBookDetail(book) }.getOrNull()
                    }
                    book.mediaKind == MediaKind.AUDIO && book.audioChapters.isEmpty() -> {
                        runCatching { repository.loadBookDetail(book) }.getOrNull()
                    }
                    else -> null
                }
                val readerBook = if (book.mediaKind == MediaKind.AUDIO && book.audioChapters.isEmpty()) {
                    detailForOpen?.let { detail ->
                        val chapters = detail.audioChapters.ifEmpty {
                            detail.book.audioChapters
                        }
                        book.copy(audioChapters = chapters)
                    } ?: book
                } else {
                    detailForOpen?.book ?: book
                }
                val progressBook = if (
                    launchMode == ReaderLaunchMode.NORMAL &&
                    !offlineOpen &&
                    syncResult == SyncAttemptResult.Success
                ) {
                    repository.loadReaderProgress(readerBook)
                } else {
                    readerBook
                }
                val preparedState = repository.buildReaderState(
                    book = progressBook,
                    localOnly = offlineOpen
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
                if (book.mediaKind == MediaKind.AUDIO) {
                    val opener = audioPlaybackOpener
                        ?: throw UserFacingException("Audiobook playback is unavailable.")
                    repository.saveActiveReader(readerState.book, launchMode)
                    if (!opener(readerState, true)) {
                        repository.clearActiveReader()
                        throw UserFacingException(AUDIO_OPEN_CANCELLED_MESSAGE)
                    }
                } else {
                    if (launchMode == ReaderLaunchMode.NORMAL) {
                        repository.saveActiveReader(readerState.book)
                    }
                    _screen.value = AppScreen.Reader(readerState)
                }
            }.onFailure { error ->
                if (error.message == AUDIO_OPEN_CANCELLED_MESSAGE) {
                    return@onFailure
                }
                if (error is AuthenticationRequiredException) {
                    showLogin(
                        message = "Your session expired. Sign in again to reopen ${book.title}.",
                        destination = PostLoginDestination.OpenBook(book, launchMode)
                    )
                    return@onFailure
                }
                val fallback = lastBrowserState
                if (fallback != null) {
                    navigateToBrowser(
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

    private fun restoreAudioInBackground(readerState: ReaderState) {
        val opener = audioPlaybackOpener ?: return
        scope.launch {
            if (!opener(readerState, false)) {
                repository.clearActiveReader()
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
                repository.downloadBook(book) { progress ->
                    scope.launch { updateDownloadProgress(fileId, progress) }
                }
            }
            activeDownloads.remove(fileId)
            result
                .onSuccess { localFile ->
                    updateDownloadState(
                        fileId = fileId,
                        isDownloading = false,
                        failed = false,
                        message = null
                    )
                    updateLocalFileState(
                        fileId = fileId,
                        localPath = localFile.absolutePath,
                        downloadedSourceUpdatedAtMillis = book.updatedAtMillis
                    )
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
                        updateDownloadState(
                            fileId = fileId,
                            isDownloading = false,
                            failed = false,
                            message = null
                        )
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

    fun dismissBrowserMessage() {
        val current = lastBrowserState ?: return
        if (current.message == null) return
        showBrowser(current.copy(message = null))
    }

    fun deleteLocalCopy(book: BookSummary) {
        scope.launch {
            runCatching {
                repository.deleteLocalCopy(book)
            }.onSuccess {
                book.fileId?.let { fileId -> updateLocalFileState(fileId, null) }
            }.onFailure { error ->
                showBrowserMessage(userMessage(error, "Unable to remove the local copy."))
            }
        }
    }

    fun deleteLocalCopies(books: List<BookSummary>) {
        if (books.isEmpty()) return
        scope.launch {
            val deletedFileIds = mutableSetOf<String>()
            var failureCount = 0
            books.forEach { book ->
                try {
                    repository.deleteLocalCopy(book)
                    book.fileId?.let(deletedFileIds::add)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    failureCount += 1
                }
            }
            val message = when {
                failureCount == 0 -> null
                deletedFileIds.isEmpty() -> "Unable to remove the selected local copies."
                else -> "Removed ${deletedFileIds.size} of ${books.size} local copies. " +
                    "$failureCount could not be removed."
            }
            if (deletedFileIds.isNotEmpty()) {
                clearLocalFileStates(deletedFileIds, message)
            } else if (message != null) {
                showBrowserMessage(message)
            }
        }
    }

    fun removeFromCurrentlyReading(book: BookSummary) {
        resetBookReadingState(
            book = book,
            successMessage = "Removed ${book.title} from Currently reading and reset its progress.",
            failureMessage = "Unable to remove ${book.title} from Currently reading."
        )
    }

    fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) {
        when (status) {
            BookReadStatus.READ -> markBookAsRead(book)
            BookReadStatus.UNREAD -> markBookAsUnread(book)
            else -> scope.launch {
                try {
                    repository.setBookReadingStatus(book, status)
                    latestProgressByTarget.entries.removeAll { (key, _) -> key.bookId == book.id }
                    queuedProgressByTarget.entries.removeAll { (key, _) -> key.bookId == book.id }
                    val current = lastBrowserState ?: return@launch
                    val completedAtMillis = if (status.isCompletedStatus()) {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
                    val updatedBook = { currentBook: BookSummary ->
                        if (currentBook.id == book.id) {
                            currentBook.copy(
                                readStatus = status,
                                isRead = status.isCompletedStatus(),
                                lastReadAtMillis = completedAtMillis
                            )
                        } else {
                            currentBook
                        }
                    }
                    showBrowser(
                        current.copy(
                            books = current.books.map(updatedBook),
                            homeBooks = current.homeBooks.map(updatedBook),
                            debugPendingProgressCount = repository.pendingProgressCount(),
                            message = "Marked ${book.title} as ${status.displayLabel()}."
                        )
                    )
                } catch (_: AuthenticationRequiredException) {
                    recoverExpiredSession()
                } catch (error: Throwable) {
                    showBrowserMessage(
                        userMessage(error, "Unable to update ${book.title}'s reading status.")
                    )
                }
            }
        }
    }
    fun markBookAsRead(book: BookSummary) {
        scope.launch {
            try {
                repository.markBookAsRead(book)
                latestProgressByTarget.entries.removeAll { (key, _) -> key.bookId == book.id }
                queuedProgressByTarget.entries.removeAll { (key, _) -> key.bookId == book.id }
                val current = lastBrowserState ?: return@launch
                val markedAtMillis = System.currentTimeMillis()
                showBrowser(
                    current.copy(
                        books = current.books.map { currentBook ->
                            if (currentBook.id == book.id) {
                                currentBook.copy(
                                    readStatus = BookReadStatus.READ,
                                    isRead = true,
                                    lastReadAtMillis = markedAtMillis
                                )
                            } else {
                                currentBook
                            }
                        },
                        homeBooks = current.homeBooks.map { currentBook ->
                            if (currentBook.id == book.id) {
                                currentBook.copy(
                                    readStatus = BookReadStatus.READ,
                                    isRead = true,
                                    lastReadAtMillis = markedAtMillis
                                )
                            } else {
                                currentBook
                            }
                        },
                        debugPendingProgressCount = repository.pendingProgressCount(),
                        message = "Marked ${book.title} as read."
                    )
                )
            } catch (_: AuthenticationRequiredException) {
                recoverExpiredSession()
            } catch (error: Throwable) {
                showBrowserMessage(userMessage(error, "Unable to mark ${book.title} as read."))
            }
        }
    }

    fun markBookAsUnread(book: BookSummary) {
        resetBookReadingState(
            book = book,
            successMessage = "Marked ${book.title} as unread and reset its progress.",
            failureMessage = "Unable to mark ${book.title} as unread."
        )
    }

    private fun resetBookReadingState(
        book: BookSummary,
        successMessage: String,
        failureMessage: String
    ) {
        scope.launch {
            try {
                repository.resetBookReadingState(book)
                latestProgressByTarget.entries.removeAll { (key, _) -> key.bookId == book.id }
                queuedProgressByTarget.entries.removeAll { (key, _) -> key.bookId == book.id }
                val current = lastBrowserState ?: return@launch
                showBrowser(
                    current.copy(
                        books = current.books.map { currentBook ->
                            if (currentBook.id == book.id) currentBook.withReadingStateReset() else currentBook
                        },
                        homeBooks = current.homeBooks.map { currentBook ->
                            if (currentBook.id == book.id) currentBook.withReadingStateReset() else currentBook
                        },
                        debugPendingProgressCount = repository.pendingProgressCount(),
                        message = successMessage
                    )
                )
            } catch (_: AuthenticationRequiredException) {
                recoverExpiredSession()
            } catch (error: Throwable) {
                showBrowserMessage(userMessage(error, failureMessage))
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
        val progressByFile = current.downloadProgressByFileId.toMutableMap()
        val failedDownloads = current.failedDownloadFileIds.toMutableSet()
        if (isDownloading) {
            downloading += fileId
        } else {
            downloading -= fileId
            progressByFile -= fileId
        }
        if (failed) {
            failedDownloads += fileId
        } else {
            failedDownloads -= fileId
        }
        showBrowser(
            current.copy(
                downloadingFileIds = downloading,
                downloadProgressByFileId = progressByFile,
                failedDownloadFileIds = failedDownloads,
                message = message
            )
        )
    }

    private fun updateDownloadProgress(fileId: String, progress: Float?) {
        val current = lastBrowserState ?: return
        if (fileId !in current.downloadingFileIds) return
        val progressByFile = current.downloadProgressByFileId.toMutableMap()
        if (progress == null) {
            progressByFile -= fileId
        } else {
            progressByFile[fileId] = progress.coerceIn(0f, 1f)
        }
        showBrowser(current.copy(downloadProgressByFileId = progressByFile))
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
        if ((_screen.value as? AppScreen.Reader)?.readerState?.launchMode == ReaderLaunchMode.PREVIEW) {
            return
        }
        val key = book.progressKey()
        val progress = PendingProgress(
            book = book,
            position = position,
            pageIndex = pageIndex,
            progressPercent = progressPercent,
            observedAtMillis = System.currentTimeMillis()
        )
        // Keep this assignment outside the coroutine. A reader can be closed immediately
        // after a page callback, and closeReader must still be able to flush this update.
        latestProgressByTarget[key] = progress
        scope.launch {
            repository.saveActiveReader(
                book.copy(
                    progressPositionMs = position.takeIf { it > 0L } ?: book.progressPositionMs,
                    progressPageIndex = if (book.mediaKind == MediaKind.EPUB) pageIndex else {
                        pageIndex.takeIf { it > 0 } ?: book.progressPageIndex
                    },
                    progressPercent = progressPercent ?: book.progressPercent,
                    readStatus = readingStatusAfterProgress(progressPercent)
                )
            )
            lastBrowserState?.let { browser ->
                lastBrowserState = browser.copy(
                    books = mergeKnownProgress(browser.books, browser.selectedLibraryId),
                    homeBooks = mergeKnownProgress(browser.homeBooks, null)
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

    fun minimizeAudioReader() {
        val reader = (_screen.value as? AppScreen.Reader)?.readerState ?: return
        if (reader.book.mediaKind != MediaKind.AUDIO) return
        lastBrowserState?.let { browser ->
            navigateToBrowser(
                browser.copy(
                    books = mergeKnownProgress(browser.books, browser.selectedLibraryId),
                    homeBooks = mergeKnownProgress(browser.homeBooks, null)
                )
            )
        } ?: loadBrowser()
    }

    fun onAudioPlaybackProgress(
        book: BookSummary,
        position: Long,
        progressPercent: Float?,
        launchMode: ReaderLaunchMode
    ) {
        if (launchMode == ReaderLaunchMode.PREVIEW) return
        onProgress(book, position, 0, progressPercent)
    }

    fun onAudioPlaybackClosed(book: BookSummary, launchMode: ReaderLaunchMode) {
        scope.launch {
            if (launchMode == ReaderLaunchMode.NORMAL) {
                val key = book.progressKey()
                latestProgressByTarget[key]?.let { progress ->
                    if (ProgressQueuePolicy.isMeaningfullyDifferent(
                            progress.toSnapshot(),
                            queuedProgressByTarget[key]?.toSnapshot()
                        )
                    ) {
                        queueProgress(key, progress)
                    }
                }
                runCatching { repository.syncPendingProgress() }
            }
            repository.clearActiveReader()
        }
    }

    fun onAudioPlaybackFailed(book: BookSummary, message: String) {
        scope.launch {
            repository.clearActiveReader()
            val browser = lastBrowserState
            if (browser != null) {
                navigateToBrowser(
                    browser.copy(
                        books = mergeKnownProgress(browser.books, browser.selectedLibraryId),
                        homeBooks = mergeKnownProgress(browser.homeBooks, null),
                        message = "Unable to open ${book.title} with Readium. $message"
                    )
                )
            } else {
                loadBrowser()
            }
        }
    }

    fun closeReader() {
        val reader = _screen.value as? AppScreen.Reader
        lastBrowserState?.let { browser ->
            navigateToBrowser(
                browser.copy(
                    books = mergeKnownProgress(browser.books, browser.selectedLibraryId),
                    homeBooks = mergeKnownProgress(browser.homeBooks, null),
                    isRefreshing = true,
                    isLoadingLibraries = true,
                    isLoadingBooks = true,
                    isOfflineSnapshot = false,
                    message = null
                )
            )
        } ?: run {
            _screen.value = AppScreen.Loading
        }
        scope.launch {
            if (reader?.readerState?.launchMode != ReaderLaunchMode.PREVIEW) {
                flushReaderProgress(reader)
                // Try to publish before clearing the active reader. WorkManager remains
                // the fallback for offline/transient failures.
                runCatching { repository.syncPendingProgress() }
                repository.clearActiveReader()
            }
            loadBrowser()
        }
    }

    private fun catalogBrowserState(
        serverUrl: String,
        libraries: List<LibrarySummary>,
        libraryId: String,
        page: LibraryBooksPage,
        homeBooks: List<BookSummary>? = null,
        pendingProgressCount: Int,
        isRefreshing: Boolean,
        isCatalogSyncing: Boolean
    ): BrowserState {
        val transient = lastBrowserState
        return BrowserState(
            serverUrl = serverUrl,
            libraries = libraries,
            selectedLibraryId = libraryId,
            books = mergeKnownProgress(page.items, libraryId),
            homeBooks = mergeKnownProgress(homeBooks ?: transient?.homeBooks.orEmpty(), null),
            booksTotal = page.total,
            booksSeriesTotal = page.seriesTotal,
            booksPage = page.page ?: 0,
            booksPageSize = page.size,
            isCatalogComplete = page.isComplete,
            isCatalogSyncing = isCatalogSyncing,
            catalogRefreshedAtMillis = page.refreshedAtMillis,
            libraryJumpBuckets = page.jumpBuckets,
            isRefreshing = isRefreshing,
            isLoadingLibraries = false,
            isLoadingBooks = false,
            downloadingFileIds = transient?.downloadingFileIds.orEmpty(),
            downloadProgressByFileId = transient?.downloadProgressByFileId.orEmpty(),
            failedDownloadFileIds = transient?.failedDownloadFileIds.orEmpty(),
            localFilePathOverrides = transient?.localFilePathOverrides.orEmpty(),
            localBooksRevision = transient?.localBooksRevision ?: 0,
            debugPendingProgressCount = pendingProgressCount,
            isOfflineSnapshot = false
        )
    }

    private fun showBrowser(state: BrowserState) {
        lastBrowserState = state
        if (_screen.value is AppScreen.ReaderLoading || _screen.value is AppScreen.Reader) {
            return
        }
        _screen.value = AppScreen.Browser(browserState = state)
    }

    private fun navigateToBrowser(state: BrowserState) {
        lastBrowserState = state
        _screen.value = AppScreen.Browser(browserState = state)
    }

    private fun updateLocalFileState(
        fileId: String,
        localPath: String?,
        downloadedSourceUpdatedAtMillis: Long? = null
    ) {
        val current = lastBrowserState ?: return
        val nextOverrides = current.localFilePathOverrides + (fileId to localPath)
        showBrowser(
            current.copy(
                books = current.books.map { book ->
                    if (book.fileId == fileId) {
                        book.copy(
                            localPath = localPath,
                            downloadedSourceUpdatedAtMillis = downloadedSourceUpdatedAtMillis
                        )
                    } else {
                        book
                    }
                },
                homeBooks = current.homeBooks.map { book ->
                    if (book.fileId == fileId) {
                        book.copy(
                            localPath = localPath,
                            downloadedSourceUpdatedAtMillis = downloadedSourceUpdatedAtMillis
                        )
                    } else {
                        book
                    }
                },
                localFilePathOverrides = nextOverrides,
                localBooksRevision = current.localBooksRevision + 1
            )
        )
    }

    private fun clearLocalFileStates(fileIds: Set<String>, message: String?) {
        val current = lastBrowserState ?: return
        val nextOverrides = current.localFilePathOverrides + fileIds.associateWith { null }
        showBrowser(
            current.copy(
                books = current.books.map { book ->
                    if (book.fileId in fileIds) book.copy(localPath = null) else book
                },
                homeBooks = current.homeBooks.map { book ->
                    if (book.fileId in fileIds) book.copy(localPath = null) else book
                },
                localFilePathOverrides = nextOverrides,
                localBooksRevision = current.localBooksRevision + 1,
                message = message ?: current.message
            )
        )
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
                readStatus = readingStatusAfterProgress(progress.progressPercent),
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
                    readStatus = readingStatusAfterProgress(progress.progressPercent),
                    isRead = progress.progressPercent?.let { it >= 99.5f } ?: progress.book.isRead
                )
            }
            .toList()
        return merged + recentBooks
    }

    private fun readingStatusAfterProgress(progressPercent: Float?): BookReadStatus = when {
        progressPercent != null && progressPercent >= 99.5f -> BookReadStatus.READ
        else -> BookReadStatus.READING
    }

    private fun List<BookSummary>.onlyFrom(libraries: List<LibrarySummary>): List<BookSummary> {
        val libraryIds = libraries.mapTo(mutableSetOf()) { it.id }
        return filter { it.libraryId in libraryIds }
    }

    private fun List<BookSummary>.replaceLibrary(
        libraryId: String,
        replacement: List<BookSummary>
    ): List<BookSummary> {
        return (filterNot { it.libraryId == libraryId } + replacement)
            .distinctBy { it.id }
    }

    private fun resetTransientState(clearBrowserState: Boolean) {
        catalogLoadJob?.cancel()
        catalogLoadJob = null
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

    private fun serverSetupFailure(
        serverUrl: String,
        result: ServerCheckResult
    ): AppScreen.ServerSetup? {
        val message = when (result) {
            ServerCheckResult.Reachable -> return null
            ServerCheckResult.MalformedUrl -> invalidServerUrlMessage()
            ServerCheckResult.UnreachableHost -> "The server host could not be resolved."
            ServerCheckResult.Timeout -> "The server took too long to respond. Retry or check the network."
            ServerCheckResult.TlsFailure -> "The server TLS certificate could not be validated."
            ServerCheckResult.Redirected -> "The server redirected this URL. Enter the final base URL directly."
            ServerCheckResult.HttpFailure -> "The server responded, but the base URL did not open correctly."
            ServerCheckResult.NetworkFailure -> "Unable to reach that server. Check the URL and try again."
        }
        return AppScreen.ServerSetup(serverUrl = serverUrl, message = message)
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

    private suspend fun flushReaderProgress(reader: AppScreen.Reader?) {
        reader ?: return
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
