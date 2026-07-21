package com.bookorbit.android

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private enum class BrowserDestination { HOME, LIBRARY, SERIES, AUTHORS, LOCAL_BOOKS, ACHIEVEMENTS, OPTIONS, ABOUT }
private enum class LibraryTab { RECOMMENDED, BROWSE }
private enum class OptionsDialog {
    THEME,
    OPENING_SCREEN,
    CELLULAR_DOWNLOADS,
    BACKGROUND_REFRESH,
    CLEAR_CACHE
}
private val BOOK_CARD_MIN_SIZE = 88.dp
private val CATALOG_GRID_PADDING = 16.dp
private val CATALOG_JUMP_RAIL_END_PADDING = 32.dp

internal fun catalogGridEndPadding(hasJumpRail: Boolean) =
    if (hasJumpRail) CATALOG_JUMP_RAIL_END_PADDING else CATALOG_GRID_PADDING

internal val LocalReduceMotion = staticCompositionLocalOf { false }

private fun DefaultOpeningScreen.toBrowserDestination(): BrowserDestination = when (this) {
    DefaultOpeningScreen.HOME -> BrowserDestination.HOME
    DefaultOpeningScreen.LIBRARY -> BrowserDestination.LIBRARY
    DefaultOpeningScreen.LOCAL_BOOKS -> BrowserDestination.LOCAL_BOOKS
}

private val LIBRARY_JUMP_LABELS = listOf('#') + ('A'..'Z').toList()

internal fun libraryJumpLabel(value: String?): Char {
    val first = value?.trim()?.firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
}

internal fun buildLibraryJumpTargets(
    displayedBooks: List<Pair<BookSummary, String?>>,
    sort: BookSortOption = BookSortOption.SERVER_DEFAULT,
    direction: SortDirection = SortDirection.ASCENDING
): List<Pair<Char, Int>> {
    val labels = displayedBooks.map { (book, seriesKey) ->
        libraryJumpLabel(
            when {
                seriesKey != null -> book.seriesName
                sort == BookSortOption.AUTHOR -> book.author
                else -> book.title
            }
        )
    }
    val railDirection = if (
        direction == SortDirection.DESCENDING &&
        sort in setOf(BookSortOption.TITLE, BookSortOption.AUTHOR) &&
        displayedBooks.none { it.second != null }
    ) {
        SortDirection.DESCENDING
    } else {
        SortDirection.ASCENDING
    }
    return buildAlphabetJumpTargets(labels, railDirection)
}

internal fun buildSeriesJumpTargets(
    series: List<SeriesSummary>,
    direction: SortDirection = SortDirection.ASCENDING
): List<Pair<Char, Int>> = buildAlphabetJumpTargets(
    labels = series.map { libraryJumpLabel(it.name) },
    direction = direction
)

private fun buildAlphabetJumpTargets(
    labels: List<Char>,
    direction: SortDirection
): List<Pair<Char, Int>> {
    val railLabels = if (
        direction == SortDirection.DESCENDING
    ) {
        ('Z' downTo 'A').toList() + '#'
    } else {
        LIBRARY_JUMP_LABELS
    }
    return railLabels.mapNotNull { target ->
        labels.indexOfFirst { it == target }
            .takeIf { it >= 0 }
            ?.let { target to it }
    }
}

internal fun catalogJumpRailLabels(direction: SortDirection): List<Char> =
    if (direction == SortDirection.DESCENDING) {
        ('Z' downTo 'A').toList() + '#'
    } else {
        LIBRARY_JUMP_LABELS
    }

internal fun buildServerLibraryJumpTargets(
    buckets: List<LibraryJumpBucket>,
    itemCount: Int
): List<Pair<Char, Int>> {
    if (buckets.isEmpty() || itemCount <= 0) return emptyList()
    val indexedBuckets = buckets.mapNotNull { bucket ->
        val label = bucket.label.trim().firstOrNull()?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
            ?: '#'
        bucket.copy(index = bucket.index.coerceIn(0, itemCount - 1)) to label
    }
    return LIBRARY_JUMP_LABELS.mapNotNull { target ->
        indexedBuckets.firstOrNull { it.second == target }
            ?.let { target to it.first.index }
    }
}

private data class LibraryGridAnchor(
    val bookId: String,
    val seriesKey: String?
)

internal fun collapsedLibraryBooks(
    books: List<BookSummary>
): List<Pair<BookSummary, String?>> {
    return buildList<Pair<BookSummary, String?>> {
        books.groupBy { it.seriesId ?: it.seriesName }
            .forEach { (seriesKey, seriesBooks) ->
                if (seriesKey.isNullOrBlank()) {
                    addAll(seriesBooks.map { it to null })
                } else {
                    val representative = seriesBooks.minWithOrNull(
                        compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
                            .thenBy { it.title }
                    ) ?: return@forEach
                    add(representative to seriesKey)
                }
            }
    }.sortedWith(
        compareBy<Pair<BookSummary, String?>> {
            val (book, seriesKey) = it
            (if (seriesKey != null) book.seriesName ?: seriesKey else book.title)
                .trim()
                .lowercase()
        }
    )
}

internal fun collapsedSeriesBookCounts(books: List<BookSummary>): Map<String, Int> = books
    .mapNotNull { book ->
        (book.seriesId ?: book.seriesName)
            ?.takeIf { it.isNotBlank() }
    }
    .groupingBy { it }
    .eachCount()

internal fun seriesBookCountLabel(count: Int): String =
    "$count ${if (count == 1) "book" else "books"}"

internal fun localBooksShelf(
    books: List<BookSummary>,
    libraryId: String? = null,
    limit: Int = 12
): List<BookSummary> = books
    .asSequence()
    .filter { it.isDownloaded && (libraryId == null || it.libraryId == libraryId) }
    .distinctBy { it.id }
    .sortedWith(compareBy<BookSummary> { it.title.lowercase() }.thenBy { it.id })
    .take(limit)
    .toList()

internal data class SeriesBookNeighbors(
    val previous: BookSummary?,
    val next: BookSummary?,
    val total: Int
)

private fun booksShareSeries(current: BookSummary, candidate: BookSummary): Boolean {
    if (candidate.id == current.id) return true
    val seriesId = current.seriesId?.takeIf { it.isNotBlank() }
    val seriesName = current.seriesName?.takeIf { it.isNotBlank() }
    return when {
        seriesId != null -> candidate.seriesId == seriesId ||
            (candidate.seriesId.isNullOrBlank() && candidate.seriesName == seriesName)
        seriesName != null -> candidate.seriesName == seriesName
        else -> false
    }
}

internal fun seriesBookNeighbors(
    current: BookSummary,
    candidates: List<BookSummary>
): SeriesBookNeighbors {
    val seriesId = current.seriesId?.takeIf { it.isNotBlank() }
    val seriesName = current.seriesName?.takeIf { it.isNotBlank() }
    if (seriesId == null && seriesName == null) {
        return SeriesBookNeighbors(previous = null, next = null, total = 0)
    }
    val sameSeries = (candidates + current)
        .filter { candidate -> booksShareSeries(current, candidate) }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )
    val currentIndex = sameSeries.indexOfFirst { it.id == current.id }
    return SeriesBookNeighbors(
        previous = sameSeries.getOrNull(currentIndex - 1),
        next = sameSeries.getOrNull(currentIndex + 1),
        total = sameSeries.size
    )
}

private val coverBitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}
private val coverLoadLocks = Array(32) { Mutex() }
private val catalogImageCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

private suspend fun loadCatalogImageWithRetry(
    url: String,
    loader: suspend (String) -> ByteArray?
): ByteArray? {
    catalogImageCache.get(url)?.let { return it }
    repeat(2) { attempt ->
        val loaded = runCatching { loader(url) }.getOrNull()
        if (loaded != null && loaded.isNotEmpty()) {
            catalogImageCache.put(url, loaded)
            return loaded
        }
        if (attempt == 0) delay(220)
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeLibraryBrowserScreen(
    state: BrowserState,
    onRefresh: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: (String) -> Unit,
    onLibrarySelected: (String) -> Unit,
    searchBooks: suspend (String) -> List<BookSummary>,
    localBooksLoader: suspend () -> List<BookSummary>,
    libraryBooksPageLoader: suspend (String, Int, BookBrowseFilter) -> LibraryBooksPage,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    bookDetailLoader: suspend (BookSummary) -> BookDetailInfo?,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    authorsCatalogLoader: suspend (String?, Int) -> AuthorCatalogPage,
    authorBooksLoader: suspend (String, Int) -> AuthorBooksPage?,
    achievementsLoader: suspend () -> AchievementCatalogue,
    catalogImageLoader: suspend (String) -> ByteArray?,
    onBookOpen: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDismissMessage: () -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    appPreferences: AppPreferences = AppPreferences(),
    onAppPreferencesChange: (AppPreferences) -> Unit = {},
    storageUsageLoader: suspend () -> StorageUsage = { StorageUsage() },
    onClearCache: suspend () -> Unit = {},
    bookDetailRequest: AudioBookDetailRequest? = null,
    onBookDetailRequestConsumed: (Long) -> Unit = {},
    bottomOverlay: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    var destination by rememberSaveable {
        mutableStateOf(appPreferences.defaultOpeningScreen.toBrowserDestination())
    }
    var query by rememberSaveable { mutableStateOf("") }
    var showLibraryPicker by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showProfileMenu by rememberSaveable { mutableStateOf(false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableStateOf(LibraryTab.RECOMMENDED) }
    var selectedBook by remember { mutableStateOf<BookSummary?>(null) }
    var selectedSeriesKey by remember { mutableStateOf<String?>(null) }
    var selectedAuthor by remember { mutableStateOf<AuthorSummary?>(null) }
    var activeBookGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSeriesGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var genreSourceBook by remember { mutableStateOf<BookSummary?>(null) }
    var genreSourceSeriesKey by remember { mutableStateOf<String?>(null) }
    var detailReturnDestination by remember { mutableStateOf(BrowserDestination.HOME) }
    var pendingCellularDownload by remember { mutableStateOf<BookSummary?>(null) }
    var showCellularDownloadBlocked by remember { mutableStateOf(false) }
    var pendingLocalDelete by remember { mutableStateOf<BookSummary?>(null) }
    var localBooksLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeServerEditor by rememberSaveable { mutableStateOf(false) }
    var changeServerUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var changeServerError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingServerChange by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(bookDetailRequest?.sequence) {
        val request = bookDetailRequest ?: return@LaunchedEffect
        detailReturnDestination = destination
        isSearchOpen = false
        query = ""
        activeBookGenre = null
        activeSeriesGenre = null
        selectedSeriesKey = null
        selectedAuthor = null
        selectedBook = request.book
        onBookDetailRequestConsumed(request.sequence)
    }
    val requestDownload: (BookSummary) -> Unit = { book ->
        when (
            cellularDownloadDecision(
                policy = appPreferences.cellularDownloadPolicy,
                isCellularOrMetered = context.isActiveCellularOrMeteredNetwork()
            )
        ) {
            CellularDownloadDecision.START -> onDownload(book)
            CellularDownloadDecision.ASK -> pendingCellularDownload = book
            CellularDownloadDecision.BLOCK -> showCellularDownloadBlocked = true
        }
    }
    val requestLocalDelete: (BookSummary) -> Unit = { book ->
        if (appPreferences.confirmDeleteLocalCopy) {
            pendingLocalDelete = book
        } else {
            onDeleteLocalCopy(book)
        }
    }
    val remoteSearchResults by produceState<List<BookSummary>?>(initialValue = null, query) {
        value = null
        if (query.isNotBlank()) {
            delay(300)
            value = searchBooks(query)
        }
    }
    val filteredBooks = remoteSearchResults.orEmpty()
    val sessionActionLabel = if (state.isOfflineSnapshot) "Sign in" else "Log out"
    val openOptions = {
        showProfileMenu = false
        showMoreMenu = false
        destination = BrowserDestination.OPTIONS
        query = ""
        selectedAuthor = null
        selectedSeriesKey = null
        activeBookGenre = null
        activeSeriesGenre = null
        genreSourceBook = null
        genreSourceSeriesKey = null
        selectedBook = null
    }
    val openAchievements = {
        showProfileMenu = false
        showMoreMenu = false
        destination = BrowserDestination.ACHIEVEMENTS
        query = ""
        selectedAuthor = null
        selectedSeriesKey = null
        activeBookGenre = null
        activeSeriesGenre = null
        genreSourceBook = null
        genreSourceSeriesKey = null
        selectedBook = null
    }
    val openAbout = {
        showProfileMenu = false
        showMoreMenu = false
        destination = BrowserDestination.ABOUT
        query = ""
        selectedAuthor = null
        selectedSeriesKey = null
        activeBookGenre = null
        activeSeriesGenre = null
        genreSourceBook = null
        genreSourceSeriesKey = null
        selectedBook = null
    }
    val openChangeServerEditor = {
        showProfileMenu = false
        changeServerUrl = state.serverUrl
        changeServerError = null
        showChangeServerEditor = true
    }

    BackHandler(enabled = isSearchOpen || activeBookGenre != null || activeSeriesGenre != null || selectedBook != null || selectedSeriesKey != null || selectedAuthor != null) {
        if (isSearchOpen) {
            isSearchOpen = false
            query = ""
        } else if (activeBookGenre != null) {
            activeBookGenre = null
            selectedBook = genreSourceBook
            genreSourceBook = null
        } else if (activeSeriesGenre != null) {
            activeSeriesGenre = null
            selectedSeriesKey = genreSourceSeriesKey
            genreSourceSeriesKey = null
        } else if (selectedBook != null) {
            selectedBook = null
        } else if (selectedAuthor != null) {
            selectedAuthor = null
            destination = BrowserDestination.AUTHORS
        } else {
            selectedSeriesKey = null
            destination = detailReturnDestination
        }
    }

    if (showMoreMenu) {
        ModalBottomSheet(onDismissRequest = { showMoreMenu = false }) {
            MoreMenu(
                onSeries = {
                    showMoreMenu = false
                    destination = BrowserDestination.SERIES
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onAuthors = {
                    showMoreMenu = false
                    destination = BrowserDestination.AUTHORS
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onLocalBooks = {
                    showMoreMenu = false
                    localBooksLibraryId = null
                    destination = BrowserDestination.LOCAL_BOOKS
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                }
            )
        }
    }

    pendingCellularDownload?.let { book ->
        val isUpdate = book.hasDownloadUpdate
        AlertDialog(
            onDismissRequest = { pendingCellularDownload = null },
            title = { Text(if (isUpdate) "Update local copy using cellular data?" else "Download using cellular data?") },
            text = {
                Text(
                    "${if (isUpdate) "Updating" else "Downloading"} ${book.title} may use a significant amount of mobile data."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCellularDownload = null
                    onDownload(book)
                }) { Text(if (isUpdate) "Update local" else "Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularDownload = null }) { Text("Cancel") }
            }
        )
    }
    if (showCellularDownloadBlocked) {
        AlertDialog(
            onDismissRequest = { showCellularDownloadBlocked = false },
            title = { Text("Cellular downloads are disabled") },
            text = { Text("Change Downloads over cellular in Options to download on this network.") },
            confirmButton = {
                TextButton(onClick = { showCellularDownloadBlocked = false }) { Text("OK") }
            }
        )
    }
    pendingLocalDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingLocalDelete = null },
            title = { Text("Delete local copy?") },
            text = { Text("${book.title} will be removed from this device. Your BookOrbit book is not deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLocalDelete = null
                        onDeleteLocalCopy(book)
                    },
                    modifier = Modifier.testTag("confirm-delete-local-copy")
                ) { Text("Delete local") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalDelete = null }) { Text("Cancel") }
            }
        )
    }
    if (showChangeServerEditor) {
        AlertDialog(
            onDismissRequest = { showChangeServerEditor = false },
            title = { Text("Change server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the BookOrbit server you want to use.")
                    OutlinedTextField(
                        value = changeServerUrl,
                        onValueChange = {
                            changeServerUrl = it
                            changeServerError = null
                        },
                        label = { Text("Server URL") },
                        singleLine = true,
                        isError = changeServerError != null,
                        supportingText = changeServerError?.let { message ->
                            { Text(message) }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = normalizeServerUrl(changeServerUrl)
                        if (normalized == null) {
                            changeServerError = invalidServerUrlMessage()
                        } else if (serverUrlsMatch(state.serverUrl, normalized)) {
                            changeServerUrl = normalized
                            showChangeServerEditor = false
                        } else {
                            changeServerUrl = normalized
                            showChangeServerEditor = false
                            pendingServerChange = normalized
                        }
                    },
                    modifier = Modifier.testTag("submit-server-change")
                ) { Text("Change server") }
            },
            dismissButton = {
                TextButton(onClick = { showChangeServerEditor = false }) { Text("Cancel") }
            }
        )
    }
    pendingServerChange?.let { serverUrl ->
        val returnToEditor = {
            pendingServerChange = null
            showChangeServerEditor = true
        }
        AlertDialog(
            onDismissRequest = returnToEditor,
            title = { Text("Change server?") },
            text = {
                Text(
                    "Changing to $serverUrl will log you out of the current server and cancel active downloads."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingServerChange = null
                        onChangeServer(serverUrl)
                    },
                    modifier = Modifier.testTag("confirm-server-change")
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = returnToEditor) { Text("Cancel") }
            }
        )
    }

    val showBrowserBottomNavigation =
        !isSearchOpen &&
            activeBookGenre == null &&
            activeSeriesGenre == null &&
            selectedBook == null &&
            selectedSeriesKey == null &&
            selectedAuthor == null

    Scaffold(
        topBar = {
            when {
                isSearchOpen -> BrowserTopBar(
                    title = "Search",
                    navigationIcon = {
                        TextButton(onClick = {
                            isSearchOpen = false
                            query = ""
                        }) { Text("Back") }
                    },
                    onSearch = {},
                    onProfile = { showProfileMenu = true },
                    showSearchAction = false,
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = {
                        showProfileMenu = false
                        if (state.isOfflineSnapshot) onSignIn() else onSignOut()
                    },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                activeBookGenre != null -> BrowserTopBar(
                    title = "Books · ${activeBookGenre!!}",
                    navigationIcon = {
                        TextButton(onClick = {
                            activeBookGenre = null
                            selectedBook = genreSourceBook
                            genreSourceBook = null
                        }) { Text("Back") }
                    },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = { showProfileMenu = false; if (state.isOfflineSnapshot) onSignIn() else onSignOut() },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                activeSeriesGenre != null -> BrowserTopBar(
                    title = "Series · ${activeSeriesGenre!!}",
                    navigationIcon = {
                        TextButton(onClick = {
                            activeSeriesGenre = null
                            selectedSeriesKey = genreSourceSeriesKey
                            genreSourceSeriesKey = null
                        }) { Text("Back") }
                    },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = { showProfileMenu = false; if (state.isOfflineSnapshot) onSignIn() else onSignOut() },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedBook != null -> BrowserTopBar(
                    title = "Book details",
                    navigationIcon = { TextButton(onClick = { selectedBook = null }) { Text("Back") } },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = {
                        showProfileMenu = false
                        if (state.isOfflineSnapshot) onSignIn() else onSignOut()
                    },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedSeriesKey != null -> BrowserTopBar(
                    title = "Series",
                    navigationIcon = { TextButton(onClick = { selectedSeriesKey = null }) { Text("Back") } },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = {
                        showProfileMenu = false
                        if (state.isOfflineSnapshot) onSignIn() else onSignOut()
                    },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedAuthor != null -> BrowserTopBar(
                    title = "Author",
                    navigationIcon = { TextButton(onClick = { selectedAuthor = null; destination = BrowserDestination.AUTHORS }) { Text("Back") } },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = {
                        showProfileMenu = false
                        if (state.isOfflineSnapshot) onSignIn() else onSignOut()
                    },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                else -> BrowserTopBar(
                    title = when {
                        destination == BrowserDestination.LIBRARY && !showLibraryPicker ->
                            state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name ?: "Library"
                        destination == BrowserDestination.LIBRARY -> "Libraries"
                        destination == BrowserDestination.SERIES -> "Series"
                        destination == BrowserDestination.AUTHORS -> "Authors"
                        destination == BrowserDestination.LOCAL_BOOKS -> "Local books"
                        destination == BrowserDestination.ACHIEVEMENTS -> "Achievements"
                        destination == BrowserDestination.OPTIONS -> "Options"
                        destination == BrowserDestination.ABOUT -> "About"
                        else -> "Home"
                    },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    showBrand = destination == BrowserDestination.HOME,
                    onTitleClick = if (destination == BrowserDestination.LIBRARY) {
                        if (showLibraryPicker) {
                            { showLibraryPicker = false }
                        } else {
                            { showLibraryPicker = true }
                        }
                    } else null,
                    onSessionAction = {
                        showProfileMenu = false
                        if (state.isOfflineSnapshot) onSignIn() else onSignOut()
                    },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onAchievements = openAchievements,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
            }
        },
        bottomBar = {
            Column(
                modifier = if (showBrowserBottomNavigation) {
                    Modifier
                } else {
                    Modifier.navigationBarsPadding()
                }
            ) {
                bottomOverlay?.invoke()
                if (showBrowserBottomNavigation) {
                    BrowserBottomNavigation(
                        destination = destination,
                        onHome = {
                            destination = BrowserDestination.HOME
                            query = ""
                            selectedAuthor = null
                            selectedSeriesKey = null
                        },
                        onLibraries = {
                            destination = BrowserDestination.LIBRARY
                            showLibraryPicker = state.selectedLibraryId == null
                            libraryTab = LibraryTab.RECOMMENDED
                            query = ""
                            selectedAuthor = null
                            selectedSeriesKey = null
                        },
                        onMore = { showMoreMenu = true }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
            when {
                isSearchOpen -> SearchLayerContent(
                    query = query,
                    onQueryChange = { query = it },
                    books = filteredBooks,
                    isSearching = query.isNotBlank() && remoteSearchResults == null,
                    state = state,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        isSearchOpen = false
                        query = ""
                        detailReturnDestination = destination
                        selectedBook = book
                    },
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                activeBookGenre != null -> GenreBooksScreen(
                    genre = activeBookGenre!!,
                    state = state,
                    modifier = Modifier.padding(padding),
                    loader = libraryBooksPageLoader,
                    coverLoader = coverLoader,
                    onBookSelected = { selectedBook = it; activeBookGenre = null; genreSourceBook = null },
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                activeSeriesGenre != null -> SeriesCatalogScreen(
                    query = "",
                    initialFilter = SeriesCatalogFilter(genre = activeSeriesGenre),
                    libraryOptions = state.libraries,
                    modifier = Modifier.padding(padding),
                    loader = seriesCatalogLoader,
                    imageLoader = catalogImageLoader,
                    onSeriesSelected = { series ->
                        selectedSeriesKey = series.id
                        activeSeriesGenre = null
                        genreSourceSeriesKey = null
                        detailReturnDestination = BrowserDestination.SERIES
                    }
                )
                selectedBook != null -> BookDetails(
                    book = selectedBook!!,
                    state = state,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = bookDetailLoader,
                    seriesDetailLoader = seriesDetailLoader,
                    onRead = onBookOpen,
                    onPreview = onPreview,
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onSeriesSelected = { seriesKey ->
                        selectedSeriesKey = seriesKey
                        selectedBook = null
                    },
                    onBookSelected = { selectedBook = it },
                    onGenreSelected = { genre ->
                        genreSourceBook = selectedBook
                        selectedBook = null
                        activeBookGenre = genre
                    }
                )
                selectedSeriesKey != null -> SeriesDetails(
                    seriesKey = selectedSeriesKey!!,
                    books = state.books,
                    libraries = state.libraries,
                    groupingMode = appPreferences.seriesGroupingMode,
                    onGroupingModeChange = { mode ->
                        onAppPreferencesChange(appPreferences.copy(seriesGroupingMode = mode))
                    },
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = seriesDetailLoader,
                    onBookSelected = { selectedBook = it },
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onGenreSelected = { genre ->
                        genreSourceSeriesKey = selectedSeriesKey
                        selectedSeriesKey = null
                        activeSeriesGenre = genre
                    }
                )
                selectedAuthor != null -> AuthorDetails(
                    author = selectedAuthor!!,
                    modifier = Modifier.padding(padding),
                    booksLoader = authorBooksLoader,
                    coverLoader = coverLoader,
                    onBookSelected = { selectedBook = it },
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                destination == BrowserDestination.SERIES -> SeriesCatalogScreen(
                    query = "",
                    initialFilter = SeriesCatalogFilter(),
                    libraryOptions = state.libraries,
                    modifier = Modifier.padding(padding),
                    loader = seriesCatalogLoader,
                    imageLoader = catalogImageLoader,
                    onSeriesSelected = { series ->
                        selectedSeriesKey = series.id
                        detailReturnDestination = BrowserDestination.SERIES
                    }
                )
                destination == BrowserDestination.AUTHORS -> AuthorsCatalogScreen(
                    query = "",
                    modifier = Modifier.padding(padding),
                    loader = authorsCatalogLoader,
                    imageLoader = catalogImageLoader,
                    onAuthorSelected = { author -> selectedAuthor = author }
                )
                destination == BrowserDestination.LOCAL_BOOKS -> LocalBooksScreen(
                    state = state,
                    modifier = Modifier.padding(padding),
                    loader = localBooksLoader,
                    libraryId = localBooksLibraryId,
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        detailReturnDestination = BrowserDestination.LOCAL_BOOKS
                        selectedBook = book
                    },
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                destination == BrowserDestination.ACHIEVEMENTS -> AchievementsScreen(
                    loader = achievementsLoader,
                    modifier = Modifier.padding(padding)
                )
                destination == BrowserDestination.OPTIONS -> OptionsScreen(
                    preferences = appPreferences,
                    onPreferencesChange = onAppPreferencesChange,
                    storageUsageLoader = storageUsageLoader,
                    onClearCache = onClearCache,
                    modifier = Modifier.padding(padding)
                )
                destination == BrowserDestination.ABOUT -> AboutScreen(
                    state = state,
                    modifier = Modifier.padding(padding)
                )
                (destination == BrowserDestination.HOME || destination == BrowserDestination.LIBRARY) && query.isNotBlank() -> SearchResults(
                    books = filteredBooks,
                    state = state,
                    modifier = Modifier.padding(padding),
                    isSearching = remoteSearchResults == null,
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        detailReturnDestination = destination
                        query = ""
                        selectedBook = book
                    },
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                destination == BrowserDestination.HOME -> RefreshableHomeFeed(
                    state = state,
                    modifier = Modifier.padding(padding),
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        detailReturnDestination = BrowserDestination.HOME
                        selectedBook = book
                    },
                    onSeriesSelected = { seriesKey ->
                        detailReturnDestination = BrowserDestination.HOME
                        selectedSeriesKey = seriesKey
                    },
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onDismissMessage = onDismissMessage,
                    onLocalBooksSelected = {
                        localBooksLibraryId = null
                        destination = BrowserDestination.LOCAL_BOOKS
                    }
                )
                destination == BrowserDestination.LIBRARY && showLibraryPicker -> LibraryPickerScreen(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onLibrarySelected = { libraryId ->
                        showLibraryPicker = false
                        onLibrarySelected(libraryId)
                    }
                )
                destination == BrowserDestination.LIBRARY -> LibraryContentScreen(
                    state = state,
                    tab = libraryTab,
                    onTabChange = { libraryTab = it },
                    modifier = Modifier.padding(padding),
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        detailReturnDestination = BrowserDestination.LIBRARY
                        selectedBook = book
                    },
                    onSeriesSelected = { seriesKey ->
                        detailReturnDestination = BrowserDestination.LIBRARY
                        selectedSeriesKey = seriesKey
                    },
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onDismissMessage = onDismissMessage,
                    onLocalBooksSelected = {
                        localBooksLibraryId = state.selectedLibraryId
                        destination = BrowserDestination.LOCAL_BOOKS
                    }
                )
                else -> HomeFeed(
                    state = state,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    onBookSelected = { book -> selectedBook = book },
                    onSeriesSelected = { seriesKey -> selectedSeriesKey = seriesKey },
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onDismissMessage = onDismissMessage
                )
            }
    }
}

internal fun serverUrlsMatch(current: String, candidate: String): Boolean {
    val currentNormalized = normalizeServerUrl(current)?.toHttpUrlOrNull() ?: return false
    val candidateNormalized = normalizeServerUrl(candidate)?.toHttpUrlOrNull() ?: return false
    return currentNormalized == candidateNormalized
}

@Composable
private fun BrowserTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    profileExpanded: Boolean,
    onDismissProfile: () -> Unit,
    onSessionAction: () -> Unit,
    sessionActionLabel: String,
    onOptions: () -> Unit = {},
    onAchievements: () -> Unit = {},
    onAbout: () -> Unit = {},
    onChangeServer: () -> Unit = {},
    showSearchAction: Boolean = true,
    showBrand: Boolean = false,
    onTitleClick: (() -> Unit)? = null
) {
    BookOrbitTopBar(
        title = title,
        showBrand = showBrand,
        onTitleClick = onTitleClick,
        navigationIcon = navigationIcon,
        actions = {
            if (showSearchAction) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            Box {
                IconButton(onClick = onProfile) {
                    Icon(Icons.Default.Person, contentDescription = "User profile")
                }
                DropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = onDismissProfile
                ) {
                    DropdownMenuItem(
                        text = { Text("Achievements") },
                        leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                        onClick = {
                            onDismissProfile()
                            onAchievements()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Options") },
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = "Options icon")
                        },
                        onClick = {
                            onDismissProfile()
                            onOptions()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("About") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            onDismissProfile()
                            onAbout()
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("profile-session-divider")
                    )
                    DropdownMenuItem(
                        text = { Text("Change server") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        onClick = onChangeServer
                    )
                    DropdownMenuItem(
                        text = { Text(sessionActionLabel) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                        onClick = onSessionAction
                    )
                }
            }
        }
    )
}

@Composable
private fun BrowserBottomNavigation(
    destination: BrowserDestination,
    onHome: () -> Unit,
    onLibraries: () -> Unit,
    onMore: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = destination == BrowserDestination.HOME,
            onClick = onHome,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = destination == BrowserDestination.LIBRARY,
            onClick = onLibraries,
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("Libraries") }
        )
        NavigationBarItem(
            selected = destination == BrowserDestination.SERIES ||
                destination == BrowserDestination.AUTHORS ||
                destination == BrowserDestination.LOCAL_BOOKS ||
                destination == BrowserDestination.ACHIEVEMENTS ||
                destination == BrowserDestination.OPTIONS ||
                destination == BrowserDestination.ABOUT,
            onClick = onMore,
            icon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
            label = { Text("More") }
        )
    }
}

@Composable
private fun MoreMenu(
    onSeries: () -> Unit,
    onAuthors: () -> Unit,
    onLocalBooks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 16.dp, bottom = 72.dp)
    ) {
        Text(
            "More",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall
        )
        ListItem(
            headlineContent = { Text("Series") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onSeries)
        )
        ListItem(
            headlineContent = { Text("Authors") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onAuthors)
        )
        ListItem(
            headlineContent = { Text("Local books") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onLocalBooks)
        )
    }
}

@Composable
private fun SearchLayerContent(
    query: String,
    onQueryChange: (String) -> Unit,
    books: List<BookSummary>,
    isSearching: Boolean,
    state: BrowserState,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )
        if (query.isBlank()) {
            Text(
                "Search across all accessible books.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SearchResults(
                books = books,
                state = state,
                modifier = Modifier.weight(1f),
                isSearching = isSearching,
                coverLoader = coverLoader,
                onBookSelected = onBookSelected,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDeleteLocalCopy = onDeleteLocalCopy,
                onMarkAsRead = onMarkAsRead,
                onMarkAsUnread = onMarkAsUnread
            )
        }
    }
}

@Composable
private fun LibraryPickerScreen(
    state: BrowserState,
    modifier: Modifier,
    onLibrarySelected: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Choose a library", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Select which library to browse.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.message?.let { message ->
            item {
                OrbitMessage(
                    text = message,
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR
                )
            }
        }
        if (state.isLoadingLibraries) {
            item { LoadingFeedRow("Loading libraries...") }
        }
        if (!state.isLoadingLibraries && state.libraries.isEmpty()) {
            item { Text("No libraries found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.libraries, key = { "library-picker-${it.id}" }) { library ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isLoadingBooks) { onLibrarySelected(library.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(library.name, style = MaterialTheme.typography.titleMedium)
                    library.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCatalogScreen(
    query: String,
    initialFilter: SeriesCatalogFilter,
    libraryOptions: List<LibrarySummary>,
    modifier: Modifier,
    loader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    imageLoader: suspend (String) -> ByteArray?,
    onSeriesSelected: (SeriesSummary) -> Unit
) {
    var items by remember(query, initialFilter) { mutableStateOf<List<SeriesSummary>>(emptyList()) }
    var total by remember(query, initialFilter) { mutableStateOf(0) }
    var isLoading by remember(query, initialFilter) { mutableStateOf(false) }
    var filter by remember(query, initialFilter) {
        mutableStateOf(initialFilter.copy(query = query.takeIf { it.isNotBlank() }))
    }
    var showFilter by rememberSaveable(query, initialFilter) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(query, filter) {
        isLoading = true
        gridState.scrollToItem(0)
        if (query.isNotBlank()) delay(300)
        val activeFilter = filter.copy(query = query.takeIf { it.isNotBlank() })
        val catalog = loadCompleteSeriesCatalog { page -> loader(activeFilter, page) }
        items = catalog.items
        total = catalog.total ?: catalog.items.size
        isLoading = false
    }
    val jumpTargets = remember(items, filter.sort, filter.direction, isLoading) {
        if (!isLoading && filter.sort == SeriesSortOption.NAME) {
            buildSeriesJumpTargets(items, filter.direction)
        } else {
            emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val hasJumpRail = jumpTargets.isNotEmpty()
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CATALOG_GRID_PADDING,
                top = CATALOG_GRID_PADDING,
                end = catalogGridEndPadding(hasJumpRail),
                bottom = CATALOG_GRID_PADDING
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (total > 0) "$total series" else "Browse every accessible series",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { showFilter = true }) {
                    Text(if (filter.isActive) "Filter · active" else "Filter")
                }
            }
        }
        if (isLoading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow("Loading series...") }
        if (!isLoading && items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No series found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        gridItems(items, key = { "catalog-series-${it.id}" }) { series ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("series_card_${series.id}")
                    .clickable { onSeriesSelected(series) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CatalogImage(
                        url = series.coverUrl,
                        label = "Cover for ${series.name}",
                        loader = imageLoader,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                    )
                    Text(series.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (series.authors.isNotEmpty()) {
                        Text(series.authors.joinToString(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        "${series.bookCount} books · ${series.readCount} read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
        if (hasJumpRail) {
            LibraryJumpRail(
                targets = jumpTargets,
                direction = filter.direction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 12.dp),
                onJump = { index ->
                    scope.launch {
                        if (reduceMotion) {
                            gridState.scrollToItem(index + 1)
                        } else {
                            gridState.animateScrollToItem(index + 1)
                        }
                    }
                }
            )
        }
    }
    if (showFilter) {
        SeriesFilterSheet(
            initial = filter,
            libraries = libraryOptions,
            onDismiss = { showFilter = false },
            onApply = {
                filter = it
                showFilter = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookFilterSheet(
    initial: BookBrowseFilter,
    onDismiss: () -> Unit,
    onApply: (BookBrowseFilter) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Filter books", style = MaterialTheme.typography.titleLarge)
            Text("These controls map to BookOrbit's readProgress, format, and sort fields.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = draft.title.orEmpty(),
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text("Title contains") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.author.orEmpty(),
                onValueChange = { draft = draft.copy(author = it) },
                label = { Text("Author contains") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.series.orEmpty(),
                onValueChange = { draft = draft.copy(series = it) },
                label = { Text("Series contains") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Reading status", style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = BookReadFilter.entries,
                selected = draft.readStatus,
                label = { it.label },
                onSelected = { draft = draft.copy(readStatus = it) }
            )
            Text("Format", style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = BookFormatFilter.entries,
                selected = draft.format,
                label = { it.label },
                onSelected = { draft = draft.copy(format = it) }
            )
            Text("Sort", style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = BookSortOption.entries,
                selected = draft.sort,
                label = { it.label },
                onSelected = { draft = draft.copy(sort = it) }
            )
            if (draft.sort != BookSortOption.SERVER_DEFAULT) {
                FilterChoiceRow(
                    options = SortDirection.entries,
                    selected = draft.direction,
                    label = { it.label },
                    onSelected = { draft = draft.copy(direction = it) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = BookBrowseFilter() }) { Text("Reset") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SeriesFilterSheet(
    initial: SeriesCatalogFilter,
    libraries: List<LibrarySummary>,
    onDismiss: () -> Unit,
    onApply: (SeriesCatalogFilter) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Filter series", style = MaterialTheme.typography.titleLarge)
            Text("These controls map to BookOrbit's completionStatus, author, and series sort fields.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = draft.author.orEmpty(),
                onValueChange = { draft = draft.copy(author = it) },
                label = { Text("Author") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.genre.orEmpty(),
                onValueChange = { draft = draft.copy(genre = it) },
                label = { Text("Genre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (libraries.isNotEmpty()) {
                Text("Library", style = MaterialTheme.typography.titleMedium)
                val libraryIds = listOf<String?>(null) + libraries.map { it.id }
                FilterChoiceRow(
                    options = libraryIds,
                    selected = draft.libraryId,
                    label = { id -> libraries.firstOrNull { it.id == id }?.name ?: "All libraries" },
                    onSelected = { draft = draft.copy(libraryId = it) }
                )
            }
            Text("Completion", style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = SeriesCompletionFilter.entries,
                selected = draft.completion,
                label = { it.label },
                onSelected = { draft = draft.copy(completion = it) }
            )
            Text("Sort", style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = SeriesSortOption.entries,
                selected = draft.sort,
                label = { it.label },
                onSelected = { draft = draft.copy(sort = it) }
            )
            FilterChoiceRow(
                options = SortDirection.entries,
                selected = draft.direction,
                label = { it.label },
                onSelected = { draft = draft.copy(direction = it) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = SeriesCatalogFilter(query = draft.query) }) { Text("Reset") }
                Button(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun <T> FilterChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
private fun AuthorsCatalogScreen(
    query: String,
    modifier: Modifier,
    loader: suspend (String?, Int) -> AuthorCatalogPage,
    imageLoader: suspend (String) -> ByteArray?,
    onAuthorSelected: (AuthorSummary) -> Unit
) {
    var items by remember(query) { mutableStateOf<List<AuthorSummary>>(emptyList()) }
    var total by remember(query) { mutableStateOf(0) }
    var nextPage by remember(query) { mutableStateOf(1) }
    var isLoading by remember(query) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        isLoading = true
        if (query.isNotBlank()) delay(300)
        val page = loader(query.takeIf { it.isNotBlank() }, 0)
        items = page.items
        total = page.total ?: page.items.size
        nextPage = 1
        isLoading = false
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (total > 0) "$total authors" else "Browse every accessible author",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLoading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow("Loading authors...") }
        if (!isLoading && items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No authors found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        gridItems(items, key = { "catalog-author-${it.id}" }) { author ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAuthorSelected(author) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CatalogImage(
                        url = author.photoUrl,
                        label = "Photo of ${author.name}",
                        loader = imageLoader,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                    Text(author.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${author.bookCount} books",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!isLoading && items.size < total) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val page = loader(query.takeIf { it.isNotBlank() }, nextPage)
                            items = (items + page.items).distinctBy { it.id }
                            total = page.total ?: total
                            nextPage += 1
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Load more") }
            }
        }
    }
}

@Composable
private fun AuthorDetails(
    author: AuthorSummary,
    modifier: Modifier,
    booksLoader: suspend (String, Int) -> AuthorBooksPage?,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val page by produceState<AuthorBooksPage?>(initialValue = null, author.id) {
        value = booksLoader(author.id, 0)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OrbitEyebrow("Author")
                Text(author.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${page?.total ?: author.bookCount} books",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (page == null) item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow("Loading books...") }
        if (page != null && page!!.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No books found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        gridItems(page?.items.orEmpty(), key = { "author-book-${it.id}" }) { book ->
            BookPosterCard(
                book = book,
                coverLoader = coverLoader,
                onClick = { onBookSelected(book) },
                onMarkAsRead = { onMarkAsRead(book) },
                onMarkAsUnread = { onMarkAsUnread(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookPosterCard(
    book: BookSummary,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onClick: () -> Unit,
    showSeriesIndex: Boolean = false,
    enabled: Boolean = true,
    displayTitle: String = book.title,
    supportingText: String? = null,
    onMarkAsRead: (() -> Unit)? = null,
    onMarkAsUnread: (() -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null
) {
    val isBookCard = supportingText == null && displayTitle == book.title
    val hasActions = enabled && (onMarkAsRead != null || onMarkAsUnread != null)
    var showActions by remember(book.id) { mutableStateOf(false) }
    val status = when {
        !enabled -> "Unavailable offline"
        book.isRead && book.isDownloaded -> "Read · Offline"
        book.isRead -> "Read"
        book.isDownloaded -> "Offline"
        book.progressPercent?.let { it > 0f } == true -> "In progress"
        else -> null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = if (selectionMode && onToggleSelection != null) onToggleSelection else onClick,
                onLongClick = if (onToggleSelection != null) onToggleSelection else if (hasActions) ({ showActions = true }) else null
            )
            .semantics {
                contentDescription = buildString {
                    append(displayTitle)
                    supportingText?.let { append(", $it") }
                    status?.let { append(", $it") }
                }
                if (!enabled) disabled()
                if (isSelected) stateDescription = "Selected"
            },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            BookCardCoverSlot(book, coverLoader) {
                if (hasActions) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(
                            onClick = { showActions = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options for ${book.title}"
                            )
                        }
                        DropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false }
                        ) {
                            onMarkAsRead?.let { markAsRead ->
                                DropdownMenuItem(
                                    text = { Text("Mark as read") },
                                    onClick = {
                                        showActions = false
                                        markAsRead()
                                    }
                                )
                            }
                            onMarkAsUnread?.let { markAsUnread ->
                                DropdownMenuItem(
                                    text = { Text("Mark as unread") },
                                    onClick = {
                                        showActions = false
                                        markAsUnread()
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Text(
                displayTitle,
                maxLines = if (isBookCard && book.seriesName.isNullOrBlank()) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            if (isBookCard) {
                book.seriesName?.takeIf { it.isNotBlank() }?.let { seriesName ->
                    Text(
                        seriesName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (showSeriesIndex || !book.seriesName.isNullOrBlank()) {
                    book.seriesIndex?.let { index ->
                        Text(
                            "#${formatSeriesIndex(index)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else {
                supportingText?.let { text ->
                    Text(
                        text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!book.author.isNullOrBlank()) {
                    Text(
                        book.author,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            status?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
internal fun OptionsScreen(
    preferences: AppPreferences,
    onPreferencesChange: (AppPreferences) -> Unit,
    storageUsageLoader: suspend () -> StorageUsage = { StorageUsage() },
    onClearCache: suspend () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var openDialog by rememberSaveable { mutableStateOf<OptionsDialog?>(null) }
    var storageRefreshKey by rememberSaveable { mutableStateOf(0) }
    var isClearingCache by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val storageUsage by produceState<StorageUsage?>(initialValue = null, storageRefreshKey) {
        value = runCatching { storageUsageLoader() }.getOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
    ) {
        item(key = "options-intro") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitEyebrow("Options")
                Text("Interface", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Choose how Lagrange looks and responds.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item(key = "lock-orientation") {
            AppPreferenceSwitchRow(
                title = "Lock orientation",
                summary = "Keep the orientation currently in use",
                checked = preferences.lockOrientation,
                testTag = "options-lock-orientation",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(lockOrientation = it))
                }
            )
        }
        item(key = "theme") {
            AppPreferenceSelectionRow(
                title = "Theme",
                value = preferences.themeMode.displayName,
                testTag = "options-theme",
                onClick = { openDialog = OptionsDialog.THEME }
            )
        }
        item(key = "opening-screen") {
            AppPreferenceSelectionRow(
                title = "Default opening screen",
                value = preferences.defaultOpeningScreen.displayName,
                summary = "Used the next time the app starts",
                testTag = "options-opening-screen",
                onClick = { openDialog = OptionsDialog.OPENING_SCREEN }
            )
        }
        item(key = "reduce-motion") {
            AppPreferenceSwitchRow(
                title = "Reduce motion",
                summary = "Use immediate catalog jumps instead of animated scrolling",
                checked = preferences.reduceMotion,
                testTag = "options-reduce-motion",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(reduceMotion = it))
                }
            )
        }
        item(key = "data-heading") {
            Column(
                modifier = Modifier.padding(start = 4.dp, top = 26.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitEyebrow("Data")
                Text("Network and storage", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Control automatic network use and local files.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item(key = "cellular-downloads") {
            AppPreferenceSelectionRow(
                title = "Downloads over cellular",
                value = preferences.cellularDownloadPolicy.displayName,
                summary = "Metered non-Wi-Fi networks are treated as cellular",
                testTag = "options-cellular-downloads",
                onClick = { openDialog = OptionsDialog.CELLULAR_DOWNLOADS }
            )
        }
        item(key = "storage") {
            ListItem(
                headlineContent = { Text("Storage") },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            storageUsage?.let {
                                "Downloads ${formatByteSize(it.downloadedBytes)} · Cache ${formatByteSize(it.cacheBytes)}"
                            } ?: "Calculating storage use…"
                        )
                        storageMessage?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "Clear cache never deletes downloaded books",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                trailingContent = {
                    TextButton(
                        onClick = { openDialog = OptionsDialog.CLEAR_CACHE },
                        enabled = !isClearingCache,
                        modifier = Modifier.testTag("options-clear-cache")
                    ) {
                        Text(if (isClearingCache) "Clearing…" else "Clear cache")
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("options-storage")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item(key = "background-refresh") {
            AppPreferenceSelectionRow(
                title = "Background metadata and covers",
                value = preferences.backgroundRefreshNetworkPolicy.displayName,
                summary = "Manual refresh remains available on any network",
                testTag = "options-background-refresh",
                onClick = { openDialog = OptionsDialog.BACKGROUND_REFRESH }
            )
        }
        item(key = "confirm-local-delete") {
            AppPreferenceSwitchRow(
                title = "Confirm before deleting local copy",
                summary = "Ask before removing a downloaded file from this device",
                checked = preferences.confirmDeleteLocalCopy,
                testTag = "options-confirm-local-delete",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(confirmDeleteLocalCopy = it))
                }
            )
        }
    }

    when (openDialog) {
        OptionsDialog.THEME -> AppPreferenceChoiceDialog(
            title = "Theme",
            choices = AppThemeMode.values().toList(),
            selected = preferences.themeMode,
            label = AppThemeMode::displayName,
            onSelect = {
                onPreferencesChange(preferences.copy(themeMode = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.OPENING_SCREEN -> AppPreferenceChoiceDialog(
            title = "Default opening screen",
            choices = DefaultOpeningScreen.values().toList(),
            selected = preferences.defaultOpeningScreen,
            label = DefaultOpeningScreen::displayName,
            onSelect = {
                onPreferencesChange(preferences.copy(defaultOpeningScreen = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.CELLULAR_DOWNLOADS -> AppPreferenceChoiceDialog(
            title = "Downloads over cellular",
            choices = CellularDownloadPolicy.values().toList(),
            selected = preferences.cellularDownloadPolicy,
            label = CellularDownloadPolicy::displayName,
            onSelect = {
                onPreferencesChange(preferences.copy(cellularDownloadPolicy = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.BACKGROUND_REFRESH -> AppPreferenceChoiceDialog(
            title = "Background metadata and covers",
            choices = BackgroundRefreshNetworkPolicy.values().toList(),
            selected = preferences.backgroundRefreshNetworkPolicy,
            label = BackgroundRefreshNetworkPolicy::displayName,
            onSelect = {
                onPreferencesChange(preferences.copy(backgroundRefreshNetworkPolicy = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.CLEAR_CACHE -> AlertDialog(
            onDismissRequest = { openDialog = null },
            title = { Text("Clear cache?") },
            text = { Text("Cached covers and temporary reader files will be removed. Downloaded books are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        openDialog = null
                        isClearingCache = true
                        storageMessage = null
                        scope.launch {
                            runCatching { onClearCache() }
                                .onSuccess {
                                    storageMessage = "Cache cleared"
                                    storageRefreshKey += 1
                                }
                                .onFailure { storageMessage = "Unable to clear cache" }
                            isClearingCache = false
                        }
                    },
                    modifier = Modifier.testTag("confirm-clear-cache")
                ) { Text("Clear cache") }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = null }) { Text("Cancel") }
            }
        )
        null -> Unit
    }
}

@Composable
private fun AppPreferenceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onCheckedChange(!checked) }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AppPreferenceSelectionRow(
    title: String,
    value: String,
    testTag: String,
    summary: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(value, color = MaterialTheme.colorScheme.primary)
                summary?.let { Text(it) }
            }
        },
        trailingContent = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun <T> AppPreferenceChoiceDialog(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = choice == selected,
                            onClick = { onSelect(choice) }
                        )
                        Text(label(choice), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AboutScreen(
    state: BrowserState,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OrbitEyebrow("About")
        Text("Lagrange", style = MaterialTheme.typography.headlineSmall)
        Text(
            "A native Android reader for books hosted on BookOrbit.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        Text("Connected server", style = MaterialTheme.typography.labelMedium)
        Text(state.serverUrl, style = MaterialTheme.typography.bodySmall)
        Text(
            "About details and acknowledgements will be expanded here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CatalogImage(
    url: String?,
    label: String,
    loader: suspend (String) -> ByteArray?,
    modifier: Modifier
) {
    val bytes by produceState<ByteArray?>(initialValue = null, url) {
        value = url?.let { imageUrl -> loadCatalogImageWithRetry(imageUrl, loader) }
    }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(label.substringAfterLast(" ").take(1).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RefreshableHomeFeed(
    state: BrowserState,
    modifier: Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onDismissMessage: () -> Unit,
    onLocalBooksSelected: () -> Unit
) {
    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_pull_to_refresh")
    ) {
        HomeFeed(
            state = state,
            books = state.homeBooks,
            modifier = Modifier.fillMaxSize(),
            coverLoader = coverLoader,
            onBookSelected = onBookSelected,
            onSeriesSelected = onSeriesSelected,
            onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
            onMarkAsRead = onMarkAsRead,
            onMarkAsUnread = onMarkAsUnread,
            onDismissMessage = onDismissMessage,
            onLocalBooksSelected = onLocalBooksSelected
        )
    }
}

@Composable
private fun HomeFeed(
    state: BrowserState,
    books: List<BookSummary> = state.books,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onDismissMessage: (() -> Unit)? = null,
    onLocalBooksSelected: (() -> Unit)? = null,
    localBooksLibraryId: String? = null,
    showHeader: Boolean = false
) {
    val currentlyReading = remember(books) { currentlyReadingBooks(books) }
    val onDeck = remember(books) { onDeckBooks(books) }
    val recentlyAddedBooks = books.sortedWith(
        compareByDescending<BookSummary> { it.addedAtMillis != null }
            .thenByDescending { it.addedAtMillis ?: 0L }
    ).take(12)
    val recentSeries = remember(books) { recentSeries(books, useUpdatedAt = false) }
    val updatedSeries = remember(books) { recentSeries(books, useUpdatedAt = true) }
    val recentlyRead = remember(books) { recentlyReadBooks(books) }
    val localBooks = remember(books, localBooksLibraryId) {
        localBooksShelf(
            books = books,
            libraryId = localBooksLibraryId
        )
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val availableMarkAsRead = onMarkAsRead.takeUnless { state.isOfflineSnapshot }
    val availableMarkAsUnread = onMarkAsUnread.takeUnless { state.isOfflineSnapshot }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (showHeader) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Home", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name
                            ?: "Your library",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        state.message?.let { message ->
            item {
                OrbitMessage(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR,
                    onDismiss = onDismissMessage
                )
            }
        }
        if (currentlyReading.isNotEmpty()) {
            item {
                BookShelf(
                    title = "Currently reading",
                    books = currentlyReading,
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading.takeUnless {
                        state.isOfflineSnapshot
                    },
                    onMarkAsRead = availableMarkAsRead,
                    onMarkAsUnread = availableMarkAsUnread
                )
            }
        }
        if (onDeck.isNotEmpty()) item {
            BookShelf("On deck", onDeck, coverLoader, onBookSelected, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (recentlyAddedBooks.isNotEmpty()) item {
            BookShelf("Recently added books", recentlyAddedBooks, coverLoader, onBookSelected, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (recentSeries.isNotEmpty()) item { SeriesShelf("Recently added series", recentSeries, coverLoader, onSeriesSelected) }
        if (updatedSeries.isNotEmpty()) item { SeriesShelf("Recently updated series", updatedSeries, coverLoader, onSeriesSelected) }
        if (recentlyRead.isNotEmpty()) item {
            BookShelf("Recently read books", recentlyRead, coverLoader, onBookSelected, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (localBooks.isNotEmpty()) item {
            BookShelf(
                title = "Local books",
                books = localBooks,
                coverLoader = coverLoader,
                onBookSelected = onBookSelected,
                onMarkAsRead = availableMarkAsRead,
                onMarkAsUnread = availableMarkAsUnread,
                onSeeAll = onLocalBooksSelected
            )
        }
        if (state.isLoadingBooks) item { LoadingFeedRow("Loading books...") }
        if (!state.isLoadingBooks && state.books.isEmpty()) {
            item {
                Text(
                    "No books found in this library.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isDebug) {
            item {
                Text(
                    "Pending sync queue: ${state.debugPendingProgressCount}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BookShelf(
    title: String,
    books: List<BookSummary>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onRemoveFromCurrentlyReading: ((BookSummary) -> Unit)? = null,
    onMarkAsRead: ((BookSummary) -> Unit)? = null,
    onMarkAsUnread: ((BookSummary) -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfTitle(title, onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books, key = { "$title-${it.id}" }) { book ->
                ShelfBookCard(
                    book = book,
                    coverLoader = coverLoader,
                    onClick = { onBookSelected(book) },
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading?.let { remove ->
                        { remove(book) }
                    },
                    onMarkAsRead = onMarkAsRead?.let { mark -> { mark(book) } },
                    onMarkAsUnread = onMarkAsUnread?.let { mark -> { mark(book) } }
                )
            }
        }
    }
}

@Composable
private fun SeriesShelf(
    title: String,
    series: List<Pair<String, BookSummary>>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onSeriesSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series, key = { "$title-${it.first}" }) { (name, book) ->
                ShelfBookCard(
                    book = book,
                    displayTitle = name,
                    coverLoader = coverLoader,
                    onClick = { onSeriesSelected(book.seriesId ?: name) }
                )
            }
        }
    }
}

@Composable
private fun ShelfTitle(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookCard(
    book: BookSummary,
    displayTitle: String = book.title,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onClick: () -> Unit,
    onRemoveFromCurrentlyReading: (() -> Unit)? = null,
    onMarkAsRead: (() -> Unit)? = null,
    onMarkAsUnread: (() -> Unit)? = null
) {
    val isBookCard = displayTitle == book.title
    var showActions by remember(book.id) { mutableStateOf(false) }
    val hasActions = onRemoveFromCurrentlyReading != null || onMarkAsRead != null || onMarkAsUnread != null
    Column(
        modifier = Modifier
            .width(84.dp)
            .testTag("book_card_${book.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (hasActions) ({ showActions = true }) else null
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BookCardCoverSlot(book, coverLoader) {
            if (hasActions) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { showActions = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options for ${book.title}"
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false }
                    ) {
                        onMarkAsRead?.let { markAsRead ->
                            DropdownMenuItem(
                                text = { Text("Mark as read") },
                                onClick = {
                                    showActions = false
                                    markAsRead()
                                }
                            )
                        }
                        onMarkAsUnread?.let { markAsUnread ->
                            DropdownMenuItem(
                                text = { Text("Mark as unread") },
                                onClick = {
                                    showActions = false
                                    markAsUnread()
                                }
                            )
                        }
                        onRemoveFromCurrentlyReading?.let { remove ->
                            DropdownMenuItem(
                                text = { Text("Remove from Currently reading") },
                                onClick = {
                                    showActions = false
                                    remove()
                                }
                            )
                        }
                    }
                }
            }
        }
        Text(
            displayTitle,
            maxLines = if (isBookCard && book.seriesName.isNullOrBlank()) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        if (isBookCard) {
            book.seriesName?.takeIf { it.isNotBlank() }?.let { seriesName ->
                Text(
                    seriesName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            book.seriesIndex?.let { index ->
                Text(
                    "#${formatSeriesIndex(index)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else if (!book.author.isNullOrBlank()) {
            Text(
                book.author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal val BOOK_CARD_COVER_SLOT_ASPECT_RATIO = CoverAspectRatio.PORTRAIT.widthToHeight
internal val BOOK_CARD_COVER_ALIGNMENT: Alignment = Alignment.BottomCenter

@Composable
private fun BookCardCoverSlot(
    book: BookSummary,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BOOK_CARD_COVER_SLOT_ASPECT_RATIO),
        contentAlignment = BOOK_CARD_COVER_ALIGNMENT
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(book.coverAspectRatio.widthToHeight),
            content = {
                BookCover(book, coverLoader)
                overlay()
            }
        )
    }
}

@Composable
private fun BookCover(book: BookSummary, coverLoader: suspend (BookSummary) -> ByteArray?) {
    val bitmap by produceState<Bitmap?>(initialValue = null, book.id, book.coverUrl, book.updatedAtMillis) {
        value = loadScaledCover(book, coverLoader)
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(book.coverAspectRatio.widthToHeight)
            .clip(MaterialTheme.shapes.small)
            .background(Brush.linearGradient(colors))
            .semantics { contentDescription = "Cover for ${book.title}" },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Cover for ${book.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
        Text(
            book.title.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.displaySmall
        )
        }
        book.progressPercent?.takeIf { it > 0f }?.let { progress ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 100f) / 100f)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}

private suspend fun loadScaledCover(
    book: BookSummary,
    coverLoader: suspend (BookSummary) -> ByteArray?
): Bitmap? {
    val key = coverBitmapCacheKey(book)
    coverBitmapCache.get(key)?.let { return it }
    val lock = coverLoadLocks[(key.hashCode() and Int.MAX_VALUE) % coverLoadLocks.size]
    return lock.withLock {
        coverBitmapCache.get(key)?.let { return@withLock it }
        repeat(2) { attempt ->
            val bytes = try {
                coverLoader(book)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            val bitmap = if (bytes != null && bytes.isNotEmpty()) {
                try {
                    withContext(Dispatchers.Default) {
                        decodeCoverBitmap(bytes, targetWidth = 256, targetHeight = 384)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            if (bitmap != null) {
                coverBitmapCache.put(key, bitmap)
                return@withLock bitmap
            }
            if (attempt == 0) delay(120)
        }
        null
    }
}

internal fun coverBitmapCacheKey(book: BookSummary): String = buildString {
    append(book.coverUrl ?: "book:${book.id}")
    book.updatedAtMillis?.let { append("#updated=").append(it) }
}

internal fun decodeCoverBitmap(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateCoverSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

internal fun calculateCoverSampleSize(
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    var sampleSize = 1
    while (width / (sampleSize * 2) >= targetWidth && height / (sampleSize * 2) >= targetHeight) {
        sampleSize *= 2
    }
    return sampleSize
}

@Composable
private fun SearchResults(
    books: List<BookSummary>,
    state: BrowserState,
    modifier: Modifier,
    isSearching: Boolean,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    LibraryBookList(
        title = "Search results",
        books = books,
        state = state,
        modifier = modifier,
        isLoading = isSearching,
        coverLoader = coverLoader,
        onBookSelected = onBookSelected,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onDeleteLocalCopy = onDeleteLocalCopy,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LibraryContentScreen(
    state: BrowserState,
    tab: LibraryTab,
    onTabChange: (LibraryTab) -> Unit,
    modifier: Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onDismissMessage: () -> Unit,
    onLocalBooksSelected: () -> Unit
) {
    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == LibraryTab.RECOMMENDED,
                    onClick = { onTabChange(LibraryTab.RECOMMENDED) },
                    text = { Text("Recommended") }
                )
                Tab(
                    selected = tab == LibraryTab.BROWSE,
                    onClick = { onTabChange(LibraryTab.BROWSE) },
                    text = { Text("Browse") }
                )
            }
            when (tab) {
                LibraryTab.RECOMMENDED -> HomeFeed(
                    state = state,
                    modifier = Modifier.weight(1f),
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    onSeriesSelected = onSeriesSelected,
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onDismissMessage = onDismissMessage,
                    onLocalBooksSelected = onLocalBooksSelected,
                    localBooksLibraryId = state.selectedLibraryId,
                    showHeader = false
                )
                LibraryTab.BROWSE -> LibraryBrowseScreen(
                    state = state,
                    modifier = Modifier.weight(1f),
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    onSeriesSelected = onSeriesSelected,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val pullState = rememberPullToRefreshState(enabled = { !isRefreshing })
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) pullState.startRefresh() else pullState.endRefresh()
    }
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing && !isRefreshing) onRefresh()
    }
    Box(modifier = modifier.nestedScroll(pullState.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun LibraryBrowseScreen(
    state: BrowserState,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val libraryId = state.selectedLibraryId
    var filter by remember(libraryId) { mutableStateOf(BookBrowseFilter()) }
    var showFilter by rememberSaveable(libraryId) { mutableStateOf(false) }
    val books = remember(state.books, filter) { filterAndSortLocalBooks(state.books, filter) }
    val total = if (filter.isActive) books.size else state.booksTotal ?: books.size
    val seriesTotal = state.booksSeriesTotal.takeUnless { filter.isActive }

    LibraryBooks(
        state = state.copy(
            books = books,
            booksTotal = total,
            booksSeriesTotal = seriesTotal,
            isLoadingBooks = state.isLoadingBooks && books.isEmpty()
        ),
        modifier = modifier,
        coverLoader = coverLoader,
        onBookSelected = onBookSelected,
        onSeriesSelected = onSeriesSelected,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread,
        totalBooks = total,
        totalSeries = seriesTotal,
        filter = filter,
        jumpRailEnabled = state.isCatalogComplete,
        serverJumpBuckets = state.libraryJumpBuckets.takeIf { filter == BookBrowseFilter() }.orEmpty(),
        onFilterClick = { showFilter = true }
    )
    if (showFilter) {
        BookFilterSheet(
            initial = filter,
            onDismiss = { showFilter = false },
            onApply = {
                filter = it
                showFilter = false
            }
        )
    }
}

@Composable
private fun GenreBooksScreen(
    genre: String,
    state: BrowserState,
    modifier: Modifier,
    loader: suspend (String, Int, BookBrowseFilter) -> LibraryBooksPage,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val libraryId = state.selectedLibraryId
    var loadError by remember(libraryId, genre) { mutableStateOf<String?>(null) }
    val books by produceState<List<BookSummary>?>(initialValue = null, libraryId, genre) {
        loadError = null
        if (libraryId == null) {
            value = emptyList()
            loadError = "Select a library before filtering books by genre."
            return@produceState
        }
        try {
            val pages = loadCompleteLibraryPages { page ->
                loader(libraryId, page, BookBrowseFilter(genre = genre))
            }
            value = mergeLibraryBooks(pages)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            value = emptyList()
            loadError = error.message ?: "Unable to load books in this genre."
        }
    }
    val filteredBooks = books.orEmpty()
    LibraryBooks(
        state = state.copy(
            books = filteredBooks,
            booksTotal = filteredBooks.size,
            booksSeriesTotal = null,
            isLoadingBooks = books == null,
            message = loadError
        ),
        modifier = modifier,
        coverLoader = coverLoader,
        onBookSelected = onBookSelected,
        titleOverride = genre,
        emptyMessage = "No books found in $genre.",
        allowSeriesCollapse = false,
        totalBooks = filteredBooks.size,
        filter = BookBrowseFilter(genre = genre),
        jumpRailEnabled = false,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread
    )
}

@Composable
private fun LibraryBooks(
    state: BrowserState,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit = {},
    titleOverride: String? = null,
    emptyMessage: String = "No books found.",
    allowSeriesCollapse: Boolean = true,
    totalBooks: Int? = null,
    totalSeries: Int? = null,
    filter: BookBrowseFilter? = null,
    jumpRailEnabled: Boolean = true,
    serverJumpBuckets: List<LibraryJumpBucket> = emptyList(),
    onFilterClick: (() -> Unit)? = null,
    onMarkAsRead: ((BookSummary) -> Unit)? = null,
    onMarkAsUnread: ((BookSummary) -> Unit)? = null
) {
    val title = titleOverride
        ?: state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name
        ?: "Library"
    var seriesCollapsed by rememberSaveable(title) { mutableStateOf(false) }
    var selectedBookIds by remember(title) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedBooks = state.books.filter { it.id in selectedBookIds }
    LaunchedEffect(state.books) {
        selectedBookIds = selectedBookIds.intersect(state.books.mapTo(mutableSetOf()) { it.id })
    }
    val seriesKeys = state.books
        .mapNotNull { it.seriesId ?: it.seriesName }
        .filter { it.isNotBlank() }
        .distinct()
    val seriesCount = librarySeriesCount(
        totalBooks = totalBooks,
        loadedBookCount = state.books.size,
        serverSeriesTotal = totalSeries,
        loadedSeriesCount = seriesKeys.size
    )
    val displayedBooks: List<Pair<BookSummary, String?>> = if (allowSeriesCollapse && seriesCollapsed) {
        collapsedLibraryBooks(state.books)
    } else {
        state.books.map { Pair(it, null) }
    }
    val seriesBookCounts = remember(state.books) { collapsedSeriesBookCounts(state.books) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    var pendingAnchor by remember(title) { mutableStateOf<LibraryGridAnchor?>(null) }
    val jumpSort = filter?.sort ?: BookSortOption.SERVER_DEFAULT
    val jumpTargets = remember(
        displayedBooks,
        jumpRailEnabled,
        seriesCollapsed,
        serverJumpBuckets,
        filter
    ) {
        if (
            !jumpRailEnabled ||
            jumpSort !in setOf(BookSortOption.SERVER_DEFAULT, BookSortOption.TITLE, BookSortOption.AUTHOR)
        ) {
            emptyList()
        } else if (!seriesCollapsed && filter == BookBrowseFilter() && serverJumpBuckets.isNotEmpty()) {
            buildServerLibraryJumpTargets(serverJumpBuckets, displayedBooks.size)
        } else {
            buildLibraryJumpTargets(
                displayedBooks = displayedBooks,
                sort = jumpSort,
                direction = filter?.direction ?: SortDirection.ASCENDING
            )
        }
    }

    LaunchedEffect(seriesCollapsed, displayedBooks) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        val targetIndex = displayedBooks.indexOfFirst { (book, seriesKey) ->
            book.id == anchor.bookId ||
                (anchor.seriesKey != null &&
                    (seriesKey == anchor.seriesKey ||
                        book.seriesId == anchor.seriesKey ||
                        book.seriesName == anchor.seriesKey))
        }
        if (targetIndex >= 0) {
            if (reduceMotion) {
                gridState.scrollToItem(targetIndex)
            } else {
                gridState.animateScrollToItem(targetIndex)
            }
        }
        pendingAnchor = null
    }

    val hasJumpRail = jumpTargets.isNotEmpty()
    Column(modifier = modifier.fillMaxSize()) {
        LibraryBooksToolbar(
            selectedBooks = selectedBooks,
            onMarkAsRead = onMarkAsRead,
            onMarkAsUnread = onMarkAsUnread,
            onClearSelection = { selectedBookIds = emptySet() },
            bookCount = totalBooks ?: state.books.size,
            seriesCount = seriesCount,
            filterActive = filter?.isActive == true,
            onFilterClick = onFilterClick,
            showSeriesCollapse = allowSeriesCollapse && seriesKeys.isNotEmpty(),
            seriesCollapsed = seriesCollapsed,
            onToggleSeriesCollapse = {
                val anchor = displayedBooks.getOrNull(gridState.firstVisibleItemIndex)
                pendingAnchor = anchor?.let { (book, seriesKey) ->
                    LibraryGridAnchor(
                        bookId = book.id,
                        seriesKey = seriesKey ?: book.seriesId ?: book.seriesName
                    )
                }
                seriesCollapsed = !seriesCollapsed
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_books_grid"),
            contentPadding = PaddingValues(
                start = CATALOG_GRID_PADDING,
                top = CATALOG_GRID_PADDING,
                end = catalogGridEndPadding(hasJumpRail),
                bottom = CATALOG_GRID_PADDING
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (state.isLoadingBooks) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow("Loading books...") }
        }
        if (state.isCatalogSyncing) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingFeedRow(
                    if (state.isCatalogComplete) "Updating cached catalog..." else "Caching full library..."
                )
            }
        }
        state.message?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                OrbitMessage(
                    message,
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR
                )
            }
        }
        if (!state.isLoadingBooks && state.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        gridItems(displayedBooks, key = { (book, seriesKey) -> "library-book-${book.id}-${seriesKey ?: "single"}" }) { (book, seriesKey) ->
            val unavailableOffline = state.isOfflineSnapshot && !book.isDownloaded
            BookPosterCard(
                book = book,
                coverLoader = coverLoader,
                enabled = !unavailableOffline,
                displayTitle = if (seriesKey != null) book.seriesName ?: "Series" else book.title,
                supportingText = seriesKey?.let { key ->
                    seriesBookCountLabel(seriesBookCounts[key] ?: 1)
                },
                onMarkAsRead = if (seriesKey == null && !state.isOfflineSnapshot) {
                    onMarkAsRead?.let { mark -> { mark(book) } }
                } else null,
                onMarkAsUnread = if (seriesKey == null && !state.isOfflineSnapshot) {
                    onMarkAsUnread?.let { mark -> { mark(book) } }
                } else null,
                isSelected = book.id in selectedBookIds,
                selectionMode = selectedBookIds.isNotEmpty(),
                onToggleSelection = if (seriesKey == null && !state.isOfflineSnapshot) {
                    { selectedBookIds = if (book.id in selectedBookIds) selectedBookIds - book.id else selectedBookIds + book.id }
                } else null,
                onClick = {
                    if (seriesKey != null) onSeriesSelected(seriesKey) else onBookSelected(book)
                }
            )
        }
        }
        if (hasJumpRail) {
            LibraryJumpRail(
                targets = jumpTargets,
                direction = filter?.direction ?: SortDirection.ASCENDING,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 12.dp),
                onJump = { index ->
                    scope.launch {
                        if (reduceMotion) {
                            gridState.scrollToItem(index)
                        } else {
                            gridState.animateScrollToItem(index)
                        }
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun LibraryBooksToolbar(
    selectedBooks: List<BookSummary>,
    onMarkAsRead: ((BookSummary) -> Unit)?,
    onMarkAsUnread: ((BookSummary) -> Unit)?,
    onClearSelection: () -> Unit,
    bookCount: Int,
    seriesCount: Int?,
    filterActive: Boolean,
    onFilterClick: (() -> Unit)?,
    showSeriesCollapse: Boolean,
    seriesCollapsed: Boolean,
    onToggleSeriesCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_books_toolbar")
            .padding(
                start = CATALOG_GRID_PADDING,
                top = CATALOG_GRID_PADDING,
                end = CATALOG_GRID_PADDING
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedBooks.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("${selectedBooks.size} selected", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            selectedBooks.forEach { onMarkAsRead?.invoke(it) }
                            onClearSelection()
                        }
                    ) { Text("Mark read") }
                    TextButton(
                        onClick = {
                            selectedBooks.forEach { onMarkAsUnread?.invoke(it) }
                            onClearSelection()
                        }
                    ) { Text("Mark unread") }
                    TextButton(onClick = onClearSelection) { Text("Clear selection") }
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    buildString {
                        append("$bookCount ${if (bookCount == 1) "book" else "books"}")
                        if (seriesCount != null && seriesCount > 0) {
                            append(" · $seriesCount series")
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onFilterClick != null) {
                OutlinedButton(onClick = onFilterClick) {
                    Text(if (filterActive) "Filter · active" else "Filter")
                }
            }
            if (showSeriesCollapse) {
                TextButton(
                    onClick = onToggleSeriesCollapse,
                    modifier = Modifier.semantics {
                        contentDescription = if (seriesCollapsed) {
                            "Expand series"
                        } else {
                            "Collapse series"
                        }
                    }
                ) {
                    Text(if (seriesCollapsed) "Expand series" else "Collapse series")
                }
            }
        }
    }
}

@Composable
private fun LibraryJumpRail(
    targets: List<Pair<Char, Int>>,
    direction: SortDirection,
    modifier: Modifier,
    onJump: (Int) -> Unit
) {
    val targetsByLabel = targets.toMap()
    Column(
        modifier = modifier
            .testTag("catalog_jump_rail")
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        catalogJumpRailLabels(direction).forEach { label ->
            val index = targetsByLabel[label]
            Box(
                modifier = Modifier.size(20.dp).then(
                    if (index != null) {
                        Modifier
                            .clickable { onJump(index) }
                            .semantics { contentDescription = "Jump to $label" }
                    } else {
                        Modifier.semantics {
                            contentDescription = "$label unavailable"
                            disabled()
                        }
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalBooksScreen(
    state: BrowserState,
    modifier: Modifier,
    loader: suspend () -> List<BookSummary>,
    libraryId: String?,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val books by produceState<List<BookSummary>?>(initialValue = null, state.localBooksRevision) {
        value = loader()
    }
    var filter by remember { mutableStateOf(BookBrowseFilter()) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val scopedBooks = remember(books, libraryId) {
        books.orEmpty().filter { libraryId == null || it.libraryId == libraryId }
    }
    val filteredBooks = remember(scopedBooks, filter) {
        filterAndSortLocalBooks(scopedBooks, filter)
    }
    LibraryBooks(
        state = state.copy(
            books = filteredBooks,
            isLoadingBooks = books == null,
            message = null,
            isOfflineSnapshot = false
        ),
        modifier = modifier,
        coverLoader = coverLoader,
        onBookSelected = onBookSelected,
        titleOverride = state.libraries.firstOrNull { it.id == libraryId }
            ?.let { "Local books · ${it.name}" }
            ?: "Local books",
        emptyMessage = "No local books found.",
        allowSeriesCollapse = false,
        totalBooks = filteredBooks.size,
        filter = filter,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread,
        onFilterClick = { showFilter = true }
    )
    if (showFilter) {
        BookFilterSheet(
            initial = filter,
            onDismiss = { showFilter = false },
            onApply = {
                filter = it
                showFilter = false
            }
        )
    }
}

@Composable
private fun LibraryBookList(
    title: String,
    books: List<BookSummary>,
    state: BrowserState,
    modifier: Modifier,
    isLoading: Boolean,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall) }
        if (isLoading) item { LoadingFeedRow("Loading books...") }
        state.message?.let { message ->
            item {
                OrbitMessage(
                    message,
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR
                )
            }
        }
        if (!isLoading && books.isEmpty()) {
            item { Text("No books found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(books, key = { it.id }) { book ->
            LibraryBookCard(
                book = book,
                state = state,
                coverLoader = coverLoader,
                onBookSelected = onBookSelected,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDeleteLocalCopy = onDeleteLocalCopy,
                onMarkAsRead = onMarkAsRead,
                onMarkAsUnread = onMarkAsUnread
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(
    book: BookSummary,
    state: BrowserState,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val fileId = book.fileId
    val isDownloading = fileId != null && fileId in state.downloadingFileIds
    val failed = fileId != null && fileId in state.failedDownloadFileIds
    val unavailableOffline = state.isOfflineSnapshot && !book.isDownloaded
    val hasActions = !state.isOfflineSnapshot
    val canOpenDetails = !isDownloading && !unavailableOffline
    var showActions by remember(book.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_book_${book.id}")
            .combinedClickable(
                enabled = canOpenDetails || hasActions,
                onClick = { if (canOpenDetails) onBookSelected(book) },
                onLongClick = if (hasActions) ({ showActions = true }) else null
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.width(56.dp)) { BookCover(book, coverLoader) }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(nativeBookStatus(book, state.isOfflineSnapshot), style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onBookSelected(book) },
                        enabled = !isDownloading && !unavailableOffline
                    ) {
                        Text(if (unavailableOffline) "Unavailable offline" else "Details")
                    }
                    when {
                        book.isDownloaded -> OutlinedButton(
                            onClick = { onDeleteLocalCopy(book) },
                            enabled = !isDownloading
                        ) { Text("Delete local") }
                        isDownloading -> OutlinedButton(onClick = { onCancelDownload(book) }) { Text("Cancel") }
                        fileId != null && !state.isOfflineSnapshot -> OutlinedButton(onClick = { onDownload(book) }) {
                            Text(if (failed) "Retry" else "Download")
                        }
                    }
                }
            }
            if (hasActions) {
                Box {
                    IconButton(onClick = { showActions = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options for ${book.title}"
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mark as read") },
                            onClick = {
                                showActions = false
                                onMarkAsRead(book)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as unread") },
                            onClick = {
                                showActions = false
                                onMarkAsUnread(book)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetails(
    book: BookSummary,
    state: BrowserState,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    detailLoader: suspend (BookSummary) -> BookDetailInfo?,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    onRead: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onBookSelected: (BookSummary) -> Unit,
    onGenreSelected: (String) -> Unit
) {
    val canonicalStateBook = state.books.firstOrNull { it.id == book.id }
        ?: state.homeBooks.firstOrNull { it.id == book.id }
    val stateBook = canonicalStateBook ?: book
    val currentBook = stateBook.fileId?.let { fileId ->
        if (state.localFilePathOverrides.containsKey(fileId)) {
            stateBook.copy(localPath = state.localFilePathOverrides[fileId])
        } else {
            stateBook
        }
    } ?: stateBook
    val currentFileId = currentBook.fileId
    val isDownloading = currentFileId != null && currentFileId in state.downloadingFileIds
    val detail by produceState(
        initialValue = BookDetailInfo(currentBook),
        currentBook.id,
        currentBook.updatedAtMillis,
        currentBook.localPath,
        isDownloading
    ) {
        value = value.copy(
            book = value.book.copy(
                localPath = currentBook.localPath,
                progressLabel = currentBook.progressLabel ?: value.book.progressLabel,
                progressPercent = currentBook.progressPercent ?: value.book.progressPercent,
                progressPositionMs = currentBook.progressPositionMs ?: value.book.progressPositionMs,
                progressPageIndex = currentBook.progressPageIndex ?: value.book.progressPageIndex,
                lastReadAtMillis = currentBook.lastReadAtMillis ?: value.book.lastReadAtMillis,
                isRead = currentBook.isRead,
                updatedAtMillis = currentBook.updatedAtMillis ?: value.book.updatedAtMillis,
                downloadedSourceUpdatedAtMillis = currentBook.downloadedSourceUpdatedAtMillis
            )
        )
        value = detailLoader(currentBook) ?: value
    }
    var showCoverViewer by rememberSaveable(book.id) { mutableStateOf(false) }
    val displayBook = detail.book.copy(
        localPath = currentBook.localPath,
        progressLabel = if (canonicalStateBook != null) currentBook.progressLabel else currentBook.progressLabel ?: detail.book.progressLabel,
        progressPercent = if (canonicalStateBook != null) currentBook.progressPercent else currentBook.progressPercent ?: detail.book.progressPercent,
        progressPositionMs = if (canonicalStateBook != null) currentBook.progressPositionMs else currentBook.progressPositionMs ?: detail.book.progressPositionMs,
        progressPageIndex = if (canonicalStateBook != null) currentBook.progressPageIndex else currentBook.progressPageIndex ?: detail.book.progressPageIndex,
        lastReadAtMillis = if (canonicalStateBook != null) currentBook.lastReadAtMillis else currentBook.lastReadAtMillis ?: detail.book.lastReadAtMillis,
        isRead = currentBook.isRead,
        updatedAtMillis = currentBook.updatedAtMillis ?: detail.book.updatedAtMillis,
        downloadedSourceUpdatedAtMillis = currentBook.downloadedSourceUpdatedAtMillis,
        audioChapters = detail.audioChapters.ifEmpty { currentBook.audioChapters }
    )
    val fileId = displayBook.fileId
    val downloadProgress = fileId?.let(state.downloadProgressByFileId::get)
    val downloadFailed = fileId != null && fileId in state.failedDownloadFileIds
    val unavailableOffline = state.isOfflineSnapshot && !displayBook.isDownloaded
    val seriesKey = displayBook.seriesId ?: displayBook.seriesName
    val localSeriesBooks = remember(state.books, state.homeBooks, displayBook, seriesKey) {
        (state.books + state.homeBooks + displayBook)
            .distinctBy { it.id }
            .filter { candidate -> booksShareSeries(displayBook, candidate) }
    }
    var loadedSeriesBooks by remember(seriesKey) { mutableStateOf<List<BookSummary>?>(null) }
    LaunchedEffect(seriesKey, state.isOfflineSnapshot) {
        if (seriesKey != null && !state.isOfflineSnapshot && loadedSeriesBooks == null) {
            loadedSeriesBooks = seriesDetailLoader(seriesKey)?.books?.takeIf { it.isNotEmpty() }
        }
    }
    val seriesBooks = remember(localSeriesBooks, loadedSeriesBooks) {
        (localSeriesBooks + loadedSeriesBooks.orEmpty()).distinctBy { it.id }
    }
    val seriesNeighbors = remember(displayBook, seriesBooks) {
        seriesBookNeighbors(displayBook, seriesBooks)
    }
    val publicationMetadata = buildList {
        detail.publisher?.let { add("Publisher" to it) }
        detail.publishedDate?.let { add("Published" to it) }
        detail.language?.let { add("Language" to it) }
        detail.pageCount?.let { add("Pages" to it.toString()) }
        detail.rating?.let {
            add("Rating" to String.format(java.util.Locale.US, "%.1f / 5", it))
        }
    }
    val identifierMetadata = buildList {
        detail.isbn13?.let { add("ISBN-13" to it) }
        detail.isbn10?.let { add("ISBN-10" to it) }
    }
    val fileMetadata = buildList {
        detail.libraryName?.let { add("Library" to it) }
        displayBook.format?.let { add("Format" to it.uppercase()) }
        detail.durationSeconds?.takeIf { it > 0 }?.let {
            add("Duration" to formatDetailDuration(it))
        }
        detail.totalSizeBytes?.takeIf { it > 0 }?.let {
            add("File size" to formatFileSize(it))
        }
        if (detail.fileCount > 1) add("Files" to detail.fileCount.toString())
        if (displayBook.isDownloaded) add("Offline" to "Available")
    }

    if (showCoverViewer) {
        FullScreenCoverViewer(
            book = displayBook,
            coverLoader = coverLoader,
            onDismiss = { showCoverViewer = false }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .width(116.dp)
                        .clickable { showCoverViewer = true }
                        .semantics {
                            contentDescription = "Open full-screen cover for ${displayBook.title}"
                        }
                ) {
                    BookCover(displayBook, coverLoader)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayBook.seriesName?.let { seriesName ->
                        OrbitEyebrow(
                            text = "$seriesName${displayBook.seriesIndex?.let(::formatSeriesIndex)?.let { " #$it" }.orEmpty()}  ›",
                            modifier = if (seriesKey != null) {
                                Modifier
                                    .clickable { onSeriesSelected(seriesKey) }
                                    .semantics {
                                        contentDescription = "Open series $seriesName"
                                    }
                            } else {
                                Modifier
                            }
                        )
                    }
                    displayBook.seriesIndex?.let { index ->
                        Text(
                            "Book ${formatSeriesIndex(index)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpandableBookTitle(displayBook.title)
                    detail.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    displayBook.author?.let {
                        Text("by $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    detail.narrators.takeIf { it.isNotEmpty() }?.let {
                        Text("Narrated by ${it.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        bookDetailIdentityStatus(displayBook, state.isOfflineSnapshot),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (seriesNeighbors.total > 1) {
            item {
                SeriesNeighborNavigation(
                    seriesName = displayBook.seriesName ?: "Series",
                    neighbors = seriesNeighbors,
                    onBookSelected = onBookSelected
                )
            }
        }
        item {
            val readingProgressLabel = bookDetailReadingProgressLabel(displayBook)
            val actionState = bookDetailActionState(
                isDownloaded = displayBook.isDownloaded,
                isDownloading = isDownloading,
                downloadFailed = downloadFailed,
                hasDownloadUpdate = displayBook.hasDownloadUpdate,
                isOfflineSnapshot = state.isOfflineSnapshot
            )
            val statusActionLabel = bookDetailReadingStatusActionLabel(displayBook)
            var showActionMenu by rememberSaveable(displayBook.id) { mutableStateOf(false) }
            val performStatusAction = {
                if (statusActionLabel == "Mark as unread") {
                    onMarkAsUnread(displayBook)
                } else {
                    onMarkAsRead(displayBook)
                }
            }
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelLarge
            fun actionWidth(label: String, minimumDp: Float): Float = with(density) {
                val textWidth = textMeasurer.measure(label, style = labelStyle, maxLines = 1).size.width.toDp()
                maxOf(minimumDp.dp, 20.dp + 24.dp + 6.dp + textWidth).value
            }
            val primaryActionLabel = bookDetailPrimaryActionLabel(displayBook)
            val readWidth = actionWidth(primaryActionLabel, 82f)
            val previewWidth = actionWidth("Preview", 94f)
            val markWidth = actionWidth(statusActionLabel, 118f)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                readingProgressLabel?.let { label ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("book-detail-reading-progress"),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("book-detail-actions")
                ) {
                    val layout = bookDetailActionRowLayout(
                        availableWidth = maxWidth.value,
                        readWidth = readWidth,
                        previewWidth = previewWidth,
                        markWidth = markWidth,
                        hasInlineTransfer = actionState.inlineTransfer != null,
                        hasFixedOverflow = actionState.hasFixedOverflow
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val readModifier = if (layout.compactRequiredActions) {
                            Modifier.weight(1f).height(46.dp)
                        } else {
                            Modifier.width(readWidth.dp).height(46.dp)
                        }
                        val previewModifier = if (layout.compactRequiredActions) {
                            Modifier.weight(1f).height(46.dp)
                        } else {
                            Modifier.width(previewWidth.dp).height(46.dp)
                        }
                        DetailActionTile(
                            label = primaryActionLabel,
                            icon = Icons.Default.PlayArrow,
                            showLabel = true,
                            emphasized = true,
                            enabled = !isDownloading && !unavailableOffline,
                            modifier = readModifier,
                            applyDefaultSize = false,
                            onClick = { onRead(displayBook) }
                        )
                        DetailActionTile(
                            label = "Preview",
                            icon = Icons.Default.Visibility,
                            showLabel = true,
                            enabled = !isDownloading && !unavailableOffline,
                            modifier = previewModifier,
                            applyDefaultSize = false,
                            onClick = { onPreview(displayBook) }
                        )
                        actionState.inlineTransfer?.let { transfer ->
                            DetailActionTile(
                                label = transfer.contentDescription,
                                icon = if (transfer == BookDetailInlineTransfer.CANCEL_DOWNLOAD) {
                                    Icons.Default.Close
                                } else {
                                    Icons.Default.Download
                                },
                                enabled = transfer == BookDetailInlineTransfer.CANCEL_DOWNLOAD ||
                                    (fileId != null && !state.isOfflineSnapshot),
                                modifier = Modifier.size(46.dp),
                                applyDefaultSize = false,
                                onClick = {
                                    if (transfer == BookDetailInlineTransfer.CANCEL_DOWNLOAD) {
                                        onCancelDownload(displayBook)
                                    } else {
                                        onDownload(displayBook)
                                    }
                                }
                            )
                        }
                        if (layout.showInlineStatusAction) {
                            DetailActionTile(
                                label = statusActionLabel,
                                icon = if (statusActionLabel == "Mark as unread") {
                                    Icons.AutoMirrored.Filled.Undo
                                } else {
                                    Icons.Default.CheckCircle
                                },
                                showLabel = true,
                                enabled = !state.isOfflineSnapshot,
                                modifier = Modifier
                                    .width(markWidth.dp)
                                    .height(46.dp)
                                    .testTag("book-detail-status-inline"),
                                applyDefaultSize = false,
                                onClick = performStatusAction
                            )
                        }
                        if (layout.showMore) {
                            if (layout.pinMoreToEnd) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Box {
                                DetailActionTile(
                                    label = "More book actions",
                                    icon = Icons.Default.MoreVert,
                                    modifier = Modifier.size(46.dp).testTag("book-detail-more"),
                                    applyDefaultSize = false,
                                    onClick = { showActionMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showActionMenu,
                                    onDismissRequest = { showActionMenu = false }
                                ) {
                                    actionState.overflowTransferLabel?.let { transferLabel ->
                                        DropdownMenuItem(
                                            text = { Text(transferLabel) },
                                            leadingIcon = {
                                                Icon(
                                                    if (transferLabel.startsWith("Cancel")) Icons.Default.Close else Icons.Default.Download,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showActionMenu = false
                                                if (transferLabel.startsWith("Cancel")) {
                                                    onCancelDownload(displayBook)
                                                } else {
                                                    onDownload(displayBook)
                                                }
                                            }
                                        )
                                    }
                                    if (actionState.showDeleteLocal) {
                                        DropdownMenuItem(
                                            text = { Text("Delete local") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = {
                                                showActionMenu = false
                                                onDeleteLocalCopy(displayBook)
                                            }
                                        )
                                    }
                                    if (!layout.showInlineStatusAction) {
                                        DropdownMenuItem(
                                            text = { Text(statusActionLabel) },
                                            leadingIcon = {
                                                Icon(
                                                    if (statusActionLabel == "Mark as unread") {
                                                        Icons.AutoMirrored.Filled.Undo
                                                    } else {
                                                        Icons.Default.CheckCircle
                                                    },
                                                    contentDescription = null
                                                )
                                            },
                                            enabled = !state.isOfflineSnapshot,
                                            onClick = {
                                                showActionMenu = false
                                                performStatusAction()
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
        if (isDownloading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book-download-status"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        downloadProgress?.let {
                            "${if (displayBook.isDownloaded) "Updating local" else "Downloading"} · ${(it * 100).toInt()}%"
                        } ?: if (displayBook.isDownloaded) "Updating local…" else "Downloading…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        if (displayBook.isDownloaded) {
                            "Use More > Cancel update to stop."
                        } else {
                            "Use the × action above to cancel."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (downloadFailed) {
            item {
                Text(
                    if (displayBook.isDownloaded) {
                        "Update failed. Your previous local copy is still available; use More > Update local to retry."
                    } else {
                        "Download failed. Tap the download action to retry."
                    },
                    modifier = Modifier.testTag("book-download-status"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        detail.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            item { ExpandableDescription("Synopsis", plainText(synopsis)) }
        }
        if (detail.genres.isNotEmpty()) {
            item {
                DetailLabelGroup(
                    title = "Genres",
                    labels = detail.genres,
                    onLabelClick = onGenreSelected
                )
            }
        }
        if (detail.tags.isNotEmpty()) {
            item {
                DetailLabelGroup(
                    title = "Tags",
                    labels = detail.tags
                )
            }
        }
        if (publicationMetadata.isNotEmpty() || identifierMetadata.isNotEmpty() || fileMetadata.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    Text(
                        "Book details",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (publicationMetadata.isNotEmpty()) {
                        DetailMetadataGroup("Publication", publicationMetadata)
                    }
                    if (identifierMetadata.isNotEmpty()) {
                        DetailMetadataGroup("Identifiers", identifierMetadata)
                    }
                    if (fileMetadata.isNotEmpty()) {
                        DetailMetadataGroup("Library and file", fileMetadata)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesNeighborNavigation(
    seriesName: String,
    neighbors: SeriesBookNeighbors,
    onBookSelected: (BookSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series-neighbor-navigation"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        OrbitEyebrow("Series navigation")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SeriesNeighborButton(
                direction = "Previous",
                book = neighbors.previous,
                seriesName = seriesName,
                emptyLabel = "Start of series",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                modifier = Modifier.weight(1f).testTag("series-previous-book"),
                onClick = onBookSelected
            )
            SeriesNeighborButton(
                direction = "Next",
                book = neighbors.next,
                seriesName = seriesName,
                emptyLabel = "End of series",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.weight(1f).testTag("series-next-book"),
                onClick = onBookSelected
            )
        }
    }
}

@Composable
private fun SeriesNeighborButton(
    direction: String,
    book: BookSummary?,
    seriesName: String,
    emptyLabel: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: (BookSummary) -> Unit
) {
    val bookLabel = book?.let { neighbor ->
        listOfNotNull(
            neighbor.seriesIndex?.let(::formatSeriesIndex)?.let { "#$it" },
            neighbor.title
        ).joinToString(" \u00B7 ")
    }
    Card(
        onClick = { book?.let(onClick) },
        enabled = book != null,
        modifier = modifier
            .height(46.dp)
            .semantics {
                contentDescription = if (bookLabel != null) {
                    "$direction book in $seriesName: $bookLabel"
                } else {
                    "No ${direction.lowercase()} book in $seriesName"
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (direction == "Previous") {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (direction == "Previous") Alignment.Start else Alignment.End
            ) {
                Text(direction, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = bookLabel ?: emptyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (direction == "Next") {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

internal fun bookDetailReadingStatusActionLabel(book: BookSummary): String {
    val completed = book.isRead || book.progressPercent?.let { it >= 99.5f } == true
    return if (completed) "Mark as unread" else "Mark as read"
}

internal fun bookDetailPrimaryActionLabel(book: BookSummary): String =
    if (book.mediaKind == MediaKind.AUDIO) "Play" else "Read"

internal fun bookDetailReadingProgressLabel(book: BookSummary): String? {
    val progress = normalizeStoredProgressPercent(book.progressPercent?.takeIf { it.isFinite() })
    val completed = book.isRead || progress?.let { it >= 99.5f } == true
    val currentlyReading = !completed && book.hasReadingActivity() && book.isStillInProgress()
    if (!completed && !currentlyReading) return null

    val status = if (completed) "Read" else "Reading"
    val percentage = progress?.let(::formatBookDetailProgressPercent) ?: return status
    return "$status \u00B7 $percentage%"
}

private fun formatBookDetailProgressPercent(progress: Float): String =
    String.format(java.util.Locale.US, "%.2f", progress.coerceIn(0f, 100f))
        .trimEnd('0')
        .trimEnd('.')

internal enum class BookDetailInlineTransfer(val contentDescription: String) {
    DOWNLOAD("Download"),
    RETRY_DOWNLOAD("Retry download"),
    CANCEL_DOWNLOAD("Cancel download")
}

internal data class BookDetailActionState(
    val inlineTransfer: BookDetailInlineTransfer?,
    val overflowTransferLabel: String?,
    val showDeleteLocal: Boolean
) {
    val hasFixedOverflow: Boolean
        get() = overflowTransferLabel != null || showDeleteLocal
}

internal fun bookDetailActionState(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadFailed: Boolean,
    hasDownloadUpdate: Boolean,
    isOfflineSnapshot: Boolean
): BookDetailActionState = if (!isDownloaded) {
    BookDetailActionState(
        inlineTransfer = when {
            isDownloading -> BookDetailInlineTransfer.CANCEL_DOWNLOAD
            downloadFailed -> BookDetailInlineTransfer.RETRY_DOWNLOAD
            else -> BookDetailInlineTransfer.DOWNLOAD
        },
        overflowTransferLabel = null,
        showDeleteLocal = false
    )
} else {
    BookDetailActionState(
        inlineTransfer = null,
        overflowTransferLabel = when {
            isDownloading -> "Cancel update"
            hasDownloadUpdate && !isOfflineSnapshot -> "Update local"
            else -> null
        },
        showDeleteLocal = true
    )
}

internal data class BookDetailActionRowLayout(
    val showInlineStatusAction: Boolean,
    val showMore: Boolean,
    val compactRequiredActions: Boolean,
    val pinMoreToEnd: Boolean
)

internal fun bookDetailActionRowLayout(
    availableWidth: Float,
    readWidth: Float,
    previewWidth: Float,
    markWidth: Float,
    hasInlineTransfer: Boolean,
    hasFixedOverflow: Boolean,
    iconWidth: Float = 46f,
    spacing: Float = 8f
): BookDetailActionRowLayout {
    val requiredWidths = buildList {
        add(readWidth)
        add(previewWidth)
        if (hasInlineTransfer) add(iconWidth)
    }
    fun occupied(widths: List<Float>): Float =
        widths.sum() + spacing * (widths.size - 1).coerceAtLeast(0)

    val withInlineStatus = buildList {
        addAll(requiredWidths)
        add(markWidth)
        if (hasFixedOverflow) add(iconWidth)
    }
    val showInlineStatus = occupied(withInlineStatus) <= availableWidth
    val showMore = hasFixedOverflow || !showInlineStatus
    val visibleRequired = buildList {
        addAll(requiredWidths)
        if (showMore) add(iconWidth)
    }
    return BookDetailActionRowLayout(
        showInlineStatusAction = showInlineStatus,
        showMore = showMore,
        compactRequiredActions = occupied(visibleRequired) > availableWidth,
        pinMoreToEnd = showMore && occupied(visibleRequired) + spacing <= availableWidth
    )
}

@Composable
private fun DetailActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier,
    applyDefaultSize: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(
            if (applyDefaultSize) {
                if (showLabel) Modifier.widthIn(min = 88.dp).height(46.dp) else Modifier.size(46.dp)
            } else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (showLabel) 10.dp else 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailLabelGroup(
    title: String,
    labels: List<String>,
    onLabelClick: ((String) -> Unit)? = null
) {
    val distinctLabels = labels.filter { it.isNotBlank() }.distinct()
    if (distinctLabels.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            distinctLabels.forEach { label ->
                Card(
                    modifier = if (onLabelClick != null) {
                        Modifier
                            .clickable { onLabelClick(label) }
                            .semantics { contentDescription = "Filter $title by $label" }
                    } else {
                        Modifier
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableBookTitle(title: String) {
    var expanded by remember(title) { mutableStateOf(false) }
    var overflow by remember(title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { overflow = it.hasVisualOverflow }
        )
        if (overflow || expanded) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Collapse" else "Expand title")
            }
        }
    }
}

@Composable
private fun DetailMetadataGroup(title: String, entries: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OrbitEyebrow(title)
            entries.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.width(78.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenCoverViewer(
    book: BookSummary,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onDismiss: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, book.id, book.coverUrl, book.updatedAtMillis) {
        val bytes = try {
            coverLoader(book)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        value = if (bytes != null && bytes.isNotEmpty()) {
            try {
                withContext(Dispatchers.Default) {
                    decodeCoverBitmap(bytes, targetWidth = 1080, targetHeight = 1620)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .padding(24.dp)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Full-screen cover for ${book.title}. Tap anywhere to close" },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(book.coverAspectRatio.widthToHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    )
                    .clickable(onClick = onDismiss)
                    .semantics {
                        contentDescription = "Full-screen cover for ${book.title}. Tap to close"
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = book.title.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            }
        }
    }
}

internal data class SeriesBookSection(
    val key: String,
    val title: String?,
    val books: List<BookSummary>
)

internal fun toggledSeriesGroupingMode(
    current: SeriesGroupingMode,
    requested: SeriesGroupingMode
): SeriesGroupingMode = if (current == requested) SeriesGroupingMode.NONE else requested

internal fun seriesBookSections(
    books: List<BookSummary>,
    libraries: List<LibrarySummary>,
    groupingMode: SeriesGroupingMode
): List<SeriesBookSection> {
    val orderedBooks = books.sortedWith(
        compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }
    )
    if (groupingMode == SeriesGroupingMode.NONE) {
        return listOf(SeriesBookSection(key = "all", title = null, books = orderedBooks))
    }

    val libraryNames = libraries.associate { it.id to it.name }
    val libraryOrder = libraries.mapIndexed { index, library -> library.id to index }.toMap()
    val keyedBooks = orderedBooks.map { book ->
        when (groupingMode) {
            SeriesGroupingMode.LIBRARY -> {
                val title = libraryNames[book.libraryId] ?: book.libraryId
                Triple(book.libraryId, title, book)
            }
            SeriesGroupingMode.FORMAT -> {
                val title = book.format
                    ?.trim()
                    ?.trimStart('.')
                    ?.takeIf { it.isNotBlank() }
                    ?.uppercase()
                    ?: book.mediaKind.name
                        .takeUnless { book.mediaKind == MediaKind.UNKNOWN }
                        ?: "Unknown format"
                Triple(title.lowercase(), title, book)
            }
            SeriesGroupingMode.NONE -> error("Handled above")
        }
    }
    return keyedBooks
        .groupBy { it.first }
        .map { (key, entries) ->
            SeriesBookSection(
                key = key,
                title = entries.first().second,
                books = entries.map { it.third }
            )
        }
        .sortedWith(
            when (groupingMode) {
                SeriesGroupingMode.LIBRARY -> compareBy<SeriesBookSection>(
                    { libraryOrder[it.key] ?: Int.MAX_VALUE },
                    { it.title?.lowercase().orEmpty() }
                )
                SeriesGroupingMode.FORMAT -> compareBy { it.title?.lowercase().orEmpty() }
                SeriesGroupingMode.NONE -> compareBy { 0 }
            }
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesGroupingControls(
    groupingMode: SeriesGroupingMode,
    onGroupingModeChange: (SeriesGroupingMode) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("series-grouping-controls"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Separate by", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = groupingMode == SeriesGroupingMode.LIBRARY,
                onClick = {
                    onGroupingModeChange(
                        toggledSeriesGroupingMode(groupingMode, SeriesGroupingMode.LIBRARY)
                    )
                },
                label = { Text("Library") },
                modifier = Modifier.testTag("series-group-by-library")
            )
            FilterChip(
                selected = groupingMode == SeriesGroupingMode.FORMAT,
                onClick = {
                    onGroupingModeChange(
                        toggledSeriesGroupingMode(groupingMode, SeriesGroupingMode.FORMAT)
                    )
                },
                label = { Text("File format") },
                modifier = Modifier.testTag("series-group-by-format")
            )
        }
    }
}

@Composable
private fun SeriesDetails(
    seriesKey: String,
    books: List<BookSummary>,
    libraries: List<LibrarySummary>,
    groupingMode: SeriesGroupingMode,
    onGroupingModeChange: (SeriesGroupingMode) -> Unit,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    detailLoader: suspend (String) -> SeriesDetailInfo?,
    onBookSelected: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onGenreSelected: (String) -> Unit
) {
    val localBooks = books
        .filter { (it.seriesId ?: it.seriesName) == seriesKey }
        .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
    val localDetail = SeriesDetailInfo(
        id = seriesKey,
        name = localBooks.firstOrNull()?.seriesName ?: "Series",
        bookCount = localBooks.size,
        readCount = localBooks.count { it.isRead },
        authors = localBooks.mapNotNull { it.author }.distinct(),
        books = localBooks
    )
    val detail by produceState(initialValue = localDetail, seriesKey) {
        value = detailLoader(seriesKey) ?: value
    }
    val bookSections = seriesBookSections(detail.books, libraries, groupingMode)
    val completion = if (detail.bookCount > 0) detail.readCount.toFloat() / detail.bookCount else 0f
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OrbitEyebrow("Series")
                Text(detail.name, style = MaterialTheme.typography.headlineSmall)
                detail.authors.takeIf { it.isNotEmpty() }?.let {
                    Text("by ${it.joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${detail.bookCount} ${if (detail.bookCount == 1) "book" else "books"} · ${detail.readCount} read",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { completion.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
        }
        if (detail.possibleGaps.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Possible missing positions: ${detail.possibleGaps.joinToString { formatSeriesIndex(it) }}",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        detail.firstBook?.let { first ->
            first.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ExpandableDescription("About this series", plainText(synopsis))
                }
            }
            if (first.genres.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DetailLabelGroup("Genres", first.genres, onGenreSelected)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesGroupingControls(
                groupingMode = groupingMode,
                onGroupingModeChange = onGroupingModeChange
            )
        }
        detail.firstBook?.let { first ->
            if (first.tags.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DetailLabelGroup("Tags", first.tags)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Books", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        }
        bookSections.forEach { section ->
            section.title?.let { title ->
                item(
                    key = "series-section-${section.key}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("series-section-${section.key}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                                .testTag("series-section-divider-${section.key}")
                        )
                    }
                }
            }
            gridItems(section.books, key = { "series-detail-${it.id}" }) { book ->
                BookPosterCard(
                    book = book,
                    coverLoader = coverLoader,
                    onClick = { onBookSelected(book) },
                    showSeriesIndex = true,
                    onMarkAsRead = { onMarkAsRead(book) },
                    onMarkAsUnread = { onMarkAsUnread(book) }
                )
            }
        }
    }
}

private const val DESCRIPTION_COLLAPSED_LINES = 4

@Composable
internal fun ExpandableDescription(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember(body) { mutableStateOf(false) }
    var hasOverflow by remember(body) { mutableStateOf(false) }
    val toggle = { expanded = !expanded }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = body,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasOverflow) {
                        Modifier
                            .clickable(onClick = toggle)
                            .semantics {
                                contentDescription = "$title description"
                                stateDescription = if (expanded) "Expanded" else "Collapsed"
                            }
                    } else {
                        Modifier
                    }
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                if (!expanded && layoutResult.hasVisualOverflow != hasOverflow) {
                    hasOverflow = layoutResult.hasVisualOverflow
                }
            }
        )
        if (hasOverflow) {
            TextButton(
                onClick = toggle,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title"
                }
            ) {
                Text(if (expanded) "Collapse" else "Expand")
            }
        }
    }
}

private fun plainText(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()

private fun formatSeriesIndex(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

private fun formatFileSize(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f GB", megabytes / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", megabytes)
    }
}

private fun formatDetailDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun LoadingFeedRow(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text)
    }
}

internal fun onDeckBooks(books: List<BookSummary>): List<BookSummary> {
    return books
        .filter { !it.seriesName.isNullOrBlank() }
        .groupBy { it.seriesId ?: it.seriesName.orEmpty() }
        .values
        .mapNotNull { seriesBooks ->
            val ordered = seriesBooks.sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
            val hasCompletedBook = ordered.any(BookSummary::isCompletedForSeriesProgression)
            if (!hasCompletedBook) return@mapNotNull null

            ordered
                .firstOrNull { !it.isCompletedForSeriesProgression() }
                ?.takeUnless { it.hasReadingActivity() && it.isStillInProgress() }
        }
        .take(12)
}

private fun BookSummary.isCompletedForSeriesProgression(): Boolean {
    return isRead || progressPercent?.let { it >= 99.5f } == true
}

internal fun currentlyReadingBooks(books: List<BookSummary>): List<BookSummary> {
    return books
        .filter { it.hasReadingActivity() && it.isStillInProgress() }
        .sortedWith(
            compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
                .thenByDescending { it.progressPercent ?: 0f }
                .thenBy { it.title.lowercase() }
        )
        .take(12)
}

internal fun recentlyReadBooks(books: List<BookSummary>): List<BookSummary> {
    val currentlyReadingIds = currentlyReadingBooks(books).mapTo(mutableSetOf()) { it.id }
    return books
        .filter { it.isRead && it.id !in currentlyReadingIds }
        .sortedWith(
            compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
                .thenByDescending { it.updatedAtMillis ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        .take(12)
}

private fun BookSummary.hasReadingActivity(): Boolean {
    return (progressPercent ?: 0f) > 0f ||
        (progressPositionMs ?: 0L) > 0L ||
        (progressPageIndex ?: 0) > 0 ||
        !progressLabel.isNullOrBlank() ||
        lastReadAtMillis != null
}

private fun BookSummary.isStillInProgress(): Boolean {
    return when {
        progressPercent != null -> progressPercent < 99.5f
        else -> !isRead
    }
}

internal fun librarySeriesCount(
    totalBooks: Int?,
    loadedBookCount: Int,
    serverSeriesTotal: Int?,
    loadedSeriesCount: Int
): Int? {
    return serverSeriesTotal ?: loadedSeriesCount.takeIf {
        totalBooks == null || loadedBookCount >= totalBooks
    }
}

internal fun recentSeries(books: List<BookSummary>, useUpdatedAt: Boolean): List<Pair<String, BookSummary>> {
    return books
        .filter { !it.seriesName.isNullOrBlank() }
        .groupBy { it.seriesId ?: it.seriesName.orEmpty() }
        .mapNotNull { (_, seriesBooks) ->
            val timestamped = seriesBooks.maxByOrNull {
                if (useUpdatedAt) it.updatedAtMillis ?: 0L else it.addedAtMillis ?: 0L
            } ?: return@mapNotNull null
            val timestamp = if (useUpdatedAt) timestamped.updatedAtMillis else timestamped.addedAtMillis
            if (timestamp == null) null else timestamped.seriesName.orEmpty() to timestamped
        }
        .sortedByDescending { (_, book) -> if (useUpdatedAt) book.updatedAtMillis else book.addedAtMillis }
        .take(12)
}

private fun nativeBookStatus(book: BookSummary, offline: Boolean): String {
    return when {
        book.isDownloaded -> "Downloaded"
        offline -> "Online only"
        book.isRead -> "Read"
        !book.progressLabel.isNullOrBlank() -> book.progressLabel
        !book.format.isNullOrBlank() -> book.format.uppercase()
        else -> book.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun bookDetailIdentityStatus(book: BookSummary, offline: Boolean): String {
    return when {
        book.isDownloaded -> "Downloaded"
        offline -> "Online only"
        !book.format.isNullOrBlank() -> book.format.uppercase()
        else -> book.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
