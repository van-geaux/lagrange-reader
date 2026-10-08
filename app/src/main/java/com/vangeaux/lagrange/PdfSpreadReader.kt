package com.vangeaux.lagrange

import android.view.MotionEvent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class PdfSpreadPage(
    val index: Int,
    val aspectRatio: Float
)

internal data class PdfSpread(
    val pages: List<PdfSpreadPage>
)

internal data class PdfSpreadVisibleItem(
    val rowIndex: Int,
    val offset: Int,
    val size: Int
)

internal fun shouldUsePdfSpreadReader(preferences: LibraryReaderPreferences): Boolean =
    preferences.pdfLayoutMode == ReaderLayoutMode.PAGINATED && preferences.joinPdfFacingPages

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

internal fun pdfSpreadVisiblePageIndex(
    visibleItems: List<PdfSpreadVisibleItem>,
    viewportStart: Int,
    viewportEnd: Int,
    spreads: List<PdfSpread>
): Int? = visibleItems
    .mapNotNull { (rowIndex, offset) ->
        val spread = spreads.getOrNull(rowIndex) ?: return@mapNotNull null
        val visibleHeight = (minOf(offset + visibleItems.first { it.rowIndex == rowIndex }.size, viewportEnd) - maxOf(offset, viewportStart))
            .coerceAtLeast(0)
        spread.pages.firstOrNull()?.index?.let { pageIndex -> pageIndex to visibleHeight }
    }
    .maxByOrNull { it.second }
    ?.first

@Composable
internal fun PdfSpreadReader(
    file: File,
    pages: List<PdfSpreadPage>,
    initialPage: Int,
    pageGapDp: Float,
    invertPdfColors: Boolean,
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
        val spreads = remember(pages, viewportWidthPx, viewportHeightPx) {
            buildPdfSpreads(pages, viewportWidthPx, viewportHeightPx)
        }
        DisposableEffect(spreads) {
            onSpreadsAvailable(spreads)
            onDispose { onSpreadsAvailable(emptyList()) }
        }
        val initialRow = remember(spreads, initialPage) {
            pdfSpreadInitialRow(spreads, initialPage)
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
        DisposableEffect(listState) {
            onListStateAvailable(listState)
            onDispose { onListStateAvailable(null) }
        }
        LaunchedEffect(listState, spreads) {
            snapshotFlow {
                Triple(
                    listState.layoutInfo.visibleItemsInfo.map { item ->
                        PdfSpreadVisibleItem(item.index, item.offset, item.size)
                    },
                    listState.layoutInfo.viewportStartOffset,
                    listState.layoutInfo.viewportEndOffset
                )
            }
                .map { (items, viewportStart, viewportEnd) ->
                    pdfSpreadVisiblePageIndex(items, viewportStart, viewportEnd, spreads)
                }
                .distinctUntilChanged()
                .collect { pageIndex -> pageIndex?.let(onPageChanged) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(pageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP).dp)
        ) {
            itemsIndexed(spreads, key = { _, spread -> spread.pages.first().index }) { _, spread ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    spread.pages.forEach { page ->
                        PdfSpreadPageView(
                            file = file,
                            page = page,
                            invertPdfColors = invertPdfColors,
                            onTap = onTap,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(page.aspectRatio.coerceAtLeast(0.1f))
                        )
                    }
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
