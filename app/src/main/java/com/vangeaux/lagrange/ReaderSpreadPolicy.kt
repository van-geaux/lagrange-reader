package com.vangeaux.lagrange

import android.content.res.Configuration
import androidx.annotation.StringRes

enum class ReaderSpreadMode(val displayName: String, @StringRes val displayNameRes: Int) {
    AUTO("Automatic", R.string.label_spread_auto),
    OFF("Off", R.string.label_spread_off)
}

/** Smallest window side, in dp, that identifies an unfolded inner display rather than a cover screen. */
internal const val READER_SPREAD_MIN_SHORT_SIDE_DP = 600

enum class ReaderHingeOrientation { VERTICAL, HORIZONTAL }

/** Provider-neutral projection of a Jetpack WindowManager `FoldingFeature`, in window pixels. */
internal data class ReaderHinge(
    val orientation: ReaderHingeOrientation,
    val isHalfOpened: Boolean,
    val isSeparating: Boolean,
    val leftPx: Int,
    val rightPx: Int
)

internal data class ReaderWindowState(
    val widthDp: Int,
    val heightDp: Int,
    val hinge: ReaderHinge? = null
) {
    val isHorizontal: Boolean get() = widthDp > heightDp
    val shortSideDp: Int get() = minOf(widthDp, heightDp)

    companion object {
        fun fromConfiguration(configuration: Configuration, hinge: ReaderHinge? = null) =
            ReaderWindowState(
                widthDp = configuration.screenWidthDp,
                heightDp = configuration.screenHeightDp,
                hinge = hinge
            )
    }
}

/**
 * Two-page spreads are shown only on a large (unfolded) window held horizontally, in paginated
 * layout. Window size, not display size, is used so split-screen falls back to one page. A
 * half-opened horizontal hinge (tabletop posture) keeps one page because the spread would straddle
 * the fold.
 */
internal fun shouldUseTwoPageSpread(
    mode: ReaderSpreadMode,
    window: ReaderWindowState,
    layoutMode: ReaderLayoutMode
): Boolean {
    if (mode == ReaderSpreadMode.OFF) return false
    if (layoutMode != ReaderLayoutMode.PAGINATED) return false
    if (window.widthDp <= 0 || window.heightDp <= 0) return false
    if (window.shortSideDp < READER_SPREAD_MIN_SHORT_SIDE_DP) return false
    if (!window.isHorizontal) return false
    val hinge = window.hinge
    if (hinge != null && hinge.orientation == ReaderHingeOrientation.HORIZONTAL && hinge.isHalfOpened) {
        return false
    }
    return true
}

internal fun readerSpreadModeStorageValue(value: ReaderSpreadMode): String = value.name.lowercase()

internal fun readerSpreadModeFromStorage(value: String?): ReaderSpreadMode =
    if (value?.trim()?.lowercase() == "off") ReaderSpreadMode.OFF else ReaderSpreadMode.AUTO
