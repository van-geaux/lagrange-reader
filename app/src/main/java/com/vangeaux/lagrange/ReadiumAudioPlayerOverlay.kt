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
    readerViewport: View,
    bindViewportSpace: Boolean = true
): View {
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
    if (bindViewportSpace) {
        bindReaderViewportAboveOverlay(readerViewport, playerView)
    }
    return playerView
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

internal fun readerViewportOverlayBottomMargin(
    visibleOverlayHeights: List<Int>,
    viewportBottomInset: Int
): Int = (
    (visibleOverlayHeights.maxOfOrNull { it.coerceAtLeast(0) } ?: 0) - viewportBottomInset.coerceAtLeast(0)
).coerceAtLeast(0)

internal fun bindReaderViewportAboveOverlays(
    readerViewport: View,
    overlays: List<View>
): () -> Unit {
    val update: () -> Unit = {
        val visibleOverlayHeights = overlays
            .filter { it.visibility == View.VISIBLE }
            .map(View::getHeight)
        val layoutParams = readerViewport.layoutParams as? FrameLayout.LayoutParams
        if (layoutParams == null) {
            Unit
        } else {
            val bottomMargin = readerViewportOverlayBottomMargin(
                visibleOverlayHeights = visibleOverlayHeights,
                viewportBottomInset = readerViewport.paddingBottom
            )
            if (layoutParams.bottomMargin != bottomMargin) {
                layoutParams.bottomMargin = bottomMargin
                readerViewport.layoutParams = layoutParams
            }
        }
    }
    overlays.forEach { overlay ->
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    }
    update()
    return update
}
