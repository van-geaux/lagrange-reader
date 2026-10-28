package com.vangeaux.lagrange

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPermissionsTest {

    @Test
    fun permissionSpecsExposeNotificationsOnAndroid13AndLater() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            appPermissionSpecs(Build.VERSION_CODES.TIRAMISU).map { it.permission }
        )
    }

    @Test
    fun permissionSpecsExposeLegacyStorageOnlyThroughAndroid9() {
        assertEquals(
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            appPermissionSpecs(Build.VERSION_CODES.P).map { it.permission }
        )
        assertEquals(emptyList<AppPermissionSpec>(), appPermissionSpecs(Build.VERSION_CODES.Q))
    }
}
