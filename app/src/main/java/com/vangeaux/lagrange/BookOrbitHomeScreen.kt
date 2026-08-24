package com.vangeaux.lagrange

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.util.DisplayMetrics
import android.util.LruCache
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class BrowserDestination { HOME, LIBRARY, SERIES, AUTHORS, ANNOTATIONS, LOCAL_BOOKS, STATISTICS, ACHIEVEMENTS, OPTIONS, ABOUT }
private enum class LibraryTab { RECOMMENDED, BROWSE }

private fun <T> browserRouteProperty(
    value: () -> T,
    update: (T) -> Unit
): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        update(value)
    }
}

private enum class OptionsDialog {
    THEME,
    OPENING_SCREEN,
    LIBRARY_CARD_SIZE,
    CELLULAR_DOWNLOADS,
    BACKGROUND_REFRESH,
    CLEAR_CACHE
}
private val CATALOG_GRID_PADDING = 16.dp
private val CATALOG_JUMP_RAIL_END_PADDING = 32.dp

internal fun libraryCardGridMinSize(size: LibraryCardSize): androidx.compose.ui.unit.Dp = when (size) {
        LibraryCardSize.SMALL -> 88.dp
        LibraryCardSize.MEDIUM -> 110.dp
        LibraryCardSize.LARGE -> 132.dp
    }

internal fun libraryShelfCardWidth(size: LibraryCardSize): androidx.compose.ui.unit.Dp = when (size) {
        LibraryCardSize.SMALL -> 84.dp
        LibraryCardSize.MEDIUM -> 105.dp
        LibraryCardSize.LARGE -> 126.dp
    }

private val LibraryCardSize.gridMinSize: androidx.compose.ui.unit.Dp
    get() = libraryCardGridMinSize(this)

private val LibraryCardSize.shelfWidth: androidx.compose.ui.unit.Dp
    get() = libraryShelfCardWidth(this)

internal fun catalogGridEndPadding(hasJumpRail: Boolean) =
    if (hasJumpRail) CATALOG_JUMP_RAIL_END_PADDING else CATALOG_GRID_PADDING

internal val LocalReduceMotion = staticCompositionLocalOf { false }
internal val LocalLibraryCardSize = staticCompositionLocalOf { LibraryCardSize.SMALL }

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

internal fun buildAuthorJumpTargets(
    authors: List<AuthorSummary>
): List<Pair<Char, Int>> = buildAlphabetJumpTargets(
    labels = authors.map { libraryJumpLabel(it.name) },
    direction = SortDirection.ASCENDING
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

internal fun booksDownloadableForLibrary(books: List<BookSummary>, libraryId: String?): List<BookSummary> =
    if (libraryId == null) emptyList() else books.filter { it.libraryId == libraryId && it.fileId != null }

internal fun booksDownloadableForSeries(books: List<BookSummary>): List<BookSummary> =
    books.filter { it.fileId != null }

internal fun seriesSelectableFiles(books: List<BookSummary>): List<BookSummary> =
    booksDownloadableForSeries(books).distinctBy { it.fileId }

internal fun seriesDownloadDispatchOrder(books: List<BookSummary>): List<BookSummary> =
    books.sortedWith(
        compareBy<BookSummary> { it.seriesIndex == null }
            .thenBy { it.seriesIndex ?: 0.0 }
            .thenBy { it.title.lowercase() }
            .thenBy { it.fileId.orEmpty() }
    )

internal fun seriesFileIsSelectable(book: BookSummary): Boolean =
    !book.isDownloaded || book.hasDownloadUpdate

internal fun hasSelectableBulkDownloads(books: List<BookSummary>): Boolean =
    seriesSelectableFiles(books).any(::seriesFileIsSelectable)

internal fun defaultSeriesFileSelection(books: List<BookSummary>): Set<String> =
    seriesSelectableFiles(books)
        .filter { seriesFileIsSelectable(it) }
        .mapNotNull { it.fileId }
        .toSet()

internal fun selectedSeriesFiles(books: List<BookSummary>, selectedFileIds: Set<String>): List<BookSummary> =
    seriesSelectableFiles(books).filter { seriesFileIsSelectable(it) && selectedFileIds.contains(it.fileId) }

internal fun booksWithLocalFilePathOverrides(
    books: List<BookSummary>,
    localFilePathOverrides: Map<String, String?>
): List<BookSummary> = books.map { book ->
    val fileId = book.fileId
    if (fileId != null && localFilePathOverrides.containsKey(fileId)) {
        book.copy(localPath = localFilePathOverrides[fileId])
    } else {
        book
    }
}

internal fun resolveInitialSelectedFileId(
    availableFiles: List<BookFileOption>,
    localFilePathOverrides: Map<String, String?>,
    currentFileId: String?
): String? {
    val downloaded = availableFiles.firstOrNull { option ->
        val fileId = option.fileId ?: return@firstOrNull false
        val effectiveLocalPath = if (localFilePathOverrides.containsKey(fileId)) {
            localFilePathOverrides[fileId]
        } else {
            option.localPath
        }
        !effectiveLocalPath.isNullOrBlank()
    }
    return downloaded?.fileId ?: currentFileId
}

internal fun localCopiesForBulkAction(
    books: List<BookSummary>,
    localFilePathOverrides: Map<String, String?>
): List<BookSummary> = seriesSelectableFiles(
    booksWithLocalFilePathOverrides(books, localFilePathOverrides)
)
    .filter { it.isDownloaded }

internal data class CollectionDownloadProgress(
    val progress: Float?,
    val activeCount: Int,
    val totalCount: Int
)

internal fun collectionDownloadProgress(
    fileIds: List<String>,
    downloadingFileIds: Set<String>,
    progressByFileId: Map<String, Float>,
    completedFileIds: Set<String>
): CollectionDownloadProgress? {
    val distinctFileIds = fileIds.distinct()
    val activeFileIds = distinctFileIds.filter { it in downloadingFileIds }
    if (activeFileIds.isEmpty()) return null
    val progress = if (activeFileIds.all(progressByFileId::containsKey)) {
        distinctFileIds.sumOf { fileId ->
            when {
                fileId in downloadingFileIds -> progressByFileId.getValue(fileId).toDouble()
                fileId in completedFileIds -> 1.0
                else -> 0.0
            }
        }.div(distinctFileIds.size).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    return CollectionDownloadProgress(
        progress = progress,
        activeCount = activeFileIds.size,
        totalCount = distinctFileIds.size
    )
}

private data class BulkDownloadGroup(
    val key: String,
    val label: String,
    val books: List<BookSummary>
)

private data class PendingBulkDownload(
    val scopeLabel: String,
    val books: List<BookSummary>
)

internal fun bulkDownloadGroupKeys(
    books: List<BookSummary>,
    libraries: List<LibrarySummary>
): List<String> {
    val libraryNames = libraries.associate { it.id to it.name }
    return seriesSelectableFiles(books)
        .groupBy { book ->
            val library = libraryNames[book.libraryId] ?: book.libraryId
            val format = book.format?.takeIf { it.isNotBlank() }
                ?: book.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
            "$library · $format"
        }
        .keys
        .sorted()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesFileSelectionDialog(
    candidates: List<BookSummary>,
    libraries: List<LibrarySummary>,
    groupingMode: SeriesGroupingMode,
    onGroupingModeChange: (SeriesGroupingMode) -> Unit,
    selectedFileIds: Set<String>,
    onSelectedFileIdsChange: (Set<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Select downloads"
) {
    val libraryNames = remember(libraries) { libraries.associate { it.id to it.name } }
    val groups = remember(candidates, libraryNames) {
        candidates
            .groupBy { book ->
                val library = libraryNames[book.libraryId] ?: book.libraryId
                val format = book.format?.takeIf { it.isNotBlank() }
                    ?: book.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
                "$library · $format"
            }
            .map { (key, books) -> BulkDownloadGroup(key, key, books) }
            .sortedBy { it.label.lowercase() }
    }
    val selectableIds = remember(candidates) {
        candidates.filter(::seriesFileIsSelectable).mapNotNull { it.fileId }.toSet()
    }
    val selectedCount = selectedFileIds.intersect(selectableIds).size
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("bulk-download-selection-sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "$selectedCount of ${selectableIds.size} files selected",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSelectedFileIdsChange(selectableIds) },
                    modifier = Modifier.testTag("bulk-download-select-all")
                ) { Text("Select all") }
                TextButton(
                    onClick = { onSelectedFileIdsChange(emptySet()) },
                    modifier = Modifier.testTag("bulk-download-clear-all")
                ) { Text("Clear all") }
            }
            if (groupingMode != SeriesGroupingMode.NONE) {
                SeriesGroupingControls(groupingMode, onGroupingModeChange)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                groups.forEach { group ->
                    item(key = "bulk-group-${group.key}") {
                        Text(
                            group.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(group.books, key = { "bulk-file-${it.fileId}" }) { book ->
                        val fileId = book.fileId ?: return@items
                        val selectable = seriesFileIsSelectable(book)
                        val checked = !selectable || selectedFileIds.contains(fileId)
                        ListItem(
                            headlineContent = {
                                Text(
                                    buildString {
                                        book.seriesIndex?.let { append("#${formatSeriesIndex(it)} · ") }
                                        append(book.title)
                                    }
                                )
                            },
                            supportingContent = {
                                Text(if (selectable) group.label else "Downloaded")
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    enabled = selectable,
                                    onCheckedChange = { value ->
                                        onSelectedFileIdsChange(
                                            if (value) selectedFileIds + fileId else selectedFileIds - fileId
                                        )
                                    }
                                )
                            },
                            modifier = Modifier.testTag("bulk-download-file-$fileId")
                        )
                    }
                }
            }
            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth().testTag("bulk-download-continue")
            ) { Text("Continue") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CollectionDownloadProgressIndicator(
    state: CollectionDownloadProgress,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            state.progress?.let { progress ->
                "Downloading ${state.totalCount} ${if (state.totalCount == 1) "file" else "files"} · " +
                    "${(progress * 100).toInt()}%"
            } ?: "Downloading ${state.totalCount} ${if (state.totalCount == 1) "file" else "files"}…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
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

internal fun homeLocalBooksPreview(
    catalogHomeBooks: List<BookSummary>,
    downloadedBooks: List<BookSummary>?,
    libraryId: String? = null,
    limit: Int = 12
): List<BookSummary> = localBooksShelf(
    books = downloadedBooks ?: catalogHomeBooks,
    libraryId = libraryId,
    limit = limit
)

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

internal fun bookDetailOtherVersions(
    current: BookSummary,
    candidates: List<BookSummary>
): List<BookSummary> {
    val seriesIndex = current.seriesIndex ?: return emptyList()
    if (current.seriesId.isNullOrBlank() && current.seriesName.isNullOrBlank()) return emptyList()
    return candidates
        .asSequence()
        .filter { candidate ->
            candidate.id != current.id &&
                booksShareSeries(current, candidate) &&
                candidate.seriesIndex == seriesIndex
        }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<BookSummary> { it.title.lowercase() }
                .thenBy { it.format?.lowercase().orEmpty() }
                .thenBy { it.libraryId }
                .thenBy { it.id }
        )
        .toList()
}

private val EPUB_FAMILY_FORMATS = setOf("epub", "kepub")
private val PDF_FAMILY_FORMATS = setOf("pdf")
private val COMIC_FAMILY_FORMATS = setOf("cbz", "cbr", "cb7")
private val AUDIO_FAMILY_FORMATS = setOf(
    "mp3", "mpeg", "m4a", "m4b", "mp4", "x-m4b", "aac", "aif", "aiff", "flac", "ogg", "oga", "opus", "wav", "webm"
)

private fun formatFamilyKey(book: BookSummary): Int? {
    if (book.mediaKind != MediaKind.UNKNOWN) {
        return when (book.mediaKind) {
            MediaKind.EPUB -> 0
            MediaKind.PDF -> 1
            MediaKind.COMIC -> 2
            MediaKind.AUDIO -> 3
            MediaKind.UNKNOWN -> null
        }
    }
    val format = book.format?.lowercase()
    return when {
        format in EPUB_FAMILY_FORMATS || book.mediaKind == MediaKind.EPUB -> 0
        format in PDF_FAMILY_FORMATS || book.mediaKind == MediaKind.PDF -> 1
        format in COMIC_FAMILY_FORMATS || book.mediaKind == MediaKind.COMIC -> 2
        format in AUDIO_FAMILY_FORMATS || book.mediaKind == MediaKind.AUDIO -> 3
        else -> null
    }
}

private fun formatFamilyRank(book: BookSummary): Int = formatFamilyKey(book) ?: 4

private fun bestSeriesTarget(
    current: BookSummary,
    targets: List<BookSummary>,
    libraryOrder: List<String>
): BookSummary? {
    if (targets.size <= 1) return targets.firstOrNull()
    val tieBreak = compareBy<BookSummary> { it.title.lowercase() }.thenBy { it.id }
    val libraryRank = libraryOrder.withIndex().associate { (rank, id) -> id to rank }
    val currentFamily = formatFamilyKey(current)

    if (currentFamily != null) {
        targets
            .filter { it.libraryId == current.libraryId && formatFamilyKey(it) == currentFamily }
            .minWithOrNull(tieBreak)
            ?.let { return it }

        targets
            .filter { formatFamilyKey(it) == currentFamily }
            .minWithOrNull(
                compareBy<BookSummary> { libraryRank[it.libraryId] ?: Int.MAX_VALUE }.thenComparing(tieBreak)
            )
            ?.let { return it }
    }

    return targets.minWithOrNull(
        compareBy<BookSummary> { libraryRank[it.libraryId] ?: Int.MAX_VALUE }
            .thenBy { formatFamilyRank(it) }
            .thenComparing(tieBreak)
    )
}

internal fun seriesBookNeighbors(
    current: BookSummary,
    candidates: List<BookSummary>,
    libraryOrder: List<String> = emptyList()
): SeriesBookNeighbors {
    val seriesId = current.seriesId?.takeIf { it.isNotBlank() }
    val seriesName = current.seriesName?.takeIf { it.isNotBlank() }
    if (seriesId == null && seriesName == null) {
        return SeriesBookNeighbors(previous = null, next = null, total = 0)
    }
    val sameSeries = (candidates + current)
        .filter { candidate -> booksShareSeries(current, candidate) }
        .distinctBy { it.id }
    val slots = (
        sameSeries.filter { it.seriesIndex != null }.groupBy { it.seriesIndex }.values +
            sameSeries.filter { it.seriesIndex == null }.map { listOf(it) }
        )
        .sortedWith(
            compareBy<List<BookSummary>> { it.first().seriesIndex ?: Double.MAX_VALUE }
                .thenBy { it.first().title.lowercase() }
                .thenBy { it.first().id }
        )
    val currentSlotIndex = slots.indexOfFirst { slot ->
        if (current.seriesIndex != null) {
            slot.first().seriesIndex == current.seriesIndex
        } else {
            slot.any { it.id == current.id }
        }
    }
    return SeriesBookNeighbors(
        previous = slots.getOrNull(currentSlotIndex - 1)?.let { bestSeriesTarget(current, it, libraryOrder) },
        next = slots.getOrNull(currentSlotIndex + 1)?.let { bestSeriesTarget(current, it, libraryOrder) },
        total = slots.size
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
    sessionHistoryLoader: suspend (BookSummary) -> List<AudiobookSessionEvent> = { emptyList() },
    onSessionHistoryEntryClick: (BookSummary, Long) -> Unit = { _, _ -> },
    onClearSessionHistory: (BookSummary) -> Unit = {},
    serverReadingSessionsLoader: suspend (String) -> BookReadingSessionsResult = {
        BookReadingSessionsResult(status = ServerReadingHistoryStatus.UNSUPPORTED)
    },
    serverReadingAttemptsLoader: suspend (String) -> ReadingAttemptsResult = {
        ReadingAttemptsResult(status = ServerReadingHistoryStatus.UNSUPPORTED)
    },
    onBookUserRatingChange: suspend (BookSummary, Int?) -> BookDetailInfo?,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    authorsCatalogLoader: suspend (String?, Int) -> AuthorCatalogPage,
    authorBooksLoader: suspend (String, Int) -> AuthorBooksPage?,
    annotationsLoader: suspend (AnnotationsFilter, Int) -> BookAnnotationsPage,
    onAnnotationSelected: (BookAnnotation) -> Unit = {},

    onUpdateAnnotation: suspend (annotation: BookAnnotation, note: String?, color: String?, style: String?) -> Boolean =
        { _, _, _, _ -> false },
    onTrashAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    onRestoreAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    onPurgeAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    achievementsLoader: suspend () -> AchievementCatalogue,
    statisticsLoader: suspend () -> UserStatistics,
    catalogImageLoader: suspend (String) -> ByteArray?,
    onBookOpen: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onClearAllFailedDownloads: () -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDeleteLocalCopies: (List<BookSummary>) -> Unit,
    onDismissMessage: () -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: (BookSummary, BookReadStatus) -> Unit,
    appPreferences: AppPreferences = AppPreferences(),
    onAppPreferencesChange: (AppPreferences) -> Unit = {},
    releaseCheckStatus: ReleaseCheckStatus = ReleaseCheckStatus.IDLE,
    onCheckForUpdates: () -> Unit = {},
    storageUsageLoader: suspend () -> StorageUsage = { StorageUsage() },
    onClearCache: suspend () -> Unit = {},
    offlineCacheStatusLoader: suspend () -> OfflineCacheStatus = { OfflineCacheStatus() },
    onStartOfflineCacheUpdate: suspend () -> Boolean = { false },
    onCancelOfflineCacheUpdate: suspend () -> Unit = {},
    onClearOfflineCache: suspend () -> Unit = {},
    bookDetailRequest: AudioBookDetailRequest? = null,
    onBookDetailRequestConsumed: (Long) -> Unit = {},
    bottomOverlay: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    var destination by rememberSaveable {
        mutableStateOf(appPreferences.defaultOpeningScreen.toBrowserDestination())
    }
    var query by rememberSaveable { mutableStateOf("") }
    val remoteSearchResults by produceState<List<BookSummary>?>(initialValue = null, query) {
        value = null
        if (query.isNotBlank()) {
            delay(300)
            value = searchBooks(query)
        }
    }
    val filteredBooks = remoteSearchResults.orEmpty()
    var showLibraryPicker by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showProfileMenu by rememberSaveable { mutableStateOf(false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableStateOf(LibraryTab.RECOMMENDED) }
    var browserRoute by rememberSaveable(stateSaver = BrowserRouteSaver) {
        mutableStateOf(BrowserRouteSnapshot())
    }
    var openSessionHistoryRequest by rememberSaveable { mutableStateOf(false) }
    val selectedBookProperty = object : ReadWriteProperty<Any?, BookSummary?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): BookSummary? =
            resolveSelectedBook(listOf(state.books, state.homeBooks, filteredBooks), browserRoute.selectedBook)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: BookSummary?) {
            openSessionHistoryRequest = false
            browserRoute = browserRoute.copy(selectedBook = value?.let(::bookSelectionSnapshot))
        }
    }
    var selectedBook by selectedBookProperty
    val selectedSeriesKeyProperty = browserRouteProperty(
        value = { browserRoute.selectedSeriesKey },
        update = { browserRoute = browserRoute.copy(selectedSeriesKey = it) }
    )
    var selectedSeriesKey by selectedSeriesKeyProperty
    val selectedAuthorProperty = browserRouteProperty(
        value = { browserRoute.selectedAuthor },
        update = { browserRoute = browserRoute.copy(selectedAuthor = it) }
    )
    var selectedAuthor by selectedAuthorProperty
    val activeBookGenreProperty = browserRouteProperty(
        value = { browserRoute.activeBookGenre },
        update = { browserRoute = browserRoute.copy(activeBookGenre = it) }
    )
    var activeBookGenre by activeBookGenreProperty
    val activeSeriesGenreProperty = browserRouteProperty(
        value = { browserRoute.activeSeriesGenre },
        update = { browserRoute = browserRoute.copy(activeSeriesGenre = it) }
    )
    var activeSeriesGenre by activeSeriesGenreProperty
    val genreSourceBookProperty = object : ReadWriteProperty<Any?, BookSummary?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): BookSummary? =
            resolveSelectedBook(listOf(state.books, state.homeBooks, filteredBooks), browserRoute.genreSourceBook)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: BookSummary?) {
            browserRoute = browserRoute.copy(genreSourceBook = value?.let(::bookSelectionSnapshot))
        }
    }
    var genreSourceBook by genreSourceBookProperty
    val genreSourceSeriesKeyProperty = browserRouteProperty(
        value = { browserRoute.genreSourceSeriesKey },
        update = { browserRoute = browserRoute.copy(genreSourceSeriesKey = it) }
    )
    var genreSourceSeriesKey by genreSourceSeriesKeyProperty
    val detailReturnDestinationProperty = browserRouteProperty(
        value = { browserRoute.detailReturnDestination },
        update = { browserRoute = browserRoute.copy(detailReturnDestination = it) }
    )
    var detailReturnDestination by detailReturnDestinationProperty
    var pendingCellularDownload by remember { mutableStateOf<BookSummary?>(null) }
    var pendingCellularBulkDownload by remember { mutableStateOf<PendingBulkDownload?>(null) }
    var showCellularDownloadBlocked by remember { mutableStateOf(false) }
    var pendingLocalDelete by remember { mutableStateOf<BookSummary?>(null) }
    var localBooksLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeServerEditor by rememberSaveable { mutableStateOf(false) }
    var changeServerUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var changeServerError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingServerChange by rememberSaveable { mutableStateOf<String?>(null) }
    val browserScope = rememberCoroutineScope()

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
        openSessionHistoryRequest = request.openSessionHistory
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
    val requestBulkDownload: (String, List<BookSummary>) -> Unit = { scopeLabel, books ->
        val frozen = seriesSelectableFiles(books)
        if (frozen.isNotEmpty()) {
            when (
                cellularDownloadDecision(
                    policy = appPreferences.cellularDownloadPolicy,
                    isCellularOrMetered = context.isActiveCellularOrMeteredNetwork()
                )
            ) {
                CellularDownloadDecision.START -> frozen.forEach(onDownload)
                CellularDownloadDecision.ASK -> {
                    pendingCellularBulkDownload = PendingBulkDownload(scopeLabel, frozen)
                }
                CellularDownloadDecision.BLOCK -> showCellularDownloadBlocked = true
            }
        }
    }
    val requestLocalDelete: (BookSummary) -> Unit = { book ->
        if (appPreferences.confirmDeleteLocalCopy) {
            pendingLocalDelete = book
        } else {
            onDeleteLocalCopy(book)
        }
    }
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
    val openStatistics = {
        showProfileMenu = false
        showMoreMenu = false
        destination = BrowserDestination.STATISTICS
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
                    selectedBook = null
                    destination = BrowserDestination.SERIES
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onAuthors = {
                    showMoreMenu = false
                    selectedBook = null
                    destination = BrowserDestination.AUTHORS
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onAnnotations = {
                    showMoreMenu = false
                    selectedBook = null
                    destination = BrowserDestination.ANNOTATIONS
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onLocalBooks = {
                    showMoreMenu = false
                    selectedBook = null
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
    pendingCellularBulkDownload?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingCellularBulkDownload = null },
            title = { Text("Download using cellular data?") },
            text = {
                Text(
                    "Downloading ${pending.books.size} ${if (pending.books.size == 1) "file" else "files"} for ${pending.scopeLabel} " +
                        "may use a significant amount of mobile data."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCellularBulkDownload = null
                        pending.books.forEach(onDownload)
                    },
                    modifier = Modifier.testTag("confirm-bulk-cellular-download")
                ) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularBulkDownload = null }) { Text("Cancel") }
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
                    onStatistics = openStatistics,
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
                    onStatistics = openStatistics,
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
                    onStatistics = openStatistics,
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
                    onStatistics = openStatistics,
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
                    onStatistics = openStatistics,
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
                    onStatistics = openStatistics,
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
                        destination == BrowserDestination.ANNOTATIONS -> "Annotations"
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
                    onStatistics = openStatistics,
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
                            selectedBook = null
                            selectedAuthor = null
                            selectedSeriesKey = null
                        },
                        onLibraries = {
                            destination = BrowserDestination.LIBRARY
                            showLibraryPicker = state.selectedLibraryId == null
                            libraryTab = LibraryTab.RECOMMENDED
                            query = ""
                            selectedBook = null
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
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
                    openSessionHistory = openSessionHistoryRequest,
                    state = state,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = bookDetailLoader,
                    sessionHistoryLoader = sessionHistoryLoader,
                    onSessionHistoryEntryClick = onSessionHistoryEntryClick,
                    onClearSessionHistory = onClearSessionHistory,
                    serverReadingSessionsLoader = serverReadingSessionsLoader,
                    serverReadingAttemptsLoader = serverReadingAttemptsLoader,
                    onBookUserRatingChange = onBookUserRatingChange,
                    seriesDetailLoader = seriesDetailLoader,
                    onRead = onBookOpen,
                    onPreview = onPreview,
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsStatus = onMarkAsStatus,
                    onSeriesSelected = { seriesKey ->
                        selectedSeriesKey = seriesKey
                        selectedBook = null
                    },
                    onAuthorSelected = { authorName ->
                        browserScope.launch {
                            authorsCatalogLoader(authorName, 0).items
                                .firstOrNull { it.name.trim().equals(authorName.trim(), ignoreCase = true) }
                                ?.let { author ->
                                    destination = BrowserDestination.AUTHORS
                                    detailReturnDestination = BrowserDestination.AUTHORS
                                    selectedBook = null
                                    selectedAuthor = author
                                }
                        }
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
                    books = (state.books + state.homeBooks).distinctBy { it.id to it.fileId },
                    libraries = state.libraries,
                    groupingMode = appPreferences.seriesGroupingMode,
                    onGroupingModeChange = { mode ->
                        onAppPreferencesChange(appPreferences.copy(seriesGroupingMode = mode))
                    },
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = seriesDetailLoader,
                    onBookSelected = { selectedBook = it },
                    onDeleteLocalCopy = requestLocalDelete,
                    onDeleteLocalCopies = onDeleteLocalCopies,
                    downloadState = state,
                    onDownload = requestDownload,
                    onBulkDownload = requestBulkDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
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
                    downloadState = state,
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
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
                destination == BrowserDestination.ANNOTATIONS -> AnnotationsScreen(
                    loader = annotationsLoader,
                    onAnnotationSelected = onAnnotationSelected,
                    onBookDetails = { book ->
                        detailReturnDestination = destination
                        selectedBook = book
                    },
                    onUpdateAnnotation = onUpdateAnnotation,
                    onTrashAnnotation = onTrashAnnotation,
                    onRestoreAnnotation = onRestoreAnnotation,
                    onPurgeAnnotation = onPurgeAnnotation,
                    modifier = Modifier.padding(padding)
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
                    onDeleteLocalCopy = requestLocalDelete,
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onClearAllFailedDownloads = onClearAllFailedDownloads,
                    confirmDeleteLocalCopy = appPreferences.confirmDeleteLocalCopy,
                    onDeleteLocalCopies = onDeleteLocalCopies,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                destination == BrowserDestination.STATISTICS -> StatisticsScreen(
                    loader = statisticsLoader,
                    modifier = Modifier.padding(padding)
                )
                destination == BrowserDestination.ACHIEVEMENTS -> AchievementsScreen(
                    loader = achievementsLoader,
                    modifier = Modifier.padding(padding)
                )
                destination == BrowserDestination.OPTIONS -> OptionsScreen(
                    preferences = appPreferences,
                    libraries = state.libraries,
                    selectedLibraryId = state.selectedLibraryId,
                    onPreferencesChange = onAppPreferencesChange,
                    storageUsageLoader = storageUsageLoader,
                    onClearCache = onClearCache,
                    offlineCacheStatusLoader = offlineCacheStatusLoader,
                    onStartOfflineCacheUpdate = onStartOfflineCacheUpdate,
                    onCancelOfflineCacheUpdate = onCancelOfflineCacheUpdate,
                    onClearOfflineCache = onClearOfflineCache,
                    modifier = Modifier.padding(padding)
                )
                destination == BrowserDestination.ABOUT -> AboutScreen(
                    state = state,
                    modifier = Modifier.padding(padding),
                    releaseCheckStatus = releaseCheckStatus,
                    onCheckForUpdates = onCheckForUpdates
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
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
                destination == BrowserDestination.HOME -> RefreshableHomeFeed(
                    state = state,
                    modifier = Modifier.padding(padding),
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    localBooksLoader = localBooksLoader,
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
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
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
                    localBooksLoader = localBooksLoader,
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
                    onDownload = requestDownload,
                    onBulkDownload = requestBulkDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onDeleteLocalCopies = onDeleteLocalCopies,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
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
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread
                )
            }
            state.message?.let { message ->
                OrbitMessage(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(padding)
                        .padding(16.dp),
                    tone = if (state.isOfflineSnapshot) OrbitMessageTone.OFFLINE else OrbitMessageTone.ERROR,
                    onDismiss = onDismissMessage
                )
            }
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
    onStatistics: () -> Unit = {},
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
                        text = { Text("Statistics") },
                        leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null) },
                        onClick = {
                            onDismissProfile()
                            onStatistics()
                        }
                    )
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
    NavigationBar(modifier = Modifier.testTag("browser_bottom_navigation")) {
        NavigationBarItem(
            selected = destination == BrowserDestination.HOME,
            onClick = onHome,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = destination == BrowserDestination.LIBRARY,
            onClick = onLibraries,
            icon = { Icon(Icons.Default.LocalLibrary, contentDescription = "Libraries icon") },
            label = { Text("Libraries") }
        )
        NavigationBarItem(
            selected = destination == BrowserDestination.SERIES ||
                destination == BrowserDestination.AUTHORS ||
                destination == BrowserDestination.LOCAL_BOOKS ||
                destination == BrowserDestination.ANNOTATIONS ||
                destination == BrowserDestination.STATISTICS ||
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
    onAnnotations: () -> Unit,
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
            leadingContent = { Icon(Icons.Default.CollectionsBookmark, contentDescription = "Series icon") },
            modifier = Modifier.clickable(onClick = onSeries)
        )
        ListItem(
            headlineContent = { Text("Authors") },
            leadingContent = { Icon(Icons.Default.Groups, contentDescription = "Authors icon") },
            modifier = Modifier.clickable(onClick = onAuthors)
        )
        ListItem(
            headlineContent = { Text("Annotations") },
            leadingContent = { Icon(Icons.Default.Highlight, contentDescription = "Annotations icon") },
            modifier = Modifier.clickable(onClick = onAnnotations)
        )
        ListItem(
            headlineContent = { Text("Local books") },
            leadingContent = { Icon(Icons.Default.DownloadForOffline, contentDescription = "Local books icon") },
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
    onMarkAsUnread: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit = {}
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
                onMarkAsUnread = onMarkAsUnread,
                onClearFailedDownload = onClearFailedDownload
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
    var isRefreshing by remember(query, initialFilter) { mutableStateOf(false) }
    var reloadKey by remember(query, initialFilter) { mutableIntStateOf(0) }
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
    LaunchedEffect(reloadKey) {
        if (reloadKey == 0) return@LaunchedEffect
        try {
            val activeFilter = filter.copy(query = query.takeIf { it.isNotBlank() })
            val catalog = loadCompleteSeriesCatalog { page -> loader(activeFilter, page) }
            if (catalog.items.isNotEmpty() || items.isEmpty()) {
                items = catalog.items
                total = catalog.total ?: catalog.items.size
            }
        } finally {
            isRefreshing = false
        }
    }
    val onRefresh: () -> Unit = {
        if (!isRefreshing && !isLoading) {
            isRefreshing = true
            reloadKey += 1
        }
    }
    val jumpTargets = remember(items, filter.sort, filter.direction, isLoading) {
        if (!isLoading && filter.sort == SeriesSortOption.NAME) {
            buildSeriesJumpTargets(items, filter.direction)
        } else {
            emptyList()
        }
    }

    val hasJumpRail = jumpTargets.isNotEmpty()
    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("series_catalog_pull_to_refresh")
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("series_catalog_toolbar")
                .padding(
                    start = CATALOG_GRID_PADDING,
                    top = CATALOG_GRID_PADDING,
                    end = CATALOG_GRID_PADDING
                ),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = LocalLibraryCardSize.current.gridMinSize),
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
    var isLoading by remember(query) { mutableStateOf(false) }
    var isRefreshing by remember(query) { mutableStateOf(false) }
    var reloadKey by remember(query) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(query) {
        isLoading = true
        gridState.scrollToItem(0)
        if (query.isNotBlank()) delay(300)
        val catalog = loadCompleteAuthorCatalog { page ->
            loader(query.takeIf { it.isNotBlank() }, page)
        }
        items = catalog.items.sortedWith(
            compareBy<AuthorSummary> { it.name.trim().lowercase() }.thenBy { it.id }
        )
        total = catalog.total ?: catalog.items.size
        isLoading = false
    }
    LaunchedEffect(reloadKey) {
        if (reloadKey == 0) return@LaunchedEffect
        try {
            val catalog = loadCompleteAuthorCatalog { page ->
                loader(query.takeIf { it.isNotBlank() }, page)
            }
            if (catalog.items.isNotEmpty() || items.isEmpty()) {
                items = catalog.items.sortedWith(
                    compareBy<AuthorSummary> { it.name.trim().lowercase() }.thenBy { it.id }
                )
                total = catalog.total ?: catalog.items.size
            }
        } finally {
            isRefreshing = false
        }
    }
    val onRefresh: () -> Unit = {
        if (!isRefreshing && !isLoading) {
            isRefreshing = true
            reloadKey += 1
        }
    }
    val jumpTargets = remember(items, isLoading) {
        if (isLoading) emptyList() else buildAuthorJumpTargets(items)
    }

    val hasJumpRail = jumpTargets.isNotEmpty()
    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("authors_catalog_pull_to_refresh")
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (total > 0) "$total authors" else "Browse every accessible author",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("authors_catalog_toolbar")
                .padding(
                    start = CATALOG_GRID_PADDING,
                    top = CATALOG_GRID_PADDING,
                    end = CATALOG_GRID_PADDING
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = LocalLibraryCardSize.current.gridMinSize),
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
                    .testTag("author_card_${author.id}")
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
        }
        if (hasJumpRail) {
        LibraryJumpRail(
            targets = jumpTargets,
            direction = SortDirection.ASCENDING,
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
}

@Composable
private fun AuthorDetails(
    author: AuthorSummary,
    modifier: Modifier,
    booksLoader: suspend (String, Int) -> AuthorBooksPage?,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    downloadState: BrowserState? = null,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    val page by produceState<AuthorBooksPage?>(initialValue = null, author.id) {
        value = booksLoader(author.id, 0)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LocalLibraryCardSize.current.gridMinSize),
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
                downloadState = downloadState,
                onDownload = onDownload?.let { download -> { download(book) } },
                onCancelDownload = onCancelDownload?.let { cancel -> { cancel(book) } },
                onClearFailedDownload = onClearFailedDownload?.let { clear -> { clear(book) } },
                onDeleteLocalCopy = { onDeleteLocalCopy(book) },
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
    downloadState: BrowserState? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onClearFailedDownload: (() -> Unit)? = null,
    onMarkAsRead: (() -> Unit)? = null,
    onMarkAsUnread: (() -> Unit)? = null,
    onDeleteLocalCopy: (() -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelection: (() -> Unit)? = null
) {
    val isBookCard = supportingText == null && displayTitle == book.title
    val fileId = book.fileId
    val isDownloading = fileId != null && fileId in (downloadState?.downloadingFileIds.orEmpty())
    val downloadFailed = fileId != null && fileId in (downloadState?.failedDownloadFileIds.orEmpty())
    val hasDownloadAction = fileId != null && onDownload != null && !book.isServerMissing
    val hasActions = enabled && (onMarkAsRead != null || onMarkAsUnread != null || (book.isDownloaded && onDeleteLocalCopy != null) || hasDownloadAction || isDownloading || downloadFailed)
    var showActions by remember(book.id) { mutableStateOf(false) }
    val status = when {
        book.isLocalOnly -> "Local only"
        book.isServerMissing -> "Missing on server"
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
                            if (isDownloading) {
                                onCancelDownload?.let { cancel ->
                                    DropdownMenuItem(text = { Text("Cancel") }, onClick = { showActions = false; cancel() })
                                }
                            } else if (downloadFailed) {
                                if (!book.isServerMissing) onDownload?.let { retry ->
                                    DropdownMenuItem(
                                    text = { Text(if (book.isDownloaded) "Update local" else "Retry") },
                                    onClick = { showActions = false; retry() }
                                )
                                }
                                onClearFailedDownload?.let { clear ->
                                    DropdownMenuItem(text = { Text("Clear") }, onClick = { showActions = false; clear() })
                                }
                            } else if (!book.isDownloaded && !book.isServerMissing && downloadState?.isOfflineSnapshot != true) {
                                onDownload?.let { download ->
                                    DropdownMenuItem(text = { Text("Download local") }, onClick = { showActions = false; download() })
                                }
                            }
                            if (book.isDownloaded) onDeleteLocalCopy?.let { deleteLocal -> DropdownMenuItem(text = { Text("Delete local") }, onClick = { showActions = false; deleteLocal() }) }
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
    libraries: List<LibrarySummary> = emptyList(),
    selectedLibraryId: String? = null,
    onPreferencesChange: (AppPreferences) -> Unit,
    storageUsageLoader: suspend () -> StorageUsage = { StorageUsage() },
    onClearCache: suspend () -> Unit = {},
    offlineCacheStatusLoader: suspend () -> OfflineCacheStatus = { OfflineCacheStatus() },
    onStartOfflineCacheUpdate: suspend () -> Boolean = { false },
    onCancelOfflineCacheUpdate: suspend () -> Unit = {},
    onClearOfflineCache: suspend () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var openDialog by rememberSaveable { mutableStateOf<OptionsDialog?>(null) }
    var storageRefreshKey by rememberSaveable { mutableStateOf(0) }
    var isClearingCache by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var offlineCacheRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var readingLibraryId by rememberSaveable {
        mutableStateOf(selectedLibraryId ?: libraries.firstOrNull()?.id)
    }
    LaunchedEffect(libraries, selectedLibraryId) {
        if (readingLibraryId !in libraries.map { it.id }) {
            readingLibraryId = selectedLibraryId?.takeIf { id -> libraries.any { it.id == id } }
                ?: libraries.firstOrNull()?.id
        }
    }
    val scope = rememberCoroutineScope()
    val storageUsage by produceState<StorageUsage?>(initialValue = null, storageRefreshKey) {
        value = runCatching { storageUsageLoader() }.getOrNull()
    }
    val offlineCacheStatus by produceState(
        initialValue = OfflineCacheStatus(),
        offlineCacheRefreshKey
    ) {
        do {
            value = runCatching { offlineCacheStatusLoader() }.getOrDefault(value)
            if (value.state == OfflineCacheRunState.RUNNING) delay(750) else break
        } while (true)
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
        item(key = "library-card-size") {
            AppPreferenceSelectionRow(
                title = "Library card size",
                value = preferences.libraryCardSize.displayName,
                summary = "Apply one card size across libraries and content types",
                testTag = "options-library-card-size",
                onClick = { openDialog = OptionsDialog.LIBRARY_CARD_SIZE }
            )
        }
        item(key = "reading-configuration") {
            LibraryReaderConfiguration(
                libraries = libraries,
                selectedLibraryId = readingLibraryId,
                preferences = readingLibraryId?.let(preferences::readerPreferencesFor),
                onLibrarySelected = { readingLibraryId = it },
                onPreferencesChange = { libraryId, readerPreferences ->
                    onPreferencesChange(
                        preferences.withReaderPreferences(libraryId, readerPreferences)
                    )
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
        item(key = "offline-library-cache") {
            OfflineLibraryCacheConfiguration(
                preferences = preferences,
                libraries = libraries,
                status = offlineCacheStatus,
                onPreferencesChange = onPreferencesChange,
                onStart = {
                    scope.launch {
                        if (!onStartOfflineCacheUpdate()) {
                            storageMessage = "Select a library and at least one cache type"
                        }
                        offlineCacheRefreshKey += 1
                    }
                },
                onCancel = {
                    scope.launch {
                        onCancelOfflineCacheUpdate()
                        offlineCacheRefreshKey += 1
                    }
                },
                onClear = {
                    scope.launch {
                        onClearOfflineCache()
                        storageRefreshKey += 1
                        offlineCacheRefreshKey += 1
                    }
                }
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
        OptionsDialog.LIBRARY_CARD_SIZE -> AppPreferenceChoiceDialog(
            title = "Library card size",
            choices = LibraryCardSize.values().toList(),
            selected = preferences.libraryCardSize,
            label = LibraryCardSize::displayName,
            onSelect = {
                onPreferencesChange(preferences.copy(libraryCardSize = it))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfflineLibraryCacheConfiguration(
    preferences: AppPreferences,
    libraries: List<LibrarySummary>,
    status: OfflineCacheStatus,
    onPreferencesChange: (AppPreferences) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit
) {
    val selectedIds = preferences.offlineCacheLibraryIds
    val hasContentType = preferences.offlineCacheDetailsEnabled || preferences.offlineCacheCoversEnabled
    val isRunning = status.state == OfflineCacheRunState.RUNNING
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("options-offline-cache"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Offline library cache", style = MaterialTheme.typography.titleLarge)
            Text(
                "Keep selected library metadata and thumbnails available offline. This never downloads readable book files.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Libraries", style = MaterialTheme.typography.titleMedium)
            if (libraries.isEmpty()) {
                Text("No libraries available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    libraries.forEach { library ->
                        FilterChip(
                            selected = library.id in selectedIds,
                            onClick = {
                                val updated = if (library.id in selectedIds) {
                                    selectedIds - library.id
                                } else {
                                    selectedIds + library.id
                                }
                                onPreferencesChange(preferences.copy(offlineCacheLibraryIds = updated))
                            },
                            label = { Text(library.name) },
                            modifier = Modifier.testTag("offline-cache-library-${library.id}")
                        )
                    }
                }
            }
            AppPreferenceSwitchRow(
                title = "Book details",
                summary = "Synopsis, authors, genres, tags, series, and file metadata",
                checked = preferences.offlineCacheDetailsEnabled,
                testTag = "offline-cache-details",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(offlineCacheDetailsEnabled = it))
                }
            )
            AppPreferenceSwitchRow(
                title = "Cover thumbnails",
                summary = "Browsing-size images with a 256 MB device cache limit",
                checked = preferences.offlineCacheCoversEnabled,
                testTag = "offline-cache-covers",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(offlineCacheCoversEnabled = it))
                }
            )
            AppPreferenceSwitchRow(
                title = "Automatically refresh",
                summary = "Approximately daily on unmetered networks",
                checked = preferences.offlineCacheAutoRefreshEnabled,
                testTag = "offline-cache-auto-refresh",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(offlineCacheAutoRefreshEnabled = it))
                }
            )
            if (isRunning) {
                if (status.total > 0) {
                    LinearProgressIndicator(
                        progress = { (status.processed.toFloat() / status.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().testTag("offline-cache-progress")
                    )
                    Text("${status.processed} of ${status.total} books checked")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Refreshing selected library catalogs…")
                }
            } else if (status.state != OfflineCacheRunState.IDLE) {
                Text(
                    status.message ?: "${status.processed} books checked",
                    color = if (status.state == OfflineCacheRunState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (status.unavailable > 0 || status.failed > 0) {
                    Text(
                        "Unavailable ${status.unavailable} · Failed ${status.failed}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning && selectedIds.isNotEmpty() && hasContentType,
                    modifier = Modifier.testTag("offline-cache-update")
                ) { Text("Download/update now") }
                if (isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("offline-cache-cancel")
                    ) { Text("Cancel") }
                }
                TextButton(
                    onClick = onClear,
                    enabled = !isRunning,
                    modifier = Modifier.testTag("offline-cache-clear")
                ) { Text("Clear offline cache") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryReaderConfiguration(
    libraries: List<LibrarySummary>,
    selectedLibraryId: String?,
    preferences: LibraryReaderPreferences?,
    onLibrarySelected: (String) -> Unit,
    onPreferencesChange: (String, LibraryReaderPreferences) -> Unit
) {
    var libraryMenuExpanded by remember { mutableStateOf(false) }
    val selectedLibrary = libraries.firstOrNull { it.id == selectedLibraryId }
    val value = preferences ?: LibraryReaderPreferences()
    Column(
        modifier = Modifier.padding(start = 4.dp, top = 26.dp, end = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OrbitEyebrow("Reading configuration")
        Text("Library reader profile", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Direction, typography, margins, and format layouts are saved independently for each library.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { libraryMenuExpanded = true },
                enabled = libraries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("options-reading-library")
            ) {
                Text(
                    selectedLibrary?.name ?: "No libraries available",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = libraryMenuExpanded,
                onDismissRequest = { libraryMenuExpanded = false }
            ) {
                libraries.forEach { library ->
                    DropdownMenuItem(
                        text = { Text(library.name) },
                        onClick = {
                            libraryMenuExpanded = false
                            onLibrarySelected(library.id)
                        },
                        modifier = Modifier.testTag("options-reading-library-${library.id}")
                    )
                }
            }
        }
        if (selectedLibrary != null) {
            Text("Reading direction", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryReadingDirection.values().forEach { direction ->
                    FilterChip(
                        selected = value.readingDirection == direction,
                        onClick = {
                            onPreferencesChange(
                                selectedLibrary.id,
                                value.copy(readingDirection = direction)
                            )
                        },
                        label = { Text(direction.displayName) },
                        modifier = Modifier.testTag(
                            "options-reading-direction-${direction.name.lowercase()}"
                        )
                    )
                }
            }
            ReaderTapZoneSettings(
                value = value,
                onPreferencesChange = { next ->
                    onPreferencesChange(selectedLibrary.id, next)
                },
                testTagPrefix = "options-reading-tap-zone"
            )
            Text("Typography", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EPUB_THEME_OPTIONS.forEach { theme ->
                    FilterChip(
                        selected = value.theme == theme,
                        onClick = {
                            onPreferencesChange(selectedLibrary.id, value.copy(theme = theme))
                        },
                        label = { Text(theme.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onPreferencesChange(
                            selectedLibrary.id,
                            value.copy(fontScale = value.fontScale - 0.1f)
                        )
                    }
                ) { Text("A-") }
                Text(
                    "Text size ${formatEpubFontScale(value.fontScale)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = {
                        onPreferencesChange(
                            selectedLibrary.id,
                            value.copy(fontScale = value.fontScale + 0.1f)
                        )
                    }
                ) { Text("A+") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReaderLayoutModeSettings(
                formatLabel = "EPUB",
                layoutMode = value.epubLayoutMode,
                testTagPrefix = "options-reading-epub",
                onLayoutModeChange = { layoutMode ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(epubLayoutMode = layoutMode)
                    )
                }
            )
            Text("Page margins", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Top" to value.padding.top,
                "Bottom" to value.padding.bottom,
                "Left" to value.padding.left,
                "Right" to value.padding.right
            ).forEach { (label, margin) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("$label ${margin.toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = margin,
                        onValueChange = { next ->
                            val padding = when (label) {
                                "Top" -> value.padding.copy(top = next)
                                "Bottom" -> value.padding.copy(bottom = next)
                                "Left" -> value.padding.copy(left = next)
                                else -> value.padding.copy(right = next)
                            }
                            onPreferencesChange(selectedLibrary.id, value.copy(padding = padding))
                        },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.testTag(
                            "options-reading-margin-${label.lowercase()}"
                        )
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReaderFormatLayoutSettings(
                formatLabel = "PDF",
                layoutMode = value.pdfLayoutMode,
                pageGapDp = value.pdfPageGapDp,
                testTagPrefix = "options-reading-pdf",
                onLayoutModeChange = { layoutMode ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(pdfLayoutMode = layoutMode)
                    )
                },
                onPageGapChange = { pageGapDp ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(pdfPageGapDp = pageGapDp)
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReaderFormatLayoutSettings(
                formatLabel = "CBR/CBZ",
                layoutMode = value.comicLayoutMode,
                pageGapDp = value.comicPageGapDp,
                testTagPrefix = "options-reading-comic",
                onLayoutModeChange = { layoutMode ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(comicLayoutMode = layoutMode)
                    )
                },
                onPageGapChange = { pageGapDp ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(comicPageGapDp = pageGapDp)
                    )
                }
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ReaderFormatLayoutSettings(
    formatLabel: String,
    layoutMode: ReaderLayoutMode,
    pageGapDp: Float,
    testTagPrefix: String,
    onLayoutModeChange: (ReaderLayoutMode) -> Unit,
    onPageGapChange: (Float) -> Unit
) {
    ReaderLayoutModeSettings(
        formatLabel = formatLabel,
        layoutMode = layoutMode,
        testTagPrefix = testTagPrefix,
        onLayoutModeChange = onLayoutModeChange
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Continuous page gap ${pageGapDp.toInt()} dp",
            style = MaterialTheme.typography.bodySmall,
            color = if (layoutMode == ReaderLayoutMode.CONTINUOUS) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Slider(
            value = pageGapDp,
            onValueChange = onPageGapChange,
            enabled = layoutMode == ReaderLayoutMode.CONTINUOUS,
            valueRange = 0f..MAX_READER_PAGE_GAP_DP,
            steps = 11,
            modifier = Modifier.testTag("$testTagPrefix-page-gap")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderLayoutModeSettings(
    formatLabel: String,
    layoutMode: ReaderLayoutMode,
    testTagPrefix: String,
    onLayoutModeChange: (ReaderLayoutMode) -> Unit
) {
    Text("$formatLabel layout", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderLayoutMode.values().forEach { mode ->
            FilterChip(
                selected = layoutMode == mode,
                onClick = { onLayoutModeChange(mode) },
                label = { Text(mode.displayName) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-layout-${mode.name.lowercase()}"
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderTapZoneSettings(
    value: LibraryReaderPreferences,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit,
    testTagPrefix: String
) {
    Text("Tap zones", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderTapZoneLayout.values().forEach { layout ->
            FilterChip(
                selected = value.tapZoneLayout == layout,
                onClick = { onPreferencesChange(value.copy(tapZoneLayout = layout)) },
                label = { Text(layout.displayName) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-layout-${layout.name.lowercase()}"
                )
            )
        }
    }
    Text("Tap-zone inversion", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderTapZoneInvertMode.values().forEach { invertMode ->
            FilterChip(
                selected = value.tapZoneInvertMode == invertMode,
                onClick = { onPreferencesChange(value.copy(tapZoneInvertMode = invertMode)) },
                label = { Text(invertMode.displayName) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-invert-${invertMode.name.lowercase()}"
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderConfigurationControls(
    value: LibraryReaderPreferences,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit,
    testTagPrefix: String = "reader-options-reading",
    isEpub: Boolean = true,
    onCustomFontRequest: () -> Unit = {},
    onCustomFontRemove: () -> Unit = {}
) {
    AppPreferenceSwitchRow(
        title = "Turn pages with volume buttons",
        summary = "Use Volume Up and Volume Down for reader navigation instead of changing volume.",
        checked = value.volumeButtonPageNavigation,
        testTag = "$testTagPrefix-volume-button-navigation",
        onCheckedChange = { enabled ->
            onPreferencesChange(value.copy(volumeButtonPageNavigation = enabled))
        }
    )
    AppPreferenceSwitchRow(
        title = "Swap volume button actions",
        summary = "Use Volume Down for previous and Volume Up for next",
        checked = value.reverseVolumeButtonNavigation,
        enabled = value.volumeButtonPageNavigation,
        testTag = "$testTagPrefix-reverse-volume-button-navigation",
        onCheckedChange = { reversed ->
            onPreferencesChange(value.copy(reverseVolumeButtonNavigation = reversed))
        }
    )
    Text("Reading direction", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryReadingDirection.values().forEach { direction ->
            FilterChip(
                selected = value.readingDirection == direction,
                onClick = { onPreferencesChange(value.copy(readingDirection = direction)) },
                label = { Text(direction.displayName) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-direction-${direction.name.lowercase()}"
                )
            )
        }
    }
    ReaderTapZoneSettings(
        value = value,
        onPreferencesChange = onPreferencesChange,
        testTagPrefix = "$testTagPrefix-tap-zone"
    )
    Text("Typography", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EPUB_THEME_OPTIONS.forEach { theme ->
            FilterChip(
                selected = value.theme == theme,
                onClick = { onPreferencesChange(value.copy(theme = theme)) },
                label = { Text(theme.label) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-theme-${theme.name.lowercase()}"
                )
            )
        }
    }
    if (isEpub) {
        var fontMenuExpanded by remember { mutableStateOf(false) }
        val selectedFontLabel = value.customFont
            ?.takeIf { value.fontFamily == EpubReaderFontFamily.CUSTOM }
            ?.displayName
            ?: value.fontFamily.displayName
        Text("Fonts", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(
                onClick = { fontMenuExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("$testTagPrefix-font-family-dropdown")
            ) {
                Text(selectedFontLabel, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(
                expanded = fontMenuExpanded,
                onDismissRequest = { fontMenuExpanded = false }
            ) {
                Text(
                    "Normal fonts",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EPUB_NORMAL_FONT_FAMILY_OPTIONS.forEach { fontFamily ->
                    DropdownMenuItem(
                        text = { Text(fontFamily.displayName) },
                        onClick = {
                            fontMenuExpanded = false
                            onPreferencesChange(value.copy(fontFamily = fontFamily))
                        },
                        modifier = Modifier.testTag(
                            "$testTagPrefix-font-family-${fontFamily.name.lowercase()}"
                        )
                    )
                }
                Text(
                    "Accessibility fonts",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EPUB_ACCESSIBILITY_FONT_FAMILY_OPTIONS.forEach { fontFamily ->
                    DropdownMenuItem(
                        text = { Text(fontFamily.displayName) },
                        onClick = {
                            fontMenuExpanded = false
                            onPreferencesChange(value.copy(fontFamily = fontFamily))
                        },
                        modifier = Modifier.testTag(
                            "$testTagPrefix-font-family-${fontFamily.name.lowercase()}"
                        )
                    )
                }
                Text(
                    "Custom font",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenuItem(
                    text = { Text("Choose custom font…") },
                    onClick = {
                        fontMenuExpanded = false
                        onCustomFontRequest()
                    },
                    modifier = Modifier.testTag("$testTagPrefix-font-family-custom-choose")
                )
                if (value.customFont != null) {
                    DropdownMenuItem(
                        text = { Text("Remove ${value.customFont.displayName}") },
                        onClick = {
                            fontMenuExpanded = false
                            onCustomFontRemove()
                        },
                        modifier = Modifier.testTag("$testTagPrefix-font-family-custom-remove")
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Line spacing ${formatEpubLineSpacing(value.lineSpacing)}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = value.lineSpacing,
                onValueChange = { onPreferencesChange(value.copy(lineSpacing = it)) },
                valueRange = DEFAULT_EPUB_LINE_SPACING..MAX_EPUB_LINE_SPACING,
                steps = 9,
                modifier = Modifier.testTag("$testTagPrefix-line-spacing")
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Word spacing ${formatEpubWordSpacing(value.wordSpacing)}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = value.wordSpacing,
                onValueChange = { onPreferencesChange(value.copy(wordSpacing = it)) },
                valueRange = DEFAULT_EPUB_WORD_SPACING..MAX_EPUB_WORD_SPACING,
                steps = 9,
                modifier = Modifier.testTag("$testTagPrefix-word-spacing")
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onPreferencesChange(value.copy(fontScale = value.fontScale - 0.1f)) },
            modifier = Modifier.testTag("$testTagPrefix-font-decrease")
        ) { Text("A-") }
        Text(
            "Text size ${formatEpubFontScale(value.fontScale)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = { onPreferencesChange(value.copy(fontScale = value.fontScale + 0.1f)) },
            modifier = Modifier.testTag("$testTagPrefix-font-increase")
        ) { Text("A+") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ReaderLayoutModeSettings(
        formatLabel = "EPUB",
        layoutMode = value.epubLayoutMode,
        testTagPrefix = "$testTagPrefix-epub",
        onLayoutModeChange = { onPreferencesChange(value.copy(epubLayoutMode = it)) }
    )
    Text("Page margins", style = MaterialTheme.typography.titleMedium)
    listOf(
        "Top" to value.padding.top,
        "Bottom" to value.padding.bottom,
        "Left" to value.padding.left,
        "Right" to value.padding.right
    ).forEach { (label, margin) ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$label ${margin.toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = margin,
                onValueChange = { next ->
                    val padding = when (label) {
                        "Top" -> value.padding.copy(top = next)
                        "Bottom" -> value.padding.copy(bottom = next)
                        "Left" -> value.padding.copy(left = next)
                        else -> value.padding.copy(right = next)
                    }
                    onPreferencesChange(value.copy(padding = padding))
                },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.testTag("$testTagPrefix-margin-${label.lowercase()}")
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ReaderFormatLayoutSettings(
        formatLabel = "PDF",
        layoutMode = value.pdfLayoutMode,
        pageGapDp = value.pdfPageGapDp,
        testTagPrefix = "$testTagPrefix-pdf",
        onLayoutModeChange = { onPreferencesChange(value.copy(pdfLayoutMode = it)) },
        onPageGapChange = { onPreferencesChange(value.copy(pdfPageGapDp = it)) }
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    ReaderFormatLayoutSettings(
        formatLabel = "CBR/CBZ",
        layoutMode = value.comicLayoutMode,
        pageGapDp = value.comicPageGapDp,
        testTagPrefix = "$testTagPrefix-comic",
        onLayoutModeChange = { onPreferencesChange(value.copy(comicLayoutMode = it)) },
        onPageGapChange = { onPreferencesChange(value.copy(comicPageGapDp = it)) }
    )
}

@Composable
private fun AppPreferenceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    testTag: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
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
    modifier: Modifier,
    releaseCheckStatus: ReleaseCheckStatus,
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrbitEyebrow("About")
                Text("Lagrange Reader", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "An independent Android app for reading and listening to books hosted on BookOrbit.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Lagrange is a standalone native Android client, not a wrapper around the BookOrbit web interface. BookOrbit supplies the authenticated server and library data; Lagrange provides the Android interface, local state, reading, listening, and offline behavior."
                )
            }
        }
        item {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Build information", style = MaterialTheme.typography.titleMedium)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("Connected server", style = MaterialTheme.typography.labelMedium)
                Text(state.serverUrl, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = releaseCheckStatus != ReleaseCheckStatus.CHECKING,
                    modifier = Modifier.testTag("check-for-updates")
                ) {
                    if (releaseCheckStatus == ReleaseCheckStatus.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Check for updates")
                }
                val result = when (releaseCheckStatus) {
                    ReleaseCheckStatus.UP_TO_DATE -> "You are up to date."
                    ReleaseCheckStatus.UPDATE_AVAILABLE -> "An update is available."
                    ReleaseCheckStatus.ERROR -> "Unable to check for updates."
                    else -> null
                }
                result?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Not an official BookOrbit application", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lagrange Reader is an independent community project. It is not affiliated with, endorsed by, or sponsored by the BookOrbit maintainers. BookOrbit remains the source of the server and library experience used by this app."
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Acknowledgements", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Thanks to the BookOrbit maintainers and contributors, the Readium Foundation, the Android and Kotlin open-source communities, and everyone who tests Lagrange Reader and reports issues."
                )
                Text(
                    "The app uses BookOrbit, Readium, AndroidX, Jetpack Compose, Kotlin, Media3, OkHttp, Room, and other open-source libraries. Their respective licenses and notices remain authoritative."
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Project links", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { openLink("https://github.com/van-geaux/lagrange-reader") }) {
                    Text("Lagrange Reader on GitHub")
                }
                TextButton(onClick = { openLink("https://github.com/BookOrbit") }) {
                    Text("BookOrbit on GitHub")
                }
                TextButton(onClick = { openLink("https://readium.org/") }) {
                    Text("Readium Foundation")
                }
                TextButton(onClick = { openLink("https://github.com/van-geaux/lagrange-reader/blob/main/LICENSE") }) {
                    Text("License and notices")
                }
            }
        }
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
    localBooksLoader: suspend () -> List<BookSummary>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onLocalBooksSelected: () -> Unit
) {
    val downloadedLocalBooks by produceState<List<BookSummary>?>(
        initialValue = null,
        state.localBooksRevision
    ) {
        value = runCatching { localBooksLoader() }.getOrNull()
    }
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
            downloadedLocalBooks = downloadedLocalBooks,
            modifier = Modifier.fillMaxSize(),
            coverLoader = coverLoader,
            onBookSelected = onBookSelected,
            onSeriesSelected = onSeriesSelected,
            onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onClearFailedDownload = onClearFailedDownload,
            onDeleteLocalCopy = onDeleteLocalCopy,
            onMarkAsRead = onMarkAsRead,
            onMarkAsUnread = onMarkAsUnread,
            onLocalBooksSelected = onLocalBooksSelected
        )
    }
}

@Composable
private fun HomeFeed(
    state: BrowserState,
    books: List<BookSummary> = state.books,
    downloadedLocalBooks: List<BookSummary>? = null,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onLocalBooksSelected: (() -> Unit)? = null,
    localBooksLibraryId: String? = null,
    showHeader: Boolean = false
) {
    val currentlyReading = remember(books) { currentlyReadingBooks(books) }
    val onDeck = remember(books) { onDeckBooks(books) }
    val wantToRead = remember(books) { wantToReadBooks(books) }
    val recentlyAddedBooks = books.sortedWith(
        compareByDescending<BookSummary> { it.addedAtMillis != null }
            .thenByDescending { it.addedAtMillis ?: 0L }
    ).take(12)
    val recentSeries = remember(books) { recentSeries(books, useUpdatedAt = false) }
    val updatedSeries = remember(books) { recentSeries(books, useUpdatedAt = true) }
    val recentlyRead = remember(books) { recentlyReadBooks(books) }
    val localBooks = remember(books, downloadedLocalBooks, localBooksLibraryId) {
        homeLocalBooksPreview(
            catalogHomeBooks = books,
            downloadedBooks = downloadedLocalBooks,
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
        if (currentlyReading.isNotEmpty()) {
            item {
                BookShelf(
                    title = "Currently reading",
                    books = currentlyReading,
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    state = state,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading.takeUnless {
                        state.isOfflineSnapshot
                    },
                    onMarkAsRead = availableMarkAsRead,
                    onMarkAsUnread = availableMarkAsUnread
                )
            }
        }
        if (onDeck.isNotEmpty()) item {
            BookShelf("On deck", onDeck, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (wantToRead.isNotEmpty()) item {
            BookShelf("Want to read", wantToRead, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (recentlyAddedBooks.isNotEmpty()) item {
            BookShelf("Recently added books", recentlyAddedBooks, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (recentSeries.isNotEmpty()) item { SeriesShelf("Recently added series", recentSeries, coverLoader, onSeriesSelected) }
        if (updatedSeries.isNotEmpty()) item { SeriesShelf("Recently updated series", updatedSeries, coverLoader, onSeriesSelected) }
        if (recentlyRead.isNotEmpty()) item {
            BookShelf("Recently read books", recentlyRead, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread)
        }
        if (localBooks.isNotEmpty()) item {
            BookShelf(
                title = "Local books",
                books = localBooks,
                coverLoader = coverLoader,
                onBookSelected = onBookSelected,
                state = state,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onClearFailedDownload = onClearFailedDownload,
                onDeleteLocalCopy = onDeleteLocalCopy,
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
    state: BrowserState? = null,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onRemoveFromCurrentlyReading: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: ((BookSummary) -> Unit)? = null,
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
                    downloadState = state,
                    onDownload = onDownload?.let { download -> { download(book) } },
                    onCancelDownload = onCancelDownload?.let { cancel -> { cancel(book) } },
                    onClearFailedDownload = onClearFailedDownload?.let { clear -> { clear(book) } },
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading?.let { remove ->
                        { remove(book) }
                    },
                    onDeleteLocalCopy = onDeleteLocalCopy?.let { delete -> { delete(book) } },
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
    downloadState: BrowserState? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onClearFailedDownload: (() -> Unit)? = null,
    onRemoveFromCurrentlyReading: (() -> Unit)? = null,
    onMarkAsRead: (() -> Unit)? = null,
    onMarkAsUnread: (() -> Unit)? = null,
    onDeleteLocalCopy: (() -> Unit)? = null,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    val isBookCard = displayTitle == book.title
    val fileId = book.fileId
    val isDownloading = fileId != null && fileId in (downloadState?.downloadingFileIds.orEmpty())
    val failed = fileId != null && fileId in (downloadState?.failedDownloadFileIds.orEmpty())
    var showActions by remember(book.id) { mutableStateOf(false) }
    val hasActions = onRemoveFromCurrentlyReading != null || onMarkAsRead != null || onMarkAsUnread != null || (book.isDownloaded && onDeleteLocalCopy != null) || (onDownload != null && !book.isServerMissing) || isDownloading || failed
    Column(
        modifier = Modifier
            .width(LocalLibraryCardSize.current.shelfWidth)
            .testTag("book_card_${book.id}")
            .then(modifier)
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
                        if (isDownloading) onCancelDownload?.let { cancel -> DropdownMenuItem(text = { Text("Cancel") }, onClick = { showActions = false; cancel() }) }
                        else if (failed) {
                            if (!book.isServerMissing) onDownload?.let { retry -> DropdownMenuItem(text = { Text("Retry") }, onClick = { showActions = false; retry() }) }
                            onClearFailedDownload?.let { clear -> DropdownMenuItem(text = { Text("Clear") }, onClick = { showActions = false; clear() }) }
                        } else if (!book.isDownloaded && !book.isServerMissing && downloadState?.isOfflineSnapshot != true) onDownload?.let { download -> DropdownMenuItem(text = { Text("Download local") }, onClick = { showActions = false; download() }) }
                        if (book.isDownloaded) onDeleteLocalCopy?.let { deleteLocal -> DropdownMenuItem(text = { Text("Delete local") }, onClick = { showActions = false; deleteLocal() }) }
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
        if (supportingText != null) {
            Text(
                supportingText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else if (isBookCard) {
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
        if (book.isServerMissing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCCFFC107))
                    .semantics {
                        contentDescription = "Missing! ${book.title} is missing on the server"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Missing!",
                    color = Color(0xFF3E2723),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
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
    onMarkAsUnread: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit = {}
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
        onMarkAsUnread = onMarkAsUnread,
        onClearFailedDownload = onClearFailedDownload
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun LibraryContentScreen(
    state: BrowserState,
    tab: LibraryTab,
    onTabChange: (LibraryTab) -> Unit,
    modifier: Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    localBooksLoader: suspend () -> List<BookSummary>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onDownload: ((BookSummary) -> Unit)? = null,
    onBulkDownload: ((String, List<BookSummary>) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDeleteLocalCopies: (List<BookSummary>) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onLocalBooksSelected: () -> Unit
) {
    val downloadedLocalBooks by produceState<List<BookSummary>?>(
        initialValue = null,
        state.localBooksRevision,
        state.selectedLibraryId
    ) {
        value = runCatching { localBooksLoader() }.getOrNull()
    }
    val libraryDownloadCandidates = remember(state.books, state.selectedLibraryId) {
        booksDownloadableForLibrary(state.books, state.selectedLibraryId)
    }
    var showLibraryFileSelection by remember(state.selectedLibraryId) { mutableStateOf(false) }
    var librarySelectedFileIds by remember(state.selectedLibraryId) { mutableStateOf(emptySet<String>()) }
    var libraryFrozenSelection by remember(state.selectedLibraryId) { mutableStateOf<List<BookSummary>>(emptyList()) }
    var libraryDownloadConfirmStep by remember(state.selectedLibraryId) { mutableStateOf(0) }
    var pendingLibraryLocalDeletes by remember(state.selectedLibraryId) {
        mutableStateOf<List<BookSummary>?>(null)
    }
    val libraryLocalCopies = localCopiesForBulkAction(
        books = libraryDownloadCandidates,
        localFilePathOverrides = state.localFilePathOverrides
    )
    val libraryActiveFileIds = libraryDownloadCandidates
        .mapNotNull { it.fileId }
        .filterTo(linkedSetOf()) { it in state.downloadingFileIds }
    val libraryFrozenFileIds = libraryFrozenSelection.mapNotNull { it.fileId }
    val libraryProgressFileIds = if (libraryFrozenFileIds.any { it in libraryActiveFileIds }) {
        libraryFrozenFileIds
    } else {
        libraryActiveFileIds.toList()
    }
    val libraryDownloadProgress = collectionDownloadProgress(
        fileIds = libraryProgressFileIds,
        downloadingFileIds = state.downloadingFileIds,
        progressByFileId = state.downloadProgressByFileId,
        completedFileIds = libraryLocalCopies.mapNotNullTo(mutableSetOf()) { it.fileId }
    )
    if (showLibraryFileSelection) {
        SeriesFileSelectionDialog(
            title = "Select library downloads",
            candidates = libraryDownloadCandidates,
            libraries = state.libraries,
            groupingMode = SeriesGroupingMode.NONE,
            onGroupingModeChange = {},
            selectedFileIds = librarySelectedFileIds,
            onSelectedFileIdsChange = { librarySelectedFileIds = it },
            onDismissRequest = { showLibraryFileSelection = false },
            onConfirm = {
                libraryFrozenSelection = selectedSeriesFiles(libraryDownloadCandidates, librarySelectedFileIds)
                showLibraryFileSelection = false
                if (libraryFrozenSelection.isNotEmpty()) libraryDownloadConfirmStep = 1
            }
        )
    }
    if (libraryDownloadConfirmStep == 1) {
        AlertDialog(
            onDismissRequest = { libraryDownloadConfirmStep = 0 },
            title = { Text("Download this library?") },
            text = {
                Text(
                    "This will download ${libraryFrozenSelection.size} " +
                        "${if (libraryFrozenSelection.size == 1) "file" else "files"} to this device. " +
                        "This may use a significant amount of storage and data."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { libraryDownloadConfirmStep = 2 },
                    modifier = Modifier.testTag("confirm-download-library-step1")
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { libraryDownloadConfirmStep = 0 }) { Text("Cancel") }
            }
        )
    } else if (libraryDownloadConfirmStep == 2) {
        AlertDialog(
            onDismissRequest = { libraryDownloadConfirmStep = 0 },
            title = { Text("Are you sure?") },
            text = {
                Text(
                    "Downloading the entire library may use a significant amount of storage " +
                        "and mobile data, and cannot be paused as one operation."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryDownloadConfirmStep = 0
                        onBulkDownload?.invoke("the selected library", libraryFrozenSelection)
                    },
                    modifier = Modifier.testTag("confirm-download-library-step2")
                ) { Text("Download library") }
            },
            dismissButton = {
                TextButton(onClick = { libraryDownloadConfirmStep = 0 }) { Text("Cancel") }
            }
        )
    }
    pendingLibraryLocalDeletes?.let { books ->
        AlertDialog(
            onDismissRequest = { pendingLibraryLocalDeletes = null },
            title = { Text("Delete ${books.size} local ${if (books.size == 1) "copy" else "copies"}?") },
            text = {
                Text(
                    "The selected files will be removed from this device. " +
                        "Your BookOrbit books are not deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLibraryLocalDeletes = null
                        onDeleteLocalCopies(books)
                    },
                    modifier = Modifier.testTag("confirm-delete-library-local-copies")
                ) { Text("Delete local") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLibraryLocalDeletes = null }) { Text("Cancel") }
            }
        )
    }
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
                    downloadedLocalBooks = downloadedLocalBooks,
                    modifier = Modifier.weight(1f),
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    onSeriesSelected = onSeriesSelected,
                    onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
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
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    headerContent = {
                        val showDownload = onBulkDownload != null &&
                            state.isCatalogComplete &&
                            !state.isCatalogSyncing &&
                            hasSelectableBulkDownloads(libraryDownloadCandidates)
                        if (showDownload || libraryLocalCopies.isNotEmpty() || libraryDownloadProgress != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (showDownload) {
                                        OutlinedButton(
                                            onClick = {
                                                librarySelectedFileIds =
                                                    defaultSeriesFileSelection(libraryDownloadCandidates)
                                                showLibraryFileSelection = true
                                            },
                                            enabled = libraryActiveFileIds.isEmpty(),
                                            modifier = Modifier.testTag("download-library")
                                        ) { Text("Download library") }
                                    }
                                    if (libraryLocalCopies.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { pendingLibraryLocalDeletes = libraryLocalCopies },
                                            enabled = libraryActiveFileIds.isEmpty(),
                                            modifier = Modifier.testTag("delete-library-local-copies")
                                        ) { Text("Delete local books") }
                                    }
                                }
                                libraryDownloadProgress?.let { progress ->
                                    CollectionDownloadProgressIndicator(
                                        state = progress,
                                        testTag = "library-download-progress"
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val pullState = rememberPullToRefreshState(enabled = { !isRefreshing })
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullState.startRefresh()
        } else {
            pullState.endRefresh()
        }
    }
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing && !isRefreshing) onRefresh()
    }
    Box(modifier = modifier.nestedScroll(pullState.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(
            state = pullState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("pull_to_refresh_indicator")
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
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    headerContent: (@Composable () -> Unit)? = null
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
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onClearFailedDownload = onClearFailedDownload,
        onDeleteLocalCopy = onDeleteLocalCopy,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread,
        totalBooks = total,
        totalSeries = seriesTotal,
        filter = filter,
        jumpRailEnabled = state.isCatalogComplete,
        serverJumpBuckets = state.libraryJumpBuckets.takeIf { filter == BookBrowseFilter() }.orEmpty(),
        onFilterClick = { showFilter = true },
        headerContent = headerContent
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
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
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
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onClearFailedDownload = onClearFailedDownload,
        onDeleteLocalCopy = onDeleteLocalCopy,
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
    onMarkAsUnread: ((BookSummary) -> Unit)? = null,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: ((BookSummary) -> Unit)? = null,
    confirmDeleteLocalCopy: Boolean = true,
    onDeleteLocalCopies: ((List<BookSummary>) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    val title = titleOverride
        ?: state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name
        ?: "Library"
    var seriesCollapsed by rememberSaveable(title) { mutableStateOf(false) }
    var selectedBookIds by remember(title) { mutableStateOf<Set<String>>(emptySet()) }
    var pendingLocalDeletes by remember(title) { mutableStateOf<List<BookSummary>?>(null) }
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
            onDeleteLocalCopies = onDeleteLocalCopies?.let { deleteCopies ->
                { books ->
                    if (confirmDeleteLocalCopy) {
                        pendingLocalDeletes = books
                    } else {
                        deleteCopies(books)
                        selectedBookIds = emptySet()
                    }
                }
            },
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
        headerContent?.invoke()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = LocalLibraryCardSize.current.gridMinSize),
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
                downloadState = state,
                onDownload = onDownload?.let { download -> { download(book) } },
                onCancelDownload = onCancelDownload?.let { cancel -> { cancel(book) } },
                onClearFailedDownload = onClearFailedDownload?.let { clear -> { clear(book) } },
                onDeleteLocalCopy = onDeleteLocalCopy?.let { delete -> { delete(book) } },
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
    pendingLocalDeletes?.let { books ->
        AlertDialog(
            onDismissRequest = { pendingLocalDeletes = null },
            title = {
                Text(
                    if (books.size == 1) "Delete local copy?"
                    else "Delete ${books.size} local copies?"
                )
            },
            text = {
                Text(
                    if (books.size == 1) {
                        "The selected file will be removed from this device. " +
                            "Your BookOrbit book is not deleted."
                    } else {
                        "The selected files will be removed from this device. " +
                            "Your BookOrbit books are not deleted."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLocalDeletes = null
                        onDeleteLocalCopies?.invoke(books)
                        selectedBookIds = emptySet()
                    },
                    modifier = Modifier.testTag("confirm-delete-local-copies")
                ) { Text("Delete local") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalDeletes = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LibraryBooksToolbar(
    selectedBooks: List<BookSummary>,
    onMarkAsRead: ((BookSummary) -> Unit)?,
    onMarkAsUnread: ((BookSummary) -> Unit)?,
    onDeleteLocalCopies: ((List<BookSummary>) -> Unit)?,
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
                    if (onDeleteLocalCopies != null) {
                        TextButton(onClick = { onDeleteLocalCopies(selectedBooks) }) {
                            Text("Delete local")
                        }
                    }
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

internal data class DownloadTransferRow(
    val book: BookSummary,
    val fileId: String,
    val isActive: Boolean,
    val isFailed: Boolean,
    val progress: Float?
)

internal fun downloadTransferRows(state: BrowserState): List<DownloadTransferRow> {
    val booksByFileId = (state.books + state.homeBooks)
        .filter { it.fileId != null }
        .associateBy { it.fileId!! }
    val fileIds = (
        state.downloadingFileIds +
            state.failedDownloadFileIds +
            state.downloadMetadataByFileId.keys
        ).toList().sorted()
    return fileIds.map { fileId ->
        val metadata = state.downloadMetadataByFileId[fileId]
        val book = state.downloadBooksByFileId[fileId] ?: metadata?.let { restored ->
            BookSummary(
                id = restored.bookId,
                libraryId = "",
                fileId = fileId,
                title = restored.title,
                mediaKind = restored.mediaKind,
                format = restored.mimeType
            )
        } ?: booksByFileId[fileId] ?: BookSummary(
            id = "download-$fileId",
            libraryId = "",
            fileId = fileId,
            title = "File $fileId"
        )
        DownloadTransferRow(
            book = book,
            fileId = fileId,
            isActive = fileId in state.downloadingFileIds,
            isFailed = fileId in state.failedDownloadFileIds,
            progress = state.downloadProgressByFileId[fileId]
        )
    }
}

@Composable
private fun DownloadTransfersSection(
    rows: List<DownloadTransferRow>,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onClearAllFailedDownloads: () -> Unit
) {
    val failedRows = rows.filter { it.isFailed && !it.isActive }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("local-downloads-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Downloads", style = MaterialTheme.typography.titleLarge)
            if (failedRows.isNotEmpty()) {
                TextButton(onClick = onClearAllFailedDownloads) { Text("Clear all") }
            }
        }
        rows.filter { it.isActive }.forEach { row ->
            DownloadTransferRowItem(
                row = row,
                onAction = { onCancelDownload(row.book) },
                actionLabel = "Cancel"
            )
        }
        failedRows.forEach { row ->
            DownloadTransferRowItem(
                row = row,
                onAction = { onDownload(row.book) },
                actionLabel = "Retry",
                onClear = { onClearFailedDownload(row.book) }
            )
        }
    }
}

@Composable
private fun DownloadTransferRowItem(
    row: DownloadTransferRow,
    onAction: () -> Unit,
    actionLabel: String,
    onClear: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("download-row-${row.fileId}")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.book.title, style = MaterialTheme.typography.titleMedium)
            if (row.isActive) {
                row.progress?.let { progress ->
                    Text("Downloading · ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } ?: run {
                    Text("Downloading…", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                Text("Download failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
                onClear?.let { clear ->
                    TextButton(onClick = clear) { Text("Clear") }
                }
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
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onClearAllFailedDownloads: () -> Unit,
    confirmDeleteLocalCopy: Boolean,
    onDeleteLocalCopies: (List<BookSummary>) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val books by produceState<List<BookSummary>?>(initialValue = null, state.localBooksRevision, reloadKey) {
        try {
            value = loader()
        } finally {
            isRefreshing = false
        }
    }
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            reloadKey += 1
        }
    }
    var filter by remember { mutableStateOf(BookBrowseFilter()) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val scopedBooks = remember(books, libraryId) {
        books.orEmpty().filter { libraryId == null || it.libraryId == libraryId }
    }
    val filteredBooks = remember(scopedBooks, filter) {
        filterAndSortLocalBooks(scopedBooks, filter)
    }
    val transferRows = remember(
        state.downloadingFileIds,
        state.failedDownloadFileIds,
        state.downloadProgressByFileId,
        state.downloadBooksByFileId,
        state.downloadMetadataByFileId,
        state.books,
        state.homeBooks
    ) {
        downloadTransferRows(state)
    }
    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("local_books_pull_to_refresh")
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (transferRows.isNotEmpty()) {
            DownloadTransfersSection(
                rows = transferRows,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onClearFailedDownload = onClearFailedDownload,
                onClearAllFailedDownloads = onClearAllFailedDownloads
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            LibraryBooks(
                state = state.copy(
                    books = filteredBooks,
                    isLoadingBooks = books == null,
                    isOfflineSnapshot = false
                ),
                modifier = Modifier,
                coverLoader = coverLoader,
                onBookSelected = onBookSelected,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onClearFailedDownload = onClearFailedDownload,
                titleOverride = state.libraries.firstOrNull { it.id == libraryId }
                    ?.let { "Local books · ${it.name}" }
                    ?: "Local books",
                emptyMessage = "No local books found.",
                allowSeriesCollapse = false,
                totalBooks = filteredBooks.size,
                filter = filter,
                onMarkAsRead = onMarkAsRead,
                onMarkAsUnread = onMarkAsUnread,
                onDeleteLocalCopy = onDeleteLocalCopy,
                confirmDeleteLocalCopy = confirmDeleteLocalCopy,
                onDeleteLocalCopies = onDeleteLocalCopies,
                onFilterClick = { showFilter = true }
            )
        }
    }
    }
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
    onMarkAsUnread: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall) }
        if (isLoading) item { LoadingFeedRow("Loading books...") }
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
                onClearFailedDownload = onClearFailedDownload,
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
    onClearFailedDownload: (BookSummary) -> Unit,
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
                        fileId != null && !book.isServerMissing && !state.isOfflineSnapshot -> OutlinedButton(onClick = { onDownload(book) }) {
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
                        when {
                            isDownloading -> DropdownMenuItem(text = { Text("Cancel") }, onClick = { showActions = false; onCancelDownload(book) })
                            failed -> {
                                DropdownMenuItem(
                                    text = { Text(if (book.isDownloaded) "Update local" else "Retry") },
                                    onClick = { showActions = false; onDownload(book) }
                                )
                                DropdownMenuItem(text = { Text("Clear") }, onClick = { showActions = false; onClearFailedDownload(book) })
                            }
                            book.isDownloaded -> DropdownMenuItem(text = { Text("Delete local") }, onClick = { showActions = false; onDeleteLocalCopy(book) })
                            fileId != null && !book.isServerMissing && !state.isOfflineSnapshot -> DropdownMenuItem(text = { Text("Download local") }, onClick = { showActions = false; onDownload(book) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BookDetails(
    book: BookSummary,
    openSessionHistory: Boolean,
    state: BrowserState,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    detailLoader: suspend (BookSummary) -> BookDetailInfo?,
    sessionHistoryLoader: suspend (BookSummary) -> List<AudiobookSessionEvent>,
    onSessionHistoryEntryClick: (BookSummary, Long) -> Unit,
    onClearSessionHistory: (BookSummary) -> Unit,
    serverReadingSessionsLoader: suspend (String) -> BookReadingSessionsResult,
    serverReadingAttemptsLoader: suspend (String) -> ReadingAttemptsResult,
    onBookUserRatingChange: suspend (BookSummary, Int?) -> BookDetailInfo?,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    onRead: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsStatus: (BookSummary, BookReadStatus) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onAuthorSelected: (String) -> Unit,
    onBookSelected: (BookSummary) -> Unit,
    onGenreSelected: (String) -> Unit
) {
    val canonicalStateBook = state.books.firstOrNull {
        it.id == book.id && (book.fileId == null || it.fileId == book.fileId)
    } ?: state.homeBooks.firstOrNull {
        it.id == book.id && (book.fileId == null || it.fileId == book.fileId)
    }
    val stateBook = canonicalStateBook ?: book
    val currentBook = stateBook.fileId?.let { fileId ->
        if (state.localFilePathOverrides.containsKey(fileId)) {
            stateBook.copy(localPath = state.localFilePathOverrides[fileId])
        } else {
            stateBook
        }
    } ?: stateBook
    val currentFileId = currentBook.fileId
    val currentBookIsDownloading = currentFileId != null && currentFileId in state.downloadingFileIds
    var isRefreshing by remember(currentBook.id, currentBook.fileId) { mutableStateOf(false) }
    var reloadKey by remember(currentBook.id, currentBook.fileId) { mutableIntStateOf(0) }
    var isInitialDetailLoadComplete by remember(currentBook.id, currentBook.fileId) { mutableStateOf(false) }
    val detail by key(currentBook.id, currentBook.fileId) {
        produceState(
            initialValue = BookDetailInfo(currentBook),
            currentBook.id,
            currentBook.fileId,
            currentBook.updatedAtMillis,
            currentBook.localPath,
            currentBookIsDownloading,
            reloadKey
        ) {
            try {
                value = value.copy(
                    book = value.book.copy(
                        localPath = currentBook.localPath,
                        progressLabel = currentBook.progressLabel ?: value.book.progressLabel,
                        progressPercent = currentBook.progressPercent ?: value.book.progressPercent,
                        progressPositionMs = currentBook.progressPositionMs ?: value.book.progressPositionMs,
                        progressPageIndex = currentBook.progressPageIndex ?: value.book.progressPageIndex,
                        lastReadAtMillis = currentBook.lastReadAtMillis ?: value.book.lastReadAtMillis,
                        readStatus = currentBook.readStatus ?: value.book.readStatus,
                        isRead = currentBook.isRead,
                        updatedAtMillis = currentBook.updatedAtMillis ?: value.book.updatedAtMillis,
                        downloadedSourceUpdatedAtMillis = currentBook.downloadedSourceUpdatedAtMillis
                    )
                )
                value = detailLoader(currentBook) ?: value
            } finally {
                isRefreshing = false
                isInitialDetailLoadComplete = true
            }
        }
    }
    val onRefresh: () -> Unit = {
        if (!isRefreshing && !currentBookIsDownloading) {
            isRefreshing = true
            reloadKey += 1
        }
    }
    var showCoverViewer by rememberSaveable(book.id) { mutableStateOf(false) }
    var displayedUserRating by remember(book.id) { mutableStateOf<Int?>(null) }
    var isUserRatingUpdating by remember(book.id) { mutableStateOf(false) }
    val userRatingScope = rememberCoroutineScope()
    LaunchedEffect(book.id, detail.userRating) {
        if (!isUserRatingUpdating) {
            displayedUserRating = detail.userRating
        }
    }
    var selectedFileId by remember(book.id, currentBook.fileId) { mutableStateOf(currentBook.fileId) }
    var hasAppliedInitialFileSelection by remember(book.id, currentBook.fileId) { mutableStateOf(false) }
    var showAvailableFileSheet by rememberSaveable(book.id, currentBook.fileId) { mutableStateOf(false) }
    LaunchedEffect(book.id, currentBook.fileId, isInitialDetailLoadComplete, detail.availableFiles) {
        if (!hasAppliedInitialFileSelection && isInitialDetailLoadComplete) {
            hasAppliedInitialFileSelection = true
            selectedFileId = resolveInitialSelectedFileId(
                availableFiles = detail.availableFiles,
                localFilePathOverrides = state.localFilePathOverrides,
                currentFileId = currentBook.fileId
            )
        }
    }
    val selectedFile = detail.availableFiles.firstOrNull { it.fileId == selectedFileId }
    val selectedBaseBook = selectedFile?.book ?: detail.book
    val selectedStateBook = if (selectedBaseBook.fileId == currentBook.fileId) currentBook else selectedBaseBook
    val selectedLocalPath = selectedBaseBook.fileId?.let { state.localFilePathOverrides[it] }
        ?: selectedStateBook.localPath
    val displayBook = selectedBaseBook.copy(
        localPath = selectedLocalPath,
        progressLabel = selectedStateBook.progressLabel ?: selectedBaseBook.progressLabel,
        progressPercent = selectedStateBook.progressPercent ?: selectedBaseBook.progressPercent,
        progressPositionMs = selectedStateBook.progressPositionMs ?: selectedBaseBook.progressPositionMs,
        progressPageIndex = selectedStateBook.progressPageIndex ?: selectedBaseBook.progressPageIndex,
        lastReadAtMillis = selectedStateBook.lastReadAtMillis ?: selectedBaseBook.lastReadAtMillis,
        readStatus = selectedStateBook.readStatus ?: selectedBaseBook.readStatus,
        isRead = selectedStateBook.isRead,
        updatedAtMillis = selectedStateBook.updatedAtMillis ?: selectedBaseBook.updatedAtMillis,
        downloadedSourceUpdatedAtMillis = selectedStateBook.downloadedSourceUpdatedAtMillis,
        audioChapters = selectedBaseBook.audioChapters.ifEmpty { detail.audioChapters }
    )
    val isDownloading = displayBook.fileId != null && displayBook.fileId in state.downloadingFileIds
    val showSessionHistoryButton = showAudiobookSessionHistoryButton(displayBook)
    var showSessionHistory by remember(displayBook.id, displayBook.fileId, openSessionHistory) {
        mutableStateOf(openSessionHistory)
    }
    var sessionHistory by remember(displayBook.id, displayBook.fileId) {
        mutableStateOf<List<AudiobookSessionEvent>>(emptyList())
    }
    var showClearSessionHistory by remember(displayBook.id, displayBook.fileId) {
        mutableStateOf(false)
    }
    LaunchedEffect(state.serverUrl, displayBook.id, displayBook.fileId, displayBook.mediaKind) {
        sessionHistory = if (displayBook.mediaKind == MediaKind.AUDIO) {
            sessionHistoryLoader(displayBook)
        } else {
            emptyList()
        }
    }
    var serverReadingSessions by remember(displayBook.id) {
        mutableStateOf<BookReadingSessionsResult?>(null)
    }
    var serverReadingAttempts by remember(displayBook.id) {
        mutableStateOf<ReadingAttemptsResult?>(null)
    }
    var isLoadingServerReadingHistory by remember(displayBook.id) { mutableStateOf(false) }
    LaunchedEffect(state.serverUrl, displayBook.id, displayBook.mediaKind, state.isOfflineSnapshot) {
        if (displayBook.mediaKind == MediaKind.AUDIO && !state.isOfflineSnapshot) {
            isLoadingServerReadingHistory = true
            try {
                serverReadingSessions = serverReadingSessionsLoader(displayBook.id)
                serverReadingAttempts = serverReadingAttemptsLoader(displayBook.id)
            } finally {
                isLoadingServerReadingHistory = false
            }
        } else {
            serverReadingSessions = null
            serverReadingAttempts = null
        }
    }
    val fileId = displayBook.fileId
    val downloadProgress = fileId?.let(state.downloadProgressByFileId::get)
    val downloadFailed = fileId != null && fileId in state.failedDownloadFileIds
    val permissionDenied = fileId != null && fileId in state.permissionDeniedDownloadFileIds
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
    val seriesNeighbors = remember(displayBook, seriesBooks, state.libraries) {
        seriesBookNeighbors(displayBook, seriesBooks, state.libraries.map { it.id })
    }
    val otherVersions = remember(displayBook, seriesBooks) {
        bookDetailOtherVersions(displayBook, seriesBooks)
    }
    val libraryNamesById = remember(state.libraries) {
        state.libraries.associate { it.id to it.name }
    }
    val publicationMetadata = buildList {
        detail.publisher?.let { add("Publisher" to it) }
        detail.publishedDate?.let { add("Published" to it) }
        detail.language?.let { add("Language" to it) }
        detail.pageCount?.let { add("Pages" to it.toString()) }
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
    val readingProgressLabel = bookDetailReadingProgressLabel(displayBook)

    if (showCoverViewer) {
        FullScreenCoverViewer(
            book = displayBook,
            coverLoader = coverLoader,
            onDismiss = { showCoverViewer = false }
        )
    }

    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("book_detail_pull_to_refresh")
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(
                    modifier = Modifier.width(116.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCoverViewer = true }
                            .semantics {
                                contentDescription = "Open full-screen cover for ${displayBook.title}"
                            }
                    ) {
                        BookCover(displayBook, coverLoader)
                    }
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
                    BookUserRatingStars(
                        rating = displayedUserRating,
                        enabled = !state.isOfflineSnapshot && !isUserRatingUpdating,
                        onRatingSelected = { selectedRating ->
                            val previousRating = displayedUserRating
                            val requestedRating = if (selectedRating == previousRating) null else selectedRating
                            displayedUserRating = requestedRating
                            isUserRatingUpdating = true
                            userRatingScope.launch {
                                try {
                                    val authoritativeDetail = onBookUserRatingChange(displayBook, requestedRating)
                                    displayedUserRating = if (authoritativeDetail == null) {
                                        previousRating
                                    } else {
                                        authoritativeDetail.userRating
                                    }
                                } catch (error: CancellationException) {
                                    displayedUserRating = previousRating
                                    throw error
                                } finally {
                                    isUserRatingUpdating = false
                                }
                            }
                        }
                    )
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
                        Text(
                            text = "by $it",
                            modifier = Modifier
                                .clickable { onAuthorSelected(it) }
                                .semantics { contentDescription = "Open author $it" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            val actionState = bookDetailActionState(
                isDownloaded = displayBook.isDownloaded,
                isDownloading = isDownloading,
                downloadFailed = downloadFailed,
                permissionDenied = permissionDenied,
                hasDownloadUpdate = displayBook.hasDownloadUpdate,
                isOfflineSnapshot = state.isOfflineSnapshot,
                isServerMissing = displayBook.isServerMissing
            )
            val statusActionLabel = bookDetailReadingStatusActionLabel()
            var showActionMenu by rememberSaveable(displayBook.id) { mutableStateOf(false) }
            var showStatusMenu by rememberSaveable(displayBook.id) { mutableStateOf(false) }
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelLarge
            fun actionWidth(label: String, minimumDp: Float): Float = with(density) {
                val textWidth = textMeasurer.measure(label, style = labelStyle, maxLines = 1).size.width.toDp()
                maxOf(minimumDp.dp, 8.dp + 24.dp + 2.dp + textWidth).value
            }
            val primaryActionLabel = bookDetailPrimaryActionLabel(displayBook)
            val readWidth = actionWidth(primaryActionLabel, 72f)
            val previewWidth = actionWidth("Preview", 84f)
            val markWidth = actionWidth(statusActionLabel, 100f)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        hasFixedOverflow = actionState.hasFixedOverflow,
                        hasSessionHistory = showSessionHistoryButton,
                        sessionHistoryWidth = 32f
                    )
                    val transferOverflowLabel = actionState.overflowTransferLabel ?: actionState.inlineTransfer
                        ?.contentDescription
                        ?.takeIf { !layout.showInlineTransferAction }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showSessionHistoryButton) {
                                DetailActionTile(
                                    label = "Open audiobook session history",
                                    icon = Icons.Default.History,
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(40.dp)
                                        .testTag("book-detail-session-history"),
                                    applyDefaultSize = false,
                                    onClick = { showSessionHistory = true }
                                )
                            }
                            val readModifier = Modifier.width(readWidth.dp).height(46.dp)
                            val previewModifier = Modifier.width(previewWidth.dp).height(46.dp)
                            DetailActionTile(
                            label = primaryActionLabel,
                            icon = Icons.Default.PlayArrow,
                            showLabel = true,
                            emphasized = true,
                            enabled = !isDownloading && !unavailableOffline && !displayBook.isServerMissing,
                            modifier = readModifier,
                            applyDefaultSize = false,
                            onClick = { onRead(displayBook) }
                            )
                            DetailActionTile(
                            label = "Preview",
                            icon = Icons.Default.Visibility,
                            showLabel = true,
                            enabled = !isDownloading && !unavailableOffline && !displayBook.isServerMissing,
                            modifier = previewModifier,
                            applyDefaultSize = false,
                            onClick = { onPreview(displayBook) }
                            )
                            actionState.inlineTransfer
                                ?.takeIf { layout.showInlineTransferAction }
                                ?.let { transfer ->
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
                                Box {
                                    DetailActionTile(
                                    label = statusActionLabel,
                                    icon = Icons.Default.CheckCircle,
                                    showLabel = true,
                                    enabled = !state.isOfflineSnapshot,
                                    modifier = Modifier
                                        .width(markWidth.dp)
                                        .height(46.dp)
                                        .testTag("book-detail-status-inline"),
                                    applyDefaultSize = false,
                                    onClick = { showStatusMenu = true }
                                    )
                                    BookDetailReadingStatusMenu(
                                    expanded = showStatusMenu,
                                    currentStatus = displayBook.readStatus,
                                    onDismissRequest = { showStatusMenu = false },
                                    onStatusSelected = { status ->
                                        showStatusMenu = false
                                        onMarkAsStatus(displayBook, status)
                                    }
                                    )
                                }
                            }
                        }
                        if (layout.showMore) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                DetailActionTile(
                                    label = "More book actions",
                                    icon = Icons.Default.MoreVert,
                                    modifier = Modifier.size(40.dp).testTag("book-detail-more"),
                                    applyDefaultSize = false,
                                    onClick = { showActionMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showActionMenu,
                                    onDismissRequest = { showActionMenu = false }
                                ) {
                                    transferOverflowLabel?.let { transferLabel ->
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
                                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                            },
                                            enabled = !state.isOfflineSnapshot,
                                            onClick = {
                                                showActionMenu = false
                                                showStatusMenu = true
                                            }
                                        )
                                    }
                                }
                                if (!layout.showInlineStatusAction) {
                                    BookDetailReadingStatusMenu(
                                        expanded = showStatusMenu,
                                        currentStatus = displayBook.readStatus,
                                        onDismissRequest = { showStatusMenu = false },
                                        onStatusSelected = { status ->
                                            showStatusMenu = false
                                            onMarkAsStatus(displayBook, status)
                                        }
                                    )
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
        if (detail.availableFiles.isNotEmpty()) {
            item {
                BookDetailAvailableFileSummary(
                    options = detail.availableFiles,
                    selectedFileId = selectedFileId,
                    onOpenSheet = { showAvailableFileSheet = true }
                )
            }
        }
        detail.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            item { ExpandableDescription("Synopsis", plainText(synopsis)) }
        }
        if (detail.providerIds.isNotEmpty()) {
            item { BookDetailProviderLinks(detail.providerIds) }
        }
        if (otherVersions.isNotEmpty()) {
            item {
                BookDetailOtherVersions(
                    versions = otherVersions,
                    libraryNamesById = libraryNamesById,
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    onDeleteLocalCopy = onDeleteLocalCopy
                )
            }
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
    if (showAvailableFileSheet && detail.availableFiles.size > 1) {
        BookDetailAvailableFileSheet(
            options = detail.availableFiles,
            selectedFileId = selectedFileId,
            onFileSelected = {
                hasAppliedInitialFileSelection = true
                selectedFileId = it
            },
            onDismissRequest = { showAvailableFileSheet = false }
        )
    }
    if (showSessionHistory) {
        Dialog(
            onDismissRequest = { showSessionHistory = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 680.dp)
            ) {
                AudiobookSessionHistory(
                    bookTitle = displayBook.title,
                    events = sessionHistory,
                    isLoadingServerReadingHistory = isLoadingServerReadingHistory,
                    serverReadingSessions = serverReadingSessions,
                    serverReadingAttempts = serverReadingAttempts,
                    onEventClick = { event ->
                        showSessionHistory = false
                        onSessionHistoryEntryClick(displayBook, event.positionMs)
                    },
                    onClearClick = { showClearSessionHistory = true },
                    onCloseClick = { showSessionHistory = false }
                )
            }
        }
    }
    if (showClearSessionHistory) {
        AlertDialog(
            onDismissRequest = { showClearSessionHistory = false },
            title = { Text("Clear session history?") },
            text = { Text("This removes the local audiobook play and pause history for this book.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSessionHistory = false
                        sessionHistory = emptyList()
                        onClearSessionHistory(displayBook)
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionHistory = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun AudiobookSessionHistory(
    bookTitle: String,
    events: List<AudiobookSessionEvent>,
    isLoadingServerReadingHistory: Boolean,
    serverReadingSessions: BookReadingSessionsResult?,
    serverReadingAttempts: ReadingAttemptsResult?,
    onEventClick: (AudiobookSessionEvent) -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audiobook-session-history"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 20.dp, 20.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Session history", style = MaterialTheme.typography.titleLarge)
                Text(
                    bookTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearClick) {
                Text("Clear")
            }
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.testTag("audiobook-session-history-close")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close session history")
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp, 8.dp, 20.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalListeningHistorySection(
                events = events,
                onEventClick = onEventClick
            )
            ServerReadingHistorySection(
                isLoading = isLoadingServerReadingHistory,
                sessions = serverReadingSessions,
                attempts = serverReadingAttempts
            )
        }
    }
}

@Composable
private fun LocalListeningHistorySection(
    events: List<AudiobookSessionEvent>,
    onEventClick: (AudiobookSessionEvent) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local-listening-history")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("local-listening-history-header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Local listening history",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse local listening history" else "Expand local listening history"
                )
            }
            if (expanded) {
                Text(
                    "Local history shows play and pause events on this device and can seek to the exact position.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (events.isEmpty()) {
                    Text("No local listening activity recorded.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    events.forEachIndexed { index, event ->
                        ListItem(
                            modifier = Modifier
                                .clickable { onEventClick(event) }
                                .testTag("audiobook-session-history-event-$index"),
                            headlineContent = {
                                Text(if (event.type == AudiobookSessionEventType.PLAY) "Played" else "Paused")
                            },
                            supportingContent = {
                                Text(
                                    "${formatSessionHistoryTimestamp(event.occurredAtMillis)} · " +
                                        formatDetailDuration(event.positionMs / 1000L)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerReadingHistorySection(
    isLoading: Boolean,
    sessions: BookReadingSessionsResult?,
    attempts: ReadingAttemptsResult?
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server-reading-history")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("server-reading-history-header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Server reading history",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse server reading history" else "Expand server reading history"
                )
            }
            if (expanded) {
                Text(
                    "Server history shows reading activity and analytics. It does not contain exact audio positions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    isLoading -> Text("Loading server history…", style = MaterialTheme.typography.bodyMedium)
                    sessions?.status == ServerReadingHistoryStatus.UNSUPPORTED &&
                        attempts?.status == ServerReadingHistoryStatus.UNSUPPORTED ->
                        Text("This BookOrbit server does not provide reading history.", style = MaterialTheme.typography.bodyMedium)
                    sessions?.status == ServerReadingHistoryStatus.ERROR &&
                        attempts?.status == ServerReadingHistoryStatus.ERROR ->
                        Text("Server reading history could not be loaded.", style = MaterialTheme.typography.bodyMedium)
                    sessions?.items.isNullOrEmpty() && attempts?.items.isNullOrEmpty() ->
                        Text("No server reading activity recorded.", style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        sessions?.items.orEmpty().forEach { session ->
                            ListItem(
                                headlineContent = {
                                    Text("Reading session · ${formatServerReadingDuration(session.durationSeconds)}")
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            formatServerDateRange(session.startedAt, session.endedAt),
                                            session.progressDelta?.let { "Progress change ${formatServerProgress(it)}" },
                                            session.endProgress?.let { "Ended at ${formatServerProgress(it)}" },
                                            session.format,
                                            session.source
                                        ).joinToString(" · ")
                                    )
                                }
                            )
                        }
                        attempts?.items.orEmpty().forEach { attempt ->
                            ListItem(
                                headlineContent = {
                                    Text("Reading attempt · ${formatReadingAttemptOutcome(attempt.outcome)}")
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            formatServerDateRange(attempt.startedOn, attempt.endedOn),
                                            attempt.totalSessions?.let { "$it sessions" },
                                            attempt.totalSeconds?.let { formatServerReadingDuration(it) },
                                            attempt.origin
                                        ).joinToString(" · ")
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatServerReadingDuration(seconds: Long?): String =
    seconds?.takeIf { it >= 0 }?.let(::formatDetailDuration) ?: "Duration unavailable"

private fun formatServerProgress(progress: Double): String =
    "${"%.1f".format(Locale.US, progress.coerceIn(0.0, 100.0))}%"

private fun formatServerDateRange(start: String?, end: String?): String? =
    listOfNotNull(start?.takeIf { it.isNotBlank() }, end?.takeIf { it.isNotBlank() })
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" → ")

private fun formatReadingAttemptOutcome(outcome: ReadingAttemptOutcome?): String = when (outcome) {
    null -> "In progress"
    ReadingAttemptOutcome.COMPLETED -> "Completed"
    ReadingAttemptOutcome.SKIMMED -> "Skimmed"
    ReadingAttemptOutcome.ABANDONED -> "Abandoned"
}

private fun formatSessionHistoryTimestamp(timestampMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestampMillis))

@Composable
private fun BookUserRatingStars(
    rating: Int?,
    enabled: Boolean,
    onRatingSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book-detail-user-rating"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { star ->
            val isCurrentRating = rating == star
            IconButton(
                onClick = { onRatingSelected(star) },
                enabled = enabled,
                modifier = Modifier
                    .size(23.dp)
                    .testTag("book-detail-user-rating-$star")
                    .semantics {
                        contentDescription = if (isCurrentRating) {
                            "Clear $star-star rating"
                        } else {
                            "Set rating to $star ${if (star == 1) "star" else "stars"}"
                        }
                        stateDescription = if (isCurrentRating) "Selected" else "Not selected"
                    }
            ) {
                Icon(
                    imageVector = if (star <= (rating ?: 0)) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (star <= (rating ?: 0)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun availableFileType(option: BookFileOption?, label: AvailableFileLabel?): String =
    option?.filename
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.uppercase(Locale.US)
        ?: label?.title?.substringBefore(" · ")
        ?: "FILE"

private fun availableFilename(option: BookFileOption?, label: AvailableFileLabel?): String =
    option?.filename?.takeIf { it.isNotBlank() }
        ?: label?.title?.substringAfter(" · ", "Unknown file")
        ?: "Unknown file"

private fun availableFileMetadata(option: BookFileOption?, label: AvailableFileLabel?): String = buildList {
    label?.metadata?.takeIf { it.isNotBlank() }?.let { value ->
        val parts = value.split(" · ")
        add(if (parts.size > 1) "${parts.drop(1).joinToString(" · ")} · ${parts.first()}" else value)
    }
    if (!option?.localPath.isNullOrBlank()) add("Available offline")
}.joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookDetailAvailableFileSummary(
    options: List<BookFileOption>,
    selectedFileId: String?,
    onOpenSheet: () -> Unit
) {
    val labels = availableFileDisplayLabels(options)
    val selectedOption = options.firstOrNull { it.fileId == selectedFileId } ?: options.firstOrNull()
    val selectedLabel = selectedOption?.fileId?.let(labels::get)
    val canChooseFile = options.size > 1

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canChooseFile) Modifier.clickable(onClick = onOpenSheet) else Modifier)
                .testTag("book-detail-available-file"),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = availableFileType(selectedOption, selectedLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("book-detail-available-file-format")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = availableFilename(selectedOption, selectedLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                            .testTag("book-detail-available-file-name")
                    )
                    availableFileMetadata(selectedOption, selectedLabel).takeIf { it.isNotBlank() }?.let { value ->
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("book-detail-available-file-metadata")
                        )
                    }
                }
                if (canChooseFile) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Choose available file"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailAvailableFileSheet(
    options: List<BookFileOption>,
    selectedFileId: String?,
    onFileSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    val labels = availableFileDisplayLabels(options)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("book-detail-available-file-sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                "Available files",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            options.forEachIndexed { index, option ->
                val label = option.fileId?.let(labels::get)
                val isSelected = option.fileId == selectedFileId
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFileSelected(option.fileId) }
                        .testTag("book-detail-available-file-option-${option.fileId}"),
                    leadingContent = {
                        Text(
                            text = availableFileType(option, label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    },
                    headlineContent = {
                        Text(
                            text = availableFilename(option, label),
                            maxLines = if (isSelected) Int.MAX_VALUE else 1,
                            overflow = if (isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(
                                "book-detail-available-file-option-name-${option.fileId}"
                            )
                        )
                    },
                    supportingContent = {
                        Text(
                            text = availableFileMetadata(option, label),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = if (isSelected) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = "Selected") }
                    } else {
                        null
                    }
                )
                if (index < options.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun BookDetailOtherVersions(
    versions: List<BookSummary>,
    libraryNamesById: Map<String, String>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book-detail-other-versions"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Other versions", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(versions, key = { "other-version-${it.id}" }) { version ->
                val format = version.format
                    ?.trim()
                    ?.trimStart('.')
                    ?.takeIf { it.isNotBlank() }
                    ?.uppercase()
                    ?: version.mediaKind.name
                        .lowercase()
                        .replaceFirstChar { it.titlecase() }
                        .takeUnless { version.mediaKind == MediaKind.UNKNOWN }
                val supportingText = listOfNotNull(
                    format,
                    libraryNamesById[version.libraryId]
                ).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
                ShelfBookCard(
                    book = version,
                    supportingText = supportingText,
                    coverLoader = coverLoader,
                    onClick = { onBookSelected(version) },
                    onDeleteLocalCopy = { onDeleteLocalCopy(version) },
                    modifier = Modifier.testTag("book-detail-other-version-${version.id}")
                )
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

@Composable
private fun BookDetailReadingStatusMenu(
    expanded: Boolean,
    currentStatus: BookReadStatus?,
    onDismissRequest: () -> Unit,
    onStatusSelected: (BookReadStatus) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("book-detail-status-menu")
    ) {
        BOOK_READ_STATUS_OPTIONS.forEach { status ->
            DropdownMenuItem(
                text = { Text(status.displayLabel()) },
                leadingIcon = {
                    RadioButton(
                        selected = status == currentStatus,
                        onClick = null
                    )
                },
                modifier = Modifier.testTag("book-detail-status-${status.wireValue}"),
                onClick = { onStatusSelected(status) }
            )
        }
    }
}

internal fun bookDetailReadingStatusActionLabel(): String = "Mark as..."

internal fun showAudiobookSessionHistoryButton(book: BookSummary): Boolean =
    book.mediaKind == MediaKind.AUDIO &&
        (book.readStatus == BookReadStatus.READING || book.readStatus == BookReadStatus.REREADING)

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
    permissionDenied: Boolean = false,
    hasDownloadUpdate: Boolean,
    isOfflineSnapshot: Boolean,
    isServerMissing: Boolean = false
): BookDetailActionState = if (isServerMissing) {
    BookDetailActionState(
        inlineTransfer = if (isDownloading) BookDetailInlineTransfer.CANCEL_DOWNLOAD else null,
        overflowTransferLabel = null,
        showDeleteLocal = isDownloaded
    )
} else if (!isDownloaded) {
    BookDetailActionState(
        inlineTransfer = when {
            isDownloading -> BookDetailInlineTransfer.CANCEL_DOWNLOAD
            permissionDenied -> null
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
            (hasDownloadUpdate || downloadFailed) && !isOfflineSnapshot -> "Update local"
            else -> null
        },
        showDeleteLocal = true
    )
}

internal data class BookDetailActionRowLayout(
    val showInlineStatusAction: Boolean,
    val showInlineTransferAction: Boolean,
    val showMore: Boolean,
    val moreSlotWidth: Float
)

internal fun bookDetailActionRowLayout(
    availableWidth: Float,
    readWidth: Float,
    previewWidth: Float,
    markWidth: Float,
    hasInlineTransfer: Boolean,
    hasFixedOverflow: Boolean,
    hasSessionHistory: Boolean = false,
    sessionHistoryWidth: Float = 32f,
    iconWidth: Float = 40f,
    spacing: Float = 2f
): BookDetailActionRowLayout {
    val baseRequiredWidths = buildList {
        if (hasSessionHistory) add(sessionHistoryWidth)
        add(readWidth)
        add(previewWidth)
    }
    fun occupied(widths: List<Float>): Float =
        widths.sum() + spacing * (widths.size - 1).coerceAtLeast(0)

    val withInlineStatus = buildList {
        addAll(baseRequiredWidths)
        if (hasInlineTransfer) add(iconWidth)
        add(markWidth)
        if (hasFixedOverflow) add(iconWidth)
    }
    val showInlineStatus = occupied(withInlineStatus) <= availableWidth
    val showMoreAfterStatus = hasFixedOverflow || !showInlineStatus
    val withInlineTransfer = buildList {
        addAll(baseRequiredWidths)
        if (hasInlineTransfer) add(iconWidth)
        if (showMoreAfterStatus) add(iconWidth)
    }
    val showInlineTransfer = !hasInlineTransfer || occupied(withInlineTransfer) <= availableWidth
    val showMore = showMoreAfterStatus || (hasInlineTransfer && !showInlineTransfer)
    return BookDetailActionRowLayout(
        showInlineStatusAction = showInlineStatus,
        showInlineTransferAction = showInlineTransfer,
        showMore = showMore,
        moreSlotWidth = if (showMore) iconWidth else 0f
    )
}

@Composable
private fun DetailActionTile(
    label: String,
    icon: ImageVector?,
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
                .padding(horizontal = if (showLabel) 4.dp else 0.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(
                if (showLabel) 2.dp else 0.dp,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let { imageVector ->
                Icon(imageVector, contentDescription = label, modifier = Modifier.size(24.dp))
            }
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailProviderLinks(providerIds: List<BookProviderId>) {
    val links = providerIds.mapNotNull { providerId ->
        providerUrl(providerId)?.let { url -> providerId to url }
    }
    if (links.isEmpty()) return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Metadata sources", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            links.forEach { (providerId, url) ->
                val name = providerDisplayName(providerId.provider)
                Card(
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            runCatching { context.startActivity(intent) }
                        }
                        .semantics { contentDescription = "Open $name page for this book" },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(text = name, style = MaterialTheme.typography.labelMedium)
                    }
                }
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

/**
 * Vertical distance (in px) to shift the cover so its center lands on the physical
 * display's center, correcting for a Dialog root that is offset from the screen
 * (e.g. when the host Activity fits system windows but the dialog does not).
 */
internal fun coverCenterCorrectionPx(
    rootScreenY: Float,
    rootHeightPx: Float,
    physicalScreenHeightPx: Float
): Float {
    val physicalScreenCenterY = physicalScreenHeightPx / 2f
    val rootCenterY = rootScreenY + rootHeightPx / 2f
    return physicalScreenCenterY - rootCenterY
}

internal fun isPointInsideTransformedCover(
    pointX: Float,
    pointY: Float,
    coverLeft: Float,
    coverTop: Float,
    coverWidth: Float,
    coverHeight: Float,
    scale: Float,
    panX: Float,
    panY: Float
): Boolean {
    if (coverWidth <= 0f || coverHeight <= 0f || scale <= 0f) return false
    val centerX = coverLeft + coverWidth / 2f + panX
    val centerY = coverTop + coverHeight / 2f + panY
    val halfWidth = coverWidth * scale / 2f
    val halfHeight = coverHeight * scale / 2f
    return pointX in (centerX - halfWidth)..(centerX + halfWidth) &&
        pointY in (centerY - halfHeight)..(centerY + halfHeight)
}

private fun physicalDisplayHeightPx(context: Context): Int {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.maximumWindowMetrics.bounds.height()
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        metrics.heightPixels
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FullScreenCoverViewer(
    book: BookSummary,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var coverBytes by remember(book.id, book.coverUrl, book.updatedAtMillis) { mutableStateOf<ByteArray?>(null) }
    var pendingDownloadBytes by remember { mutableStateOf<ByteArray?>(null) }
    fun showExportResult(result: CoverExportResult) {
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
    }
    fun export(bytes: ByteArray) {
        showExportResult(exportCoverImage(context, book.title, bytes))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val bytes = pendingDownloadBytes
        pendingDownloadBytes = null
        if (granted && bytes != null) {
            export(bytes)
        } else if (!granted) {
            showExportResult(CoverExportResult(false, "Storage permission is required to save the cover"))
        }
    }
    fun requestExport() {
        val bytes = coverBytes
        if (bytes == null) {
            showExportResult(CoverExportResult(false, "Cover image is not available"))
        } else if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadBytes = bytes
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            export(bytes)
        }
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, book.id, book.coverUrl, book.updatedAtMillis) {
        val bytes = try {
            coverLoader(book)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        coverBytes = bytes
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
    val density = LocalDensity.current
    var scale by remember(book.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(book.id) { mutableStateOf(Offset.Zero) }
    var coverLayoutPosition by remember(book.id) { mutableStateOf(Offset.Zero) }
    var coverLayoutSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    var menuAnchor by remember(book.id) { mutableStateOf<Offset?>(null) }
    val minScale = 1f
    val maxScale = 4f
    val presetZoomScale = 2.5f

    fun boundedPan(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= minScale || coverLayoutSize == IntSize.Zero) return Offset.Zero
        val maxX = (coverLayoutSize.width * (currentScale - 1f)) / 2f
        val maxY = (coverLayoutSize.height * (currentScale - 1f)) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun resetTransform() {
        scale = minScale
        panOffset = Offset.Zero
    }

    fun isInsideVisibleCover(position: Offset): Boolean = isPointInsideTransformedCover(
        pointX = position.x,
        pointY = position.y,
        coverLeft = coverLayoutPosition.x,
        coverTop = coverLayoutPosition.y,
        coverWidth = coverLayoutSize.width.toFloat(),
        coverHeight = coverLayoutSize.height.toFloat(),
        scale = scale,
        panX = panOffset.x,
        panY = panOffset.y
    )

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale
        panOffset = boundedPan(panOffset + panChange, newScale)
    }

    var verticalCorrectionPx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .onGloballyPositioned { coordinates ->
                    val dialogLocation = IntArray(2)
                    dialogView.getLocationOnScreen(dialogLocation)
                    val rootScreenY = dialogLocation[1] + coordinates.positionInWindow().y
                    val rootHeightPx = coordinates.size.height.toFloat()
                    val physicalHeightPx = physicalDisplayHeightPx(context).toFloat()
                    verticalCorrectionPx = coverCenterCorrectionPx(
                        rootScreenY = rootScreenY,
                        rootHeightPx = rootHeightPx,
                        physicalScreenHeightPx = physicalHeightPx
                    )
                }
                .pointerInput(book.id) {
                    detectTapGestures(
                        onTap = { position ->
                            if (isInsideVisibleCover(position)) {
                                resetTransform()
                            } else {
                                onDismiss()
                            }
                        },
                        onDoubleTap = { position ->
                            if (isInsideVisibleCover(position)) {
                                if (scale > minScale) {
                                    resetTransform()
                                } else {
                                    scale = presetZoomScale
                                    panOffset = Offset.Zero
                                }
                            } else {
                                onDismiss()
                            }
                        },
                        onLongPress = { position ->
                            if (isInsideVisibleCover(position)) menuAnchor = position
                        }
                    )
                }
                .transformable(state = transformableState)
                .semantics {
                    contentDescription = "Full-screen cover for ${book.title}. Pinch or double-tap to zoom. Tap the cover to reset zoom. Tap outside the cover or use back to close. Long-press the cover for options"
                },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, verticalCorrectionPx.roundToInt()) }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val ratio = book.coverAspectRatio.widthToHeight.coerceAtLeast(0.01f)
                val coverWidth = minOf(maxWidth, maxHeight * ratio)
                val coverHeight = minOf(maxHeight, maxWidth / ratio)
                Box(
                    modifier = Modifier
                        .width(coverWidth)
                        .height(coverHeight)
                        .onGloballyPositioned { coverLayoutPosition = it.positionInRoot() }
                        .onSizeChanged { coverLayoutSize = it }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        ),
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
            val anchor = menuAnchor
            if (anchor != null) {
                val anchorOffset = with(density) { DpOffset(anchor.x.toDp(), anchor.y.toDp()) }
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { menuAnchor = null },
                    offset = anchorOffset
                ) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            menuAnchor = null
                            requestExport()
                        }
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
@OptIn(ExperimentalLayoutApi::class)
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
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDeleteLocalCopies: (List<BookSummary>) -> Unit,
    downloadState: BrowserState? = null,
    onDownload: ((BookSummary) -> Unit)? = null,
    onBulkDownload: ((String, List<BookSummary>) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
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
    var isRefreshing by remember(seriesKey) { mutableStateOf(false) }
    var reloadKey by remember(seriesKey) { mutableIntStateOf(0) }
    val detail by produceState(initialValue = localDetail, seriesKey, reloadKey) {
        try {
            value = detailLoader(seriesKey) ?: value
        } finally {
            isRefreshing = false
        }
    }
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            reloadKey += 1
        }
    }
    val bookSections = seriesBookSections(detail.books, libraries, groupingMode)
    val completion = if (detail.bookCount > 0) detail.readCount.toFloat() / detail.bookCount else 0f
    val seriesDownloadCandidates = seriesSelectableFiles(
        booksWithLocalFilePathOverrides(
            books = detail.books,
            localFilePathOverrides = downloadState?.localFilePathOverrides.orEmpty()
        )
    )
    var showSeriesFileSelection by remember(seriesKey) { mutableStateOf(false) }
    var seriesSelectedFileIds by remember(seriesKey) { mutableStateOf<Set<String>>(emptySet()) }
    var seriesFrozenSelection by remember(seriesKey) { mutableStateOf<List<BookSummary>>(emptyList()) }
    var showSeriesDownloadConfirm by remember(seriesKey) { mutableStateOf(false) }
    var pendingSeriesLocalDeletes by remember(seriesKey) { mutableStateOf<List<BookSummary>?>(null) }
    val seriesLocalCopies = localCopiesForBulkAction(
        books = seriesDownloadCandidates,
        localFilePathOverrides = downloadState?.localFilePathOverrides.orEmpty()
    )
    val seriesActiveFileIds = seriesDownloadCandidates
        .mapNotNull { it.fileId }
        .filterTo(linkedSetOf()) { it in downloadState?.downloadingFileIds.orEmpty() }
    val seriesFrozenFileIds = seriesFrozenSelection.mapNotNull { it.fileId }
    val seriesProgressFileIds = if (seriesFrozenFileIds.any { it in seriesActiveFileIds }) {
        seriesFrozenFileIds
    } else {
        seriesActiveFileIds.toList()
    }
    val seriesDownloadProgress = collectionDownloadProgress(
        fileIds = seriesProgressFileIds,
        downloadingFileIds = downloadState?.downloadingFileIds.orEmpty(),
        progressByFileId = downloadState?.downloadProgressByFileId.orEmpty(),
        completedFileIds = seriesLocalCopies.mapNotNullTo(mutableSetOf()) { it.fileId }
    )
    if (showSeriesFileSelection) {
        SeriesFileSelectionDialog(
            candidates = seriesDownloadCandidates,
            libraries = libraries,
            groupingMode = groupingMode,
            onGroupingModeChange = onGroupingModeChange,
            selectedFileIds = seriesSelectedFileIds,
            onSelectedFileIdsChange = { seriesSelectedFileIds = it },
            onDismissRequest = { showSeriesFileSelection = false },
            onConfirm = {
                seriesFrozenSelection = seriesDownloadDispatchOrder(
                    selectedSeriesFiles(seriesDownloadCandidates, seriesSelectedFileIds)
                )
                showSeriesFileSelection = false
                if (seriesFrozenSelection.isNotEmpty()) showSeriesDownloadConfirm = true
            }
        )
    }
    if (showSeriesDownloadConfirm) {
        AlertDialog(
            onDismissRequest = { showSeriesDownloadConfirm = false },
            title = { Text("Download this series?") },
            text = {
                Text(
                    "This will download ${seriesFrozenSelection.size} " +
                        "${if (seriesFrozenSelection.size == 1) "file" else "files"} to this device. " +
                        "This may use a significant amount of storage and data."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSeriesDownloadConfirm = false
                        onBulkDownload?.invoke("this series", seriesFrozenSelection)
                    },
                    modifier = Modifier.testTag("confirm-download-series")
                ) { Text("Download series") }
            },
            dismissButton = {
                TextButton(onClick = { showSeriesDownloadConfirm = false }) { Text("Cancel") }
            }
        )
    }
    pendingSeriesLocalDeletes?.let { booksToDelete ->
        AlertDialog(
            onDismissRequest = { pendingSeriesLocalDeletes = null },
            title = {
                Text(
                    "Delete ${booksToDelete.size} local " +
                        "${if (booksToDelete.size == 1) "copy" else "copies"}?"
                )
            },
            text = {
                Text(
                    "The selected files will be removed from this device. " +
                        "Your BookOrbit books are not deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSeriesLocalDeletes = null
                        onDeleteLocalCopies(booksToDelete)
                    },
                    modifier = Modifier.testTag("confirm-delete-series-local-copies")
                ) { Text("Delete local") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSeriesLocalDeletes = null }) { Text("Cancel") }
            }
        )
    }
    PullToRefreshLayout(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .testTag("series_detail_pull_to_refresh")
    ) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = LocalLibraryCardSize.current.gridMinSize),
        modifier = Modifier.fillMaxSize(),
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
                val showSeriesDownload = onBulkDownload != null &&
                    hasSelectableBulkDownloads(seriesDownloadCandidates)
                if (showSeriesDownload || seriesLocalCopies.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showSeriesDownload) {
                            OutlinedButton(
                                onClick = {
                                    seriesSelectedFileIds = defaultSeriesFileSelection(seriesDownloadCandidates)
                                    showSeriesFileSelection = true
                                },
                                enabled = seriesActiveFileIds.isEmpty(),
                                modifier = Modifier.testTag("download-series")
                            ) { Text("Download series") }
                        }
                        if (seriesLocalCopies.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { pendingSeriesLocalDeletes = seriesLocalCopies },
                                enabled = seriesActiveFileIds.isEmpty(),
                                modifier = Modifier.testTag("delete-series-local-copies")
                            ) { Text("Delete local books") }
                        }
                    }
                }
                seriesDownloadProgress?.let { progress ->
                    CollectionDownloadProgressIndicator(
                        state = progress,
                        modifier = Modifier.padding(top = 6.dp),
                        testTag = "series-download-progress"
                    )
                }
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
                    downloadState = downloadState,
                    onDownload = onDownload?.let { download -> { download(book) } },
                    onCancelDownload = onCancelDownload?.let { cancel -> { cancel(book) } },
                    onClearFailedDownload = onClearFailedDownload?.let { clear -> { clear(book) } },
                    onDeleteLocalCopy = { onDeleteLocalCopy(book) },
                    showSeriesIndex = true,
                    onMarkAsRead = { onMarkAsRead(book) },
                    onMarkAsUnread = { onMarkAsUnread(book) }
                )
            }
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
            val ordered = seriesBooks.sortedWith(
                compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            if (ordered.none { it.readStatus == BookReadStatus.READ }) {
                return@mapNotNull null
            }
            ordered
                .firstOrNull { it.readStatus != BookReadStatus.READ }
                ?.takeUnless {
                    it.readStatus in setOf(BookReadStatus.READING, BookReadStatus.REREADING)
                }
        }
        .take(12)
}

internal fun wantToReadBooks(books: List<BookSummary>): List<BookSummary> {
    return books
        .filter { it.readStatus == BookReadStatus.WANT_TO_READ }
        .sortedWith(readingStateShelfComparator())
        .take(12)
}

internal fun currentlyReadingBooks(books: List<BookSummary>): List<BookSummary> {
    return books
        .filter {
            it.readStatus == BookReadStatus.READING ||
                it.readStatus == BookReadStatus.REREADING
        }
        .sortedWith(
            compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
                .thenByDescending { it.progressPercent ?: 0f }
                .thenByDescending { it.updatedAtMillis ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        .take(12)
}

internal fun recentlyReadBooks(books: List<BookSummary>): List<BookSummary> {
    return books
        .filter {
            it.readStatus == BookReadStatus.READ ||
                it.readStatus == BookReadStatus.SKIMMED
        }
        .sortedWith(readingStateShelfComparator())
        .take(12)
}

private fun readingStateShelfComparator(): Comparator<BookSummary> =
    compareByDescending<BookSummary> { it.lastReadAtMillis ?: 0L }
        .thenByDescending { it.updatedAtMillis ?: 0L }
        .thenBy { it.title.lowercase() }

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
