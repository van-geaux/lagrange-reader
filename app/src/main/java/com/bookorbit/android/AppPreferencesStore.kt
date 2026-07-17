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

enum class CellularDownloadPolicy(val displayName: String) {
    ALWAYS("Always"),
    NEVER("Never"),
    ASK_FOR_CONFIRMATION("Ask for confirmation")
}

enum class BackgroundRefreshNetworkPolicy(val displayName: String) {
    ANY_NETWORK("Any network"),
    WIFI_ONLY("Wi-Fi only"),
    DISABLED("Disabled")
}

data class AppPreferences(
    val lockOrientation: Boolean = false,
    val hapticFeedback: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val defaultOpeningScreen: DefaultOpeningScreen = DefaultOpeningScreen.HOME,
    val reduceMotion: Boolean = false,
    val cellularDownloadPolicy: CellularDownloadPolicy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
    val backgroundRefreshNetworkPolicy: BackgroundRefreshNetworkPolicy =
        BackgroundRefreshNetworkPolicy.WIFI_ONLY,
    val confirmDeleteLocalCopy: Boolean = true
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
        reduceMotion = preferences.getBoolean(REDUCE_MOTION_KEY, false),
        cellularDownloadPolicy = cellularDownloadPolicyFromStorage(
            preferences.getString(CELLULAR_DOWNLOAD_POLICY_KEY, null)
        ),
        backgroundRefreshNetworkPolicy = backgroundRefreshNetworkPolicyFromStorage(
            preferences.getString(BACKGROUND_REFRESH_NETWORK_POLICY_KEY, null)
        ),
        confirmDeleteLocalCopy = preferences.getBoolean(CONFIRM_DELETE_LOCAL_COPY_KEY, true)
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
            .putString(
                CELLULAR_DOWNLOAD_POLICY_KEY,
                cellularDownloadPolicyStorageValue(value.cellularDownloadPolicy)
            )
            .putString(
                BACKGROUND_REFRESH_NETWORK_POLICY_KEY,
                backgroundRefreshNetworkPolicyStorageValue(value.backgroundRefreshNetworkPolicy)
            )
            .putBoolean(CONFIRM_DELETE_LOCAL_COPY_KEY, value.confirmDeleteLocalCopy)
            .apply()
    }

    private companion object {
        const val APP_PREFERENCES_FILE = "app_preferences"
        const val LOCK_ORIENTATION_KEY = "lock_orientation"
        const val HAPTIC_FEEDBACK_KEY = "haptic_feedback"
        const val THEME_MODE_KEY = "theme_mode"
        const val DEFAULT_OPENING_SCREEN_KEY = "default_opening_screen"
        const val REDUCE_MOTION_KEY = "reduce_motion"
        const val CELLULAR_DOWNLOAD_POLICY_KEY = "cellular_download_policy"
        const val BACKGROUND_REFRESH_NETWORK_POLICY_KEY = "background_refresh_network_policy"
        const val CONFIRM_DELETE_LOCAL_COPY_KEY = "confirm_delete_local_copy"
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

internal fun cellularDownloadPolicyStorageValue(value: CellularDownloadPolicy): String =
    value.name.lowercase()

internal fun cellularDownloadPolicyFromStorage(value: String?): CellularDownloadPolicy = when (
    value?.trim()?.lowercase()
) {
    "always" -> CellularDownloadPolicy.ALWAYS
    "never" -> CellularDownloadPolicy.NEVER
    else -> CellularDownloadPolicy.ASK_FOR_CONFIRMATION
}

internal fun backgroundRefreshNetworkPolicyStorageValue(
    value: BackgroundRefreshNetworkPolicy
): String = value.name.lowercase()

internal fun backgroundRefreshNetworkPolicyFromStorage(
    value: String?
): BackgroundRefreshNetworkPolicy = when (value?.trim()?.lowercase()) {
    "any_network" -> BackgroundRefreshNetworkPolicy.ANY_NETWORK
    "disabled" -> BackgroundRefreshNetworkPolicy.DISABLED
    else -> BackgroundRefreshNetworkPolicy.WIFI_ONLY
}
