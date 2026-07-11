package com.bookorbit.android

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var firstComposeFrameReady = false
        splashScreen.setKeepOnScreenCondition { !firstComposeFrameReady }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val graph = AppGraph(this)

        setContent {
            val screen by graph.coordinator.screen.collectAsState()
            LaunchedEffect(Unit) {
                firstComposeFrameReady = true
                graph.coordinator.bootstrap()
            }
            BookOrbitTheme {
                BookOrbitApp(
                    screen = screen,
                    coordinator = graph.coordinator
                )
            }
        }
    }
}
