package com.bookorbit.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val graph = AppGraph(this)

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
