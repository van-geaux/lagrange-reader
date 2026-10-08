package com.vangeaux.lagrange

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class PdfSpreadPage(
    val index: Int,
    val aspectRatio: Float
)

internal data class PdfSpread(
    val pages: List<PdfSpreadPage>
)

internal fun shouldUsePdfSpreadReader(preferences: LibraryReaderPreferences): Boolean =
    preferences.pdfLayoutMode == ReaderLayoutMode.PAGINATED && preferences.joinPdfFacingPages

internal fun pdfSpreadUsesVerticalNavigation(viewportWidthPx: Int, viewportHeightPx: Int): Boolean =
    viewportWidthPx > viewportHeightPx

internal fun pdfSpreadAspectRatio(width: Int?, height: Int?): Float =
    if (width != null && height != null && width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        0.75f
    }

internal fun buildPdfSpreads(
    pages: List<PdfSpreadPage>,
    viewportWidthPx: Int,
    viewportHeightPx: Int
): List<PdfSpread> {
    if (pages.isEmpty()) return emptyList()
    if (viewportWidthPx <= viewportHeightPx) return pages.map { page -> PdfSpread(listOf(page)) }

    val viewportAspectRatio = viewportWidthPx.toFloat() / viewportHeightPx.coerceAtLeast(1)
    val spreads = mutableListOf<PdfSpread>()
    var index = 0
    while (index < pages.size) {
        val page = pages[index]
        val next = pages.getOrNull(index + 1)
        val canPair = next != null &&
            page.aspectRatio < 1f &&
            next.aspectRatio < 1f &&
            page.aspectRatio + next.aspectRatio <= viewportAspectRatio
        if (canPair) {
            spreads += PdfSpread(listOf(page, next!!))
            index += 2
        } else {
            spreads += PdfSpread(listOf(page))
            index += 1
        }
    }
    return spreads
}

internal fun pdfSpreadInitialRow(spreads: List<PdfSpread>, pageIndex: Int): Int =
    spreads.indexOfFirst { spread -> spread.pages.any { page -> page.index == pageIndex } }
        .takeIf { it >= 0 } ?: 0

@Composable
internal fun PdfSpreadReader(
    file: File,
    pages: List<PdfSpreadPage>,
    initialPage: Int,
    pageGapDp: Float,
    invertPdfColors: Boolean,
    readingDirection: LibraryReadingDirection,
    onPageChanged: (Int) -> Unit,
    onTap: (MotionEvent, Int, Int) -> Unit,
    onListStateAvailable: (LazyListState?) -> Unit,
    onSpreadsAvailable: (List<PdfSpread>) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        val vertical = pdfSpreadUsesVerticalNavigation(viewportWidthPx, viewportHeightPx)
        val spreads = remember(pages, viewportWidthPx, viewportHeightPx) {
            buildPdfSpreads(pages, viewportWidthPx, viewportHeightPx)
        }
        val initialRow = remember(spreads, initialPage) {
            pdfSpreadInitialRow(spreads, initialPage)
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)

        DisposableEffect(spreads) {
            onSpreadsAvailable(spreads)
            onDispose { onSpreadsAvailable(emptyList()) }
        }
        DisposableEffect(listState) {
            onListStateAvailable(listState)
            onDispose { onListStateAvailable(null) }
        }
        LaunchedEffect(listState, spreads) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { row ->
                    spreads.getOrNull(row)?.pages?.firstOrNull()?.index?.let(onPageChanged)
                }
        }

        val gap = pageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP).dp
        if (vertical) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                pdfSpreadItems(
                    spreads = spreads,
                    pageWidth = maxWidth,
                    pageHeight = maxHeight,
                    file = file,
                    invertPdfColors = invertPdfColors,
                    onTap = onTap
                )
            }
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT,
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                pdfSpreadItems(
                    spreads = spreads,
                    pageWidth = maxWidth,
                    pageHeight = maxHeight,
                    file = file,
                    invertPdfColors = invertPdfColors,
                    onTap = onTap
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pdfSpreadItems(
    spreads: List<PdfSpread>,
    pageWidth: Dp,
    pageHeight: Dp,
    file: File,
    invertPdfColors: Boolean,
    onTap: (MotionEvent, Int, Int) -> Unit
) {
    itemsIndexed(spreads, key = { _, spread -> spread.pages.first().index }) { _, spread ->
        Box(
            modifier = Modifier
                .width(pageWidth)
                .height(pageHeight),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                spread.pages.forEach { page ->
                    PdfSpreadPageView(
                        file = file,
                        page = page,
                        invertPdfColors = invertPdfColors,
                        onTap = onTap,
                        modifier = if (spread.pages.size == 1) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfSpreadPageView(
    file: File,
    page: PdfSpreadPage,
    invertPdfColors: Boolean,
    onTap: (MotionEvent, Int, Int) -> Unit,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            PDFView(context, null).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                fromFile(file)
                    .pages(page.index)
                    .enableSwipe(false)
                    .enableDoubletap(true)
                    .enableAntialiasing(true)
                    .spacing(0)
                    .onTap { event ->
                        onTap(event, width, height)
                        true
                    }
                    .load()
            }.also { pdfView ->
                if (invertPdfColors) {
                    pdfView.setLayerType(
                        View.LAYER_TYPE_HARDWARE,
                        Paint().apply {
                            colorFilter = ColorMatrixColorFilter(ColorMatrix(pdfColorMatrix(inverted = true)))
                        }
                    )
                }
            }
        },
        modifier = modifier,
        update = { pdfView ->
            if (invertPdfColors) {
                pdfView.setLayerType(
                    View.LAYER_TYPE_HARDWARE,
                    Paint().apply {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix(pdfColorMatrix(inverted = true)))
                    }
                )
            } else {
                pdfView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    )
}
