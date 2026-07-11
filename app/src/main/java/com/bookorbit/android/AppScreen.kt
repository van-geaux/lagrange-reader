package com.bookorbit.android

sealed interface AppScreen {
    data object Loading : AppScreen
    data class ServerSetup(
        val serverUrl: String = "",
        val message: String? = null
    ) : AppScreen
    data class Login(
        val serverUrl: String,
        val message: String? = null,
        val isSubmitting: Boolean = false
    ) : AppScreen

    data class Browser(
        val browserState: BrowserState
    ) : AppScreen

    data class ReaderLoading(
        val book: BookSummary,
        val launchMode: ReaderLaunchMode = ReaderLaunchMode.NORMAL
    ) : AppScreen

    data class Reader(
        val readerState: ReaderState
    ) : AppScreen
}
