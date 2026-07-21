package com.bookorbit.android

import android.Manifest
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferences
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
fun BookOrbitApp(
    screen: AppScreen,
    coordinator: AppCoordinator,
    audioPlaybackController: ReadiumAudioPlaybackController? = null,
    appPreferences: AppPreferences = AppPreferences(),
    onAppPreferencesChange: (AppPreferences) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BookOrbitDestination(
            screen = screen,
            coordinator = coordinator,
            audioPlaybackController = audioPlaybackController,
            appPreferences = appPreferences,
            onAppPreferencesChange = onAppPreferencesChange
        )
        if (screen !is AppScreen.Browser) audioPlaybackController?.let { controller ->
            ReadiumCompactAudioPlayer(
                controller = controller,
                onClosed = coordinator::onAudioPlaybackClosed,
                onCoverClick = controller::requestBookDetail,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun BookOrbitDestination(
    screen: AppScreen,
    coordinator: AppCoordinator,
    audioPlaybackController: ReadiumAudioPlaybackController?,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
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
            onChangeServer = coordinator::changeServer,
            onLibrarySelected = coordinator::selectLibrary,
            searchBooks = coordinator::searchBooks,
            localBooksLoader = coordinator::loadLocalBooks,
            libraryBooksPageLoader = coordinator::loadLibraryBooksPage,
            coverLoader = coordinator::loadBookCover,
            bookDetailLoader = coordinator::loadBookDetail,
            seriesDetailLoader = coordinator::loadSeriesDetail,
            seriesCatalogLoader = coordinator::loadSeriesCatalog,
            authorsCatalogLoader = coordinator::loadAuthorsCatalog,
            authorBooksLoader = coordinator::loadAuthorBooks,
            achievementsLoader = coordinator::loadAchievements,
            catalogImageLoader = coordinator::loadCatalogImage,
            onBookOpen = coordinator::openBook,
            onPreview = coordinator::previewBook,
            onDownload = coordinator::downloadBook,
            onCancelDownload = coordinator::cancelDownload,
            onDeleteLocalCopy = coordinator::deleteLocalCopy,
            onDismissMessage = coordinator::dismissBrowserMessage,
            onRemoveFromCurrentlyReading = coordinator::removeFromCurrentlyReading,
            onMarkAsRead = coordinator::markBookAsRead,
            onMarkAsUnread = coordinator::markBookAsUnread,
            appPreferences = appPreferences,
            onAppPreferencesChange = onAppPreferencesChange,
            storageUsageLoader = coordinator::loadStorageUsage,
            onClearCache = coordinator::clearAppCache,
            bookDetailRequest = audioPlaybackController?.bookDetailRequest?.collectAsState()?.value,
            onBookDetailRequestConsumed = { sequence ->
                audioPlaybackController?.consumeBookDetailRequest(sequence)
            },
            bottomOverlay = audioPlaybackController?.let { controller ->
                {
                    ReadiumCompactAudioPlayer(
                        controller = controller,
                        onClosed = coordinator::onAudioPlaybackClosed,
                        onCoverClick = controller::requestBookDetail
                    )
                }
            }
        )
        is AppScreen.ReaderLoading -> ReaderLoadingScreen(
            book = screen.book,
            launchMode = screen.launchMode,
            onBack = coordinator::closeReader
        )
        is AppScreen.Reader -> ReaderScreen(
            state = screen.readerState,
            onBack = coordinator::closeReader,
            onProgress = coordinator::onProgress,
            comicPageLoader = coordinator::loadCatalogImage,
            audioPlaybackController = audioPlaybackController,
            onAudioReady = coordinator::minimizeAudioReader,
            onAudioFailure = coordinator::onAudioPlaybackFailed
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
                .verticalScroll(rememberScrollState())
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
    onProgress: (BookSummary, Long, Int, Float?) -> Unit,
    comicPageLoader: suspend (String) -> ByteArray?,
    audioPlaybackController: ReadiumAudioPlaybackController?,
    onAudioReady: () -> Unit,
    onAudioFailure: (BookSummary, String) -> Unit
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
    if (shouldUseReadiumEpubReader(state.book.mediaKind)) {
        val readerFile = state.localFile
        if (readerFile == null || !readerFile.exists()) {
            ReaderMessage("Unable to prepare this EPUB.")
        } else {
            ReadiumEpubReaderLauncher(
                file = readerFile,
                title = state.book.title,
                readerKey = listOf(state.book.id, state.book.fileId.orEmpty()).joinToString("|"),
                launchMode = state.launchMode,
                initialChapter = if (isPreview) 0 else state.pageIndex,
                initialPage = if (isPreview) 0 else state.readerPageIndex,
                initialPageCount = if (isPreview) 1 else state.book.readerPageCount ?: 1,
                initialPercent = if (isPreview) null else state.progressPercent,
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
                },
                onFinished = onBack
            )
        }
        return
    }
    if (shouldUseReadiumComicReader(state.book, state.localFile, state.comicPagesUrl)) {
        ReadiumComicReaderLauncher(
            book = state.book,
            file = state.localFile,
            pagesUrl = state.comicPagesUrl,
            pageLoader = comicPageLoader,
            title = state.book.title,
            readerKey = listOf(state.book.id, state.book.fileId.orEmpty()).joinToString("|"),
            launchMode = state.launchMode,
            initialPage = if (isPreview) 0 else state.pageIndex,
            onProgress = { pageIndex, _, percent ->
                readerProgress(state.book, 0L, pageIndex, percent)
            },
            onFinished = onBack
        )
        return
    }
    if (state.book.mediaKind == MediaKind.COMIC) {
        ReaderMessage(
            "This comic container must be converted to CBZ before Readium can open it. " +
                "Reconnect to BookOrbit to prepare it."
        )
        return
    }
    if (shouldUseReadiumPdfReader(state.book.mediaKind, state.localFile)) {
        ReadiumPdfReaderLauncher(
            file = requireNotNull(state.localFile),
            title = state.book.title,
            readerKey = listOf(state.book.id, state.book.fileId.orEmpty()).joinToString("|"),
            launchMode = state.launchMode,
            initialPage = if (isPreview) 0 else state.pageIndex,
            onProgress = { pageIndex, _, percent ->
                readerProgress(state.book, 0L, pageIndex, percent)
            },
            onFinished = onBack
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
                    state = state,
                    controller = audioPlaybackController,
                    onReady = onAudioReady,
                    onFailure = onAudioFailure
                )
                MediaKind.PDF -> UnsupportedReaderView(
                    title = state.book.title,
                    message = "Unable to prepare this PDF for Readium."
                )
                MediaKind.EPUB -> Unit
                MediaKind.COMIC -> Unit
                MediaKind.UNKNOWN -> UnsupportedReaderView(
                    title = state.book.title,
                    message = "This file format is not supported."
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

internal data class EpubReaderProgressStatus(
    val completionPercent: Float,
    val chapterNumber: Int,
    val chapterCount: Int,
    val chapterPageNumber: Int,
    val chapterPageCount: Int,
    val bookPageNumber: Int?,
    val bookPageCount: Int?
)

internal fun epubReaderProgressStatus(
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    pageCount: Int,
    chapterPageCounts: List<Int>? = null
): EpubReaderProgressStatus {
    val safeChapterCount = chapterCount.coerceAtLeast(1)
    val safeChapterIndex = chapterIndex.coerceIn(0, safeChapterCount - 1)
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    val measuredPageCounts = chapterPageCounts
        ?.takeIf { it.size == safeChapterCount }
        ?.map { it.coerceAtLeast(1) }
        ?.toMutableList()
        ?.also { it[safeChapterIndex] = safePageCount }
    val bookPageCount = measuredPageCounts?.sum()
    val bookPageNumber = measuredPageCounts?.let { counts ->
        (counts.take(safeChapterIndex).sum() + safePageIndex + 1)
            .coerceIn(1, bookPageCount ?: 1)
    }
    val chapterProgress = (safePageIndex + 1f) / safePageCount
    val bookProgress = if (bookPageNumber != null && bookPageCount != null) {
        bookPageNumber.toFloat() / bookPageCount
    } else {
        (safeChapterIndex + chapterProgress) / safeChapterCount
    }.coerceIn(0f, 1f)
    return EpubReaderProgressStatus(
        completionPercent = bookProgress * 100f,
        chapterNumber = safeChapterIndex + 1,
        chapterCount = safeChapterCount,
        chapterPageNumber = safePageIndex + 1,
        chapterPageCount = safePageCount,
        bookPageNumber = bookPageNumber,
        bookPageCount = bookPageCount
    )
}

internal fun EpubReaderProgressStatus.displayText(): String =
    "${completionPercent.roundToInt()}% · Chapter $chapterNumber/$chapterCount · " +
        "Page $chapterPageNumber/$chapterPageCount · " +
        if (bookPageNumber != null && bookPageCount != null) {
            "Book $bookPageNumber/$bookPageCount"
        } else {
            "Book pages calculating…"
        }

internal fun EpubReaderProgressStatus.accessibilityText(): String =
    "Book completion ${completionPercent.roundToInt()} percent; chapter $chapterNumber of $chapterCount; " +
        "chapter page $chapterPageNumber of $chapterPageCount; " +
        if (bookPageNumber != null && bookPageCount != null) {
            "book page $bookPageNumber of $bookPageCount"
        } else {
            "whole-book pages are calculating"
        }

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
    state: ReaderState,
    controller: ReadiumAudioPlaybackController?,
    onReady: () -> Unit,
    onFailure: (BookSummary, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var errorMessage by remember(state.book.id, state.book.fileId, state.launchMode) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(state.book.id, state.book.fileId, state.launchMode, state.localFile) {
        val audioController = controller
        val file = state.localFile?.takeIf { it.isFile }
        if (audioController == null || file == null) {
            val message = "Unable to prepare this audiobook for Readium playback."
            errorMessage = message
            onFailure(state.book, message)
            return@LaunchedEffect
        }
        val result = try {
            withTimeoutOrNull(AUDIO_PREPARATION_TIMEOUT_MILLIS) {
                audioController.open(
                    book = state.book,
                    file = file,
                    initialPositionMs = state.lastKnownPosition,
                    launchMode = state.launchMode
                )
            } ?: ReadiumAudioOpenResult.Error(
                "Audiobook preparation timed out. Try opening it again."
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e("ReadiumAudio", "Audiobook preparation failed for ${state.book.id}", error)
            ReadiumAudioOpenResult.Error("Audiobook preparation failed safely.")
        }
        when (result) {
            is ReadiumAudioOpenResult.Error -> errorMessage = result.message
            is ReadiumAudioOpenResult.Opened -> {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    (context as? Activity)?.let { activity ->
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            AUDIO_NOTIFICATION_PERMISSION_REQUEST
                        )
                    }
                }
                onReady()
            }
        }
        if (result is ReadiumAudioOpenResult.Error) {
            withTimeoutOrNull(AUDIO_CLOSE_TIMEOUT_MILLIS) {
                runCatching { audioController.close() }
            }
            onFailure(state.book, result.message)
        }
    }
    if (errorMessage != null) {
        ReaderMessage(requireNotNull(errorMessage))
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingRow("Preparing audiobook with Readium…")
        }
    }
}

@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
@Composable
internal fun ReadiumCompactAudioPlayer(
    controller: ReadiumAudioPlaybackController,
    onClosed: (BookSummary, ReaderLaunchMode) -> Unit = { _, _ -> },
    onCoverClick: (BookSummary) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val session by produceState<ReadiumAudioPlaybackService.Session?>(null, controller) {
        controller.session().collect { value = it }
    }
    val current = session ?: return
    val playback by current.navigator.playback.collectAsState()
    val settings by current.navigator.settings.collectAsState()
    val positionMs = audioPlaybackPositionMs(current.navigator)
    val durationMs = current.navigator.readingOrder.duration
        ?.inWholeMilliseconds
        ?.takeIf { it > 0L }
    val scope = rememberCoroutineScope()
    var chapterMenuExpanded by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var speedMenuExpanded by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var isSeeking by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var seekPositionMs by remember(current.book.id, current.book.fileId) {
        mutableStateOf(positionMs.toFloat())
    }
    LaunchedEffect(positionMs, isSeeking) {
        if (!isSeeking) seekPositionMs = positionMs.toFloat()
    }
    val chapters = current.book.audioChapters
    val activeChapterIndex = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val remainingMs = durationMs
        ?.minus(if (isSeeking) seekPositionMs.toLong() else positionMs)
        ?.coerceAtLeast(0L)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "Audiobook player for ${current.book.title}"
                stateDescription = if (playback.playWhenReady) "Playing" else "Paused"
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactAudiobookCover(
                book = current.book,
                coverLoader = controller::loadCover,
                onClick = { onCoverClick(current.book) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            current.book.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            current.book.author.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                controller.close()
                                onClosed(current.book, current.launchMode)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close audiobook player")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatPlaybackTime(if (isSeeking) seekPositionMs.toLong() else positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = seekPositionMs.coerceIn(0f, (durationMs ?: 1L).toFloat()),
                        onValueChange = {
                            isSeeking = true
                            seekPositionMs = it
                        },
                        onValueChangeFinished = {
                            seekAudioTo(current.navigator, seekPositionMs.toLong())
                            isSeeking = false
                        },
                        valueRange = 0f..(durationMs ?: 1L).toFloat(),
                        enabled = durationMs != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .padding(horizontal = 4.dp)
                    )
                    Text(
                        remainingMs?.let { "−${formatPlaybackTime(it)}" } ?: "−–:––",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { chapterMenuExpanded = true },
                            enabled = chapters.isNotEmpty(),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = if (chapters.isEmpty()) {
                                    "No audiobook chapters available"
                                } else {
                                    "Select audiobook chapter"
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = chapterMenuExpanded,
                            onDismissRequest = { chapterMenuExpanded = false }
                        ) {
                            chapters.forEachIndexed { index, chapter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            chapter.title.ifBlank { "Chapter ${index + 1}" },
                                            fontWeight = if (index == activeChapterIndex) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    },
                                    onClick = {
                                        chapterMenuExpanded = false
                                        seekAudioTo(current.navigator, chapter.startMs)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { current.navigator.skip((-10).seconds) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Skip back 10 seconds")
                    }
                    IconButton(
                        onClick = {
                            if (playback.playWhenReady) current.navigator.pause() else current.navigator.play()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playback.playWhenReady) "Pause audiobook" else "Play audiobook"
                        )
                    }
                    IconButton(
                        onClick = { current.navigator.skip(30.seconds) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Forward30, contentDescription = "Skip forward 30 seconds")
                    }
                    Box {
                        TextButton(
                            onClick = { speedMenuExpanded = true },
                            modifier = Modifier
                                .height(40.dp)
                                .semantics { contentDescription = "Select playback speed" },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("${formatPlaybackSpeed(settings.speed)}×")
                        }
                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false }
                        ) {
                            listOf(0.75, 1.0, 1.25, 1.5, 2.0).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${formatPlaybackSpeed(speed)}×") },
                                    onClick = {
                                        speedMenuExpanded = false
                                        current.navigator.submitPreferences(ExoPlayerPreferences(speed = speed))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackSpeed(speed: Double): String =
    if (speed % 1.0 == 0.0) speed.toInt().toString() else speed.toString()

@Composable
private fun CompactAudiobookCover(
    book: BookSummary,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onClick: () -> Unit = {}
) {
    val bitmap by produceState<Bitmap?>(null, book.id, book.coverUrl, book.updatedAtMillis) {
        value = runCatching { coverLoader(book) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Box(
        modifier = Modifier
            .height(72.dp)
            .aspectRatio(book.coverAspectRatio.widthToHeight)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open details for ${book.title}" },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap).asImageBitmap(),
                contentDescription = "Cover for ${book.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                book.title.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleLarge
            )
        }
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
    backgroundAlpha: Float = 0.32f,
    contentDescription: String = "Dismiss reader options and continue reading",
    onDismiss: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .semantics { this.contentDescription = contentDescription }
    )
}

internal data class ReaderChromePositionState(
    val currentIndex: Int,
    val itemCount: Int,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean
)

internal fun readerChromePositionState(currentIndex: Int, itemCount: Int): ReaderChromePositionState {
    val safeCount = itemCount.coerceAtLeast(1)
    val safeIndex = currentIndex.coerceIn(0, safeCount - 1)
    return ReaderChromePositionState(
        currentIndex = safeIndex,
        itemCount = safeCount,
        canGoPrevious = safeIndex > 0,
        canGoNext = safeIndex < safeCount - 1
    )
}

internal const val READER_TAP_ZONE_TUTORIAL_DURATION_MILLIS = 3_000L
internal const val READER_TAP_ZONE_TUTORIAL_LABEL_FONT_SIZE_SP = 28
internal const val READER_POSITION_CONTROL_HEIGHT_FRACTION = 0.75f

internal data class ReaderTapZoneTutorialRegion(
    val label: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Float,
    val widthWeight: Float = 1f
)

internal val READER_TAP_ZONE_TUTORIAL_REGIONS = listOf(
    ReaderTapZoneTutorialRegion("Previous", red = 255, green = 114, blue = 118, alpha = 0.5f),
    ReaderTapZoneTutorialRegion("Menu", red = 0, green = 0, blue = 0, alpha = 0.5f),
    ReaderTapZoneTutorialRegion("Next", red = 144, green = 238, blue = 144, alpha = 0.5f)
)

@Composable
internal fun ReaderTapZoneTutorial(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSources = remember {
        List(READER_TAP_ZONE_TUTORIAL_REGIONS.size) { MutableInteractionSource() }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .testTag("reader-tap-zone-tutorial")
                .semantics { contentDescription = "Reader tap regions tutorial" }
        ) {
            READER_TAP_ZONE_TUTORIAL_REGIONS.forEachIndexed { index, region ->
                Box(
                    modifier = Modifier
                        .weight(region.widthWeight)
                        .fillMaxSize()
                        .background(
                            Color(region.red, region.green, region.blue).copy(alpha = region.alpha)
                        )
                        .clickable(
                            interactionSource = interactionSources[index],
                            indication = null,
                            onClick = onDismiss
                        )
                        .testTag("reader-tap-zone-${region.label.lowercase()}")
                        .semantics { contentDescription = "${region.label} tap region" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = region.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = READER_TAP_ZONE_TUTORIAL_LABEL_FONT_SIZE_SP.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReaderLightweightChrome(
    title: String,
    theme: EpubReaderTheme,
    positionKind: String,
    positionTitles: List<String>,
    currentPosition: Int,
    onBackToReading: () -> Unit,
    onCloseBook: () -> Unit,
    onOpenSettings: () -> Unit,
    onPositionSelected: (Int) -> Unit,
    listPositionKind: String = positionKind,
    listPositionTitles: List<String> = positionTitles,
    currentListPosition: Int = currentPosition,
    onListPositionSelected: (Int) -> Unit = onPositionSelected,
    secondaryPositionKind: String? = null,
    secondaryCurrentPosition: Int = 0,
    secondaryPositionCount: Int = 0,
    onSecondaryPositionSelected: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val entries = positionTitles.ifEmpty { listOf(positionKind) }
    val position = readerChromePositionState(currentPosition, entries.size)
    val listEntries = listPositionTitles.ifEmpty { listOf(listPositionKind) }
    val listPosition = readerChromePositionState(currentListPosition, listEntries.size)
    val secondaryPosition = secondaryPositionKind?.let {
        readerChromePositionState(secondaryCurrentPosition, secondaryPositionCount)
    }
    var sliderPosition by remember(position.currentIndex) { mutableStateOf(position.currentIndex.toFloat()) }
    var showPositionList by remember { mutableStateOf(false) }
    val palette = theme.readerOptionsPalette()
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val colors = remember(theme) {
        if (theme == EpubReaderTheme.Dark) {
            darkColorScheme(
                primary = Color(palette.accent),
                onPrimary = Color(palette.onAccent),
                primaryContainer = Color(palette.surfaceVariant),
                onPrimaryContainer = Color(palette.content),
                background = Color(palette.container),
                onBackground = Color(palette.content),
                surface = Color(palette.container),
                onSurface = Color(palette.content),
                surfaceVariant = Color(palette.surfaceVariant),
                onSurfaceVariant = Color(palette.mutedContent),
                outline = Color(palette.outline)
            )
        } else {
            lightColorScheme(
                primary = Color(palette.accent),
                onPrimary = Color(palette.onAccent),
                primaryContainer = Color(palette.surfaceVariant),
                onPrimaryContainer = Color(palette.content),
                background = Color(palette.container),
                onBackground = Color(palette.content),
                surface = Color(palette.container),
                onSurface = Color(palette.content),
                surfaceVariant = Color(palette.surfaceVariant),
                onSurfaceVariant = Color(palette.mutedContent),
                outline = Color(palette.outline)
            )
        }
    }

    MaterialTheme(colorScheme = colors, typography = parentTypography, shapes = parentShapes) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val positionControlHeight = (maxHeight * READER_POSITION_CONTROL_HEIGHT_FRACTION)
                .coerceAtLeast(240.dp)
                .coerceAtMost(maxHeight)
            EpubReaderDismissScrim(
                backgroundAlpha = 0f,
                contentDescription = "Dismiss reader controls and continue reading",
                onDismiss = onBackToReading
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .testTag("reader-lightweight-top-bar"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 5.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCloseBook,
                        modifier = Modifier.semantics { contentDescription = "Close book" },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text("Exit", modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(72.dp)
                    .height(positionControlHeight)
                    .testTag("reader-lightweight-position-control"),
                shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 5.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        enabled = position.canGoPrevious,
                        onClick = { onPositionSelected(position.currentIndex - 1) }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Previous ${positionKind.lowercase()}"
                        )
                    }
                    Box(
                        modifier = Modifier.width(64.dp).weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                            Slider(
                                value = sliderPosition,
                                onValueChange = { sliderPosition = it },
                                onValueChangeFinished = {
                                    onPositionSelected(sliderPosition.roundToInt().coerceIn(0, position.itemCount - 1))
                                },
                                valueRange = 0f..(position.itemCount - 1).coerceAtLeast(1).toFloat(),
                                steps = (position.itemCount - 2).coerceAtLeast(0),
                                enabled = position.itemCount > 1,
                                modifier = Modifier
                                    .requiredWidth(maxHeight)
                                    .rotate(90f)
                                    .semantics {
                                        contentDescription = "$positionKind jump bar"
                                        stateDescription = "${position.currentIndex + 1} of ${position.itemCount}"
                                    }
                            )
                        }
                    }
                    Text(
                        "${position.currentIndex + 1}/${position.itemCount}",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall
                    )
                    IconButton(
                        enabled = position.canGoNext,
                        onClick = { onPositionSelected(position.currentIndex + 1) }
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next ${positionKind.lowercase()}"
                        )
                    }
                    if (
                        secondaryPositionKind != null &&
                        secondaryPosition != null &&
                        onSecondaryPositionSelected != null
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        IconButton(
                            enabled = secondaryPosition.canGoPrevious,
                            onClick = {
                                onSecondaryPositionSelected(secondaryPosition.currentIndex - 1)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous ${secondaryPositionKind.lowercase()}"
                            )
                        }
                        Text(
                            "Chapter ${secondaryPosition.currentIndex + 1}/${secondaryPosition.itemCount}",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall
                        )
                        IconButton(
                            enabled = secondaryPosition.canGoNext,
                            onClick = {
                                onSecondaryPositionSelected(secondaryPosition.currentIndex + 1)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next ${secondaryPositionKind.lowercase()}"
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = EPUB_READER_PROGRESS_FOOTER_HEIGHT)
                    .testTag("reader-lightweight-bottom-bar"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 5.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { showPositionList = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Text(
                            text = if (listPositionKind == "Chapter") "Chapters" else "Pages",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Reader settings")
                    }
                }
            }
        }

        if (showPositionList) {
            ReaderPositionListDialog(
                positionKind = listPositionKind,
                entries = listEntries,
                currentPosition = listPosition.currentIndex,
                onDismiss = { showPositionList = false },
                onPositionSelected = { index ->
                    showPositionList = false
                    onListPositionSelected(index)
                }
            )
        }
    }
}

@Composable
private fun ReaderPositionListDialog(
    positionKind: String,
    entries: List<String>,
    currentPosition: Int,
    onDismiss: () -> Unit,
    onPositionSelected: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = if (positionKind == "Chapter") "Chapters" else "Pages",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(entries.indices.toList(), key = { it }) { index ->
                        val selected = index == currentPosition
                        Surface(
                            onClick = { onPositionSelected(index) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .semantics {
                                    contentDescription = "Select ${positionKind.lowercase()} ${index + 1}: ${entries[index]}"
                                    if (selected) stateDescription = "Current"
                                },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    "$positionKind ${index + 1}",
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    entries[index],
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal data class EpubReaderOptionsPalette(
    val container: Int,
    val content: Int,
    val mutedContent: Int,
    val accent: Int,
    val onAccent: Int,
    val surfaceVariant: Int,
    val outline: Int
)

internal fun EpubReaderTheme.readerOptionsPalette(): EpubReaderOptionsPalette = when (this) {
    EpubReaderTheme.Light -> EpubReaderOptionsPalette(
        container = 0xFFF7F4EE.toInt(),
        content = 0xFF1F1C17.toInt(),
        mutedContent = 0xFF5E574D.toInt(),
        accent = 0xFF2457A6.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        surfaceVariant = 0xFFE7E0D4.toInt(),
        outline = 0xFF6F685E.toInt()
    )
    EpubReaderTheme.Sepia -> EpubReaderOptionsPalette(
        container = 0xFFF1E6D2.toInt(),
        content = 0xFF3B2E1E.toInt(),
        mutedContent = 0xFF6A5944.toInt(),
        accent = 0xFF7A4B00.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        surfaceVariant = 0xFFE4D4B9.toInt(),
        outline = 0xFF796A57.toInt()
    )
    EpubReaderTheme.Dark -> EpubReaderOptionsPalette(
        container = 0xFF181512.toInt(),
        content = 0xFFECE4D8.toInt(),
        mutedContent = 0xFFBDB4A7.toInt(),
        accent = 0xFF8DB5FF.toInt(),
        onAccent = 0xFF10284D.toInt(),
        surfaceVariant = 0xFF2B2723.toInt(),
        outline = 0xFF8B8175.toInt()
    )
}

@Composable
internal fun EpubReaderOptionsBottomSheet(
    title: String,
    status: String,
    theme: EpubReaderTheme,
    padding: EpubPaddingPercentages,
    fontScale: Float,
    onContinueReading: () -> Unit,
    onCloseBook: () -> Unit,
    onThemeSelected: (EpubReaderTheme) -> Unit,
    onPaddingChange: (EpubPaddingPercentages) -> Unit,
    onPaddingChangeFinished: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val colors = remember(theme) {
        val palette = theme.readerOptionsPalette()
        if (theme == EpubReaderTheme.Dark) {
            darkColorScheme(
                primary = Color(palette.accent),
                onPrimary = Color(palette.onAccent),
                primaryContainer = Color(palette.surfaceVariant),
                onPrimaryContainer = Color(palette.content),
                background = Color(palette.container),
                onBackground = Color(palette.content),
                surface = Color(palette.container),
                onSurface = Color(palette.content),
                surfaceVariant = Color(palette.surfaceVariant),
                onSurfaceVariant = Color(palette.mutedContent),
                outline = Color(palette.outline)
            )
        } else {
            lightColorScheme(
                primary = Color(palette.accent),
                onPrimary = Color(palette.onAccent),
                primaryContainer = Color(palette.surfaceVariant),
                onPrimaryContainer = Color(palette.content),
                background = Color(palette.container),
                onBackground = Color(palette.content),
                surface = Color(palette.container),
                onSurface = Color(palette.content),
                surfaceVariant = Color(palette.surfaceVariant),
                onSurfaceVariant = Color(palette.mutedContent),
                outline = Color(palette.outline)
            )
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = parentTypography,
        shapes = parentShapes
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        top = 10.dp,
                        end = 20.dp,
                        bottom = 18.dp + EPUB_READER_PROGRESS_FOOTER_HEIGHT
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Reader options",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onContinueReading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Continue reading")
                    }
                    OutlinedButton(
                        onClick = onCloseBook,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close book")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EPUB_THEME_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = option == theme,
                                onClick = { onThemeSelected(option) },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDecreaseFont) { Text("A-") }
                        Text(
                            "Text size ${formatEpubFontScale(fontScale)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = onIncreaseFont) { Text("A+") }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Page margins", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Each 100% setting equals 25% of that screen edge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EpubPaddingSlider(
                        label = "Top",
                        value = padding.top,
                        onValueChange = { value -> onPaddingChange(padding.copy(top = value)) },
                        onValueChangeFinished = onPaddingChangeFinished
                    )
                    EpubPaddingSlider(
                        label = "Bottom",
                        value = padding.bottom,
                        onValueChange = { value -> onPaddingChange(padding.copy(bottom = value)) },
                        onValueChangeFinished = onPaddingChangeFinished
                    )
                    EpubPaddingSlider(
                        label = "Left",
                        value = padding.left,
                        onValueChange = { value -> onPaddingChange(padding.copy(left = value)) },
                        onValueChangeFinished = onPaddingChangeFinished
                    )
                    EpubPaddingSlider(
                        label = "Right",
                        value = padding.right,
                        onValueChange = { value -> onPaddingChange(padding.copy(right = value)) },
                        onValueChangeFinished = onPaddingChangeFinished
                    )
                }
            }
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

@Suppress("UNUSED_PARAMETER")
private class EpubPageMeasurementBridge(
    private val onMeasured: (Int, Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun centerTap() = Unit

    @JavascriptInterface
    fun pageChanged(page: Int, count: Int) = Unit

    @JavascriptInterface
    fun chapterBoundary(direction: Int) = Unit

    @JavascriptInterface
    fun chapterPageCount(chapterIndex: Int, count: Int) {
        mainHandler.post { onMeasured(chapterIndex, count.coerceAtLeast(1)) }
    }
}

@Composable
internal fun ComicReaderProgressFooter(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(EPUB_READER_PROGRESS_FOOTER_HEIGHT),
        color = Color.Black,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Page ${currentPage + 1} of $pageCount",
                modifier = Modifier.semantics {
                    contentDescription = "Comic page ${currentPage + 1} of $pageCount"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun ComicReaderOptionsBottomSheet(
    title: String,
    currentPage: Int,
    pageCount: Int,
    onContinueReading: () -> Unit,
    onCloseBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val palette = remember { EpubReaderTheme.Dark.readerOptionsPalette() }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(palette.accent),
            onPrimary = Color(palette.onAccent),
            primaryContainer = Color(palette.surfaceVariant),
            onPrimaryContainer = Color(palette.content),
            background = Color(palette.container),
            onBackground = Color(palette.content),
            surface = Color(palette.container),
            onSurface = Color(palette.content),
            surfaceVariant = Color(palette.surfaceVariant),
            onSurfaceVariant = Color(palette.mutedContent),
            outline = Color(palette.outline)
        ),
        typography = parentTypography,
        shapes = parentShapes
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 10.dp,
                    end = 20.dp,
                    bottom = 18.dp + EPUB_READER_PROGRESS_FOOTER_HEIGHT
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Reader options",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Page ${currentPage + 1} of $pageCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = onContinueReading, modifier = Modifier.weight(1f)) {
                        Text("Continue reading")
                    }
                    OutlinedButton(onClick = onCloseBook, modifier = Modifier.weight(1f)) {
                        Text("Close book")
                    }
                }
            }
        }
    }
}

private sealed interface ComicPageSource {
    val pageCount: Int

    data object Loading : ComicPageSource {
        override val pageCount: Int = 0
    }

    data class Local(val pages: List<File>) : ComicPageSource {
        override val pageCount: Int = pages.size
    }

    data class Remote(val pagesUrl: String, override val pageCount: Int) : ComicPageSource

    data class Error(val message: String) : ComicPageSource {
        override val pageCount: Int = 0
    }
}

private sealed interface ComicPageImage {
    data object Loading : ComicPageImage
    data class Ready(val bitmap: Bitmap) : ComicPageImage
    data object Error : ComicPageImage
}

internal fun parseComicPageCount(payload: ByteArray): Int? {
    return runCatching {
        JSONObject(payload.toString(Charsets.UTF_8)).optInt("pageCount")
            .takeIf { it > 0 }
    }.getOrNull()
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

internal fun styleEpubHtml(
    html: String,
    theme: EpubReaderTheme,
    fontScale: Float,
    startAtEnd: Boolean,
    topPaddingPercent: Float = EPUB_DEFAULT_TOP_PADDING_PERCENT,
    bottomPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    leftPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    rightPaddingPercent: Float = EPUB_DEFAULT_PADDING_PERCENT,
    initialPage: Int = 0,
    measurementChapterIndex: Int? = null,
    chapterBaseUrl: String? = null
): String {
    val fontPercent = (fontScale * 100f).roundToInt()
    val topInset = epubPaddingViewportPercent(topPaddingPercent)
    val bottomInset = epubPaddingViewportPercent(bottomPaddingPercent)
    val leftInset = epubPaddingViewportPercent(leftPaddingPercent)
    val rightInset = epubPaddingViewportPercent(rightPaddingPercent)
    val pageInsetHeight = topInset + bottomInset
    val pageInsetWidth = leftInset + rightInset
    val chapterBaseTag = chapterBaseUrl?.let { url ->
        "<base href=\"${escapeEpubHtmlAttribute(url)}\">"
    }.orEmpty()
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
        img {
            max-width: 100%;
            max-height: var(--bookorbit-reader-page-height);
            height: auto;
            object-fit: contain;
            break-inside: avoid;
        }
        img.bookorbit-svg-raster {
            display: block !important;
            width: auto !important;
            height: auto !important;
            max-width: 100% !important;
            max-height: var(--bookorbit-reader-page-height) !important;
            margin: 0 auto !important;
            object-fit: contain !important;
        }
        svg {
            max-width: 100%;
            max-height: var(--bookorbit-reader-page-height);
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
          const measurementChapterIndex = ${measurementChapterIndex?.coerceAtLeast(0) ?: "null"};
          let measurementToken = 0;
          const bridge = window.$EPUB_READER_BRIDGE;
          const normalizeBitmapOnlySvgPages = () => {
            const vectorContentSelector = [
              'path', 'rect', 'polygon', 'polyline', 'line', 'circle', 'ellipse',
              'text', 'use', 'foreignObject'
            ].join(',');
            Array.from(document.querySelectorAll('svg')).forEach((svg) => {
              const rasterImages = svg.querySelectorAll('image');
              if (rasterImages.length !== 1 || svg.querySelector(vectorContentSelector)) return;
              const rasterImage = rasterImages[0];
              const source = rasterImage.getAttribute('href') ||
                rasterImage.getAttribute('xlink:href') ||
                rasterImage.getAttributeNS('http://www.w3.org/1999/xlink', 'href');
              if (!source) return;
              const replacement = document.createElement('img');
              replacement.src = source;
              replacement.alt = svg.getAttribute('aria-label') ||
                rasterImage.getAttribute('aria-label') ||
                rasterImage.getAttribute('alt') || '';
              replacement.className = [svg.getAttribute('class'), 'bookorbit-svg-raster']
                .filter(Boolean)
                .join(' ');
              if (svg.id) replacement.id = svg.id;
              replacement.loading = 'eager';
              replacement.decoding = 'sync';
              replacement.setAttribute('data-bookorbit-normalized-svg', 'true');
              svg.replaceWith(replacement);
            });
          };
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
          const scheduleMeasuredPageCount = () => {
            if (measurementChapterIndex === null) return;
            const token = ++measurementToken;
            const fontReady = document.fonts && document.fonts.ready
              ? Promise.resolve(document.fonts.ready).catch(() => undefined)
              : Promise.resolve();
            const imageReady = Array.from(strip ? strip.querySelectorAll('img') : []).map((image) => {
              image.loading = 'eager';
              if (image.complete) return Promise.resolve();
              return new Promise((resolve) => {
                image.addEventListener('load', resolve, { once: true });
                image.addEventListener('error', resolve, { once: true });
              });
            });
            const assetsReady = Promise.all([fontReady, ...imageReady]);
            const readinessTimeout = new Promise((resolve) => window.setTimeout(resolve, 750));
            Promise.race([assetsReady, readinessTimeout]).then(() => {
              requestAnimationFrame(() => requestAnimationFrame(() => {
                if (token !== measurementToken || !strip) return;
                bridge.chapterPageCount(measurementChapterIndex, pageCount());
              }));
            });
          };
          const publish = () => {
            bridge.pageChanged(page, pageCount());
            scheduleMeasuredPageCount();
          };
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
          const jumpToPage = (next) => {
            const target = Number(next);
            if (!Number.isFinite(target)) return;
            pinToEnd = false;
            page = Math.min(Math.max(0, Math.round(target)), pageCount() - 1);
            renderPage();
            publish();
          };
          window.BookOrbitReaderLayout = Object.freeze({
            setInsets,
            refresh: repaginate,
            goToPage: jumpToPage
          });
          window.addEventListener('load', () => {
            normalizeBitmapOnlySvgPages();
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
    val headTag = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    val headMatch = headTag.find(html)
    val htmlWithBase = if (chapterBaseTag.isNotEmpty() && headMatch != null) {
        val insertionIndex = headMatch.range.last + 1
        html.substring(0, insertionIndex) + chapterBaseTag + html.substring(insertionIndex)
    } else {
        html
    }
    return if (htmlWithBase.contains("</head>", ignoreCase = true)) {
        htmlWithBase.replaceFirst("</head>", "$readerAssets</head>", ignoreCase = true)
    } else {
        """
        <html>
          <head>$chapterBaseTag$readerAssets</head>
          <body>$htmlWithBase</body>
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

internal fun epubPageJumpJavascript(pageIndex: Int): String {
    return "if (window.BookOrbitReaderLayout) { " +
        "window.BookOrbitReaderLayout.goToPage(${pageIndex.coerceAtLeast(0)}); }"
}

private fun formatEpubFontScale(fontScale: Float): String {
    return String.format(Locale.US, "%.0f%%", fontScale * 100f)
}

private fun formatEpubCssPercent(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

private fun escapeEpubHtmlAttribute(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

internal fun epubPaddingViewportPercent(value: Float): Float {
    return value.coerceIn(0f, 100f) / 4f
}

internal data class EpubPaddingPercentages(
    val top: Float = EPUB_DEFAULT_TOP_PADDING_PERCENT,
    val bottom: Float = EPUB_DEFAULT_PADDING_PERCENT,
    val left: Float = EPUB_DEFAULT_PADDING_PERCENT,
    val right: Float = EPUB_DEFAULT_PADDING_PERCENT
)

internal fun EpubPaddingPercentages.forWebViewContent(): EpubPaddingPercentages {
    return copy(top = 0f, bottom = 0f)
}

private data class EpubWebViewRenderState(
    val documentKey: String?,
    val padding: EpubPaddingPercentages?,
    val assetSession: EpubWebViewAssetSession
)

private data class EpubMeasurementRenderState(
    val documentKey: String?,
    val assetSession: EpubWebViewAssetSession
)

internal const val EPUB_DEFAULT_PADDING_PERCENT = 15f
internal const val EPUB_DEFAULT_TOP_PADDING_PERCENT = 30f
internal const val EPUB_READER_PROGRESS_FOOTER_HEIGHT_DP = 30
private val EPUB_READER_PROGRESS_FOOTER_HEIGHT = EPUB_READER_PROGRESS_FOOTER_HEIGHT_DP.dp

private const val AUDIO_NOTIFICATION_PERMISSION_REQUEST = 4102
private const val AUDIO_PREPARATION_TIMEOUT_MILLIS = 30_000L
private const val AUDIO_CLOSE_TIMEOUT_MILLIS = 5_000L
private val COMIC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
private const val COMIC_SWIPE_THRESHOLD_FRACTION = 0.15f
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
