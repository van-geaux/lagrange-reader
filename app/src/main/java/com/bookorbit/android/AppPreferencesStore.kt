package com.bookorbit.android

import android.content.Context

enum class AppThemeMode(val displayName: String) {
    FOLLOW_SYSTEM("Follow system"),
    LIGHT("Light"),
    CHARCOAL("Charcoal"),
    WARM_BLACK("Warm black"),
    OLED_BLACK("OLED black")
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

enum class SeriesGroupingMode {
    NONE,
    LIBRARY,
    FORMAT
}

enum class LibraryCardSize(val displayName: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large")
}

enum class LockedOrientation {
    PORTRAIT,
    LANDSCAPE
}

data class AppPreferences(
    val lockOrientation: Boolean = false,
    val lockedOrientation: LockedOrientation = LockedOrientation.PORTRAIT,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val defaultOpeningScreen: DefaultOpeningScreen = DefaultOpeningScreen.HOME,
    val reduceMotion: Boolean = false,
    val cellularDownloadPolicy: CellularDownloadPolicy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
    val backgroundRefreshNetworkPolicy: BackgroundRefreshNetworkPolicy =
        BackgroundRefreshNetworkPolicy.WIFI_ONLY,
    val confirmDeleteLocalCopy: Boolean = true,
    val seriesGroupingMode: SeriesGroupingMode = SeriesGroupingMode.LIBRARY,
    val libraryCardSize: LibraryCardSize = LibraryCardSize.SMALL,
    val libraryReaderPreferences: Map<String, LibraryReaderPreferences> = emptyMap()
)

internal class AppPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        APP_PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )

    fun read(): AppPreferences = AppPreferences(
        lockOrientation = preferences.getBoolean(LOCK_ORIENTATION_KEY, false),
        lockedOrientation = lockedOrientationFromStorage(
            preferences.getString(LOCKED_ORIENTATION_KEY, null)
        ),
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
        confirmDeleteLocalCopy = preferences.getBoolean(CONFIRM_DELETE_LOCAL_COPY_KEY, true),
        seriesGroupingMode = seriesGroupingModeFromStorage(
            preferences.getString(SERIES_GROUPING_MODE_KEY, null)
        ),
        libraryCardSize = libraryCardSizeFromStorage(
            preferences.getString(LIBRARY_CARD_SIZE_KEY, null)
        ),
        libraryReaderPreferences = libraryReaderPreferencesFromStorage(
            preferences.getString(LIBRARY_READER_PREFERENCES_KEY, null)
        )
    )

    fun save(value: AppPreferences) {
        preferences.edit()
            .putBoolean(LOCK_ORIENTATION_KEY, value.lockOrientation)
            .putString(
                LOCKED_ORIENTATION_KEY,
                lockedOrientationStorageValue(value.lockedOrientation)
            )
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
            .putString(
                SERIES_GROUPING_MODE_KEY,
                seriesGroupingModeStorageValue(value.seriesGroupingMode)
            )
            .putString(
                LIBRARY_CARD_SIZE_KEY,
                libraryCardSizeStorageValue(value.libraryCardSize)
            )
            .putString(
                LIBRARY_READER_PREFERENCES_KEY,
                libraryReaderPreferencesStorageValue(value.libraryReaderPreferences)
            )
            .apply()
    }

    fun readAudioPlaybackSpeed(): Float =
        normalizeAudioPlaybackSpeed(preferences.getFloat(AUDIO_PLAYBACK_SPEED_KEY, 1f))

    fun saveAudioPlaybackSpeed(value: Float) {
        preferences.edit()
            .putFloat(AUDIO_PLAYBACK_SPEED_KEY, normalizeAudioPlaybackSpeed(value))
            .apply()
    }

    fun readIgnoredReleaseTag(): String? = preferences.getString(IGNORED_RELEASE_TAG_KEY, null)

    fun saveIgnoredReleaseTag(value: String) {
        preferences.edit()
            .putString(IGNORED_RELEASE_TAG_KEY, value)
            .apply()
    }

    private companion object {
        const val APP_PREFERENCES_FILE = "app_preferences"
        const val LOCK_ORIENTATION_KEY = "lock_orientation"
        const val LOCKED_ORIENTATION_KEY = "locked_orientation"
        const val THEME_MODE_KEY = "theme_mode"
        const val DEFAULT_OPENING_SCREEN_KEY = "default_opening_screen"
        const val REDUCE_MOTION_KEY = "reduce_motion"
        const val CELLULAR_DOWNLOAD_POLICY_KEY = "cellular_download_policy"
        const val BACKGROUND_REFRESH_NETWORK_POLICY_KEY = "background_refresh_network_policy"
        const val CONFIRM_DELETE_LOCAL_COPY_KEY = "confirm_delete_local_copy"
        const val SERIES_GROUPING_MODE_KEY = "series_grouping_mode"
        const val LIBRARY_CARD_SIZE_KEY = "library_card_size"
        const val LIBRARY_READER_PREFERENCES_KEY = "library_reader_preferences"
        const val AUDIO_PLAYBACK_SPEED_KEY = "audio_playback_speed"
        const val IGNORED_RELEASE_TAG_KEY = "ignored_release_tag"
    }
}

internal fun normalizeAudioPlaybackSpeed(value: Float): Float =
    AUDIO_PLAYBACK_SPEED_OPTIONS
        .minByOrNull { option -> kotlin.math.abs(option - value) }
        ?: 1f

internal val AUDIO_PLAYBACK_SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun lockedOrientationStorageValue(value: LockedOrientation): String =
    value.name.lowercase()

internal fun lockedOrientationFromStorage(value: String?): LockedOrientation = when (
    value?.trim()?.lowercase()
) {
    "landscape" -> LockedOrientation.LANDSCAPE
    else -> LockedOrientation.PORTRAIT
}

internal fun appThemeModeStorageValue(value: AppThemeMode): String = value.name.lowercase()

internal fun appThemeModeFromStorage(value: String?): AppThemeMode = when (value?.trim()?.lowercase()) {
    "light" -> AppThemeMode.LIGHT
    "dark", "charcoal" -> AppThemeMode.CHARCOAL
    "warm_black" -> AppThemeMode.WARM_BLACK
    "oled_black" -> AppThemeMode.OLED_BLACK
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

internal fun seriesGroupingModeStorageValue(value: SeriesGroupingMode): String =
    value.name.lowercase()

internal fun seriesGroupingModeFromStorage(value: String?): SeriesGroupingMode = when (
    value?.trim()?.lowercase()
) {
    "none" -> SeriesGroupingMode.NONE
    "format" -> SeriesGroupingMode.FORMAT
    else -> SeriesGroupingMode.LIBRARY
}

internal fun libraryCardSizeStorageValue(value: LibraryCardSize): String = value.name.lowercase()

internal fun libraryCardSizeFromStorage(value: String?): LibraryCardSize = when (
    value?.trim()?.lowercase()
) {
    "medium" -> LibraryCardSize.MEDIUM
    "large" -> LibraryCardSize.LARGE
    else -> LibraryCardSize.SMALL
}
