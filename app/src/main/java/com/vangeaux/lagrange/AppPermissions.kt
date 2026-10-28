package com.vangeaux.lagrange

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

internal data class AppPermissionSpec(
    val permission: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val settingsIntent: (Context) -> Intent
)

internal fun appPermissionSpecs(sdkInt: Int = Build.VERSION.SDK_INT): List<AppPermissionSpec> = buildList {
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        add(
            AppPermissionSpec(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                titleRes = R.string.settings_permission_notifications,
                summaryRes = R.string.settings_permission_notifications_summary,
                settingsIntent = { context ->
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                }
            )
        )
    }
    if (sdkInt <= Build.VERSION_CODES.P) {
        add(
            AppPermissionSpec(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                titleRes = R.string.settings_permission_storage,
                summaryRes = R.string.settings_permission_storage_summary,
                settingsIntent = ::applicationDetailsSettingsIntent
            )
        )
    }
}

internal fun isAppPermissionGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

internal fun applicationDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
