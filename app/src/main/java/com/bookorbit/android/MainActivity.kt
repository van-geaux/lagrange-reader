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

        val graph = AppGraph(this)
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
            LaunchedEffect(Unit) {
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
