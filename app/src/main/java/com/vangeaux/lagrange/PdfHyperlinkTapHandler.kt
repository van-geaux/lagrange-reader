package com.vangeaux.lagrange

import android.graphics.PointF
import android.graphics.RectF
import java.net.URI
import kotlin.math.roundToInt

import com.github.barteksc.pdfviewer.PDFView
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.lang.reflect.Field
import org.readium.adapter.pdfium.navigator.PdfiumDocumentFragment
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi

internal sealed interface PdfHyperlinkTarget {
    data class External(val uri: String) : PdfHyperlinkTarget
    data class Internal(val pageIndex: Int) : PdfHyperlinkTarget
}

internal fun classifyPdfHyperlink(
    destinationPageIndex: Int?,
    uri: String?
): PdfHyperlinkTarget? = when {
    destinationPageIndex != null && destinationPageIndex >= 0 ->
        PdfHyperlinkTarget.Internal(destinationPageIndex)
    else -> uri?.trim()?.let { value ->
        val parsed = runCatching { URI(value) }.getOrNull() ?: return@let null
        if (parsed.scheme.equals("http", ignoreCase = true) ||
            parsed.scheme.equals("https", ignoreCase = true) ||
            parsed.scheme.equals("mailto", ignoreCase = true)
        ) {
            PdfHyperlinkTarget.External(value)
        } else {
            null
        }
    }
}

internal fun routePdfTap(
    target: PdfHyperlinkTarget?,
    openExternal: (String) -> Boolean,
    openInternal: (Int) -> Boolean,
    fallback: () -> Boolean
): Boolean {
    when (target) {
        is PdfHyperlinkTarget.External -> openExternal(target.uri)
        is PdfHyperlinkTarget.Internal -> openInternal(target.pageIndex)
        null -> return fallback()
    }
    return true
}

@OptIn(ExperimentalReadiumApi::class)
internal class PdfHyperlinkTapHandler(
    private val navigator: PdfiumNavigatorFragment,
    private val onExternalLink: (String) -> Boolean,
    private val onInternalLink: (Int) -> Boolean = { false },
    private val fallback: InputListener
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        return routePdfTap(
            target = findTarget(event.point),
            openExternal = onExternalLink,
            openInternal = onInternalLink,
            fallback = { fallback.onTap(event) }
        )
    }

    private fun findTarget(point: PointF): PdfHyperlinkTarget? {
        val documentFragment = navigator.childFragmentManager.fragments
            .filterIsInstance<PdfiumDocumentFragment>()
            .firstOrNull() ?: return null
        val pdfView = documentFragment.privateField<PDFView>("pdfView") ?: return null
        val pdfDocument = pdfView.privateField<PdfDocument>("pdfDocument") ?: return null
        val pdfiumCore = pdfView.privateField<PdfiumCore>("pdfiumCore") ?: return null
        val pageIndex = pdfView.currentPage
        val links = runCatching { pdfiumCore.getPageLinks(pdfDocument, pageIndex) }
            .getOrNull() ?: return null
        val displayWidth = pdfView.optimalPageWidth * pdfView.zoom
        val displayHeight = pdfView.optimalPageHeight * pdfView.zoom
        if (displayWidth <= 0f || displayHeight <= 0f) return null
        val pageOffset = pdfView.invokePrivateFloat("calculatePageOffset", pageIndex) ?: return null
        val isVertical = pdfView.privateField<Boolean>("swipeVertical") != false
        val pageLeft = if (isVertical) {
            pdfView.currentXOffset
        } else {
            pdfView.currentXOffset + pageOffset
        }
        val pageTop = if (isVertical) {
            pdfView.currentYOffset + pageOffset
        } else {
            pdfView.currentYOffset
        }
        val touchSlop = 12f * pdfView.resources.displayMetrics.density
        val link = links.firstOrNull { item ->
            val mappedBounds = runCatching {
                pdfiumCore.mapRectToDevice(
                    pdfDocument,
                    pageIndex,
                    pageLeft.roundToInt(),
                    pageTop.roundToInt(),
                    displayWidth.roundToInt(),
                    displayHeight.roundToInt(),
                    0,
                    item.bounds
                )
            }.getOrNull() ?: return@firstOrNull false
            RectF(mappedBounds).apply {
                inset(-touchSlop, -touchSlop)
            }.contains(point.x, point.y)
        } ?: return null
        return classifyPdfHyperlink(
            destinationPageIndex = link.destPageIdx,
            uri = link.uri
        )
    }
}

private fun Any.invokePrivateFloat(name: String, vararg args: Any?): Float? = runCatching {
    javaClass.declaredMethods.firstOrNull { method ->
        method.name == name && method.parameterCount == args.size
    }?.apply { isAccessible = true }?.invoke(this, *args) as? Float
}.getOrNull()

private inline fun <reified T> Any.privateField(name: String): T? = runCatching {
    var type: Class<*>? = javaClass
    var field: Field? = null
    while (type != null && field == null) {
        field = type.declaredFields.firstOrNull { it.name == name }
        type = type.superclass
    }
    field ?: return@runCatching null
    field.isAccessible = true
    field.get(this) as? T
}.getOrNull()
