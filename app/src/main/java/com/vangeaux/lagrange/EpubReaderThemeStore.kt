package com.vangeaux.lagrange

import android.content.Context

internal const val EPUB_READER_THEME_PREFERENCES = "epub_reader_theme"
private const val EPUB_READER_THEME_KEY = "selected_theme"

/** Persists one EPUB background choice across books and app sessions. */
internal class EpubReaderThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        EPUB_READER_THEME_PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun read(): EpubReaderTheme = epubReaderThemeFromStorage(
        preferences.getString(EPUB_READER_THEME_KEY, null)
    )

    fun save(theme: EpubReaderTheme) {
        preferences.edit()
            .putString(EPUB_READER_THEME_KEY, epubReaderThemeStorageValue(theme))
            .apply()
    }
}

internal fun epubReaderThemeStorageValue(theme: EpubReaderTheme): String = when (theme) {
    EpubReaderTheme.Light -> "light"
    EpubReaderTheme.Sepia -> "sepia"
    EpubReaderTheme.DarkerSepia -> "darker_sepia"
    EpubReaderTheme.Fern -> "fern"
    EpubReaderTheme.Arctic -> "arctic"
    EpubReaderTheme.Dark -> "dark"
    EpubReaderTheme.Midnight -> "midnight"
    EpubReaderTheme.AmberNight -> "amber_night"
    EpubReaderTheme.DarkMoss -> "dark_moss"
    EpubReaderTheme.OledBlack -> "oled_black"
}

internal fun epubReaderThemeFromStorage(value: String?): EpubReaderTheme = when (value?.trim()?.lowercase()) {
    "light" -> EpubReaderTheme.Light
    "darker_sepia" -> EpubReaderTheme.DarkerSepia
    "fern" -> EpubReaderTheme.Fern
    "arctic" -> EpubReaderTheme.Arctic
    "dark" -> EpubReaderTheme.Dark
    "midnight" -> EpubReaderTheme.Midnight
    "amber_night" -> EpubReaderTheme.AmberNight
    "dark_moss" -> EpubReaderTheme.DarkMoss
    "oled_black" -> EpubReaderTheme.OledBlack
    "sepia" -> EpubReaderTheme.Sepia
    else -> EpubReaderTheme.Sepia
}
