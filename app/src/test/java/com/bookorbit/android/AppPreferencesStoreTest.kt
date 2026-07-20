package com.bookorbit.android

import android.content.pm.ActivityInfo
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesStoreTest {
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
    fun `orientation preference locks the current orientation`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, requestedOrientationForLock(true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientationForLock(false))
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
