package com.bookorbit.android

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesStoreTest {
    @Test
    fun `stored app theme values round trip and invalid values follow system`() {
        AppThemeMode.values().forEach { value ->
            assertEquals(value, appThemeModeFromStorage(appThemeModeStorageValue(value)))
        }
        assertEquals(AppThemeMode.DARK, appThemeModeFromStorage(" DARK "))
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
}
