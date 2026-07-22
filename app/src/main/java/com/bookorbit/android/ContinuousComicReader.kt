package com.bookorbit.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class ContinuousComicTapAction {
    PREVIOUS,
    NEXT,
    MENU
}

internal const val MAX_CONTINUOUS_COMIC_BITMAP_PIXELS = 16_000_000L
internal const val CONTINUOUS_COMIC_PREFETCH_PAGE_COUNT = 2

internal fun continuousComicTapAction(
    x: Float,
    width: Float,
    readingDirection: LibraryReadingDirection
): ContinuousComicTapAction {
    if (width <= 0f) return ContinuousComicTapAction.MENU
    val edge = width * 0.25f
    val isLeftEdge = x <= edge
    val isRightEdge = x >= width - edge
    if (!isLeftEdge && !isRightEdge) return ContinuousComicTapAction.MENU
    val isForward = when (readingDirection) {
        LibraryReadingDirection.LEFT_TO_RIGHT -> isRightEdge
        LibraryReadingDirection.RIGHT_TO_LEFT -> isLeftEdge
    }
    return if (isForward) ContinuousComicTapAction.NEXT else ContinuousComicTapAction.PREVIOUS
}

internal fun decodeContinuousComicPage(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = continuousComicSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            targetWidthPx = targetWidthPx
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

internal fun continuousComicSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidthPx: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1
    val targetWidth = targetWidthPx.coerceAtLeast(1)
    var sample = 1
    while (
        sample <= Int.MAX_VALUE / 2 &&
        sourceWidth / (sample * 2) >= targetWidth
    ) {
        sample *= 2
    }
    while (
        sample <= Int.MAX_VALUE / 2 &&
        (sourceWidth / sample).toLong() * (sourceHeight / sample).toLong() >
            MAX_CONTINUOUS_COMIC_BITMAP_PIXELS
    ) {
        sample *= 2
    }
    return sample
}

private sealed interface ContinuousComicPageState {
    data object Loading : ContinuousComicPageState
    data class Loaded(val bitmap: Bitmap) : ContinuousComicPageState
    data object Failed : ContinuousComicPageState
}

@Composable
internal fun ContinuousComicReader(
    pageIndexes: List<Int>,
    initialPage: Int,
    pageGapDp: Float,
    readingDirection: LibraryReadingDirection,
    cachedPage: (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    cachedPageAspectRatio: (pageIndex: Int, targetWidthPx: Int) -> Float?,
    loadPage: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    onPageChanged: (Int) -> Unit,
    onTap: (ContinuousComicTapAction) -> Unit,
    onListStateAvailable: (LazyListState?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, pageIndexes.lastIndex.coerceAtLeast(0))
    )
    DisposableEffect(listState) {
        onListStateAvailable(listState)
        onDispose { onListStateAvailable(null) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect(onPageChanged)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val targetWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LaunchedEffect(listState, targetWidthPx, pageIndexes) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collectLatest { firstVisiblePage ->
                    val prefetchPages = (-CONTINUOUS_COMIC_PREFETCH_PAGE_COUNT..CONTINUOUS_COMIC_PREFETCH_PAGE_COUNT)
                        .map { firstVisiblePage + it }
                        .filter { it in pageIndexes.indices && it != firstVisiblePage }
                    coroutineScope {
                        prefetchPages.map { pageIndex ->
                            async(Dispatchers.IO) {
                                runCatching { loadPage(pageIndexes[pageIndex], targetWidthPx) }
                            }
                        }.awaitAll()
                    }
                }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(readingDirection, pageIndexes.size) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            onTap(
                                continuousComicTapAction(
                                    x = up.position.x,
                                    width = size.width.toFloat(),
                                    readingDirection = readingDirection
                                )
                            )
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(pageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP).dp)
        ) {
            items(pageIndexes, key = { it }) { pageIndex ->
                ContinuousComicPage(
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                    cachedPage = cachedPage,
                    cachedPageAspectRatio = cachedPageAspectRatio,
                    loadPage = loadPage,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ContinuousComicPage(
    pageIndex: Int,
    targetWidthPx: Int,
    cachedPage: (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    cachedPageAspectRatio: (pageIndex: Int, targetWidthPx: Int) -> Float?,
    loadPage: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    var retryKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val cachedBitmap = cachedPage(pageIndex, targetWidthPx)
    val knownAspectRatio = cachedBitmap
        ?.let { bitmap -> bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1) }
        ?: cachedPageAspectRatio(pageIndex, targetWidthPx)
    val placeholderModifier = knownAspectRatio
        ?.takeIf { ratio -> ratio.isFinite() && ratio > 0f }
        ?.let { ratio -> modifier.aspectRatio(ratio) }
    val state by produceState<ContinuousComicPageState>(
        initialValue = cachedBitmap
            ?.let(ContinuousComicPageState::Loaded)
            ?: ContinuousComicPageState.Loading,
        pageIndex,
        targetWidthPx,
        retryKey
    ) {
        value = withContext(Dispatchers.IO) {
            cachedPage(pageIndex, targetWidthPx)
                ?: runCatching { loadPage(pageIndex, targetWidthPx) }.getOrNull()
        }
            ?.let { bitmap -> ContinuousComicPageState.Loaded(bitmap) }
            ?: ContinuousComicPageState.Failed
    }
    when (val pageState = state) {
        ContinuousComicPageState.Loading -> Box(
            modifier = placeholderModifier ?: modifier.padding(vertical = 96.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Color.White) }
        is ContinuousComicPageState.Loaded -> Image(
            bitmap = pageState.bitmap.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = modifier
        )
        ContinuousComicPageState.Failed -> Box(
            modifier = placeholderModifier ?: modifier.padding(vertical = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Page ${pageIndex + 1} could not be loaded", color = Color.White)
                androidx.compose.material3.TextButton(onClick = { retryKey++ }) { Text("Retry") }
            }
        }
    }
}
