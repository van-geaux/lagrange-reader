package com.bookorbit.android

import android.content.pm.ActivityInfo
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

internal fun requestedOrientationForLock(enabled: Boolean): Int = if (enabled) {
    ActivityInfo.SCREEN_ORIENTATION_LOCKED
} else {
    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val graph = AppGraph(this)
        val preferencesStore = AppPreferencesStore(this)
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
            var appPreferences by remember { mutableStateOf(preferencesStore.read()) }
            LaunchedEffect(Unit) {
                graph.coordinator.bootstrap()
            }
            LaunchedEffect(appPreferences.lockOrientation) {
                requestedOrientation = requestedOrientationForLock(appPreferences.lockOrientation)
            }
            CompositionLocalProvider(
                LocalReduceMotion provides appPreferences.reduceMotion
            ) {
                BookOrbitTheme(themeMode = appPreferences.themeMode) {
                    BookOrbitApp(
                        screen = screen,
                        coordinator = graph.coordinator,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = { updated ->
                            val refreshPolicyChanged =
                                updated.backgroundRefreshNetworkPolicy !=
                                    appPreferences.backgroundRefreshNetworkPolicy
                            preferencesStore.save(updated)
                            appPreferences = updated
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
