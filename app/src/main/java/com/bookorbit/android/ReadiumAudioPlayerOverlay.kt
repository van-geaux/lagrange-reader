package com.bookorbit.android

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity

/** Keeps the compact audiobook controls visible above Readium's separate reader activities. */
internal fun FragmentActivity.addReadiumAudioPlayerOverlay(root: FrameLayout) {
    val controller = (application as BookOrbitApplication).audioPlaybackController
    val themeMode = AppPreferencesStore(this).read().themeMode
    val playerView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            BookOrbitTheme(themeMode = themeMode) {
                ReadiumCompactAudioPlayer(controller = controller)
            }
        }
    }
    root.addView(
        playerView,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
    )
}
