package com.vangeaux.lagrange

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Keeps the compact audiobook controls visible above Readium's separate reader activities. */
internal fun FragmentActivity.addReadiumAudioPlayerOverlay(
    root: FrameLayout,
    readerViewport: View
) {
    val controller = (application as BookOrbitApplication).audioPlaybackController
    val preferenceStore = AppPreferencesStore(this)
    val storedPreferences = preferenceStore.read()
    val themeMode = storedPreferences.themeMode
    val playerView = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            BookOrbitTheme(themeMode = themeMode) {
                ReadiumCompactAudioPlayer(
                    controller = controller,
                    confirmAudiobookSeek = storedPreferences.confirmAudiobookSeek,
                    onConfirmAudiobookSeekChange = { enabled ->
                        preferenceStore.save(storedPreferences.copy(confirmAudiobookSeek = enabled))
                    },
                    onClosed = { _, _ ->
                        lifecycleScope.launch {
                            BookOrbitRepository(applicationContext).clearActiveReader()
                        }
                    },
                    onCoverClick = { book ->
                        controller.requestFullPlayer(book)
                        finish()
                    }
                )
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
    bindReaderViewportAboveOverlay(readerViewport, playerView)
}

internal fun bindReaderViewportAboveOverlay(readerViewport: View, overlay: View) {
    overlay.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
        val playerHeight = if (view.visibility == View.VISIBLE) bottom - top else 0
        val layoutParams = readerViewport.layoutParams as? FrameLayout.LayoutParams
            ?: return@addOnLayoutChangeListener
        val bottomMargin = playerHeight.coerceAtLeast(0)
        if (layoutParams.bottomMargin != bottomMargin) {
            layoutParams.bottomMargin = bottomMargin
            readerViewport.layoutParams = layoutParams
        }
    }
}
