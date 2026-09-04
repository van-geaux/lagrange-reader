package com.vangeaux.lagrange

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

internal fun comicPageExportTitle(title: String, pageIndex: Int): String =
    "${title.trim().ifBlank { "comic" }} - Page ${pageIndex + 1}"

internal fun paginatedComicLongPressPage(currentPage: Int, pageCount: Int): Int? =
    currentPage.takeIf { it in 0 until pageCount }

internal fun paginatedComicPageIndex(
    pageHref: String,
    readingOrderHrefs: List<String>
): Int? = readingOrderHrefs.indexOfFirst { href ->
    href.substringBefore('#') == pageHref.substringBefore('#')
}.takeIf { it >= 0 }

internal fun fittedReaderImageSize(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int
): Size {
    if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return Size.Zero
    }
    val fitScale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
    return Size(imageWidth * fitScale, imageHeight * fitScale)
}

internal fun isPointInsideTransformedReaderImage(
    point: Offset,
    imageTopLeft: Offset,
    imageSize: Size,
    scale: Float,
    pan: Offset
): Boolean {
    if (imageSize.width <= 0f || imageSize.height <= 0f || scale <= 0f) return false
    val center = imageTopLeft + Offset(imageSize.width / 2f, imageSize.height / 2f) + pan
    val halfWidth = imageSize.width * scale / 2f
    val halfHeight = imageSize.height * scale / 2f
    return point.x in (center.x - halfWidth)..(center.x + halfWidth) &&
        point.y in (center.y - halfHeight)..(center.y + halfHeight)
}

internal fun toggledReaderImageScale(scale: Float): Float = if (scale > 1f) 1f else 2.5f

internal fun readerImageMenuAnchor(press: Offset, verticalGapPx: Float): Offset =
    press + Offset(0f, verticalGapPx.coerceAtLeast(0f))

internal fun shouldDismissReaderImageViewerTap(
    isInsideImage: Boolean,
    isTransformInProgress: Boolean
): Boolean = !isInsideImage && !isTransformInProgress

internal fun boundedComicImageScale(scale: Float, minimum: Float = 1f, maximum: Float = 4f): Float =
    scale.coerceIn(minimum, maximum)

internal fun boundedComicImagePan(
    pan: Offset,
    imageWidth: Float,
    imageHeight: Float,
    scale: Float
): Offset {
    if (scale <= 1f || imageWidth <= 0f || imageHeight <= 0f) return Offset.Zero
    val maxX = imageWidth * (scale - 1f) / 2f
    val maxY = imageHeight * (scale - 1f) / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

@Composable
internal fun ComicPageImageViewer(
    title: String,
    pageIndex: Int,
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var pan by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var menuAnchor by remember(pageIndex) { mutableStateOf<Offset?>(null) }
    var imageTopLeft by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    var imageSize by remember(pageIndex) { mutableStateOf(Size.Zero) }
    var pendingExport by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val currentScale by rememberUpdatedState(scale)
    val currentPan by rememberUpdatedState(pan)
    val currentImageTopLeft by rememberUpdatedState(imageTopLeft)
    val currentImageSize by rememberUpdatedState(imageSize)

    fun export() {
        val result = exportCoverImage(context, comicPageExportTitle(title, pageIndex), bitmapToPng(bitmap))
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingExport) export()
        else if (!granted) {
            Toast.makeText(context, "Storage permission is required to save the page", Toast.LENGTH_SHORT).show()
        }
        pendingExport = false
    }

    fun requestExport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingExport = true
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            export()
        }
    }

    val transformState = rememberTransformableState { zoom, panChange, _ ->
        val newScale = boundedComicImageScale(currentScale * zoom)
        scale = newScale
        pan = boundedComicImagePan(
            currentPan + panChange,
            currentImageSize.width,
            currentImageSize.height,
            newScale
        )
    }

    fun isInsideImage(position: Offset): Boolean = isPointInsideTransformedReaderImage(
        point = position,
        imageTopLeft = currentImageTopLeft,
        imageSize = currentImageSize,
        scale = currentScale,
        pan = currentPan
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(pageIndex) {
                    detectTapGestures(
                        onTap = { position ->
                            if (
                                shouldDismissReaderImageViewerTap(
                                    isInsideImage = isInsideImage(position),
                                    isTransformInProgress = transformState.isTransformInProgress
                                )
                            ) {
                                onDismiss()
                            }
                        },
                        onDoubleTap = { position ->
                            if (isInsideImage(position)) {
                                scale = toggledReaderImageScale(currentScale)
                                pan = Offset.Zero
                            } else {
                                onDismiss()
                            }
                        },
                        onLongPress = { position ->
                            if (isInsideImage(position)) {
                                menuAnchor = readerImageMenuAnchor(
                                    position,
                                    with(density) { 8.dp.toPx() }
                                )
                            }
                        }
                    )
                }
                .transformable(transformState)
                .semantics {
                    contentDescription = "Page image ${pageIndex + 1}. Pinch or double-tap to zoom. Tap outside the image or use back to close. Long-press the image for download options."
                },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val fittedSize = fittedReaderImageSize(
                    containerWidth = constraints.maxWidth.toFloat(),
                    containerHeight = constraints.maxHeight.toFloat(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(with(density) { fittedSize.width.toDp() })
                        .height(with(density) { fittedSize.height.toDp() })
                        .onGloballyPositioned { coordinates ->
                            imageTopLeft = coordinates.positionInRoot()
                            imageSize = Size(
                                coordinates.size.width.toFloat(),
                                coordinates.size.height.toFloat()
                            )
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        }
                )
            }
            menuAnchor?.let { anchor ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) }
                        .size(1.dp)
                ) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuAnchor = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Download page") },
                            onClick = { menuAnchor = null; requestExport() }
                        )
                    }
                }
            }
        }
    }
}

private fun bitmapToPng(bitmap: Bitmap): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode comic page" }
    return output.toByteArray()
}
