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
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal sealed interface ReadiumPdfOpenResult {
    data class Opened(val publication: Publication) : ReadiumPdfOpenResult
    data class Error(val message: String) : ReadiumPdfOpenResult
}

@OptIn(ExperimentalReadiumApi::class)
internal suspend fun openReadiumPdf(
    context: Context,
    file: File
): ReadiumPdfOpenResult = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        return@withContext ReadiumPdfOpenResult.Error("The PDF file is unavailable.")
    }
    if (!ReaderFileValidator.isReadable(MediaKind.PDF, file)) {
        return@withContext ReadiumPdfOpenResult.Error("This file does not contain a readable PDF document.")
    }

    val appContext = context.applicationContext
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val asset = assetRetriever.retrieve(file, MediaType.PDF).getOrNull()
        ?: return@withContext ReadiumPdfOpenResult.Error("Readium could not read this PDF file.")
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = PdfiumDocumentFactory(appContext)
    )
    val publication = PublicationOpener(publicationParser).open(
        asset = asset,
        allowUserInteraction = false
    ).getOrNull()
        ?: return@withContext ReadiumPdfOpenResult.Error("Readium could not open this PDF publication.")
    if (!publication.conformsTo(Publication.Profile.PDF)) {
        publication.close()
        return@withContext ReadiumPdfOpenResult.Error("Readium did not recognize this file as a PDF publication.")
    }
    ReadiumPdfOpenResult.Opened(publication)
}

internal data class ReadiumPdfProgressResult(
    val pageIndex: Int,
    val pageCount: Int,
    val percent: Float?
)

internal fun pdfiumPreferencesFor(
    preferences: LibraryReaderPreferences
): PdfiumPreferences = PdfiumPreferences(
    pageSpacing = preferences.pdfPageGapDp.toDouble(),
    readingProgression = readiumReadingProgression(preferences.readingDirection),
    scrollAxis = when (preferences.pdfLayoutMode) {
        ReaderLayoutMode.PAGINATED -> Axis.HORIZONTAL
        ReaderLayoutMode.CONTINUOUS -> Axis.VERTICAL
    }
)

@OptIn(ExperimentalReadiumApi::class)
class ReadiumPdfReaderActivity : FragmentActivity() {
    private var publication: Publication? = null
    private var navigator: PdfiumNavigatorFragment? = null
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
    private var readingDirection = LibraryReadingDirection.LEFT_TO_RIGHT
    private var readerPreferences = LibraryReaderPreferences()
    private var currentPage by mutableStateOf(0)
    private var currentPageCount by mutableStateOf(1)
    private var pageLocators: List<Locator> = emptyList()
    private var tapZoneTutorialHasShown = false

    private val locatorStore by lazy { ReadiumPdfLocatorStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = PdfNavigatorFragment.createDummyFactory(
                pdfEngineProvider = PdfiumEngineProvider()
            )
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
        readerPreferences = AppPreferencesStore(this).read().readerPreferencesFor(libraryId)
        readingDirection = readerPreferences.readingDirection
        isPreview = intent.getBooleanExtra(EXTRA_IS_PREVIEW, false)
        configureSystemBars()
        createReaderViews()
        installBackHandler()

        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null) {
            showError("The PDF reader file is unavailable.")
            return
        }
        lifecycleScope.launch {
            when (val result = openReadiumPdf(this@ReadiumPdfReaderActivity, file)) {
                is ReadiumPdfOpenResult.Error -> showError(result.message)
                is ReadiumPdfOpenResult.Opened -> showPublication(result.publication)
            }
        }
    }

    private fun createReaderViews() {
        rootView = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        readerViewport = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
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
            setBackgroundColor(Color.BLACK)
            setPadding(0, 0, 0, dpToPx(EPUB_READER_PROGRESS_FOOTER_HEIGHT_DP))
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
                    ComicReaderProgressFooter(
                        currentPage = currentPage,
                        pageCount = currentPageCount,
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
                        title = if (isPreview) "Preview \u00b7 $displayTitle" else displayTitle,
                        theme = EpubReaderTheme.Dark,
                        positionKind = "Page",
                        positionTitles = List(currentPageCount.coerceAtLeast(1)) { index -> "Page ${index + 1}" },
                        currentPosition = currentPage,
                        onBackToReading = ::hideChrome,
                        onCloseBook = ::finishReader,
                        onOpenSettings = ::showOptions,
                        onPositionSelected = ::goToPage,
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
                        ComicReaderOptionsBottomSheet(
                            title = if (isPreview) "Preview \u00b7 $displayTitle" else displayTitle,
                            currentPage = currentPage,
                            pageCount = currentPageCount,
                            onContinueReading = ::hideOptions,
                            onCloseBook = ::finishReader,
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

    private suspend fun showPublication(openedPublication: Publication) {
        if (isFinishing || isDestroyed) {
            openedPublication.close()
            return
        }
        if (openedPublication.readingOrder.isEmpty()) {
            openedPublication.close()
            showError("This PDF has no readable pages.")
            return
        }
        publication = openedPublication
        pageLocators = withContext(Dispatchers.IO) { openedPublication.positions() }
        currentPageCount = pageLocators.size.coerceAtLeast(1)
        val initialLocator = initialLocator(openedPublication)
        val fragmentFactory = PdfNavigatorFragment.createFactory(
            publication = openedPublication,
            initialLocator = initialLocator,
            preferences = pdfiumPreferencesFor(readerPreferences),
            pdfEngineProvider = PdfiumEngineProvider()
        )
        supportFragmentManager.fragmentFactory = fragmentFactory
        @Suppress("UNCHECKED_CAST")
        val fragment = fragmentFactory.instantiate(
            classLoader,
            "org.readium.r2.navigator.pdf.PdfNavigatorFragment"
        ) as PdfiumNavigatorFragment
        supportFragmentManager.beginTransaction()
            .replace(readerContainerId, fragment, NAVIGATOR_TAG)
            .commitNow()
        fragment.publicationView.layoutDirection = if (
            readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT
        ) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
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
        showTapZoneTutorial()
    }

    private fun initialLocator(openedPublication: Publication): Locator? {
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
        val requestedPage = if (isPreview) 0 else {
            intent.getIntExtra(EXTRA_INITIAL_PAGE, 0)
        }.coerceIn(0, currentPageCount - 1)
        return pageLocators.getOrNull(requestedPage)
            ?: openedPublication.locatorFromLink(openedPublication.readingOrder.first())
    }

    private fun updateLocation(locator: Locator) {
        val index = (locator.locations.position ?: 1) - 1
        currentPage = index.coerceIn(0, currentPageCount - 1)
        if (!isPreview) locatorStore.save(readerKey, locator)
        updateResult()
    }

    private fun goToPage(index: Int) {
        val locator = pageLocators.getOrNull(index.coerceIn(0, currentPageCount - 1)) ?: return
        navigator?.go(locator)
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
        val percent = if (currentPageCount <= 1) {
            100f
        } else {
            currentPage.toFloat() / (currentPageCount - 1).toFloat() * 100f
        }
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_RESULT_PAGE, currentPage)
                .putExtra(EXTRA_RESULT_PAGE_COUNT, currentPageCount)
                .putExtra(EXTRA_RESULT_PERCENT, percent)
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
            isAppearanceLightStatusBars = false
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
        publication = null
        navigator = null
    }

    companion object {
        private const val EXTRA_FILE_PATH = "readium_pdf_file_path"
        private const val EXTRA_TITLE = "readium_pdf_title"
        private const val EXTRA_READER_KEY = "readium_pdf_reader_key"
        private const val EXTRA_LIBRARY_ID = "readium_pdf_library_id"
        private const val EXTRA_IS_PREVIEW = "readium_pdf_is_preview"
        private const val EXTRA_INITIAL_PAGE = "readium_pdf_initial_page"
        private const val EXTRA_RESULT_PAGE = "readium_pdf_result_page"
        private const val EXTRA_RESULT_PAGE_COUNT = "readium_pdf_result_page_count"
        private const val EXTRA_RESULT_PERCENT = "readium_pdf_result_percent"
        private const val NAVIGATOR_TAG = "readium_pdf_navigator"

        fun createIntent(
            context: Context,
            file: File,
            title: String,
            readerKey: String,
            libraryId: String = "",
            launchMode: ReaderLaunchMode,
            initialPage: Int
        ): Intent = Intent(context, ReadiumPdfReaderActivity::class.java)
            .putExtra(EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_READER_KEY, readerKey)
            .putExtra(EXTRA_LIBRARY_ID, libraryId)
            .putExtra(EXTRA_IS_PREVIEW, launchMode == ReaderLaunchMode.PREVIEW)
            .putExtra(EXTRA_INITIAL_PAGE, initialPage)

        internal fun readProgressResult(data: Intent?): ReadiumPdfProgressResult? {
            if (data == null || !data.hasExtra(EXTRA_RESULT_PAGE)) return null
            return ReadiumPdfProgressResult(
                pageIndex = data.getIntExtra(EXTRA_RESULT_PAGE, 0).coerceAtLeast(0),
                pageCount = data.getIntExtra(EXTRA_RESULT_PAGE_COUNT, 1).coerceAtLeast(1),
                percent = data.getFloatExtra(EXTRA_RESULT_PERCENT, Float.NaN).takeUnless(Float::isNaN)
            )
        }
    }
}

internal class ReadiumPdfLocatorStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "readium_pdf_locations",
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
