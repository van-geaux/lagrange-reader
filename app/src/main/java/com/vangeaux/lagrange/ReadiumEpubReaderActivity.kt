package com.vangeaux.lagrange

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.ReadingProgression as ReadiumReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal sealed interface ReadiumEpubOpenResult {
    data class Opened(val publication: Publication) : ReadiumEpubOpenResult
    data class Error(val message: String) : ReadiumEpubOpenResult
}

internal suspend fun openReadiumEpub(
    context: Context,
    file: File
): ReadiumEpubOpenResult = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        return@withContext ReadiumEpubOpenResult.Error("The EPUB file is unavailable.")
    }

    val appContext = context.applicationContext
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val assetResult = assetRetriever.retrieve(file, MediaType.EPUB)
    val asset = assetResult.getOrNull()
        ?: return@withContext ReadiumEpubOpenResult.Error("Readium could not read this EPUB file.")
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )
    val publicationResult = PublicationOpener(publicationParser).open(
        asset = asset,
        allowUserInteraction = false
    )
    val publication = publicationResult.getOrNull()
        ?: return@withContext ReadiumEpubOpenResult.Error("Readium could not open this EPUB publication.")
    if (!publication.conformsTo(Publication.Profile.EPUB)) {
        publication.close()
        return@withContext ReadiumEpubOpenResult.Error("Readium did not recognize this file as an EPUB publication.")
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

internal fun readiumEpubLineHeight(lineSpacing: Float): Double = lineSpacing
    .coerceIn(DEFAULT_EPUB_LINE_SPACING, MAX_EPUB_LINE_SPACING)
    .toDouble()

internal fun readiumEpubWordSpacing(wordSpacing: Float): Double = wordSpacing
    .coerceIn(DEFAULT_EPUB_WORD_SPACING, MAX_EPUB_WORD_SPACING)
    .toDouble()

@OptIn(ExperimentalReadiumApi::class)
internal fun readiumPreferences(
    theme: EpubReaderTheme,
    fontScale: Float,
    lineSpacing: Float = DEFAULT_EPUB_LINE_SPACING,
    readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
    layoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    fontFamily: EpubReaderFontFamily = EpubReaderFontFamily.PUBLISHER_DEFAULT,
    wordSpacing: Float = DEFAULT_EPUB_WORD_SPACING
): EpubPreferences = EpubPreferences(
    backgroundColor = ReadiumColor(theme.backgroundColor),
    textColor = ReadiumColor(cssHexColorInt(theme.foregroundCss)),
    theme = when (theme) {
        EpubReaderTheme.Light -> ReadiumTheme.LIGHT
        EpubReaderTheme.Sepia -> ReadiumTheme.SEPIA
        EpubReaderTheme.Dark -> ReadiumTheme.DARK
    },
    fontSize = fontScale.coerceIn(MIN_EPUB_FONT_SCALE, MAX_EPUB_FONT_SCALE).toDouble(),
    lineHeight = readiumEpubLineHeight(lineSpacing),
    wordSpacing = readiumEpubWordSpacing(wordSpacing),
    fontFamily = readiumFontFamily(fontFamily),
    readingProgression = readiumEpubReadingProgression(readingDirection),
    pageMargins = 0.0,
    columnCount = ColumnCount.ONE,
    scroll = layoutMode == ReaderLayoutMode.CONTINUOUS,
    // Readium only applies lineHeight and the other user CSS overrides when
    // publisher styles are disabled.
    publisherStyles = false
)

internal fun cssHexColorInt(value: String): Int {
    val hex = value.removePrefix("#")
    require(hex.length == 6) { "Expected a six-digit CSS color" }
    return (0xFF000000L or hex.toLong(16)).toInt()
}

internal fun readiumOverallPercent(
    totalProgression: Double?,
    resourceProgression: Double?,
    chapterIndex: Int,
    chapterCount: Int
): Float {
    totalProgression?.let { total ->
        return (total.coerceIn(0.0, 1.0) * 100.0).toFloat()
    }
    val safeChapterCount = chapterCount.coerceAtLeast(1)
    val safeChapterIndex = chapterIndex.coerceIn(0, safeChapterCount - 1)
    val safeResourceProgression = resourceProgression?.coerceIn(0.0, 1.0) ?: 0.0
    return (((safeChapterIndex + safeResourceProgression) / safeChapterCount) * 100.0).toFloat()
}

internal fun selectReadiumPositionIndex(
    targetProgression: Double?,
    totalProgressions: List<Double?>
): Int? {
    val target = targetProgression?.coerceIn(0.0, 1.0) ?: return null
    val usable = totalProgressions.mapIndexedNotNull { index, progression ->
        progression?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.let { index to it }
    }
    if (usable.isEmpty()) return null
    return usable
        .filter { (_, progression) -> progression <= target }
        .maxByOrNull { (_, progression) -> progression }
        ?.first
        ?: usable.minByOrNull { (_, progression) -> abs(progression - target) }?.first
}

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
    val percent: Float?
)

internal const val DEFAULT_HIGHLIGHT_COLOR = "yellow"
internal const val DEFAULT_HIGHLIGHT_STYLE = "highlight"
internal const val HIGHLIGHT_DECORATION_GROUP = "bookorbit-highlights"

internal fun readiumEpubImageColorOverrideScript(): String = """
    (function() {
      const styleId = 'bookorbit-readium-epub-image-color-preservation';
      let style = document.getElementById(styleId);
      if (!style) {
        style = document.createElement('style');
        style.id = styleId;
        document.head.appendChild(style);
      }
      style.textContent = `
        :root[style*="readium-night-on"] [epub\\:type~="titlepage"] img:only-child,
        :root[style*="readium-night-on"] [epub|type~="titlepage"] img:only-child,
        :root[style*="readium-night-on"] img[class*="gaiji"] {
          filter: none !important;
          -webkit-filter: none !important;
        }
      `;
    })();
""".trimIndent()

private const val EPUB_IMAGE_GESTURE_ASSET = "epub-image-gesture.js"
private const val EPUB_IMAGE_GESTURE_PROBE_SCRIPT =
    "(function(){const k='__bookorbitImageGesture';const v=window[k]||null;window[k]=null;return v;})()"

internal fun shouldOpenEpubImageViewer(gesture: String): Boolean = gesture == "long_press"

internal data class HighlightChoice(
    val label: String,
    val color: String,
    val style: String,
    val previewColor: Int
)

internal fun epubHighlightChoices() = listOf(
    HighlightChoice("Yellow highlight", "yellow", "highlight", 0xFFFFEB3B.toInt()),
    HighlightChoice("Green highlight", "green", "highlight", 0xFF4CAF50.toInt()),
    HighlightChoice("Blue highlight", "blue", "highlight", 0xFF2196F3.toInt()),
    HighlightChoice("Pink highlight", "pink", "highlight", 0xFFE91E63.toInt()),
    HighlightChoice("Yellow underline", "yellow", "underline", 0xFFFFEB3B.toInt())
)

internal data class EpubSelectionAction(val label: String)

internal data class CapturedEpubSelection(
    val selection: Selection,
    val cfi: String?
)

internal fun epubSelectionActions() = listOf(
    EpubSelectionAction("Copy"),
    EpubSelectionAction("Share"),
    EpubSelectionAction("Web search"),
    EpubSelectionAction("Highlight"),
    EpubSelectionAction("Highlight + Note")
)

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

internal fun resolveEpubImageHref(baseHref: String, imageHref: String): String? =
    runCatching { java.net.URI(baseHref).resolve(imageHref).toString() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

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
class ReadiumEpubReaderActivity : FragmentActivity() {
    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
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

    private lateinit var readerKey: String
    private lateinit var libraryId: String
    private lateinit var displayTitle: String
    private lateinit var readingSessionReporter: ReadingSessionReporter
    private var isPreview: Boolean = false
    private var bookId: String? = null
    private val annotationRepository by lazy { BookOrbitRepository(applicationContext) }
    private val highlightAnnotations = mutableMapOf<String, BookAnnotation>()
    private val highlightLocators = mutableMapOf<String, Locator>()
    private var selectedTheme by mutableStateOf(EpubReaderTheme.Sepia)
    private var selectedFontFamily by mutableStateOf(EpubReaderFontFamily.PUBLISHER_DEFAULT)
    private var padding by mutableStateOf(EpubPaddingPercentages())
    private var fontScale by mutableStateOf(1f)
    private var readingDirection by mutableStateOf(LibraryReadingDirection.LEFT_TO_RIGHT)
    private var epubLayoutMode by mutableStateOf(ReaderLayoutMode.PAGINATED)
    private var readerPreferences by mutableStateOf(LibraryReaderPreferences())
    private var chapterTitles by mutableStateOf(emptyList<String>())
    private var currentChapter by mutableStateOf(0)
    private var currentPage by mutableStateOf(0)
    private var currentPageCount by mutableStateOf(1)
    private var currentResourceProgression by mutableStateOf(0f)
    private var currentPercent by mutableStateOf(0f)
    private var currentBookPage by mutableStateOf<Int?>(null)
    private var bookPositionCount by mutableStateOf<Int?>(null)
    private var bookPositions: List<Locator> = emptyList()
    private var previewAnnotationLocator: Locator? = null
    private var pendingAnnotationReanchor: Locator? = null
    private var tapZoneTutorialHasShown = false
    private var tapZoneTutorialHideJob: Job? = null
    private var restoredLocator: Locator? = null
    private var readingSessionEnded = false
    private var epubImageGestureJob: Job? = null
    private var epubImageViewer by mutableStateOf<Pair<Int, Bitmap>?>(null)

    private val themeStore by lazy { EpubReaderThemeStore(this) }
    private val paddingStore by lazy { EpubReaderPaddingStore(this) }
    private val appPreferencesStore by lazy { AppPreferencesStore(this) }
    private val locatorStore by lazy { ReadiumEpubLocatorStore(this) }
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
                is CustomFontImportResult.Rejected -> showError(result.reason)
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
        if (action == null || !volumeButtonNavigationEnabled(
                readerEnabled = readerPreferences.volumeButtonPageNavigation,
                audiobookSessionActive = audioPlaybackController.hasActiveAudiobookSession()
            )
        ) {
            return super.dispatchKeyEvent(event)
        }
        if (!canNavigateWithVolumeButtons()) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) return true
        when (action) {
            VolumeButtonNavigationAction.PREVIOUS -> navigator?.goBackward(true)
            VolumeButtonNavigationAction.NEXT -> navigator?.goForward(true)
        }
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
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        restoredLocator = savedInstanceState?.readReaderLocator()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppPreferencesStore(this).read().let { preferences ->
            requestedOrientation = requestedOrientationForLock(
                enabled = preferences.lockOrientation,
                lockedOrientation = preferences.lockedOrientation
            )
        }

        displayTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        readerKey = intent.getStringExtra(EXTRA_READER_KEY).orEmpty()
        libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID).orEmpty()
        isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
        bookId = epubAnnotationBookId(intent)
        readingSessionReporter = ViewModelProvider(this)[ReadingSessionReporterViewModel::class.java].reporter(
            context = this,
            fileId = intent.getStringExtra(EXTRA_FILE_ID),
            enabled = !isPreview
        )
        val appPreferences = appPreferencesStore.read()
        readerPreferences = appPreferences.libraryReaderPreferences[libraryId]
            ?: LibraryReaderPreferences(
                theme = themeStore.read(),
                padding = paddingStore.read(readerKey)
            )
        selectedTheme = readerPreferences.theme
        selectedFontFamily = readerPreferences.fontFamily
        padding = readerPreferences.padding
        fontScale = readerPreferences.fontScale
        readingDirection = readerPreferences.readingDirection
        epubLayoutMode = readerPreferences.epubLayoutMode
        appPreferencesStore.save(
            appPreferences.withReaderPreferences(libraryId, readerPreferences)
        )
        configureSystemBars()
        createReaderViews()
        restoreReaderUi(savedInstanceState)
        discardRestoredNavigatorIfNeeded(savedInstanceState)
        installBackHandler()

        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null) {
            showError("The EPUB reader file is unavailable.")
            return
        }
        lifecycleScope.launch {
            val readerFile = withContext(Dispatchers.IO) {
                val custom = readerPreferences.customFont
                if (readerPreferences.fontFamily == EpubReaderFontFamily.CUSTOM && custom != null) {
                    val fontFile = customFontStore.fontFile(custom)
                    if (fontFile.isFile) prepareEpubWithCustomFont(this@ReadiumEpubReaderActivity, file, custom, fontFile) else file
                } else file
            }
            when (val result = openReadiumEpub(this@ReadiumEpubReaderActivity, readerFile)) {
                is ReadiumEpubOpenResult.Error -> showError(result.message)
                is ReadiumEpubOpenResult.Opened -> {
                    bookPositions = withContext(Dispatchers.IO) { result.publication.positions() }
                    bookPositionCount = bookPositions.size.takeIf { it > 0 }
                    showPublication(result.publication, restoredLocator)
                }
            }
        }
    }

    private fun createReaderViews() {
        rootView = FrameLayout(this).apply {
            setBackgroundColor(selectedTheme.backgroundColor)
        }
        readerViewport = FrameLayout(this).apply {
            setBackgroundColor(selectedTheme.backgroundColor)
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
            setBackgroundColor(selectedTheme.backgroundColor)
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
            setContent { BookOrbitTheme { ViewOnlyModeBanner(onConfirmReadMode = ::enableReadMode) } }
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
            setContent {
                BookOrbitTheme {
                    EpubReaderProgressFooter(
                        status = currentProgressStatus(),
                        theme = selectedTheme,
                        modifier = Modifier.fillMaxSize()
                    )
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
                BookOrbitTheme {
                    ReaderLightweightChrome(
                        title = if (isPreview) "Preview · $displayTitle" else displayTitle,
                        theme = selectedTheme,
                        positionKind = "Page",
                        positionTitles = List(currentPageCount.coerceAtLeast(1)) { index ->
                            "Page ${index + 1}"
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
                        listPositionKind = "Chapter",
                        listPositionTitles = chapterTitles,
                        currentListPosition = currentChapter,
                        onListPositionSelected = ::goToChapter,
                        secondaryPositionKind = "Chapter",
                        secondaryCurrentPosition = currentChapter,
                        secondaryPositionCount = chapterTitles.size,
                        onSecondaryPositionSelected = ::goToChapter,
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
                BookOrbitTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EpubReaderDismissScrim(onDismiss = ::hideOptions)
                        EpubReaderOptionsBottomSheet(
                            title = if (isPreview) "Preview · $displayTitle" else displayTitle,
                            status = "Chapter ${currentChapter + 1}/${chapterTitles.size.coerceAtLeast(1)} · " +
                                "Page ${currentPage + 1}/${currentPageCount.coerceAtLeast(1)}",
                            preferences = readerPreferences,
                            onContinueReading = ::hideOptions,
                            onCloseBook = ::finishReader,
                            onPreferencesChange = ::applyReaderPreferences,
                            onCustomFontRequest = ::chooseCustomFont,
                            onCustomFontRemove = ::removeCustomFont,
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
                BookOrbitTheme {
                    ReaderTapZoneTutorial(
                        onDismiss = ::hideTapZoneTutorial,
                        readingDirection = readingDirection,
                        tapZoneLayout = readerPreferences.tapZoneLayout,
                        tapZoneInvertMode = readerPreferences.tapZoneInvertMode,
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
                BookOrbitTheme {
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
        addReadiumAudioPlayerOverlay(rootView, readerViewport)
        setContentView(rootView)
        readerViewport.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyReaderPadding() }
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

    private fun showPublication(openedPublication: Publication, requestedLocator: Locator? = null) {
        if (isFinishing || isDestroyed) {
            openedPublication.close()
            return
        }
        if (openedPublication.readingOrder.isEmpty()) {
            openedPublication.close()
            showError("This EPUB has no readable content.")
            return
        }
        publication = openedPublication
        readingSessionReporter.start(
            intent.getFloatExtra(EXTRA_INITIAL_PERCENT, Float.NaN).takeUnless(Float::isNaN)
        )
        chapterTitles = openedPublication.readingOrder.mapIndexed { index, link ->
            chapterTitle(openedPublication.tableOfContents, link) ?: link.title ?: "Chapter ${index + 1}"
        }
        val initialLocator = requestedLocator
            ?.takeIf { saved ->
                openedPublication.readingOrder.any { link ->
                    link.url().isEquivalent(saved.href.removeFragment())
                }
            }
            ?: initialLocator(openedPublication)
        val paginationListener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                currentPage = pageIndex.coerceAtLeast(0)
                currentPageCount = totalPages.coerceAtLeast(1)
                updateLocation(locator)
            }
        }
        val navigatorFactory = EpubNavigatorFactory(openedPublication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = readiumPreferences(
                selectedTheme,
                fontScale,
                readerPreferences.lineSpacing,
                readingDirection,
                epubLayoutMode,
                selectedFontFamily,
                readerPreferences.wordSpacing
            ),
            paginationListener = paginationListener,
            configuration = EpubNavigatorFragment.Configuration(
                shouldApplyInsetsPadding = false,
                selectionActionModeCallback = if (epubAnnotationFeaturesEnabled(intent)) {
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
                onMenu = ::toggleChrome
            )
        )
        navigator = fragment
        if (epubAnnotationFeaturesEnabled(intent)) {
            fragment.addDecorationListener(HIGHLIGHT_DECORATION_GROUP, highlightDecorationListener)
            loadHighlights(openedPublication)
        } else if (isPreview) {
            applyPreviewAnnotationDecoration(fragment, openedPublication)
        }
        startEpubImageGestureBridge(fragment)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fragment.currentLocator.collect { locator ->
                    runCatching {
                        fragment.evaluateJavascript(readiumEpubImageColorOverrideScript())
                    }
                    updateLocation(locator)
                    pendingAnnotationReanchor?.let { target ->
                        pendingAnnotationReanchor = null
                        if (locator != target) fragment.go(target)
                    }
                    if (highlightAnnotations.isNotEmpty()) {
                        refreshHighlightDecorations()
                    }
                }
            }
        }
        progressView?.visibility = View.GONE
        applyReaderPadding()
        if (!tapZoneTutorialHasShown) showTapZoneTutorial()
    }

    private fun startEpubImageGestureBridge(fragment: EpubNavigatorFragment) {
        epubImageGestureJob?.cancel()
        val installScript = runCatching {
            assets.open(EPUB_IMAGE_GESTURE_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        epubImageGestureJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    runCatching { fragment.evaluateJavascript(installScript) }
                    val event = runCatching {
                        decodeJavascriptString(
                            fragment.evaluateJavascript(EPUB_IMAGE_GESTURE_PROBE_SCRIPT)
                        )
                    }.getOrNull()
                    if (event != null) handleEpubImageGesture(fragment, event)
                    delay(250)
                }
            }
        }
    }

    private fun handleEpubImageGesture(fragment: EpubNavigatorFragment, event: String) {
        val payload = runCatching { JSONObject(event) }.getOrNull() ?: return
        if (!shouldOpenEpubImageViewer(payload.optString("gesture"))) return
        val rawHref = payload.optString("href").ifBlank { payload.optString("src") }
        val baseHref = payload.optString("base")
            .ifBlank { fragment.currentLocator.value.href.toString() }
        val imageHref = resolveEpubImageHref(baseHref, rawHref) ?: return
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
                    "This EPUB image could not be opened.",
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

    private fun highlightActionModeCallback(): ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.add(0, ACTION_COPY, 0, "Copy")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu?.add(0, ACTION_SHARE, 1, "Share")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu?.add(0, ACTION_WEB_SEARCH, 2, "Web search")
            menu?.add(0, ACTION_HIGHLIGHT, 3, "Highlight")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu?.add(0, ACTION_HIGHLIGHT_WITH_NOTE, 4, "Highlight + Note")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val action = item?.itemId ?: return false
            if (action !in SELECTION_ACTIONS) return false
            captureCurrentSelection(
                mode = mode,
                requireCfi = action == ACTION_HIGHLIGHT || action == ACTION_HIGHLIGHT_WITH_NOTE
            ) { selection ->
                when (action) {
                    ACTION_COPY -> copySelection(selection)
                    ACTION_SHARE -> shareSelection(selection)
                    ACTION_WEB_SEARCH -> searchSelection(selection)
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
                        CapturedEpubSelection(selection, cfi)
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
                    if (requireCfi) {
                        "This text selection cannot be highlighted. Please try selecting it again."
                    } else {
                        "The selected text is no longer available. Please select it again."
                    },
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

    private fun selectedText(selection: Selection): String? =
        selection.locator.text.highlight?.takeIf { it.isNotBlank() }

    private fun copySelection(captured: CapturedEpubSelection) {
        val selection = captured.selection
        val text = selectedText(selection) ?: return
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Selected text", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
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
                "Share selected text"
            )
        )
    }

    private fun searchSelection(captured: CapturedEpubSelection) {
        val selection = captured.selection
        val text = selectedText(selection) ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))
        }.onFailure {
            Toast.makeText(this, "No web search app is available.", Toast.LENGTH_LONG).show()
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
                view.text = choice.label
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
            .setTitle("Highlight style")
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
            .setNegativeButton("Cancel", null)
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
                    "This text selection cannot be highlighted. Please try selecting it again.",
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
                    "The highlight could not be saved.",
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
            .setTitle(if (annotation.note.isNullOrBlank()) "Highlight" else "Highlight note")
            .setItems(arrayOf("Edit note", "Delete highlight", "Cancel")) { _, which ->
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
            .setTitle("Note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                onSave(input.text?.toString()?.trim()?.takeIf { it.isNotBlank() })
            }
            .setNegativeButton("Cancel", null)
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
                runCatching { Locator.fromJSON(json) }.getOrNull()?.let { return it }
            }
        }
        if (!isPreview) {
            locatorStore.read(readerKey)?.let { stored ->
                if (openedPublication.readingOrder.any { link ->
                        link.url().isEquivalent(stored.href.removeFragment())
                    }
                ) {
                    return stored
                }
            }
        }
        val chapterCount = openedPublication.readingOrder.size.coerceAtLeast(1)
        val initialPercent = intent.getFloatExtra(EXTRA_INITIAL_PERCENT, Float.NaN)
            .takeUnless(Float::isNaN)
        val requestedChapter = intent.getIntExtra(EXTRA_INITIAL_CHAPTER, 0)
        val chapterIndex = when {
            isPreview -> 0
            requestedChapter > 0 -> requestedChapter.coerceIn(0, chapterCount - 1)
            else -> percentToChapterIndex(initialPercent, chapterCount)
        }
        val initialPage = intent.getIntExtra(EXTRA_INITIAL_PAGE, 0).coerceAtLeast(0)
        val initialPageCount = intent.getIntExtra(EXTRA_INITIAL_PAGE_COUNT, 1).coerceAtLeast(1)
        val totalProgression = if (isPreview) 0.0 else initialPercent
            ?.div(100f)
            ?.coerceIn(0f, 1f)
            ?.toDouble()
        if (!isPreview) {
            val positionIndex = selectReadiumPositionIndex(
                targetProgression = totalProgression,
                totalProgressions = bookPositions.map { it.locations.totalProgression }
            )
            bookPositions.getOrNull(positionIndex ?: -1)?.let { return it }
        }
        val resourceProgression = when {
            isPreview -> 0.0
            initialPageCount > 1 -> initialPage.toDouble() / (initialPageCount - 1).toDouble()
            totalProgression != null -> (totalProgression * chapterCount - chapterIndex).coerceIn(0.0, 1.0)
            else -> 0.0
        }
        return requireNotNull(openedPublication.locatorFromLink(openedPublication.readingOrder[chapterIndex]))
            .copyWithLocations(
                progression = resourceProgression.coerceIn(0.0, 1.0),
                totalProgression = totalProgression
            )
    }

    private fun updateLocation(locator: Locator) {
        val openedPublication = publication ?: return
        val chapterIndex = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(locator.href.removeFragment())
        }.takeIf { it >= 0 } ?: currentChapter
        currentChapter = chapterIndex.coerceIn(0, openedPublication.readingOrder.lastIndex)
        currentPercent = readiumOverallPercent(
            totalProgression = locator.locations.totalProgression,
            resourceProgression = locator.locations.progression,
            chapterIndex = currentChapter,
            chapterCount = openedPublication.readingOrder.size
        )
        currentResourceProgression = normalizedReaderProgression(locator.locations.progression?.toFloat())
        currentBookPage = locator.locations.position
        if (!isPreview) locatorStore.save(readerKey, locator)
        readingSessionReporter.activity(currentPercent)
        updateResult()
    }

    private fun currentProgressStatus(): EpubReaderProgressStatus = EpubReaderProgressStatus(
        completionPercent = currentPercent.coerceIn(0f, 100f),
        chapterNumber = currentChapter + 1,
        chapterCount = chapterTitles.size.coerceAtLeast(1),
        chapterPageNumber = currentPage + 1,
        chapterPageCount = currentPageCount.coerceAtLeast(1),
        bookPageNumber = currentBookPage,
        bookPageCount = bookPositionCount
    )

    private fun goToChapter(index: Int) {
        val link = publication?.readingOrder?.getOrNull(index) ?: return
        navigator?.go(link)
    }

    private fun goToPage(index: Int) {
        val activeNavigator = navigator ?: return
        val safePageCount = currentPageCount.coerceAtLeast(1)
        val target = index.coerceIn(0, safePageCount - 1)
        val progression = if (safePageCount <= 1) 0.0 else {
            target.toDouble() / (safePageCount - 1).toDouble()
        }
        activeNavigator.go(
            activeNavigator.currentLocator.value.copyWithLocations(progression = progression)
        )
    }

    private fun goToProgression(progression: Float) {
        val activeNavigator = navigator ?: return
        activeNavigator.go(
            activeNavigator.currentLocator.value.copyWithLocations(
                progression = normalizedReaderProgression(progression).toDouble()
            )
        )
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
        val tapZoneChanged = readerTapZonePreferencesChanged(readerPreferences, normalized)
        readerPreferences = normalized
        selectedTheme = normalized.theme
        selectedFontFamily = normalized.fontFamily
        padding = normalized.padding
        fontScale = normalized.fontScale
        readingDirection = normalized.readingDirection
        epubLayoutMode = normalized.epubLayoutMode
        themeStore.save(normalized.theme)
        rootView.setBackgroundColor(normalized.theme.backgroundColor)
        readerViewport.setBackgroundColor(normalized.theme.backgroundColor)
        readerContainer.setBackgroundColor(normalized.theme.backgroundColor)
        applyReaderPadding()
        navigator?.submitPreferences(
            readiumPreferences(
                normalized.theme,
                normalized.fontScale,
                normalized.lineSpacing,
                normalized.readingDirection,
                normalized.epubLayoutMode,
                normalized.fontFamily,
                normalized.wordSpacing
            )
        )
        val current = appPreferencesStore.read()
        appPreferencesStore.save(
            current.withReaderPreferences(libraryId, normalized)
        )
        configureSystemBars()
        if (tapZoneChanged) showTapZoneTutorial()
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
        readerContainer.setPadding(
            horizontal(padding.left),
            effectiveReaderTopSpace(normalTopPadding, occupiedBannerBottom, isPreview),
            horizontal(padding.right),
            vertical(padding.bottom) + dpToPx(EPUB_READER_PROGRESS_FOOTER_HEIGHT_DP)
        )
    }

    private fun toggleChrome() {
        if (chromeView.visibility == View.VISIBLE) hideChrome() else showChrome()
    }

    private fun showChrome() {
        hideOptions()
        chromeView.visibility = View.VISIBLE
    }

    private fun hideChrome() {
        chromeView.visibility = View.GONE
    }

    private fun showOptions() {
        hideChrome()
        optionsView.visibility = View.VISIBLE
    }

    private fun hideOptions() {
        optionsView.visibility = View.GONE
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
                    if (locator != target) navigator?.go(target)
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

    private fun updateResult(reason: ReaderCompletionReason? = null) {
        val data = Intent().apply {
            reason?.let { putExtra(EXTRA_READER_COMPLETION_REASON, it.name) }
            if (!isPreview) {
                putExtra(EXTRA_RESULT_CHAPTER, currentChapter)
                putExtra(EXTRA_RESULT_PAGE, currentPage)
                putExtra(EXTRA_RESULT_PAGE_COUNT, currentPageCount)
                putExtra(EXTRA_RESULT_PERCENT, currentPercent)
            }
        }
        setResult(Activity.RESULT_OK, data)
    }

    private fun finishReader() {
        endReadingSession()
        updateResult(ReaderCompletionReason.USER_CLOSED)
        finish()
    }

    private fun endReadingSession() {
        if (!readingSessionEnded && ::readingSessionReporter.isInitialized) {
            readingSessionEnded = true
            readingSessionReporter.end(currentPercent)
        }
    }

    private fun showError(message: String) {
        progressView?.visibility = View.GONE
        val root = findViewById<FrameLayout>(android.R.id.content)
        root.addView(
            TextView(this).apply {
                text = "$message\n\nPress Back to return."
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

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = selectedTheme.usesDarkStatusBarIcons()
        }
        window.statusBarColor = selectedTheme.backgroundColor
        window.navigationBarColor = Color.BLACK
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

    override fun onStop() {
        if (
            shouldPauseReadingSession(isChangingConfigurations) &&
            ::readingSessionReporter.isInitialized
        ) {
            readingSessionReporter.pause(currentPercent)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::readingSessionReporter.isInitialized && publication != null && !isFinishing) {
            readingSessionReporter.resume(currentPercent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putReaderLocator(navigator?.currentLocator?.value ?: restoredLocator)
        outState.putBoolean(STATE_READER_CHROME_VISIBLE, areLightweightControlsVisible())
        outState.putBoolean(STATE_READER_OPTIONS_VISIBLE, areReaderOptionsVisible())
        outState.putBoolean(STATE_READER_TUTORIAL_SHOWN, tapZoneTutorialHasShown)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) endReadingSession()
        super.onDestroy()
        publication?.close()
        publication = null
        navigator = null
    }

    companion object {
        private const val EXTRA_FILE_PATH = "readium_epub_file_path"
        private const val EXTRA_TITLE = "readium_epub_title"
        private const val EXTRA_READER_KEY = "readium_epub_reader_key"
        private const val EXTRA_LIBRARY_ID = "readium_epub_library_id"
        private const val EXTRA_FILE_ID = "readium_epub_file_id"
        internal const val EXTRA_BOOK_ID = "readium_epub_book_id"
        internal const val EXTRA_IS_PREVIEW = "readium_epub_is_preview"
        private const val EXTRA_INITIAL_CHAPTER = "readium_epub_initial_chapter"
        private const val EXTRA_INITIAL_PAGE = "readium_epub_initial_page"
        private const val EXTRA_INITIAL_PAGE_COUNT = "readium_epub_initial_page_count"
        private const val EXTRA_INITIAL_PERCENT = "readium_epub_initial_percent"
        private const val EXTRA_INITIAL_CFI = "readium_epub_initial_cfi"
        private const val EXTRA_INITIAL_ANNOTATION_TEXT = "readium_epub_initial_annotation_text"
        private const val EXTRA_INITIAL_ANNOTATION_CHAPTER = "readium_epub_initial_annotation_chapter"
        private const val EXTRA_INITIAL_ANNOTATION_ID = "readium_epub_initial_annotation_id"
        private const val EXTRA_INITIAL_ANNOTATION_COLOR = "readium_epub_initial_annotation_color"
        private const val EXTRA_INITIAL_ANNOTATION_STYLE = "readium_epub_initial_annotation_style"
        private const val EXTRA_RESULT_CHAPTER = "readium_epub_result_chapter"
        private const val EXTRA_RESULT_PAGE = "readium_epub_result_page"
        private const val EXTRA_RESULT_PAGE_COUNT = "readium_epub_result_page_count"
        private const val EXTRA_RESULT_PERCENT = "readium_epub_result_percent"
        private const val NAVIGATOR_TAG = "readium_epub_navigator"
        private const val TAG = "ReadiumEpubReader"
        private const val FOLIATE_SELECTION_CFI_ASSET = "foliate-selection-cfi.js"
        private const val ACTION_COPY = 1000
        private const val ACTION_HIGHLIGHT = 1001
        private const val ACTION_HIGHLIGHT_WITH_NOTE = 1002
        private const val ACTION_SHARE = 1003
        private const val ACTION_WEB_SEARCH = 1004
        private val SELECTION_ACTIONS = setOf(
            ACTION_COPY,
            ACTION_SHARE,
            ACTION_WEB_SEARCH,
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
            libraryId: String = "",
            launchMode: ReaderLaunchMode,
            initialChapter: Int,
            initialPage: Int,
            initialPageCount: Int,
            initialPercent: Float?,
            initialCfi: String? = null,
            initialAnnotationText: String? = null,
            initialAnnotationChapterIndex: Int? = null,
            initialAnnotationId: String? = null,
            initialAnnotationColor: String? = null,
            initialAnnotationStyle: String? = null
        ): Intent = Intent(context, ReadiumEpubReaderActivity::class.java)
            .putExtra(EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(EXTRA_FILE_ID, fileId)
            .putExtra(EXTRA_BOOK_ID, bookId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_READER_KEY, readerKey)
            .putExtra(EXTRA_LIBRARY_ID, libraryId)
            .putExtra(EXTRA_IS_PREVIEW, launchMode == ReaderLaunchMode.PREVIEW)
            .putExtra(EXTRA_INITIAL_CHAPTER, initialChapter)
            .putExtra(EXTRA_INITIAL_PAGE, initialPage)
            .putExtra(EXTRA_INITIAL_PAGE_COUNT, initialPageCount)
            .apply {
                initialPercent?.let { putExtra(EXTRA_INITIAL_PERCENT, it) }
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
                percent = data.getFloatExtra(EXTRA_RESULT_PERCENT, Float.NaN).takeUnless(Float::isNaN)
            )
        }
    }
}

internal class ReadiumEpubLocatorStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "readium_epub_locations",
        Context.MODE_PRIVATE
    )

    fun read(readerKey: String): Locator? {
        if (readerKey.isBlank()) return null
        val json = preferences.getString(readerKey, null) ?: return null
        return runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
    }

    fun save(readerKey: String, locator: Locator) {
        if (readerKey.isBlank()) return
        preferences.edit().putString(readerKey, locator.toJSON().toString()).apply()
    }
}
