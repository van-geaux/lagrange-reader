package com.bookorbit.android

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class BrowserDestination { HOME, LIBRARY }

@Composable
internal fun NativeLibraryBrowserScreen(
    state: BrowserState,
    onRefresh: () -> Unit,
    onSessionAction: () -> Unit,
    onLibrarySelected: (String) -> Unit,
    onBookOpen: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(BrowserDestination.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredBooks = remember(state.books, query) {
        if (query.isBlank()) state.books else state.books.filter { book ->
            listOf(book.title, book.author, book.seriesName)
                .filterNotNull()
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BrowserDrawer(
                state = state,
                destination = destination,
                onHome = {
                    destination = BrowserDestination.HOME
                    query = ""
                    scope.launch { drawerState.close() }
                },
                onLibraries = {
                    destination = BrowserDestination.LIBRARY
                    query = ""
                    scope.launch { drawerState.close() }
                },
                onLibrarySelected = { libraryId ->
                    destination = BrowserDestination.LIBRARY
                    query = ""
                    onLibrarySelected(libraryId)
                    scope.launch { drawerState.close() }
                },
                onSessionAction = {
                    scope.launch { drawerState.close() }
                    onSessionAction()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                BrowserSearchBar(
                    query = query,
                    isRefreshing = state.isRefreshing,
                    onQueryChange = { query = it },
                    onMenu = { scope.launch { drawerState.open() } },
                    onRefresh = onRefresh
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            when {
                query.isNotBlank() -> SearchResults(
                    books = filteredBooks,
                    state = state,
                    modifier = Modifier.padding(padding),
                    onBookOpen = onBookOpen,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy
                )
                destination == BrowserDestination.HOME -> HomeFeed(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onBookOpen = onBookOpen
                )
                else -> LibraryBooks(
                    state = state,
                    modifier = Modifier.padding(padding),
                    onBookOpen = onBookOpen,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy
                )
            }
        }
    }
}

@Composable
private fun BrowserSearchBar(
    query: String,
    isRefreshing: Boolean,
    onQueryChange: (String) -> Unit,
    onMenu: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Default.Menu, contentDescription = "Open navigation")
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun BrowserDrawer(
    state: BrowserState,
    destination: BrowserDestination,
    onHome: () -> Unit,
    onLibraries: () -> Unit,
    onLibrarySelected: (String) -> Unit,
    onSessionAction: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 20.dp)
        ) {
            OrbitEyebrow("BookOrbit", modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "Your reading space",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            NavigationDrawerItem(
                label = { Text("Home") },
                selected = destination == BrowserDestination.HOME,
                onClick = onHome,
                icon = { Icon(Icons.Default.Home, contentDescription = null) }
            )
            NavigationDrawerItem(
                label = { Text("Libraries") },
                selected = destination == BrowserDestination.LIBRARY,
                onClick = onLibraries,
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
            )
            state.libraries.forEach { library ->
                NavigationDrawerItem(
                    label = { Text(library.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = destination == BrowserDestination.LIBRARY && library.id == state.selectedLibraryId,
                    onClick = {
                        if (!state.isLoadingBooks) onLibrarySelected(library.id)
                    },
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .semantics {
                            if (state.isLoadingBooks) disabled()
                        }
                )
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text(if (state.isOfflineSnapshot) "Sign in" else "Log out") },
                selected = false,
                onClick = onSessionAction,
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun HomeFeed(state: BrowserState, modifier: Modifier, onBookOpen: (BookSummary) -> Unit) {
    val keepReading = state.books
        .filter { (it.progressPercent ?: 0f) > 0f && !it.isRead }
        .sortedByDescending { it.lastReadAtMillis ?: it.progressPercent?.toLong() ?: 0L }
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
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
        state.message?.let { message ->
            item {
                OrbitMessage(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR
                )
            }
        }
        if (keepReading.isNotEmpty()) item { BookShelf("Keep reading", keepReading, onBookOpen) }
        if (onDeck.isNotEmpty()) item { BookShelf("On deck", onDeck, onBookOpen) }
        if (recentlyAddedBooks.isNotEmpty()) item { BookShelf("Recently added books", recentlyAddedBooks, onBookOpen) }
        if (recentSeries.isNotEmpty()) item { SeriesShelf("Recently added series", recentSeries, onBookOpen) }
        if (updatedSeries.isNotEmpty()) item { SeriesShelf("Recently updated series", updatedSeries, onBookOpen) }
        if (recentlyRead.isNotEmpty()) item { BookShelf("Recently read books", recentlyRead, onBookOpen) }
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
private fun BookShelf(title: String, books: List<BookSummary>, onBookOpen: (BookSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books, key = { "$title-${it.id}" }) { book ->
                ShelfBookCard(book = book, onClick = { onBookOpen(book) })
            }
        }
    }
}

@Composable
private fun SeriesShelf(title: String, series: List<Pair<String, BookSummary>>, onBookOpen: (BookSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series, key = { "$title-${it.first}" }) { (name, book) ->
                ShelfBookCard(book = book, displayTitle = name, onClick = { onBookOpen(book) })
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

@Composable
private fun ShelfBookCard(
    book: BookSummary,
    displayTitle: String = book.title,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BookCoverPlaceholder(book)
        Text(
            displayTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        book.author?.let {
            Text(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BookCoverPlaceholder(book: BookSummary) {
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
        Text(
            book.title.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.displaySmall
        )
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

@Composable
private fun SearchResults(
    books: List<BookSummary>,
    state: BrowserState,
    modifier: Modifier,
    onBookOpen: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    LibraryBookList(
        title = "Search results",
        books = books,
        state = state,
        modifier = modifier,
        onBookOpen = onBookOpen,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onDeleteLocalCopy = onDeleteLocalCopy
    )
}

@Composable
private fun LibraryBooks(
    state: BrowserState,
    modifier: Modifier,
    onBookOpen: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    val title = state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name ?: "Library"
    LibraryBookList(
        title = title,
        books = state.books,
        state = state,
        modifier = modifier,
        onBookOpen = onBookOpen,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onDeleteLocalCopy = onDeleteLocalCopy
    )
}

@Composable
private fun LibraryBookList(
    title: String,
    books: List<BookSummary>,
    state: BrowserState,
    modifier: Modifier,
    onBookOpen: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall) }
        if (state.isLoadingBooks) item { LoadingFeedRow("Loading books...") }
        state.message?.let { message ->
            item {
                OrbitMessage(
                    message,
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR
                )
            }
        }
        if (!state.isLoadingBooks && books.isEmpty()) {
            item { Text("No books found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(books, key = { it.id }) { book ->
            LibraryBookCard(
                book = book,
                state = state,
                onBookOpen = onBookOpen,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDeleteLocalCopy = onDeleteLocalCopy
            )
        }
    }
}

@Composable
private fun LibraryBookCard(
    book: BookSummary,
    state: BrowserState,
    onBookOpen: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    val fileId = book.fileId
    val isDownloading = fileId != null && fileId in state.downloadingFileIds
    val failed = fileId != null && fileId in state.failedDownloadFileIds
    val unavailableOffline = state.isOfflineSnapshot && !book.isDownloaded
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.width(72.dp)) { BookCoverPlaceholder(book) }
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
        }
    }
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
            val hasStarted = ordered.any { it.isRead || (it.progressPercent ?: 0f) > 0f }
            if (!hasStarted) null else ordered.firstOrNull { !it.isRead && (it.progressPercent ?: 0f) <= 0f }
        }
        .take(12)
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
