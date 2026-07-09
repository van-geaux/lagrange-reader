package com.bookorbit.android

sealed interface AppScreen {
    data object Loading : AppScreen
    data class ServerSetup(
        val message: String? = null
    ) : AppScreen
    data class Login(
        val serverUrl: String,
        val message: String? = null
    ) : AppScreen

    data class Browser(
        val browserState: BrowserState
    ) : AppScreen

    data class ReaderLoading(
        val book: BookSummary
    ) : AppScreen

    data class Reader(
        val readerState: ReaderState
    ) : AppScreen
}
