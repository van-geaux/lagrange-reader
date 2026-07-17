package com.bookorbit.android

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

internal fun requestedOrientationForLock(enabled: Boolean): Int = if (enabled) {
    ActivityInfo.SCREEN_ORIENTATION_LOCKED
} else {
    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

private object DisabledHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

internal fun hapticFeedbackConstantForSdk(sdkInt: Int): Int = if (sdkInt >= Build.VERSION_CODES.R) {
    HapticFeedbackConstants.CONFIRM
} else {
    HapticFeedbackConstants.VIRTUAL_KEY
}

internal fun shouldConfirmHapticEnable(current: Boolean, updated: Boolean): Boolean = updated && !current

private class PerceptibleHapticFeedback(private val view: View) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        view.performHapticFeedback(hapticFeedbackConstantForSdk(Build.VERSION.SDK_INT))
    }
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
            val view = LocalView.current
            val perceptibleHaptics = remember(view) { PerceptibleHapticFeedback(view) }
            CompositionLocalProvider(
                LocalHapticFeedback provides if (appPreferences.hapticFeedback) {
                    perceptibleHaptics
                } else {
                    DisabledHapticFeedback
                },
                LocalReduceMotion provides appPreferences.reduceMotion
            ) {
                BookOrbitTheme(themeMode = appPreferences.themeMode) {
                    BookOrbitApp(
                        screen = screen,
                        coordinator = graph.coordinator,
                        appPreferences = appPreferences,
                        onAppPreferencesChange = { updated ->
                            if (shouldConfirmHapticEnable(appPreferences.hapticFeedback, updated.hapticFeedback)) {
                                perceptibleHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
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
