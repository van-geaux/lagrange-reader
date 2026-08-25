package com.vangeaux.lagrange

import android.view.KeyEvent

internal enum class VolumeButtonNavigationAction {
    PREVIOUS,
    NEXT
}

internal fun volumeButtonNavigationEnabled(
    readerEnabled: Boolean,
    audiobookSessionActive: Boolean
): Boolean = readerEnabled && !audiobookSessionActive

internal fun volumeButtonNavigationAction(
    keyCode: Int,
    reverse: Boolean = false
): VolumeButtonNavigationAction? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverse) {
        VolumeButtonNavigationAction.PREVIOUS
    } else {
        VolumeButtonNavigationAction.NEXT
    }
    KeyEvent.KEYCODE_VOLUME_UP -> if (reverse) {
        VolumeButtonNavigationAction.NEXT
    } else {
        VolumeButtonNavigationAction.PREVIOUS
    }
    else -> null
}

internal fun volumeButtonNavigationTarget(
    action: VolumeButtonNavigationAction,
    currentIndex: Int,
    pageCount: Int
): Int? {
    val lastIndex = pageCount - 1
    if (currentIndex !in 0..lastIndex) return null
    val target = when (action) {
        VolumeButtonNavigationAction.PREVIOUS -> currentIndex - 1
        VolumeButtonNavigationAction.NEXT -> currentIndex + 1
    }
    return target.takeIf { it in 0..lastIndex }
}
