package com.vangeaux.lagrange

/** Pages at or above this width/height ratio are double-page art and are always shown alone. */
internal const val COMIC_WIDE_PAGE_MIN_ASPECT = 1.0f

/**
 * One screen of a two-page comic spread. [pages] are reading-order page indexes in ascending
 * order: a single page, or two consecutive pages.
 */
internal data class ComicSpread(val pages: List<Int>) {
    init {
        require(pages.size in 1..2) { "A spread holds one or two pages" }
        require(pages.size == 1 || pages[1] == pages[0] + 1) { "Spread pages must be consecutive" }
    }

    val firstPage: Int get() = pages.first()
    val lastPage: Int get() = pages.last()
    val isPair: Boolean get() = pages.size == 2

    operator fun contains(page: Int): Boolean = page in pages
}

internal fun isWideComicPage(aspectRatio: Float?): Boolean =
    aspectRatio != null && aspectRatio.isFinite() && aspectRatio >= COMIC_WIDE_PAGE_MIN_ASPECT

/**
 * Pairs comic pages like a printed book.
 *
 * - [coverAlone] keeps page 1 by itself so pages 2–3, 4–5… face each other.
 * - [shift] moves every following pair by one page, for books whose double-page art is split.
 * - Wide (double-page) pages are always shown alone, and pairing restarts after them so the
 *   following pages keep facing each other.
 * - Unknown aspect ratios are treated as portrait.
 */
internal fun comicSpreads(
    pageCount: Int,
    coverAlone: Boolean,
    shift: Boolean,
    isWidePage: (Int) -> Boolean = { false }
): List<ComicSpread> {
    if (pageCount <= 0) return emptyList()
    val spreads = ArrayList<ComicSpread>(pageCount / 2 + 2)
    var page = 0
    var leadingSingles = (if (coverAlone) 1 else 0) + (if (shift) 1 else 0)
    while (page < pageCount) {
        val wide = isWidePage(page)
        val canPair = !wide &&
            leadingSingles == 0 &&
            page + 1 < pageCount &&
            !isWidePage(page + 1)
        if (canPair) {
            spreads += ComicSpread(listOf(page, page + 1))
            page += 2
        } else {
            spreads += ComicSpread(listOf(page))
            if (leadingSingles > 0) leadingSingles--
            page += 1
        }
    }
    return spreads
}

/** Index of the spread that shows [page], clamped into the book. */
internal fun comicSpreadIndexForPage(spreads: List<ComicSpread>, page: Int): Int {
    if (spreads.isEmpty()) return 0
    val clamped = page.coerceIn(0, spreads.last().lastPage)
    val index = spreads.binarySearch { spread ->
        when {
            spread.lastPage < clamped -> -1
            spread.firstPage > clamped -> 1
            else -> 0
        }
    }
    return if (index >= 0) index else 0
}

/** Left-to-right screen order of a spread's pages: right-to-left books put the earlier page on the right. */
internal fun comicSpreadScreenOrder(spread: ComicSpread, readingDirection: LibraryReadingDirection): List<Int> =
    if (readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT) spread.pages.reversed() else spread.pages

/**
 * Which page of a spread a horizontal position falls on. Single-page spreads always resolve to
 * their page; pairs split at the middle of the spread, honouring reading direction.
 */
internal fun comicSpreadPageAt(
    spread: ComicSpread,
    x: Float,
    width: Float,
    readingDirection: LibraryReadingDirection,
    leftWidthPx: Float = width / 2f,
    gutterPx: Float = 0f,
    scale: Float = 1f,
    offsetX: Float = 0f
): Int {
    if (!spread.isPair || width <= 0f) return spread.firstPage
    val screenOrder = comicSpreadScreenOrder(spread, readingDirection)
    // Undo the zoom (about the centre) and pan, then split at the middle of the actual gutter.
    val centre = width / 2f
    val contentX = (x - centre - offsetX) / scale.coerceAtLeast(0.01f) + centre
    return if (contentX < leftWidthPx + gutterPx / 2f) screenOrder[0] else screenOrder[1]
}

/** Page persisted as the resume position: the first page so reopening shows the same spread. */
internal fun comicSpreadResumePage(spread: ComicSpread): Int = spread.firstPage

/** Page used for progress: the last visible page, so the final spread reports 100%. */
internal fun comicSpreadProgressPage(spread: ComicSpread): Int = spread.lastPage

/**
 * Re-anchors after the pairing changes (for example when a page turns out to be wide), keeping
 * the page the reader was looking at on screen.
 */
internal fun comicSpreadIndexAfterRepairing(
    previous: ComicSpread?,
    spreads: List<ComicSpread>
): Int = comicSpreadIndexForPage(spreads, previous?.firstPage ?: 0)

internal fun comicReaderFooterLabel(currentPage: Int, pageCount: Int, spreadFirstPage: Int? = null): String {
    val count = pageCount.coerceAtLeast(1)
    val last = (currentPage + 1).coerceIn(1, count)
    val first = spreadFirstPage?.let { (it + 1).coerceIn(1, count) }
    return if (first != null && first < last) "Pages $first–$last of $count" else "Page $last of $count"
}

internal fun comicReaderFooterAccessibilityLabel(
    currentPage: Int,
    pageCount: Int,
    spreadFirstPage: Int? = null
): String {
    val count = pageCount.coerceAtLeast(1)
    val last = (currentPage + 1).coerceIn(1, count)
    val first = spreadFirstPage?.let { (it + 1).coerceIn(1, count) }
    return if (first != null && first < last) {
        "Comic pages $first and $last of $count"
    } else {
        "Comic page $last of $count"
    }
}
