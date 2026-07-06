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

    fun bootstrap() {
        scope.launch {
            _screen.value = AppScreen.Loading
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isNullOrBlank()) {
                _screen.value = AppScreen.ServerSetup
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
            repository.setServerUrl(serverUrl)
            _screen.value = AppScreen.Login(
                serverUrl = repository.getServerUrl().orEmpty(),
                message = "Connect to the server and complete sign in."
            )
        }
    }

    fun clearServer() {
        scope.launch {
            repository.clearServer()
            _screen.value = AppScreen.ServerSetup
        }
    }

    fun refreshLoginState() {
        scope.launch {
            val serverUrl = repository.getServerUrl()
            if (serverUrl.isNullOrBlank()) {
                _screen.value = AppScreen.ServerSetup
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
            runCatching {
                val libraries = repository.loadLibraries()
                val selectedLibrary = repository.getSelectedLibraryId() ?: libraries.firstOrNull()?.id
                val books = selectedLibrary?.let { repository.loadBooks(it) }.orEmpty()
                _screen.value = AppScreen.Browser(
                    browserState = BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = selectedLibrary,
                        books = books
                    )
                )
                if (selectedLibrary != null) {
                    repository.setSelectedLibraryId(selectedLibrary)
                }
                repository.syncPendingProgress()
            }.onFailure {
                _screen.value = AppScreen.Login(
                    serverUrl = serverUrl,
                    message = "Session expired or the server is unavailable. Sign in again."
                )
            }
        }
    }

    fun selectLibrary(libraryId: String) {
        scope.launch {
            val serverUrl = repository.getServerUrl().orEmpty()
            runCatching {
                repository.setSelectedLibraryId(libraryId)
                val current = _screen.value
                val libraries = when (current) {
                    is AppScreen.Browser -> current.browserState.libraries
                    else -> repository.loadLibraries()
                }
                val books = repository.loadBooks(libraryId)
                _screen.value = AppScreen.Browser(
                    browserState = BrowserState(
                        serverUrl = serverUrl,
                        libraries = libraries,
                        selectedLibraryId = libraryId,
                        books = books
                    )
                )
            }.onFailure {
                _screen.value = AppScreen.Browser(
                    browserState = BrowserState(
                        serverUrl = serverUrl,
                        libraries = emptyList(),
                        selectedLibraryId = null,
                        books = emptyList(),
                        message = it.message ?: "Unable to load the selected library."
                    )
                )
            }
        }
    }

    fun openBook(book: BookSummary) {
        scope.launch {
            runCatching {
                val readerState = repository.buildReaderState(book)
                _screen.value = AppScreen.Reader(readerState)
            }.onFailure {
                loadBrowser()
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
            repository.queueProgress(
                book = book,
                position = position,
                pageIndex = pageIndex,
                progressPercent = progressPercent
            )
        }
    }

    fun closeReader() {
        scope.launch {
            loadBrowser()
        }
    }
}
