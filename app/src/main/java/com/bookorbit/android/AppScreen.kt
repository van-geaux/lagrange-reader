package com.bookorbit.android

sealed interface AppScreen {
    data object Loading : AppScreen
    data object ServerSetup : AppScreen
    data class Login(
        val serverUrl: String,
        val message: String? = null
    ) : AppScreen

    data class Browser(
        val browserState: BrowserState
    ) : AppScreen

    data class Reader(
        val readerState: ReaderState
    ) : AppScreen
}
