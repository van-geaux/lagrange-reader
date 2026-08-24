package com.vangeaux.lagrange

import android.view.KeyEvent

internal enum class VolumeButtonNavigationAction {
    PREVIOUS,
    NEXT
}

internal fun volumeButtonNavigationAction(keyCode: Int): VolumeButtonNavigationAction? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeButtonNavigationAction.PREVIOUS
    KeyEvent.KEYCODE_VOLUME_UP -> VolumeButtonNavigationAction.NEXT
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
