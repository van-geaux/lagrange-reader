package com.bookorbit.android

import android.content.Context

enum class AppThemeMode(val displayName: String) {
    FOLLOW_SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class DefaultOpeningScreen(val displayName: String) {
    HOME("Home"),
    LIBRARY("Library"),
    LOCAL_BOOKS("Local books")
}

data class AppPreferences(
    val lockOrientation: Boolean = false,
    val hapticFeedback: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val defaultOpeningScreen: DefaultOpeningScreen = DefaultOpeningScreen.HOME,
    val reduceMotion: Boolean = false
)

internal class AppPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        APP_PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )

    fun read(): AppPreferences = AppPreferences(
        lockOrientation = preferences.getBoolean(LOCK_ORIENTATION_KEY, false),
        hapticFeedback = preferences.getBoolean(HAPTIC_FEEDBACK_KEY, true),
        themeMode = appThemeModeFromStorage(preferences.getString(THEME_MODE_KEY, null)),
        defaultOpeningScreen = defaultOpeningScreenFromStorage(
            preferences.getString(DEFAULT_OPENING_SCREEN_KEY, null)
        ),
        reduceMotion = preferences.getBoolean(REDUCE_MOTION_KEY, false)
    )

    fun save(value: AppPreferences) {
        preferences.edit()
            .putBoolean(LOCK_ORIENTATION_KEY, value.lockOrientation)
            .putBoolean(HAPTIC_FEEDBACK_KEY, value.hapticFeedback)
            .putString(THEME_MODE_KEY, appThemeModeStorageValue(value.themeMode))
            .putString(
                DEFAULT_OPENING_SCREEN_KEY,
                defaultOpeningScreenStorageValue(value.defaultOpeningScreen)
            )
            .putBoolean(REDUCE_MOTION_KEY, value.reduceMotion)
            .apply()
    }

    private companion object {
        const val APP_PREFERENCES_FILE = "app_preferences"
        const val LOCK_ORIENTATION_KEY = "lock_orientation"
        const val HAPTIC_FEEDBACK_KEY = "haptic_feedback"
        const val THEME_MODE_KEY = "theme_mode"
        const val DEFAULT_OPENING_SCREEN_KEY = "default_opening_screen"
        const val REDUCE_MOTION_KEY = "reduce_motion"
    }
}

internal fun appThemeModeStorageValue(value: AppThemeMode): String = value.name.lowercase()

internal fun appThemeModeFromStorage(value: String?): AppThemeMode = when (value?.trim()?.lowercase()) {
    "light" -> AppThemeMode.LIGHT
    "dark" -> AppThemeMode.DARK
    else -> AppThemeMode.FOLLOW_SYSTEM
}

internal fun defaultOpeningScreenStorageValue(value: DefaultOpeningScreen): String = value.name.lowercase()

internal fun defaultOpeningScreenFromStorage(value: String?): DefaultOpeningScreen = when (
    value?.trim()?.lowercase()
) {
    "library" -> DefaultOpeningScreen.LIBRARY
    "local_books" -> DefaultOpeningScreen.LOCAL_BOOKS
    else -> DefaultOpeningScreen.HOME
}
