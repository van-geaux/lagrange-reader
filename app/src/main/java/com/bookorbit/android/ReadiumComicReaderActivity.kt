package com.bookorbit.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.LruCache
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
import androidx.compose.foundation.lazy.LazyListState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpContainer
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

private const val MAX_CONTINUOUS_COMIC_PAGE_BYTES = 64L * 1024L * 1024L
private const val MIN_CONTINUOUS_COMIC_CACHE_BYTES = 48L * 1024L * 1024L
private const val MAX_CONTINUOUS_COMIC_CACHE_BYTES = 192L * 1024L * 1024L

private fun continuousComicCacheBudgetBytes(): Int =
    (Runtime.getRuntime().maxMemory() / 4L)
        .coerceIn(MIN_CONTINUOUS_COMIC_CACHE_BYTES, MAX_CONTINUOUS_COMIC_CACHE_BYTES)
        .toInt()

internal sealed interface ReadiumComicOpenResult {
    data class Opened(val publication: Publication) : ReadiumComicOpenResult
    data class Error(val message: String) : ReadiumComicOpenResult
}

internal suspend fun openReadiumComic(
    context: Context,
    file: File
): ReadiumComicOpenResult = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        return@withContext ReadiumComicOpenResult.Error("The comic file is unavailable.")
    }
    if (!ReaderFileValidator.canRenderComicLocally(file)) {
        return@withContext ReadiumComicOpenResult.Error("Readium requires a CBZ image archive.")
    }

    val appContext = context.applicationContext
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    val asset = assetRetriever.retrieve(file, MediaType.CBZ).getOrNull()
        ?: return@withContext ReadiumComicOpenResult.Error("Readium could not read this comic file.")
    val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )
    val publication = PublicationOpener(publicationParser).open(
        asset = asset,
        allowUserInteraction = false
    ).getOrNull()
        ?: return@withContext ReadiumComicOpenResult.Error("Readium could not open this comic publication.")
    if (!publication.conformsTo(Publication.Profile.DIVINA)) {
        publication.close()
        return@withContext ReadiumComicOpenResult.Error("Readium did not recognize this file as an image publication.")
    }
    ReadiumComicOpenResult.Opened(publication)
}

@OptIn(DelicateReadiumApi::class)
internal suspend fun openReadiumRemoteComic(
    context: Context,
    title: String,
    pagesUrl: String,
    pageCount: Int,
    pageMediaType: MediaType,
    httpClient: HttpClient? = null
): ReadiumComicOpenResult = withContext(Dispatchers.IO) {
    if (pageCount <= 0) {
        return@withContext ReadiumComicOpenResult.Error("BookOrbit reported no comic pages.")
    }
    val normalizedPagesUrl = pagesUrl.trimEnd('/')
    val baseUrl = AbsoluteUrl(normalizedPagesUrl + "/")
        ?: return@withContext ReadiumComicOpenResult.Error("The comic page URL is invalid.")
    val pageUrls = (0 until pageCount).mapNotNull { pageIndex ->
        AbsoluteUrl(normalizedPagesUrl + "/" + pageIndex)
    }
    if (pageUrls.size != pageCount) {
        return@withContext ReadiumComicOpenResult.Error("A comic page URL is invalid.")
    }
    val client = httpClient ?: BookOrbitRepository(context.applicationContext).let { repository ->
        AuthenticatedReadiumHttpClient(
            delegate = DefaultHttpClient(),
            headersProvider = { url -> repository.streamingRequestHeaders(url.toString()) },
            recoverAuthentication = repository::recoverStreamingAuthentication
        )
    }
    val manifest = Manifest(
        metadata = Metadata(
            identifier = normalizedPagesUrl,
            conformsTo = setOf(Publication.Profile.DIVINA),
            localizedTitle = LocalizedString(title)
        ),
        readingOrder = pageUrls.map { pageUrl ->
            Link(href = Href(pageUrl), mediaType = pageMediaType)
        }
    )
    val publication = Publication(
        manifest,
        HttpContainer(baseUrl, pageUrls.toSet(), client)
    )
    if (!publication.conformsTo(Publication.Profile.DIVINA)) {
        publication.close()
        return@withContext ReadiumComicOpenResult.Error(
            "Readium did not recognize the remote comic as an image publication."
        )
    }
    ReadiumComicOpenResult.Opened(publication)
}

internal data class ReadiumComicProgressResult(
    val pageIndex: Int,
    val pageCount: Int,
    val percent: Float?
)

internal suspend fun readContinuousComicPageBytes(resource: Resource): ByteArray? {
    val declaredLength = resource.length().getOrNull()
    if (declaredLength != null && declaredLength !in 1..MAX_CONTINUOUS_COMIC_PAGE_BYTES) {
        return null
    }
    val requestedLength = declaredLength ?: (MAX_CONTINUOUS_COMIC_PAGE_BYTES + 1L)
    return resource.read(0L until requestedLength).getOrNull()
        ?.takeIf { bytes -> bytes.isNotEmpty() && bytes.size.toLong() <= MAX_CONTINUOUS_COMIC_PAGE_BYTES }
}

class ReadiumComicReaderActivity : FragmentActivity() {
    private var publication: Publication? = null
    private var navigator: ImageNavigatorFragment? = null
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
    private var readingDirection by mutableStateOf(LibraryReadingDirection.LEFT_TO_RIGHT)
    private var readerPreferences by mutableStateOf(LibraryReaderPreferences())
    private val continuousComicPageCache = object : LruCache<String, Bitmap>(
        continuousComicCacheBudgetBytes()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private var continuousListState: LazyListState? = null
    private var continuousComicView: ComposeView? = null
    private var navigatorLocationJob: Job? = null
    private var currentPage by mutableStateOf(0)
    private var currentPageCount by mutableStateOf(1)
    private var tapZoneTutorialHasShown = false

    private val locatorStore by lazy { ReadiumComicLocatorStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = ImageNavigatorFragment.createDummyFactory()
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
        val pagesUrl = intent.getStringExtra(EXTRA_PAGES_URL)
        val pageCount = intent.getIntExtra(EXTRA_PAGE_COUNT, 0)
        val pageMediaType = intent.getStringExtra(EXTRA_PAGE_MEDIA_TYPE)
            ?.let(MediaType::invoke)
        lifecycleScope.launch {
            val result = when {
                file != null -> openReadiumComic(this@ReadiumComicReaderActivity, file)
                !pagesUrl.isNullOrBlank() && pageCount > 0 && pageMediaType != null ->
                    openReadiumRemoteComic(
                        context = this@ReadiumComicReaderActivity,
                        title = displayTitle,
                        pagesUrl = pagesUrl,
                        pageCount = pageCount,
                        pageMediaType = pageMediaType
                    )
                else -> ReadiumComicOpenResult.Error(
                    "The comic reader source is unavailable."
                )
            }
            when (result) {
                is ReadiumComicOpenResult.Error -> showError(result.message)
                is ReadiumComicOpenResult.Opened -> showPublication(result.publication)
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
                        title = if (isPreview) "Preview · $displayTitle" else displayTitle,
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
                            title = if (isPreview) "Preview · $displayTitle" else displayTitle,
                            currentPage = currentPage,
                            pageCount = currentPageCount,
                            preferences = readerPreferences,
                            onContinueReading = ::hideOptions,
                            onCloseBook = ::finishReader,
                            onPreferencesChange = ::applyReaderPreferences,
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

    private fun showPublication(
        openedPublication: Publication,
        requestedLocator: Locator? = null,
        showTutorial: Boolean = true
    ) {
        if (isFinishing || isDestroyed) {
            openedPublication.close()
            return
        }
        if (openedPublication.readingOrder.isEmpty()) {
            openedPublication.close()
            showError("This comic has no readable images.")
            return
        }
        publication = openedPublication
        currentPageCount = openedPublication.readingOrder.size.coerceAtLeast(1)
        val initialLocator = requestedLocator ?: initialLocator(openedPublication)
        if (readerPreferences.comicLayoutMode == ReaderLayoutMode.CONTINUOUS) {
            showContinuousPublication(openedPublication, initialLocator)
            progressView?.visibility = View.GONE
            if (showTutorial) showTapZoneTutorial(continuous = true)
            return
        }
        val fragmentFactory = ImageNavigatorFragment.createFactory(
            publication = openedPublication,
            initialLocator = initialLocator
        )
        supportFragmentManager.fragmentFactory = fragmentFactory
        val fragment = fragmentFactory.instantiate(
            classLoader,
            ImageNavigatorFragment::class.java.name
        ) as ImageNavigatorFragment
        supportFragmentManager.beginTransaction()
            .replace(readerContainerId, fragment, NAVIGATOR_TAG)
            .commitNow()
        fragment.publicationView.layoutDirection = if (
            readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT
        ) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        fragment.addInputListener(
            LibraryDirectionalNavigationAdapter(
                navigator = fragment,
                readingDirection = { readingDirection },
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
        navigatorLocationJob?.cancel()
        navigatorLocationJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fragment.currentLocator.collect(::updateLocation)
            }
        }
        progressView?.visibility = View.GONE
        if (showTutorial) showTapZoneTutorial()
    }

    private fun showContinuousPublication(
        openedPublication: Publication,
        initialLocator: Locator
    ) {
        val initialPage = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(initialLocator.href.removeFragment())
        }.coerceAtLeast(0)
        currentPage = initialPage
        continuousComicView = ComposeView(this).apply {
            setContent {
                BookOrbitTheme {
                    ContinuousComicReader(
                        pageIndexes = openedPublication.readingOrder.indices.toList(),
                        initialPage = initialPage,
                        pageGapDp = readerPreferences.comicPageGapDp,
                        readingDirection = readingDirection,
                        loadPage = { pageIndex, targetWidthPx ->
                            loadContinuousComicPage(
                                openedPublication,
                                pageIndex,
                                targetWidthPx
                            )
                        },
                        onPageChanged = ::updateContinuousPage,
                        onTap = ::handleContinuousTap,
                        onListStateAvailable = { continuousListState = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }.also { view ->
            readerContainer.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private suspend fun loadContinuousComicPage(
        openedPublication: Publication,
        pageIndex: Int,
        targetWidthPx: Int
    ) = withContext(Dispatchers.IO) {
        val cacheKey = "$pageIndex:$targetWidthPx"
        synchronized(continuousComicPageCache) {
            continuousComicPageCache.get(cacheKey)
        }?.let { cached -> return@withContext cached }
        val link = openedPublication.readingOrder.getOrNull(pageIndex)
            ?: return@withContext null
        val resource = openedPublication.get(link) ?: return@withContext null
        try {
            val bytes = readContinuousComicPageBytes(resource)
                ?: return@withContext null
            val decoded = decodeContinuousComicPage(bytes, targetWidthPx)
            if (decoded != null) {
                synchronized(continuousComicPageCache) {
                    continuousComicPageCache.put(cacheKey, decoded)
                }
            }
            decoded
        } finally {
            resource.close()
        }
    }

    private fun updateContinuousPage(index: Int) {
        val openedPublication = publication ?: return
        val link = openedPublication.readingOrder.getOrNull(index) ?: return
        val locator = openedPublication.locatorFromLink(link) ?: return
        updateLocation(locator)
    }

    private fun handleContinuousTap(action: ContinuousComicTapAction) {
        when (action) {
            ContinuousComicTapAction.PREVIOUS -> goToPage(currentPage - 1)
            ContinuousComicTapAction.NEXT -> goToPage(currentPage + 1)
            ContinuousComicTapAction.MENU -> toggleChrome()
        }
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
        val requestedPage = if (isPreview) 0 else {
            intent.getIntExtra(EXTRA_INITIAL_PAGE, 0)
        }.coerceIn(0, openedPublication.readingOrder.lastIndex)
        return requireNotNull(
            openedPublication.locatorFromLink(openedPublication.readingOrder[requestedPage])
        )
    }

    private fun updateLocation(locator: Locator) {
        val openedPublication = publication ?: return
        val index = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(locator.href.removeFragment())
        }.takeIf { it >= 0 } ?: currentPage
        currentPage = index.coerceIn(0, openedPublication.readingOrder.lastIndex)
        if (!isPreview) locatorStore.save(readerKey, locator)
        updateResult()
    }

    private fun goToPage(index: Int) {
        continuousListState?.let { listState ->
            lifecycleScope.launch {
                val target = index.coerceIn(0, currentPageCount - 1)
                if (AppPreferencesStore(this@ReadiumComicReaderActivity).read().reduceMotion) {
                    listState.scrollToItem(target)
                } else {
                    listState.animateScrollToItem(target)
                }
            }
            return
        }
        val link = publication?.readingOrder?.getOrNull(
            index.coerceIn(0, currentPageCount - 1)
        ) ?: return
        navigator?.go(link)
    }

    private fun applyReaderPreferences(next: LibraryReaderPreferences) {
        val normalized = next.normalized()
        val layoutChanged = normalized.comicLayoutMode != readerPreferences.comicLayoutMode
        readerPreferences = normalized
        readingDirection = normalized.readingDirection
        val store = AppPreferencesStore(this)
        store.save(store.read().withReaderPreferences(libraryId, normalized))
        if (layoutChanged) {
            rebuildPublication()
        } else {
            navigator?.publicationView?.layoutDirection = if (
                normalized.readingDirection == LibraryReadingDirection.RIGHT_TO_LEFT
            ) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
    }

    private fun rebuildPublication() {
        val openedPublication = publication ?: return
        val locator = openedPublication.readingOrder.getOrNull(currentPage)
            ?.let(openedPublication::locatorFromLink)
            ?: return
        supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        }
        navigatorLocationJob?.cancel()
        navigatorLocationJob = null
        navigator = null
        continuousComicView?.let(readerContainer::removeView)
        continuousComicView = null
        continuousListState = null
        showPublication(openedPublication, requestedLocator = locator, showTutorial = false)
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

    private fun showTapZoneTutorial(continuous: Boolean = false) {
        tapZoneTutorialHasShown = true
        tapZoneTutorialView.visibility = View.VISIBLE
        tapZoneTutorialView.setContent {
            BookOrbitTheme {
                ReaderTapZoneTutorial(
                    onDismiss = ::hideTapZoneTutorial,
                    readingDirection = readingDirection,
                    continuous = continuous,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
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

    internal fun isContinuousComicReaderVisible(): Boolean =
        continuousComicView?.isAttachedToWindow == true

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
        synchronized(continuousComicPageCache) {
            continuousComicPageCache.evictAll()
        }
        navigatorLocationJob?.cancel()
        navigatorLocationJob = null
        navigator = null
        continuousListState = null
        continuousComicView = null
    }

    companion object {
        private const val EXTRA_FILE_PATH = "readium_comic_file_path"
        private const val EXTRA_PAGES_URL = "readium_comic_pages_url"
        private const val EXTRA_PAGE_COUNT = "readium_comic_page_count"
        private const val EXTRA_PAGE_MEDIA_TYPE = "readium_comic_page_media_type"
        private const val EXTRA_TITLE = "readium_comic_title"
        private const val EXTRA_READER_KEY = "readium_comic_reader_key"
        private const val EXTRA_LIBRARY_ID = "readium_comic_library_id"
        private const val EXTRA_IS_PREVIEW = "readium_comic_is_preview"
        private const val EXTRA_INITIAL_PAGE = "readium_comic_initial_page"
        private const val EXTRA_RESULT_PAGE = "readium_comic_result_page"
        private const val EXTRA_RESULT_PAGE_COUNT = "readium_comic_result_page_count"
        private const val EXTRA_RESULT_PERCENT = "readium_comic_result_percent"
        private const val NAVIGATOR_TAG = "readium_comic_navigator"

        fun createIntent(
            context: Context,
            file: File,
            title: String,
            readerKey: String,
            libraryId: String = "",
            launchMode: ReaderLaunchMode,
            initialPage: Int
        ): Intent = Intent(context, ReadiumComicReaderActivity::class.java)
            .putExtra(EXTRA_FILE_PATH, file.absolutePath)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_READER_KEY, readerKey)
            .putExtra(EXTRA_LIBRARY_ID, libraryId)
            .putExtra(EXTRA_IS_PREVIEW, launchMode == ReaderLaunchMode.PREVIEW)
            .putExtra(EXTRA_INITIAL_PAGE, initialPage)

        fun createRemoteIntent(
            context: Context,
            pagesUrl: String,
            pageCount: Int,
            pageMediaType: MediaType,
            title: String,
            readerKey: String,
            libraryId: String = "",
            launchMode: ReaderLaunchMode,
            initialPage: Int
        ): Intent = Intent(context, ReadiumComicReaderActivity::class.java)
            .putExtra(EXTRA_PAGES_URL, pagesUrl)
            .putExtra(EXTRA_PAGE_COUNT, pageCount)
            .putExtra(EXTRA_PAGE_MEDIA_TYPE, pageMediaType.toString())
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_READER_KEY, readerKey)
            .putExtra(EXTRA_LIBRARY_ID, libraryId)
            .putExtra(EXTRA_IS_PREVIEW, launchMode == ReaderLaunchMode.PREVIEW)
            .putExtra(EXTRA_INITIAL_PAGE, initialPage)

        internal fun readProgressResult(data: Intent?): ReadiumComicProgressResult? {
            if (data == null || !data.hasExtra(EXTRA_RESULT_PAGE)) return null
            return ReadiumComicProgressResult(
                pageIndex = data.getIntExtra(EXTRA_RESULT_PAGE, 0).coerceAtLeast(0),
                pageCount = data.getIntExtra(EXTRA_RESULT_PAGE_COUNT, 1).coerceAtLeast(1),
                percent = data.getFloatExtra(EXTRA_RESULT_PERCENT, Float.NaN).takeUnless(Float::isNaN)
            )
        }
    }
}

internal class ReadiumComicLocatorStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "readium_comic_locations",
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
