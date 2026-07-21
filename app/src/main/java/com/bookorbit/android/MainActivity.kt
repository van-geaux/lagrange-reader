package com.bookorbit.android

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider

internal fun requestedOrientationForLock(
    enabled: Boolean,
    lockedOrientation: LockedOrientation = LockedOrientation.PORTRAIT
): Int = when {
    !enabled -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    lockedOrientation == LockedOrientation.LANDSCAPE ->
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

internal fun preferencesForOrientationLockChange(
    previous: AppPreferences,
    updated: AppPreferences,
    currentConfigurationOrientation: Int
): AppPreferences {
    if (!updated.lockOrientation || previous.lockOrientation) return updated
    val lockedOrientation = if (
        currentConfigurationOrientation == Configuration.ORIENTATION_LANDSCAPE
    ) {
        LockedOrientation.LANDSCAPE
    } else {
        LockedOrientation.PORTRAIT
    }
    return updated.copy(lockedOrientation = lockedOrientation)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val graph = AppGraph(this)
        val preferencesStore = AppPreferencesStore(this)
        val initialPreferences = preferencesStore.read()
        requestedOrientation = requestedOrientationForLock(
            enabled = initialPreferences.lockOrientation,
            lockedOrientation = initialPreferences.lockedOrientation
        )
        val audioPlaybackController =
            (application as BookOrbitApplication).audioPlaybackController
        graph.configureAudioPlayback(audioPlaybackController)
        audioPlaybackController.setProgressListener(graph.coordinator::onAudioPlaybackProgress)
        audioPlaybackController.setCoverLoader(graph.coordinator::loadBookCover)
        splashScreen.setKeepOnScreenCondition {
            graph.coordinator.screen.value is AppScreen.Loading
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val screen by graph.coordinator.screen.collectAsState()
            var appPreferences by remember { mutableStateOf(initialPreferences) }
            LaunchedEffect(Unit) {
                graph.coordinator.bootstrap()
            }
            LaunchedEffect(
                appPreferences.lockOrientation,
                appPreferences.lockedOrientation
            ) {
                requestedOrientation = requestedOrientationForLock(
                    enabled = appPreferences.lockOrientation,
                    lockedOrientation = appPreferences.lockedOrientation
                )
            }
            CompositionLocalProvider(
                LocalReduceMotion provides appPreferences.reduceMotion
            ) {
                BookOrbitTheme(themeMode = appPreferences.themeMode) {
                    BookOrbitApp(
                        screen = screen,
                        coordinator = graph.coordinator,
                        audioPlaybackController = audioPlaybackController,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = { updated ->
                            val persisted = preferencesForOrientationLockChange(
                                previous = appPreferences,
                                updated = updated,
                                currentConfigurationOrientation =
                                    resources.configuration.orientation
                            )
                            val refreshPolicyChanged =
                                persisted.backgroundRefreshNetworkPolicy !=
                                    appPreferences.backgroundRefreshNetworkPolicy
                            preferencesStore.save(persisted)
                            appPreferences = persisted
                            if (refreshPolicyChanged) {
                                graph.coordinator.reconfigureBackgroundRefresh()
                            }
                        }
                    )
                }
            }
        }
    }
}
