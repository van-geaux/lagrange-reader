package com.vangeaux.lagrange

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesStoreTest {
    @Test
    fun `EPUB image minimum dimension defaults to 250 and normalizes to 25 pixel steps`() {
        assertEquals(250, AppPreferences().epubImageMinimumDimensionPx)
        assertEquals(0, normalizeEpubImageMinimumDimensionPx(-1))
        assertEquals(250, normalizeEpubImageMinimumDimensionPx(250))
        assertEquals(250, normalizeEpubImageMinimumDimensionPx(238))
        assertEquals(275, normalizeEpubImageMinimumDimensionPx(263))
        assertEquals(1000, normalizeEpubImageMinimumDimensionPx(1001))
    }

    @Test
    fun audioPlaybackSpeedStaysWithinTheSupportedGlobalOptions() {
        assertEquals(
            listOf(0.75f, 1f, 1.05f, 1.10f, 1.15f, 1.20f, 1.25f, 1.5f, 2f),
            AUDIO_PLAYBACK_SPEED_OPTIONS
        )
        assertEquals(1.5f, normalizeAudioPlaybackSpeed(1.5f))
        assertEquals(0.75f, normalizeAudioPlaybackSpeed(0.6f))
        assertEquals(1.05f, normalizeAudioPlaybackSpeed(1.04f))
        assertEquals(1.10f, normalizeAudioPlaybackSpeed(1.08f))
        assertEquals(1.15f, normalizeAudioPlaybackSpeed(1.13f))
        assertEquals(1.20f, normalizeAudioPlaybackSpeed(1.18f))
        assertEquals(2f, normalizeAudioPlaybackSpeed(2.5f))
    }

    @Test
    fun playbackSpeedLabelsAvoidFloatPrecisionNoise() {
        assertEquals("1", formatPlaybackSpeed(1.0))
        assertEquals("1.05", formatPlaybackSpeed(1.0499999523162842))
        assertEquals("1.1", formatPlaybackSpeed(1.100000023841858))
        assertEquals("1.15", formatPlaybackSpeed(1.149999976158142))
        assertEquals("1.2", formatPlaybackSpeed(1.2000000476837158))
        assertEquals("2", formatPlaybackSpeed(2.0))
    }

    @Test
    fun audioSkipIntervalsNormalizeToSupportedValues() {
        assertEquals(5, normalizeAudioSkipSeconds(1))
        assertEquals(10, normalizeAudioSkipSeconds(12))
        assertEquals(60, normalizeAudioSkipSeconds(90))
    }

    @Test
    fun audiobookSeekConfirmationDefaultsToEnabledAndRoundTrips() {
        assertEquals(true, AppPreferences().confirmAudiobookSeek)
        assertEquals(false, audiobookSeekConfirmationFromStorage(false))
        assertEquals(true, audiobookSeekConfirmationFromStorage(true))
        assertEquals(true, audiobookSeekConfirmationFromStorage(null))
    }

    @Test
    fun audioInterruptionPauseDefaultsToEnabledAndRoundTrips() {
        assertEquals(true, AppPreferences().pauseAudiobookForAudioInterruptions)
        assertEquals(false, audiobookAudioInterruptionPauseFromStorage(false))
        assertEquals(true, audiobookAudioInterruptionPauseFromStorage(true))
        assertEquals(true, audiobookAudioInterruptionPauseFromStorage(null))
    }

    @Test
    fun `stored app theme values round trip and invalid values follow system`() {
        AppThemeMode.values().forEach { value ->
            assertEquals(value, appThemeModeFromStorage(appThemeModeStorageValue(value)))
        }
        assertEquals(AppThemeMode.CHARCOAL, appThemeModeFromStorage(" DARK "))
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, appThemeModeFromStorage("unknown"))
        assertEquals(AppThemeMode.FOLLOW_SYSTEM, appThemeModeFromStorage(null))
    }

    @Test
    fun `stored opening screens round trip and invalid values open home`() {
        DefaultOpeningScreen.values().forEach { value ->
            assertEquals(
                value,
                defaultOpeningScreenFromStorage(defaultOpeningScreenStorageValue(value))
            )
        }
        assertEquals(
            DefaultOpeningScreen.LOCAL_BOOKS,
            defaultOpeningScreenFromStorage(" LOCAL_BOOKS ")
        )
        assertEquals(DefaultOpeningScreen.HOME, defaultOpeningScreenFromStorage("unknown"))
        assertEquals(DefaultOpeningScreen.HOME, defaultOpeningScreenFromStorage(null))
    }

    @Test
    fun `orientation preference restores the orientation captured when enabled`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationForLock(true, LockedOrientation.PORTRAIT)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            requestedOrientationForLock(true, LockedOrientation.LANDSCAPE)
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientationForLock(false))
    }

    @Test
    fun `enabling orientation lock captures the current app orientation`() {
        val previous = AppPreferences(lockOrientation = false)

        assertEquals(
            LockedOrientation.LANDSCAPE,
            preferencesForOrientationLockChange(
                previous = previous,
                updated = previous.copy(lockOrientation = true),
                currentConfigurationOrientation = Configuration.ORIENTATION_LANDSCAPE
            ).lockedOrientation
        )
        assertEquals(
            LockedOrientation.PORTRAIT,
            preferencesForOrientationLockChange(
                previous = previous,
                updated = previous.copy(lockOrientation = true),
                currentConfigurationOrientation = Configuration.ORIENTATION_PORTRAIT
            ).lockedOrientation
        )
    }

    @Test
    fun `stored orientation values round trip and legacy values default to portrait`() {
        LockedOrientation.values().forEach { value ->
            assertEquals(
                value,
                lockedOrientationFromStorage(lockedOrientationStorageValue(value))
            )
        }
        assertEquals(LockedOrientation.PORTRAIT, lockedOrientationFromStorage(null))
        assertEquals(LockedOrientation.PORTRAIT, lockedOrientationFromStorage("unknown"))
    }

    @Test
    fun `stored cellular policies round trip and default to confirmation`() {
        CellularDownloadPolicy.values().forEach { value ->
            assertEquals(
                value,
                cellularDownloadPolicyFromStorage(cellularDownloadPolicyStorageValue(value))
            )
        }
        assertEquals(
            CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
            cellularDownloadPolicyFromStorage("unknown")
        )
    }

    @Test
    fun `stored background policies round trip and default to wifi only`() {
        BackgroundRefreshNetworkPolicy.values().forEach { value ->
            assertEquals(
                value,
                backgroundRefreshNetworkPolicyFromStorage(
                    backgroundRefreshNetworkPolicyStorageValue(value)
                )
            )
        }
        assertEquals(
            BackgroundRefreshNetworkPolicy.WIFI_ONLY,
            backgroundRefreshNetworkPolicyFromStorage("unknown")
        )
    }

    @Test
    fun `offline cache library ids round trip in stable order and discard blanks`() {
        val stored = offlineCacheLibraryIdsStorageValue(setOf(" lib-secondary ", "", "lib-primary"))

        assertEquals("lib-primary\nlib-secondary", stored)
        assertEquals(
            setOf("lib-primary", "lib-secondary"),
            offlineCacheLibraryIdsFromStorage(stored)
        )
        assertEquals(emptySet<String>(), offlineCacheLibraryIdsFromStorage(null))
    }

    @Test
    fun `stored series grouping modes round trip and default to library`() {
        SeriesGroupingMode.values().forEach { value ->
            assertEquals(
                value,
                seriesGroupingModeFromStorage(seriesGroupingModeStorageValue(value))
            )
        }
        assertEquals(SeriesGroupingMode.LIBRARY, seriesGroupingModeFromStorage("unknown"))
        assertEquals(SeriesGroupingMode.LIBRARY, seriesGroupingModeFromStorage(null))
    }

    @Test
    fun `stored library card sizes round trip and default to small`() {
        LibraryCardSize.values().forEach { value ->
            assertEquals(value, libraryCardSizeFromStorage(libraryCardSizeStorageValue(value)))
        }
        assertEquals(LibraryCardSize.MEDIUM, libraryCardSizeFromStorage(" MEDIUM "))
        assertEquals(LibraryCardSize.SMALL, libraryCardSizeFromStorage("unknown"))
        assertEquals(LibraryCardSize.SMALL, libraryCardSizeFromStorage(null))
    }

    @Test
    fun `cellular policy decides whether to start ask or block`() {
        assertEquals(
            CellularDownloadDecision.START,
            cellularDownloadDecision(CellularDownloadPolicy.NEVER, isCellularOrMetered = false)
        )
        assertEquals(
            CellularDownloadDecision.START,
            cellularDownloadDecision(CellularDownloadPolicy.ALWAYS, isCellularOrMetered = true)
        )
        assertEquals(
            CellularDownloadDecision.ASK,
            cellularDownloadDecision(
                CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = true
            )
        )
        assertEquals(
            CellularDownloadDecision.BLOCK,
            cellularDownloadDecision(CellularDownloadPolicy.NEVER, isCellularOrMetered = true)
        )
    }

    @Test
    fun `background policy maps to work manager constraints`() {
        assertEquals(
            NetworkType.CONNECTED,
            backgroundRefreshNetworkType(BackgroundRefreshNetworkPolicy.ANY_NETWORK)
        )
        assertEquals(
            NetworkType.UNMETERED,
            backgroundRefreshNetworkType(BackgroundRefreshNetworkPolicy.WIFI_ONLY)
        )
        assertEquals(null, backgroundRefreshNetworkType(BackgroundRefreshNetworkPolicy.DISABLED))
    }
}
