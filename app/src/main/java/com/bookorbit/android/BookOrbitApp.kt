package com.bookorbit.android

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

@Composable
fun BookOrbitApp(
    screen: AppScreen,
    coordinator: AppCoordinator
) {
    when (screen) {
        AppScreen.Loading -> LoadingScreen()
        is AppScreen.ServerSetup -> ServerSetupScreen(
            initialServerUrl = screen.serverUrl,
            message = screen.message,
            onContinue = coordinator::saveServer
        )
        is AppScreen.Login -> LoginScreen(
            serverUrl = screen.serverUrl,
            message = screen.message,
            isSubmitting = screen.isSubmitting,
            onChangeServer = coordinator::clearServer,
            onSubmit = coordinator::submitLogin
        )
        is AppScreen.Browser -> NativeLibraryBrowserScreen(
            state = screen.browserState,
            onRefresh = coordinator::loadBrowser,
            onSignIn = coordinator::beginSignIn,
            onSignOut = coordinator::signOut,
            onLibrarySelected = coordinator::selectLibrary,
            searchBooks = coordinator::searchBooks,
            localBooksLoader = coordinator::loadLocalBooks,
            coverLoader = coordinator::loadBookCover,
            bookDetailLoader = coordinator::loadBookDetail,
            seriesDetailLoader = coordinator::loadSeriesDetail,
            seriesCatalogLoader = coordinator::loadSeriesCatalog,
            authorsCatalogLoader = coordinator::loadAuthorsCatalog,
            authorBooksLoader = coordinator::loadAuthorBooks,
            catalogImageLoader = coordinator::loadCatalogImage,
            onBookOpen = coordinator::openBook,
            onPreview = coordinator::previewBook,
            onDownload = coordinator::downloadBook,
            onCancelDownload = coordinator::cancelDownload,
            onDeleteLocalCopy = coordinator::deleteLocalCopy,
            onRemoveFromCurrentlyReading = coordinator::removeFromCurrentlyReading,
            onMarkAsRead = coordinator::markBookAsRead,
            onMarkAsUnread = coordinator::markBookAsUnread
        )
        is AppScreen.ReaderLoading -> ReaderLoadingScreen(
            book = screen.book,
            launchMode = screen.launchMode,
            onBack = coordinator::closeReader
        )
        is AppScreen.Reader -> ReaderScreen(
            state = screen.readerState,
            onBack = coordinator::closeReader,
            onProgress = coordinator::onProgress
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderLoadingScreen(
    book: BookSummary,
    launchMode: ReaderLaunchMode,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (launchMode == ReaderLaunchMode.PREVIEW) "Preview · ${book.title}" else book.title)
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Lagrange logo",
                modifier = Modifier.size(96.dp)
            )
            Text("Lagrange", style = MaterialTheme.typography.headlineSmall)
            Text(
                "a BookOrbit reader",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Loading your library…",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSetupScreen(
    initialServerUrl: String,
    message: String?,
    onContinue: (String) -> Unit
) {
    var server by remember(initialServerUrl) { mutableStateOf(initialServerUrl) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { BookOrbitTopBar(title = "Connect") },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OrbitEyebrow("Private reader")
                Text("Your library, in orbit.", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Connect securely to your BookOrbit server. Your library stays on your server; this app is your reading window.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!message.isNullOrBlank()) {
                    OrbitMessage(message, tone = OrbitMessageTone.ERROR)
                }
                OutlinedTextField(
                    value = server,
                    onValueChange = {
                        server = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "BookOrbit server URL" },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://books.example.com") },
                    singleLine = true
                )
                error?.let {
                    OrbitMessage(it, tone = OrbitMessageTone.ERROR)
                }
                Button(
                    onClick = {
                        val normalized = normalizeServerUrl(server)
                        if (normalized == null) {
                            error = invalidServerUrlMessage()
                        } else {
                            onContinue(normalized)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text("Continue")
                }
                if (server.isNotBlank() && !message.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onContinue(server) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    serverUrl: String,
    message: String?,
    isSubmitting: Boolean,
    onChangeServer: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var username by remember(serverUrl) { mutableStateOf("") }
    var password by remember(serverUrl) { mutableStateOf("") }
    var passwordVisible by remember(serverUrl) { mutableStateOf(false) }
    var validationMessage by remember(serverUrl) { mutableStateOf<String?>(null) }
    val submit = {
        when {
            username.isBlank() -> validationMessage = "Enter your username."
            password.isBlank() -> validationMessage = "Enter your password."
            else -> {
                validationMessage = null
                onSubmit(username.trim(), password)
            }
        }
    }

    Scaffold(
        topBar = {
            BookOrbitTopBar(
                title = "Sign in",
                actions = { TextButton(onClick = onChangeServer) { Text("Change server") } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxSize()
        ) {
            if (!message.isNullOrBlank()) {
                OrbitMessage(
                    text = message,
                    modifier = Modifier.padding(bottom = 12.dp),
                    tone = OrbitMessageTone.ERROR
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OrbitEyebrow("BookOrbit server")
                    Text(serverUrl, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            validationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "BookOrbit username" },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            validationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "BookOrbit password" },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isSubmitting
                            ) {
                                Text(if (passwordVisible) "Hide" else "Show")
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() })
                    )
                    validationMessage?.let {
                        OrbitMessage(it, tone = OrbitMessageTone.ERROR)
                    }
                    Button(
                        onClick = submit,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign in")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryBrowserScreen(
    state: BrowserState,
    onRefresh: () -> Unit,
    onSessionAction: () -> Unit,
    onLibrarySelected: (String) -> Unit,
    onBookOpen: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    Scaffold(
        topBar = {
            BookOrbitTopBar(
                title = "Libraries",
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    TextButton(
                        onClick = onRefresh,
                        enabled = !state.isRefreshing
                    ) {
                        Text("Refresh")
                    }
                    TextButton(onClick = onSessionAction) {
                        Text(if (state.isOfflineSnapshot) "Sign in" else "Sign out")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OrbitEyebrow("Connected server")
                Text(state.serverUrl, style = MaterialTheme.typography.bodySmall)
            }
            if (isDebugBuild) {
                item {
                    Text(
                        text = "Pending sync queue: ${state.debugPendingProgressCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            state.message?.let { message ->
                item {
                    OrbitMessage(
                        text = message,
                        tone = if (state.isOfflineSnapshot) {
                            OrbitMessageTone.OFFLINE
                        } else {
                            OrbitMessageTone.ERROR
                        }
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        state.isLoadingLibraries -> LoadingRow("Loading libraries...")
                        state.libraries.isEmpty() -> Text(
                            text = "No libraries found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        else -> state.libraries.forEach { library ->
                            FilterChip(
                                selected = library.id == state.selectedLibraryId,
                                onClick = { onLibrarySelected(library.id) },
                                enabled = !state.isLoadingBooks,
                                label = { Text(library.name) }
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = if (state.selectedLibraryId == null) "Select a library" else "Books",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (state.isLoadingBooks) {
                item {
                    LoadingRow("Loading books...")
                }
            } else if (state.selectedLibraryId != null && state.books.isEmpty()) {
                item {
                    Text(
                        text = "No books found in this library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            items(state.books) { book ->
                val fileId = book.fileId
                val isDownloading = fileId != null && state.downloadingFileIds.contains(fileId)
                val downloadFailed = fileId != null && state.failedDownloadFileIds.contains(fileId)
                val unavailableOffline = state.isOfflineSnapshot && !book.isDownloaded
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = book.title,
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            text = bookStatus(book, state.isOfflineSnapshot),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onBookOpen(book) },
                                enabled = !isDownloading && !unavailableOffline
                            ) {
                                Text(
                                    when {
                                        book.isDownloaded -> "Open local"
                                        unavailableOffline -> "Unavailable offline"
                                        else -> "Read / Listen"
                                    }
                                )
                            }
                            if (book.isDownloaded) {
                                OutlinedButton(
                                    onClick = { onDeleteLocalCopy(book) },
                                    enabled = !isDownloading
                                ) {
                                    Text("Delete local")
                                }
                            } else if (fileId != null && !state.isOfflineSnapshot) {
                                if (isDownloading) {
                                    Button(
                                        onClick = {},
                                        enabled = false
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Downloading",
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    OutlinedButton(onClick = { onCancelDownload(book) }) {
                                        Text("Cancel")
                                    }
                                } else {
                                    OutlinedButton(onClick = { onDownload(book) }) {
                                        Text(if (downloadFailed) "Retry" else "Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderState,
    onBack: () -> Unit,
    onProgress: (BookSummary, Long, Int, Float?) -> Unit
) {
    if (readerKeepsScreenAwake(state.book.mediaKind)) {
        KeepReaderScreenAwake()
    }
    val isPreview = state.launchMode == ReaderLaunchMode.PREVIEW
    val readerProgress: (BookSummary, Long, Int, Float?) -> Unit = if (isPreview) {
        { _, _, _, _ -> }
    } else {
        onProgress
    }
    if (state.book.mediaKind == MediaKind.EPUB) {
        EpubReaderView(
            title = state.book.title,
            readerKey = listOf(state.book.id, state.book.fileId.orEmpty()).joinToString("|"),
            file = state.localFile,
            initialChapter = if (isPreview) 0 else state.pageIndex,
            initialPage = if (isPreview) 0 else state.readerPageIndex,
            initialPercent = if (isPreview) null else state.progressPercent,
            isPreview = isPreview,
            onBack = onBack,
            onProgress = { chapterIndex, pageIndex, pageCount, percent ->
                readerProgress(
                    state.book.copy(
                        readerPageIndex = pageIndex,
                        readerPageCount = pageCount
                    ),
                    0L,
                    chapterIndex,
                    percent
                )
            }
        )
        return
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (isPreview) "Preview · ${state.book.title}" else state.book.title)
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (state.book.mediaKind) {
                MediaKind.AUDIO -> AudioReader(
                    file = state.localFile,
                    streamUrl = state.streamUrl,
                    lastKnownPosition = state.lastKnownPosition,
                    onProgress = { position, percent ->
                        readerProgress(state.book, position, 0, percent)
                    }
                )
                MediaKind.PDF -> PdfReaderView(
                    file = state.localFile,
                    initialPage = state.pageIndex,
                    onProgress = { pageIndex, percent ->
                        readerProgress(state.book, 0L, pageIndex, percent)
                    }
                )
                MediaKind.EPUB -> Unit
                MediaKind.COMIC -> ComicReaderView(
                    title = state.book.title,
                    file = state.localFile,
                    initialPage = state.pageIndex,
                    onProgress = { pageIndex, percent ->
                        readerProgress(state.book, 0L, pageIndex, percent)
                    }
                )
                MediaKind.UNKNOWN -> UnsupportedReaderView(
                    title = state.book.title,
                    message = "This file format is not supported yet."
                )
            }
        }
    }
}

internal fun readerKeepsScreenAwake(mediaKind: MediaKind): Boolean = when (mediaKind) {
    MediaKind.EPUB, MediaKind.PDF, MediaKind.COMIC -> true
    MediaKind.AUDIO, MediaKind.UNKNOWN -> false
}

@Composable
internal fun KeepReaderScreenAwake() {
    val view = LocalView.current
    DisposableEffect(view) {
        val wasKeepingScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = wasKeepingScreenOn
        }
    }
}

internal data class EpubReaderSystemBarsPolicy(
    val showStatusBar: Boolean,
    val showNavigationBar: Boolean
)

internal val EPUB_READER_SYSTEM_BARS_POLICY = EpubReaderSystemBarsPolicy(
    showStatusBar = true,
    showNavigationBar = false
)

internal fun EpubReaderTheme.usesDarkStatusBarIcons(): Boolean = this != EpubReaderTheme.Dark

internal const val EPUB_BOOK_LOCATION_TOTAL = 1000

internal data class EpubReaderProgressStatus(
    val completionPercent: Float,
    val chapterNumber: Int,
    val chapterCount: Int,
    val chapterPageNumber: Int,
    val chapterPageCount: Int,
    val bookLocation: Int
)

internal fun epubReaderProgressStatus(
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    pageCount: Int
): EpubReaderProgressStatus {
    val safeChapterCount = chapterCount.coerceAtLeast(1)
    val safeChapterIndex = chapterIndex.coerceIn(0, safeChapterCount - 1)
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    val chapterProgress = (safePageIndex + 1f) / safePageCount
    val bookProgress = ((safeChapterIndex + chapterProgress) / safeChapterCount).coerceIn(0f, 1f)
    return EpubReaderProgressStatus(
        completionPercent = bookProgress * 100f,
        chapterNumber = safeChapterIndex + 1,
        chapterCount = safeChapterCount,
        chapterPageNumber = safePageIndex + 1,
        chapterPageCount = safePageCount,
        bookLocation = (bookProgress * EPUB_BOOK_LOCATION_TOTAL)
            .roundToInt()
            .coerceIn(1, EPUB_BOOK_LOCATION_TOTAL)
    )
}

internal fun EpubReaderProgressStatus.displayText(): String =
    "${completionPercent.roundToInt()}% · Chapter $chapterNumber/$chapterCount · " +
        "Page $chapterPageNumber/$chapterPageCount · Book $bookLocation/$EPUB_BOOK_LOCATION_TOTAL"

internal fun EpubReaderProgressStatus.accessibilityText(): String =
    "Book completion ${completionPercent.roundToInt()} percent; chapter $chapterNumber of $chapterCount; " +
        "chapter page $chapterPageNumber of $chapterPageCount; book location $bookLocation of $EPUB_BOOK_LOCATION_TOTAL"

@Suppress("DEPRECATION")
@Composable
internal fun EpubReaderSystemBars(theme: EpubReaderTheme) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (EPUB_READER_SYSTEM_BARS_POLICY.showStatusBar) {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
        if (EPUB_READER_SYSTEM_BARS_POLICY.showNavigationBar) {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.navigationBars())
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(view, theme) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatusBarColor = window?.statusBarColor
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        window?.statusBarColor = theme.backgroundColor
        controller?.isAppearanceLightStatusBars = theme.usesDarkStatusBarIcons()
        onDispose {
            previousStatusBarColor?.let { window.statusBarColor = it }
            previousLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
        }
    }
}

@Composable
private fun AudioReader(
    file: File?,
    streamUrl: String?,
    lastKnownPosition: Long,
    onProgress: (Long, Float?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    var isPlaying by remember(player) { mutableStateOf(false) }
    var currentPosition by remember(player) { mutableStateOf(lastKnownPosition.coerceAtLeast(0L)) }
    var duration by remember(player) { mutableStateOf(0L) }
    var playbackSpeed by remember(player) { mutableStateOf(1f) }

    DisposableEffect(player, file, streamUrl, lastKnownPosition) {
        val uri = when {
            file != null && file.exists() -> Uri.fromFile(file)
            !streamUrl.isNullOrBlank() -> Uri.parse(streamUrl)
            else -> null
        }
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            if (lastKnownPosition > 0) {
                player.seekTo(lastKnownPosition)
            }
            player.playWhenReady = true
        }
        onDispose {
            player.release()
        }
    }

    val hasTarget = (file != null && file.exists()) || !streamUrl.isNullOrBlank()
    if (!hasTarget) {
        ReaderMessage("Unable to prepare audio playback.")
        return
    }

    LaunchedEffect(player) {
        while (isActive) {
            val playerDuration = player.duration.takeIf { it > 0L } ?: 0L
            val playerPosition = player.currentPosition.coerceAtLeast(0L)
            currentPosition = playerPosition
            duration = playerDuration
            isPlaying = player.isPlaying
            playbackSpeed = player.playbackParameters.speed
            val percent = if (playerDuration > 0L) {
                (playerPosition.toFloat() / playerDuration.toFloat()) * 100f
            } else {
                null
            }
            onProgress(playerPosition, percent)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                }
            }
        )
        Text(
            text = "${formatPlaybackTime(currentPosition)} / ${formatPlaybackTime(duration)}",
            modifier = Modifier.semantics {
                contentDescription = "Playback position ${formatPlaybackTime(currentPosition)} of ${formatPlaybackTime(duration)}"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (isPlaying) "Playing at ${formatPlaybackSpeed(playbackSpeed)}" else "Paused at ${formatPlaybackSpeed(playbackSpeed)}",
            modifier = Modifier.semantics {
                stateDescription = if (isPlaying) {
                    "Playing at ${formatPlaybackSpeed(playbackSpeed)}"
                } else {
                    "Paused at ${formatPlaybackSpeed(playbackSpeed)}"
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val target = (player.currentPosition - 15_000L).coerceAtLeast(0L)
                    player.seekTo(target)
                    currentPosition = target
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Skip back 15 seconds" }
            ) {
                Text("-15s")
            }
            Button(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.play()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = if (isPlaying) "Pause playback" else "Resume playback"
                    }
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            OutlinedButton(
                onClick = {
                    val safeDuration = player.duration.takeIf { it > 0L }
                    val target = (player.currentPosition + 30_000L).let { position ->
                        safeDuration?.let { position.coerceAtMost(it) } ?: position
                    }
                    player.seekTo(target)
                    currentPosition = target
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Skip forward 30 seconds" }
            ) {
                Text("+30s")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AUDIO_SPEED_OPTIONS.forEach { speed ->
                val selected = kotlin.math.abs(playbackSpeed - speed) < 0.01f
                if (selected) {
                    Button(
                        onClick = {
                            player.playbackParameters = PlaybackParameters(speed)
                            playbackSpeed = speed
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Playback speed ${formatPlaybackSpeed(speed)}"
                                stateDescription = "Selected"
                            }
                    ) {
                        Text(formatPlaybackSpeed(speed))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            player.playbackParameters = PlaybackParameters(speed)
                            playbackSpeed = speed
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Playback speed ${formatPlaybackSpeed(speed)}" }
                    ) {
                        Text(formatPlaybackSpeed(speed))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfReaderView(
    file: File?,
    initialPage: Int,
    onProgress: (Int, Float?) -> Unit
) {
    if (file == null || !file.exists()) {
        ReaderMessage("Unable to prepare this PDF.")
        return
    }

    val pageCount = remember(file) { readPdfPageCount(file) }
    var currentPage by remember(file, initialPage) {
        mutableStateOf(initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }
    var zoom by remember(file, currentPage) { mutableStateOf(1f) }
    var offsetX by remember(file, currentPage) { mutableStateOf(0f) }
    var offsetY by remember(file, currentPage) { mutableStateOf(0f) }
    val pageBitmap by produceState<Bitmap?>(initialValue = null, file, currentPage) {
        value = renderPdfPage(file, currentPage)
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    LaunchedEffect(currentPage, pageCount) {
        val percent = if (pageCount > 0) {
            (currentPage.toFloat() / pageCount.toFloat()) * 100f
        } else {
            null
        }
        onProgress(currentPage, percent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Page ${currentPage + 1} of ${pageCount.coerceAtLeast(1)}",
            modifier = Modifier.semantics {
                heading()
                contentDescription = "PDF page ${currentPage + 1} of ${pageCount.coerceAtLeast(1)}"
            }
        )
        if (pageCount <= 0) {
            ReaderMessage("Unable to read pages from this PDF.")
            return@Column
        }
        pageBitmap?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "PDF page",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .transformable(state = transformState)
                        .semantics {
                            contentDescription = "PDF page ${currentPage + 1}"
                            stateDescription = "Zoom ${formatZoomLevel(zoom)}"
                        }
                )
            }
        } ?: Text("Unable to render PDF.")
        Text(
            text = "Zoom ${formatZoomLevel(zoom)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                enabled = currentPage > 0
            ) {
                Text("Previous")
            }
            Button(
                onClick = { currentPage = (currentPage + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) },
                enabled = currentPage < pageCount - 1
            ) {
                Text("Next")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    zoom = (zoom - 0.25f).coerceAtLeast(1f)
                    if (zoom == 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            ) {
                Text("Zoom out")
            }
            OutlinedButton(
                onClick = {
                    zoom = (zoom + 0.25f).coerceAtMost(4f)
                }
            ) {
                Text("Zoom in")
            }
            TextButton(
                onClick = {
                    zoom = 1f
                    offsetX = 0f
                    offsetY = 0f
                }
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun EpubReaderView(
    title: String,
    readerKey: String,
    file: File?,
    initialChapter: Int,
    initialPage: Int,
    initialPercent: Float?,
    isPreview: Boolean,
    onBack: () -> Unit,
    onProgress: (Int, Int, Int, Float?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val epubBook = remember(file) { file?.takeIf(File::exists)?.let { loadEpubBook(context, it) } }
    val paddingStore = remember(context) { EpubReaderPaddingStore(context) }
    val themeStore = remember(context) { EpubReaderThemeStore(context) }
    if (file == null || !file.exists()) {
        ReaderMessage("Unable to prepare this EPUB.")
        return
    }
    if (epubBook == null || epubBook.chapters.isEmpty()) {
        ReaderMessage("Unable to open this EPUB.")
        return
    }

    val chapterCount = epubBook.chapters.size
    val estimatedChapter = remember(chapterCount, initialPercent, isPreview) {
        if (isPreview) 0 else percentToChapterIndex(initialPercent, chapterCount)
    }
    var currentChapter by remember(file, initialChapter, estimatedChapter, isPreview) {
        mutableStateOf(
            when {
                isPreview -> 0
                initialChapter > 0 -> initialChapter.coerceIn(0, chapterCount - 1)
                else -> estimatedChapter
            }
        )
    }
    var selectedTheme by remember(file) { mutableStateOf(themeStore.read()) }
    EpubReaderSystemBars(selectedTheme)
    var fontScale by remember(file) { mutableStateOf(1f) }
    var paddingDraft by remember(file, readerKey) { mutableStateOf(paddingStore.read(readerKey)) }
    var appliedPadding by remember(file, readerKey) { mutableStateOf(paddingStore.read(readerKey)) }
    var showControls by remember(file) { mutableStateOf(false) }
    var showChapterPicker by remember(file) { mutableStateOf(false) }
    var currentPage by remember(file, initialPage) { mutableStateOf(initialPage.coerceAtLeast(0)) }
    var currentPageCount by remember(file) { mutableStateOf(1) }
    var openChapterAtEnd by remember(file) { mutableStateOf(false) }
    val currentChapterState by rememberUpdatedState(epubBook.chapters[currentChapter])
    val currentAppliedPadding by rememberUpdatedState(appliedPadding)
    val applyPadding = { next: EpubPaddingPercentages ->
        paddingDraft = next
        appliedPadding = next
    }
    val dismissControls = {
        showControls = false
        showChapterPicker = false
    }
    BackHandler {
        if (showControls) dismissControls() else onBack()
    }
    val centerTap = rememberUpdatedState {
        if (showControls) dismissControls() else showControls = true
    }
    val pageChanged = rememberUpdatedState { page: Int, count: Int ->
        currentPage = page.coerceAtLeast(0)
        currentPageCount = count.coerceAtLeast(1)
    }
    val chapterBoundary = rememberUpdatedState { direction: Int ->
        when {
            direction < 0 && currentChapter > 0 -> {
                openChapterAtEnd = true
                currentChapter--
            }
            direction > 0 && currentChapter < chapterCount - 1 -> {
                openChapterAtEnd = false
                currentChapter++
            }
            else -> Unit
        }
    }

    val progressStatus = epubReaderProgressStatus(
        chapterIndex = currentChapter,
        chapterCount = chapterCount,
        pageIndex = currentPage,
        pageCount = currentPageCount
    )

    LaunchedEffect(currentChapter, currentPage, currentPageCount, progressStatus.completionPercent) {
        onProgress(currentChapter, currentPage, currentPageCount, progressStatus.completionPercent)
    }

    LaunchedEffect(paddingDraft) {
        delay(180)
        appliedPadding = paddingDraft
        paddingStore.save(readerKey, paddingDraft)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(selectedTheme.backgroundColor))) {
        val topReaderInset = maxHeight * (epubPaddingViewportPercent(appliedPadding.top) / 100f)
        val bottomReaderInset = maxHeight * (epubPaddingViewportPercent(appliedPadding.bottom) / 100f)
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = topReaderInset,
                    bottom = bottomReaderInset + EPUB_READER_PROGRESS_FOOTER_HEIGHT
                )
                .semantics {
                    contentDescription = if (isPreview) {
                        "Preview paginated EPUB reader; progress is not saved"
                    } else {
                        "Paginated EPUB reader"
                    }
                },
            factory = { webContext ->
                WebView(webContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.setSupportZoom(false)
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(
                                epubPaddingUpdateJavascript(currentAppliedPadding.forWebViewContent()),
                                null
                            )
                        }
                    }
                    addJavascriptInterface(
                        EpubReaderBridge(
                            onCenterTap = { centerTap.value.invoke() },
                            onPageChanged = { page, count -> pageChanged.value.invoke(page, count) },
                            onChapterBoundary = { direction -> chapterBoundary.value.invoke(direction) }
                        ),
                        EPUB_READER_BRIDGE
                    )
                }
            },
            update = { webView ->
                val chapter = currentChapterState
                val documentKey = "${chapter.file.absolutePath}|${selectedTheme.name}|$fontScale|$openChapterAtEnd"
                val previousState = webView.tag as? EpubWebViewRenderState
                val nextState = EpubWebViewRenderState(documentKey, appliedPadding)
                if (previousState?.documentKey != documentKey) {
                    val firstRender = previousState == null
                    webView.tag = nextState
                    currentPage = if (firstRender) initialPage.coerceAtLeast(0) else 0
                    currentPageCount = 1
                    val html = chapter.file.readText()
                    webView.setBackgroundColor(selectedTheme.backgroundColor)
                    webView.loadDataWithBaseURL(
                        chapter.file.parentFile?.toURI()?.toString(),
                        styleEpubHtml(
                            html = html,
                            theme = selectedTheme,
                            fontScale = fontScale,
                            topPaddingPercent = 0f,
                            bottomPaddingPercent = 0f,
                            leftPaddingPercent = appliedPadding.left,
                            rightPaddingPercent = appliedPadding.right,
                            initialPage = if (firstRender) initialPage else 0,
                            startAtEnd = openChapterAtEnd
                        ),
                        "text/html",
                        Charsets.UTF_8.name(),
                        null
                    )
                } else if (previousState.padding != appliedPadding) {
                    webView.tag = nextState
                    webView.evaluateJavascript(
                        epubPaddingUpdateJavascript(appliedPadding.forWebViewContent()),
                        null
                    )
                }
            }
        )

        if (showControls) {
            EpubReaderDismissScrim(onDismiss = dismissControls)
            EpubReaderOptionsHeader(
                title = if (isPreview) "Preview · ${epubBook.title ?: title}" else (epubBook.title ?: title),
                status = "Chapter ${currentChapter + 1}/$chapterCount · Page ${currentPage + 1}/$currentPageCount",
                onClose = dismissControls,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        top = 10.dp,
                        end = 12.dp,
                        bottom = 10.dp + EPUB_READER_PROGRESS_FOOTER_HEIGHT
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showChapterPicker) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            epubBook.chapters.forEachIndexed { index, chapter ->
                                FilterChip(
                                    selected = index == currentChapter,
                                    onClick = {
                                        openChapterAtEnd = false
                                        currentChapter = index
                                        showChapterPicker = false
                                    },
                                    label = { Text(chapter.title.ifBlank { "Chapter ${index + 1}" }) }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { showChapterPicker = !showChapterPicker }) {
                                Text("Chapters")
                            }
                            EPUB_THEME_OPTIONS.forEach { theme ->
                                FilterChip(
                                    selected = theme == selectedTheme,
                                    onClick = {
                                        selectedTheme = theme
                                        themeStore.save(theme)
                                    },
                                    label = { Text(theme.label) }
                                )
                            }
                        }
                        Text(
                            "Padding · 100% equals 25% of the screen edge",
                            style = MaterialTheme.typography.labelMedium
                        )
                        EpubPaddingSlider(
                            label = "Top",
                            value = paddingDraft.top,
                            onValueChange = { value -> applyPadding(paddingDraft.copy(top = value)) },
                            onValueChangeFinished = { paddingStore.save(readerKey, paddingDraft) }
                        )
                        EpubPaddingSlider(
                            label = "Bottom",
                            value = paddingDraft.bottom,
                            onValueChange = { value -> applyPadding(paddingDraft.copy(bottom = value)) },
                            onValueChangeFinished = { paddingStore.save(readerKey, paddingDraft) }
                        )
                        EpubPaddingSlider(
                            label = "Left",
                            value = paddingDraft.left,
                            onValueChange = { value -> applyPadding(paddingDraft.copy(left = value)) },
                            onValueChangeFinished = { paddingStore.save(readerKey, paddingDraft) }
                        )
                        EpubPaddingSlider(
                            label = "Right",
                            value = paddingDraft.right,
                            onValueChange = { value -> applyPadding(paddingDraft.copy(right = value)) },
                            onValueChangeFinished = { paddingStore.save(readerKey, paddingDraft) }
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { fontScale = (fontScale - 0.1f).coerceAtLeast(0.9f) }) {
                                Text("A-")
                            }
                            Text(formatEpubFontScale(fontScale), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { fontScale = (fontScale + 0.1f).coerceAtMost(1.5f) }) {
                                Text("A+")
                            }
                        }
                    }
                }
            }
        }
        EpubReaderProgressFooter(
            status = progressStatus,
            theme = selectedTheme,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        )
    }
}

@Composable
internal fun EpubReaderProgressFooter(
    status: EpubReaderProgressStatus,
    theme: EpubReaderTheme,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(EPUB_READER_PROGRESS_FOOTER_HEIGHT),
        color = Color(theme.backgroundColor),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = status.displayText(),
                modifier = Modifier.semantics {
                    contentDescription = status.accessibilityText()
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(android.graphics.Color.parseColor(theme.foregroundCss)).copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun EpubReaderDismissScrim(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .semantics { contentDescription = "Close reader options" }
    )
}

@Composable
internal fun EpubReaderOptionsHeader(
    title: String,
    status: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

@Composable
private fun EpubPaddingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "$label ${value.roundToInt()}%",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private class EpubReaderBridge(
    private val onCenterTap: () -> Unit,
    private val onPageChanged: (Int, Int) -> Unit,
    private val onChapterBoundary: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun centerTap() {
        mainHandler.post(onCenterTap)
    }

    @JavascriptInterface
    fun pageChanged(page: Int, count: Int) {
        mainHandler.post { onPageChanged(page, count) }
    }

    @JavascriptInterface
    fun chapterBoundary(direction: Int) {
        mainHandler.post { onChapterBoundary(direction) }
    }
}

@Composable
private fun ComicReaderView(
    title: String,
    file: File?,
    initialPage: Int,
    onProgress: (Int, Float?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val comicPages = remember(file) { file?.takeIf(File::exists)?.let { loadComicPages(context, it) } }
    if (file == null || !file.exists()) {
        ReaderMessage("Unable to prepare this comic.")
        return
    }
    if (comicPages.isNullOrEmpty()) {
        ReaderMessage("Unable to open this comic.")
        return
    }

    val pageCount = comicPages.size
    var currentPage by remember(file, initialPage) {
        mutableStateOf(initialPage.coerceIn(0, pageCount - 1))
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, file, currentPage) {
        value = runCatching {
            BitmapFactory.decodeFile(comicPages[currentPage].absolutePath)
        }.getOrNull()
    }

    LaunchedEffect(currentPage, pageCount) {
        val percent = if (pageCount <= 1) 100f else (currentPage.toFloat() / (pageCount - 1).toFloat()) * 100f
        onProgress(currentPage, percent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            "Page ${currentPage + 1} of $pageCount",
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Comic page ${currentPage + 1} of $pageCount"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Comic page ${currentPage + 1}",
                    modifier = Modifier.fillMaxWidth()
                )
            } ?: Text("Unable to render this comic page.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                enabled = currentPage > 0
            ) {
                Text("Previous")
            }
            Button(
                onClick = { currentPage = (currentPage + 1).coerceAtMost(pageCount - 1) },
                enabled = currentPage < pageCount - 1
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun UnsupportedReaderView(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ReaderMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun bookStatus(book: BookSummary, isOfflineSnapshot: Boolean): String {
    val parts = mutableListOf(book.mediaKind.name.lowercase(Locale.US))
    book.progressLabel?.takeIf { it.isNotBlank() }?.let(parts::add)
    if (book.isDownloaded) {
        parts += "downloaded"
    } else if (isOfflineSnapshot) {
        parts += "not available offline"
    }
    return parts.joinToString(" | ")
}

private fun readPdfPageCount(file: File): Int {
    return runCatching {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                renderer.pageCount
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }.getOrDefault(0)
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap? {
    return runCatching {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            try {
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount.coerceAtLeast(1) - 1)
                val page = renderer.openPage(safeIndex)
                try {
                    Bitmap.createBitmap(
                        page.width.coerceAtLeast(1),
                        page.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    ).also { bitmap ->
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        } finally {
            descriptor.close()
        }
    }.getOrNull()
}

private fun loadComicPages(context: android.content.Context, file: File): List<File> {
    val extension = file.extension.lowercase(Locale.US)
    if (extension != "cbz") {
        return emptyList()
    }

    val targetDir = File(context.cacheDir, "comic-pages/${file.nameWithoutExtension}").apply {
        mkdirs()
    }
    val existingPages = targetDir.listFiles()
        ?.filter { it.isFile && it.extension.lowercase(Locale.US) in COMIC_IMAGE_EXTENSIONS }
        ?.sortedBy { it.name.lowercase(Locale.US) }
        .orEmpty()
    if (existingPages.isNotEmpty()) {
        return existingPages
    }

    return runCatching {
        ZipFile(file).use { zip ->
            val imageEntries = mutableListOf<java.util.zip.ZipEntry>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }
                val imageExtension = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
                if (imageExtension in COMIC_IMAGE_EXTENSIONS) {
                    imageEntries += entry
                }
            }
            imageEntries
                .sortedBy { it.name.lowercase(Locale.US) }
                .mapIndexedNotNull { index, entry ->
                    val imageExtension = entry.name.substringAfterLast('.', "jpg").lowercase(Locale.US)
                    val extracted = File(targetDir, "${index.toString().padStart(4, '0')}.$imageExtension")
                    zip.getInputStream(entry).use { input ->
                        extracted.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    extracted.takeIf(File::exists)
                }
        }
    }.getOrDefault(emptyList())
}

internal fun normalizeServerUrl(value: String): String? {
    val raw = value.trim()
    if (raw.isBlank()) {
        return null
    }
    val explicitScheme = raw.substringBefore("://", missingDelimiterValue = "")
        .takeIf { raw.contains("://") }
    if (explicitScheme != null && !explicitScheme.equals("http", ignoreCase = true) && !explicitScheme.equals("https", ignoreCase = true)) {
        return null
    }
    if (explicitScheme == null && Regex("^[A-Za-z][A-Za-z0-9+.-]*:(?![0-9])").containsMatchIn(raw)) {
        return null
    }
    val prefixed = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
        raw
    } else {
        val bareHost = runCatching { URI("http://$raw").host }.getOrNull() ?: return null
        val defaultScheme = if (isAllowedCleartextHost(bareHost)) "http" else "https"
        "$defaultScheme://$raw"
    }
    val uri = runCatching { URI(prefixed) }.getOrNull() ?: return null
    val scheme = uri.scheme ?: return null
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        return null
    }
    val host = uri.host ?: return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    val path = uri.path?.takeIf { it.isNotBlank() && it != "/" }?.trimEnd('/') ?: ""
    return "${scheme.lowercase(Locale.US)}://$host$port$path"
}

internal fun invalidServerUrlMessage(): String {
    return "Enter a valid HTTP or HTTPS server URL."
}

internal fun isAllowedCleartextHost(host: String): Boolean {
    return host.equals("localhost", ignoreCase = true) ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        host == "10.0.3.2"
}

internal fun percentToChapterIndex(percent: Float?, chapterCount: Int): Int {
    if (chapterCount <= 1) {
        return 0
    }
    val raw = percent ?: return 0
    val normalized = raw / 100f
    return (normalized.coerceIn(0f, 1f) * (chapterCount - 1)).roundToInt()
}

private fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        String.format(Locale.US, "%.0fx", speed)
    } else {
        String.format(Locale.US, "%.1fx", speed)
    }
}

private fun formatZoomLevel(zoom: Float): String {
    return String.format(Locale.US, "%.0f%%", zoom * 100f)
}

internal fun styleEpubHtml(
    html: String,
    theme: EpubReaderTheme,
    fontScale: Float,
    startAtEnd: Boolean,
    topPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    bottomPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    leftPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    rightPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    initialPage: Int = 0
): String {
    val fontPercent = (fontScale * 100f).roundToInt()
    val topInset = epubPaddingViewportPercent(topPaddingPercent)
    val bottomInset = epubPaddingViewportPercent(bottomPaddingPercent)
    val leftInset = epubPaddingViewportPercent(leftPaddingPercent)
    val rightInset = epubPaddingViewportPercent(rightPaddingPercent)
    val pageInsetHeight = topInset + bottomInset
    val pageInsetWidth = leftInset + rightInset
    val readerAssets = """
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
        :root {
            color-scheme: light;
            --bookorbit-reader-page-height: calc(100vh - ${formatEpubCssPercent(pageInsetHeight)}vh);
        }
        html, body {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            overflow: hidden;
            background: ${theme.backgroundCss};
            color: ${theme.foregroundCss};
            font-size: ${fontPercent}%;
            line-height: 1.7;
        }
        #bookorbit-page-strip {
            position: absolute;
            top: ${formatEpubCssPercent(topInset)}vh;
            left: ${formatEpubCssPercent(leftInset)}vw;
            box-sizing: border-box;
            width: calc(100vw - ${formatEpubCssPercent(pageInsetWidth)}vw);
            height: calc(100vh - ${formatEpubCssPercent(pageInsetHeight)}vh);
            min-height: calc(100vh - ${formatEpubCssPercent(pageInsetHeight)}vh);
            overflow: visible;
            word-wrap: break-word;
            will-change: transform;
        }
        img, svg {
            max-width: 100%;
            max-height: var(--bookorbit-reader-page-height);
            height: auto;
            break-inside: avoid;
        }
        a { color: ${theme.linkCss}; }
        pre, code {
            white-space: pre-wrap;
        }
        </style>
        <script>
        (() => {
          let page = 0;
          let strip = null;
          let initialized = false;
          let repaginationToken = 0;
          let insets = {
            top: ${formatEpubCssPercent(topInset)},
            bottom: ${formatEpubCssPercent(bottomInset)},
            left: ${formatEpubCssPercent(leftInset)},
            right: ${formatEpubCssPercent(rightInset)}
          };
          let pinToEnd = ${startAtEnd.toString()};
          const initialPage = ${initialPage.coerceAtLeast(0)};
          const bridge = window.$EPUB_READER_BRIDGE;
          const normalizeInset = (value) => Math.min(25, Math.max(0, Number(value) || 0));
          const pageHeight = () => Math.max(
            1,
            window.innerHeight * (1 - (insets.top + insets.bottom) / 100)
          );
          const applyPageGeometry = () => {
            const rootStyle = document.documentElement.style;
            rootStyle.setProperty(
              '--bookorbit-reader-page-height',
              `calc(100vh - ${'$'}{(insets.top + insets.bottom).toFixed(2)}vh)`
            );
            if (!strip) return;
            strip.style.top = `${'$'}{insets.top.toFixed(2)}vh`;
            strip.style.left = `${'$'}{insets.left.toFixed(2)}vw`;
            strip.style.width = `calc(100vw - ${'$'}{(insets.left + insets.right).toFixed(2)}vw)`;
            strip.style.height = `calc(100vh - ${'$'}{(insets.top + insets.bottom).toFixed(2)}vh)`;
            strip.style.minHeight = strip.style.height;
          };
          const pageCount = () => strip
            ? Math.max(1, Math.ceil(strip.scrollHeight / pageHeight()))
            : 1;
          const publish = () => bridge.pageChanged(page, pageCount());
          const renderPage = () => {
            if (!strip) return;
            strip.style.transform = `translate3d(0, ${'$'}{-page * pageHeight()}px, 0)`;
          };
          const moveTo = (next) => {
            pinToEnd = false;
            const count = pageCount();
            if (next < 0) return bridge.chapterBoundary(-1);
            if (next >= count) return bridge.chapterBoundary(1);
            page = next;
            renderPage();
            publish();
          };
          const repaginateFromOffset = (contentOffset) => {
            const token = ++repaginationToken;
            requestAnimationFrame(() => requestAnimationFrame(() => {
              if (token !== repaginationToken) return;
              const count = pageCount();
              page = pinToEnd
                ? count - 1
                : Math.min(Math.max(0, Math.floor(contentOffset / pageHeight())), count - 1);
              renderPage();
              publish();
            }));
          };
          const repaginate = () => {
            if (!initialized) return;
            const contentOffset = page * pageHeight();
            repaginateFromOffset(contentOffset);
          };
          const setInsets = (top, bottom, left, right) => {
            const next = {
              top: normalizeInset(top),
              bottom: normalizeInset(bottom),
              left: normalizeInset(left),
              right: normalizeInset(right)
            };
            if (
              next.top === insets.top &&
              next.bottom === insets.bottom &&
              next.left === insets.left &&
              next.right === insets.right
            ) return;
            const contentOffset = page * pageHeight();
            insets = next;
            applyPageGeometry();
            if (strip && initialized) repaginateFromOffset(contentOffset);
          };
          window.BookOrbitReaderLayout = Object.freeze({ setInsets, refresh: repaginate });
          window.addEventListener('load', () => {
            strip = document.createElement('main');
            strip.id = 'bookorbit-page-strip';
            Array.from(document.body.childNodes).forEach((node) => strip.appendChild(node));
            document.body.appendChild(strip);
            applyPageGeometry();
            requestAnimationFrame(() => requestAnimationFrame(() => {
              page = pinToEnd ? pageCount() - 1 : Math.min(initialPage, pageCount() - 1);
              initialized = true;
              renderPage();
              publish();
            }));
            if (document.fonts && document.fonts.ready) {
              document.fonts.ready.then(repaginate);
            }
            Array.from(strip.querySelectorAll('img')).forEach((image) => {
              if (!image.complete) image.addEventListener('load', repaginate, { once: true });
            });
          });
          window.addEventListener('resize', repaginate);
          let touchStartX = 0;
          let touchStartY = 0;
          let suppressClick = false;
          document.addEventListener('touchstart', (event) => {
            const touch = event.changedTouches && event.changedTouches[0];
            if (!touch) return;
            touchStartX = touch.clientX;
            touchStartY = touch.clientY;
          }, { passive: true });
          document.addEventListener('touchend', (event) => {
            const touch = event.changedTouches && event.changedTouches[0];
            if (!touch) return;
            const deltaX = touch.clientX - touchStartX;
            const deltaY = touch.clientY - touchStartY;
            if (Math.abs(deltaX) < 48 || Math.abs(deltaX) < Math.abs(deltaY) * 1.2) return;
            event.preventDefault();
            suppressClick = true;
            moveTo(deltaX < 0 ? page + 1 : page - 1);
            window.setTimeout(() => { suppressClick = false; }, 350);
          }, { passive: false });
          document.addEventListener('click', (event) => {
            if (suppressClick) return;
            if (event.target.closest && event.target.closest('a')) return;
            event.preventDefault();
            const zone = event.clientX / window.innerWidth;
            if (zone < 0.25) return moveTo(page - 1);
            if (zone > 0.75) return moveTo(page + 1);
            bridge.centerTap();
          }, true);
        })();
        </script>
    """.trimIndent()
    return if (html.contains("</head>", ignoreCase = true)) {
        html.replaceFirst("</head>", "$readerAssets</head>", ignoreCase = true)
    } else {
        """
        <html>
          <head>$readerAssets</head>
          <body>$html</body>
        </html>
        """.trimIndent()
    }
}

internal fun epubPaddingUpdateJavascript(padding: EpubPaddingPercentages): String {
    val topInset = epubPaddingViewportPercent(padding.top)
    val bottomInset = epubPaddingViewportPercent(padding.bottom)
    val leftInset = epubPaddingViewportPercent(padding.left)
    val rightInset = epubPaddingViewportPercent(padding.right)
    return "if (window.BookOrbitReaderLayout) { " +
        "window.BookOrbitReaderLayout.setInsets(" +
        "${formatEpubCssPercent(topInset)}, ${formatEpubCssPercent(bottomInset)}, " +
        "${formatEpubCssPercent(leftInset)}, ${formatEpubCssPercent(rightInset)}); " +
        "window.BookOrbitReaderLayout.refresh(); }"
}

private fun formatEpubFontScale(fontScale: Float): String {
    return String.format(Locale.US, "%.0f%%", fontScale * 100f)
}

private fun formatEpubCssPercent(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

internal fun epubPaddingViewportPercent(value: Float): Float {
    return value.coerceIn(0f, 100f) / 4f
}

internal data class EpubPaddingPercentages(
    val top: Float = EPUB_DEFAULT_PADDING_PERCENT,
    val bottom: Float = EPUB_DEFAULT_PADDING_PERCENT,
    val left: Float = EPUB_DEFAULT_PADDING_PERCENT,
    val right: Float = EPUB_DEFAULT_PADDING_PERCENT
)

internal fun EpubPaddingPercentages.forWebViewContent(): EpubPaddingPercentages {
    return copy(top = 0f, bottom = 0f)
}

private data class EpubWebViewRenderState(
    val documentKey: String,
    val padding: EpubPaddingPercentages
)

internal const val EPUB_DEFAULT_PADDING_PERCENT = 15f
private val EPUB_READER_PROGRESS_FOOTER_HEIGHT = 30.dp

private val AUDIO_SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f)
private val COMIC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
private const val EPUB_READER_BRIDGE = "BookOrbitReader"
private val EPUB_THEME_OPTIONS = listOf(
    EpubReaderTheme.Light,
    EpubReaderTheme.Sepia,
    EpubReaderTheme.Dark
)

internal enum class EpubReaderTheme(
    val label: String,
    val backgroundColor: Int,
    val backgroundCss: String,
    val foregroundCss: String,
    val linkCss: String
) {
    Light(
        label = "Light",
        backgroundColor = 0xFFF7F4EE.toInt(),
        backgroundCss = "#F7F4EE",
        foregroundCss = "#1F1C17",
        linkCss = "#2457A6"
    ),
    Sepia(
        label = "Sepia",
        backgroundColor = 0xFFF1E6D2.toInt(),
        backgroundCss = "#F1E6D2",
        foregroundCss = "#3B2E1E",
        linkCss = "#7A4B00"
    ),
    Dark(
        label = "Dark",
        backgroundColor = 0xFF181512.toInt(),
        backgroundCss = "#181512",
        foregroundCss = "#ECE4D8",
        linkCss = "#8DB5FF"
    )
}
