package com.vangeaux.lagrange

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val COMIC_SPREAD_PREFETCH_SPREAD_COUNT = 2

/** Concurrent prefetch decodes; each one can approach the per-page bitmap cap. */
internal const val COMIC_SPREAD_PREFETCH_CONCURRENCY = 2
internal const val COMIC_SPREAD_READER_TEST_TAG = "comic-spread-reader"

/** Activity-facing navigation for the spread pager (tap zones, volume buttons, page list). */
internal interface ComicSpreadNavigator {
    fun goToPage(page: Int)
    fun step(delta: Int)
}

/** Decode widths for the two halves of a spread around [gutter], and for a lone page. */
internal data class ComicSpreadWidths(val leftPx: Int, val gutterPx: Int, val rightPx: Int, val singlePx: Int) {
    val pairDecodePx: Int get() = maxOf(leftPx, rightPx, 1)
}

internal fun comicSpreadWidths(totalWidthPx: Int, gutter: IntRange): ComicSpreadWidths {
    val total = totalWidthPx.coerceAtLeast(0)
    val start = gutter.first.coerceIn(0, total)
    val end = (gutter.last + 1).coerceIn(start, total)
    return ComicSpreadWidths(
        leftPx = start,
        gutterPx = end - start,
        rightPx = total - end,
        singlePx = total.coerceAtLeast(1)
    )
}

internal fun comicSpreadDecodeWidth(spread: ComicSpread, widths: ComicSpreadWidths): Int =
    if (spread.isPair) widths.pairDecodePx else widths.singlePx

/** Spread indexes to warm around the settled one, nearest first. */
internal fun comicSpreadPrefetchIndexes(settled: Int, spreadCount: Int, radius: Int): List<Int> =
    (1..radius.coerceAtLeast(0))
        .flatMap { distance -> listOf(settled + distance, settled - distance) }
        .filter { it in 0 until spreadCount }

private sealed interface SpreadPageState {
    data object Loading : SpreadPageState
    data class Loaded(val bitmap: Bitmap) : SpreadPageState
    data object Failed : SpreadPageState
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComicSpreadReader(
    pageCount: Int,
    initialPage: Int,
    coverAlone: Boolean,
    shift: Boolean,
    widePages: Set<Int>,
    readingDirection: LibraryReadingDirection,
    tapZoneLayout: ReaderTapZoneLayout,
    tapZoneInvertMode: ReaderTapZoneInvertMode,
    gutterForWidth: (widthPx: Int) -> IntRange,
    /** Changes whenever [gutterForWidth] would answer differently for the same width (hinge geometry). */
    gutterKey: Any? = null,
    animateNavigation: () -> Boolean,
    cachedPage: (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    loadPage: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    onSpreadChanged: (ComicSpread) -> Unit,
    onTap: (ContinuousComicTapAction) -> Unit,
    onLongPress: (pageIndex: Int, bitmap: Bitmap) -> Unit,
    onNavigatorAvailable: (ComicSpreadNavigator?) -> Unit,
    modifier: Modifier = Modifier
) {
    val spreads = remember(pageCount, coverAlone, shift, widePages) {
        comicSpreads(pageCount, coverAlone, shift) { it in widePages }
    }
    if (spreads.isEmpty()) return
    // Plain holder: re-anchoring must not trigger recomposition.
    val anchor = remember {
        arrayOf(ComicSpread(listOf(initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))))
    }
    val pagerState = rememberPagerState(
        initialPage = comicSpreadIndexForPage(spreads, initialPage)
    ) { spreads.size }
    val currentSpreads by rememberUpdatedState(spreads)
    val currentOnSpreadChanged by rememberUpdatedState(onSpreadChanged)
    val scope = rememberCoroutineScope()
    var zoomScale by remember { mutableFloatStateOf(1f) }

    DisposableEffect(pagerState) {
        val navigator = object : ComicSpreadNavigator {
            override fun goToPage(page: Int) {
                scrollTo(scope, pagerState, comicSpreadIndexForPage(currentSpreads, page), animateNavigation())
            }

            override fun step(delta: Int) {
                val target = (pagerState.currentPage + delta).coerceIn(0, currentSpreads.lastIndex)
                scrollTo(scope, pagerState, target, animateNavigation())
            }
        }
        onNavigatorAvailable(navigator)
        onDispose { onNavigatorAvailable(null) }
    }

    // Re-anchor first when pairing changes (e.g. a wide page was discovered), then report settles.
    LaunchedEffect(pagerState, spreads) {
        val target = comicSpreadIndexAfterRepairing(anchor[0], spreads)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { index ->
                spreads.getOrNull(index)?.let { spread ->
                    anchor[0] = spread
                    zoomScale = 1f
                    currentOnSpreadChanged(spread)
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(COMIC_SPREAD_READER_TEST_TAG)
    ) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val widths = remember(totalWidthPx, gutterKey) { comicSpreadWidths(totalWidthPx, gutterForWidth(totalWidthPx)) }

        val prefetchPermits = remember { Semaphore(COMIC_SPREAD_PREFETCH_CONCURRENCY) }
        LaunchedEffect(pagerState, spreads, widths) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collectLatest { settled ->
                    coroutineScope {
                        comicSpreadPrefetchIndexes(settled, spreads.size, COMIC_SPREAD_PREFETCH_SPREAD_COUNT)
                            .flatMap { index ->
                                val spread = spreads[index]
                                spread.pages.map { page -> page to comicSpreadDecodeWidth(spread, widths) }
                            }
                            .map { (page, width) ->
                                // Bounded: each decode can approach the per-page pixel cap.
                                async(Dispatchers.IO) {
                                    prefetchPermits.withPermit { runCatching { loadPage(page, width) } }
                                }
                            }
                            .awaitAll()
                    }
                }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Right-to-left books advance leftwards, like a printed manga.
            reverseLayout = readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT,
            userScrollEnabled = zoomScale <= 1.01f,
            beyondBoundsPageCount = 1,
            key = { index -> spreads.getOrNull(index)?.firstPage ?: index }
        ) { index ->
            val spread = spreads.getOrNull(index) ?: return@HorizontalPager
            ComicSpreadPage(
                spread = spread,
                widths = widths,
                readingDirection = readingDirection,
                isCurrent = index == pagerState.currentPage,
                zoomScale = if (index == pagerState.currentPage) zoomScale else 1f,
                onZoomChange = { zoomScale = it },
                cachedPage = cachedPage,
                loadPage = loadPage,
                onTap = { position, width, height ->
                    onTap(
                        continuousComicTapAction(
                            x = position.x,
                            y = position.y,
                            width = width,
                            height = height,
                            readingDirection = readingDirection,
                            layout = tapZoneLayout,
                            invertMode = tapZoneInvertMode
                        )
                    )
                },
                onLongPress = onLongPress
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun scrollTo(scope: CoroutineScope, pagerState: PagerState, target: Int, animate: Boolean) {
    scope.launch {
        if (animate) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
    }
}

@Composable
private fun ComicSpreadPage(
    spread: ComicSpread,
    widths: ComicSpreadWidths,
    readingDirection: LibraryReadingDirection,
    isCurrent: Boolean,
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    cachedPage: (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    loadPage: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    onTap: (position: Offset, width: Float, height: Float) -> Unit,
    onLongPress: (pageIndex: Int, bitmap: Bitmap) -> Unit
) {
    val decodeWidth = comicSpreadDecodeWidth(spread, widths)
    val loaded = remember(spread, decodeWidth) { mutableStateMapOf<Int, Bitmap>() }
    var offset by remember(spread) { mutableStateOf(Offset.Zero) }
    val currentZoom by rememberUpdatedState(zoomScale)
    // The tap gesture outlives recompositions (its keys stay equal), so read the latest callbacks:
    // tap-zone layout and inversion can change while this spread stays on screen.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    // Replaced when the decode width changes (e.g. a hinge update) while the spread stays the same.
    val currentLoaded by rememberUpdatedState(loaded)
    val density = LocalDensity.current
    val pageDescription = if (spread.isPair) {
        "Pages ${spread.firstPage + 1} and ${spread.lastPage + 1}"
    } else {
        "Page ${spread.firstPage + 1}"
    }
    LaunchedEffect(isCurrent) { if (!isCurrent) offset = Offset.Zero }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = pageDescription }
            .pointerInput(spread) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Gesture-local scale: the hoisted value only updates after recomposition.
                    var scale = currentZoom
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1.01f) {
                            val nextScale = boundedComicImageScale(scale * event.calculateZoom())
                            val nextOffset = boundedComicImagePan(
                                offset + event.calculatePan(),
                                size.width.toFloat(),
                                size.height.toFloat(),
                                nextScale
                            )
                            if (nextScale <= 1.01f) {
                                scale = 1f
                                onZoomChange(1f)
                                offset = Offset.Zero
                            } else {
                                scale = nextScale
                                onZoomChange(nextScale)
                                offset = nextOffset
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(spread, readingDirection) {
                detectTapGestures(
                    onTap = { position ->
                        currentOnTap(position, size.width.toFloat(), size.height.toFloat())
                    },
                    onLongPress = { position ->
                        val page = comicSpreadPageAt(spread, position.x, size.width.toFloat(), readingDirection)
                        currentLoaded[page]?.let { bitmap -> currentOnLongPress(page, bitmap) }
                    }
                )
            }
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        if (spread.isPair) {
            val screenOrder = comicSpreadScreenOrder(spread, readingDirection)
            Row(modifier = Modifier.fillMaxSize()) {
                SpreadPageImage(
                    page = screenOrder[0],
                    decodeWidth = decodeWidth,
                    // Pages meet at the gutter like a bound book.
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.width(with(density) { widths.leftPx.toDp() }).fillMaxHeight(),
                    cachedPage = cachedPage,
                    loadPage = loadPage,
                    onLoaded = { loaded[screenOrder[0]] = it }
                )
                Spacer(modifier = Modifier.width(with(density) { widths.gutterPx.toDp() }))
                SpreadPageImage(
                    page = screenOrder[1],
                    decodeWidth = decodeWidth,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.width(with(density) { widths.rightPx.toDp() }).fillMaxHeight(),
                    cachedPage = cachedPage,
                    loadPage = loadPage,
                    onLoaded = { loaded[screenOrder[1]] = it }
                )
            }
        } else {
            SpreadPageImage(
                page = spread.firstPage,
                decodeWidth = decodeWidth,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
                cachedPage = cachedPage,
                loadPage = loadPage,
                onLoaded = { loaded[spread.firstPage] = it }
            )
        }
    }
}

@Composable
private fun SpreadPageImage(
    page: Int,
    decodeWidth: Int,
    alignment: Alignment,
    modifier: Modifier,
    cachedPage: (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    loadPage: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    onLoaded: (Bitmap) -> Unit
) {
    var retryKey by remember { mutableIntStateOf(0) }
    val state by produceState<SpreadPageState>(
        initialValue = cachedPage(page, decodeWidth)?.let(SpreadPageState::Loaded) ?: SpreadPageState.Loading,
        page,
        decodeWidth,
        retryKey
    ) {
        value = withContext(Dispatchers.IO) {
            cachedPage(page, decodeWidth) ?: runCatching { loadPage(page, decodeWidth) }.getOrNull()
        }?.let(SpreadPageState::Loaded) ?: SpreadPageState.Failed
    }
    Box(modifier = modifier, contentAlignment = alignment) {
        when (val pageState = state) {
            SpreadPageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            is SpreadPageState.Loaded -> {
                LaunchedEffect(pageState.bitmap) { onLoaded(pageState.bitmap) }
                Image(
                    bitmap = pageState.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SpreadPageState.Failed -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("Page ${page + 1} could not be loaded", color = Color.White)
                TextButton(onClick = { retryKey++ }) { Text("Retry") }
            }
        }
    }
}
