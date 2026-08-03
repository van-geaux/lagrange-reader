package com.vangeaux.lagrange

enum class ReaderTapZoneLayout(val displayName: String) {
    CURRENT_EDGES("Equal thirds"),
    VERTICAL_THIRDS("Vertical thirds"),
    KINDLE("Kindle"),
    L_SHAPE("L-shape"),
    EDGE("Edge"),
    MENU_ONLY("Menu only")
}

enum class ReaderTapZoneInvertMode(
    val displayName: String,
    val horizontal: Boolean,
    val vertical: Boolean
) {
    NONE("None", horizontal = false, vertical = false),
    HORIZONTAL("Horizontal", horizontal = true, vertical = false),
    VERTICAL("Vertical", horizontal = false, vertical = true),
    BOTH("Both", horizontal = true, vertical = true)
}

enum class ReaderTapZoneAction(val displayName: String) {
    PREVIOUS("Previous"),
    NEXT("Next"),
    MENU("Menu")
}

data class ReaderTapZoneRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class ReaderTapZoneRegion(
    val rect: ReaderTapZoneRect,
    val action: ReaderTapZoneAction
)

private fun readerTapZoneRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    action: ReaderTapZoneAction
): ReaderTapZoneRegion = ReaderTapZoneRegion(
    rect = ReaderTapZoneRect(x, y, width, height),
    action = action
)

private val CURRENT_EDGE_REGIONS = listOf(
    readerTapZoneRect(0f, 0f, 1f / 3f, 1f, ReaderTapZoneAction.PREVIOUS),
    readerTapZoneRect(1f / 3f, 0f, 1f / 3f, 1f, ReaderTapZoneAction.MENU),
    readerTapZoneRect(2f / 3f, 0f, 1f / 3f, 1f, ReaderTapZoneAction.NEXT)
)

private val VERTICAL_THIRDS_REGIONS = listOf(
    readerTapZoneRect(0f, 0f, 1f, 1f / 3f, ReaderTapZoneAction.PREVIOUS),
    readerTapZoneRect(0f, 1f / 3f, 1f, 1f / 3f, ReaderTapZoneAction.MENU),
    readerTapZoneRect(0f, 2f / 3f, 1f, 1f / 3f, ReaderTapZoneAction.NEXT)
)

private val KINDLE_REGIONS = listOf(
    readerTapZoneRect(0f, 0f, 1f, 1f / 3f, ReaderTapZoneAction.MENU),
    readerTapZoneRect(0f, 1f / 3f, 1f / 3f, 2f / 3f, ReaderTapZoneAction.PREVIOUS),
    readerTapZoneRect(1f / 3f, 1f / 3f, 2f / 3f, 2f / 3f, ReaderTapZoneAction.NEXT)
)

private val L_SHAPE_REGIONS = listOf(
    readerTapZoneRect(0f, 0f, 1f, 1f / 3f, ReaderTapZoneAction.PREVIOUS),
    readerTapZoneRect(0f, 1f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.PREVIOUS),
    readerTapZoneRect(1f / 3f, 1f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.MENU),
    readerTapZoneRect(2f / 3f, 1f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.NEXT),
    readerTapZoneRect(0f, 2f / 3f, 1f, 1f / 3f, ReaderTapZoneAction.NEXT)
)

private val EDGE_REGIONS = listOf(
    readerTapZoneRect(0f, 0f, 1f, 1f / 3f, ReaderTapZoneAction.NEXT),
    readerTapZoneRect(0f, 1f / 3f, 1f / 3f, 2f / 3f, ReaderTapZoneAction.NEXT),
    readerTapZoneRect(2f / 3f, 1f / 3f, 1f / 3f, 2f / 3f, ReaderTapZoneAction.NEXT),
    readerTapZoneRect(1f / 3f, 1f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.MENU),
    readerTapZoneRect(1f / 3f, 2f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.PREVIOUS),
    readerTapZoneRect(0f, 2f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.MENU),
    readerTapZoneRect(2f / 3f, 2f / 3f, 1f / 3f, 1f / 3f, ReaderTapZoneAction.MENU)
)

private val MENU_ONLY_REGIONS = listOf(
    readerTapZoneRect(0f, 0f, 1f, 1f, ReaderTapZoneAction.MENU)
)

private fun baseReaderTapZoneRegions(layout: ReaderTapZoneLayout): List<ReaderTapZoneRegion> = when (layout) {
    ReaderTapZoneLayout.CURRENT_EDGES -> CURRENT_EDGE_REGIONS
    ReaderTapZoneLayout.VERTICAL_THIRDS -> VERTICAL_THIRDS_REGIONS
    ReaderTapZoneLayout.KINDLE -> KINDLE_REGIONS
    ReaderTapZoneLayout.L_SHAPE -> L_SHAPE_REGIONS
    ReaderTapZoneLayout.EDGE -> EDGE_REGIONS
    ReaderTapZoneLayout.MENU_ONLY -> MENU_ONLY_REGIONS
}

private fun transformReaderTapZoneRect(
    rect: ReaderTapZoneRect,
    flipHorizontal: Boolean,
    flipVertical: Boolean
): ReaderTapZoneRect = rect.copy(
    x = if (flipHorizontal) 1f - (rect.x + rect.width) else rect.x,
    y = if (flipVertical) 1f - (rect.y + rect.height) else rect.y
)

internal fun readerTapZoneRegions(
    layout: ReaderTapZoneLayout,
    readingDirection: LibraryReadingDirection,
    invertMode: ReaderTapZoneInvertMode
): List<ReaderTapZoneRegion> {
    val flipHorizontal =
        (readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT) xor invertMode.horizontal
    return baseReaderTapZoneRegions(layout).map { region ->
        region.copy(
            rect = transformReaderTapZoneRect(
                rect = region.rect,
                flipHorizontal = flipHorizontal,
                flipVertical = invertMode.vertical
            )
        )
    }
}

private fun containsCoordinate(value: Float, start: Float, size: Float): Boolean {
    val end = start + size
    return value >= start && (value < end || (value == 1f && end >= 1f))
}

internal fun readerTapZoneAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    layout: ReaderTapZoneLayout,
    readingDirection: LibraryReadingDirection,
    invertMode: ReaderTapZoneInvertMode
): ReaderTapZoneAction {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
        return ReaderTapZoneAction.MENU
    }
    val normalizedX = (x / width).coerceIn(0f, 1f)
    val normalizedY = (y / height).coerceIn(0f, 1f)
    return readerTapZoneRegions(layout, readingDirection, invertMode)
        .firstOrNull { region ->
            val rect = region.rect
            containsCoordinate(normalizedX, rect.x, rect.width) &&
                containsCoordinate(normalizedY, rect.y, rect.height)
        }
        ?.action
        ?: ReaderTapZoneAction.MENU
}

internal fun readerTapZonePreferencesChanged(
    previous: LibraryReaderPreferences,
    next: LibraryReaderPreferences
): Boolean = previous.tapZoneLayout != next.tapZoneLayout ||
    previous.tapZoneInvertMode != next.tapZoneInvertMode

internal fun readerTapZoneLayoutStorageValue(value: ReaderTapZoneLayout): String =
    value.name.lowercase()

internal fun readerTapZoneLayoutFromStorage(value: String?): ReaderTapZoneLayout = when (
    value?.trim()?.lowercase()
) {
    "kindle" -> ReaderTapZoneLayout.KINDLE
    "vertical_thirds" -> ReaderTapZoneLayout.VERTICAL_THIRDS
    "l_shape" -> ReaderTapZoneLayout.L_SHAPE
    "edge" -> ReaderTapZoneLayout.EDGE
    "menu_only" -> ReaderTapZoneLayout.MENU_ONLY
    else -> ReaderTapZoneLayout.CURRENT_EDGES
}

internal fun readerTapZoneInvertModeStorageValue(value: ReaderTapZoneInvertMode): String =
    value.name.lowercase()

internal fun readerTapZoneInvertModeFromStorage(value: String?): ReaderTapZoneInvertMode = when (
    value?.trim()?.lowercase()
) {
    "horizontal" -> ReaderTapZoneInvertMode.HORIZONTAL
    "vertical" -> ReaderTapZoneInvertMode.VERTICAL
    "both" -> ReaderTapZoneInvertMode.BOTH
    else -> ReaderTapZoneInvertMode.NONE
}
