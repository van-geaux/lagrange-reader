package com.bookorbit.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.ReadingProgression as ReadiumReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
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

internal fun readiumReadingProgression(
    readingDirection: LibraryReadingDirection
): ReadiumReadingProgression = if (
    readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT
) {
    ReadiumReadingProgression.RTL
} else {
    ReadiumReadingProgression.LTR
}

@OptIn(ExperimentalReadiumApi::class)
internal fun readiumPreferences(
    theme: EpubReaderTheme,
    fontScale: Float,
    readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT
): EpubPreferences = EpubPreferences(
    backgroundColor = ReadiumColor(theme.backgroundColor),
    textColor = ReadiumColor(cssHexColorInt(theme.foregroundCss)),
    theme = when (theme) {
        EpubReaderTheme.Light -> ReadiumTheme.LIGHT
        EpubReaderTheme.Sepia -> ReadiumTheme.SEPIA
        EpubReaderTheme.Dark -> ReadiumTheme.DARK
    },
    fontSize = fontScale.coerceIn(0.9f, 1.5f).toDouble(),
    readingProgression = readiumReadingProgression(readingDirection),
    pageMargins = 0.0,
    columnCount = ColumnCount.ONE,
    scroll = false
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

internal data class ReadiumEpubProgressResult(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val percent: Float?
)

@OptIn(ExperimentalReadiumApi::class)
class ReadiumEpubReaderActivity : FragmentActivity() {
    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private var progressView: ProgressBar? = null
    private var readerContainerId: Int = View.NO_ID
    private lateinit var rootView: FrameLayout
    private lateinit var readerViewport: FrameLayout
    private lateinit var readerContainer: FrameLayout
    private lateinit var chromeView: ComposeView
    private lateinit var optionsView: ComposeView
    private lateinit var footerView: ComposeView
    private lateinit var tapZoneTutorialView: ComposeView

    private lateinit var readerKey: String
    private lateinit var libraryId: String
    private lateinit var displayTitle: String
    private var isPreview: Boolean = false
    private var selectedTheme by mutableStateOf(EpubReaderTheme.Sepia)
    private var padding by mutableStateOf(EpubPaddingPercentages())
    private var fontScale by mutableStateOf(1f)
    private var readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT
    private var chapterTitles by mutableStateOf(emptyList<String>())
    private var currentChapter by mutableStateOf(0)
    private var currentPage by mutableStateOf(0)
    private var currentPageCount by mutableStateOf(1)
    private var currentPercent by mutableStateOf(0f)
    private var currentBookPage by mutableStateOf<Int?>(null)
    private var bookPositionCount by mutableStateOf<Int?>(null)
    private var tapZoneTutorialHasShown = false

    private val themeStore by lazy { EpubReaderThemeStore(this) }
    private val paddingStore by lazy { EpubReaderPaddingStore(this) }
    private val appPreferencesStore by lazy { AppPreferencesStore(this) }
    private val locatorStore by lazy { ReadiumEpubLocatorStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
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
        val appPreferences = appPreferencesStore.read()
        val readerPreferences = appPreferences.libraryReaderPreferences[libraryId]
            ?: LibraryReaderPreferences(
                theme = themeStore.read(),
                padding = paddingStore.read(readerKey)
            )
        selectedTheme = readerPreferences.theme
        padding = readerPreferences.padding
        fontScale = readerPreferences.fontScale
        readingDirection = readerPreferences.readingDirection
        appPreferencesStore.save(
            appPreferences.withReaderPreferences(libraryId, readerPreferences)
        )
        configureSystemBars()
        createReaderViews()
        installBackHandler()

        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null) {
            showError("The EPUB reader file is unavailable.")
            return
        }
        lifecycleScope.launch {
            when (val result = openReadiumEpub(this@ReadiumEpubReaderActivity, file)) {
                is ReadiumEpubOpenResult.Error -> showError(result.message)
                is ReadiumEpubOpenResult.Opened -> {
                    bookPositionCount = withContext(Dispatchers.IO) {
                        result.publication.positions().size.takeIf { it > 0 }
                    }
                    showPublication(result.publication)
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
        readerContainerId = View.generateViewId()
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
                        onBackToReading = ::hideChrome,
                        onCloseBook = ::finishReader,
                        onOpenSettings = ::showOptions,
                        onPositionSelected = ::goToPage,
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
                            theme = selectedTheme,
                            padding = padding,
                            fontScale = fontScale,
                            onContinueReading = ::hideOptions,
                            onCloseBook = ::finishReader,
                            onThemeSelected = ::applyTheme,
                            onPaddingChange = ::applyPadding,
                            onPaddingChangeFinished = { saveLibraryReaderPreferences() },
                            onDecreaseFont = { applyFontScale(fontScale - 0.1f) },
                            onIncreaseFont = { applyFontScale(fontScale + 0.1f) },
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
        addReadiumAudioPlayerOverlay(rootView, readerViewport)
        setContentView(rootView)
        readerViewport.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyReaderPadding() }
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

    private fun showPublication(openedPublication: Publication) {
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
        chapterTitles = openedPublication.readingOrder.mapIndexed { index, link ->
            chapterTitle(openedPublication.tableOfContents, link) ?: link.title ?: "Chapter ${index + 1}"
        }
        val initialLocator = initialLocator(openedPublication)
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
            initialPreferences = readiumPreferences(selectedTheme, fontScale, readingDirection),
            paginationListener = paginationListener,
            configuration = EpubNavigatorFragment.Configuration(shouldApplyInsetsPadding = false)
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
                readingDirection = readingDirection,
                horizontalEdgeThresholdPercent = 0.25f
            )
        )
        fragment.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    toggleChrome()
                    return true
                }
            }
        )
        navigator = fragment
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fragment.currentLocator.collect(::updateLocation)
            }
        }
        progressView?.visibility = View.GONE
        applyReaderPadding()
        showTapZoneTutorial()
    }

    private fun initialLocator(openedPublication: Publication): Locator {
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
        currentBookPage = locator.locations.position
        if (!isPreview) locatorStore.save(readerKey, locator)
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

    private fun applyTheme(theme: EpubReaderTheme) {
        selectedTheme = theme
        themeStore.save(theme)
        rootView.setBackgroundColor(theme.backgroundColor)
        readerViewport.setBackgroundColor(theme.backgroundColor)
        readerContainer.setBackgroundColor(theme.backgroundColor)
        navigator?.submitPreferences(readiumPreferences(theme, fontScale, readingDirection))
        saveLibraryReaderPreferences()
        configureSystemBars()
    }

    private fun applyFontScale(scale: Float) {
        fontScale = scale.coerceIn(0.9f, 1.5f)
        navigator?.submitPreferences(
            readiumPreferences(selectedTheme, fontScale, readingDirection)
        )
        saveLibraryReaderPreferences()
    }

    private fun applyPadding(next: EpubPaddingPercentages) {
        padding = next
        applyReaderPadding()
    }

    private fun saveLibraryReaderPreferences() {
        val current = appPreferencesStore.read()
        appPreferencesStore.save(
            current.withReaderPreferences(
                libraryId,
                LibraryReaderPreferences(
                    readingDirection = readingDirection,
                    theme = selectedTheme,
                    fontScale = fontScale,
                    padding = padding
                )
            )
        )
    }

    private fun applyReaderPadding() {
        if (!::readerViewport.isInitialized || readerViewport.width <= 0 || readerViewport.height <= 0) return
        fun horizontal(value: Float): Int =
            (readerViewport.width * value.coerceIn(0f, 100f) / 400f).toInt()
        fun vertical(value: Float): Int =
            (readerViewport.height * value.coerceIn(0f, 100f) / 400f).toInt()
        readerContainer.setPadding(
            horizontal(padding.left),
            vertical(padding.top),
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

    private fun showTapZoneTutorial() {
        tapZoneTutorialHasShown = true
        tapZoneTutorialView.visibility = View.VISIBLE
        tapZoneTutorialView.doOnPreDraw {
            lifecycleScope.launch {
                delay(READER_TAP_ZONE_TUTORIAL_DURATION_MILLIS)
                hideTapZoneTutorial()
            }
        }
    }

    private fun hideTapZoneTutorial() {
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

    private fun updateResult() {
        if (isPreview) return
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_RESULT_CHAPTER, currentChapter)
                .putExtra(EXTRA_RESULT_PAGE, currentPage)
                .putExtra(EXTRA_RESULT_PAGE_COUNT, currentPageCount)
                .putExtra(EXTRA_RESULT_PERCENT, currentPercent)
        )
    }

    private fun finishReader() {
        updateResult()
        finish()
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

    override fun onDestroy() {
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
        private const val EXTRA_IS_PREVIEW = "readium_epub_is_preview"
        private const val EXTRA_INITIAL_CHAPTER = "readium_epub_initial_chapter"
        private const val EXTRA_INITIAL_PAGE = "readium_epub_initial_page"
        private const val EXTRA_INITIAL_PAGE_COUNT = "readium_epub_initial_page_count"
        private const val EXTRA_INITIAL_PERCENT = "readium_epub_initial_percent"
        private const val EXTRA_RESULT_CHAPTER = "readium_epub_result_chapter"
        private const val EXTRA_RESULT_PAGE = "readium_epub_result_page"
        private const val EXTRA_RESULT_PAGE_COUNT = "readium_epub_result_page_count"
        private const val EXTRA_RESULT_PERCENT = "readium_epub_result_percent"
        private const val NAVIGATOR_TAG = "readium_epub_navigator"

        fun createIntent(
            context: Context,
            file: File,
            title: String,
            readerKey: String,
            libraryId: String = "",
            launchMode: ReaderLaunchMode,
            initialChapter: Int,
            initialPage: Int,
            initialPageCount: Int,
            initialPercent: Float?
        ): Intent = Intent(context, ReadiumEpubReaderActivity::class.java)
            .putExtra(EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_READER_KEY, readerKey)
            .putExtra(EXTRA_LIBRARY_ID, libraryId)
            .putExtra(EXTRA_IS_PREVIEW, launchMode == ReaderLaunchMode.PREVIEW)
            .putExtra(EXTRA_INITIAL_CHAPTER, initialChapter)
            .putExtra(EXTRA_INITIAL_PAGE, initialPage)
            .putExtra(EXTRA_INITIAL_PAGE_COUNT, initialPageCount)
            .apply {
                initialPercent?.let { putExtra(EXTRA_INITIAL_PERCENT, it) }
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
