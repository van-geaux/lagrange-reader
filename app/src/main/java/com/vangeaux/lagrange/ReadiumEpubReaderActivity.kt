package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.*

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import com.vangeaux.lagrange.epubtts.EpubTtsModuleImpl
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.epub.css.ColCount as ReadiumCssColCount
import org.readium.r2.navigator.epub.css.Length as ReadiumCssLength
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.ReadingProgression as ReadiumReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.publication.services.content.ContentService
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Language as ReadiumLanguage
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal sealed interface ReadiumEpubOpenResult {
    data class Opened(val publication: Publication) : ReadiumEpubOpenResult
    data class Error(val message: String) : ReadiumEpubOpenResult
}

internal data class EpubOverallPosition(val number: Int, val count: Int)

internal data class EpubRenderedPageLedger(
    val layoutKey: String,
    val pageCounts: List<Int>
)

internal data class PendingEpubPagination(
    val pageIndex: Int,
    val pageCount: Int,
    val locator: Locator
)

internal fun shouldUseSoftwareEpubRendering(manufacturer: String, model: String): Boolean =
    manufacturer.equals("Bigme", ignoreCase = true) &&
        model.equals("HiBreak", ignoreCase = true)

internal fun isHealthyEpubRendererProbeResult(value: String?): Boolean {
    val decoded = decodeJavascriptString(value) ?: return false
    val payload = runCatching { JSONObject(decoded) }.getOrNull() ?: return false
    return payload.optString("marker") == EPUB_RENDERER_PROBE_MARKER &&
        payload.optString("readyState") in setOf("interactive", "complete") &&
        payload.optBoolean("hasBody") &&
        payload.optBoolean("hasReadium") &&
        payload.optInt("viewportWidth") > 0 &&
        payload.optInt("viewportHeight") > 0 &&
        payload.optInt("contentWidth") > 0 &&
        payload.optInt("contentHeight") > 0
}

internal data class EpubCanonicalPosition(
    val href: String,
    val totalProgression: Double?,
    val resourceProgression: Double?,
    val contentPosition: Int? = null
)

internal class EpubCanonicalPositionIndex internal constructor(
    val positions: List<EpubCanonicalPosition>,
    internal val totalProgressions: DoubleArray,
    internal val resourceProgressions: DoubleArray,
    internal val rangesByHref: Map<String, IntRange>,
    internal val sourceIndices: IntArray
)

internal class EpubContentPositionIndex internal constructor(
    val positions: List<Locator>,
    internal val canonical: EpubCanonicalPositionIndex
) {
    internal val totalProgressions: DoubleArray get() = canonical.totalProgressions
    internal val resourceProgressions: DoubleArray get() = canonical.resourceProgressions
    internal val rangesByHref: Map<String, IntRange> get() = canonical.rangesByHref
}

internal class EpubPositionSingleFlight<T>(
    private val scope: CoroutineScope,
    private val maximumEntries: Int = 4
) {
    private val jobs = LinkedHashMap<String, Deferred<T?>>(maximumEntries, 0.75f, true)

    fun getOrStart(key: String, loader: suspend () -> T?): Deferred<T?> = synchronized(jobs) {
        jobs[key]?.let { return@synchronized it }
        while (jobs.size >= maximumEntries) {
            val removable = jobs.entries.firstOrNull { (_, job) -> job.isCompleted }
                ?: return@synchronized CompletableDeferred<T?>(null)
            jobs.remove(removable.key)
        }
        scope.async { loader() }.also { created -> jobs[key] = created }
    }
}

private const val EPUB_END_EPSILON = 0.000001
private const val EPUB_AUTHORITATIVE_MOVEMENT_TIMEOUT_MILLIS = 10_000L
internal const val MAX_EPUB_CONTENT_POSITIONS = 100_000
internal const val MAX_EPUB_POSITION_SOURCE_BYTES = 64L * 1024L * 1024L
internal const val MAX_EPUB_POSITION_READING_ORDER = 2_000
internal const val MAX_EPUB_POSITION_ARCHIVE_ENTRIES = 10_000
internal const val MAX_EPUB_POSITION_MARKUP_ENTRY_BYTES = 8L * 1024L * 1024L
internal const val MAX_EPUB_POSITION_MARKUP_TOTAL_BYTES = 64L * 1024L * 1024L
internal const val MAX_EPUB_RENDERED_PAGE_RESOURCES = 4_096
internal const val MAX_EPUB_RENDERED_PAGES_PER_RESOURCE = 1_000_000
private const val STATE_READER_POSITION_WRITE_MILLIS = "epub_reader_position_write_millis"
private const val STATE_READER_POSITION_CHAPTER = "epub_reader_position_chapter"
private const val STATE_READER_POSITION_PAGE = "epub_reader_position_page"
private const val STATE_READER_POSITION_PAGE_COUNT = "epub_reader_position_page_count"
private const val STATE_READER_POSITION_PERCENT = "epub_reader_position_percent"
private const val STATE_READER_POSITION_OVERALL_NUMBER = "epub_reader_position_overall_number"
private const val STATE_READER_POSITION_OVERALL_COUNT = "epub_reader_position_overall_count"
private const val STATE_READER_RENDERED_PAGE_LAYOUT_KEY = "epub_reader_rendered_page_layout_key"
private const val STATE_READER_RENDERED_PAGE_COUNTS = "epub_reader_rendered_page_counts"
private const val STATE_READER_POSITION_RESET_EPOCH = "epub_reader_position_reset_epoch"
private const val EPUB_RENDERER_PROBE_MARKER = "lagrange-epub-renderer-v1"
private const val EPUB_RENDERER_PROBE_TIMEOUT_MILLIS = 20_000L
private const val EPUB_RENDERER_PROBE_ATTEMPT_TIMEOUT_MILLIS = 2_500L
private const val EPUB_RENDERER_PROBE_RETRY_MILLIS = 250L
private const val EPUB_RENDERER_PROBE_SCRIPT = """
    (() => {
        const root = document.documentElement;
        const body = document.body;
        return JSON.stringify({
            marker: 'lagrange-epub-renderer-v1',
            readyState: document.readyState,
            hasBody: !!body,
            hasReadium: typeof readium === 'object' && readium !== null,
            viewportWidth: window.innerWidth || 0,
            viewportHeight: window.innerHeight || 0,
            contentWidth: Math.max(root ? root.scrollWidth : 0, body ? body.scrollWidth : 0),
            contentHeight: Math.max(root ? root.scrollHeight : 0, body ? body.scrollHeight : 0)
        });
    })();
"""
private val epubPositionWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val epubPositionGenerationDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "epub-position-generator").apply { isDaemon = true }
}.asCoroutineDispatcher()
private val epubPositionGenerationScope = CoroutineScope(SupervisorJob() + epubPositionGenerationDispatcher)
private val epubPositionSingleFlight = EpubPositionSingleFlight<EpubContentPositionIndex>(
    epubPositionGenerationScope
)

internal fun shouldGenerateEpubContentPositions(fileSize: Long, readingOrderCount: Int): Boolean =
    fileSize in 1..MAX_EPUB_POSITION_SOURCE_BYTES &&
        readingOrderCount in 1..MAX_EPUB_POSITION_READING_ORDER

internal fun normalizedLocalEpubResourcePath(href: String): String? {
    if (href.isBlank() || '\\' in href || '\u0000' in href) return null
    val rawPath = href.substringBefore('#')
    if (
        rawPath.startsWith("//") ||
        '?' in rawPath ||
        Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(rawPath)
    ) return null
    val decoded = runCatching {
        URLDecoder.decode(
            rawPath
                .replace("+", "%2B")
                .replace(Regex("%(?![0-9A-Fa-f]{2})"), "%25"),
            StandardCharsets.UTF_8.name()
        )
    }.getOrNull() ?: return null
    return normalizedDecodedEpubPath(decoded)
}

internal fun normalizedLocalEpubArchivePath(path: String): String? {
    if (path.isBlank()) return null
    return normalizedDecodedEpubPath(path)
}

private fun normalizedDecodedEpubPath(decoded: String): String? {
    if ('\\' in decoded || '\u0000' in decoded) return null
    val segments = ArrayDeque<String>()
    decoded.removePrefix("/").split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) return null else segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/").takeIf(String::isNotBlank)
}

internal fun epubReadingOrderFingerprint(readingOrderHrefs: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    readingOrderHrefs.forEach { href ->
        val bytes = href.toByteArray(StandardCharsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun epubRenderedPageLayoutKey(
    readingOrderHrefs: List<String>,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    contentPaddingLeftPx: Int,
    contentPaddingTopPx: Int,
    contentPaddingRightPx: Int,
    contentPaddingBottomPx: Int,
    densityDpi: Int,
    systemFontScale: Float,
    webViewVersion: String,
    twoPageSpreadActive: Boolean,
    spreadPageMarginCssPx: Double,
    contentLanguage: EpubContentLanguage,
    preferences: LibraryReaderPreferences
): String? {
    if (
        readingOrderHrefs.size !in 1..MAX_EPUB_RENDERED_PAGE_RESOURCES ||
        viewportWidthPx <= 0 ||
        viewportHeightPx <= 0 ||
        densityDpi <= 0 ||
        !systemFontScale.isFinite() ||
        systemFontScale <= 0f ||
        !spreadPageMarginCssPx.isFinite() ||
        spreadPageMarginCssPx < 0.0
    ) return null
    val normalizedHrefs = readingOrderHrefs.map { href ->
        normalizedLocalEpubResourcePath(href) ?: return null
    }
    if (normalizedHrefs.distinct().size != normalizedHrefs.size) return null
    val normalized = preferences.normalized()
    if (normalized.epubLayoutMode != ReaderLayoutMode.PAGINATED) return null
    val customFont = normalized.customFont
    val material = unambiguousPersistenceKey(
        "rendered-pages-v2",
        "readium-3.0.2",
        epubReadingOrderFingerprint(normalizedHrefs),
        viewportWidthPx.toString(),
        viewportHeightPx.toString(),
        contentPaddingLeftPx.coerceAtLeast(0).toString(),
        contentPaddingTopPx.coerceAtLeast(0).toString(),
        contentPaddingRightPx.coerceAtLeast(0).toString(),
        contentPaddingBottomPx.coerceAtLeast(0).toString(),
        densityDpi.toString(),
        systemFontScale.toRawBits().toString(),
        webViewVersion,
        normalized.fontFamily.name,
        customFont?.fileName.orEmpty(),
        customFont?.familyName.orEmpty(),
        normalized.fontScale.toRawBits().toString(),
        normalized.lineSpacing.toRawBits().toString(),
        normalized.wordSpacing.toRawBits().toString(),
        normalized.padding.top.toRawBits().toString(),
        normalized.padding.bottom.toRawBits().toString(),
        normalized.padding.left.toRawBits().toString(),
        normalized.padding.right.toRawBits().toString(),
        normalized.textAlignment.name,
        normalized.hyphenation.name,
        normalized.readingDirection.name,
        normalized.twoPageSpread.name,
        twoPageSpreadActive.toString(),
        spreadPageMarginCssPx.toRawBits().toString(),
        contentLanguage.source.name,
        contentLanguage.tag.orEmpty()
    )
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
    return "rendered-pages-v2:" + digest.joinToString("") { byte -> "%02x".format(byte) }
}

internal fun updateEpubRenderedPageLedger(
    current: EpubRenderedPageLedger?,
    layoutKey: String,
    readingOrderCount: Int,
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int
): EpubRenderedPageLedger? {
    if (
        layoutKey.isBlank() ||
        readingOrderCount !in 1..MAX_EPUB_RENDERED_PAGE_RESOURCES ||
        chapterIndex !in 0 until readingOrderCount ||
        pageCount !in 1..MAX_EPUB_RENDERED_PAGES_PER_RESOURCE ||
        pageIndex !in 0 until pageCount
    ) return current?.takeIf {
        it.layoutKey == layoutKey &&
            it.pageCounts.size == readingOrderCount &&
            it.pageCounts.all { count -> count in 0..MAX_EPUB_RENDERED_PAGES_PER_RESOURCE }
    }

    val counts = current
        ?.takeIf {
            it.layoutKey == layoutKey &&
                it.pageCounts.size == readingOrderCount &&
                it.pageCounts.all { count -> count in 0..MAX_EPUB_RENDERED_PAGES_PER_RESOURCE }
        }
        ?.pageCounts
        ?.toMutableList()
        ?: MutableList(readingOrderCount) { 0 }
    val previousCount = counts[chapterIndex]
    if (previousCount != 0 && previousCount != pageCount) {
        counts.fill(0)
    }
    counts[chapterIndex] = pageCount
    return EpubRenderedPageLedger(layoutKey = layoutKey, pageCounts = counts.toList())
}

internal fun renderedEpubOverallPosition(
    ledger: EpubRenderedPageLedger?,
    layoutKey: String,
    readingOrderCount: Int,
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int
): EpubOverallPosition? {
    val counts = ledger
        ?.takeIf { it.layoutKey == layoutKey }
        ?.pageCounts
        ?.takeIf { it.size == readingOrderCount && it.all { count -> count > 0 } }
        ?: return null
    if (
        readingOrderCount !in 1..MAX_EPUB_RENDERED_PAGE_RESOURCES ||
        chapterIndex !in counts.indices ||
        pageCount != counts[chapterIndex] ||
        pageIndex !in 0 until pageCount
    ) return null
    val total = counts.fold(0L) { sum, count -> sum + count }
    val number = counts.take(chapterIndex).fold(0L) { sum, count -> sum + count } + pageIndex + 1L
    if (total !in 1..Int.MAX_VALUE.toLong() || number !in 1..total) return null
    return EpubOverallPosition(number = number.toInt(), count = total.toInt())
}

internal fun shouldGenerateEpubContentPositions(file: File, readingOrderHrefs: List<String>): Boolean {
    if (!shouldGenerateEpubContentPositions(file.length(), readingOrderHrefs.size)) return false
    val boundedResources = readingOrderHrefs.map { href ->
        normalizedLocalEpubResourcePath(href) ?: return false
    }
    if (boundedResources.distinct().size != boundedResources.size) return false
    return runCatching {
        ZipFile(file).use { zip ->
            var entryCount = 0
            val archiveEntries = mutableMapOf<String, ZipEntry>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount += 1
                if (entryCount > MAX_EPUB_POSITION_ARCHIVE_ENTRIES) return@use false
                if (entry.isDirectory) continue
                val normalized = normalizedLocalEpubArchivePath(entry.name) ?: return@use false
                if (archiveEntries.put(normalized, entry) != null) return@use false
            }
            val boundedMarkup = boundedResources.toMutableSet()
            archiveEntries.keys.filterTo(boundedMarkup, ::isEpubPositionMarkup)
            val containerPath = "META-INF/container.xml"
            val containerEntry = archiveEntries[containerPath] ?: return@use false
            boundedMarkup += containerPath
            val containerXml = readBoundedZipMarkup(zip, containerEntry) ?: return@use false
            val packagePath = ROOTFILE_PATH_REGEX.find(containerXml)
                ?.groupValues
                ?.getOrNull(2)
                ?.let(::normalizedLocalEpubResourcePath)
                ?: return@use false
            val packageEntry = archiveEntries[packagePath] ?: return@use false
            boundedMarkup += packagePath
            val packageXml = readBoundedZipMarkup(zip, packageEntry) ?: return@use false
            val packageDirectory = packagePath.substringBeforeLast('/', missingDelimiterValue = "")
            ITEM_TAG_REGEX.findAll(packageXml).forEach { itemMatch ->
                val attributes = XML_ATTRIBUTE_REGEX.findAll(itemMatch.value).associate { match ->
                    match.groupValues[1].lowercase() to (match.groupValues[2].ifEmpty { match.groupValues[3] })
                }
                val mediaType = attributes["media-type"]?.lowercase().orEmpty()
                val isMarkup = mediaType in EPUB_POSITION_MARKUP_MEDIA_TYPES ||
                    attributes["properties"]?.split(Regex("\\s+"))?.contains("nav") == true
                if (isMarkup) {
                    val href = attributes["href"] ?: return@use false
                    val resolved = normalizedLocalEpubResourcePath(
                        if (packageDirectory.isBlank()) href else "$packageDirectory/$href"
                    ) ?: return@use false
                    if (resolved !in archiveEntries) return@use false
                    boundedMarkup += resolved
                }
            }
            var markupBytes = 0L
            boundedMarkup.forEach { resource ->
                val size = archiveEntries[resource]?.size ?: return@use false
                if (size < 0L || size > MAX_EPUB_POSITION_MARKUP_ENTRY_BYTES) return@use false
                if (markupBytes > MAX_EPUB_POSITION_MARKUP_TOTAL_BYTES - size) return@use false
                markupBytes += size
            }
            markupBytes in 1..MAX_EPUB_POSITION_MARKUP_TOTAL_BYTES
        }
    }.getOrDefault(false)
}

private val ROOTFILE_PATH_REGEX = Regex(
    "<rootfile\\b[^>]*\\bfull-path\\s*=\\s*([\"'])(.*?)\\1",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val ITEM_TAG_REGEX = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
private val XML_ATTRIBUTE_REGEX = Regex(
    "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')"
)
private val EPUB_POSITION_MARKUP_MEDIA_TYPES = setOf(
    "application/xhtml+xml",
    "application/x-dtbncx+xml",
    "application/xml",
    "text/xml",
    "text/html"
)

private fun isEpubPositionMarkup(name: String): Boolean = when (
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
) {
    "xhtml", "html", "htm", "xml", "opf", "ncx" -> true
    else -> false
}

private fun readBoundedZipMarkup(zip: ZipFile, entry: ZipEntry): String? {
    if (entry.size !in 0..MAX_EPUB_POSITION_MARKUP_ENTRY_BYTES) return null
    val maximum = MAX_EPUB_POSITION_MARKUP_ENTRY_BYTES.toInt()
    return zip.getInputStream(entry).buffered().use { input ->
        val output = ByteArrayOutputStream(minOf(maximum, entry.size.toInt().coerceAtLeast(1024)))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maximum) return@use null
            output.write(buffer, 0, count)
        }
        output.toString(StandardCharsets.UTF_8.name())
    }
}

/**
 * Keeps Readium's publication order. A backwards or unbounded table is less trustworthy than no
 * table, so callers omit Overall rather than sorting or partially accepting it.
 */
internal fun validatedEpubCanonicalPositionIndex(
    positions: List<EpubCanonicalPosition>,
    readingOrderHrefs: List<String>
): EpubCanonicalPositionIndex? {
    if (positions.isEmpty() || positions.size > MAX_EPUB_CONTENT_POSITIONS) return null
    val readingOrder = readingOrderHrefs.map { href ->
        normalizedLocalEpubResourcePath(href) ?: return null
    }
    if (readingOrder.isEmpty() || readingOrder.distinct().size != readingOrder.size) return null
    val readingOrderOrdinal = readingOrder.withIndex().associate { (index, href) -> href to index }
    var previous = -1.0
    var previousReadingOrderOrdinal = -1
    val previousResourceByHref = mutableMapOf<String, Double>()
    val accepted = ArrayList<EpubCanonicalPosition>(positions.size)
    val acceptedSourceIndices = ArrayList<Int>(positions.size)
    var previousKey: Triple<String, Double, Double>? = null
    positions.forEachIndexed { sourceIndex, position ->
        val total = position.totalProgression?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?: return null
        if (total < previous) return null
        val resource = position.resourceProgression
            ?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?: return null
        val href = normalizedLocalEpubResourcePath(position.href) ?: return null
        val ordinal = readingOrderOrdinal[href] ?: return null
        if (ordinal < previousReadingOrderOrdinal) return null
        if (resource < (previousResourceByHref[href] ?: -1.0)) return null
        val key = Triple(href, total, resource)
        if (key != previousKey) {
            accepted += EpubCanonicalPosition(href, total, resource)
            acceptedSourceIndices += sourceIndex
        }
        previousKey = key
        previous = total
        previousReadingOrderOrdinal = ordinal
        previousResourceByHref[href] = resource
    }
    if (accepted.isEmpty()) return null
    val ranges = mutableMapOf<String, IntRange>()
    accepted.forEachIndexed { index, position ->
        val href = position.href
        val current = ranges[href]
        ranges[href] = if (current == null) index..index else current.first..index
    }
    return EpubCanonicalPositionIndex(
        positions = accepted.toList(),
        totalProgressions = DoubleArray(accepted.size) { index ->
            requireNotNull(accepted[index].totalProgression)
        },
        resourceProgressions = DoubleArray(accepted.size) { index ->
            requireNotNull(accepted[index].resourceProgression)
        },
        rangesByHref = ranges.toMap(),
        sourceIndices = acceptedSourceIndices.toIntArray()
    )
}

internal fun validatedEpubContentPositionIndex(
    positions: List<Locator>,
    readingOrderHrefs: List<String>
): EpubContentPositionIndex? {
    val canonical = validatedEpubCanonicalPositionIndex(
        positions = positions.map { it.toEpubCanonicalPosition() },
        readingOrderHrefs = readingOrderHrefs
    ) ?: return null
    return EpubContentPositionIndex(
        positions = canonical.sourceIndices.map(positions::get),
        canonical = canonical
    )
}

internal fun Locator.toEpubCanonicalPosition(): EpubCanonicalPosition = EpubCanonicalPosition(
    href = href.toString(),
    totalProgression = locations.totalProgression,
    resourceProgression = locations.progression,
    contentPosition = locations.position
)

internal fun readiumOverallPosition(
    position: EpubCanonicalPosition,
    index: EpubCanonicalPositionIndex
): EpubOverallPosition? {
    val targetHref = normalizedLocalEpubResourcePath(position.href) ?: return null
    val targetResource = position.resourceProgression
        ?.takeIf { it.isFinite() && it in 0.0..1.0 }
    val resourceIndex = targetResource?.let { resource ->
        index.rangesByHref[targetHref]?.let { range ->
            selectReadiumPositionIndex(resource, index.resourceProgressions, range)
        }
    }
    val positionIndex = resourceIndex ?: return null
    return EpubOverallPosition(positionIndex + 1, index.positions.size)
}

internal fun readiumOverallPosition(
    locator: Locator,
    index: EpubContentPositionIndex
): EpubOverallPosition? = readiumOverallPosition(locator.toEpubCanonicalPosition(), index.canonical)

private suspend fun generateEpubContentPositionIndex(
    context: Context,
    sourceFile: File,
    expectedReadingOrderHrefs: List<String>
): EpubContentPositionIndex? {
    if (expectedReadingOrderHrefs.any { normalizedLocalEpubResourcePath(it) == null }) return null
    return when (val result = openReadiumEpub(context.applicationContext, sourceFile)) {
        is ReadiumEpubOpenResult.Error -> null
        is ReadiumEpubOpenResult.Opened -> try {
            val actualReadingOrder = result.publication.readingOrder.map { it.url().toString() }
            if (
                actualReadingOrder != expectedReadingOrderHrefs ||
                actualReadingOrder.any { normalizedLocalEpubResourcePath(it) == null }
            ) null else {
                validatedEpubContentPositionIndex(
                    positions = result.publication.positions(),
                    readingOrderHrefs = actualReadingOrder
                )
            }
        } finally {
            result.publication.close()
        }
    }
}

internal suspend fun loadBoundedEpubContentPositionIndex(
    context: Context,
    sourceFile: File,
    readingOrderHrefs: List<String>,
    sourceFingerprint: String? = null,
    timeoutMillis: Long = 10_000L
): EpubContentPositionIndex? {
    val eligible = withContext(Dispatchers.IO) {
        shouldGenerateEpubContentPositions(sourceFile, readingOrderHrefs)
    }
    if (!eligible) return null
    val fingerprint = sourceFingerprint ?: withContext(Dispatchers.IO) {
        runCatching { epubPublicationFingerprint(sourceFile) }.getOrNull()
    } ?: return null
    val canonicalPath = withContext(Dispatchers.IO) {
        runCatching { sourceFile.canonicalPath }.getOrNull()
    } ?: return null
    val cacheKey = unambiguousPersistenceKey(
        canonicalPath,
        fingerprint,
        epubReadingOrderFingerprint(readingOrderHrefs)
    )
    val generated = epubPositionSingleFlight.getOrStart(cacheKey) {
        try {
            generateEpubContentPositionIndex(
                context = context.applicationContext,
                sourceFile = sourceFile,
                expectedReadingOrderHrefs = readingOrderHrefs
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w("ReadiumEpubReader", "Unable to generate bounded EPUB content positions", error)
            null
        }
    }
    // The generator owns a separate Publication in a process-level single-flight job. A timeout
    // stops this caller waiting, while a rotation or TTS session can attach to the same job.
    return withTimeoutOrNull(timeoutMillis) { generated.await() }
}

internal suspend fun loadBoundedEpubContentPositionIndex(
    publication: Publication,
    sourceFile: File,
    timeoutMillis: Long = 10_000L
): EpubContentPositionIndex? {
    val readingOrderHrefs = publication.readingOrder.map { it.url().toString() }
    val eligible = withContext(Dispatchers.IO) {
        shouldGenerateEpubContentPositions(sourceFile, readingOrderHrefs)
    }
    if (!eligible) return null
    val positions = loadEpubPositionsSafely(timeoutMillis) {
        withContext(Dispatchers.IO) { publication.positions() }
    } ?: return null
    return validatedEpubContentPositionIndex(positions, readingOrderHrefs)
}

internal suspend fun <T> loadEpubPositionsSafely(
    timeoutMillis: Long,
    load: suspend () -> T
): T? = try {
    withTimeoutOrNull(timeoutMillis) { load() }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    runCatching {
        Log.w("ReadiumEpubReader", "Unable to load bounded EPUB content positions", error)
    }
    null
}

internal suspend fun openReadiumEpub(
    context: Context,
    file: File,
    ttsImageSettings: EpubTtsImageSettings? = null
): ReadiumEpubOpenResult = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        return@withContext ReadiumEpubOpenResult.Error(
            context.getString(R.string.epub_open_error_file_unavailable)
        )
    }

    val appContext = context.applicationContext
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val assetResult = assetRetriever.retrieve(file, MediaType.EPUB)
    val asset = assetResult.getOrNull()
        ?: return@withContext ReadiumEpubOpenResult.Error(
            context.getString(R.string.epub_open_error_read_failed)
        )
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )
    val publicationResult = PublicationOpener(publicationParser).open(
        asset = asset,
        allowUserInteraction = false,
        onCreatePublication = {
            ttsImageSettings?.let { imageSettings ->
                val currentFactory = servicesBuilder[ContentService::class]
                if (currentFactory !is EpubTtsImageContentServiceFactory) {
                    servicesBuilder[ContentService::class] = EpubTtsImageContentServiceFactory(
                        delegate = currentFactory,
                        settings = imageSettings
                    )
                }
            }
        }
    )
    val publication = publicationResult.getOrNull()
        ?: return@withContext ReadiumEpubOpenResult.Error(
            context.getString(R.string.epub_open_error_open_failed)
        )
    if (!publication.conformsTo(Publication.Profile.EPUB)) {
        publication.close()
        return@withContext ReadiumEpubOpenResult.Error(
            context.getString(R.string.epub_open_error_not_epub)
        )
    }
    ReadiumEpubOpenResult.Opened(publication)
}

internal fun readiumEpubReadingProgression(
    readingDirection: LibraryReadingDirection
): ReadiumReadingProgression {
    // LibraryReadingDirection controls physical tap navigation only. Keep EPUB
    // content/layout direction unchanged so Readium does not alter typography.
    return when (readingDirection) {
        LibraryReadingDirection.LEFT_TO_RIGHT,
        LibraryReadingDirection.RIGHT_TO_LEFT -> ReadiumReadingProgression.LTR
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal fun readiumFontFamily(fontFamily: EpubReaderFontFamily): ReadiumFontFamily? = when (fontFamily) {
    EpubReaderFontFamily.PUBLISHER_DEFAULT -> null
    EpubReaderFontFamily.SYSTEM_SERIF -> ReadiumFontFamily.SERIF
    EpubReaderFontFamily.SYSTEM_SANS_SERIF -> ReadiumFontFamily.SANS_SERIF
    EpubReaderFontFamily.SYSTEM_MONOSPACE -> ReadiumFontFamily.MONOSPACE
    EpubReaderFontFamily.ACCESSIBLE_DFA -> ReadiumFontFamily.ACCESSIBLE_DFA
    EpubReaderFontFamily.OPEN_DYSLEXIC -> ReadiumFontFamily.OPEN_DYSLEXIC
    EpubReaderFontFamily.CUSTOM -> null
}

/**
 * Base unit of the Readium page gutter in a spread. Readium pads every column with
 * `pageGutter * pageMargins`, so with a 1px base `pageMargins` is the page margin in CSS px and can
 * follow the reader margin setting live, without recreating the navigator.
 */
internal const val EPUB_SPREAD_PAGE_GUTTER_UNIT_CSS_PX = 1.0

/** Smallest margin on each side of a spread page so the two pages' text never touches. */
internal const val EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX = 12.0

/**
 * Margin on each side of each spread page, in CSS px (= dp). It is the same size the left/right
 * margin setting gives a single page on this window, so changing margins changes all four page
 * edges equally and both pages stay symmetric around the fold. It is capped at an eighth of the
 * window, so each page keeps at least half its width for text, as a single page does at 100%.
 */
internal fun epubSpreadPageMarginCssPx(windowWidthDp: Int, leftPercent: Float, rightPercent: Float): Double {
    val mean = (leftPercent.coerceIn(0f, 100f) + rightPercent.coerceIn(0f, 100f)) / 2f
    val width = windowWidthDp.coerceAtLeast(0)
    return maxOf(minOf(width * mean / 400.0, width / 8.0), EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX)
}

internal fun epubSpreadActive(twoPageSpread: Boolean, layoutMode: ReaderLayoutMode): Boolean =
    twoPageSpread && layoutMode == ReaderLayoutMode.PAGINATED

/**
 * ReadiumCSS only honours a two-column request above a 60em viewport. Inline Readium CSS
 * properties take precedence over that media query, so spreads also work on unfolded phones whose
 * inner display is narrower than 960 CSS px. Column gap stays zero to keep Readium's
 * viewport-width pagination exact; the gap between pages comes from the per-column page gutter.
 */
@OptIn(ExperimentalReadiumApi::class)
internal fun epubSpreadRsProperties(twoPageSpread: Boolean): RsProperties = if (twoPageSpread) {
    RsProperties(
        colCount = ReadiumCssColCount.TWO,
        pageGutter = ReadiumCssLength.Px(EPUB_SPREAD_PAGE_GUTTER_UNIT_CSS_PX),
        overrides = mapOf("--RS__colWidth" to "auto")
    )
} else {
    RsProperties()
}

/**
 * Left/right reader container padding percentages. A reflowable spread moves the horizontal margins
 * into each Readium column (see [epubSpreadPageMarginCssPx]) so both pages get the same margin on
 * both sides. Fixed-layout spreads are laid out by Readium itself and keep the container margins.
 */
internal fun epubHorizontalPaddingPercents(
    left: Float,
    right: Float,
    twoPageSpread: Boolean,
    fixedLayout: Boolean = false
): Pair<Float, Float> {
    if (twoPageSpread && !fixedLayout) return 0f to 0f
    return left.coerceIn(0f, 100f) to right.coerceIn(0f, 100f)
}

internal fun readiumEpubLineHeight(lineSpacing: Float): Double = lineSpacing
    .coerceIn(DEFAULT_EPUB_LINE_SPACING, MAX_EPUB_LINE_SPACING)
    .toDouble()

internal fun readiumEpubTextAlign(alignment: EpubTextAlignment): ReadiumTextAlign = when (alignment) {
    // START follows the text direction, so right-to-left books stay aligned to their own edge.
    EpubTextAlignment.START -> ReadiumTextAlign.START
    EpubTextAlignment.JUSTIFY -> ReadiumTextAlign.JUSTIFY
}

/** Base languages without system hyphenation patterns, or whose script changes Readium's layout (RTL, CJK). */
private val EPUB_NON_HYPHENATING_LANGUAGES = setOf(
    "ar", "he", "iw", "fa", "ur", "yi", "ji", "ps", "sd", "ckb", "dv", "ug", "zh", "ja", "ko"
)

/**
 * The browser hyphenates only text whose language is known. Books that declare no language get the
 * device language so hyphenation still works. It is applied only for languages that hyphenate,
 * because Readium also derives vertical text and right-to-left layout from the content language.
 */
internal fun epubFallbackContentLanguage(publicationLanguages: List<String>, deviceLocale: Locale): String? {
    if (publicationLanguages.any { it.isNotBlank() }) return null
    val base = deviceLocale.language.lowercase(Locale.ROOT)
    if (base.isBlank() || base == "und" || base in EPUB_NON_HYPHENATING_LANGUAGES) return null
    return deviceLocale.toLanguageTag()
}

internal fun readiumEpubWordSpacing(wordSpacing: Float): Double = wordSpacing
    .coerceIn(DEFAULT_EPUB_WORD_SPACING, MAX_EPUB_WORD_SPACING)
    .toDouble()

internal fun readiumEpubParagraphSpacing(paragraphSpacing: Float): Double = paragraphSpacing
    .coerceIn(DEFAULT_EPUB_PARAGRAPH_SPACING, MAX_EPUB_PARAGRAPH_SPACING)
    .toDouble()

@OptIn(ExperimentalReadiumApi::class)
internal fun readiumPreferences(
    theme: EpubReaderTheme,
    fontScale: Float,
    lineSpacing: Float = DEFAULT_EPUB_LINE_SPACING,
    readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
    layoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    fontFamily: EpubReaderFontFamily = EpubReaderFontFamily.PUBLISHER_DEFAULT,
    wordSpacing: Float = DEFAULT_EPUB_WORD_SPACING,
    twoPageSpread: Boolean = false,
    spreadPageMarginCssPx: Double = EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX,
    textAlignment: EpubTextAlignment = EpubTextAlignment.START,
    hyphenation: EpubHyphenation = EpubHyphenation.AUTO,
    fallbackLanguage: String? = null,
    customBackgroundColor: Int? = null,
    customTextColor: Int? = null,
    backgroundTheme: EpubReaderTheme = theme,
    useCustomBackground: Boolean = customBackgroundColor != null,
    useCustomTextColor: Boolean = customTextColor != null,
    systemDarkMode: Boolean? = null,
    paragraphSpacing: Float = DEFAULT_EPUB_PARAGRAPH_SPACING
): EpubPreferences {
    val activeCustomBackground = customBackgroundColor.takeIf { useCustomBackground }
    val effectiveTheme = activeCustomBackground?.let(::customReaderBackgroundReadiumTheme) ?: backgroundTheme
    val effectiveBackground = activeCustomBackground ?: backgroundTheme.backgroundColor
    val defaultTextColor = defaultReaderTextColor(
        customBackgroundActive = activeCustomBackground != null,
        presetTextColor = cssHexColorInt(backgroundTheme.foregroundCss),
        systemDarkMode = systemDarkMode
    )
    val effectiveTextColor = readerTextColor(
        customTextColor = customTextColor,
        useCustomTextColor = useCustomTextColor,
        defaultTextColor = defaultTextColor
    )
    return EpubPreferences(
        backgroundColor = ReadiumColor(effectiveBackground),
        textColor = ReadiumColor(effectiveTextColor),
        theme = when (effectiveTheme) {
            EpubReaderTheme.Light -> ReadiumTheme.LIGHT
            EpubReaderTheme.Sepia -> ReadiumTheme.SEPIA
            EpubReaderTheme.Dark, EpubReaderTheme.OledBlack -> ReadiumTheme.DARK
        },
        fontSize = fontScale.coerceIn(MIN_EPUB_FONT_SCALE, MAX_EPUB_FONT_SCALE).toDouble(),
        lineHeight = readiumEpubLineHeight(lineSpacing),
        wordSpacing = readiumEpubWordSpacing(wordSpacing),
        paragraphSpacing = readiumEpubParagraphSpacing(paragraphSpacing),
        textAlign = readiumEpubTextAlign(textAlignment),
        hyphens = epubHyphenationActive(textAlignment, hyphenation),
        language = fallbackLanguage?.let(::ReadiumLanguage),
        fontFamily = readiumFontFamily(fontFamily),
        readingProgression = readiumEpubReadingProgression(readingDirection),
        // Spreads give each column its own margin (in units of the 1px spread page gutter).
        pageMargins = if (epubSpreadActive(twoPageSpread, layoutMode)) {
            spreadPageMarginCssPx.coerceAtLeast(EPUB_SPREAD_MIN_PAGE_MARGIN_CSS_PX) / EPUB_SPREAD_PAGE_GUTTER_UNIT_CSS_PX
        } else 0.0,
        columnCount = if (epubSpreadActive(twoPageSpread, layoutMode)) ColumnCount.TWO else ColumnCount.ONE,
        // Fixed-layout only. Readium 3.x treats Spread.AUTO as single page, so it must be explicit.
        spread = if (epubSpreadActive(twoPageSpread, layoutMode)) Spread.ALWAYS else Spread.NEVER,
        scroll = layoutMode == ReaderLayoutMode.CONTINUOUS,
        // Readium only applies lineHeight and the other user CSS overrides when
        // publisher styles are disabled.
        publisherStyles = false
    )
}

internal fun cssHexColorInt(value: String): Int {
    val hex = value.removePrefix("#")
    require(hex.length == 6) { "Expected a six-digit CSS color" }
    return (0xFF000000L or hex.toLong(16)).toInt()
}

internal fun readiumOverallPercent(
    @Suppress("UNUSED_PARAMETER")
    totalProgression: Double?,
    resourceProgression: Double?,
    chapterIndex: Int,
    chapterCount: Int,
    overallPosition: EpubOverallPosition? = null,
    renderedPagePosition: EpubOverallPosition? = null,
    navigatorAtEnd: Boolean = false
): Float {
    val safeChapterCount = chapterCount.coerceAtLeast(1)
    val safeChapterIndex = chapterIndex.coerceIn(0, safeChapterCount - 1)
    val safeResourceProgression = resourceProgression
        ?.takeIf { it.isFinite() && it in 0.0..1.0 }
    val semanticEnd = navigatorAtEnd ||
        (safeChapterIndex == safeChapterCount - 1 &&
            (safeResourceProgression ?: 0.0) >= 1.0 - EPUB_END_EPSILON)
    if (semanticEnd) return 100f
    renderedPagePosition?.takeIf { it.count > 0 && it.number in 1..it.count }?.let { overall ->
        if (overall.count == 1) return 0f
        return ((overall.number - 1).toDouble() / (overall.count - 1).toDouble() * 100.0)
            .toFloat()
            .coerceIn(0f, Math.nextDown(100f))
    }
    overallPosition?.takeIf { it.count > 0 && it.number in 1..it.count }?.let { overall ->
        // A generated N/N position starts the final content chunk. It is not proof that the
        // publication was finished, so this fallback deliberately remains below 100 percent.
        return ((overall.number - 1).toDouble() / overall.count.toDouble() * 100.0).toFloat()
    }
    return (((safeChapterIndex + (safeResourceProgression ?: 0.0)) / safeChapterCount) * 100.0)
        .toFloat()
        .coerceIn(0f, Math.nextDown(100f))
}

/** Renderers lay a book out differently, so smaller percent differences are drift, not newer reading. */
internal const val SERVER_PROGRESS_MIN_DIFFERENCE_PERCENT = 2f

/**
 * A server value this close to what this device last reported is this device's own write. The
 * server may round or truncate what it stores, so this is wider than a float comparison.
 */
internal const val OWN_REPORT_TOLERANCE_PERCENT = 1f

/**
 * Whether progress recorded on the server should replace the exact locator saved here.
 *
 * Only progress ahead of the locator is followed, as when a book is reopened elsewhere in the app:
 * a position that merely moved back, or a reset written by another client, never pulls this device
 * backwards. Server progress that matches what this device last reported is its own write and
 * leaves the locator alone. A lead within [SERVER_PROGRESS_MIN_DIFFERENCE_PERCENT] is renderer
 * drift, not reading.
 */
internal fun shouldPreferServerProgress(
    serverPercent: Float?,
    lastReportedPercent: Float?,
    locatorPercent: Float?,
    preferServerProgress: Boolean = true
): Boolean {
    if (!preferServerProgress || serverPercent == null || locatorPercent == null) return false
    if (lastReportedPercent != null &&
        abs(serverPercent - lastReportedPercent) <= OWN_REPORT_TOLERANCE_PERCENT
    ) {
        return false
    }
    return serverPercent - locatorPercent > SERVER_PROGRESS_MIN_DIFFERENCE_PERCENT
}

/**
 * Tracks when the narration position stored on this device, replaced by newer server progress, can
 * be dropped. That waits until the reader has moved to the server position and the narration service
 * is connected, so narration of this book that is already running keeps its position. The service
 * binds asynchronously, so either can happen first. A pending cleanup is carried across activity
 * recreation through [isPending], since the recreated reader no longer sees the server progress.
 */
internal class StaleNarrationPositionCleanup {
    private var resumedFromServer = false
    private var readerOnServerPosition = false

    val isPending: Boolean get() = resumedFromServer

    fun serverProgressWon() {
        resumedFromServer = true
        readerOnServerPosition = false
    }

    fun readerMoved() {
        if (resumedFromServer) readerOnServerPosition = true
    }

    /** Whether to drop the stored narration position now; decided once per server resume. */
    fun shouldDrop(serviceConnected: Boolean, narrationActive: () -> Boolean): Boolean {
        if (!resumedFromServer || !readerOnServerPosition || !serviceConnected) return false
        resumedFromServer = false
        readerOnServerPosition = false
        return !narrationActive()
    }
}

/**
 * Overall progression (0..1) for a position inside one resource, read from the book's own positions
 * in that resource rather than assuming equal chapter lengths. Locators written by narration or
 * annotations lack a total progression and need this. Each candidate is (resource progression,
 * total progression).
 */
internal fun progressionFromPositions(
    resourceProgression: Double?,
    resourcePositions: List<Pair<Double?, Double?>>
): Double? {
    val index = selectReadiumPositionIndex(
        targetProgression = resourceProgression ?: 0.0,
        totalProgressions = resourcePositions.map { it.first }
    ) ?: return null
    return resourcePositions[index].second
}

internal fun isRenderedEpubPublicationEnd(
    layoutMode: ReaderLayoutMode,
    pageIndex: Int,
    pageCount: Int,
    chapterIndex: Int,
    chapterCount: Int
): Boolean = layoutMode == ReaderLayoutMode.PAGINATED &&
    pageCount > 0 &&
    pageIndex == pageCount - 1 &&
    chapterCount > 0 &&
    chapterIndex == chapterCount - 1

internal fun shouldCancelFinishedEpubSwipe(
    layoutMode: ReaderLayoutMode,
    horizontalOffset: Float,
    pagingTouchSlop: Int,
    pageIndex: Int,
    pageCount: Int,
    chapterIndex: Int,
    chapterCount: Int
): Boolean {
    if (layoutMode != ReaderLayoutMode.PAGINATED) return false
    if (abs(horizontalOffset) <= pagingTouchSlop.coerceAtLeast(0)) return true
    val atPublicationStart = chapterIndex == 0 && pageIndex == 0
    val atPublicationEnd = pageCount > 0 && pageIndex == pageCount - 1 &&
        chapterCount > 0 && chapterIndex == chapterCount - 1
    // EPUB layout progression remains LTR in this app. A positive drag asks for the previous
    // rendered page and a negative drag asks for the next one.
    return (horizontalOffset > 0f && atPublicationStart) ||
        (horizontalOffset < 0f && atPublicationEnd)
}

internal fun sameEpubRenderedLocation(
    left: EpubCanonicalPosition?,
    right: EpubCanonicalPosition
): Boolean {
    left ?: return false
    val leftHref = normalizedLocalEpubResourcePath(left.href) ?: return false
    val rightHref = normalizedLocalEpubResourcePath(right.href) ?: return false
    if (leftHref != rightHref) return false
    val leftProgression = left.resourceProgression ?: return false
    val rightProgression = right.resourceProgression ?: return false
    return leftProgression.isFinite() && rightProgression.isFinite() &&
        kotlin.math.abs(leftProgression - rightProgression) <= EPUB_END_EPSILON
}

internal fun sameEpubSemanticAnchor(
    left: EpubCanonicalPosition?,
    right: EpubCanonicalPosition
): Boolean {
    left ?: return false
    val leftHref = normalizedLocalEpubResourcePath(left.href) ?: return false
    val rightHref = normalizedLocalEpubResourcePath(right.href) ?: return false
    if (leftHref != rightHref) return false
    val leftContentPosition = left.contentPosition
    val rightContentPosition = right.contentPosition
    val leftTotalProgression = left.totalProgression
    val rightTotalProgression = right.totalProgression
    return if (
        leftContentPosition != null &&
        rightContentPosition != null &&
        leftTotalProgression?.isFinite() == true &&
        rightTotalProgression?.isFinite() == true
    ) {
        leftContentPosition == rightContentPosition &&
            kotlin.math.abs(leftTotalProgression - rightTotalProgression) <= EPUB_END_EPSILON
    } else {
        sameEpubRenderedLocation(left, right)
    }
}

internal fun shouldRestoreLegacyEpubLocator(
    hasCanonicalIdentity: Boolean,
    staleSavedStateRejected: Boolean
): Boolean = !hasCanonicalIdentity && !staleSavedStateRejected

internal fun shouldRunEpubRendererProbe(
    lifecycleStarted: Boolean,
    deviceInteractive: Boolean
): Boolean = lifecycleStarted && deviceInteractive

internal fun shouldApplyEpubPagination(
    validPagination: Boolean,
    visualLocatorAccepted: Boolean
): Boolean = validPagination && visualLocatorAccepted

internal fun shouldConfirmEpubProgressDisplay(
    layoutMode: ReaderLayoutMode,
    paginationApplied: Boolean,
    visualLocatorApplied: Boolean
): Boolean = paginationApplied || (layoutMode != ReaderLayoutMode.PAGINATED && visualLocatorApplied)

internal fun shouldApplyEpubLocatorSeparately(
    layoutMode: ReaderLayoutMode,
    hasLocator: Boolean,
    paginationApplied: Boolean,
    paginationMatchesLocator: Boolean
): Boolean = hasLocator &&
    layoutMode != ReaderLayoutMode.PAGINATED &&
    (!paginationApplied || !paginationMatchesLocator)

/**
 * Keeps paginated renderer recovery separate from reading activity. Readium can reflow and
 * re-emit a different page count or resource progression after STOP/START without the reader
 * moving. Canonical progress remains protected until an app- or navigator-owned movement is
 * requested and a matching visual update arrives.
 */
internal class EpubPassiveRecoveryGuard {
    private sealed interface ExpectedMovement {
        data class PageOrChapter(val inputFinished: Boolean = false) : ExpectedMovement
        data class SemanticTarget(val target: EpubCanonicalPosition) : ExpectedMovement
    }

    private var preservedPosition: EpubReaderPosition? = null
    private var preservedCanonicalPosition: EpubCanonicalPosition? = null
    private var expectedMovement: ExpectedMovement? = null
    private var expectedMovementUntilMillis = Long.MIN_VALUE

    val positionToPreserve: EpubReaderPosition?
        get() = preservedPosition

    val isProtectingCanonicalProgress: Boolean
        get() = preservedPosition != null

    fun isMovementExpected(nowMillis: Long = 0L): Boolean {
        if (nowMillis > expectedMovementUntilMillis) cancelExpectedMovement()
        return expectedMovement != null
    }

    fun begin(position: EpubReaderPosition?) {
        val canonical = position?.toEpubCanonicalPosition()
        preservedPosition = position?.takeIf {
            it.updatedAtMillis > 0L && canonical != null
        }
        preservedCanonicalPosition = canonical.takeIf { preservedPosition != null }
        expectedMovement = null
        expectedMovementUntilMillis = Long.MIN_VALUE
    }

    fun expectPageOrChapterMovement(untilMillis: Long = Long.MAX_VALUE) {
        if (preservedPosition != null) {
            expectedMovement = ExpectedMovement.PageOrChapter()
            expectedMovementUntilMillis = untilMillis
        }
    }

    fun finishPageOrChapterMovement() {
        val movement = expectedMovement as? ExpectedMovement.PageOrChapter ?: return
        expectedMovement = movement.copy(inputFinished = true)
    }

    fun expectSemanticMovement(
        target: EpubCanonicalPosition,
        untilMillis: Long = Long.MAX_VALUE
    ) {
        if (preservedPosition != null) {
            expectedMovement = ExpectedMovement.SemanticTarget(target)
            expectedMovementUntilMillis = untilMillis
        }
    }

    fun cancelExpectedMovement() {
        expectedMovement = null
        expectedMovementUntilMillis = Long.MIN_VALUE
    }

    fun shouldCommitVisualUpdate(
        chapterIndex: Int,
        pageIndex: Int,
        candidate: EpubCanonicalPosition,
        nowMillis: Long = 0L
    ): Boolean {
        val preserved = preservedPosition ?: return true
        if (nowMillis > expectedMovementUntilMillis) {
            cancelExpectedMovement()
            return false
        }
        return when (val expected = expectedMovement) {
            null -> false
            is ExpectedMovement.PageOrChapter -> {
                val moved = chapterIndex != preserved.chapterIndex || pageIndex != preserved.pageIndex
                if (!moved && expected.inputFinished) cancelExpectedMovement()
                moved
            }
            is ExpectedMovement.SemanticTarget -> {
                val target = expected.target
                val targetHref = normalizedLocalEpubResourcePath(target.href)
                val candidateHref = normalizedLocalEpubResourcePath(candidate.href)
                val pageMoved = chapterIndex != preserved.chapterIndex || pageIndex != preserved.pageIndex
                val targetReached = targetHref != null && targetHref == candidateHref && when {
                    pageMoved && target.resourceProgression == null -> true
                    pageMoved -> sameEpubSemanticAnchor(target, candidate)
                    else -> sameEpubRenderedLocation(target, candidate)
                }
                targetReached && !sameEpubRenderedLocation(preservedCanonicalPosition, candidate)
            }
        }
    }

    fun committed(positionToProtect: EpubReaderPosition? = null) = begin(positionToProtect)
}

internal fun EpubReaderPosition.toEpubCanonicalPosition(): EpubCanonicalPosition? {
    val json = locatorJson?.let(::boundedReaderLocatorJson) ?: return null
    return runCatching {
        val root = JSONObject(json)
        val locations = root.optJSONObject("locations") ?: JSONObject()
        EpubCanonicalPosition(
            href = root.optString("href"),
            totalProgression = locations.optDouble("totalProgression", Double.NaN)
                .takeIf(Double::isFinite),
            resourceProgression = locations.optDouble("progression", Double.NaN)
                .takeIf(Double::isFinite),
            contentPosition = locations.optInt("position", Int.MIN_VALUE)
                .takeUnless { it == Int.MIN_VALUE }
        ).takeIf { normalizedLocalEpubResourcePath(it.href) != null }
    }.getOrNull()
}

internal fun epubPositionMatchesOpeningLocator(
    position: EpubReaderPosition?,
    openingLocator: Locator
): Boolean = epubPositionMatchesOpeningPosition(position, openingLocator.toEpubCanonicalPosition())

internal fun epubPositionMatchesOpeningPosition(
    position: EpubReaderPosition?,
    openingPosition: EpubCanonicalPosition
): Boolean {
    val stored = position?.toEpubCanonicalPosition() ?: return false
    return sameEpubRenderedLocation(stored, openingPosition)
}

internal fun epubPositionToPreserveBeforeRendererConfirmation(
    passiveRecoveryPosition: EpubReaderPosition? = null,
    validatedDisplayPosition: EpubReaderPosition?,
    pendingClaimedPosition: EpubReaderPosition?
): EpubReaderPosition? = passiveRecoveryPosition ?: validatedDisplayPosition ?: pendingClaimedPosition

internal fun epubProgressPercentForSavedState(
    preservedPosition: EpubReaderPosition?,
    currentPercent: Float
): Float = if (preservedPosition != null) {
    preservedPosition.progressPercent ?: Float.NaN
} else {
    currentPercent
}

/**
 * Restores only the footer's last confirmed display state while a recreated renderer starts.
 * The returned value is never used as a new locator, writer snapshot, or reading-session event.
 */
internal fun restoredEpubDisplayProgress(
    position: EpubReaderPosition?,
    openingLocator: Locator,
    openingChapterIndex: Int,
    chapterCount: Int,
    layoutMode: ReaderLayoutMode,
    renderedLayoutKey: String?
): EpubReaderProgressStatus? = restoredEpubDisplayProgress(
    position = position,
    savedPosition = position?.locatorJson
        ?.let(::boundedReaderLocatorJson)
        ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }
        ?.toEpubCanonicalPosition(),
    openingPosition = openingLocator.toEpubCanonicalPosition(),
    openingChapterIndex = openingChapterIndex,
    chapterCount = chapterCount,
    layoutMode = layoutMode,
    renderedLayoutKey = renderedLayoutKey
)

internal fun restoredEpubDisplayProgress(
    position: EpubReaderPosition?,
    savedPosition: EpubCanonicalPosition?,
    openingPosition: EpubCanonicalPosition,
    openingChapterIndex: Int,
    chapterCount: Int,
    layoutMode: ReaderLayoutMode,
    renderedLayoutKey: String?
): EpubReaderProgressStatus? {
    position ?: return null
    if (position.updatedAtMillis <= 0L || chapterCount <= 0) return null
    if (position.chapterIndex !in 0 until chapterCount) return null
    if (position.chapterIndex != openingChapterIndex) return null
    if (position.pageCount <= 0 || position.pageIndex !in 0 until position.pageCount) return null
    val percent = position.progressPercent
        ?.takeIf { it.isFinite() && it in 0f..100f }
        ?: return null
    if (position.locatorJson == null || !sameEpubRenderedLocation(savedPosition, openingPosition)) return null
    if (
        layoutMode == ReaderLayoutMode.PAGINATED &&
        (renderedLayoutKey == null || position.renderedPageLayoutKey != renderedLayoutKey)
    ) return null
    val renderedOverall = renderedLayoutKey?.let { layoutKey ->
        position.renderedPageLayoutKey
            ?.takeIf { it == layoutKey }
            ?.let { storedLayoutKey -> EpubRenderedPageLedger(storedLayoutKey, position.renderedPageCounts) }
            ?.let { ledger ->
                renderedEpubOverallPosition(
                    ledger = ledger,
                    layoutKey = layoutKey,
                    readingOrderCount = chapterCount,
                    chapterIndex = position.chapterIndex,
                    pageIndex = position.pageIndex,
                    pageCount = position.pageCount
                )
            }
    }
    return epubReaderProgressStatus(
        chapterIndex = position.chapterIndex,
        chapterCount = chapterCount,
        pageIndex = position.pageIndex,
        pageCount = position.pageCount,
        completionPercent = percent,
        overallPositionNumber = renderedOverall?.number,
        overallPositionCount = renderedOverall?.count,
        layoutMode = layoutMode
    )
}

internal fun passiveEpubDisplayProgress(
    position: EpubReaderPosition?,
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    pageCount: Int,
    renderedOverallPosition: EpubOverallPosition?,
    layoutMode: ReaderLayoutMode
): EpubReaderProgressStatus? {
    val percent = position?.progressPercent
        ?.takeIf { it.isFinite() && it in 0f..100f }
        ?: return null
    if (chapterCount <= 0 || chapterIndex !in 0 until chapterCount) return null
    if (pageCount <= 0 || pageIndex !in 0 until pageCount) return null
    return epubReaderProgressStatus(
        chapterIndex = chapterIndex,
        chapterCount = chapterCount,
        pageIndex = pageIndex,
        pageCount = pageCount,
        completionPercent = percent,
        overallPositionNumber = renderedOverallPosition?.number,
        overallPositionCount = renderedOverallPosition?.count,
        layoutMode = layoutMode
    )
}

internal fun selectReadiumPositionIndex(
    targetProgression: Double?,
    totalProgressions: List<Double?>
): Int? {
    val target = targetProgression?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: return null
    if (totalProgressions.isEmpty()) return null
    val values = totalProgressions.map { progression ->
        progression?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: return null
    }
    if (values.zipWithNext().any { (left, right) -> right < left }) return null
    return selectReadiumPositionIndex(target, values.toDoubleArray(), values.indices)
}

internal fun selectReadiumPositionIndex(
    targetProgression: Double?,
    progressions: DoubleArray,
    range: IntRange = progressions.indices
): Int? {
    val target = targetProgression?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: return null
    if (progressions.isEmpty() || range.isEmpty() || range.first < 0 || range.last >= progressions.size) {
        return null
    }
    var low = range.first
    var high = range.last + 1
    while (low < high) {
        val middle = (low + high) ushr 1
        if (progressions[middle] <= target) low = middle + 1 else high = middle
    }
    return (low - 1).coerceAtLeast(range.first)
}

internal fun selectCanonicalEpubPositionIndex(
    progressPercent: Float?,
    index: EpubContentPositionIndex?
): Int? = selectCanonicalEpubPositionIndex(progressPercent, index?.canonical)

internal fun selectCanonicalEpubPositionIndex(
    progressPercent: Float?,
    index: EpubCanonicalPositionIndex?
): Int? {
    val positionCount = index?.positions?.size?.takeIf { it > 0 } ?: return null
    val percent = progressPercent
        ?.takeIf(Float::isFinite)
        ?.coerceIn(0f, 100f)
        ?: return null
    if (percent >= 100f) return positionCount - 1
    val scaled = percent.toDouble() / 100.0 * positionCount.toDouble()
    // Canonical percentages are serialized as Float. Snap values within one Float ULP of an
    // ordinal boundary before flooring so every saved (N - 1) / count value round-trips.
    val nearest = kotlin.math.round(scaled)
    val roundTripTolerance = Math.ulp(percent).toDouble() * positionCount.toDouble() / 100.0 + 1e-12
    val ordinal = if (kotlin.math.abs(scaled - nearest) <= roundTripTolerance) {
        nearest.toInt()
    } else {
        kotlin.math.floor(scaled).toInt()
    }
    return ordinal.coerceIn(0, positionCount - 1)
}

internal class LatestEpubPositionWriter<T>(
    private val scope: CoroutineScope,
    private val onIdleLockedForTest: () -> Unit = {},
    private val write: suspend (sequence: Long, snapshot: T) -> Boolean
) {
    private data class Pending<T>(val sequence: Long, val snapshot: T)

    private val lock = Any()
    private var highestSubmittedSequence = Long.MIN_VALUE
    private var pending: Pending<T>? = null
    private var worker: Deferred<Boolean>? = null
    private var lastResult = true

    fun submit(sequence: Long, snapshot: T) {
        synchronized(lock) {
            if (sequence <= highestSubmittedSequence) return
            highestSubmittedSequence = sequence
            pending = Pending(sequence, snapshot)
            if (worker == null) {
                worker = scope.async(start = CoroutineStart.LAZY) { drain() }.also { it.start() }
            }
        }
    }

    suspend fun flush(): Boolean {
        var successful = true
        while (true) {
            val active = synchronized(lock) { worker } ?: return successful && lastResult
            successful = active.await() && successful
        }
    }

    private suspend fun drain(): Boolean {
        var successful = true
        while (true) {
            val next = synchronized(lock) {
                pending?.also { pending = null } ?: run {
                    onIdleLockedForTest()
                    lastResult = successful
                    worker = null
                    null
                }
            }
            if (next == null) return successful
            successful = runCatching { write(next.sequence, next.snapshot) }
                .getOrDefault(false) && successful
        }
    }
}

internal suspend fun awaitEpubPositionCloseFlush(
    flush: suspend () -> Boolean
): Boolean = withContext(NonCancellable) { flush() }

internal fun effectiveReaderTopSpace(
    normalTopPadding: Int,
    bannerHeight: Int,
    isPreview: Boolean
): Int = if (isPreview) maxOf(normalTopPadding, bannerHeight) else normalTopPadding

internal fun occupiedPreviewBannerBottom(statusBarTop: Int, bannerHeight: Int): Int =
    statusBarTop.coerceAtLeast(0) + bannerHeight.coerceAtLeast(0)

internal data class ReadiumEpubProgressResult(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val percent: Float?,
    val locatorJson: String?
)

internal fun epubProgressResultDuringRecovery(
    preservedPosition: EpubReaderPosition?,
    currentChapterIndex: Int,
    currentPageIndex: Int,
    currentPageCount: Int,
    currentPercent: Float,
    currentLocatorJson: String?
): ReadiumEpubProgressResult = if (preservedPosition != null) {
    ReadiumEpubProgressResult(
        chapterIndex = preservedPosition.chapterIndex,
        pageIndex = preservedPosition.pageIndex,
        pageCount = preservedPosition.pageCount,
        percent = preservedPosition.progressPercent,
        locatorJson = boundedReaderLocatorJson(preservedPosition.locatorJson)
    )
} else {
    ReadiumEpubProgressResult(
        chapterIndex = currentChapterIndex,
        pageIndex = currentPageIndex,
        pageCount = currentPageCount,
        percent = currentPercent,
        locatorJson = boundedReaderLocatorJson(currentLocatorJson)
    )
}

internal const val DEFAULT_HIGHLIGHT_COLOR = "yellow"
internal const val DEFAULT_HIGHLIGHT_STYLE = "highlight"
internal const val HIGHLIGHT_DECORATION_GROUP = "bookorbit-highlights"

internal fun readiumEpubImageColorOverrideScript(): String = """
    (function() {
      const styleId = 'bookorbit-readium-epub-image-color-preservation';
      if (document.getElementById(styleId)) return 'already-installed';
      const style = document.createElement('style');
      style.id = styleId;
      style.textContent = `
        img, svg {
          filter: none !important;
          -webkit-filter: none !important;
          mix-blend-mode: normal !important;
        }
      `;
      document.head.appendChild(style);
      return 'installed';
    })();
""".trimIndent()

private const val EPUB_IMAGE_GESTURE_ASSET = "epub-image-gesture.js"
private const val EPUB_IMAGE_GESTURE_PROBE_SCRIPT =
    "(function(){const k='__bookorbitImageGesture';const v=window[k]||null;window[k]=null;return v;})()"

internal fun epubCurrentDocumentInstallScript(
    gestureScript: String,
    oledText: Boolean = false
): String = buildString {
    append(readiumEpubImageColorOverrideScript())
    if (oledText) {
        append("\n")
        append(readiumEpubOledTextStyleScript())
    }
    append("\n")
    append(gestureScript)
}

internal suspend fun runEpubCurrentDocumentBridgeLoop(
    installScript: () -> String,
    evaluateJavascript: suspend (String) -> String?,
    onGesture: (String) -> Unit,
    pollIntervalMillis: Long = 250L
) {
    while (currentCoroutineContext().isActive) {
        runCatching { evaluateJavascript(installScript()) }
        val event = runCatching {
            evaluateJavascript(EPUB_IMAGE_GESTURE_PROBE_SCRIPT)
                ?.let(::decodeJavascriptString)
        }.getOrNull()
        if (event != null) onGesture(event)
        delay(pollIntervalMillis)
    }
}

internal fun shouldOpenEpubImageViewer(gesture: String): Boolean = gesture == "long_press"

internal data class HighlightChoice(
    val label: String,
    @StringRes val labelRes: Int,
    val color: String,
    val style: String,
    val previewColor: Int
)

internal fun epubHighlightChoices() = listOf(
    HighlightChoice("Yellow highlight", R.string.epub_highlight_yellow, "yellow", "highlight", 0xFFFFEB3B.toInt()),
    HighlightChoice("Green highlight", R.string.epub_highlight_green, "green", "highlight", 0xFF4CAF50.toInt()),
    HighlightChoice("Blue highlight", R.string.epub_highlight_blue, "blue", "highlight", 0xFF2196F3.toInt()),
    HighlightChoice("Pink highlight", R.string.epub_highlight_pink, "pink", "highlight", 0xFFE91E63.toInt()),
    HighlightChoice("Yellow underline", R.string.epub_highlight_yellow_underline, "yellow", "underline", 0xFFFFEB3B.toInt())
)

internal enum class EpubSelectionActionPresentation {
    ALWAYS,
    IF_ROOM,
    OVERFLOW
}

internal data class EpubSelectionAction(
    val label: String,
    val presentation: EpubSelectionActionPresentation = EpubSelectionActionPresentation.IF_ROOM
)

internal data class CapturedEpubSelection(
    val selection: Selection,
    val cfi: String?,
    val mediaOverlayTextFragment: String? = null,
    val ttsLocator: Locator? = null
)

internal fun locatorForTtsSelection(locator: Locator, cssSelector: String?): Locator {
    val selector = cssSelector?.takeIf { it.isNotBlank() } ?: return locator
    return locator.copyWithLocations(
        otherLocations = locator.locations.otherLocations + ("cssSelector" to selector)
    )
}

internal fun epubSelectionActions(
    mediaOverlayAvailable: Boolean = false,
    textToSpeechAvailable: Boolean = false
): List<EpubSelectionAction> = buildList {
    add(EpubSelectionAction("Copy"))
    add(EpubSelectionAction("Share"))
    if (mediaOverlayAvailable) {
        add(EpubSelectionAction("Play narration", EpubSelectionActionPresentation.ALWAYS))
    }
    if (textToSpeechAvailable) {
        add(EpubSelectionAction("Listen from here", EpubSelectionActionPresentation.ALWAYS))
    }
    add(EpubSelectionAction("Web search", EpubSelectionActionPresentation.OVERFLOW))
    add(EpubSelectionAction("Highlight"))
    add(EpubSelectionAction("Highlight + Note", EpubSelectionActionPresentation.OVERFLOW))
}

internal suspend fun <T> captureSelectionBeforeAction(
    capture: suspend () -> T?,
    action: (T) -> Unit
): Boolean {
    val selection = capture() ?: return false
    action(selection)
    return true
}

internal fun decodeJavascriptString(value: String?): String? {
    val encoded = value?.takeIf { it != "null" } ?: return null
    return runCatching { org.json.JSONArray("[$encoded]").getString(0) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

internal fun resolveEpubImageHref(
    currentLocatorHref: String,
    imageHrefs: List<String>
): String? {
    fun publicationPath(value: String): String? {
        val uri = runCatching { java.net.URI(value).normalize() }.getOrNull() ?: return null
        val path = when {
            uri.isAbsolute -> {
                if (
                    !uri.scheme.equals("https", ignoreCase = true) ||
                    !uri.host.equals("readium", ignoreCase = true) ||
                    uri.port != -1 ||
                    uri.userInfo != null
                ) {
                    return null
                }
                uri.rawPath?.removePrefix("/publication/")
                    ?.takeIf { it != uri.rawPath }
            }
            uri.rawAuthority == null -> uri.rawPath
            else -> null
        } ?: return null
        return path.takeIf {
            it.isNotBlank() &&
                !it.startsWith('/') &&
                it != ".." &&
                !it.startsWith("../")
        }
    }

    val currentPath = publicationPath(currentLocatorHref) ?: return null
    val baseUri = runCatching { java.net.URI(currentPath) }.getOrNull() ?: return null
    return imageHrefs.firstNotNullOfOrNull { imageHref ->
        val candidate = imageHref.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
        val candidateUri = runCatching { java.net.URI(candidate) }.getOrNull()
            ?: return@firstNotNullOfOrNull null
        if (candidateUri.isAbsolute || candidateUri.rawAuthority != null) {
            publicationPath(candidate)
        } else {
            val resolved = runCatching { baseUri.resolve(candidateUri).normalize() }.getOrNull()
                ?: return@firstNotNullOfOrNull null
            publicationPath(resolved.rawPath.orEmpty())
        }
    }
}

internal fun selectedSpineIndex(
    selectedHref: String,
    readingOrderHrefs: List<String>
): Int? {
    fun normalized(value: String): String = value.substringBefore('#').substringBefore('?')
    val selected = normalized(selectedHref)
    return readingOrderHrefs.indexOfFirst { normalized(it) == selected }
        .takeIf { it >= 0 }
}

internal fun combineEpubCfi(spineIndex: Int, innerRangeCfi: String?): String? {
    if (spineIndex < 0) return null
    val inner = innerRangeCfi
        ?.trim()
        ?.takeIf { it.startsWith("epubcfi(") && it.endsWith(")") }
        ?.removePrefix("epubcfi(")
        ?.removeSuffix(")")
        ?: return null
    if (inner.count { it == ',' } != 2 || !inner.startsWith('/')) return null
    return "epubcfi(/6/${(spineIndex + 1) * 2}!$inner)"
}

internal fun epubCfiSpineIndex(cfi: String?): Int? {
    val spineStep = Regex("^epubcfi\\(/6/(\\d+)!")
        .find(cfi.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: return null
    if (spineStep < 2 || spineStep % 2 != 0) return null
    return spineStep / 2 - 1
}

private val NAMED_HIGHLIGHT_COLORS = mapOf(
    "yellow" to 0x33FFEB3B,
    "green" to 0x334CAF50,
    "blue" to 0x332196F3,
    "pink" to 0x33E91E63,
    "orange" to 0x33FF9800,
    "purple" to 0x339C27B0
)

internal fun highlightTintForColor(color: String?): Int {
    val value = color?.trim()?.lowercase().orEmpty()
    NAMED_HIGHLIGHT_COLORS[value]?.let { return it }
    if (value.startsWith("#")) {
        runCatching { Color.parseColor(value) }.getOrNull()?.let { return it }
    }
    return NAMED_HIGHLIGHT_COLORS.getValue(DEFAULT_HIGHLIGHT_COLOR)
}

internal fun annotationLocatorJson(
    cfi: String,
    chapterHref: String,
    text: String? = null
): JSONObject = JSONObject().apply {
    put("href", chapterHref)
    put("type", MediaType.XHTML.toString())
    put("locations", JSONObject().put("fragments", org.json.JSONArray().put(cfi)))
    text?.takeIf { it.isNotBlank() }?.let { selectedText ->
        put("text", JSONObject().put("highlight", selectedText))
    }
}

internal fun resolveAnnotationChapterHref(
    cfi: String,
    explicitChapterIndex: Int?,
    readingOrderHrefs: List<String>
): String? {
    val index = explicitChapterIndex?.takeIf { it in readingOrderHrefs.indices }
        ?: epubCfiSpineIndex(cfi)?.takeIf { it in readingOrderHrefs.indices }
        ?: return null
    return readingOrderHrefs.getOrNull(index)
}

internal fun resolveInitialAnnotationLocatorJson(
    cfi: String,
    explicitChapterIndex: Int?,
    text: String?,
    readingOrderHrefs: List<String>
): JSONObject? {
    val href = resolveAnnotationChapterHref(cfi, explicitChapterIndex, readingOrderHrefs) ?: return null
    return annotationLocatorJson(cfi, href, text)
}

internal fun previewAnnotationTarget(
    annotationId: String?,
    bookId: String?,
    cfi: String?,
    text: String?,
    chapterIndex: Int?,
    color: String?,
    style: String?
): BookAnnotation? {
    val id = annotationId?.takeIf { it.isNotBlank() } ?: return null
    val cfiValue = cfi?.takeIf { it.isNotBlank() } ?: return null
    return BookAnnotation(
        id = id,
        bookId = bookId.orEmpty(),
        cfi = cfiValue,
        text = text,
        chapterIndex = chapterIndex,
        color = color,
        style = style
    )
}

internal fun epubAnnotationBookId(intent: Intent): String? =
    intent.getStringExtra(ReadiumEpubReaderActivity.EXTRA_BOOK_ID)?.takeIf { it.isNotBlank() }

internal fun epubAnnotationFeaturesEnabled(intent: Intent): Boolean =
    !intent.getBooleanExtra(ReadiumEpubReaderActivity.EXTRA_IS_PREVIEW, false) &&
        epubAnnotationBookId(intent) != null

internal fun shouldLoadNextAnnotationPage(
    loadedCount: Int,
    total: Int?,
    receivedCount: Int,
    pageSize: Int
): Boolean = when {
    receivedCount == 0 -> false
    total != null -> loadedCount < total
    else -> receivedCount >= pageSize
}

@OptIn(ExperimentalReadiumApi::class)
open class ReadiumEpubReaderActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL && pageSwipeActive) {
            pageSwipeActive = false
            cancelExpectedReaderMovement()
        }
        return super.dispatchTouchEvent(event)
    }

    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private var rendererHealthJob: Job? = null
    private var rendererHealthConfirmed = false
    private var rendererProbeAttemptCount = 0
    private var rendererProbeMissingWebViewCount = 0
    private var rendererProbeActiveMillis = 0L
    private var confirmedProgressDisplay by mutableStateOf(false)
    private var restoredDisplayProgress by mutableStateOf<EpubReaderProgressStatus?>(null)
    private var restoredDisplayPosition: EpubReaderPosition? = null
    private var pendingRestoredDisplayPosition: EpubReaderPosition? = null
    private var pendingRendererPagination: PendingEpubPagination? = null
    private var pendingRendererLocator: Locator? = null
    private var lastRecordedVisualLocator: Locator? = null
    private var visualLocatorReportCount = 0
    private var duplicateVisualLocatorReportCount = 0
    // Newer position written while the reader was stopped (by background read-aloud), until the
    // visual navigator reaches it; with its deadline.
    private var foregroundSyncTarget: Locator? = null
    private var foregroundSyncDeadline = 0L
    private var stoppedSinceLastStart = false
    private var foregroundSyncTimeoutJob: Job? = null
    private var fallbackContentLanguage: String? = null
    private var publicationLanguages: List<String> = emptyList()
    private var bookLanguageOverride by mutableStateOf<String?>(null)
    private val bookLanguageStore by lazy { EpubBookLanguageStore(this) }
    private var progressView: ProgressBar? = null
    private val readerContainerId: Int = R.id.readium_reader_container
    private lateinit var rootView: FrameLayout
    private lateinit var readerViewport: FrameLayout
    private lateinit var readerContainer: FrameLayout
    private lateinit var viewOnlyBannerView: ComposeView
    private lateinit var chromeView: ComposeView
    private lateinit var optionsView: ComposeView
    private lateinit var footerView: ComposeView
    private lateinit var tapZoneTutorialView: ComposeView
    private lateinit var mediaOverlayView: ComposeView
    private lateinit var ttsView: ComposeView
    private var updateReaderViewportOverlaySpace: (() -> Unit)? = null

    private lateinit var readerKey: String
    private var publicationFingerprint: String? = null
    private lateinit var ttsOwnerToken: String
    private lateinit var libraryId: String
    private lateinit var displayTitle: String
    private lateinit var readingSessionReporter: ReadingSessionReporter
    private var isPreview: Boolean = false
    private var bookId: String? = null
    private val annotationRepository by lazy { resolveProviderAnnotationModule(
        resolveProviderRepository(
            applicationContext,
            ServerProfileStore(applicationContext).active()?.serverUrl.orEmpty()
        )
    ) }
    private val highlightAnnotations = mutableMapOf<String, BookAnnotation>()
    private val highlightLocators = mutableMapOf<String, Locator>()
    private var selectedTheme by mutableStateOf(EpubReaderTheme.Sepia)
    private var selectedBackgroundTheme by mutableStateOf(EpubReaderTheme.Sepia)
    private var selectedUseCustomBackground by mutableStateOf(false)
    private var selectedCustomBackgroundColor by mutableStateOf<Int?>(null)
    private var selectedFontFamily by mutableStateOf(EpubReaderFontFamily.PUBLISHER_DEFAULT)
    private var padding by mutableStateOf(EpubPaddingPercentages())
    private var fontScale by mutableStateOf(1f)
    private var readingDirection by mutableStateOf(LibraryReadingDirection.LEFT_TO_RIGHT)
    private var epubLayoutMode by mutableStateOf(ReaderLayoutMode.PAGINATED)
    private var readerPreferences by mutableStateOf(LibraryReaderPreferences())
    private val windowTracker by lazy { ReaderWindowTracker(this) }
    // Fixed for the navigator's lifetime: Readium CSS properties are set at fragment creation.
    private var twoPageSpreadActive = false
    private var fixedLayoutPublication = false
    private var showTapZoneTutorialAfterChanges by mutableStateOf(true)
    private var highContrastTapZoneTutorial = false
    private var chapterTitles by mutableStateOf(emptyList<String>())
    private var currentChapter by mutableStateOf(0)
    private var currentPage by mutableStateOf(0)
    private var currentPageCount by mutableStateOf(1)
    private var currentResourceProgression by mutableStateOf(0f)
    private var currentPercent by mutableStateOf(0f)
    private var currentDisplayPercent by mutableStateOf(0f)
    private var bookPositionIndex: EpubContentPositionIndex? = null
    private var currentContentPosition: EpubOverallPosition? = null
    private var renderedPageLedger: EpubRenderedPageLedger? = null
    private var renderedPageChapterIndex: Int? = null
    private var renderedPageLocator: EpubCanonicalPosition? = null
    private var currentRenderedOverallPosition by mutableStateOf<EpubOverallPosition?>(null)
    private var latestReaderLocator: Locator? = null
    private var renderedEndPosition: EpubCanonicalPosition? = null
    private var persistedPositionIdentity: EpubReaderPosition? = null
    private var lastCanonicalPosition: EpubReaderPosition? = null
    private val passiveRecoveryGuard = EpubPassiveRecoveryGuard()
    private var pageSwipeActive = false
    private var lastPositionWriteMillis = 0L
    private val positionWriterSessionId = UUID.randomUUID().toString()
    private var positionWriterLease: EpubReaderWriterLease? = null
    private var positionWriterSequence = 0L
    private var latestPositionWriter: LatestEpubPositionWriter<EpubReaderPosition>? = null
    private var finishReaderJob: Job? = null
    private var previewAnnotationLocator: Locator? = null
    private var pendingAnnotationReanchor: Locator? = null
    private var tapZoneTutorialHasShown = false
    private var tapZoneTutorialHideJob: Job? = null
    private var restoredLocator: Locator? = null
    private var allowLegacyLocatorFallback = false
    private var readingSessionEnded = false
    private var readerExitPlaybackClosed = false
    private var epubImageGestureJob: Job? = null
    private var epubImageViewer by mutableStateOf<Pair<Int, Bitmap>?>(null)
    private lateinit var mediaOverlayPlayback: EpubMediaOverlayPlaybackViewModel
    private var mediaOverlayPlaylist by mutableStateOf(emptyList<EpubMediaOverlayClip>())
    private var mediaOverlayPlayerListener: Player.Listener? = null
    private var mediaOverlayStartupNavigationPending = false
    private var ttsServiceBinder: EpubTtsPlaybackService.PlaybackBinder? = null
    private var ttsServiceConnection: EpubTtsServiceConnection? = null
    private var ttsServiceStateJob: Job? = null
    private var ttsStartJob: Job? = null
    private var ttsStartRequestId: Long? = null
    private var pendingTtsSpec: EpubTtsSessionSpec? = null
    private var lastHandledTtsLocator: Locator? = null
    private var lastTtsFailureSerial = 0L
    private var lastTtsCompletionSerial = 0L
    private var ttsAvailable by mutableStateOf(false)
    private var ttsIsPlaying by mutableStateOf(false)
    private var ttsCanGoPrevious by mutableStateOf(false)
    private var ttsCanGoNext by mutableStateOf(false)
    private var ttsSettings by mutableStateOf(EpubTtsSettings())
    private var ttsVoiceLanguageTag by mutableStateOf<String?>(null)
    private var ttsVoices by mutableStateOf<List<EpubTtsVoice>>(emptyList())
    private var lastTtsLocator: Locator? = null
    private val staleNarrationPositionCleanup = StaleNarrationPositionCleanup()
    private var restoredTtsLocator: Locator? = null

    private val themeStore by lazy { EpubReaderThemeStore(this) }
    private val paddingStore by lazy { EpubReaderPaddingStore(this) }
    private val appPreferencesStore by lazy { AppPreferencesStore(this) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val legacyLocatorStore by lazy { ReadiumEpubLocatorStore(this) }
    private val epubPositionStore by lazy { EpubReaderPositionStore(this) }
    private val ttsPositionStore by lazy { ReadiumEpubTtsPositionStore(this) }
    private val customFontStore by lazy { CustomFontStore(this) }
    private val customFontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            when (val result = customFontStore.import(uri, displayNameForUri(uri))) {
                is CustomFontImportResult.Imported -> {
                    customFontStore.remove(readerPreferences.customFont)
                    val next = readerPreferences.copy(
                        customFont = result.record,
                        fontFamily = EpubReaderFontFamily.CUSTOM
                    )
                    appPreferencesStore.save(appPreferencesStore.read().withReaderPreferences(libraryId, next))
                    recreate()
                }
                is CustomFontImportResult.Rejected -> showError(getString(result.reasonRes))
            }
        }
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = volumeButtonNavigationAction(
            event.keyCode,
            reverse = readerPreferences.reverseVolumeButtonNavigation
        )
        val audioPlaybackController =
            (application as BookOrbitApplication).audioPlaybackController
        val readAlongSessionActive = mediaOverlayPlayback.player != null
        if (hasActiveTtsSession() || action == null || !volumeButtonNavigationEnabled(
                readerEnabled = readerPreferences.volumeButtonPageNavigation,
                audiobookSessionActive = audioPlaybackController.hasActiveAudiobookSession(),
                readAlongSessionActive = readAlongSessionActive
            )
        ) {
            return super.dispatchKeyEvent(event)
        }
        if (!canNavigateWithVolumeButtons()) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) return true
        expectPageOrChapterReaderMovement()
        endForegroundSync()
        val moved = when (action) {
            VolumeButtonNavigationAction.PREVIOUS -> navigator?.goBackward(true) == true
            VolumeButtonNavigationAction.NEXT -> navigator?.goForward(true) == true
        }
        if (!moved) cancelExpectedReaderMovement()
        return true
    }

    private fun canNavigateWithVolumeButtons(): Boolean =
        !isFinishing && !isDestroyed &&
            publication != null &&
            ::readerContainer.isInitialized &&
            readerContainer.isShown &&
            !areReaderControlsVisible() &&
            !isTapZoneTutorialVisible()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(epubReaderActivityThemeRes(initialReaderTheme()))
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        mediaOverlayPlayback = ViewModelProvider(this)[EpubMediaOverlayPlaybackViewModel::class.java]
        mediaOverlayPlaylist = mediaOverlayPlayback.playableClips.map { it.clip }
        restoredLocator = savedInstanceState?.readReaderLocator()
        lastPositionWriteMillis = savedInstanceState
            ?.getLong(STATE_READER_POSITION_WRITE_MILLIS, 0L)
            ?: 0L
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppPreferencesStore(this).read().let { preferences ->
            requestedOrientation = requestedOrientationForLock(
                enabled = preferences.lockOrientation,
                lockedOrientation = preferences.lockedOrientation
            )
        }

        displayTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        readerKey = intent.getStringExtra(EXTRA_READER_KEY).orEmpty()
        publicationFingerprint = intent.getStringExtra(EXTRA_PUBLICATION_FINGERPRINT)
            ?.takeIf(String::isNotBlank)
        ttsOwnerToken = intent.getStringExtra(EXTRA_TTS_OWNER_TOKEN)
            ?: UUID.randomUUID().toString().also {
                intent.putExtra(EXTRA_TTS_OWNER_TOKEN, it)
            }
        libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID).orEmpty()
        // The restored reader opens where the server progress put it; the narration position it
        // replaced must still be dropped once the reader is shown again and the service connects.
        if (savedInstanceState?.getBoolean(STATE_STALE_NARRATION_CLEANUP_PENDING) == true) {
            staleNarrationPositionCleanup.serverProgressWon()
        }
        isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
        restoredTtsLocator = epubRestoredLocator(
            savedState = savedInstanceState?.readEpubTtsLocator(),
            stored = ttsPositionStore.read(readerKey)
        )
        lastTtsLocator = restoredTtsLocator ?: ttsPositionStore.read(readerKey)
        bookLanguageOverride = bookLanguageStore.read(readerKey)
        bookId = epubAnnotationBookId(intent)
        readingSessionReporter = ViewModelProvider(this)[ReadingSessionReporterViewModel::class.java].reporter(
            context = this,
            fileId = intent.getStringExtra(EXTRA_FILE_ID),
            enabled = !isPreview
        )
        val appPreferences = appPreferencesStore.read()
        showTapZoneTutorialAfterChanges = appPreferences.showEpubTapZoneTutorialAfterChanges
        highContrastTapZoneTutorial = isColorEinkTheme(appPreferences.themeMode)
        readerPreferences = appPreferences.libraryReaderPreferences[libraryId]
            ?: LibraryReaderPreferences(
                theme = themeStore.read(),
                backgroundTheme = themeStore.read(),
                padding = paddingStore.read(readerKey)
            )
        selectedTheme = readerPreferences.theme
        selectedBackgroundTheme = readerPreferences.backgroundTheme
        selectedUseCustomBackground = readerPreferences.useCustomBackground
        selectedCustomBackgroundColor = readerPreferences.customBackgroundColor
        selectedFontFamily = readerPreferences.fontFamily
        padding = readerPreferences.padding
        fontScale = readerPreferences.fontScale
        readingDirection = readerPreferences.readingDirection
        epubLayoutMode = readerPreferences.epubLayoutMode
        ttsSettings = appPreferencesStore.readEpubTtsSettings()
        appPreferencesStore.save(
            appPreferences.withReaderPreferences(libraryId, readerPreferences)
        )
        twoPageSpreadActive = computeTwoPageSpread()
        windowTracker.start()
        windowTracker.observeSpreadChanges({ computeTwoPageSpread() }) { recreateIfSpreadChanged() }
        configureSystemBars()
        createReaderViews()
        bindTtsService()
        restoreReaderUi(savedInstanceState)
        discardRestoredNavigatorIfNeeded(savedInstanceState)
        installBackHandler()

        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null) {
            showError(getString(R.string.epub_reader_file_unavailable))
            return
        }
        lifecycleScope.launch {
            val persistenceScope = intent.epubReaderPersistenceScope()
            val launchFingerprint = publicationFingerprint
            val verifiedFingerprint = withContext(Dispatchers.IO) {
                verifiedEpubPublicationFingerprint(file)
            }
            publicationFingerprint = verifiedFingerprint
            if (
                verifiedFingerprint == null ||
                (launchFingerprint != null && verifiedFingerprint != launchFingerprint)
            ) {
                val currentBookId = bookId
                if (verifiedFingerprint != null && persistenceScope != null && currentBookId != null) {
                    readerKey = epubReaderPersistenceKey(
                        profileId = persistenceScope.profileId,
                        providerId = persistenceScope.providerId,
                        accountScope = persistenceScope.accountScope,
                        serverUrl = persistenceScope.serverUrl,
                        bookId = currentBookId,
                        fileId = intent.getStringExtra(EXTRA_FILE_ID),
                        publicationFingerprint = verifiedFingerprint
                    )
                }
                // The file changed between launch preparation and Activity open. None of the
                // old revision's auxiliary or numeric resume state may be applied to new bytes.
                restoredLocator = null
                restoredTtsLocator = null
                lastTtsLocator = null
                intent.removeExtra(EXTRA_INITIAL_LOCATOR_JSON)
                intent.removeExtra(EXTRA_INITIAL_PERCENT)
                intent.putExtra(EXTRA_INITIAL_CHAPTER, 0)
                intent.putExtra(EXTRA_INITIAL_PAGE, 0)
                intent.putExtra(EXTRA_INITIAL_PAGE_COUNT, 1)
            }
            persistedPositionIdentity = withContext(Dispatchers.IO) {
                bookId?.let { currentBookId ->
                    persistenceScope?.let { scope ->
                        runCatching {
                            epubReaderPositionIdentity(
                                scope = scope,
                                bookId = currentBookId,
                                fileId = intent.getStringExtra(EXTRA_FILE_ID),
                                file = file,
                                publicationFingerprint = verifiedFingerprint ?: return@runCatching null
                            )
                        }.getOrNull()
                    }
                }
            }
            val handoffPosition = persistedPositionIdentity?.let { identity ->
                val locatorJson = restoredLocator?.toJSON()?.toString()?.let(::boundedReaderLocatorJson)
                if (savedInstanceState == null || locatorJson == null) null else identity.copy(
                    chapterIndex = savedInstanceState.getInt(STATE_READER_POSITION_CHAPTER, 0)
                        .coerceAtLeast(0),
                    pageIndex = savedInstanceState.getInt(STATE_READER_POSITION_PAGE, 0)
                        .coerceAtLeast(0),
                    pageCount = savedInstanceState.getInt(STATE_READER_POSITION_PAGE_COUNT, 1)
                        .coerceAtLeast(1),
                    updatedAtMillis = lastPositionWriteMillis.coerceAtLeast(1L),
                    locatorJson = locatorJson,
                    progressPercent = savedInstanceState
                        .getFloat(STATE_READER_POSITION_PERCENT, Float.NaN)
                        .takeUnless(Float::isNaN)
                        ?.takeIf(Float::isFinite)
                        ?.coerceIn(0f, 100f),
                    overallPositionNumber = savedInstanceState
                        .getInt(STATE_READER_POSITION_OVERALL_NUMBER, 0)
                        .takeIf { it > 0 },
                    overallPositionCount = savedInstanceState
                        .getInt(STATE_READER_POSITION_OVERALL_COUNT, 0)
                        .takeIf { it > 0 },
                    renderedPageLayoutKey = savedInstanceState
                        .getString(STATE_READER_RENDERED_PAGE_LAYOUT_KEY)
                        ?.takeIf { it.isNotBlank() && it.length <= 8 * 1024 },
                    renderedPageCounts = savedInstanceState
                        .getIntArray(STATE_READER_RENDERED_PAGE_COUNTS)
                        ?.takeIf { counts ->
                            counts.size <= MAX_EPUB_RENDERED_PAGE_RESOURCES &&
                                counts.all { it in 0..MAX_EPUB_RENDERED_PAGES_PER_RESOURCE }
                        }
                        ?.toList()
                        .orEmpty()
                )
            }
            var staleSavedStateRejected = false
            val claimedWriterLease = if (isPreview) null else withContext(Dispatchers.IO) {
                persistedPositionIdentity
                    ?.let {
                        try {
                            epubPositionStore.claimWriter(
                                identity = it,
                                sessionId = positionWriterSessionId,
                                handoff = handoffPosition,
                                expectedResetEpoch = savedInstanceState
                                    ?.takeIf { state ->
                                        state.containsKey(STATE_READER_POSITION_RESET_EPOCH)
                                    }
                                    ?.getLong(STATE_READER_POSITION_RESET_EPOCH)
                            )
                        } catch (_: StaleEpubReaderResetException) {
                            staleSavedStateRejected = true
                            epubPositionStore.claimWriter(
                                identity = it,
                                sessionId = positionWriterSessionId
                            )
                        }
                    }
            }
            positionWriterLease = claimedWriterLease
            positionWriterSequence = claimedWriterLease?.sequence ?: 0L
            latestPositionWriter = claimedWriterLease?.let { lease ->
                LatestEpubPositionWriter(epubPositionWriteScope) { sequence, snapshot ->
                    epubPositionStore.saveOwned(lease, sequence, snapshot).also { saved ->
                        if (saved) legacyLocatorStore.remove(readerKey)
                    }
                }
            }
            val persistedPosition = claimedWriterLease?.position
            lastCanonicalPosition = persistedPosition?.takeIf { it.updatedAtMillis > 0L }
            pendingRestoredDisplayPosition = persistedPosition
            renderedPageLedger = persistedPosition
                ?.renderedPageLayoutKey
                ?.takeIf(String::isNotBlank)
                ?.let { layoutKey ->
                    persistedPosition.renderedPageCounts
                        .takeIf { counts ->
                            counts.size <= MAX_EPUB_RENDERED_PAGE_RESOURCES &&
                                counts.all { it in 0..MAX_EPUB_RENDERED_PAGES_PER_RESOURCE }
                        }
                        ?.let { counts -> EpubRenderedPageLedger(layoutKey, counts) }
                }
            lastPositionWriteMillis = maxOf(
                lastPositionWriteMillis,
                persistedPosition?.updatedAtMillis ?: 0L
            )
            // Pre-v3 auxiliary locator keys had no account identity. Never adopt them into an
            // authenticated canonical scope. They remain quarantined for unscoped legacy opens.
            allowLegacyLocatorFallback = shouldRestoreLegacyEpubLocator(
                hasCanonicalIdentity = persistedPositionIdentity != null,
                staleSavedStateRejected = staleSavedStateRejected
            ) && verifiedFingerprint != null
            val resetWithoutNewProgress = claimedWriterLease?.let { lease ->
                lease.resetEpoch > 0L && lease.position.updatedAtMillis <= 0L
            } == true
            if (staleSavedStateRejected || resetWithoutNewProgress) {
                restoredLocator = null
                restoredTtsLocator = null
                lastTtsLocator = null
                legacyLocatorStore.remove(readerKey)
                ttsPositionStore.remove(readerKey)
            }
            val persistedLocator = if (isPreview) null else {
                persistedPosition
                    ?.locatorJson
                    ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }
                    ?: if (allowLegacyLocatorFallback) legacyLocatorStore.read(readerKey) else null
            }
            val preparation = withContext(Dispatchers.IO) {
                val custom = readerPreferences.customFont
                if (readerPreferences.fontFamily == EpubReaderFontFamily.CUSTOM && custom != null) {
                    val fontFile = customFontStore.fontFile(custom)
                    if (fontFile.isFile) {
                        prepareEpubWithCustomFontSafely(this@ReadiumEpubReaderActivity, file, custom, fontFile)
                    } else {
                        CustomFontEpubPreparationResult.Prepared(file)
                    }
                } else CustomFontEpubPreparationResult.Prepared(file)
            }
            val readerFile = when (preparation) {
                is CustomFontEpubPreparationResult.Prepared -> preparation.file
                is CustomFontEpubPreparationResult.Rejected -> {
                    showError(getString(preparation.messageRes))
                    return@launch
                }
            }
            when (val result = openReadiumEpub(this@ReadiumEpubReaderActivity, readerFile)) {
                is ReadiumEpubOpenResult.Error -> showError(result.message)
                is ReadiumEpubOpenResult.Opened -> {
                    val fingerprintAfterOpen = withContext(Dispatchers.IO) {
                        verifiedEpubPublicationFingerprint(file)
                    }
                    if (fingerprintAfterOpen == null || fingerprintAfterOpen != verifiedFingerprint) {
                        result.publication.close()
                        showError(getString(R.string.epub_open_error_file_changed))
                        return@launch
                    }
                    prepareAndAttachPublicationWhenResumed(
                        publication = result.publication,
                        prepare = {
                            val playlist = withContext(Dispatchers.IO) {
                                EpubMediaOverlayParser.parse(readerFile).items
                            }
                            val positions = loadBoundedEpubContentPositionIndex(
                                publication = result.publication,
                                sourceFile = readerFile
                            )
                            playlist to positions
                        }
                    ) { (playlist, positions) ->
                        mediaOverlayPlaylist = playlist
                        bookPositionIndex = positions
                        val explicitAnnotation = intent.getStringExtra(EXTRA_INITIAL_CFI)
                            ?.isNotBlank() == true
                        showPublication(
                            result.publication,
                            if (explicitAnnotation) null else persistedLocator ?: restoredLocator
                        )
                    }
                }
            }
        }
    }

    private fun createReaderViews() {
        rootView = FrameLayout(this).apply {
            setBackgroundColor(readerContentBackgroundColor(readerPreferences))
        }
        readerViewport = FrameLayout(this).apply {
            setBackgroundColor(readerContentBackgroundColor(readerPreferences))
        }
        ViewCompat.setOnApplyWindowInsetsListener(readerViewport) { view, insets ->
            val navigationBarBottom = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
            val bottomInset = readerViewportBottomInset(
                navigationBarBottom,
                hideNavigationBar = appPreferencesStore.read().hideNavigationBarWhileReading
            )
            if (view.paddingBottom != bottomInset) {
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
                updateReaderViewportOverlaySpace?.invoke()
            }
            insets
        }

        rootView.addView(
            readerViewport,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        readerContainer = FrameLayout(this).apply {
            id = readerContainerId
            setBackgroundColor(readerContentBackgroundColor(readerPreferences))
        }
        readerViewport.addView(
            readerContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        viewOnlyBannerView = ComposeView(this).apply {
            visibility = if (isPreview) View.VISIBLE else View.GONE
            setContent { EpubReaderMaterialTheme(selectedTheme) { ViewOnlyModeBanner(onConfirmReadMode = ::enableReadMode) } }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyReaderPadding() }
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                    if (params.topMargin != statusBarTop) {
                        params.topMargin = statusBarTop
                        view.layoutParams = params
                    }
                }
                applyReaderPadding()
                insets
            }
        }
        readerViewport.addView(viewOnlyBannerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
        progressView = ProgressBar(this).also { progress ->
            readerViewport.addView(
                progress,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
        footerView = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    if (confirmedProgressDisplay || restoredDisplayProgress != null) {
                        EpubReaderProgressFooter(
                            status = currentProgressStatus(),
                            theme = selectedBackgroundTheme,
                            modifier = Modifier.fillMaxSize(),
                            customBackgroundColor = if (selectedUseCustomBackground) {
                                selectedCustomBackgroundColor
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
        readerViewport.addView(
            footerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(EPUB_READER_PROGRESS_FOOTER_HEIGHT_DP),
                Gravity.BOTTOM
            )
        )
        chromeView = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    val context = LocalContext.current
                    val pagePositionCount = currentPageCount.coerceAtLeast(1)
                    ReaderLightweightChrome(
                        title = if (isPreview) {
                            stringResource(R.string.epub_preview_title, displayTitle)
                        } else {
                            displayTitle
                        },
                        theme = selectedTheme,
                        positionKind = stringResource(R.string.epub_page_kind),
                        positionTitles = List(pagePositionCount) { index ->
                            context.getString(R.string.epub_page_position, index + 1)
                        },
                        currentPosition = currentPage,
                        currentProgression = currentResourceProgression.takeIf {
                            epubLayoutMode == ReaderLayoutMode.CONTINUOUS
                        },
                        onBackToReading = ::hideChrome,
                        onCloseBook = ::finishReader,
                        onOpenSettings = ::showOptions,
                        onPositionSelected = ::goToPage,
                        onProgressionSelected = ::goToProgression,
                        listPositionKind = stringResource(R.string.epub_chapter_kind),
                        listPositionTitles = chapterTitles,
                        currentListPosition = currentChapter,
                        onListPositionSelected = ::goToChapter,
                        secondaryPositionKind = stringResource(R.string.epub_chapter_kind),
                        secondaryCurrentPosition = currentChapter,
                        secondaryPositionCount = chapterTitles.size,
                        onSecondaryPositionSelected = ::goToChapter,
                        onListenToNarration = if (
                            !isPreview && (mediaOverlayPlaylist.isNotEmpty() || ttsAvailable)
                        ) {
                            {
                                hideChrome()
                                showListenChoices()
                            }
                        } else null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        readerViewport.addView(
            chromeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        optionsView = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EpubReaderDismissScrim(
                            contentDescription = stringResource(
                                R.string.epub_dismiss_reader_options_cd
                            ),
                            onDismiss = ::hideOptions
                        )
                        EpubReaderOptionsBottomSheet(
                            title = if (isPreview) {
                                stringResource(R.string.epub_preview_title, displayTitle)
                            } else {
                                displayTitle
                            },
                            status = stringResource(
                                R.string.epub_options_status,
                                currentChapter + 1,
                                chapterTitles.size.coerceAtLeast(1),
                                currentPage + 1,
                                currentPageCount.coerceAtLeast(1)
                            ),
                            preferences = readerPreferences,
                            onContinueReading = ::continueReading,
                            onCloseBook = ::finishReader,
                            onPreferencesChange = ::applyReaderPreferences,
                            showTapZoneTutorialAfterChanges = showTapZoneTutorialAfterChanges,
                            onShowTapZoneTutorialAfterChangesChange = ::updateShowTapZoneTutorialAfterChanges,
                            onCustomFontRequest = ::chooseCustomFont,
                            onCustomFontRemove = ::removeCustomFont,
                            bookLanguage = remember(bookLanguageOverride, publication) { bookLanguageChoice() },
                            onBookLanguageChange = ::setBookLanguage,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )
                    }
                }
            }
        }
        readerViewport.addView(
            optionsView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        tapZoneTutorialView = ComposeView(this).apply {
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    ReaderTapZoneTutorial(
                        onDismiss = ::hideTapZoneTutorial,
                        readingDirection = readingDirection,
                        tapZoneLayout = readerPreferences.tapZoneLayout,
                        tapZoneInvertMode = readerPreferences.tapZoneInvertMode,
                        highContrast = highContrastTapZoneTutorial,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        readerViewport.addView(
            tapZoneTutorialView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val epubImageViewerView = ComposeView(this).apply {
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    epubImageViewer?.let { (_, bitmap) ->
                        ComicPageImageViewer(
                            title = displayTitle,
                            pageIndex = currentPage,
                            bitmap = bitmap,
                            onDismiss = { epubImageViewer = null }
                        )
                    }
                }
            }
        }
        readerViewport.addView(
            epubImageViewerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        optionsView.bringToFront()
        val audioPlayerOverlay = addReadiumAudioPlayerOverlay(
            rootView,
            readerViewport,
            bindViewportSpace = false
        )
        mediaOverlayView = ComposeView(this).apply {
            visibility = if (mediaOverlayPlayback.player == null) View.GONE else View.VISIBLE
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    val clip = mediaOverlayPlayback.activeClip
                    if (clip != null) {
                        EpubMediaOverlayControls(
                            speed = mediaOverlayPlayback.speed,
                            isPlaying = mediaOverlayPlayback.isPlaying,
                            canGoPrevious = (mediaOverlayPlayback.player?.currentMediaItemIndex ?: 0) > 0,
                            canGoNext = mediaOverlayPlayback.player?.let {
                                it.currentMediaItemIndex < it.mediaItemCount - 1
                            } == true,
                            onPlayPause = ::toggleMediaOverlayPlayback,
                            onPrevious = { seekMediaOverlayClip(-1) },
                            onNext = { seekMediaOverlayClip(1) },
                            onSpeedChange = ::setMediaOverlaySpeed,
                            onClose = ::closeMediaOverlayPlayback
                        )
                    }
                }
            }
        }
        rootView.addView(
            mediaOverlayView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        ttsView = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                EpubReaderMaterialTheme(selectedTheme) {
                    EpubTtsControls(
                        settings = ttsSettings,
                        isPlaying = ttsIsPlaying,
                        canGoPrevious = ttsCanGoPrevious,
                        canGoNext = ttsCanGoNext,
                        voiceLanguageTag = ttsVoiceLanguageTag,
                        voices = ttsVoices,
                        onPlayPause = ::toggleTtsPlayback,
                        onPrevious = ::previousTtsUtterance,
                        onNext = ::nextTtsUtterance,
                        onSettingsChange = ::applyTtsSettings,
                        onClose = ::closeTtsPlayback
                    )
                }
            }
        }
        rootView.addView(
            ttsView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        updateReaderViewportOverlaySpace = bindReaderViewportAboveOverlays(
            readerViewport,
            listOf(audioPlayerOverlay, mediaOverlayView, ttsView)
        )
        setContentView(rootView)
        syncProgressFooter()
        readerViewport.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyReaderPadding() }
        ViewCompat.requestApplyInsets(readerViewport)
    }

    private fun restoreReaderUi(savedInstanceState: Bundle?) {
        savedInstanceState ?: return
        tapZoneTutorialHasShown = savedInstanceState.getBoolean(STATE_READER_TUTORIAL_SHOWN)
        chromeView.visibility = if (
            savedInstanceState.getBoolean(STATE_READER_CHROME_VISIBLE)
        ) View.VISIBLE else View.GONE
        optionsView.visibility = if (
            savedInstanceState.getBoolean(STATE_READER_OPTIONS_VISIBLE)
        ) View.VISIBLE else View.GONE
        syncProgressFooter()
    }

    private fun discardRestoredNavigatorIfNeeded(savedInstanceState: Bundle?) {
        if (readerRestoreAction(savedInstanceState != null) != ReaderRestoreAction.REOPEN) return
        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { restored ->
            supportFragmentManager.beginTransaction().remove(restored).commitNow()
        }
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        optionsView.visibility == View.VISIBLE -> hideOptions()
                        chromeView.visibility == View.VISIBLE -> hideChrome()
                        else -> finishReader()
                    }
                }
            }
        )
    }

    private fun renderedPageLayoutKey(openedPublication: Publication): String? {
        if (!::readerContainer.isInitialized) return null
        return epubRenderedPageLayoutKey(
            readingOrderHrefs = openedPublication.readingOrder.map { it.url().toString() },
            viewportWidthPx = readerContainer.width,
            viewportHeightPx = readerContainer.height,
            contentPaddingLeftPx = readerContainer.paddingLeft,
            contentPaddingTopPx = readerContainer.paddingTop,
            contentPaddingRightPx = readerContainer.paddingRight,
            contentPaddingBottomPx = readerContainer.paddingBottom,
            densityDpi = resources.displayMetrics.densityDpi,
            systemFontScale = resources.configuration.fontScale,
            webViewVersion = WebView.getCurrentWebViewPackage()?.versionName.orEmpty(),
            twoPageSpreadActive = twoPageSpreadActive,
            spreadPageMarginCssPx = spreadPageMarginCssPx(),
            contentLanguage = currentContentLanguage(),
            preferences = readerPreferences
        )
    }

    private fun showPublication(openedPublication: Publication, requestedLocator: Locator? = null) {
        if (isFinishing || isDestroyed) {
            openedPublication.close()
            return
        }
        if (openedPublication.readingOrder.isEmpty()) {
            openedPublication.close()
            showError(getString(R.string.epub_no_readable_content))
            return
        }
        publication = openedPublication
        rendererHealthJob?.cancel()
        rendererHealthJob = null
        rendererHealthConfirmed = false
        rendererProbeAttemptCount = 0
        rendererProbeMissingWebViewCount = 0
        rendererProbeActiveMillis = 0L
        pendingRendererPagination = null
        pendingRendererLocator = null
        publicationLanguages = openedPublication.metadata.languages
        fallbackContentLanguage = epubNavigatorLanguage(currentContentLanguage())
        ttsAvailable = !isPreview && AndroidTtsNavigatorFactory(application, openedPublication) != null
        chapterTitles = openedPublication.readingOrder.mapIndexed { index, link ->
            chapterTitle(openedPublication.tableOfContents, link) ?: link.title
                ?: getString(R.string.epub_chapter_fallback, index + 1)
        }
        val validRequestedLocator = requestedLocator
            ?.takeIf { saved ->
                openedPublication.readingOrder.any { link ->
                    link.url().isEquivalent(saved.href.removeFragment())
                }
            }
        val serverPercent = intent.getFloatExtra(EXTRA_SERVER_PROGRESS_PERCENT, Float.NaN)
            .takeUnless(Float::isNaN)
        // The server value describes the moment the book was opened; later activity recreation
        // must not compare it with positions the user has since moved to.
        intent.removeExtra(EXTRA_SERVER_PROGRESS_PERCENT)
        val serverProgressWins = validRequestedLocator != null && shouldPreferServerProgress(
            serverPercent = serverPercent,
            lastReportedPercent = intent.getFloatExtra(EXTRA_LAST_REPORTED_PERCENT, Float.NaN)
                .takeUnless(Float::isNaN),
            locatorPercent = locatorOverallPercent(openedPublication, validRequestedLocator),
            preferServerProgress = readerPreferences.preferServerProgressOnReopen
        )
        val initialLocator = if (serverProgressWins) {
            staleNarrationPositionCleanup.serverProgressWon()
            locatorFromPercent(
                openedPublication,
                initialPercent = serverPercent,
                requestedChapter = 0,
                initialPage = 0,
                initialPageCount = 1
            )
        } else {
            validRequestedLocator ?: initialLocator(openedPublication)
        }
        val displayPosition = pendingRestoredDisplayPosition
        passiveRecoveryGuard.begin(
            displayPosition?.takeIf {
                epubLayoutMode == ReaderLayoutMode.PAGINATED &&
                    !serverProgressWins &&
                    epubPositionMatchesOpeningLocator(it, initialLocator)
            }
        )
        restoredDisplayProgress = restoredEpubDisplayProgress(
            position = displayPosition,
            openingLocator = initialLocator,
            openingChapterIndex = openedPublication.readingOrder.indexOfFirst { link ->
                link.url().isEquivalent(initialLocator.href.removeFragment())
            },
            chapterCount = openedPublication.readingOrder.size,
            layoutMode = epubLayoutMode,
            renderedLayoutKey = renderedPageLayoutKey(openedPublication)
        )
        restoredDisplayPosition = displayPosition?.takeIf { restoredDisplayProgress != null }
        pendingRestoredDisplayPosition = null
        syncProgressFooter()
        // Start the session where the reader actually opens, so reaching it is not counted as reading.
        readingSessionReporter.start(locatorOverallPercent(openedPublication, initialLocator))
        val paginationListener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                val pagination = PendingEpubPagination(pageIndex, totalPages, locator)
                if (!rendererHealthConfirmed) {
                    pendingRendererPagination = pagination
                    return
                }
                applyEpubPagination(openedPublication, pagination)
            }
        }
        val navigatorListener = object : EpubNavigatorFragment.Listener {
            override fun onJumpToLocator(locator: Locator) {
                if (!passiveRecoveryGuard.isMovementExpected(SystemClock.elapsedRealtime())) {
                    expectSemanticReaderMovement(locator)
                }
            }

            override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit
        }
        val navigatorFactory = EpubNavigatorFactory(openedPublication)
        // Readium draws fixed-layout spreads itself, so those keep the container margins.
        fixedLayoutPublication =
            navigatorFactory.createPreferencesEditor(EpubPreferences()).layout == EpubLayout.FIXED
        applyReaderPadding()
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = readiumPreferences(
                selectedTheme,
                fontScale,
                readerPreferences.lineSpacing,
                readingDirection,
                epubLayoutMode,
                selectedFontFamily,
                readerPreferences.wordSpacing,
                twoPageSpreadActive,
                spreadPageMarginCssPx(),
                readerPreferences.textAlignment,
                readerPreferences.hyphenation,
                fallbackContentLanguage,
                readerPreferences.customBackgroundColor,
                readerPreferences.customTextColor,
                readerPreferences.backgroundTheme,
                readerPreferences.useCustomBackground,
                readerPreferences.useCustomTextColor,
                systemDarkMode(),
                readerPreferences.paragraphSpacing
            ),
            listener = navigatorListener,
            paginationListener = paginationListener,
            configuration = EpubNavigatorFragment.Configuration(
                readiumCssRsProperties = epubSpreadRsProperties(twoPageSpreadActive),
                shouldApplyInsetsPadding = false,
                selectionActionModeCallback = if (
                    epubAnnotationFeaturesEnabled(intent) ||
                    (!isPreview && (mediaOverlayPlaylist.isNotEmpty() || ttsAvailable))
                ) {
                    highlightActionModeCallback()
                } else null
            )
        )
        supportFragmentManager.fragmentFactory = fragmentFactory
        val fragment = fragmentFactory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name
        ) as EpubNavigatorFragment
        supportFragmentManager.beginTransaction()
            .replace(readerContainerId, fragment, NAVIGATOR_TAG)
            .commitNow()
        fragment.addInputListener(
            LibraryDirectionalNavigationAdapter(
                navigator = fragment,
                readingDirection = { readingDirection },
                tapZoneLayout = { readerPreferences.tapZoneLayout },
                tapZoneInvertMode = { readerPreferences.tapZoneInvertMode },
                onMenu = ::toggleChrome,
                onPageTurn = {
                    expectPageOrChapterReaderMovement()
                    endForegroundSync()
                },
                onPageTurnCancelled = ::cancelExpectedReaderMovement,
                onSwipeStarted = { pageSwipeActive = true },
                onSwipeFinished = { horizontalOffset ->
                    pageSwipeActive = false
                    finishPageOrChapterReaderSwipe(
                        horizontalOffset = horizontalOffset,
                        chapterCount = openedPublication.readingOrder.size
                    )
                }
            )
        )
        navigator = fragment
        restoreRetainedMediaOverlayPlayback()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fragment.currentLocator.collect { locator ->
                    if (rendererHealthConfirmed) {
                        recordVisualLocator(locator)
                        pendingAnnotationReanchor?.let { target ->
                            pendingAnnotationReanchor = null
                            if (locator != target) {
                                expectSemanticReaderMovement(target)
                                if (!fragment.go(target)) cancelExpectedReaderMovement()
                            }
                        }
                    } else {
                        pendingRendererLocator = locator
                    }
                    if (highlightAnnotations.isNotEmpty()) {
                        refreshHighlightDecorations()
                    }
                }
            }
        }
        applyReaderPadding()
        startRendererHealthCheck(fragment, openedPublication)
    }

    private fun applyEpubPagination(
        openedPublication: Publication,
        pagination: PendingEpubPagination
    ): Boolean {
        val (pageIndex, totalPages, locator) = pagination
        val pageChapter = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(locator.href.removeFragment())
        }
        val validPage = pageChapter >= 0 && totalPages > 0 && pageIndex in 0 until totalPages
        val pageMoved = pageChapter != currentChapter || pageIndex != currentPage
        val canonicalCandidate = locator.toEpubCanonicalPosition()
        val commitProgress = validPage && passiveRecoveryGuard.shouldCommitVisualUpdate(
            chapterIndex = pageChapter,
            pageIndex = pageIndex,
            candidate = canonicalCandidate,
            nowMillis = SystemClock.elapsedRealtime()
        )
        val visualLocatorAccepted = validPage && acceptVisualLocator(locator, pageMoved)
        if (!shouldApplyEpubPagination(validPage, visualLocatorAccepted)) return false

        currentPage = pageIndex
        currentPageCount = totalPages
        val layoutKey = renderedPageLayoutKey(openedPublication)
        if (layoutKey != null) {
            renderedPageLedger = updateEpubRenderedPageLedger(
                current = renderedPageLedger,
                layoutKey = layoutKey,
                readingOrderCount = openedPublication.readingOrder.size,
                chapterIndex = pageChapter,
                pageIndex = pageIndex,
                pageCount = totalPages
            )
            renderedPageChapterIndex = pageChapter
            renderedPageLocator = canonicalCandidate
        } else {
            renderedPageChapterIndex = null
            renderedPageLocator = null
            currentRenderedOverallPosition = null
        }
        val navigatorAtEnd = isRenderedEpubPublicationEnd(
            layoutMode = epubLayoutMode,
            pageIndex = pageIndex,
            pageCount = totalPages,
            chapterIndex = pageChapter,
            chapterCount = openedPublication.readingOrder.size
        )
        renderedEndPosition = canonicalCandidate.takeIf { navigatorAtEnd }
        val visualLocatorApplied = applyAcceptedVisualLocator(
            locator = locator,
            navigatorAtEnd = navigatorAtEnd,
            commitProgress = commitProgress
        )
        if (visualLocatorApplied) {
            if (commitProgress) {
                confirmProgressDisplay()
            } else {
                showPassiveRecoveryDisplay()
            }
        }
        return visualLocatorApplied
    }

    private fun startRendererHealthCheck(
        fragment: EpubNavigatorFragment,
        openedPublication: Publication
    ) {
        rendererHealthJob?.cancel()
        if (this !is SoftwareRenderedEpubReaderActivity) {
            rendererHealthJob = null
            rendererHealthConfirmed = true
            completeRendererStartup(fragment, openedPublication)
            return
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            rendererHealthJob = null
            return
        }
        rendererHealthJob = lifecycleScope.launch {
            var healthy = false
            while (
                !healthy &&
                navigator === fragment &&
                rendererProbeActiveMillis < EPUB_RENDERER_PROBE_TIMEOUT_MILLIS
            ) {
                if (
                    !shouldRunEpubRendererProbe(
                        lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        deviceInteractive = powerManager?.isInteractive == true
                    )
                ) {
                    delay(EPUB_RENDERER_PROBE_RETRY_MILLIS)
                    continue
                }
                val activeCycleStartedAt = SystemClock.elapsedRealtime()
                val webView = fragment.view?.largestVisibleWebView()
                rendererProbeAttemptCount += 1
                if (webView == null) rendererProbeMissingWebViewCount += 1
                try {
                    val result = webView?.let { candidate ->
                        withTimeoutOrNull(EPUB_RENDERER_PROBE_ATTEMPT_TIMEOUT_MILLIS) {
                            val response = CompletableDeferred<String?>()
                            candidate.evaluateJavascript(EPUB_RENDERER_PROBE_SCRIPT) { value ->
                                response.complete(value)
                            }
                            response.await()
                        }
                    }
                    healthy = isHealthyEpubRendererProbeResult(result)
                    if (!healthy) delay(EPUB_RENDERER_PROBE_RETRY_MILLIS)
                } finally {
                    rendererProbeActiveMillis += (
                        SystemClock.elapsedRealtime() - activeCycleStartedAt
                        ).coerceAtLeast(0L)
                }
            }
            if (navigator !== fragment || isFinishing || isDestroyed) return@launch
            if (!healthy) {
                if (
                    !shouldRunEpubRendererProbe(
                        lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        deviceInteractive = powerManager?.isInteractive == true
                    )
                ) return@launch
                pendingRendererPagination = null
                pendingRendererLocator = null
                showError(getString(R.string.epub_renderer_start_failed))
                return@launch
            }

            rendererHealthConfirmed = true
            completeRendererStartup(fragment, openedPublication)
        }
    }

    private fun completeRendererStartup(
        fragment: EpubNavigatorFragment,
        openedPublication: Publication
    ) {
        val pagination = pendingRendererPagination.also { pendingRendererPagination = null }
        val locator = pendingRendererLocator.also { pendingRendererLocator = null }
        val paginationApplied = pagination?.let { applyEpubPagination(openedPublication, it) } == true
        val locatorNeedsSeparateApply = shouldApplyEpubLocatorSeparately(
            layoutMode = epubLayoutMode,
            hasLocator = locator != null,
            paginationApplied = paginationApplied,
            paginationMatchesLocator = pagination?.locator == locator
        )
        if (locatorNeedsSeparateApply) {
            recordVisualLocator(requireNotNull(locator))
        }
        pendingAnnotationReanchor?.let { target ->
            pendingAnnotationReanchor = null
            if (fragment.currentLocator.value != target) {
                expectSemanticReaderMovement(target)
                if (!fragment.go(target)) cancelExpectedReaderMovement()
            }
        }

        if (epubAnnotationFeaturesEnabled(intent)) {
            fragment.addDecorationListener(HIGHLIGHT_DECORATION_GROUP, highlightDecorationListener)
            loadHighlights(openedPublication)
        } else if (isPreview) {
            applyPreviewAnnotationDecoration(fragment, openedPublication)
        }
        startEpubImageGestureBridge(fragment)
        progressView?.visibility = View.GONE
        if (showTapZoneTutorialAfterChanges && !tapZoneTutorialHasShown) showTapZoneTutorial()
        restoreTtsPositionAfterRecreation(openedPublication)
    }

    private fun View.largestVisibleWebView(): WebView? {
        val candidates = mutableListOf<WebView>()
        fun collect(view: View) {
            if (view is WebView) candidates += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(this)
        return candidates
            .filter { it.isShown && it.width > 0 && it.height > 0 }
            .maxByOrNull { candidate ->
                val visible = Rect()
                if (candidate.getGlobalVisibleRect(visible)) visible.width() * visible.height() else 0
            }
    }

    private fun showListenChoices() {
        val hasPublisherNarration = mediaOverlayPlaylist.isNotEmpty()
        val hasTextToSpeech = ttsAvailable
        val canKeepListening = validTtsLocator(lastTtsLocator) != null
        val choices = epubListenChoices(
            hasPublisherNarration = hasPublisherNarration,
            hasTextToSpeech = hasTextToSpeech,
            canKeepListening = canKeepListening
        )
        if (choices.isEmpty()) {
            Toast.makeText(this, getString(R.string.epub_listen_nothing_available), Toast.LENGTH_LONG)
                .show()
            return
        }
        if (choices.size == 1) {
            performListenChoice(choices.single())
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.epub_listen_title))
            .setItems(choices.map(::listenChoiceLabel).toTypedArray()) { _, index ->
                performListenChoice(choices[index])
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun listenChoiceLabel(choice: EpubListenChoice): String = when (choice) {
        EpubListenChoice.PUBLISHER_NARRATION -> getString(R.string.epub_listen_publisher_narration)
        EpubListenChoice.TTS_FROM_HERE -> getString(R.string.epub_listen_from_here)
        EpubListenChoice.TTS_KEEP_LISTENING -> getString(R.string.epub_listen_keep_listening)
    }

    private fun performListenChoice(choice: EpubListenChoice) {
        when (choice) {
            EpubListenChoice.PUBLISHER_NARRATION -> startMediaOverlayPlayback()
            EpubListenChoice.TTS_FROM_HERE,
            EpubListenChoice.TTS_KEEP_LISTENING -> requestTtsPlayback(choice)
        }
    }

    private fun requestTtsPlayback(
        choice: EpubListenChoice,
        restoredLocator: Locator? = null,
        selectionText: String? = null,
        playWhenReady: Boolean = true
    ) {
        if (isPreview) return
        val preferences = getSharedPreferences(TTS_PRIVACY_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(TTS_PRIVACY_ACKNOWLEDGED, false)) {
            startTtsPlayback(
                choice,
                restoredLocator = restoredLocator,
                selectionText = selectionText,
                playWhenReady = playWhenReady
            )
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tts_privacy_title))
            .setMessage(getString(R.string.tts_privacy_message))
            .setPositiveButton(getString(R.string.common_continue)) { _, _ ->
                preferences.edit().putBoolean(TTS_PRIVACY_ACKNOWLEDGED, true).apply()
                startTtsPlayback(
                    choice,
                    restoredLocator = restoredLocator,
                    selectionText = selectionText,
                    playWhenReady = playWhenReady
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun startTtsPlayback(
        choice: EpubListenChoice,
        restoredLocator: Locator? = null,
        selectionText: String? = null,
        playWhenReady: Boolean = true
    ) {
        ttsServiceBinder?.state?.value?.takeIf {
            it.ownerToken == ttsOwnerToken && it.readerKey == readerKey && it.hasSession &&
                choice == EpubListenChoice.TTS_KEEP_LISTENING && restoredLocator == null &&
                ttsStartJob == null && pendingTtsSpec == null
        }?.let {
            closeMediaOverlayPlayback()
            ttsView.visibility = View.VISIBLE
            updateReaderViewportOverlaySpace?.invoke()
            ttsServiceBinder?.play(ttsOwnerToken)
            return
        }
        val visualNavigator = navigator
        if (!ttsAvailable || visualNavigator == null) {
            Toast.makeText(this, getString(R.string.tts_unavailable_for_epub), Toast.LENGTH_LONG).show()
            return
        }
        val requestedLocator = restoredLocator ?: when (choice) {
            EpubListenChoice.TTS_KEEP_LISTENING -> validTtsLocator(lastTtsLocator)
            EpubListenChoice.TTS_FROM_HERE -> null
            EpubListenChoice.PUBLISHER_NARRATION -> null
        }
        (application as BookOrbitApplication).audioPlaybackController.pause()
        closeMediaOverlayPlayback()
        cancelPendingTtsStart()
        EpubTtsModuleImpl.stop(applicationContext, ttsOwnerToken)
        val requestAccountEpoch = EpubTtsModuleImpl.currentAccountEpoch()
        val requestId = EpubTtsModuleImpl.beginRequest()
        ttsStartRequestId = requestId
        EpubTtsModuleImpl.start(
            applicationContext,
            ownerToken = ttsOwnerToken,
            requestId = requestId,
            accountEpoch = requestAccountEpoch,
            readerKey = readerKey,
            title = displayTitle
        )
        ttsServiceConnection?.bind()
        lateinit var startJob: Job
        startJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            var handedOffToService = false
            try {
                val initialLocator = requestedLocator ?: if (
                    choice == EpubListenChoice.TTS_FROM_HERE
                ) {
                    (visualNavigator as? VisualNavigator)?.firstVisibleElementLocator()
                } else {
                    null
                }
                if (ttsStartRequestId != requestId ||
                    !EpubTtsModuleImpl.isCurrentRequest(requestId) ||
                    !EpubTtsModuleImpl.isCurrentAccount(requestAccountEpoch)
                ) return@launch
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
                if (!File(filePath).isFile || isFinishing || isDestroyed) return@launch
                val spec = EpubTtsSessionSpec(
                    ownerToken = ttsOwnerToken,
                    requestId = requestId,
                    accountEpoch = requestAccountEpoch,
                    readerKey = readerKey,
                    libraryId = libraryId,
                    bookId = bookId,
                    fileId = intent.getStringExtra(EXTRA_FILE_ID),
                    filePath = filePath,
                    publicationFingerprint = publicationFingerprint,
                    title = displayTitle,
                    initialLocator = initialLocator,
                    selectionText = selectionText,
                    settings = ttsSettings,
                    languageTag = epubTtsLanguage(currentContentLanguage()),
                    playWhenReady = playWhenReady
                )
                if (ttsStartRequestId != requestId ||
                    !EpubTtsModuleImpl.isCurrentRequest(requestId) ||
                    !EpubTtsModuleImpl.isCurrentAccount(requestAccountEpoch)
                ) return@launch
                pendingTtsSpec = spec
                ttsView.visibility = View.VISIBLE
                updateReaderViewportOverlaySpace?.invoke()
                openPendingTtsSession()
                handedOffToService = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.tts_no_readable_position),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                if (!handedOffToService) {
                    EpubTtsModuleImpl.cancelRequest(requestId)
                    EpubTtsModuleImpl.stop(applicationContext, ttsOwnerToken, requestId)
                    if (ttsStartRequestId == requestId) ttsStartRequestId = null
                }
            }
        }
        ttsStartJob = startJob
        startJob.invokeOnCompletion {
            if (ttsStartJob === startJob) ttsStartJob = null
        }
        startJob.start()
    }

    /**
     * Follows a read-aloud session that is still running for this reader after recreation. The
     * saved read-aloud position ([restoredTtsLocator]) is not followed: without a live session it
     * is only where listening last stopped, and jumping there would pull the reader back from where
     * it has read since. It stays available as the "Keep listening" start point.
     */
    private fun restoreTtsPositionAfterRecreation(openedPublication: Publication) {
        val locator = ttsServiceBinder?.state?.value
            ?.takeIf { it.ownerToken == ttsOwnerToken && it.readerKey == readerKey && it.hasSession }
            ?.locator
        restoredTtsLocator = null
        locator?.takeIf { candidate ->
            openedPublication.readingOrder.any { link ->
                link.url().isEquivalent(candidate.href.removeFragment())
            }
        }?.let(::handleTtsLocation)
    }

    private fun validTtsLocator(locator: Locator?): Locator? {
        val candidate = locator ?: return null
        val openedPublication = publication ?: return null
        return candidate.takeIf {
            openedPublication.readingOrder.any { link ->
                link.url().isEquivalent(candidate.href.removeFragment())
            }
        }
    }

    private fun bindTtsService() {
        if (ttsServiceConnection != null) return
        ttsServiceConnection = EpubTtsServiceConnection(
            context = applicationContext,
            onConnected = { service ->
                ttsServiceBinder = service
                observeTtsService(service)
                dropStaleNarrationPosition()
                openPendingTtsSession()
            },
            onDisconnected = {
                ttsServiceBinder = null
                ttsServiceStateJob?.cancel()
                ttsServiceStateJob = null
            }
        ).also(EpubTtsServiceConnection::bind)
    }

    private fun openPendingTtsSession() {
        val service = ttsServiceBinder ?: return
        val spec = pendingTtsSpec ?: return
        pendingTtsSpec = null
        if (!service.open(spec) && ttsStartRequestId == spec.requestId) {
            ttsStartRequestId = null
        }
    }

    private fun observeTtsService(service: EpubTtsPlaybackService.PlaybackBinder) {
        ttsServiceStateJob?.cancel()
        ttsServiceStateJob = lifecycleScope.launch {
            service.state.collect { state ->
                if (state.completedOwnerToken == ttsOwnerToken &&
                    state.completionSerial > lastTtsCompletionSerial
                ) {
                    lastTtsCompletionSerial = state.completionSerial
                    lastTtsLocator = null
                }
                if (state.ownerToken != ttsOwnerToken) {
                    hideInactiveTtsControls()
                    return@collect
                }
                if (state.failure != null && state.failureSerial > lastTtsFailureSerial) {
                    lastTtsFailureSerial = state.failureSerial
                    showTtsFailure(state.failure)
                    return@collect
                }
                if (state.readerKey != readerKey || !state.hasSession) {
                    hideInactiveTtsControls()
                    return@collect
                }
                val wasPlaying = ttsIsPlaying
                ttsSettings = state.settings.normalized()
                ttsVoiceLanguageTag = state.voiceLanguageTag
                ttsVoices = state.voices
                ttsIsPlaying = state.isPlaying
                ttsCanGoPrevious = state.canGoPrevious
                ttsCanGoNext = state.canGoNext
                ttsView.visibility = View.VISIBLE
                updateReaderViewportOverlaySpace?.invoke()
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    when {
                        state.isPlaying && !wasPlaying -> readingSessionReporter.resume(progressPercentForReporting())
                        !state.isPlaying && wasPlaying -> readingSessionReporter.pause(progressPercentForReporting())
                    }
                }
                state.locator?.takeIf { it != lastHandledTtsLocator }?.let(::handleTtsLocation)
            }
        }
    }

    private fun hideInactiveTtsControls() {
        val wasPlaying = ttsIsPlaying
        ttsIsPlaying = false
        ttsCanGoPrevious = false
        ttsCanGoNext = false
        ttsVoiceLanguageTag = null
        ttsVoices = emptyList()
        if (wasPlaying && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            readingSessionReporter.pause(progressPercentForReporting())
        }
        if (::ttsView.isInitialized) {
            ttsView.visibility = View.GONE
            updateReaderViewportOverlaySpace?.invoke()
        }
    }

    private fun handleTtsLocation(locator: Locator) {
        lastHandledTtsLocator = locator
        lastTtsLocator = locator
        val visual = navigator ?: return
        expectSemanticReaderMovement(locator)
        if (epubLayoutMode != ReaderLayoutMode.PAGINATED) updateLocation(locator)
        lifecycleScope.launch {
            runCatching {
                visual.applyDecorations(
                    listOf(
                        Decoration(
                            id = "epub-tts-current",
                            locator = locator,
                            style = Decoration.Style.Highlight(
                                tint = highlightTintForColor("yellow"),
                                isActive = true
                            )
                        )
                    ),
                    EPUB_TTS_DECORATION_GROUP
                )
                if (!visual.go(locator, animated = false)) cancelExpectedReaderMovement()
            }.onFailure { cancelExpectedReaderMovement() }
        }
    }

    /** Narration of this book is running in the service, possibly started by an earlier reader screen. */
    private fun bookNarrationActive(): Boolean = ttsServiceBinder?.state?.value?.let { state ->
        state.readerKey == readerKey && state.hasSession
    } == true

    /** Called from both updateLocation and the service connection, whichever completes the conditions last. */
    private fun dropStaleNarrationPosition() {
        if (staleNarrationPositionCleanup.shouldDrop(ttsServiceBinder != null, ::bookNarrationActive)) {
            lastTtsLocator = null
            ttsPositionStore.remove(readerKey)
        }
    }

    private fun hasActiveTtsSession(): Boolean = ttsServiceBinder?.state?.value?.let { state ->
        state.ownerToken == ttsOwnerToken && state.readerKey == readerKey && state.hasSession
    } == true

    private fun toggleTtsPlayback() {
        val active = ttsServiceBinder ?: return
        if (ttsIsPlaying) {
            active.pause(ttsOwnerToken)
        } else {
            active.play(ttsOwnerToken)
        }
    }

    private fun previousTtsUtterance() {
        ttsServiceBinder?.previous(ttsOwnerToken)
    }

    private fun nextTtsUtterance() {
        ttsServiceBinder?.next(ttsOwnerToken)
    }

    private fun applyTtsSettings(settings: EpubTtsSettings) {
        val normalized = settings.normalized()
        ttsSettings = normalized
        appPreferencesStore.saveEpubTtsSettings(normalized)
        ttsServiceBinder?.setSettings(normalized, ttsOwnerToken)
    }

    private fun showTtsFailure(error: EpubTtsFailureKind) {
        val voiceLanguageTag = ttsVoiceLanguageTag
        val failedVoiceId = voiceLanguageTag?.let(ttsSettings.voiceIds::get)
        val recoveryLocator = validTtsLocator(lastTtsLocator)
        val alternativeVoices = ttsVoices.filterNot { it.id == failedVoiceId }
        if (error == EpubTtsFailureKind.LANGUAGE_DATA) {
            closeTtsPlayback()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.tts_voice_data_missing_title))
                .setMessage(getString(R.string.tts_voice_data_missing_message))
                .setPositiveButton(getString(R.string.tts_install_voice)) { _, _ ->
                    AndroidTtsEngine.requestInstallVoice(applicationContext)
                }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        } else if (voiceLanguageTag != null && failedVoiceId != null) {
            val nextSettings = ttsSettings.copy(
                voiceIds = ttsSettings.voiceIds - voiceLanguageTag
            )
            closeTtsPlayback()
            applyTtsSettings(nextSettings)
            requestTtsPlayback(
                EpubListenChoice.TTS_KEEP_LISTENING,
                restoredLocator = recoveryLocator,
                playWhenReady = false
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.tts_voice_failed_title))
                .setMessage(getString(R.string.tts_voice_failed_default_message))
                .setPositiveButton(getString(R.string.epub_ok), null)
                .show()
        } else if (voiceLanguageTag != null && alternativeVoices.isNotEmpty()) {
            val choices = listOf<EpubTtsVoice?>(null) + alternativeVoices
            val message = if (error == EpubTtsFailureKind.NETWORK) {
                getString(R.string.tts_voice_failed_network_alternative)
            } else {
                getString(R.string.tts_voice_failed_alternative)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.tts_voice_failed_title))
                .setMessage(message)
                .setItems(
                    choices.map { voice ->
                        voice?.displayName(this) ?: getString(R.string.tts_default_android_voice)
                    }.toTypedArray()
                ) { _, index ->
                    val selectedVoice = choices[index]
                    val nextSettings = ttsSettings.copy(
                        voiceIds = if (selectedVoice == null) {
                            ttsSettings.voiceIds - voiceLanguageTag
                        } else {
                            ttsSettings.voiceIds + (voiceLanguageTag to selectedVoice.id)
                        }
                    )
                    applyTtsSettings(nextSettings)
                    requestTtsPlayback(
                        EpubListenChoice.TTS_KEEP_LISTENING,
                        restoredLocator = recoveryLocator
                    )
                }
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show()
        } else {
            closeTtsPlayback()
            val message = if (error == EpubTtsFailureKind.NETWORK) {
                getString(R.string.tts_voice_failed_network)
            } else {
                getString(R.string.tts_voice_failed_generic)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun closeTtsPlayback() {
        cancelPendingTtsStart()
        ttsServiceBinder?.stop(ttsOwnerToken)
        EpubTtsModuleImpl.stop(applicationContext, ttsOwnerToken)
        ttsIsPlaying = false
        ttsCanGoPrevious = false
        ttsCanGoNext = false
        ttsVoiceLanguageTag = null
        ttsVoices = emptyList()
        if (::ttsView.isInitialized) {
            ttsView.visibility = View.GONE
            updateReaderViewportOverlaySpace?.invoke()
        }
        val visual = navigator
        if (visual != null && !isDestroyed) {
            lifecycleScope.launch {
                runCatching { visual.applyDecorations(emptyList(), EPUB_TTS_DECORATION_GROUP) }
            }
        }
    }

    private fun cancelPendingTtsStart() {
        val requestId = ttsStartRequestId
        ttsStartJob?.cancel()
        ttsStartJob = null
        pendingTtsSpec = null
        ttsStartRequestId = null
        requestId?.let(EpubTtsModuleImpl::cancelRequest)
    }

    private fun startMediaOverlayPlayback(targetClipIndex: Int? = null) {
        closeTtsPlayback()
        mediaOverlayPlayback.player?.let { retainedPlayer ->
            val targetIndex = targetClipIndex?.let { requested ->
                mediaOverlayPlayback.playableClips.indexOfFirst { it.clip.index == requested }
            }
            if (targetIndex != null && targetIndex < 0) {
                Toast.makeText(
                    this,
                    getString(R.string.epub_narration_sentence_unavailable),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            retainedPlayer.pause()
            mediaOverlayStartupNavigationPending = true
            if (targetIndex != null) retainedPlayer.seekTo(targetIndex, 0L)
            val activeIndex = targetIndex ?: retainedPlayer.currentMediaItemIndex
            val clip = mediaOverlayPlayback.playableClips.getOrNull(activeIndex)?.clip
            if (clip == null) {
                mediaOverlayStartupNavigationPending = false
                Toast.makeText(
                    this,
                    getString(R.string.epub_narration_sentence_unavailable),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            mediaOverlayPlayback.updateClip(clip)
            EpubMediaOverlayPositionStore(this).write(readerKey, clip.textHref, clip.textFragment)
            mediaOverlayView.visibility = View.VISIBLE
            updateReaderViewportOverlaySpace?.invoke()
            lifecycleScope.launch {
                try {
                    if (navigateToAndHighlightMediaOverlayClip(clip)) {
                        if (mediaOverlayPlayback.player === retainedPlayer && !isFinishing && !isDestroyed) {
                            retainedPlayer.play()
                        }
                    } else {
                        Toast.makeText(
                            this@ReadiumEpubReaderActivity,
                            getString(R.string.epub_narration_location_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    mediaOverlayStartupNavigationPending = false
                }
            }
            return
        }
        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        val source = file?.takeIf(File::isFile)
        if (source == null || mediaOverlayPlaylist.isEmpty()) {
            Toast.makeText(this, getString(R.string.epub_narration_none_available), Toast.LENGTH_LONG)
                .show()
            return
        }
        lifecycleScope.launch {
            val playable = runCatching {
                EpubMediaOverlayResources.extract(
                    epubFile = source,
                    playlist = EpubMediaOverlayPlaylist(mediaOverlayPlaylist),
                    cacheDir = cacheDir,
                    readerKey = readerKey
                )
            }.getOrDefault(emptyList())
            if (playable.isEmpty() || isFinishing || isDestroyed) {
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_narration_prepare_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val resumeIndex = epubMediaOverlayStartIndex(
                playable,
                EpubMediaOverlayPositionStore(this@ReadiumEpubReaderActivity).read(readerKey)
            )
            val requestedIndex = targetClipIndex?.let { target ->
                playable.indexOfFirst { it.clip.index == target }.takeIf { it >= 0 }
            }
            if (targetClipIndex != null && requestedIndex == null) {
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_narration_sentence_unavailable),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val startIndex = requestedIndex ?: resumeIndex
            val player = createEpubMediaOverlayPlayer(
                context = this@ReadiumEpubReaderActivity,
                pauseForAudioInterruptions = appPreferencesStore.read()
                    .pauseAudiobookForAudioInterruptions
            )
            val sessionBook = BookSummary(
                libraryId = libraryId,
                id = bookId.orEmpty(),
                fileId = intent.getStringExtra(EXTRA_FILE_ID),
                title = displayTitle,
                mediaKind = MediaKind.EPUB,
                localPath = source.absolutePath
            )
            val sessionBinder = (application as BookOrbitApplication)
                .audioPlaybackController
                .openEpubMediaOverlaySession(sessionBook, player)
            if (sessionBinder == null) {
                player.release()
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_narration_controls_unavailable),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val playbackController = (application as BookOrbitApplication).audioPlaybackController
            val savedSpeed = AppPreferencesStore(this@ReadiumEpubReaderActivity).readAudioPlaybackSpeed()
            playbackController.setPlaybackSpeed(player, savedSpeed)
            player.setMediaItems(
                playable.map {
                    epubMediaOverlayMediaItem(it, displayTitle, this@ReadiumEpubReaderActivity)
                },
                startIndex,
                0L
            )
            val activeClip = playable.getOrNull(startIndex)?.clip
            mediaOverlayStartupNavigationPending = true
            mediaOverlayPlayback.attach(player, sessionBinder, playable, activeClip)
            attachMediaOverlayPlayerListener(player)
            activeClip?.let { clip ->
                EpubMediaOverlayPositionStore(this@ReadiumEpubReaderActivity)
                    .write(readerKey, clip.textHref, clip.textFragment)
            }
            mediaOverlayView.visibility = View.VISIBLE
            updateReaderViewportOverlaySpace?.invoke()
            try {
                prepareEpubMediaOverlayThenNavigateAndPlay(
                    prepare = player::prepare,
                    navigate = {
                        try {
                            val clip = activeClip
                                ?: throw IllegalStateException("The active narration clip is unavailable.")
                            if (!navigateToAndHighlightMediaOverlayClip(clip)) {
                                throw IllegalStateException("The narration location could not be opened.")
                            }
                        } finally {
                            mediaOverlayStartupNavigationPending = false
                        }
                    },
                    play = {
                        if (mediaOverlayPlayback.player === player && !isFinishing && !isDestroyed) {
                            player.play()
                        }
                    }
                )
            } catch (_: Exception) {
                closeMediaOverlayPlayback()
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_narration_location_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun attachMediaOverlayPlayerListener(player: ExoPlayer) {
        mediaOverlayPlayerListener?.let(player::removeListener)
        mediaOverlayPlayerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = mediaItem?.mediaId?.toIntOrNull()
                val active = mediaOverlayPlayback.playableClips.firstOrNull {
                    it.clip.index == index
                }?.clip ?: return
                mediaOverlayPlayback.updateClip(active)
                EpubMediaOverlayPositionStore(this@ReadiumEpubReaderActivity)
                    .write(readerKey, active.textHref, active.textFragment)
                if (!mediaOverlayStartupNavigationPending) applyMediaOverlayHighlight(active)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mediaOverlayPlayback.updatePlaying(isPlaying)
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                mediaOverlayPlayback.updateSpeed(playbackParameters.speed)
            }

            override fun onPlayerError(error: PlaybackException) {
                mediaOverlayPlayback.updatePlaying(false)
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_narration_play_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }.also(player::addListener)
    }

    private fun restoreRetainedMediaOverlayPlayback() {
        val player = mediaOverlayPlayback.player ?: return
        if (mediaOverlayPlayback.playableClips.isEmpty()) return
        attachMediaOverlayPlayerListener(player)
        val active = mediaOverlayPlayback.playableClips.firstOrNull {
            it.clip.index == player.currentMediaItem?.mediaId?.toIntOrNull()
        }?.clip
        active?.let {
            mediaOverlayPlayback.updateClip(it)
            applyMediaOverlayHighlight(it)
        }
        mediaOverlayPlayback.updatePlaying(player.isPlaying)
        mediaOverlayPlayback.updateSpeed(player.playbackParameters.speed)
        mediaOverlayView.visibility = View.VISIBLE
        updateReaderViewportOverlaySpace?.invoke()
    }

    private fun setMediaOverlaySpeed(speed: Float) {
        val player = mediaOverlayPlayback.player ?: return
        (application as BookOrbitApplication).audioPlaybackController.setPlaybackSpeed(player, speed)
        mediaOverlayPlayback.updateSpeed(player.playbackParameters.speed)
    }

    private fun closeMediaOverlayPlayback() {
        mediaOverlayPlayerListener?.let { listener ->
            mediaOverlayPlayback.player?.removeListener(listener)
        }
        mediaOverlayPlayerListener = null
        mediaOverlayStartupNavigationPending = false
        mediaOverlayPlayback.closePlayback()
        mediaOverlayView.visibility = View.GONE
        updateReaderViewportOverlaySpace?.invoke()
        lifecycleScope.launch {
            runCatching {
                navigator?.applyDecorations(emptyList(), EPUB_MEDIA_OVERLAY_DECORATION_GROUP)
            }
        }
    }

    private fun applyMediaOverlayHighlight(clip: EpubMediaOverlayClip) {
        lifecycleScope.launch { navigateToAndHighlightMediaOverlayClip(clip) }
    }

    private suspend fun navigateToAndHighlightMediaOverlayClip(clip: EpubMediaOverlayClip): Boolean {
        val activeNavigator = navigator ?: return false
        val openedPublication = publication ?: return false
        val expectedHref = clip.textHref.removePrefix("/")
        val link = openedPublication.readingOrder.firstOrNull { candidate ->
            runCatching { URI(candidate.url().toString()).path.orEmpty() }
                .getOrDefault(candidate.url().toString())
                .removePrefix("/")
                .endsWith(expectedHref)
        } ?: openedPublication.readingOrder.getOrNull(clip.sectionIndex)
            ?: return false
        val locatorJson = JSONObject().apply {
            put("href", link.url().toString())
            put("type", MediaType.XHTML.toString())
            put("locations", JSONObject())
            clip.textFragment?.takeIf(String::isNotBlank)?.let { fragmentId ->
                put("locations", JSONObject().put("fragments", org.json.JSONArray().put(fragmentId)))
            }
        }
        val locator = runCatching { Locator.fromJSON(locatorJson) }.getOrNull() ?: return false
        return try {
            activeNavigator.applyDecorations(
                listOf(
                    Decoration(
                        id = "epub-media-overlay-current",
                        locator = locator,
                        style = Decoration.Style.Highlight(
                            tint = highlightTintForColor("yellow"),
                            isActive = true
                        )
                    )
                ),
                EPUB_MEDIA_OVERLAY_DECORATION_GROUP
            )
            expectSemanticReaderMovement(locator)
            activeNavigator.go(locator).also { moved ->
                if (!moved) cancelExpectedReaderMovement()
            }
        } catch (_: Exception) {
            cancelExpectedReaderMovement()
            false
        }
    }

    private fun toggleMediaOverlayPlayback() {
        val player = mediaOverlayPlayback.player ?: return
        if (player.isPlaying) player.pause() else startMediaOverlayPlayback()
    }

    private fun seekMediaOverlayClip(direction: Int) {
        val player = mediaOverlayPlayback.player ?: return
        val target = (player.currentMediaItemIndex + direction).coerceIn(0, player.mediaItemCount - 1)
        val targetClip = mediaOverlayPlayback.playableClips.getOrNull(target)?.clip ?: return
        startMediaOverlayPlayback(targetClip.index)
    }

    // This is installed once per navigator after renderer confirmation and replaces its prior job.
    @SuppressLint("RepeatOnLifecycleWrongUsage")
    private fun startEpubImageGestureBridge(fragment: EpubNavigatorFragment) {
        epubImageGestureJob?.cancel()
        val gestureScript = runCatching {
            assets.open(EPUB_IMAGE_GESTURE_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        // OLED in-page styling is fixed per navigator: switching into or out of OLED recreates.
        val installScript = epubCurrentDocumentInstallScript(
            gestureScript,
            oledText = selectedTheme.isOled()
        )
        epubImageGestureJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                runEpubCurrentDocumentBridgeLoop(
                    installScript = {
                        installScript + "\n" + epubDocumentLanguageScript(bookLanguageOverride)
                    },
                    evaluateJavascript = fragment::evaluateJavascript,
                    onGesture = { event -> handleEpubImageGesture(fragment, event) }
                )
            }
        }
    }

    private fun handleEpubImageGesture(fragment: EpubNavigatorFragment, event: String) {
        val payload = runCatching { JSONObject(event) }.getOrNull() ?: return
        if (!shouldOpenEpubImageViewer(payload.optString("gesture"))) return
        val imageHref = resolveEpubImageHref(
            currentLocatorHref = fragment.currentLocator.value.href.toString(),
            imageHrefs = listOf(payload.optString("href"), payload.optString("src"))
        ) ?: return
        val openedPublication = publication ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val href = Href(imageHref) ?: return@withContext null
                val link = Link(href = href)
                val resource = openedPublication.get(link) ?: return@withContext null
                try {
                    val bytes = readContinuousComicPageBytes(resource) ?: return@withContext null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } finally {
                    resource.close()
                }
            }
            if (bitmap != null) {
                epubImageViewer = currentPage to bitmap
            } else {
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_image_open_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadHighlights(openedPublication: Publication) {
        val currentBookId = bookId ?: return
        lifecycleScope.launch {
            var pageNumber = 1
            var loadedCount = 0
            do {
                val page = runCatching {
                    annotationRepository.loadAnnotations(
                        AnnotationsFilter(bookId = currentBookId, status = "active"),
                        page = pageNumber,
                        pageSize = ANNOTATIONS_PAGE_SIZE
                    )
                }.getOrNull() ?: break
                page.items.forEach { annotation ->
                    val locator = annotation.toLocatorOrNull(openedPublication) ?: return@forEach
                    highlightAnnotations[annotation.id] = annotation
                    highlightLocators[annotation.id] = locator
                }
                loadedCount += page.items.size
                pageNumber++
            } while (
                shouldLoadNextAnnotationPage(
                    loadedCount = loadedCount,
                    total = page.total,
                    receivedCount = page.items.size,
                    pageSize = page.pageSize ?: ANNOTATIONS_PAGE_SIZE
                )
            )
            refreshHighlightDecorations()
        }
    }

    private fun applyPreviewAnnotationDecoration(fragment: EpubNavigatorFragment, openedPublication: Publication) {
        val target = previewAnnotationTarget(
            annotationId = intent.getStringExtra(EXTRA_INITIAL_ANNOTATION_ID),
            bookId = bookId,
            cfi = intent.getStringExtra(EXTRA_INITIAL_CFI),
            text = intent.getStringExtra(EXTRA_INITIAL_ANNOTATION_TEXT),
            chapterIndex = intent.getIntExtra(EXTRA_INITIAL_ANNOTATION_CHAPTER, -1).takeIf { it >= 0 },
            color = intent.getStringExtra(EXTRA_INITIAL_ANNOTATION_COLOR),
            style = intent.getStringExtra(EXTRA_INITIAL_ANNOTATION_STYLE)
        ) ?: return
        val locator = target.toLocatorOrNull(openedPublication) ?: return
        previewAnnotationLocator = locator
        fragment.addDecorationListener(HIGHLIGHT_DECORATION_GROUP, highlightDecorationListener)
        highlightAnnotations[target.id] = target
        highlightLocators[target.id] = locator
        lifecycleScope.launch { refreshHighlightDecorations() }
    }

    private fun BookAnnotation.toLocatorOrNull(openedPublication: Publication): Locator? {
        val cfiValue = cfi?.takeIf { it.isNotBlank() } ?: return null
        val link = (chapterIndex ?: epubCfiSpineIndex(cfiValue))
            ?.let { openedPublication.readingOrder.getOrNull(it) }
            ?: openedPublication.readingOrder.firstOrNull()
            ?: return null
        return runCatching {
            Locator.fromJSON(annotationLocatorJson(cfiValue, link.url().toString(), text))
        }.getOrNull()
    }

    private suspend fun refreshHighlightDecorations() {
        val fragment = navigator ?: return
        val decorations = highlightAnnotations.mapNotNull { (id, annotation) ->
            val locator = highlightLocators[id] ?: return@mapNotNull null
            Decoration(
                id = id,
                locator = locator,
                style = when (annotation.style?.trim()?.lowercase()) {
                    "underline" -> Decoration.Style.Underline(
                        tint = highlightTintForColor(annotation.color),
                        isActive = false
                    )
                    else -> Decoration.Style.Highlight(
                        tint = highlightTintForColor(annotation.color),
                        isActive = false
                    )
                }
            )
        }
        runCatching { fragment.applyDecorations(decorations, HIGHLIGHT_DECORATION_GROUP) }
    }

    private fun selectionActionMenu(action: EpubSelectionAction): Pair<Int, Int> = when (action.label) {
        "Copy" -> ACTION_COPY to R.string.epub_selection_copy
        "Share" -> ACTION_SHARE to R.string.epub_selection_share
        "Play narration" -> ACTION_PLAY_NARRATION to R.string.epub_selection_play_narration
        "Listen from here" -> ACTION_TTS_FROM_SELECTION to R.string.epub_selection_listen_from_here
        "Web search" -> ACTION_WEB_SEARCH to R.string.epub_selection_web_search
        "Highlight" -> ACTION_HIGHLIGHT to R.string.epub_selection_highlight
        else -> ACTION_HIGHLIGHT_WITH_NOTE to R.string.epub_selection_highlight_note
    }

    private fun highlightActionModeCallback(): ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            epubSelectionActions(
                mediaOverlayAvailable = !isPreview && mediaOverlayPlaylist.isNotEmpty(),
                textToSpeechAvailable = !isPreview && ttsAvailable
            ).forEachIndexed { order, action ->
                val (actionId, labelRes) = selectionActionMenu(action)
                val menuItem = menu?.add(0, actionId, order, getString(labelRes))
                    ?: return@forEachIndexed
                val showAsAction = when (action.presentation) {
                    EpubSelectionActionPresentation.ALWAYS -> MenuItem.SHOW_AS_ACTION_ALWAYS
                    EpubSelectionActionPresentation.IF_ROOM -> MenuItem.SHOW_AS_ACTION_IF_ROOM
                    EpubSelectionActionPresentation.OVERFLOW -> MenuItem.SHOW_AS_ACTION_NEVER
                }
                menuItem.setShowAsAction(showAsAction)
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val action = item?.itemId ?: return false
            if (action !in SELECTION_ACTIONS) return false
            captureCurrentSelection(
                mode = mode,
                requireCfi = action == ACTION_HIGHLIGHT || action == ACTION_HIGHLIGHT_WITH_NOTE,
                captureMediaOverlayFragment = action == ACTION_PLAY_NARRATION,
                captureTtsSelection = action == ACTION_TTS_FROM_SELECTION
            ) { selection ->
                when (action) {
                    ACTION_COPY -> copySelection(selection)
                    ACTION_SHARE -> shareSelection(selection)
                    ACTION_WEB_SEARCH -> searchSelection(selection)
                    ACTION_PLAY_NARRATION -> playNarrationForSelection(selection)
                    ACTION_TTS_FROM_SELECTION -> requestTtsPlayback(
                        EpubListenChoice.TTS_FROM_HERE,
                        restoredLocator = selection.ttsLocator ?: selection.selection.locator,
                        selectionText = selectedText(selection.selection)
                    )
                    ACTION_HIGHLIGHT -> showHighlightChoiceDialog(selection, note = null)
                    ACTION_HIGHLIGHT_WITH_NOTE -> promptForNote(existingNote = null) { note ->
                        showHighlightChoiceDialog(selection, note)
                    }
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) = Unit
    }

    private fun captureCurrentSelection(
        mode: ActionMode?,
        requireCfi: Boolean,
        captureMediaOverlayFragment: Boolean = false,
        captureTtsSelection: Boolean = false,
        action: (CapturedEpubSelection) -> Unit
    ) {
        val fragment = navigator ?: return
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val captured = runCatching {
                captureSelectionBeforeAction(
                    capture = {
                        val selection = fragment.currentSelection()
                            ?: return@captureSelectionBeforeAction null
                        val cfi = if (requireCfi) generateSelectionCfi(fragment, selection) else null
                        if (requireCfi && cfi == null) return@captureSelectionBeforeAction null
                        val textFragment = if (captureMediaOverlayFragment) {
                            selectedMediaOverlayFragmentId(fragment)
                        } else null
                        val ttsLocator = if (captureTtsSelection) {
                            locatorForTtsSelection(
                                selection.locator,
                                selectedTtsCssSelector(fragment)
                            )
                        } else null
                        CapturedEpubSelection(selection, cfi, textFragment, ttsLocator)
                    },
                    action = action
                )
            }.getOrElse { error ->
                Log.e(TAG, "Could not capture the EPUB text selection.", error)
                false
            }
            if (!captured) {
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(
                        if (requireCfi) {
                            R.string.epub_selection_highlight_failed
                        } else {
                            R.string.epub_selection_unavailable
                        }
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
            mode?.finish()
        }
    }

    private suspend fun generateSelectionCfi(
        fragment: EpubNavigatorFragment,
        selection: Selection
    ): String? {
        val openedPublication = publication ?: return null
        val spineIndex = selectedSpineIndex(
            selectedHref = selection.locator.href.toString(),
            readingOrderHrefs = openedPublication.readingOrder.map { it.url().toString() }
        ) ?: return null
        val script = assets.open(FOLIATE_SELECTION_CFI_ASSET).bufferedReader().use { it.readText() }
        val innerCfi = decodeJavascriptString(fragment.evaluateJavascript(script))
        return combineEpubCfi(spineIndex, innerCfi)
    }

    private suspend fun selectedMediaOverlayFragmentId(fragment: EpubNavigatorFragment): String? =
        decodeJavascriptString(
            fragment.evaluateJavascript(
                """(() => {
                    const selection = window.getSelection();
                    if (!selection || selection.rangeCount === 0) return null;
                    const idFor = node => {
                        const element = node?.nodeType === 1 ? node : node?.parentElement;
                        return element?.closest('[id]')?.id || null;
                    };
                    const startId = idFor(selection.anchorNode);
                    const endId = idFor(selection.focusNode);
                    return startId && startId === endId ? startId : idFor(selection.getRangeAt(0).commonAncestorContainer);
                })()"""
            )
        )

    private suspend fun selectedTtsCssSelector(fragment: EpubNavigatorFragment): String? =
        decodeJavascriptString(
            fragment.evaluateJavascript(
                """(() => {
                    const selection = window.getSelection();
                    if (!selection || selection.rangeCount === 0) return null;
                    let element = selection.anchorNode?.nodeType === Node.ELEMENT_NODE
                        ? selection.anchorNode
                        : selection.anchorNode?.parentElement;
                    if (!element) return null;
                    element = element.closest(
                        'p,li,blockquote,pre,h1,h2,h3,h4,h5,h6,section,article,div'
                    ) || element;
                    const parts = [];
                    while (element && element !== document.body) {
                        let index = 1;
                        let sibling = element.previousElementSibling;
                        while (sibling) {
                            if (sibling.tagName === element.tagName) index++;
                            sibling = sibling.previousElementSibling;
                        }
                        parts.unshift(
                            element.tagName.toLowerCase() + ':nth-of-type(' + index + ')'
                        );
                        element = element.parentElement;
                    }
                    return parts.length ? 'body > ' + parts.join(' > ') : null;
                })()"""
            )
        )

    private fun playNarrationForSelection(selection: CapturedEpubSelection) {
        val openedPublication = publication ?: return
        val sectionIndex = selectedSpineIndex(
            selectedHref = selection.selection.locator.href.toString(),
            readingOrderHrefs = openedPublication.readingOrder.map { it.url().toString() }
        ) ?: return showNarrationSelectionUnavailable()
        val clipIndex = epubMediaOverlayClipIndexForSelection(
            playlist = mediaOverlayPlaylist,
            sectionIndex = sectionIndex,
            textFragment = selection.mediaOverlayTextFragment
        ) ?: return showNarrationSelectionUnavailable()
        startMediaOverlayPlayback(clipIndex)
    }

    private fun showNarrationSelectionUnavailable() {
        Toast.makeText(
            this,
            getString(R.string.epub_narration_selection_no_match),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun selectedText(selection: Selection): String? =
        selection.locator.text.highlight?.takeIf { it.isNotBlank() }

    private fun copySelection(captured: CapturedEpubSelection) {
        val selection = captured.selection
        val text = selectedText(selection) ?: return
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.epub_clipboard_selected_text), text)
            )
        Toast.makeText(this, getString(R.string.epub_selection_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareSelection(captured: CapturedEpubSelection) {
        val selection = captured.selection
        val text = selectedText(selection) ?: return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.epub_share_selected_text)
            )
        )
    }

    private fun searchSelection(captured: CapturedEpubSelection) {
        val selection = captured.selection
        val text = selectedText(selection) ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.epub_selection_web_search_unavailable),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showHighlightChoiceDialog(selection: CapturedEpubSelection, note: String?) {
        if (isFinishing || isDestroyed) return
        val choices = epubHighlightChoices()
        val adapter = object : ArrayAdapter<HighlightChoice>(
            this,
            android.R.layout.simple_list_item_1,
            choices
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val choice = getItem(position) ?: return view
                view.text = getString(choice.labelRes)
                view.setPadding(dpToPx(20), view.paddingTop, dpToPx(20), view.paddingBottom)
                val swatch = if (choice.style == "underline") {
                    ColorDrawable(choice.previewColor).apply {
                        setBounds(0, 0, dpToPx(32), dpToPx(4))
                    }
                } else {
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(4).toFloat()
                        setColor(choice.previewColor)
                        setBounds(0, 0, dpToPx(32), dpToPx(20))
                    }
                }
                view.setCompoundDrawables(swatch, null, null, null)
                view.compoundDrawablePadding = dpToPx(16)
                return view
            }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.epub_highlight_style_title))
            .setAdapter(adapter) { _, which ->
                choices.getOrNull(which)?.let { choice ->
                    createHighlightFromSelection(
                        selection = selection,
                        note = note,
                        color = choice.color,
                        style = choice.style
                    )
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun createHighlightFromSelection(
        selection: CapturedEpubSelection,
        note: String?,
        color: String = DEFAULT_HIGHLIGHT_COLOR,
        style: String = DEFAULT_HIGHLIGHT_STYLE
    ) {
        val currentBookId = bookId ?: return
        lifecycleScope.launch {
            val cfiValue = selection.cfi
            val text = selection.selection.locator.text.highlight?.takeIf { it.isNotBlank() }
            if (cfiValue.isNullOrBlank() || text == null) {
                Log.w(TAG, "The captured EPUB selection did not produce text and a range CFI.")
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_selection_highlight_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val annotation = runCatching {
                annotationRepository.createAnnotation(
                    bookId = currentBookId,
                    cfi = cfiValue,
                    bookFileId = intent.getStringExtra(EXTRA_FILE_ID),
                    text = text,
                    color = color,
                    style = style,
                    note = note,
                    chapterTitle = chapterTitles.getOrNull(currentChapter)
                )
            }.onFailure { error ->
                Log.e(TAG, "Could not create the EPUB annotation.", error)
            }.getOrNull()
            if (annotation == null) {
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_highlight_save_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            highlightAnnotations[annotation.id] = annotation
            highlightLocators[annotation.id] = selection.selection.locator.copyWithLocations(
                fragments = listOf(cfiValue)
            )
            navigator?.clearSelection()
            refreshHighlightDecorations()
        }
    }

    private val highlightDecorationListener = object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
            val annotation = highlightAnnotations[event.decoration.id] ?: return false
            showHighlightActionsDialog(annotation)
            return true
        }
    }

    private fun showHighlightActionsDialog(annotation: BookAnnotation) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    if (annotation.note.isNullOrBlank()) {
                        R.string.epub_highlight_actions_title
                    } else {
                        R.string.epub_highlight_note_actions_title
                    }
                )
            )
            .setItems(
                arrayOf(
                    getString(R.string.epub_highlight_edit_note),
                    getString(R.string.epub_highlight_delete),
                    getString(R.string.common_cancel)
                )
            ) { _, which ->
                when (which) {
                    0 -> promptForNote(existingNote = annotation.note) { note -> updateHighlightNote(annotation, note) }
                    1 -> deleteHighlight(annotation)
                }
            }
            .show()
    }

    private fun promptForNote(existingNote: String?, onSave: (String?) -> Unit) {
        if (isFinishing || isDestroyed) return
        val input = EditText(this).apply { setText(existingNote.orEmpty()) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.epub_note_title))
            .setView(input)
            .setPositiveButton(getString(R.string.epub_note_save)) { _, _ ->
                onSave(input.text?.toString()?.trim()?.takeIf { it.isNotBlank() })
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun updateHighlightNote(annotation: BookAnnotation, note: String?) {
        val currentBookId = bookId ?: return
        lifecycleScope.launch {
            val succeeded = runCatching {
                annotationRepository.updateAnnotation(
                    bookId = currentBookId,
                    annotationId = annotation.id,
                    note = note,
                    color = annotation.color,
                    style = annotation.style
                )
            }.isSuccess
            if (succeeded) {
                highlightAnnotations[annotation.id] = annotation.copy(note = note)
            }
        }
    }

    private fun deleteHighlight(annotation: BookAnnotation) {
        val currentBookId = bookId ?: return
        lifecycleScope.launch {
            val succeeded = runCatching {
                annotationRepository.deleteAnnotation(currentBookId, annotation.id)
            }.isSuccess
            if (succeeded) {
                highlightAnnotations.remove(annotation.id)
                highlightLocators.remove(annotation.id)
                refreshHighlightDecorations()
            }
        }
    }

    private fun initialLocator(openedPublication: Publication): Locator {
        val requestedChapter = intent.getIntExtra(EXTRA_INITIAL_CHAPTER, 0)
            .coerceIn(0, openedPublication.readingOrder.lastIndex)
        val initialPage = intent.getIntExtra(EXTRA_INITIAL_PAGE, 0).coerceAtLeast(0)
        val initialPageCount = intent.getIntExtra(EXTRA_INITIAL_PAGE_COUNT, 1).coerceAtLeast(1)
        fun belongsToPublication(locator: Locator): Boolean =
            openedPublication.readingOrder.any { link ->
                link.url().isEquivalent(locator.href.removeFragment())
            }

        // An explicit annotation/deep link is a navigation request, not resume state, and wins.
        intent.getStringExtra(EXTRA_INITIAL_CFI)?.takeIf { it.isNotBlank() }?.let { cfi ->
            val explicitChapterIndex = intent.getIntExtra(EXTRA_INITIAL_ANNOTATION_CHAPTER, -1)
                .takeIf { it >= 0 }
            val json = resolveInitialAnnotationLocatorJson(
                cfi = cfi,
                explicitChapterIndex = explicitChapterIndex,
                text = intent.getStringExtra(EXTRA_INITIAL_ANNOTATION_TEXT),
                readingOrderHrefs = openedPublication.readingOrder.map { it.url().toString() }
            )
            if (json != null) {
                runCatching { Locator.fromJSON(json) }.getOrNull()
                    ?.takeIf(::belongsToPublication)
                    ?.let { return it }
            }
        }
        intent.getStringExtra(EXTRA_INITIAL_LOCATOR_JSON)?.takeIf { it.isNotBlank() }?.let { json ->
            boundedReaderLocatorJson(json)
                ?.let { bounded -> runCatching { Locator.fromJSON(JSONObject(bounded)) }.getOrNull() }
                ?.takeIf(::belongsToPublication)
                ?.let { return it }
        }
        if (!isPreview && allowLegacyLocatorFallback) {
            legacyLocatorStore.read(readerKey)?.let { stored ->
                if (belongsToPublication(stored)) {
                    return stored
                }
            }
        }
        return locatorFromPercent(
            openedPublication,
            initialPercent = intent.getFloatExtra(EXTRA_INITIAL_PERCENT, Float.NaN).takeUnless(Float::isNaN),
            requestedChapter = requestedChapter,
            initialPage = initialPage,
            initialPageCount = initialPageCount
        )
    }

    private fun locatorFromPercent(
        openedPublication: Publication,
        initialPercent: Float?,
        requestedChapter: Int,
        initialPage: Int,
        initialPageCount: Int
    ): Locator {
        val chapterCount = openedPublication.readingOrder.size.coerceAtLeast(1)
        val chapterIndex = when {
            isPreview -> 0
            requestedChapter > 0 -> requestedChapter.coerceIn(0, chapterCount - 1)
            else -> percentToChapterIndex(initialPercent, chapterCount)
        }
        val totalProgression = if (isPreview) 0.0 else initialPercent
            ?.div(100f)
            ?.coerceIn(0f, 1f)
            ?.toDouble()
        if (!isPreview) {
            val stableIndex = bookPositionIndex
            val positionIndex = selectCanonicalEpubPositionIndex(initialPercent, stableIndex)
            stableIndex?.positions?.getOrNull(positionIndex ?: -1)?.let { return it }
        }
        val resourceProgression = when {
            isPreview -> 0.0
            initialPageCount > 1 -> initialPage.toDouble() / initialPageCount.toDouble()
            totalProgression != null -> (totalProgression * chapterCount - chapterIndex).coerceIn(0.0, 1.0)
            else -> 0.0
        }
        return requireNotNull(openedPublication.locatorFromLink(openedPublication.readingOrder[chapterIndex]))
            .copyWithLocations(
                progression = resourceProgression.coerceIn(0.0, 1.0),
                totalProgression = totalProgression
            )
    }

    /** Total progression of a locator, read from the book's own positions when it carries none. */
    private fun locatorTotalProgression(locator: Locator): Double? = locator.locations.totalProgression

    /** Overall percent of a locator, using the book's own positions when it carries no total progression. */
    private fun locatorOverallPercent(openedPublication: Publication, locator: Locator): Float {
        val chapterIndex = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(locator.href.removeFragment())
        }.coerceAtLeast(0)
        return readiumOverallPercent(
            totalProgression = locatorTotalProgression(locator),
            resourceProgression = locator.locations.progression,
            chapterIndex = chapterIndex,
            chapterCount = openedPublication.readingOrder.size,
            overallPosition = bookPositionIndex?.let { readiumOverallPosition(locator, it) }
        )
    }

    /**
     * Records a position reported by the visual navigator. When background read-aloud moved the
     * book, suppress re-pagination reports from the old page until the navigator reaches the newer
     * position or the bounded foreground-sync window expires.
     */
    private fun acceptVisualLocator(locator: Locator, pageMoved: Boolean = false): Boolean {
        if (!rendererHealthConfirmed) {
            pendingRendererLocator = locator
            return false
        }
        val last = lastRecordedVisualLocator
        visualLocatorReportCount += 1
        foregroundSyncTarget?.let { target ->
            val expired = SystemClock.elapsedRealtime() > foregroundSyncDeadline
            if (!expired && isStaleForegroundReReport(last, target, locator)) return false
            endForegroundSync()
        }
        if (!pageMoved && !shouldRecordVisualLocator(last, locator)) {
            duplicateVisualLocatorReportCount += 1
            return false
        }
        return true
    }

    private fun applyAcceptedVisualLocator(
        locator: Locator,
        navigatorAtEnd: Boolean = false,
        commitProgress: Boolean = true
    ): Boolean {
        if (!updateLocation(locator, navigatorAtEnd, commitProgress)) return false
        lastRecordedVisualLocator = locator
        return true
    }

    private fun recordVisualLocator(locator: Locator, navigatorAtEnd: Boolean = false): Boolean {
        // Paginated progress is accepted only with the matching page geometry supplied by
        // PaginationListener. The locator flow often arrives first or repeats it afterwards.
        if (epubLayoutMode == ReaderLayoutMode.PAGINATED) return false
        if (!acceptVisualLocator(locator)) return false
        val visualLocatorApplied = applyAcceptedVisualLocator(
            locator = locator,
            navigatorAtEnd = navigatorAtEnd,
            commitProgress = true
        )
        if (
            shouldConfirmEpubProgressDisplay(
                layoutMode = epubLayoutMode,
                paginationApplied = false,
                visualLocatorApplied = visualLocatorApplied
            )
        ) confirmProgressDisplay()
        return visualLocatorApplied
    }

    private fun updateLocation(
        locator: Locator,
        navigatorAtEnd: Boolean = false,
        commitProgress: Boolean = true
    ): Boolean {
        val openedPublication = publication ?: return false
        val chapterIndex = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(locator.href.removeFragment())
        }.takeIf { it >= 0 } ?: return false
        latestReaderLocator = locator
        currentChapter = chapterIndex.coerceIn(0, openedPublication.readingOrder.lastIndex)
        currentContentPosition = bookPositionIndex?.let { readiumOverallPosition(locator, it) }
        val layoutKey = renderedPageLayoutKey(openedPublication)
        val canonicalLocator = locator.toEpubCanonicalPosition()
        currentRenderedOverallPosition = if (
            layoutKey != null &&
            renderedPageChapterIndex == currentChapter &&
            sameEpubRenderedLocation(renderedPageLocator, canonicalLocator)
        ) {
            renderedEpubOverallPosition(
                ledger = renderedPageLedger,
                layoutKey = layoutKey,
                readingOrderCount = openedPublication.readingOrder.size,
                chapterIndex = currentChapter,
                pageIndex = currentPage,
                pageCount = currentPageCount
            )
        } else null
        currentPercent = readiumOverallPercent(
            totalProgression = null,
            resourceProgression = locator.locations.progression,
            chapterIndex = currentChapter,
            chapterCount = openedPublication.readingOrder.size,
            overallPosition = currentContentPosition,
            navigatorAtEnd = epubLayoutMode == ReaderLayoutMode.PAGINATED && (
                navigatorAtEnd ||
                    sameEpubRenderedLocation(renderedEndPosition, canonicalLocator)
                )
        )
        currentDisplayPercent = readiumOverallPercent(
            totalProgression = null,
            resourceProgression = locator.locations.progression,
            chapterIndex = currentChapter,
            chapterCount = openedPublication.readingOrder.size,
            overallPosition = currentContentPosition,
            renderedPagePosition = currentRenderedOverallPosition,
            navigatorAtEnd = epubLayoutMode == ReaderLayoutMode.PAGINATED && (
                navigatorAtEnd || sameEpubRenderedLocation(renderedEndPosition, canonicalLocator)
                )
        )
        currentResourceProgression = normalizedReaderProgression(locator.locations.progression?.toFloat())
        if (commitProgress) {
            if (!isPreview) persistReaderPosition(locator)
            staleNarrationPositionCleanup.readerMoved()
            dropStaleNarrationPosition()
            readingSessionReporter.activity(currentPercent)
        }
        updateResult()
        return true
    }

    private fun persistReaderPosition(locator: Locator) {
        val identity = persistedPositionIdentity
        if (identity == null) {
            // Direct and legacy launches have no account-scoped identity. Publication callbacks
            // start only after identity resolution, so canonical sessions cannot enter this path.
            if (!isPreview) {
                legacyLocatorStore.save(readerKey, locator)
            }
            return
        }
        positionWriterLease ?: return
        val nextWriteMillis = maxOf(System.currentTimeMillis(), lastPositionWriteMillis + 1)
        lastPositionWriteMillis = nextWriteMillis
        positionWriterSequence += 1L
        val sequence = positionWriterSequence
        val snapshot = identity.copy(
            chapterIndex = currentChapter,
            pageIndex = currentPage,
            pageCount = currentPageCount,
            updatedAtMillis = nextWriteMillis,
            locatorJson = boundedReaderLocatorJson(locator.toJSON().toString()),
            progressPercent = currentPercent,
            overallPositionNumber = currentContentPosition?.number,
            overallPositionCount = currentContentPosition?.count,
            renderedPageLayoutKey = renderedPageLedger?.layoutKey,
            renderedPageCounts = renderedPageLedger?.pageCounts.orEmpty()
        )
        lastCanonicalPosition = snapshot
        passiveRecoveryGuard.committed(
            snapshot.takeIf { epubLayoutMode == ReaderLayoutMode.PAGINATED }
        )
        latestPositionWriter?.submit(sequence, snapshot)
    }

    private suspend fun flushLatestReaderPosition() {
        val writer = latestPositionWriter ?: return
        if (awaitEpubPositionCloseFlush { writer.flush() }) return
        val lease = positionWriterLease ?: return
        val snapshot = lastCanonicalPosition ?: return
        positionWriterSequence += 1L
        val retrySequence = positionWriterSequence
        // A false retry means this reader was superseded or reset. In that case the newer
        // owner is authoritative and this activity must not recreate its old position.
        withContext(Dispatchers.IO) {
            epubPositionStore.saveOwned(lease, retrySequence, snapshot)
        }
    }

    private fun expectPageOrChapterReaderMovement() {
        passiveRecoveryGuard.expectPageOrChapterMovement(
            SystemClock.elapsedRealtime() + EPUB_AUTHORITATIVE_MOVEMENT_TIMEOUT_MILLIS
        )
    }

    private fun finishPageOrChapterReaderSwipe(horizontalOffset: Float, chapterCount: Int) {
        if (epubLayoutMode != ReaderLayoutMode.PAGINATED) return
        if (
            shouldCancelFinishedEpubSwipe(
                layoutMode = epubLayoutMode,
                horizontalOffset = horizontalOffset,
                pagingTouchSlop = ViewConfiguration.get(this).scaledPagingTouchSlop,
                pageIndex = currentPage,
                pageCount = currentPageCount,
                chapterIndex = currentChapter,
                chapterCount = chapterCount
            )
        ) {
            cancelExpectedReaderMovement()
        } else {
            passiveRecoveryGuard.finishPageOrChapterMovement()
        }
    }

    private fun expectSemanticReaderMovement(target: Locator) {
        passiveRecoveryGuard.expectSemanticMovement(
            target.toEpubCanonicalPosition(),
            SystemClock.elapsedRealtime() + EPUB_AUTHORITATIVE_MOVEMENT_TIMEOUT_MILLIS
        )
    }

    private fun cancelExpectedReaderMovement() {
        passiveRecoveryGuard.cancelExpectedMovement()
    }

    private fun beginPassiveRendererRecovery() {
        if (epubLayoutMode != ReaderLayoutMode.PAGINATED) {
            passiveRecoveryGuard.begin(null)
            return
        }
        val position = lastCanonicalPosition
        passiveRecoveryGuard.begin(position)
        position ?: return
        val openedPublication = publication
        val locator = position.locatorJson
            ?.let(::boundedReaderLocatorJson)
            ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }
        restoredDisplayProgress = if (openedPublication != null && locator != null) {
            restoredEpubDisplayProgress(
                position = position,
                openingLocator = locator,
                openingChapterIndex = position.chapterIndex,
                chapterCount = openedPublication.readingOrder.size,
                layoutMode = epubLayoutMode,
                renderedLayoutKey = renderedPageLayoutKey(openedPublication)
            )
        } else {
            null
        }
        restoredDisplayPosition = position.takeIf { restoredDisplayProgress != null }
        confirmedProgressDisplay = false
        syncProgressFooter()
    }

    private fun showPassiveRecoveryDisplay() {
        val protected = passiveRecoveryGuard.positionToPreserve ?: return
        restoredDisplayProgress = passiveEpubDisplayProgress(
            position = protected,
            chapterIndex = currentChapter,
            chapterCount = chapterTitles.size.coerceAtLeast(1),
            pageIndex = currentPage,
            pageCount = currentPageCount,
            renderedOverallPosition = currentRenderedOverallPosition,
            layoutMode = epubLayoutMode
        )
        restoredDisplayPosition = protected
        confirmedProgressDisplay = false
        syncProgressFooter()
    }

    private fun progressPercentForReporting(): Float? {
        val protected = passiveRecoveryGuard.positionToPreserve
        return if (protected != null) protected.progressPercent else currentPercent
    }

    private fun currentProgressStatus(): EpubReaderProgressStatus = epubReaderProgressStatus(
        chapterIndex = currentChapter,
        chapterCount = chapterTitles.size.coerceAtLeast(1),
        pageIndex = currentPage,
        pageCount = currentPageCount,
        completionPercent = currentDisplayPercent,
        overallPositionNumber = currentRenderedOverallPosition?.number,
        overallPositionCount = currentRenderedOverallPosition?.count,
        layoutMode = epubLayoutMode
    ).let { confirmed -> restoredDisplayProgress ?: confirmed }

    private fun confirmProgressDisplay() {
        val changed = !confirmedProgressDisplay || restoredDisplayProgress != null
        confirmedProgressDisplay = true
        restoredDisplayProgress = null
        restoredDisplayPosition = null
        if (changed) syncProgressFooter()
    }

    private fun goToChapter(index: Int) {
        val link = publication?.readingOrder?.getOrNull(index) ?: return
        expectPageOrChapterReaderMovement()
        endForegroundSync()
        if (navigator?.go(link) != true) cancelExpectedReaderMovement()
    }

    private fun goToPage(index: Int) {
        val activeNavigator = navigator ?: return
        val safePageCount = currentPageCount.coerceAtLeast(1)
        val target = index.coerceIn(0, safePageCount - 1)
        expectPageOrChapterReaderMovement()
        endForegroundSync()
        val progression = if (safePageCount <= 1) 0.0 else {
            target.toDouble() / (safePageCount - 1).toDouble()
        }
        if (!activeNavigator.go(
            activeNavigator.currentLocator.value.copyWithLocations(progression = progression)
        )) cancelExpectedReaderMovement()
    }

    private fun goToProgression(progression: Float) {
        val activeNavigator = navigator ?: return
        val target = activeNavigator.currentLocator.value.copyWithLocations(
            progression = normalizedReaderProgression(progression).toDouble()
        )
        expectPageOrChapterReaderMovement()
        endForegroundSync()
        if (!activeNavigator.go(target)) cancelExpectedReaderMovement()
    }

    private fun chooseCustomFont() {
        customFontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream"))
    }

    private fun displayNameForUri(uri: android.net.Uri): String? = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun removeCustomFont() {
        val old = readerPreferences.customFont
        val next = readerPreferences.copy(
            customFont = null,
            fontFamily = EpubReaderFontFamily.PUBLISHER_DEFAULT
        )
        customFontStore.remove(old)
        applyReaderPreferences(next)
        recreate()
    }

    private fun applyReaderPreferences(next: LibraryReaderPreferences) {
        val normalized = next.normalized()
        val previousTheme = readerPreferences.theme
        val tapZoneChanged = readerTapZonePreferencesChanged(readerPreferences, normalized)
        renderedPageChapterIndex = null
        renderedPageLocator = null
        currentRenderedOverallPosition = null
        readerPreferences = normalized
        selectedTheme = normalized.theme
        selectedBackgroundTheme = normalized.backgroundTheme
        selectedUseCustomBackground = normalized.useCustomBackground
        selectedCustomBackgroundColor = normalized.customBackgroundColor
        selectedFontFamily = normalized.fontFamily
        padding = normalized.padding
        fontScale = normalized.fontScale
        readingDirection = normalized.readingDirection
        epubLayoutMode = normalized.epubLayoutMode
        // Paginated preference submission can reflow Readium, but changing appearance is not reading.
        beginPassiveRendererRecovery()
        syncProgressFooter()
        themeStore.save(normalized.theme)
        rootView.setBackgroundColor(readerContentBackgroundColor(normalized))
        readerViewport.setBackgroundColor(readerContentBackgroundColor(normalized))
        readerContainer.setBackgroundColor(readerContentBackgroundColor(normalized))
        applyReaderPadding()
        navigator?.submitPreferences(
            readiumPreferences(
                normalized.theme,
                normalized.fontScale,
                normalized.lineSpacing,
                normalized.readingDirection,
                normalized.epubLayoutMode,
                normalized.fontFamily,
                normalized.wordSpacing,
                twoPageSpreadActive,
                spreadPageMarginCssPx(),
                normalized.textAlignment,
                normalized.hyphenation,
                fallbackContentLanguage,
                normalized.customBackgroundColor,
                normalized.customTextColor,
                normalized.backgroundTheme,
                normalized.useCustomBackground,
                normalized.useCustomTextColor,
                systemDarkMode(),
                normalized.paragraphSpacing
            )
        )
        val current = appPreferencesStore.read()
        appPreferencesStore.save(
            current.withReaderPreferences(libraryId, normalized)
        )
        configureSystemBars()
        if (epubReaderThemeChangeRequiresRecreate(previousTheme, normalized.theme)) {
            recreate()
            return
        }
        if (recreateIfSpreadChanged()) return
        if (tapZoneChanged && showTapZoneTutorialAfterChanges) showTapZoneTutorial()
    }

    private fun spreadPageMarginCssPx(): Double =
        epubSpreadPageMarginCssPx(resources.configuration.screenWidthDp, padding.left, padding.right)

    private fun computeTwoPageSpread(): Boolean = shouldUseTwoPageSpread(
        mode = readerPreferences.twoPageSpread,
        window = windowTracker.state.value,
        layoutMode = epubLayoutMode
    )

    /**
     * Window size changes already recreate this activity. Posture or preference changes that flip
     * the spread decision recreate it too, reopening at the saved locator, because Readium CSS
     * column properties cannot be changed on a live navigator.
     */
    private fun recreateIfSpreadChanged(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (computeTwoPageSpread() == twoPageSpreadActive) return false
        recreate()
        return true
    }

    /** Mirrors the reader-preference resolution in [onCreate] so the window theme matches it. */
    private fun initialReaderTheme(): EpubReaderTheme {
        val library = intent.getStringExtra(EXTRA_LIBRARY_ID).orEmpty()
        return appPreferencesStore.read().libraryReaderPreferences[library]?.theme
            ?: themeStore.read()
    }

    private fun currentContentLanguage(): EpubContentLanguage =
        resolveEpubContentLanguage(bookLanguageOverride, publicationLanguages, Locale.getDefault())

    private fun bookLanguageChoice(): EpubBookLanguageChoice? =
        publication?.let {
            epubBookLanguageChoice(
                bookLanguageOverride,
                publicationLanguages,
                Locale.getDefault(),
                context = this
            )
        }

    /** Sets or clears (null = Auto) this book's language for hyphenation and read-aloud. */
    private fun setBookLanguage(languageTag: String?) {
        val normalized = languageTag?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == bookLanguageOverride) return
        bookLanguageOverride = normalized
        if (!isPreview) bookLanguageStore.save(readerKey, normalized)
        val language = currentContentLanguage()
        fallbackContentLanguage = epubNavigatorLanguage(language)
        ttsServiceBinder?.setLanguage(ttsOwnerToken, epubTtsLanguage(language))
        applyReaderPreferences(readerPreferences)
    }

    internal fun setBookLanguageForTest(languageTag: String?) = setBookLanguage(languageTag)

    private fun updateShowTapZoneTutorialAfterChanges(enabled: Boolean) {
        showTapZoneTutorialAfterChanges = enabled
        appPreferencesStore.save(
            appPreferencesStore.read().copy(showEpubTapZoneTutorialAfterChanges = enabled)
        )
    }

    private fun applyReaderPadding() {
        if (!::readerViewport.isInitialized || readerViewport.width <= 0 || readerViewport.height <= 0) return
        fun horizontal(value: Float): Int =
            (readerViewport.width * value.coerceIn(0f, 100f) / 400f).toInt()
        fun vertical(value: Float): Int =
            (readerViewport.height * value.coerceIn(0f, 100f) / 400f).toInt()
        val normalTopPadding = vertical(padding.top)
        val occupiedBannerBottom = if (
            ::viewOnlyBannerView.isInitialized && viewOnlyBannerView.visibility == View.VISIBLE
        ) {
            val topMargin = (viewOnlyBannerView.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0
            occupiedPreviewBannerBottom(topMargin, viewOnlyBannerView.height)
        } else 0
        val (leftPercent, rightPercent) = epubHorizontalPaddingPercents(
            padding.left,
            padding.right,
            epubSpreadActive(twoPageSpreadActive, epubLayoutMode),
            fixedLayout = fixedLayoutPublication
        )
        readerContainer.setPadding(
            horizontal(leftPercent),
            effectiveReaderTopSpace(normalTopPadding, occupiedBannerBottom, isPreview),
            horizontal(rightPercent),
            vertical(padding.bottom) + if (readerPreferences.showPageProgress) {
                dpToPx(EPUB_READER_PROGRESS_FOOTER_HEIGHT_DP)
            } else {
                0
            }
        )
    }

    private fun syncProgressFooter() {
        if (!::footerView.isInitialized) return
        val controlsVisible = ::chromeView.isInitialized && chromeView.visibility == View.VISIBLE
        val progressReady = confirmedProgressDisplay || restoredDisplayProgress != null
        footerView.visibility = if (progressReady && (readerPreferences.showPageProgress || controlsVisible)) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (controlsVisible) footerView.bringToFront()
        applyReaderPadding()
    }

    private fun toggleChrome() {
        if (chromeView.visibility == View.VISIBLE) hideChrome() else showChrome()
    }

    private fun showChrome() {
        hideOptions()
        chromeView.visibility = View.VISIBLE
        syncProgressFooter()
    }

    private fun hideChrome() {
        chromeView.visibility = View.GONE
        syncProgressFooter()
    }

    private fun showOptions() {
        optionsView.visibility = View.VISIBLE
        optionsView.bringToFront()
    }

    private fun hideOptions() {
        optionsView.visibility = View.GONE
    }

    private fun continueReading() {
        hideOptions()
        hideChrome()
    }

    private fun enableReadMode() {
        if (!isPreview) return
        isPreview = false
        readingSessionReporter.enable(currentPercent)
        viewOnlyBannerView.visibility = View.GONE
        applyReaderPadding()
        pendingAnnotationReanchor = previewAnnotationLocator
        readerContainer.post {
            navigator?.currentLocator?.value?.let { locator ->
                pendingAnnotationReanchor?.let { target ->
                    pendingAnnotationReanchor = null
                    if (locator != target) {
                        expectSemanticReaderMovement(target)
                        if (navigator?.go(target) != true) cancelExpectedReaderMovement()
                    }
                }
            }
            lifecycleScope.launch { refreshHighlightDecorations() }
        }
    }

    private fun showTapZoneTutorial() {
        tapZoneTutorialHasShown = true
        tapZoneTutorialView.visibility = View.VISIBLE
        tapZoneTutorialHideJob?.cancel()
        tapZoneTutorialHideJob = lifecycleScope.launch {
            delay(READER_TAP_ZONE_TUTORIAL_DURATION_MILLIS)
            hideTapZoneTutorial()
        }
    }

    private fun hideTapZoneTutorial() {
        tapZoneTutorialHideJob?.cancel()
        tapZoneTutorialHideJob = null
        tapZoneTutorialView.visibility = View.GONE
    }

    internal fun areReaderControlsVisible(): Boolean =
        areLightweightControlsVisible() || areReaderOptionsVisible()

    internal fun areLightweightControlsVisible(): Boolean =
        ::chromeView.isInitialized && chromeView.visibility == View.VISIBLE

    internal fun areReaderOptionsVisible(): Boolean =
        ::optionsView.isInitialized && optionsView.visibility == View.VISIBLE

    internal fun isTapZoneTutorialVisible(): Boolean =
        ::tapZoneTutorialView.isInitialized && tapZoneTutorialView.visibility == View.VISIBLE

    internal fun hasShownTapZoneTutorial(): Boolean = tapZoneTutorialHasShown

    internal fun isProgressFooterVisible(): Boolean =
        ::footerView.isInitialized && footerView.visibility == View.VISIBLE

    internal fun setShowPageProgressForTest(enabled: Boolean) {
        applyReaderPreferences(readerPreferences.copy(showPageProgress = enabled))
    }

    internal fun showReaderOptionsForTest() = showOptions()

    internal fun hideReaderOptionsForTest() = hideOptions()

    internal fun readerContentBottomPaddingForTest(): Int = readerContainer.paddingBottom

    internal fun isTwoPageSpreadActive(): Boolean = twoPageSpreadActive

    /** Applies reader options the same way the options sheet does; for instrumented tests. */
    internal fun applyReaderPreferencesForTest(change: (LibraryReaderPreferences) -> LibraryReaderPreferences) =
        applyReaderPreferences(change(readerPreferences))

    internal suspend fun evaluateReaderJavascript(script: String): String? =
        navigator?.evaluateJavascript(script)

    private fun updateResult(reason: ReaderCompletionReason? = null) {
        val preserved = passiveRecoveryGuard.positionToPreserve
        val currentLocatorJson = (navigator?.currentLocator?.value?.takeIf { rendererHealthConfirmed }
            ?: restoredLocator)
            ?.toJSON()?.toString()
        val progress = epubProgressResultDuringRecovery(
            preservedPosition = preserved,
            currentChapterIndex = currentChapter,
            currentPageIndex = currentPage,
            currentPageCount = currentPageCount,
            currentPercent = currentPercent,
            currentLocatorJson = currentLocatorJson
        )
        val data = Intent().apply {
            reason?.let { putExtra(EXTRA_READER_COMPLETION_REASON, it.name) }
            if (!isPreview) {
                putExtra(EXTRA_RESULT_CHAPTER, progress.chapterIndex)
                putExtra(EXTRA_RESULT_PAGE, progress.pageIndex)
                putExtra(EXTRA_RESULT_PAGE_COUNT, progress.pageCount)
                putExtra(EXTRA_RESULT_PERCENT, progress.percent ?: Float.NaN)
                progress.locatorJson?.let { putExtra(EXTRA_RESULT_LOCATOR_JSON, it) }
            }
        }
        setResult(Activity.RESULT_OK, data)
    }

    private fun finishReader() {
        if (finishReaderJob != null) return
        closePlaybackForReaderExit()
        endReadingSession()
        updateResult(ReaderCompletionReason.USER_CLOSED)
        finishReaderJob = lifecycleScope.launch {
            try {
                flushLatestReaderPosition()
                finish()
            } catch (_: Exception) {
                finishReaderJob = null
                Toast.makeText(
                    this@ReadiumEpubReaderActivity,
                    getString(R.string.epub_progress_save_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun closePlaybackForReaderExit() {
        if (readerExitPlaybackClosed) return
        readerExitPlaybackClosed = true
        closeEpubReaderPlayback(
            closeMediaOverlayPlayback = ::closeMediaOverlayPlayback,
            closeTtsPlayback = ::closeTtsPlayback
        )
    }

    private fun endReadingSession() {
        if (!readingSessionEnded && ::readingSessionReporter.isInitialized) {
            readingSessionEnded = true
            readingSessionReporter.end(progressPercentForReporting())
        }
    }

    private fun showError(message: String) {
        progressView?.visibility = View.GONE
        val root = findViewById<FrameLayout>(android.R.id.content)
        root.addView(
            TextView(this).apply {
                text = "$message\n\n${this@ReadiumEpubReaderActivity.getString(R.string.epub_error_press_back)}"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
                setBackgroundColor(Color.BLACK)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /** Returns the system night-mode state, or null when the platform does not expose it. */
    private fun systemDarkMode(): Boolean? {
        val mode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return when (mode) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            android.content.res.Configuration.UI_MODE_NIGHT_NO -> false
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val preferences = appPreferencesStore.read()
        val policy = readerSystemBarsPolicy(
            hideNavigationBar = preferences.hideNavigationBarWhileReading,
            hideStatusBar = preferences.hideStatusBarWhileReading
        )
        val contentBackground = readerContentBackgroundColor(readerPreferences)
        val lightSystemBarIcons = isLightCustomReaderBackground(contentBackground)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (policy.showStatusBar) show(WindowInsetsCompat.Type.statusBars())
            else hide(WindowInsetsCompat.Type.statusBars())
            if (policy.showNavigationBar) show(WindowInsetsCompat.Type.navigationBars())
            else hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = lightSystemBarIcons
            isAppearanceLightNavigationBars = lightSystemBarIcons
        }
        window.statusBarColor = contentBackground
        window.navigationBarColor = contentBackground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Keep the system from drawing a translucent contrast scrim over the custom surface
            // when the system bars are transparent (Android 10+ / edge-to-edge on Android 15+).
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        // Android 15 draws the status-bar strip from the window background, not statusBarColor.
        window.setBackgroundDrawable(ColorDrawable(contentBackground))
    }

    private fun chapterTitle(tableOfContents: List<Link>, resource: Link): String? {
        fun find(links: List<Link>): String? {
            links.forEach { link ->
                if (link.url().isEquivalent(resource.url())) return link.title
                find(link.children)?.let { return it }
            }
            return null
        }
        return find(tableOfContents)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        if (stoppedSinceLastStart) {
            stoppedSinceLastStart = false
            syncToPositionSavedWhileStopped()
        }
        if (!rendererHealthConfirmed) {
            val fragment = navigator
            val openedPublication = publication
            if (fragment != null && openedPublication != null) {
                startRendererHealthCheck(fragment, openedPublication)
            }
        }
    }

    /**
     * Background read-aloud keeps saving its position while the reader is stopped. On return the
     * visual navigator still shows the old page, and Readium may re-report it after re-paginating
     * with slightly different details. Show the newer position instead, and ignore re-reports of
     * the old page until the navigator gets there (bounded, so real moves are never lost for long).
     */
    private fun syncToPositionSavedWhileStopped() {
        if (isPreview) return
        val last = lastRecordedVisualLocator ?: return
        val storedJson = legacyLocatorStore.readJson(readerKey) ?: return
        if (storedJson == last.toJSON().toString()) return
        val stored = legacyLocatorStore.read(readerKey) ?: return
        expectSemanticReaderMovement(stored)
        foregroundSyncTarget = stored
        foregroundSyncDeadline = SystemClock.elapsedRealtime() + FOREGROUND_SYNC_TIMEOUT_MILLIS
        foregroundSyncTimeoutJob?.cancel()
        foregroundSyncTimeoutJob = lifecycleScope.launch {
            delay(FOREGROUND_SYNC_TIMEOUT_MILLIS)
            expireForegroundSync()
        }
        if (!holdForegroundSyncNavigationForTest && navigator?.go(stored) != true) {
            cancelExpectedReaderMovement()
        }
    }

    /** Test barrier: catch-up starts but does not navigate until [releaseForegroundSyncNavigationForTest]. */
    internal var holdForegroundSyncNavigationForTest = false

    internal fun releaseForegroundSyncNavigationForTest() {
        holdForegroundSyncNavigationForTest = false
        foregroundSyncTarget?.let { target ->
            if (navigator?.go(target) != true) cancelExpectedReaderMovement()
        }
    }

    /**
     * The window has passed without a report reaching the newer position (for example read-aloud
     * only moved within the page shown, or the user swiped elsewhere). Any re-pagination replay is
     * over by now, so what the navigator shows is where the reader is: record it.
     */
    private fun expireForegroundSync() {
        val target = foregroundSyncTarget ?: return
        val current = navigator?.currentLocator?.value
        if (
            current != null &&
            recordSamePageForegroundSyncTarget(target, current)
        ) {
            endForegroundSync()
            return
        }
        endForegroundSync()
        // Paginated mode waits for PaginationListener so the locator and page geometry remain one
        // coherent tuple. Continuous mode can safely record the navigator's locator directly.
        current?.let(::recordVisualLocator)
    }

    private fun recordSamePageForegroundSyncTarget(target: Locator, current: Locator): Boolean {
        if (epubLayoutMode != ReaderLayoutMode.PAGINATED) return false
        if (!sameEpubRenderedLocation(target.toEpubCanonicalPosition(), current.toEpubCanonicalPosition())) {
            return false
        }
        val openedPublication = publication ?: return false
        val currentChapterIndex = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(current.href.removeFragment())
        }
        if (currentChapterIndex != currentChapter) return false
        val commitProgress = passiveRecoveryGuard.shouldCommitVisualUpdate(
            chapterIndex = currentChapter,
            pageIndex = currentPage,
            candidate = current.toEpubCanonicalPosition(),
            nowMillis = SystemClock.elapsedRealtime()
        )
        if (!commitProgress) return false
        val applied = applyAcceptedVisualLocator(current, commitProgress = true)
        if (applied) confirmProgressDisplay()
        return applied
    }

    private fun endForegroundSync() {
        foregroundSyncTarget = null
        foregroundSyncTimeoutJob?.cancel()
        foregroundSyncTimeoutJob = null
    }

    internal fun isForegroundSyncPendingForTest(): Boolean = foregroundSyncTarget != null

    /** Feeds a visual-navigator position report through the recording rules; for instrumented tests. */
    internal fun recordVisualLocatorForTest(locator: Locator) {
        rendererHealthConfirmed = true
        recordVisualLocator(locator)
    }

    internal fun applyEpubPaginationForTest(
        pageIndex: Int,
        pageCount: Int,
        locator: Locator,
        authoritativePageMove: Boolean = false
    ): Boolean {
        rendererHealthConfirmed = true
        val openedPublication = publication ?: return false
        if (authoritativePageMove) expectPageOrChapterReaderMovement()
        return applyEpubPagination(
            openedPublication,
            PendingEpubPagination(pageIndex, pageCount, locator)
        )
    }

    internal fun currentPageForTest(): Int = currentPage

    internal fun currentPageCountForTest(): Int = currentPageCount

    internal fun currentProgressStatusForTest(): EpubReaderProgressStatus = currentProgressStatus()

    internal fun canonicalPositionForTest(): EpubReaderPosition? = lastCanonicalPosition

    internal fun isPublicationReadyForTest(): Boolean = publication != null

    internal suspend fun flushPositionForTest(): Boolean = latestPositionWriter?.flush() ?: true

    internal fun progressResultForTest(): ReadiumEpubProgressResult {
        val locatorJson = (navigator?.currentLocator?.value?.takeIf { rendererHealthConfirmed }
            ?: restoredLocator)
            ?.toJSON()?.toString()
        return epubProgressResultDuringRecovery(
            preservedPosition = passiveRecoveryGuard.positionToPreserve,
            currentChapterIndex = currentChapter,
            currentPageIndex = currentPage,
            currentPageCount = currentPageCount,
            currentPercent = currentPercent,
            currentLocatorJson = locatorJson
        )
    }

    override fun onStop() {
        pageSwipeActive = false
        if (!isFinishing && !isDestroyed) beginPassiveRendererRecovery()
        rendererHealthJob?.cancel()
        rendererHealthJob = null
        if (!isChangingConfigurations) stoppedSinceLastStart = true
        // Background read-aloud owns the position again; the next start catches up afresh.
        endForegroundSync()
        if (
            shouldPauseEpubReadingSessionOnStop(
                isChangingConfigurations = isChangingConfigurations,
                ttsPlayingInForeground = hasActiveTtsSession() && ttsIsPlaying
            ) &&
            ::readingSessionReporter.isInitialized
        ) {
            readingSessionReporter.pause(progressPercentForReporting())
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::readingSessionReporter.isInitialized && publication != null && !isFinishing) {
            readingSessionReporter.resume(progressPercentForReporting())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val positionToPreserve = epubPositionToPreserveBeforeRendererConfirmation(
            passiveRecoveryPosition = passiveRecoveryGuard.positionToPreserve,
            validatedDisplayPosition = restoredDisplayPosition,
            pendingClaimedPosition = pendingRestoredDisplayPosition
        )
        val trustedRestoredLocator = positionToPreserve
            ?.locatorJson
            ?.let(::boundedReaderLocatorJson)
            ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }
        val trustedRestoredPosition = positionToPreserve?.takeIf { trustedRestoredLocator != null }
        val savedStateLocator = if (positionToPreserve != null) {
            trustedRestoredLocator
        } else {
            navigator?.currentLocator?.value?.takeIf { rendererHealthConfirmed } ?: restoredLocator
        }
        outState.putReaderLocator(
            savedStateLocator
        )
        outState.putLong(STATE_READER_POSITION_WRITE_MILLIS, lastPositionWriteMillis)
        outState.putInt(
            STATE_READER_POSITION_CHAPTER,
            trustedRestoredPosition?.chapterIndex ?: currentChapter
        )
        outState.putInt(
            STATE_READER_POSITION_PAGE,
            trustedRestoredPosition?.pageIndex ?: currentPage
        )
        outState.putInt(
            STATE_READER_POSITION_PAGE_COUNT,
            trustedRestoredPosition?.pageCount ?: currentPageCount
        )
        outState.putFloat(
            STATE_READER_POSITION_PERCENT,
            epubProgressPercentForSavedState(trustedRestoredPosition, currentPercent)
        )
        outState.putInt(
            STATE_READER_POSITION_OVERALL_NUMBER,
            if (trustedRestoredPosition != null) {
                trustedRestoredPosition.overallPositionNumber ?: 0
            } else {
                currentContentPosition?.number ?: 0
            }
        )
        outState.putInt(
            STATE_READER_POSITION_OVERALL_COUNT,
            if (trustedRestoredPosition != null) {
                trustedRestoredPosition.overallPositionCount ?: 0
            } else {
                currentContentPosition?.count ?: 0
            }
        )
        val trustedRenderedPageLedger = if (trustedRestoredPosition != null) {
            trustedRestoredPosition.renderedPageLayoutKey
                ?.let { layoutKey -> EpubRenderedPageLedger(layoutKey, trustedRestoredPosition.renderedPageCounts) }
        } else {
            renderedPageLedger
        }
        trustedRenderedPageLedger?.let { ledger ->
            outState.putString(STATE_READER_RENDERED_PAGE_LAYOUT_KEY, ledger.layoutKey)
            outState.putIntArray(STATE_READER_RENDERED_PAGE_COUNTS, ledger.pageCounts.toIntArray())
        }
        positionWriterLease?.let { lease ->
            outState.putLong(STATE_READER_POSITION_RESET_EPOCH, lease.resetEpoch)
        }
        outState.putEpubTtsLocator(
            ttsServiceBinder?.state?.value
                ?.takeIf { it.ownerToken == ttsOwnerToken && it.readerKey == readerKey }
                ?.locator
                ?: lastTtsLocator
        )
        outState.putBoolean(STATE_READER_CHROME_VISIBLE, areLightweightControlsVisible())
        outState.putBoolean(STATE_READER_OPTIONS_VISIBLE, areReaderOptionsVisible())
        outState.putBoolean(STATE_READER_TUTORIAL_SHOWN, tapZoneTutorialHasShown)
        outState.putBoolean(STATE_STALE_NARRATION_CLEANUP_PENDING, staleNarrationPositionCleanup.isPending)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            closePlaybackForReaderExit()
            endReadingSession()
        }
        ttsServiceStateJob?.cancel()
        ttsServiceStateJob = null
        ttsServiceConnection?.unbind()
        ttsServiceConnection = null
        ttsServiceBinder = null
        mediaOverlayPlayerListener?.let { listener ->
            mediaOverlayPlayback.player?.removeListener(listener)
        }
        mediaOverlayPlayerListener = null
        rendererHealthJob?.cancel()
        rendererHealthJob = null
        super.onDestroy()
        publication?.close()
        publication = null
        navigator = null
    }

    companion object {
        private const val EXTRA_FILE_PATH = "readium_epub_file_path"
        private const val EXTRA_TITLE = "readium_epub_title"
        private const val EXTRA_READER_KEY = "readium_epub_reader_key"
        private const val EXTRA_PUBLICATION_FINGERPRINT = "readium_epub_publication_fingerprint"
        private const val EXTRA_PERSISTENCE_SERVER = "readium_epub_persistence_server"
        private const val EXTRA_PERSISTENCE_PROFILE = "readium_epub_persistence_profile"
        private const val EXTRA_PERSISTENCE_PROVIDER = "readium_epub_persistence_provider"
        private const val EXTRA_PERSISTENCE_ACCOUNT = "readium_epub_persistence_account"
        internal const val EXTRA_TTS_OWNER_TOKEN = "readium_epub_tts_owner_token"
        private const val EXTRA_LIBRARY_ID = "readium_epub_library_id"
        private const val EXTRA_FILE_ID = "readium_epub_file_id"
        internal const val EXTRA_BOOK_ID = "readium_epub_book_id"
        internal const val EXTRA_IS_PREVIEW = "readium_epub_is_preview"
        private const val EXTRA_INITIAL_CHAPTER = "readium_epub_initial_chapter"
        private const val EXTRA_INITIAL_PAGE = "readium_epub_initial_page"
        private const val EXTRA_INITIAL_PAGE_COUNT = "readium_epub_initial_page_count"
        private const val EXTRA_INITIAL_PERCENT = "readium_epub_initial_percent"
        private const val EXTRA_SERVER_PROGRESS_PERCENT = "readium_epub_server_progress_percent"
        private const val EXTRA_LAST_REPORTED_PERCENT = "readium_epub_last_reported_percent"
        private const val EXTRA_INITIAL_CFI = "readium_epub_initial_cfi"
        private const val EXTRA_INITIAL_LOCATOR_JSON = "readium_epub_initial_locator_json"
        private const val EXTRA_INITIAL_ANNOTATION_TEXT = "readium_epub_initial_annotation_text"
        private const val EXTRA_INITIAL_ANNOTATION_CHAPTER = "readium_epub_initial_annotation_chapter"
        private const val EXTRA_INITIAL_ANNOTATION_ID = "readium_epub_initial_annotation_id"
        private const val EXTRA_INITIAL_ANNOTATION_COLOR = "readium_epub_initial_annotation_color"
        private const val EXTRA_INITIAL_ANNOTATION_STYLE = "readium_epub_initial_annotation_style"
        private const val EPUB_MEDIA_OVERLAY_DECORATION_GROUP = "epub-media-overlay"
        private const val EPUB_TTS_DECORATION_GROUP = "epub-tts"
        private const val TTS_PRIVACY_PREFERENCES = "epub_tts_privacy"
        private const val TTS_PRIVACY_ACKNOWLEDGED = "network_voice_notice_acknowledged"
        private const val EXTRA_RESULT_CHAPTER = "readium_epub_result_chapter"
        private const val EXTRA_RESULT_PAGE = "readium_epub_result_page"
        private const val EXTRA_RESULT_PAGE_COUNT = "readium_epub_result_page_count"
        private const val EXTRA_RESULT_PERCENT = "readium_epub_result_percent"
        private const val EXTRA_RESULT_LOCATOR_JSON = "readium_epub_result_locator_json"
        private const val NAVIGATOR_TAG = "readium_epub_navigator"
        private const val TAG = "ReadiumEpubReader"
        private const val FOLIATE_SELECTION_CFI_ASSET = "foliate-selection-cfi.js"
        private const val ACTION_COPY = 1000
        private const val ACTION_HIGHLIGHT = 1001
        private const val ACTION_HIGHLIGHT_WITH_NOTE = 1002
        private const val ACTION_PLAY_NARRATION = 1005
        private const val ACTION_TTS_FROM_SELECTION = 1006
        private const val ACTION_SHARE = 1003
        private const val ACTION_WEB_SEARCH = 1004
        private val SELECTION_ACTIONS = setOf(
            ACTION_COPY,
            ACTION_SHARE,
            ACTION_WEB_SEARCH,
            ACTION_PLAY_NARRATION,
            ACTION_TTS_FROM_SELECTION,
            ACTION_HIGHLIGHT,
            ACTION_HIGHLIGHT_WITH_NOTE
        )

        fun createIntent(
            context: Context,
            file: File,
            fileId: String? = null,
            bookId: String? = null,
            title: String,
            readerKey: String,
            publicationFingerprint: String? = null,
            persistenceScope: EpubReaderScope? = null,
            libraryId: String = "",
            launchMode: ReaderLaunchMode,
            initialChapter: Int,
            initialPage: Int,
            initialPageCount: Int = 1,
            initialPercent: Float? = null,
            serverProgressPercent: Float? = null,
            lastReportedProgressPercent: Float? = null,
            initialLocatorJson: String? = null,
            initialCfi: String? = null,
            initialAnnotationText: String? = null,
            initialAnnotationChapterIndex: Int? = null,
            initialAnnotationId: String? = null,
            initialAnnotationColor: String? = null,
            initialAnnotationStyle: String? = null
        ): Intent = Intent(
            context,
            if (shouldUseSoftwareEpubRendering(Build.MANUFACTURER, Build.MODEL)) {
                SoftwareRenderedEpubReaderActivity::class.java
            } else {
                ReadiumEpubReaderActivity::class.java
            }
        )
            .putExtra(EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(EXTRA_FILE_ID, fileId)
            .putExtra(EXTRA_BOOK_ID, bookId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_READER_KEY, readerKey)
            .apply {
                publicationFingerprint?.takeIf(String::isNotBlank)?.let {
                    putExtra(EXTRA_PUBLICATION_FINGERPRINT, it)
                }
            }
            .apply {
                persistenceScope?.let { scope ->
                    putExtra(EXTRA_PERSISTENCE_SERVER, scope.serverUrl)
                    putExtra(EXTRA_PERSISTENCE_PROFILE, scope.profileId)
                    putExtra(EXTRA_PERSISTENCE_PROVIDER, scope.providerId)
                    putExtra(EXTRA_PERSISTENCE_ACCOUNT, scope.accountScope)
                }
            }
            .putExtra(EXTRA_TTS_OWNER_TOKEN, UUID.randomUUID().toString())
            .putExtra(EXTRA_LIBRARY_ID, libraryId)
            .putExtra(EXTRA_IS_PREVIEW, launchMode == ReaderLaunchMode.PREVIEW)
            .putExtra(EXTRA_INITIAL_CHAPTER, initialChapter)
            .putExtra(EXTRA_INITIAL_PAGE, initialPage)
            .putExtra(EXTRA_INITIAL_PAGE_COUNT, initialPageCount)
            .apply {
                initialPercent?.let { putExtra(EXTRA_INITIAL_PERCENT, it) }
                serverProgressPercent?.let { putExtra(EXTRA_SERVER_PROGRESS_PERCENT, it) }
                lastReportedProgressPercent?.let { putExtra(EXTRA_LAST_REPORTED_PERCENT, it) }
                boundedReaderLocatorJson(initialLocatorJson)
                    ?.let { putExtra(EXTRA_INITIAL_LOCATOR_JSON, it) }
                initialCfi?.let { putExtra(EXTRA_INITIAL_CFI, it) }
                initialAnnotationText?.let { putExtra(EXTRA_INITIAL_ANNOTATION_TEXT, it) }
                initialAnnotationChapterIndex?.let { putExtra(EXTRA_INITIAL_ANNOTATION_CHAPTER, it) }
                initialAnnotationId?.let { putExtra(EXTRA_INITIAL_ANNOTATION_ID, it) }
                initialAnnotationColor?.let { putExtra(EXTRA_INITIAL_ANNOTATION_COLOR, it) }
                initialAnnotationStyle?.let { putExtra(EXTRA_INITIAL_ANNOTATION_STYLE, it) }
            }

        internal fun readProgressResult(data: Intent?): ReadiumEpubProgressResult? {
            if (data == null || !data.hasExtra(EXTRA_RESULT_CHAPTER)) return null
            return ReadiumEpubProgressResult(
                chapterIndex = data.getIntExtra(EXTRA_RESULT_CHAPTER, 0).coerceAtLeast(0),
                pageIndex = data.getIntExtra(EXTRA_RESULT_PAGE, 0).coerceAtLeast(0),
                pageCount = data.getIntExtra(EXTRA_RESULT_PAGE_COUNT, 1).coerceAtLeast(1),
                percent = data.getFloatExtra(EXTRA_RESULT_PERCENT, Float.NaN).takeUnless(Float::isNaN),
                locatorJson = boundedReaderLocatorJson(data.getStringExtra(EXTRA_RESULT_LOCATOR_JSON))
            )
        }

        private fun Intent.epubReaderPersistenceScope(): EpubReaderScope? {
            val serverUrl = getStringExtra(EXTRA_PERSISTENCE_SERVER)?.takeIf(String::isNotBlank)
                ?: return null
            val profileId = getStringExtra(EXTRA_PERSISTENCE_PROFILE)?.takeIf(String::isNotBlank)
                ?: return null
            val providerId = getStringExtra(EXTRA_PERSISTENCE_PROVIDER)?.takeIf(String::isNotBlank)
                ?: return null
            val accountScope = getStringExtra(EXTRA_PERSISTENCE_ACCOUNT)?.takeIf(String::isNotBlank)
                ?: return null
            return EpubReaderScope(serverUrl, profileId, providerId, accountScope)
        }
    }
}

/** Bigme B6 Color's WebView 113 can block hardware frame submission and poison later renderer starts. */
class SoftwareRenderedEpubReaderActivity : ReadiumEpubReaderActivity()

internal fun closeEpubReaderPlayback(
    closeMediaOverlayPlayback: () -> Unit,
    closeTtsPlayback: () -> Unit
) {
    closeMediaOverlayPlayback()
    closeTtsPlayback()
}

/**
 * A visual position is recorded only when the reader actually moved: a different resource or a
 * different progression within it. Readium re-reports the same place on resume, sometimes enriched
 * with `position`/`totalProgression` once positions are computed; that is not a move.
 */
internal const val FOREGROUND_SYNC_TIMEOUT_MILLIS = 5_000L

/**
 * While the reader catches up with a position saved in the background ([target]), whether a visual
 * report is Readium re-reporting the stale page the reader showed before ([lastRecorded]) rather
 * than a real position: it is in the stale page's resource and either read-aloud moved on to
 * another resource, or it is nearer the stale page than the target. Reports anywhere else, such as
 * the target or another chapter the user went to, are real.
 */
internal fun isStaleForegroundReReport(lastRecorded: Locator?, target: Locator, current: Locator): Boolean {
    fun Locator.resource() = href.removeFragment().toString()
    val last = lastRecorded ?: return false
    if (current.resource() != last.resource()) return false
    if (target.resource() != last.resource()) return true
    val currentProgression = current.locations.progression ?: return false
    val lastProgression = last.locations.progression ?: return false
    val targetProgression = target.locations.progression ?: return false
    // Re-pagination only nudges the stale page; a report nearer the target is the arrival.
    return kotlin.math.abs(currentProgression - lastProgression) <
        kotlin.math.abs(currentProgression - targetProgression)
}

internal fun shouldRecordVisualLocator(lastRecorded: Locator?, current: Locator): Boolean {
    val last = lastRecorded ?: return true
    if (last.href.removeFragment().toString() != current.href.removeFragment().toString()) return true
    val lastProgression = last.locations.progression
    val currentProgression = current.locations.progression
    if (lastProgression == null || currentProgression == null) return lastProgression != currentProgression
    return kotlin.math.abs(lastProgression - currentProgression) > 1e-6
}

/**
 * Position to restore when Android rebuilds the reader from saved instance state. The stores are
 * written on every position change, including by background read-aloud after the saved state was
 * captured, so a stored position is never older than the saved state and wins. The saved state is
 * only a fallback (for example in preview, which never writes the store). Without saved state this
 * is a fresh launch, which resolves its position from the intent and the store itself.
 */
internal fun epubRestoredLocator(savedState: Locator?, stored: Locator?): Locator? =
    savedState?.let { stored ?: it }

internal class ReadiumEpubLocatorStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "readium_epub_locations",
        Context.MODE_PRIVATE
    )

    fun read(readerKey: String): Locator? {
        if (readerKey.isBlank()) return null
        val json = preferences.getString(readerKey, null) ?: return null
        val bounded = boundedReaderLocatorJson(json) ?: return null
        return runCatching { Locator.fromJSON(JSONObject(bounded)) }.getOrNull()
    }

    fun readJson(readerKey: String): String? =
        readerKey.takeIf { it.isNotBlank() }?.let { preferences.getString(it, null) }

    fun save(readerKey: String, locator: Locator) {
        if (readerKey.isBlank()) return
        val json = boundedReaderLocatorJson(locator.toJSON().toString()) ?: return
        preferences.edit().putString(readerKey, json).apply()
    }

    fun remove(readerKey: String) {
        if (readerKey.isBlank()) return
        preferences.edit().remove(readerKey).apply()
    }

    fun removeForScope(scope: EpubReaderScope) {
        val prefix = epubReaderPersistenceScopePrefix(scope)
        val keys = preferences.all.keys.filter { key -> key.startsWith(prefix) }
        if (keys.isEmpty()) return
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        check(editor.commit()) { "Unable to clear scoped EPUB locators." }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}

internal class ReadiumEpubTtsPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "readium_epub_tts_locations",
        Context.MODE_PRIVATE
    )

    fun read(readerKey: String): Locator? {
        if (readerKey.isBlank()) return null
        val json = preferences.getString(readerKey, null)?.let(::boundedReaderLocatorJson) ?: return null
        return runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
    }

    fun save(readerKey: String, locator: Locator) {
        if (readerKey.isBlank()) return
        val json = boundedReaderLocatorJson(locator.toJSON().toString()) ?: return
        preferences.edit().putString(readerKey, json).apply()
    }

    fun remove(readerKey: String) {
        if (readerKey.isBlank()) return
        preferences.edit().remove(readerKey).apply()
    }

    fun removeForScope(scope: EpubReaderScope) {
        val prefix = epubReaderPersistenceScopePrefix(scope)
        val keys = preferences.all.keys.filter { key -> key.startsWith(prefix) }
        if (keys.isEmpty()) return
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        check(editor.commit()) { "Unable to clear scoped EPUB TTS positions." }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
