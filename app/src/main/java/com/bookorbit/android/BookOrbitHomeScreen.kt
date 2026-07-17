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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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

private enum class BrowserDestination { HOME, LIBRARY, SERIES, AUTHORS, LOCAL_BOOKS, OPTIONS, ABOUT }
private enum class LibraryTab { RECOMMENDED, BROWSE }
private val BOOK_CARD_MIN_SIZE = 88.dp

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
    return railLabels.mapIndexed { railIndex, target ->
        val exact = labels.indexOfFirst { it == target }
        val fallback = if (exact >= 0) {
            exact
        } else {
            labels.indexOfFirst { label -> railLabels.indexOf(label) > railIndex }
        }
        val index = fallback.takeIf { it >= 0 } ?: (labels.lastIndex.takeIf { it >= 0 } ?: 0)
        target to index
    }
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
    return LIBRARY_JUMP_LABELS.mapIndexed { railIndex, target ->
        val exact = indexedBuckets.firstOrNull { it.second == target }
        val fallback = exact ?: indexedBuckets.firstOrNull { (_, label) ->
            LIBRARY_JUMP_LABELS.indexOf(label) > railIndex
        }
        val bucket = fallback ?: indexedBuckets.last()
        target to bucket.first.index
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
    onLibrarySelected: (String) -> Unit,
    searchBooks: suspend (String) -> List<BookSummary>,
    localBooksLoader: suspend () -> List<BookSummary>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    bookDetailLoader: suspend (BookSummary) -> BookDetailInfo?,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    authorsCatalogLoader: suspend (String?, Int) -> AuthorCatalogPage,
    authorBooksLoader: suspend (String, Int) -> AuthorBooksPage?,
    catalogImageLoader: suspend (String) -> ByteArray?,
    onBookOpen: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(BrowserDestination.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var showLibraryPicker by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showProfileMenu by rememberSaveable { mutableStateOf(false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableStateOf(LibraryTab.RECOMMENDED) }
    var selectedBook by remember { mutableStateOf<BookSummary?>(null) }
    var selectedSeriesKey by remember { mutableStateOf<String?>(null) }
    var selectedAuthor by remember { mutableStateOf<AuthorSummary?>(null) }
    var detailReturnDestination by remember { mutableStateOf(BrowserDestination.HOME) }
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
    }

    BackHandler(enabled = isSearchOpen || selectedBook != null || selectedSeriesKey != null || selectedAuthor != null) {
        if (isSearchOpen) {
            isSearchOpen = false
            query = ""
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
                    destination = BrowserDestination.LOCAL_BOOKS
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onAbout = {
                    showMoreMenu = false
                    destination = BrowserDestination.ABOUT
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                }
            )
        }
    }

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
                    onOptions = openOptions
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
                    onOptions = openOptions
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
                    onOptions = openOptions
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
                    onOptions = openOptions
                )
                else -> BrowserTopBar(
                    title = when {
                        destination == BrowserDestination.LIBRARY && !showLibraryPicker ->
                            state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name ?: "Library"
                        destination == BrowserDestination.LIBRARY -> "Libraries"
                        destination == BrowserDestination.SERIES -> "Series"
                        destination == BrowserDestination.AUTHORS -> "Authors"
                        destination == BrowserDestination.LOCAL_BOOKS -> "Local books"
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
                    onOptions = openOptions
                )
            }
        },
        bottomBar = {
            if (!isSearchOpen && selectedBook == null && selectedSeriesKey == null && selectedAuthor == null) {
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
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                selectedBook != null -> BookDetails(
                    book = selectedBook!!,
                    state = state,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = bookDetailLoader,
                    onRead = onBookOpen,
                    onPreview = onPreview,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onSeriesSelected = { seriesKey ->
                        selectedSeriesKey = seriesKey
                        selectedBook = null
                    }
                )
                selectedSeriesKey != null -> SeriesDetails(
                    seriesKey = selectedSeriesKey!!,
                    books = state.books,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = seriesDetailLoader,
                    onBookSelected = { selectedBook = it },
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
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
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        detailReturnDestination = BrowserDestination.LOCAL_BOOKS
                        selectedBook = book
                    },
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                destination == BrowserDestination.OPTIONS -> OptionsScreen(
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
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
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
                    onMarkAsUnread = onMarkAsUnread
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
                    onMarkAsUnread = onMarkAsUnread
                )
                else -> HomeFeed(
                    state = state,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    onBookSelected = { book -> selectedBook = book },
                    onSeriesSelected = { seriesKey -> selectedSeriesKey = seriesKey },
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
            }
    }
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
                        text = { Text("Options") },
                        leadingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                        onClick = {
                            onDismissProfile()
                            onOptions()
                        }
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
    onLocalBooks: () -> Unit,
    onAbout: () -> Unit
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
        ListItem(
            headlineContent = { Text("About") },
            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onAbout)
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
    libraryOptions: List<LibrarySummary>,
    modifier: Modifier,
    loader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    imageLoader: suspend (String) -> ByteArray?,
    onSeriesSelected: (SeriesSummary) -> Unit
) {
    var items by remember(query) { mutableStateOf<List<SeriesSummary>>(emptyList()) }
    var total by remember(query) { mutableStateOf(0) }
    var isLoading by remember(query) { mutableStateOf(false) }
    var filter by remember(query) { mutableStateOf(SeriesCatalogFilter(query = query.takeIf { it.isNotBlank() })) }
    var showFilter by rememberSaveable(query) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

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
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
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
        if (jumpTargets.isNotEmpty()) {
            LibraryJumpRail(
                targets = jumpTargets,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 12.dp),
                onJump = { index ->
                    scope.launch { gridState.animateScrollToItem(index + 1) }
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
            Box {
                BookCover(book, coverLoader)
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
private fun OptionsScreen(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrbitEyebrow("Options")
        Text("Options", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Reader and app options will appear here later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    onMarkAsUnread: (BookSummary) -> Unit
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
            modifier = Modifier.fillMaxSize(),
            coverLoader = coverLoader,
            onBookSelected = onBookSelected,
            onSeriesSelected = onSeriesSelected,
            onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
            onMarkAsRead = onMarkAsRead,
            onMarkAsUnread = onMarkAsUnread
        )
    }
}

@Composable
private fun HomeFeed(
    state: BrowserState,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    showHeader: Boolean = false
) {
    val currentlyReading = remember(state.books) { currentlyReadingBooks(state.books) }
    val onDeck = remember(state.books) { onDeckBooks(state.books) }
    val recentlyAddedBooks = state.books.sortedWith(
        compareByDescending<BookSummary> { it.addedAtMillis != null }
            .thenByDescending { it.addedAtMillis ?: 0L }
    ).take(12)
    val recentSeries = remember(state.books) { recentSeries(state.books, useUpdatedAt = false) }
    val updatedSeries = remember(state.books) { recentSeries(state.books, useUpdatedAt = true) }
    val recentlyRead = state.books
        .filter { it.isRead || it.lastReadAtMillis != null }
        .sortedByDescending { it.lastReadAtMillis ?: 0L }
        .take(12)
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
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR
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
    onMarkAsUnread: ((BookSummary) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfTitle(title)
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
private fun ShelfTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleLarge
    )
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
        Box {
            BookCover(book, coverLoader)
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
            .aspectRatio(0.68f)
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
    onMarkAsUnread: (BookSummary) -> Unit
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
            gridState.animateScrollToItem(targetIndex + 1)
        }
        pendingAnchor = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = BOOK_CARD_MIN_SIZE),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedBookIds.isNotEmpty()) {
                    Text("${selectedBookIds.size} selected", modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedBooks.forEach { onMarkAsRead?.invoke(it) }; selectedBookIds = emptySet() }) { Text("Mark read") }
                    TextButton(onClick = { selectedBooks.forEach { onMarkAsUnread?.invoke(it) }; selectedBookIds = emptySet() }) { Text("Mark unread") }
                    TextButton(onClick = { selectedBookIds = emptySet() }) { Text("Clear") }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        buildString {
                            val bookCount = totalBooks ?: state.books.size
                            append("$bookCount ${if (bookCount == 1) "book" else "books"}")
                            if (seriesCount != null && seriesCount > 0) append(" · $seriesCount series")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onFilterClick != null) {
                    val filterAction = onFilterClick
                    OutlinedButton(onClick = filterAction) {
                        Text(if (filter?.isActive == true) "Filter · active" else "Filter")
                    }
                }
                if (allowSeriesCollapse && seriesKeys.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val contentIndex = gridState.firstVisibleItemIndex - 1
                            val anchor = displayedBooks.getOrNull(contentIndex)
                            pendingAnchor = anchor?.let { (book, seriesKey) ->
                                LibraryGridAnchor(
                                    bookId = book.id,
                                    seriesKey = seriesKey ?: book.seriesId ?: book.seriesName
                                )
                            }
                            seriesCollapsed = !seriesCollapsed
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (seriesCollapsed) "Expand series" else "Collapse series"
                        }
                    ) {
                        Text(if (seriesCollapsed) "Expand series" else "Collapse series")
                    }
                }
            }
        }
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
        if (jumpTargets.isNotEmpty()) {
            LibraryJumpRail(
                targets = jumpTargets,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 12.dp),
                onJump = { index ->
                    scope.launch {
                        gridState.animateScrollToItem(index + 1)
                    }
                }
            )
        }
    }
}

@Composable
private fun LibraryJumpRail(
    targets: List<Pair<Char, Int>>,
    modifier: Modifier,
    onJump: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        targets.forEach { (label, index) ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onJump(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        contentDescription = "Jump to $label"
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
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val books by produceState<List<BookSummary>?>(initialValue = null) {
        value = loader()
    }
    var filter by remember { mutableStateOf(BookBrowseFilter()) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val filteredBooks = remember(books, filter) {
        filterAndSortLocalBooks(books.orEmpty(), filter)
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
        titleOverride = "Local books",
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
    onRead: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit
) {
    val detail by produceState(initialValue = BookDetailInfo(book), book.id) {
        value = detailLoader(book) ?: value
    }
    var showCoverViewer by rememberSaveable(book.id) { mutableStateOf(false) }
    val displayBook = detail.book
    val fileId = displayBook.fileId
    val isDownloading = fileId != null && fileId in state.downloadingFileIds
    val unavailableOffline = state.isOfflineSnapshot && !displayBook.isDownloaded
    val seriesKey = displayBook.seriesId ?: displayBook.seriesName
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
                    Text(nativeBookStatus(displayBook, state.isOfflineSnapshot), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            LazyRow(
                modifier = Modifier.testTag("book-detail-actions"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "read") {
                    DetailActionTile(
                        label = if (displayBook.progressPercent?.let { it > 0f } == true) {
                            "Continue reading"
                        } else {
                            "Read"
                        },
                        icon = Icons.Default.PlayArrow,
                        emphasized = true,
                        enabled = !isDownloading && !unavailableOffline,
                        onClick = { onRead(displayBook) }
                    )
                }
                item(key = "preview") {
                    DetailActionTile(
                        label = "Preview",
                        icon = Icons.Default.Search,
                        enabled = !isDownloading && !unavailableOffline,
                        onClick = { onPreview(displayBook) }
                    )
                }
                when {
                    displayBook.isDownloaded -> item(key = "delete-local") {
                        DetailActionTile(
                            label = "Delete local",
                            icon = Icons.Default.Delete,
                            enabled = !isDownloading,
                            onClick = { onDeleteLocalCopy(displayBook) }
                        )
                    }
                    isDownloading -> item(key = "cancel-download") {
                        DetailActionTile(
                            label = "Cancel download",
                            icon = Icons.Default.Close,
                            onClick = { onCancelDownload(displayBook) }
                        )
                    }
                    fileId != null && !state.isOfflineSnapshot -> item(key = "download") {
                        DetailActionTile(
                            label = "Download",
                            icon = Icons.Default.ArrowDropDown,
                            onClick = { onDownload(displayBook) }
                        )
                    }
                }
            }
        }
        detail.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            item { ExpandableDescription("Synopsis", plainText(synopsis)) }
        }
        if (detail.genres.isNotEmpty()) {
            item {
                DetailLabelGroup(
                    title = "Genres",
                    labels = detail.genres
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
private fun DetailActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(46.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailLabelGroup(title: String, labels: List<String>) {
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
                    .aspectRatio(0.68f)
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

@Composable
private fun SeriesDetails(
    seriesKey: String,
    books: List<BookSummary>,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    detailLoader: suspend (String) -> SeriesDetailInfo?,
    onBookSelected: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
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
            val labels = (first.genres + first.tags).distinct()
            if (labels.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Genres and tags", style = MaterialTheme.typography.titleLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            labels.forEach { label ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Books", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        }
        gridItems(detail.books, key = { "series-detail-${it.id}" }) { book ->
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
