package com.bookorbit.android

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
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
            onChangeServer = coordinator::clearServer,
            onAuthenticated = coordinator::refreshLoginState
        )
        is AppScreen.Browser -> LibraryBrowserScreen(
            state = screen.browserState,
            onRefresh = coordinator::loadBrowser,
            onLibrarySelected = coordinator::selectLibrary,
            onBookOpen = coordinator::openBook,
            onDownload = coordinator::downloadBook,
            onCancelDownload = coordinator::cancelDownload,
            onDeleteLocalCopy = coordinator::deleteLocalCopy
        )
        is AppScreen.ReaderLoading -> ReaderLoadingScreen(
            book = screen.book,
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
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(book.title) },
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
        topBar = { CenterAlignedTopAppBar(title = { Text("BookOrbit") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Connect to a BookOrbit server", style = MaterialTheme.typography.headlineSmall)
            Text("Enter the base URL. The app will open the server sign-in page next.")
            if (!message.isNullOrBlank()) {
                Text(message, color = MaterialTheme.colorScheme.error)
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
                placeholder = { Text("https://books.example.com") }
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val normalized = normalizeServerUrl(server)
                    if (normalized == null) {
                        error = "Enter a valid server URL."
                    } else {
                        onContinue(normalized)
                    }
                }
            ) {
                Text("Continue")
            }
            if (server.isNotBlank() && !message.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onContinue(server) }
                ) {
                    Text("Retry")
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
    onChangeServer: () -> Unit,
    onAuthenticated: () -> Unit
) {
    LaunchedEffect(serverUrl) {
        while (isActive) {
            delay(1500)
            onAuthenticated()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sign in") },
                actions = { TextButton(onClick = onChangeServer) { Text("Change server") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadUrl(serverUrl)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryBrowserScreen(
    state: BrowserState,
    onRefresh: () -> Unit,
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
            CenterAlignedTopAppBar(
                title = { Text("Libraries") },
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
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = state.serverUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
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
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
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
                Card(modifier = Modifier.fillMaxWidth()) {
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
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.book.title) },
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
                        onProgress(state.book, position, 0, percent)
                    }
                )
                MediaKind.PDF -> PdfReaderView(
                    file = state.localFile,
                    initialPage = state.pageIndex,
                    onProgress = { pageIndex, percent ->
                        onProgress(state.book, 0L, pageIndex, percent)
                    }
                )
                MediaKind.EPUB -> EpubReaderView(
                    title = state.book.title,
                    file = state.localFile,
                    initialChapter = state.pageIndex,
                    initialPercent = state.progressPercent,
                    onProgress = { chapterIndex, percent ->
                        onProgress(state.book, 0L, chapterIndex, percent)
                    }
                )
                MediaKind.COMIC -> ComicReaderView(
                    title = state.book.title,
                    file = state.localFile,
                    initialPage = state.pageIndex,
                    onProgress = { pageIndex, percent ->
                        onProgress(state.book, 0L, pageIndex, percent)
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
            val percent = if (playerDuration > 0L) playerPosition.toFloat() / playerDuration.toFloat() else null
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
        val percent = if (pageCount > 0) currentPage.toFloat() / pageCount.toFloat() else null
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
    file: File?,
    initialChapter: Int,
    initialPercent: Float?,
    onProgress: (Int, Float?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val epubBook = remember(file) { file?.takeIf(File::exists)?.let { loadEpubBook(context, it) } }
    if (file == null || !file.exists()) {
        ReaderMessage("Unable to prepare this EPUB.")
        return
    }
    if (epubBook == null || epubBook.chapters.isEmpty()) {
        ReaderMessage("Unable to open this EPUB.")
        return
    }

    val chapterCount = epubBook.chapters.size
    val estimatedChapter = remember(chapterCount, initialPercent) {
        percentToChapterIndex(initialPercent, chapterCount)
    }
    var currentChapter by remember(file, initialChapter, estimatedChapter) {
        mutableStateOf(
            when {
                initialChapter > 0 -> initialChapter.coerceIn(0, chapterCount - 1)
                else -> estimatedChapter
            }
        )
    }
    var selectedTheme by remember(file) { mutableStateOf(EpubReaderTheme.Sepia) }
    var fontScale by remember(file) { mutableStateOf(1f) }
    var showChapterPicker by remember(file) { mutableStateOf(false) }
    val currentChapterState by rememberUpdatedState(epubBook.chapters[currentChapter])

    LaunchedEffect(currentChapter, chapterCount) {
        val percent = if (chapterCount <= 1) 100f else (currentChapter.toFloat() / (chapterCount - 1).toFloat()) * 100f
        onProgress(currentChapter, percent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(epubBook.title ?: title, style = MaterialTheme.typography.titleMedium)
        Text(
            "Chapter ${currentChapter + 1} of $chapterCount",
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Chapter ${currentChapter + 1} of $chapterCount"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        TextButton(
            onClick = { showChapterPicker = !showChapterPicker },
            modifier = Modifier.semantics {
                contentDescription = if (showChapterPicker) {
                    "Hide chapter picker"
                } else {
                    "Show chapter picker"
                }
            }
        ) {
            Text(if (showChapterPicker) "Hide chapters" else "Show chapters")
        }
        if (showChapterPicker) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                epubBook.chapters.forEachIndexed { index, chapter ->
                    FilterChip(
                        selected = index == currentChapter,
                        onClick = { currentChapter = index },
                        label = {
                            Text(
                                chapter.title.ifBlank { "Chapter ${index + 1}" }
                            )
                        }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EPUB_THEME_OPTIONS.forEach { theme ->
                val selected = theme == selectedTheme
                if (selected) {
                    Button(
                        onClick = { selectedTheme = theme },
                        modifier = Modifier.semantics {
                            contentDescription = "EPUB theme ${theme.label}"
                            stateDescription = "Selected"
                        }
                    ) {
                        Text(theme.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedTheme = theme },
                        modifier = Modifier.semantics { contentDescription = "EPUB theme ${theme.label}" }
                    ) {
                        Text(theme.label)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { fontScale = (fontScale - 0.1f).coerceAtLeast(0.9f) },
                modifier = Modifier.semantics { contentDescription = "Decrease EPUB text size" }
            ) {
                Text("A-")
            }
            Text(
                "Text ${formatEpubFontScale(fontScale)}",
                modifier = Modifier.semantics { contentDescription = "EPUB text size ${formatEpubFontScale(fontScale)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            OutlinedButton(
                onClick = { fontScale = (fontScale + 0.1f).coerceAtMost(1.5f) },
                modifier = Modifier.semantics { contentDescription = "Increase EPUB text size" }
            ) {
                Text("A+")
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(selectedTheme.backgroundColor))
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "EPUB chapter reader" },
                factory = { webContext ->
                    WebView(webContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webViewClient = WebViewClient()
                    }
                },
                update = { webView ->
                    val chapter = currentChapterState
                    val html = chapter.file.readText()
                    webView.setBackgroundColor(selectedTheme.backgroundColor)
                    webView.loadDataWithBaseURL(
                        chapter.file.parentFile?.toURI()?.toString(),
                        styleEpubHtml(
                            html = html,
                            theme = selectedTheme,
                            fontScale = fontScale
                        ),
                        "text/html",
                        Charsets.UTF_8.name(),
                        null
                    )
                }
            )
        }
        Text(
            epubBook.chapters[currentChapter].title,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { currentChapter = (currentChapter - 1).coerceAtLeast(0) },
                enabled = currentChapter > 0
            ) {
                Text("Previous")
            }
            Button(
                onClick = { currentChapter = (currentChapter + 1).coerceAtMost(chapterCount - 1) },
                enabled = currentChapter < chapterCount - 1
            ) {
                Text("Next")
            }
        }
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
    val explicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").find(raw)?.value?.removeSuffix(":")
    if (explicitScheme != null && !explicitScheme.equals("http", ignoreCase = true) && !explicitScheme.equals("https", ignoreCase = true)) {
        return null
    }
    val prefixed = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
        raw
    } else {
        "http://$raw"
    }
    val uri = Uri.parse(prefixed)
    val scheme = uri.scheme ?: return null
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        return null
    }
    val host = uri.host ?: return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    val path = uri.path?.takeIf { it.isNotBlank() && it != "/" }?.trimEnd('/') ?: ""
    return "$scheme://$host$port$path"
}

private fun percentToChapterIndex(percent: Float?, chapterCount: Int): Int {
    if (chapterCount <= 1) {
        return 0
    }
    val raw = percent ?: return 0
    val normalized = if (raw in 0f..1f) raw else raw / 100f
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

private fun styleEpubHtml(
    html: String,
    theme: EpubReaderTheme,
    fontScale: Float
): String {
    val fontPercent = (fontScale * 100f).roundToInt()
    val stylesheet = """
        <style>
        :root { color-scheme: light; }
        html, body {
            margin: 0;
            padding: 0;
            background: ${theme.backgroundCss};
            color: ${theme.foregroundCss};
            font-size: ${fontPercent}%;
            line-height: 1.7;
        }
        body {
            padding: 20px 18px 28px;
            max-width: 44rem;
            margin: 0 auto;
            word-wrap: break-word;
        }
        img, svg {
            max-width: 100%;
            height: auto;
        }
        a { color: ${theme.linkCss}; }
        pre, code {
            white-space: pre-wrap;
        }
        </style>
    """.trimIndent()
    return if (html.contains("</head>", ignoreCase = true)) {
        html.replaceFirst("</head>", "$stylesheet</head>", ignoreCase = true)
    } else {
        """
        <html>
          <head>$stylesheet</head>
          <body>$html</body>
        </html>
        """.trimIndent()
    }
}

private fun formatEpubFontScale(fontScale: Float): String {
    return String.format(Locale.US, "%.0f%%", fontScale * 100f)
}

private val AUDIO_SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f)
private val COMIC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
private val EPUB_THEME_OPTIONS = listOf(
    EpubReaderTheme.Light,
    EpubReaderTheme.Sepia,
    EpubReaderTheme.Dark
)

private enum class EpubReaderTheme(
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
