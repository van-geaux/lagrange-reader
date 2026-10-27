package com.vangeaux.lagrange

import android.content.res.Configuration

enum class ReaderSpreadMode(val displayName: String) {
    AUTO("Automatic"),
    OFF("Off")
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

/** The hinge relative to a reader whose left edge is [readerLeftPx] from the window's left edge. */
internal fun ReaderHinge.inReaderCoordinates(readerLeftPx: Int): ReaderHinge =
    if (readerLeftPx == 0) this else copy(leftPx = leftPx - readerLeftPx, rightPx = rightPx - readerLeftPx)

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

/**
 * Horizontal pixel span reserved between the two pages of a spread. A separating vertical hinge
 * (half-opened book posture or a dual-screen device) is avoided exactly; otherwise the gutter is
 * centred on the window with [minimumGapPx] width.
 */
internal fun readerSpreadGutterPx(
    windowWidthPx: Int,
    hinge: ReaderHinge?,
    minimumGapPx: Int
): IntRange {
    val width = windowWidthPx.coerceAtLeast(0)
    val gap = minimumGapPx.coerceIn(0, width)
    if (
        hinge != null &&
        hinge.orientation == ReaderHingeOrientation.VERTICAL &&
        hinge.isSeparating &&
        hinge.rightPx > hinge.leftPx &&
        hinge.leftPx > 0 &&
        hinge.rightPx < width
    ) {
        val hingeWidth = hinge.rightPx - hinge.leftPx
        if (hingeWidth >= gap) return hinge.leftPx until hinge.rightPx
        val centre = (hinge.leftPx + hinge.rightPx) / 2
        return (centre - gap / 2) until (centre - gap / 2 + gap)
    }
    val start = (width - gap) / 2
    return start until start + gap
}

internal fun readerSpreadModeStorageValue(value: ReaderSpreadMode): String = value.name.lowercase()

internal fun readerSpreadModeFromStorage(value: String?): ReaderSpreadMode =
    if (value?.trim()?.lowercase() == "off") ReaderSpreadMode.OFF else ReaderSpreadMode.AUTO
