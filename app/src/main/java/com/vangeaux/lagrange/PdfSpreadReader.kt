package com.vangeaux.lagrange

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView
import java.io.File

internal data class PdfSpreadPage(
    val index: Int,
    val aspectRatio: Float
)

internal data class PdfSpread(
    val pages: List<PdfSpreadPage>
)

internal enum class PdfSpreadSwipeDirection {
    PREVIOUS,
    NEXT,
    IGNORE
}

internal fun shouldUsePdfSpreadReader(preferences: LibraryReaderPreferences): Boolean =
    preferences.pdfLayoutMode == ReaderLayoutMode.PAGINATED && preferences.joinPdfFacingPages

internal fun pdfSpreadUsesVerticalNavigation(viewportWidthPx: Int, viewportHeightPx: Int): Boolean =
    viewportWidthPx > viewportHeightPx

internal fun pdfSpreadSwipeDirection(
    deltaX: Float,
    deltaY: Float,
    vertical: Boolean,
    rightToLeft: Boolean
): PdfSpreadSwipeDirection {
    if (vertical) {
        if (kotlin.math.abs(deltaY) <= kotlin.math.abs(deltaX)) return PdfSpreadSwipeDirection.IGNORE
        return if (deltaY < 0f) PdfSpreadSwipeDirection.NEXT else PdfSpreadSwipeDirection.PREVIOUS
    }
    if (kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY)) return PdfSpreadSwipeDirection.IGNORE
    val advances = if (rightToLeft) deltaX > 0f else deltaX < 0f
    return if (advances) PdfSpreadSwipeDirection.NEXT else PdfSpreadSwipeDirection.PREVIOUS
}

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
    invertPdfColors: Boolean,
    readingDirection: LibraryReadingDirection,
    onPageChanged: (Int) -> Unit,
    onTap: (MotionEvent, Int, Int) -> Unit,
    onJumpToPageAvailable: (((Int) -> Unit)?) -> Unit,
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
        var currentSpreadIndex by remember(spreads, initialPage) {
            mutableIntStateOf(pdfSpreadInitialRow(spreads, initialPage))
        }
        val jumpToPage: (Int) -> Unit = remember(spreads) {
            { pageIndex ->
                currentSpreadIndex = pdfSpreadInitialRow(spreads, pageIndex)
            }
        }

        DisposableEffect(spreads) {
            onJumpToPageAvailable(jumpToPage)
            onDispose {
                onJumpToPageAvailable(null)
            }
        }
        LaunchedEffect(currentSpreadIndex, spreads) {
            spreads.getOrNull(currentSpreadIndex)?.pages?.firstOrNull()?.index?.let(onPageChanged)
        }

        val spread = spreads.getOrNull(currentSpreadIndex)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(vertical, readingDirection, spreads) {
                    var totalX = 0f
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalX = 0f
                            totalY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            change.consume()
                        },
                        onDragEnd = {
                            val threshold = with(density) { 48.dp.toPx() }
                            if (kotlin.math.max(kotlin.math.abs(totalX), kotlin.math.abs(totalY)) >= threshold) {
                                when (pdfSpreadSwipeDirection(
                                    deltaX = totalX,
                                    deltaY = totalY,
                                    vertical = vertical,
                                    rightToLeft = readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT
                                )) {
                                    PdfSpreadSwipeDirection.PREVIOUS ->
                                        currentSpreadIndex = (currentSpreadIndex - 1).coerceAtLeast(0)
                                    PdfSpreadSwipeDirection.NEXT ->
                                        currentSpreadIndex = (currentSpreadIndex + 1).coerceAtMost(spreads.lastIndex)
                                    PdfSpreadSwipeDirection.IGNORE -> Unit
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (spread != null) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    spread.pages.forEach { page ->
                        key(page.index) {
                            PdfSpreadPageView(
                                file = file,
                                page = page,
                                invertPdfColors = invertPdfColors,
                                onTap = onTap,
                                modifier = if (spread.pages.size == 1) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier.weight(1f).fillMaxSize()
                                }
                            )
                        }
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
