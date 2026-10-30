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
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign

import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

internal enum class BrowserDestination {
    HOME,
    LIBRARY,
    SERIES,
    SMART_SCOPES,
    AUTHORS,
    ANNOTATIONS,
    LOCAL_BOOKS,
    CURRENTLY_READING,
    ON_DECK,
    WANT_TO_READ,
    RECENTLY_ADDED_BOOKS,
    RECENTLY_RELEASED_BOOKS,
    RECENTLY_ADDED_SERIES,
    RECENTLY_UPDATED_SERIES,
    RECENTLY_READ,
    STATISTICS,
    ACHIEVEMENTS,
    OPTIONS,
    ABOUT
}
enum class HomeSection(val title: String, @StringRes val titleRes: Int) {
    CURRENTLY_READING("Currently reading", R.string.browse_currently_reading),
    ON_DECK("On deck", R.string.browse_on_deck),
    WANT_TO_READ("Want to read", R.string.browse_want_to_read),
    RECENTLY_ADDED_BOOKS("Recently added books", R.string.browse_recently_added_books),
    RECENTLY_RELEASED_BOOKS("Recently released books", R.string.browse_recently_released_books),
    RECENTLY_ADDED_SERIES("Recently added series", R.string.browse_recently_added_series),
    RECENTLY_UPDATED_SERIES("Recently updated series", R.string.browse_recently_updated_series),
    RECENTLY_READ("Recently read books", R.string.browse_recently_read_books)
}

internal fun HomeSection.recentBooksFilter(): BookBrowseFilter = when (this) {
    HomeSection.RECENTLY_ADDED_BOOKS,
    HomeSection.RECENTLY_RELEASED_BOOKS,
    HomeSection.RECENTLY_ADDED_SERIES -> BookBrowseFilter(
        sort = BookSortOption.ADDED,
        direction = SortDirection.DESCENDING
    )
    HomeSection.RECENTLY_UPDATED_SERIES -> BookBrowseFilter(
        sort = BookSortOption.UPDATED,
        direction = SortDirection.DESCENDING
    )
    HomeSection.RECENTLY_READ -> BookBrowseFilter(
        sort = BookSortOption.LAST_READ,
        direction = SortDirection.DESCENDING
    )
    else -> BookBrowseFilter()
}
private enum class LibraryTab { RECOMMENDED, BROWSE }

private const val HOME_PREVIEW_LIMIT = 8

private fun HomeSection.toBrowserDestination(): BrowserDestination = when (this) {
    HomeSection.CURRENTLY_READING -> BrowserDestination.CURRENTLY_READING
    HomeSection.ON_DECK -> BrowserDestination.ON_DECK
    HomeSection.WANT_TO_READ -> BrowserDestination.WANT_TO_READ
    HomeSection.RECENTLY_ADDED_BOOKS -> BrowserDestination.RECENTLY_ADDED_BOOKS
    HomeSection.RECENTLY_RELEASED_BOOKS -> BrowserDestination.RECENTLY_RELEASED_BOOKS
    HomeSection.RECENTLY_ADDED_SERIES -> BrowserDestination.RECENTLY_ADDED_SERIES
    HomeSection.RECENTLY_UPDATED_SERIES -> BrowserDestination.RECENTLY_UPDATED_SERIES
    HomeSection.RECENTLY_READ -> BrowserDestination.RECENTLY_READ
}

private fun BrowserDestination.toHomeSection(): HomeSection? = when (this) {
    BrowserDestination.CURRENTLY_READING -> HomeSection.CURRENTLY_READING
    BrowserDestination.ON_DECK -> HomeSection.ON_DECK
    BrowserDestination.WANT_TO_READ -> HomeSection.WANT_TO_READ
    BrowserDestination.RECENTLY_ADDED_BOOKS -> HomeSection.RECENTLY_ADDED_BOOKS
    BrowserDestination.RECENTLY_RELEASED_BOOKS -> HomeSection.RECENTLY_RELEASED_BOOKS
    BrowserDestination.RECENTLY_ADDED_SERIES -> HomeSection.RECENTLY_ADDED_SERIES
    BrowserDestination.RECENTLY_UPDATED_SERIES -> HomeSection.RECENTLY_UPDATED_SERIES
    BrowserDestination.RECENTLY_READ -> HomeSection.RECENTLY_READ
    else -> null
}

internal fun browserRootBackDestination(destination: BrowserDestination): BrowserDestination? = when (destination) {
    BrowserDestination.HOME,
    BrowserDestination.SMART_SCOPES,
    BrowserDestination.CURRENTLY_READING,
    BrowserDestination.ON_DECK,
    BrowserDestination.WANT_TO_READ,
    BrowserDestination.RECENTLY_ADDED_BOOKS,
    BrowserDestination.RECENTLY_RELEASED_BOOKS,
    BrowserDestination.RECENTLY_ADDED_SERIES,
    BrowserDestination.RECENTLY_UPDATED_SERIES,
    BrowserDestination.RECENTLY_READ -> null
    BrowserDestination.LIBRARY,
    BrowserDestination.SERIES,
    BrowserDestination.AUTHORS,
    BrowserDestination.ANNOTATIONS,
    BrowserDestination.LOCAL_BOOKS,
    BrowserDestination.STATISTICS,
    BrowserDestination.ACHIEVEMENTS,
    BrowserDestination.OPTIONS,
    BrowserDestination.ABOUT -> BrowserDestination.HOME
}

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
    LANGUAGE,
    CLEAR_CACHE
}

private enum class OptionsCategory(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int
) {
    APPEARANCE(
        R.string.settings_category_appearance,
        R.string.settings_category_appearance_summary
    ),
    GENERAL(
        R.string.settings_category_general,
        R.string.settings_category_general_summary
    ),
    PLAYBACK(
        R.string.settings_category_playback,
        R.string.settings_category_playback_summary
    ),
    LIBRARY(
        R.string.settings_category_library,
        R.string.settings_category_library_summary
    ),
    DOWNLOADS_OFFLINE(
        R.string.settings_category_downloads,
        R.string.settings_category_downloads_summary
    ),
    NETWORK_SYNC(
        R.string.settings_category_network,
        R.string.settings_category_network_summary
    ),
    PERMISSIONS(
        R.string.settings_category_permissions,
        R.string.settings_category_permissions_summary
    )
}

internal enum class ReaderConfigurationFormat {
    EPUB,
    PDF,
    COMIC
}

@StringRes
internal fun ReaderConfigurationFormat.tapZoneTutorialPreferenceTitleRes(): Int = when (this) {
    ReaderConfigurationFormat.EPUB -> R.string.settings_tap_zone_tutorial_epub
    ReaderConfigurationFormat.PDF -> R.string.settings_tap_zone_tutorial_pdf
    ReaderConfigurationFormat.COMIC -> R.string.settings_tap_zone_tutorial_comic
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

internal fun moreMenuSheetBottomPaddingPx(hostNavigationBarInsetPx: Int): Int =
    hostNavigationBarInsetPx.coerceAtLeast(0)

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

internal enum class PendingCellularDownloadScope {
    GROUP,
    SINGLE_FILE
}

internal data class PendingCellularDownload(
    val book: BookSummary,
    val scope: PendingCellularDownloadScope
)

internal fun routePendingCellularDownload(
    pending: PendingCellularDownload,
    onDownload: (BookSummary) -> Unit,
    onDownloadSingleFile: (BookSummary) -> Unit
) {
    when (pending.scope) {
        PendingCellularDownloadScope.GROUP -> onDownload(pending.book)
        PendingCellularDownloadScope.SINGLE_FILE -> onDownloadSingleFile(pending.book)
    }
}

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
    title: String? = null
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
            Text(
                title ?: stringResource(R.string.browse_select_downloads),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(
                    R.string.browse_files_selected,
                    selectedCount,
                    selectableIds.size
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onSelectedFileIdsChange(selectableIds) },
                    modifier = Modifier.testTag("bulk-download-select-all")
                ) { Text(stringResource(R.string.browse_select_all)) }
                TextButton(
                    onClick = { onSelectedFileIdsChange(emptySet()) },
                    modifier = Modifier.testTag("bulk-download-clear-all")
                ) { Text(stringResource(R.string.browse_clear_all)) }
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
                                Text(
                                    if (selectable) {
                                        group.label
                                    } else {
                                        stringResource(R.string.browse_downloaded)
                                    }
                                )
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
            ) { Text(stringResource(R.string.common_continue)) }
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
                stringResource(
                    if (state.totalCount == 1) {
                        R.string.browse_downloading_file_progress
                    } else {
                        R.string.browse_downloading_files_progress
                    },
                    state.totalCount,
                    (progress * 100).toInt()
                )
            } ?: stringResource(
                if (state.totalCount == 1) {
                    R.string.browse_downloading_file
                } else {
                    R.string.browse_downloading_files
                },
                state.totalCount
            ),
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

@StringRes
internal fun seriesBookCountLabelRes(count: Int): Int =
    if (count == 1) R.string.browse_book_count_one else R.string.browse_books_count

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
): List<BookSummary> {
    val scopedCatalog = catalogHomeBooks.filter { libraryId == null || it.libraryId == libraryId }
    val sourceBooks = if (downloadedBooks == null || libraryId == null) {
        downloadedBooks ?: scopedCatalog
    } else {
        downloadedBooks.mapNotNull { localBook ->
            val catalogBook = scopedCatalog.firstOrNull { catalog ->
                catalog.id == localBook.id ||
                    (catalog.fileId != null && catalog.fileId == localBook.fileId)
            }
            catalogBook?.copy(
                localPath = localBook.localPath,
                updatedAtMillis = localBook.updatedAtMillis ?: catalogBook.updatedAtMillis
            )
        }
    }
    return localBooksShelf(
        books = sourceBooks,
        libraryId = libraryId,
        limit = limit
    )
}

internal fun homeFeedShelves(shelves: HomeShelfData, libraryId: String?): HomeShelfData {
    if (libraryId == null) return shelves
    fun scoped(source: Map<HomeSection, List<BookSummary>>): Map<HomeSection, List<BookSummary>> =
        source.mapValues { (_, books) -> books.filter { it.libraryId == libraryId } }
    return shelves.copy(
        booksBySection = scoped(shelves.booksBySection),
        seriesBySection = scoped(shelves.seriesBySection)
    )
}

internal fun homeFeedBooks(books: List<BookSummary>, libraryId: String?): List<BookSummary> =
    libraryId?.let { selectedId -> books.filter { it.libraryId == selectedId } } ?: books

internal fun homeSectionSourceBooks(
    state: BrowserState,
    section: HomeSection,
    serverWide: Boolean
): List<BookSummary> {
    val shelves = homeFeedShelves(state.homeShelves, state.selectedLibraryId.takeUnless { serverWide })
    shelves.seriesBySection[section]?.let { return it }
    if (shelves.isServerProvided && section in shelves.booksBySection.keys) {
        return shelves.booksBySection[section].orEmpty()
    }
    return if (serverWide) state.homeBooks else state.books
}

internal fun bookAvailableFormatsForDisplay(
    book: BookSummary
): List<String> = book.availableFormats.ifEmpty {
    normalizedAvailableFormats(listOf(book.format to book.mediaKind))
}


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

private suspend fun loadCatalogImageWithRetry(
    url: String,
    loader: suspend (String) -> ByteArray?
): ByteArray? {
    repeat(2) { attempt ->
        val loaded = runCatching { loader(url) }.getOrNull()
        if (loaded != null && loaded.isNotEmpty()) {
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
    onLoadMoreLibraryBooks: () -> Unit = {},
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onNewServer: () -> Unit = {},
    serverProfilesLoader: () -> List<ServerProfile> = { emptyList() },
    onSwitchServer: (String) -> Unit = {},
    onRemoveServer: (String) -> Unit = {},
    onLibrarySelected: (String) -> Unit,
    searchBooks: suspend (String) -> List<BookSummary>,
    localBooksLoader: suspend () -> List<BookSummary>,
    libraryBooksPageLoader: suspend (String, Int, BookBrowseFilter) -> LibraryBooksPage,
    recentBooksPageLoader: suspend (String, HomeSection, Int) -> LibraryBooksPage = { _, _, _ -> LibraryBooksPage() },
    coverLoader: suspend (BookSummary) -> ByteArray?,
    bookDetailLoader: suspend (BookSummary) -> BookDetailInfo?,
    epubImageLibrarySourceLoader: suspend (BookSummary, Boolean) -> EpubImageLibrarySourceResult =
        { _, _ -> EpubImageLibrarySourceResult.Unavailable("") },
    onDownloadForEpubImageLibrary: (BookSummary, (BookSummary) -> Unit) -> Unit = { _, _ -> },
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
    userRatingAvailable: Boolean = true,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    libraryBookCatalogAvailable: Boolean = true,
    seriesCatalogAvailable: Boolean = true,
    smartScopesLoader: suspend () -> List<SmartScope> = { emptyList() },
    smartScopesAvailable: Boolean = true,
    smartScopeSeriesCatalogLoader: suspend (Long, SeriesCatalogFilter, Int) -> SeriesCatalogPage = { _, _, _ -> SeriesCatalogPage() },
    smartScopeSeriesDetailLoader: suspend (Long, String) -> SeriesDetailInfo? = { _, _ -> null },
    authorsCatalogLoader: suspend (String?, Int) -> AuthorCatalogPage,
    authorsAvailable: Boolean = true,
    authorBooksLoader: suspend (String, Int) -> AuthorBooksPage?,
    annotationsLoader: suspend (AnnotationsFilter, Int) -> BookAnnotationsPage,
    onAnnotationSelected: (BookAnnotation) -> Unit = {},

    onUpdateAnnotation: suspend (annotation: BookAnnotation, note: String?, color: String?, style: String?) -> Boolean =
        { _, _, _, _ -> false },
    onTrashAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    onRestoreAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    onPurgeAnnotation: suspend (BookAnnotation) -> Boolean = { false },
    achievementsLoader: suspend () -> AchievementCatalogue,
    achievementsAvailable: Boolean = true,
    statisticsLoader: suspend () -> UserStatistics,
    statisticsAvailable: Boolean = true,
    catalogImageLoader: suspend (String) -> ByteArray?,
    onBookOpen: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onDownloadSingleFile: (BookSummary) -> Unit = onDownload,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onClearAllFailedDownloads: () -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDeleteSingleLocalCopy: (BookSummary) -> Unit = onDeleteLocalCopy,
    onDeleteLocalCopies: (List<BookSummary>) -> Unit,
    onDismissMessage: () -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    readingStatusOptions: List<BookReadStatus> = BOOK_READ_STATUS_OPTIONS,
    appPreferences: AppPreferences = AppPreferences(),
    onAppPreferencesChange: (AppPreferences) -> Unit = {},
    releaseCheckStatus: ReleaseCheckStatus = ReleaseCheckStatus.IDLE,
    onCheckForUpdates: () -> Unit = {},
    storageUsageLoader: suspend () -> StorageUsage = { StorageUsage() },
    onClearCache: suspend () -> Unit = {},
    offlineCacheAvailable: Boolean = true,
    offlineCacheStatusLoader: suspend () -> OfflineCacheStatus = { OfflineCacheStatus() },
    onStartOfflineCacheUpdate: suspend () -> Boolean = { false },
    onCancelOfflineCacheUpdate: suspend () -> Unit = {},
    onClearOfflineCache: suspend () -> Unit = {},
    bookDetailRequest: AudioBookDetailRequest? = null,
    onBookDetailRequestConsumed: (Long) -> Unit = {},
    bottomOverlay: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val hostNavigationBarInsetPx = remember(hostView) {
        ViewCompat.getRootWindowInsets(hostView)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            ?.bottom ?: 0
    }
    var destination by rememberSaveable {
        mutableStateOf(appPreferences.defaultOpeningScreen.toBrowserDestination())
    }
    LaunchedEffect(seriesCatalogAvailable, destination) {
        if (!seriesCatalogAvailable && destination == BrowserDestination.SERIES) {
            destination = BrowserDestination.LIBRARY
        }
    }
    LaunchedEffect(achievementsAvailable) {
        if (!achievementsAvailable && destination == BrowserDestination.ACHIEVEMENTS) {
            destination = BrowserDestination.HOME
        }
    }
    LaunchedEffect(statisticsAvailable) {
        if (!statisticsAvailable && destination == BrowserDestination.STATISTICS) {
            destination = BrowserDestination.HOME
        }
    }
    LaunchedEffect(smartScopesAvailable) {
        if (!smartScopesAvailable && destination == BrowserDestination.SMART_SCOPES) {
            destination = BrowserDestination.HOME
        }
    }
    LaunchedEffect(authorsAvailable) {
        if (!authorsAvailable && destination == BrowserDestination.AUTHORS) {
            destination = BrowserDestination.HOME
        }
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
    var selectedSmartScope by rememberSaveable { mutableStateOf<SmartScope?>(null) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableStateOf(LibraryTab.RECOMMENDED) }
    var browserRoute by rememberSaveable(stateSaver = BrowserRouteSaver) {
        mutableStateOf(BrowserRouteSnapshot())
    }
    var homeSectionReturnDestination by rememberSaveable { mutableStateOf(BrowserDestination.HOME) }
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
    var selectedSeriesName by remember { mutableStateOf<String?>(null) }
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
    var pendingCellularDownload by remember { mutableStateOf<PendingCellularDownload?>(null) }
    var pendingCellularBulkDownload by remember { mutableStateOf<PendingBulkDownload?>(null) }
    var showCellularDownloadBlocked by remember { mutableStateOf(false) }
    var pendingLocalDelete by remember { mutableStateOf<BookSummary?>(null) }
    var pendingSingleLocalDelete by remember { mutableStateOf(false) }
    var localBooksLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeServerEditor by rememberSaveable { mutableStateOf(false) }

    var serverProfilesRevision by rememberSaveable { mutableIntStateOf(0) }
    var pendingServerRemoval by remember { mutableStateOf<ServerProfile?>(null) }
    val configuredServerProfiles = remember(state.serverUrl, serverProfilesRevision) { serverProfilesLoader() }
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
            CellularDownloadDecision.ASK -> pendingCellularDownload = PendingCellularDownload(
                book = book,
                scope = PendingCellularDownloadScope.GROUP
            )
            CellularDownloadDecision.BLOCK -> showCellularDownloadBlocked = true
        }
    }
    val requestSingleFileDownload: (BookSummary) -> Unit = { book ->
        when (
            cellularDownloadDecision(
                policy = appPreferences.cellularDownloadPolicy,
                isCellularOrMetered = context.isActiveCellularOrMeteredNetwork()
            )
        ) {
            CellularDownloadDecision.START -> onDownloadSingleFile(book)
            CellularDownloadDecision.ASK -> pendingCellularDownload = PendingCellularDownload(
                book = book,
                scope = PendingCellularDownloadScope.SINGLE_FILE
            )
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
            pendingSingleLocalDelete = false
            pendingLocalDelete = book
        } else {
            onDeleteLocalCopy(book)
        }
    }
    val requestSingleLocalDelete: (BookSummary) -> Unit = { book ->
        if (appPreferences.confirmDeleteLocalCopy) {
            pendingSingleLocalDelete = true
            pendingLocalDelete = book
        } else {
            onDeleteSingleLocalCopy(book)
        }
    }
    val openHomeSection: (HomeSection, BrowserDestination) -> Unit = { section, returnDestination ->
        homeSectionReturnDestination = returnDestination
        destination = section.toBrowserDestination()
        selectedBook = null
        selectedSeriesKey = null
        selectedAuthor = null
    }
    val sessionActionLabel = if (state.isOfflineSnapshot) {
        stringResource(R.string.common_sign_in)
    } else {
        stringResource(R.string.browse_log_out)
    }
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
    val openFaq = {
        showProfileMenu = false
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/van-geaux/lagrange-reader/wiki/FAQ")
            )
        )
    }
    val openChangeServerEditor = {
        showProfileMenu = false
        showChangeServerEditor = true
    }

    BackHandler(enabled = showLibraryPicker || browserRootBackDestination(destination) != null || destination == BrowserDestination.SMART_SCOPES ||
        destination.toHomeSection() != null || destination in setOf(
            BrowserDestination.CURRENTLY_READING,
            BrowserDestination.ON_DECK,
            BrowserDestination.WANT_TO_READ
        ) || isSearchOpen || activeBookGenre != null || activeSeriesGenre != null || selectedBook != null || selectedSeriesKey != null || selectedAuthor != null) {
        if (isSearchOpen) {
            isSearchOpen = false
            query = ""
        } else if (showLibraryPicker) {
            showLibraryPicker = false
        } else if (activeBookGenre != null) {
            activeBookGenre = null
            selectedBook = genreSourceBook
            genreSourceBook = null
        } else if (activeSeriesGenre != null) {
            activeSeriesGenre = null
            selectedSeriesKey = genreSourceSeriesKey
            genreSourceSeriesKey = null
        } else if (destination == BrowserDestination.SMART_SCOPES) {
            destination = smartScopePickerBackDestination()
        } else if (destination.toHomeSection() != null || destination in setOf(
                BrowserDestination.CURRENTLY_READING,
                BrowserDestination.ON_DECK,
                BrowserDestination.WANT_TO_READ
            )) {
            destination = homeSectionReturnDestination
        } else if (selectedBook != null) {
            selectedBook = null
        } else if (selectedAuthor != null) {
            selectedAuthor = null
            destination = BrowserDestination.AUTHORS
        } else if (browserRootBackDestination(destination) != null) {
            destination = BrowserDestination.HOME
        } else {
            selectedSeriesKey = null
            destination = detailReturnDestination
        }
    }

    if (showMoreMenu) {
        val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            sheetState = moreSheetState,
            modifier = Modifier
                .padding(bottom = with(LocalDensity.current) {
                    moreMenuSheetBottomPaddingPx(hostNavigationBarInsetPx).toDp()
                })
                .testTag("more-menu-sheet"),
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            MoreMenu(
                seriesAvailable = seriesCatalogAvailable,
                smartScopesAvailable = smartScopesAvailable,
                authorsAvailable = authorsAvailable,
                onSeries = {
                    showMoreMenu = false
                    selectedBook = null
                    selectedSmartScope = smartScopeSelectionAfterOpeningUnscopedSeries()
                    destination = BrowserDestination.SERIES
                    query = ""
                    selectedAuthor = null
                    selectedSeriesKey = null
                },
                onSmartScopes = {
                    showMoreMenu = false
                    selectedBook = null
                    selectedSeriesKey = null
                    destination = smartScopeEntryDestination(selectedSmartScope)
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

    pendingCellularDownload?.let { pending ->
        val book = pending.book
        val isUpdate = book.hasDownloadUpdate
        AlertDialog(
            onDismissRequest = { pendingCellularDownload = null },
            title = {
                Text(
                    if (isUpdate) {
                        stringResource(R.string.browse_update_over_cellular_title)
                    } else {
                        stringResource(R.string.browse_download_over_cellular_title)
                    }
                )
            },
            text = {
                val actionLabel = stringResource(
                    if (isUpdate) R.string.browse_updating else R.string.browse_downloading
                )
                Text(
                    stringResource(
                        R.string.browse_cellular_message,
                        actionLabel,
                        book.title
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCellularDownload = null
                    routePendingCellularDownload(
                        pending = pending,
                        onDownload = onDownload,
                        onDownloadSingleFile = onDownloadSingleFile
                    )
                }) {
                    Text(
                        if (isUpdate) {
                            stringResource(R.string.browse_update_local)
                        } else {
                            stringResource(R.string.browse_download)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularDownload = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    pendingCellularBulkDownload?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingCellularBulkDownload = null },
            title = { Text(stringResource(R.string.browse_download_over_cellular_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.browse_cellular_bulk_message,
                        pending.books.size,
                        pending.scopeLabel
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCellularBulkDownload = null
                        pending.books.forEach(onDownload)
                    },
                    modifier = Modifier.testTag("confirm-bulk-cellular-download")
                ) { Text(stringResource(R.string.browse_download)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularBulkDownload = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    if (showCellularDownloadBlocked) {
        AlertDialog(
            onDismissRequest = { showCellularDownloadBlocked = false },
            title = { Text(stringResource(R.string.browse_cellular_disabled)) },
            text = { Text(stringResource(R.string.browse_cellular_disabled_summary)) },
            confirmButton = {
                TextButton(onClick = { showCellularDownloadBlocked = false }) { Text(stringResource(R.string.browse_ok)) }
            }
        )
    }
    pendingLocalDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingLocalDelete = null },
            title = { Text(stringResource(R.string.browse_delete_local_copy_title)) },
            text = {
                Text(stringResource(R.string.browse_remove_book_message, book.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLocalDelete = null
                        if (pendingSingleLocalDelete) {
                            onDeleteSingleLocalCopy(book)
                        } else {
                            onDeleteLocalCopy(book)
                        }
                        pendingSingleLocalDelete = false
                    },
                    modifier = Modifier.testTag("confirm-delete-local-copy")
                ) { Text(stringResource(R.string.browse_delete_local)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    if (showChangeServerEditor) {
        AlertDialog(
            onDismissRequest = { showChangeServerEditor = false },
            title = { Text(stringResource(R.string.common_change_server)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.browse_choose_server))
                    Button(
                        onClick = {
                            showChangeServerEditor = false
                            onNewServer()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.browse_new_server)) }
                    configuredServerProfiles.forEach { profile ->
                            val isCurrent = profile.id == state.profileId
                            ConfiguredServerCard(
                                profile = profile,
                                isCurrent = isCurrent,
                                enabled = !isCurrent,
                                onClick = {
                                    showChangeServerEditor = false
                                    onSwitchServer(profile.id)
                                },
                                onRemove = if (isCurrent) null else { { pendingServerRemoval = profile } }
                            )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChangeServerEditor = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    pendingServerRemoval?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingServerRemoval = null },
            title = { Text(stringResource(R.string.browse_remove_server_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.browse_remove_server_message,
                        profile.displayName
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingServerRemoval = null
                        onRemoveServer(profile.id)
                        serverProfilesRevision += 1
                    },
                    modifier = Modifier.testTag("confirm-remove-server")
                ) { Text(stringResource(R.string.browse_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingServerRemoval = null }) { Text(stringResource(R.string.common_cancel)) }
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
                    title = stringResource(R.string.browse_search),
                    navigationIcon = {
                        TextButton(onClick = {
                            isSearchOpen = false
                            query = ""
                        }) { Text(stringResource(R.string.browse_back)) }
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
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                activeBookGenre != null -> BrowserTopBar(
                    title = stringResource(
                        R.string.browse_books_genre_title,
                        activeBookGenre!!
                    ),
                    navigationIcon = {
                        TextButton(onClick = {
                            activeBookGenre = null
                            selectedBook = genreSourceBook
                            genreSourceBook = null
                        }) { Text(stringResource(R.string.browse_back)) }
                    },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = { showProfileMenu = false; if (state.isOfflineSnapshot) onSignIn() else onSignOut() },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                activeSeriesGenre != null -> BrowserTopBar(
                    title = stringResource(
                        R.string.browse_series_genre_title,
                        activeSeriesGenre!!
                    ),
                    navigationIcon = {
                        TextButton(onClick = {
                            activeSeriesGenre = null
                            selectedSeriesKey = genreSourceSeriesKey
                            genreSourceSeriesKey = null
                        }) { Text(stringResource(R.string.browse_back)) }
                    },
                    onSearch = { isSearchOpen = true },
                    onProfile = { showProfileMenu = true },
                    profileExpanded = showProfileMenu,
                    onDismissProfile = { showProfileMenu = false },
                    onSessionAction = { showProfileMenu = false; if (state.isOfflineSnapshot) onSignIn() else onSignOut() },
                    sessionActionLabel = sessionActionLabel,
                    onOptions = openOptions,
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedBook == null && destination.toHomeSection() != null -> BrowserTopBar(
                    title = stringResource(destination.toHomeSection()!!.titleRes),
                    navigationIcon = {
                        TextButton(onClick = { destination = homeSectionReturnDestination }) { Text(stringResource(R.string.browse_back)) }
                    },
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
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedBook != null -> BrowserTopBar(
                    title = stringResource(R.string.browse_book_details),
                    navigationIcon = { TextButton(onClick = { selectedBook = null }) { Text(stringResource(R.string.browse_back)) } },
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
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedSeriesKey != null -> BrowserTopBar(
                    title = stringResource(R.string.browse_series),
                    navigationIcon = { TextButton(onClick = { selectedSeriesKey = null }) { Text(stringResource(R.string.browse_back)) } },
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
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                selectedAuthor != null -> BrowserTopBar(
                    title = stringResource(R.string.browse_author),
                    navigationIcon = { TextButton(onClick = { selectedAuthor = null; destination = BrowserDestination.AUTHORS }) { Text(stringResource(R.string.browse_back)) } },
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
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
                    onAbout = openAbout,
                    onChangeServer = openChangeServerEditor
                )
                else -> BrowserTopBar(
                    title = when {
                        destination == BrowserDestination.LIBRARY && !showLibraryPicker ->
                            state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name
                                ?: stringResource(R.string.browse_library)
                        destination == BrowserDestination.LIBRARY -> stringResource(R.string.browse_libraries)
                        destination == BrowserDestination.SERIES && selectedSmartScope != null -> selectedSmartScope!!.name
                        destination == BrowserDestination.SERIES -> stringResource(R.string.browse_series)
                        destination == BrowserDestination.SMART_SCOPES -> stringResource(R.string.browse_smart_scopes)
                        destination == BrowserDestination.AUTHORS -> stringResource(R.string.browse_authors)
                        destination == BrowserDestination.ANNOTATIONS -> stringResource(R.string.browse_annotations)
                        destination == BrowserDestination.LOCAL_BOOKS -> stringResource(R.string.browse_local_books)
                        destination == BrowserDestination.ACHIEVEMENTS -> stringResource(R.string.browse_achievements)
                        destination == BrowserDestination.OPTIONS -> stringResource(R.string.browse_options)
                        destination == BrowserDestination.ABOUT -> stringResource(R.string.browse_about)
                        else -> stringResource(R.string.browse_home)
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
                    onStatistics = if (statisticsAvailable) openStatistics else ({}),
                    showStatistics = statisticsAvailable,
                    onAchievements = if (achievementsAvailable) openAchievements else ({}),
                    showAchievements = achievementsAvailable,
                    onFaq = openFaq,
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
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus
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
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus
                )
                activeSeriesGenre != null -> SeriesCatalogScreen(
                    query = "",
                    initialFilter = SeriesCatalogFilter(genre = activeSeriesGenre),
                    persistenceKey = "series-genre:${selectedSmartScope?.id ?: "all"}:$activeSeriesGenre",
                    appPreferences = appPreferences,
                    onAppPreferencesChange = onAppPreferencesChange,
                    libraryOptions = state.libraries,
                    selectedScope = selectedSmartScope,
                    books = state.homeBooks,
                    modifier = Modifier.padding(padding),
                    loader = seriesCatalogLoader,
                    scopeLoader = smartScopeSeriesCatalogLoader,
                    imageLoader = catalogImageLoader,
                    onSeriesSelected = { series ->
                        selectedSeriesKey = series.id
                        selectedSeriesName = series.name
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
                    epubImageLibrarySourceLoader = epubImageLibrarySourceLoader,
                    onDownloadForEpubImageLibrary = onDownloadForEpubImageLibrary,
                    appPreferences = appPreferences,
                    sessionHistoryLoader = sessionHistoryLoader,
                    onSessionHistoryEntryClick = onSessionHistoryEntryClick,
                    onClearSessionHistory = onClearSessionHistory,
                    serverReadingSessionsLoader = serverReadingSessionsLoader,
                    serverReadingAttemptsLoader = serverReadingAttemptsLoader,
                    onBookUserRatingChange = onBookUserRatingChange,
                    userRatingAvailable = userRatingAvailable,
                    seriesDetailLoader = seriesDetailLoader,
                    onRead = onBookOpen,
                    onPreview = onPreview,
                    onDownload = requestDownload,
                    onDownloadSingleFile = requestSingleFileDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onDeleteSingleLocalCopy = requestSingleLocalDelete,
                    onMarkAsStatus = onMarkAsStatus ?: { detailBook, status ->
                        when (status) {
                            BookReadStatus.READ -> onMarkAsRead(detailBook)
                            BookReadStatus.UNREAD -> onMarkAsUnread(detailBook)
                            else -> Unit
                        }
                    },
                    readingStatusOptions = readingStatusOptions,
                    onSeriesSelected = { seriesKey ->
                        selectedSeriesKey = seriesKey
                        selectedSeriesName = selectedBook?.seriesName
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
                selectedSeriesKey != null && destination != BrowserDestination.SMART_SCOPES -> SeriesDetails(
                    seriesKey = selectedSeriesKey!!,
                    seriesNameHint = selectedSeriesName,
                    books = (state.books + state.homeBooks).distinctBy { it.id to it.fileId },
                    libraries = state.libraries,
                    groupingMode = appPreferences.seriesGroupingMode,
                    onGroupingModeChange = { mode ->
                        onAppPreferencesChange(appPreferences.copy(seriesGroupingMode = mode))
                    },
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    detailLoader = if (selectedSmartScope == null) {
                        seriesDetailLoader
                    } else {
                        { seriesId -> smartScopeSeriesDetailLoader(selectedSmartScope!!.id, seriesId) }
                    },
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
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus
                )
                destination == BrowserDestination.SMART_SCOPES -> SmartScopesScreen(
                    loader = smartScopesLoader,
                    modifier = Modifier.padding(padding),
                    selectedScope = selectedSmartScope,
                    onSelected = { selectedSmartScope = it; destination = BrowserDestination.SERIES }
                )
                destination == BrowserDestination.SERIES -> SeriesCatalogScreen(
                    query = "",
                    initialFilter = SeriesCatalogFilter(),
                    persistenceKey = "series:${selectedSmartScope?.id ?: "all"}",
                    appPreferences = appPreferences,
                    onAppPreferencesChange = onAppPreferencesChange,
                    libraryOptions = state.libraries,
                    selectedScope = selectedSmartScope,
                    books = state.homeBooks,
                    loadAllPages = false,
                    modifier = Modifier.padding(padding),
                    loader = seriesCatalogLoader,
                    scopeLoader = smartScopeSeriesCatalogLoader,
                    imageLoader = catalogImageLoader,
                    onSeriesSelected = { series ->
                        selectedSeriesKey = series.id
                        selectedSeriesName = series.name
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
                    onMarkAsUnread = onMarkAsUnread,
                    appPreferences = appPreferences,
                    onAppPreferencesChange = onAppPreferencesChange
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
                    serverUrl = state.serverUrl,
                    profileId = state.profileId,
                    homeShelfPreferences = state.homeShelfPreferences,
                    onPreferencesChange = onAppPreferencesChange,
                    storageUsageLoader = storageUsageLoader,
                    onClearCache = onClearCache,
                    offlineCacheAvailable = offlineCacheAvailable,
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
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus
                )
                destination.toHomeSection() in setOf(
                    HomeSection.RECENTLY_ADDED_SERIES,
                    HomeSection.RECENTLY_UPDATED_SERIES
                ) -> if (state.homeShelves.isServerProvided) {
                    SeriesCatalogScreen(
                        query = "",
                        initialFilter = SeriesCatalogFilter(
                            libraryId = state.selectedLibraryId.takeUnless {
                                homeSectionReturnDestination == BrowserDestination.HOME
                            },
                            sort = SeriesSortOption.LAST_ADDED,
                            direction = SortDirection.DESCENDING,
                            homeSection = destination.toHomeSection()
                        ),
                        persistenceKey = "home-series:${destination.toHomeSection()}:${state.selectedLibraryId}",
                        appPreferences = appPreferences,
                        onAppPreferencesChange = onAppPreferencesChange,
                        libraryOptions = state.libraries,
                        books = emptyList(),
                        loadAllPages = false,
                        showJumpRail = false,
                        enablePagination = false,
                        modifier = Modifier.padding(padding),
                        loader = seriesCatalogLoader,
                        imageLoader = catalogImageLoader,
                        onSeriesSelected = { series ->
                            selectedSeriesKey = series.id
                            selectedSeriesName = series.name
                            detailReturnDestination = destination
                        }
                    )
                } else HomeSeriesSectionScreen(
                    section = destination.toHomeSection()!!,
                    books = homeSectionSourceBooks(
                        state = state,
                        section = destination.toHomeSection()!!,
                        serverWide = homeSectionReturnDestination == BrowserDestination.HOME
                    ),
                    state = state,
                    serverWide = homeSectionReturnDestination == BrowserDestination.HOME &&
                        !state.homeShelves.isServerProvided,
                    recentBooksPageLoader = recentBooksPageLoader,
                    seriesCatalogLoader = seriesCatalogLoader,
                    modifier = Modifier.padding(padding),
                    imageLoader = catalogImageLoader,
                    onSeriesSelected = { series ->
                        selectedSeriesKey = series.id
                        detailReturnDestination = destination
                    }
                )
                destination.toHomeSection() != null -> HomeSectionScreen(
                    section = destination.toHomeSection()!!,
                    books = homeSectionSourceBooks(
                        state = state,
                        section = destination.toHomeSection()!!,
                        serverWide = homeSectionReturnDestination == BrowserDestination.HOME
                    ),
                    state = state,
                    localOnDeckEnabled = appPreferences.showOnDeckWhenShelfSyncDisabled,
                    serverWide = homeSectionReturnDestination == BrowserDestination.HOME,
                    recentBooksPageLoader = recentBooksPageLoader,
                    modifier = Modifier.padding(padding),
                    coverLoader = coverLoader,
                    onBookSelected = { book ->
                        detailReturnDestination = destination
                        selectedBook = book
                    },
                    onDownload = requestDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = requestLocalDelete,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus
                )
                destination == BrowserDestination.HOME -> RefreshableHomeFeed(
                    state = state,
                    localOnDeckEnabled = appPreferences.showOnDeckWhenShelfSyncDisabled,
                    modifier = Modifier.padding(padding),
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    localBooksLoader = localBooksLoader,
                    seriesCatalogLoader = seriesCatalogLoader,
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
                    onMarkAsStatus = onMarkAsStatus,
                    onLocalBooksSelected = {
                        localBooksLibraryId = null
                        destination = BrowserDestination.LOCAL_BOOKS
                    },
                    onHomeSectionSelected = { section ->
                        openHomeSection(section, BrowserDestination.HOME)
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
                    onLoadMoreLibraryBooks = onLoadMoreLibraryBooks,
                    libraryBookCatalogAvailable = libraryBookCatalogAvailable,
                    seriesCatalogLoader = seriesCatalogLoader,
                    catalogImageLoader = catalogImageLoader,
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
                    onSeriesNameSelected = { seriesName ->
                        selectedSeriesName = seriesName
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
                    onMarkAsStatus = onMarkAsStatus,
                    appPreferences = appPreferences,
                    onAppPreferencesChange = onAppPreferencesChange,
                    onLocalBooksSelected = {
                        localBooksLibraryId = state.selectedLibraryId
                        destination = BrowserDestination.LOCAL_BOOKS
                    },
                    onHomeSectionSelected = { section ->
                        openHomeSection(section, BrowserDestination.LIBRARY)
                    }
                )
                else -> HomeFeed(
                    state = state,
                    localOnDeckEnabled = appPreferences.showOnDeckWhenShelfSyncDisabled,
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
                    onMarkAsUnread = onMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus
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
    showStatistics: Boolean = true,
    onAchievements: () -> Unit = {},
    showAchievements: Boolean = true,
    onFaq: () -> Unit = {},
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
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_search))
                }
            }
            Box {
                IconButton(onClick = onProfile) {
                    Icon(Icons.Default.Person, contentDescription = stringResource(R.string.browse_user_profile))
                }
                DropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = onDismissProfile
                ) {
                    if (showStatistics) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_statistics)) },
                            leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null) },
                            onClick = {
                                onDismissProfile()
                                onStatistics()
                            }
                        )
                    }
                    if (showAchievements) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_achievements)) },
                            leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                            onClick = {
                                onDismissProfile()
                                onAchievements()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browse_faq)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                        onClick = {
                            onDismissProfile()
                            onFaq()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browse_options)) },
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.browse_cd_options))
                        },
                        onClick = {
                            onDismissProfile()
                            onOptions()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browse_about)) },
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
                        text = { Text(stringResource(R.string.common_change_server)) },
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
            label = { Text(stringResource(R.string.browse_home)) }
        )
        NavigationBarItem(
            selected = destination == BrowserDestination.LIBRARY,
            onClick = onLibraries,
            icon = { Icon(Icons.Default.LocalLibrary, contentDescription = stringResource(R.string.browse_cd_libraries)) },
            label = { Text(stringResource(R.string.browse_libraries)) }
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
            label = { Text(stringResource(R.string.browse_more)) }
        )
    }
}

@Composable
private fun MoreMenu(
    seriesAvailable: Boolean = true,
    smartScopesAvailable: Boolean = true,
    authorsAvailable: Boolean = true,
    onSeries: () -> Unit,
    onSmartScopes: () -> Unit,
    onAuthors: () -> Unit,
    onAnnotations: () -> Unit,
    onLocalBooks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 72.dp)
    ) {
        Text(
            stringResource(R.string.browse_more),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall
        )
        if (seriesAvailable) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.browse_series)) },
                leadingContent = { Icon(Icons.Default.CollectionsBookmark, contentDescription = stringResource(R.string.browse_cd_series)) },
                modifier = Modifier.clickable(onClick = onSeries)
            )
        }
        if (smartScopesAvailable) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.browse_smart_scopes)) },
                leadingContent = { Icon(Icons.Default.CollectionsBookmark, contentDescription = stringResource(R.string.browse_cd_smart_scopes)) },
                modifier = Modifier.clickable(onClick = onSmartScopes)
            )
        }
        if (authorsAvailable) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.browse_authors)) },
                leadingContent = { Icon(Icons.Default.Groups, contentDescription = stringResource(R.string.browse_cd_authors)) },
                modifier = Modifier.clickable(onClick = onAuthors)
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.browse_annotations)) },
            leadingContent = { Icon(Icons.Default.Highlight, contentDescription = stringResource(R.string.browse_cd_annotations)) },
            modifier = Modifier.clickable(onClick = onAnnotations)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.browse_local_books)) },
            leadingContent = { Icon(Icons.Default.DownloadForOffline, contentDescription = stringResource(R.string.browse_cd_local_books)) },
            modifier = Modifier
                .testTag("more-menu-last-item")
                .clickable(onClick = onLocalBooks)
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    onClearFailedDownload: (BookSummary) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )
        if (query.isBlank()) {
            Text(
                stringResource(R.string.browse_search_summary),
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
                onMarkAsStatus = onMarkAsStatus,
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
            Text(stringResource(R.string.browse_choose_library), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.browse_choose_library_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.isLoadingLibraries) {
            item { LoadingFeedRow(stringResource(R.string.browse_loading_libraries)) }
        }
        if (!state.isLoadingLibraries && state.libraries.isEmpty()) {
            item { Text(stringResource(R.string.browse_no_libraries_found), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun SmartScopesScreen(
    loader: suspend () -> List<SmartScope>,
    modifier: Modifier,
    selectedScope: SmartScope?,
    onSelected: (SmartScope) -> Unit
) {
    var scopes by remember { mutableStateOf<List<SmartScope>?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { loader() }
            .onSuccess { loadedScopes ->
                scopes = loadedScopes
                selectDefaultSmartScope(loadedScopes, selectedScope)?.let { defaultScope ->
                    if (selectedScope == null) onSelected(defaultScope)
                }
            }
            .onFailure { error = true }
    }
    LazyColumn(modifier = modifier.fillMaxSize().testTag("smart_scopes_screen"), contentPadding = PaddingValues(16.dp)) {
        item { Text(stringResource(R.string.browse_smart_scopes), style = MaterialTheme.typography.headlineSmall) }
        when {
            scopes == null && !error -> item { LoadingFeedRow(stringResource(R.string.browse_loading_smart_scopes)) }
            error -> item { Text(stringResource(R.string.browse_smart_scopes_load_failed), color = MaterialTheme.colorScheme.error) }
            scopes.orEmpty().isEmpty() -> item { Text(stringResource(R.string.browse_no_smart_scopes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> items(scopes.orEmpty(), key = { "smart-scope-${it.id}" }) { scope ->
                ListItem(headlineContent = { Text(scope.name) }, modifier = Modifier.testTag("smart_scope_${scope.id}").clickable { onSelected(scope) })
            }
        }
    }
}

internal fun selectDefaultSmartScope(scopes: List<SmartScope>, selectedScope: SmartScope?): SmartScope? =
    selectedScope ?: scopes.firstOrNull()

internal fun smartScopeEntryDestination(selectedScope: SmartScope?): BrowserDestination =
    if (selectedScope == null) BrowserDestination.SMART_SCOPES else BrowserDestination.SERIES

internal fun smartScopePickerBackDestination(): BrowserDestination =
    BrowserDestination.SERIES

internal fun smartScopeSelectionAfterOpeningUnscopedSeries(): SmartScope? =
    null

internal fun smartScopeCatalogStateKey(selectedScope: SmartScope?): Long? =
    selectedScope?.id

@Composable
private fun SeriesCatalogScreen(
    query: String,
    initialFilter: SeriesCatalogFilter,
    persistenceKey: String,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit,
    libraryOptions: List<LibrarySummary>,
    selectedScope: SmartScope? = null,
    books: List<BookSummary> = emptyList(),
    loadAllPages: Boolean = true,
    showJumpRail: Boolean = true,
    enablePagination: Boolean = true,
    modifier: Modifier,
    loader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    scopeLoader: suspend (Long, SeriesCatalogFilter, Int) -> SeriesCatalogPage = { _, _, _ -> SeriesCatalogPage() },
    imageLoader: suspend (String) -> ByteArray?,
    onSeriesSelected: (SeriesSummary) -> Unit
) {
    val selectedScopeId = smartScopeCatalogStateKey(selectedScope)
    val lockedFilter = appPreferences.lockedSeriesFilters[persistenceKey]
        ?.let(::seriesCatalogFilterFromStorage)
    var items by remember(query, initialFilter, selectedScopeId) { mutableStateOf<List<SeriesSummary>>(emptyList()) }
    var jumpIndexItems by remember(query, initialFilter, selectedScopeId) { mutableStateOf<List<SeriesSummary>>(emptyList()) }
    var hasProviderJumpIndex by remember(query, initialFilter, selectedScopeId) { mutableStateOf(false) }
    var total by remember(query, initialFilter, selectedScopeId) { mutableStateOf(0) }
    var isLoading by remember(query, initialFilter, selectedScopeId) { mutableStateOf(false) }
    var isRefreshing by remember(query, initialFilter, selectedScopeId) { mutableStateOf(false) }
    var loadError by remember(query, initialFilter, selectedScopeId) { mutableStateOf(false) }
    var reloadKey by remember(query, initialFilter, selectedScopeId) { mutableIntStateOf(0) }
    var nextPage by remember(query, initialFilter, selectedScopeId) { mutableIntStateOf(1) }
    var seriesPageSize by remember(query, initialFilter, selectedScopeId) { mutableIntStateOf(20) }
    var hasMorePages by remember(query, initialFilter, selectedScopeId) { mutableStateOf(false) }
    var isLoadingMore by remember(query, initialFilter, selectedScopeId) { mutableStateOf(false) }
    var filter by remember(query, initialFilter, selectedScopeId) {
        mutableStateOf(
            (lockedFilter ?: initialFilter).copy(query = query.takeIf { it.isNotBlank() })
        )
    }
    var showFilter by rememberSaveable(query, initialFilter) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(query, filter, selectedScopeId) {
        isLoading = true
        loadError = false
        items = emptyList()
        jumpIndexItems = emptyList()
        hasProviderJumpIndex = false
        total = 0
        gridState.scrollToItem(0)
        if (query.isNotBlank()) delay(300)
        val activeFilter = filter.copy(query = query.takeIf { it.isNotBlank() })
        runCatching {
            if (loadAllPages) {
                loadCompleteSeriesCatalog { page ->
                    if (selectedScope != null) scopeLoader(selectedScope.id, activeFilter, page) else loader(activeFilter, page)
                }
            } else {
                if (selectedScope != null) scopeLoader(selectedScope.id, activeFilter, 0) else loader(activeFilter, 0)
            }
        }.onSuccess { catalog ->
            items = catalog.items
            jumpIndexItems = catalog.jumpIndex ?: catalog.items
            hasProviderJumpIndex = catalog.jumpIndex != null
            total = catalog.total ?: total.takeIf { it > 0 } ?: catalog.items.size
            nextPage = 1
            seriesPageSize = catalog.size ?: seriesPageSize
            hasMorePages = enablePagination && !loadAllPages && catalog.items.size >= (catalog.size ?: 0) && catalog.items.isNotEmpty()
        }.onFailure {
            loadError = true
        }
        isLoading = false
    }
    LaunchedEffect(reloadKey) {
        if (reloadKey == 0) return@LaunchedEffect
        try {
            val activeFilter = filter.copy(query = query.takeIf { it.isNotBlank() })
            val catalog = if (loadAllPages) {
                loadCompleteSeriesCatalog { page ->
                    if (selectedScope != null) scopeLoader(selectedScope.id, activeFilter, page) else loader(activeFilter, page)
                }
            } else {
                if (selectedScope != null) scopeLoader(selectedScope.id, activeFilter, 0) else loader(activeFilter, 0)
            }
            if (catalog.items.isNotEmpty() || items.isEmpty()) {
                items = catalog.items
                jumpIndexItems = catalog.jumpIndex ?: jumpIndexItems
                hasProviderJumpIndex = catalog.jumpIndex != null || hasProviderJumpIndex
                total = catalog.total ?: total.takeIf { it > 0 } ?: catalog.items.size
                nextPage = 1
                seriesPageSize = catalog.size ?: seriesPageSize
                hasMorePages = enablePagination && !loadAllPages && catalog.items.size >= (catalog.size ?: 0) && catalog.items.isNotEmpty()
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
    suspend fun loadNextPage(includeCounts: Boolean = true): Boolean {
        if (!enablePagination || loadAllPages || !hasMorePages || isLoading || isLoadingMore) return false
        isLoadingMore = true
        return try {
            val activeFilter = filter.copy(
                query = query.takeIf { it.isNotBlank() },
                includeCounts = includeCounts
            )
            val catalog = runCatching {
                if (selectedScope != null) scopeLoader(selectedScope.id, activeFilter, nextPage)
                else loader(activeFilter, nextPage)
            }.getOrNull() ?: return false
            items = (items + catalog.items).distinctBy { it.id }
            total = catalog.total ?: total.takeIf { it > 0 } ?: items.size
            seriesPageSize = catalog.size ?: seriesPageSize
            nextPage += 1
            hasMorePages = catalog.items.size >= (catalog.size ?: 0) && catalog.items.isNotEmpty()
            catalog.items.isNotEmpty()
        } finally {
            isLoadingMore = false
        }
    }
    LaunchedEffect(loadAllPages, filter, selectedScopeId, hasMorePages) {
        if (!enablePagination || loadAllPages) return@LaunchedEffect
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (lastVisible, totalItems) ->
                hasMorePages && totalItems > 0 && lastVisible >= totalItems - 6
            }
            .collect { loadNextPage() }
    }
    suspend fun jumpToSeriesIndex(index: Int) {
        val targetLabel = jumpIndexItems.getOrNull(index)?.let { libraryJumpLabel(it.name) }
        if (index < items.size) {
            val localIndex = targetLabel?.let { label -> items.indexOfFirst { libraryJumpLabel(it.name) == label } }
                ?.takeIf { it >= 0 } ?: index
            if (reduceMotion) gridState.scrollToItem(localIndex) else gridState.animateScrollToItem(localIndex)
            return
        }
        var targetPage = (index / seriesPageSize.coerceAtLeast(1)).coerceAtLeast(0)
        val activeFilter = filter.copy(query = query.takeIf { it.isNotBlank() }, includeCounts = false)
        suspend fun loadSummaryPage(page: Int): SeriesCatalogPage? = runCatching {
            if (selectedScope != null) scopeLoader(selectedScope.id, activeFilter, page)
            else loader(activeFilter, page)
        }.getOrNull()
        var catalog = loadSummaryPage(targetPage) ?: return
        if (targetLabel != null && catalog.items.none { libraryJumpLabel(it.name) == targetLabel }) {
            val next = loadSummaryPage(targetPage + 1)
            if (next?.items?.any { libraryJumpLabel(it.name) == targetLabel } == true) {
                targetPage += 1
                catalog = next
            }
        }
        if (catalog.items.isEmpty()) return
        items = catalog.items
        seriesPageSize = catalog.size ?: seriesPageSize
        nextPage = targetPage + 1
        hasMorePages = catalog.items.size >= (catalog.size ?: 0) && catalog.items.isNotEmpty()
        val localIndex = targetLabel?.let { label -> catalog.items.indexOfFirst { libraryJumpLabel(it.name) == label } }
            ?.takeIf { it >= 0 }
            ?: (index % seriesPageSize.coerceAtLeast(1)).coerceIn(0, catalog.items.lastIndex)
        if (reduceMotion) gridState.scrollToItem(localIndex) else gridState.animateScrollToItem(localIndex)
        scope.launch {
            val enriched = runCatching {
                val countFilter = filter.copy(query = query.takeIf { it.isNotBlank() }, includeCounts = true)
                if (selectedScope != null) scopeLoader(selectedScope.id, countFilter, targetPage)
                else loader(countFilter, targetPage)
            }.getOrNull() ?: return@launch
            val enrichedById = enriched.items.associateBy { it.id }
            items = items.map { enrichedById[it.id] ?: it }
        }
    }

    suspend fun jumpToSeriesLabel(target: Char) {
        if (loadAllPages) return
        var targetPage = if (items.any { libraryJumpLabel(it.name) == target }) 0 else -1
        while (targetPage < 0 && hasMorePages) {
            val pageToLoad = nextPage
            if (!loadNextPage(includeCounts = false)) break
            if (items.any { libraryJumpLabel(it.name) == target }) targetPage = pageToLoad
        }
        if (targetPage < 0) return
        val pagesToEnrich = (targetPage - 1..targetPage + 1).filter { it >= 0 && it < nextPage }
        pagesToEnrich.forEach { page ->
            val enriched = runCatching {
                loader(filter.copy(includeCounts = true), page)
            }.getOrNull() ?: return@forEach
            val enrichedById = enriched.items.associateBy { it.id }
            items = items.map { enrichedById[it.id] ?: it }
        }
        val index = items.indexOfFirst { libraryJumpLabel(it.name) == target }
        if (index >= 0) {
            if (reduceMotion) gridState.scrollToItem(index) else gridState.animateScrollToItem(index)
        }
    }
    val displayItems = remember(items, books) { enrichSeriesAvailableFormats(items, books) }
    val jumpTargets = remember(jumpIndexItems, filter.sort, filter.direction, isLoading) {
        if (!isLoading && filter.sort == SeriesSortOption.NAME) {
            buildSeriesJumpTargets(jumpIndexItems, filter.direction)
        } else {
            emptyList()
        }
    }

    val hasJumpRail = showJumpRail && !isLoading && (jumpTargets.isNotEmpty() || (!loadAllPages && displayItems.isNotEmpty()))
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
                if (selectedScope != null) {
                    stringResource(R.string.browse_scope_series_count, selectedScope.name, total)
                } else if (total > 0) {
                    stringResource(R.string.browse_series_count, total)
                } else {
                    stringResource(R.string.browse_browse_every_series)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showFilter = true }) {
                Text(
                    if (filter.isActive) {
                        stringResource(R.string.browse_filter_active)
                    } else {
                        stringResource(R.string.browse_filter)
                    }
                )
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
        if (isLoading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow(stringResource(R.string.browse_loading_series)) }
        if (!isLoading && items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (loadError) {
                        stringResource(R.string.browse_series_load_failed)
                    } else {
                        stringResource(R.string.browse_no_series)
                    },
                    color = if (loadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        gridItems(displayItems, key = { "catalog-series-${it.id}" }) { series ->
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
                        label = stringResource(R.string.browse_cover_for, series.name),
                        loader = imageLoader,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                    )
                    AvailableFormatsLabel(
                        series.availableFormats,
                        "series-format-label-${series.id}"
                    )
                    Text(series.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (series.authors.isNotEmpty()) {
                        Text(series.authors.joinToString(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        stringResource(
                            R.string.browse_series_books_read,
                            series.bookCount,
                            series.readCount
                        ),
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
            enabledLabels = if (!loadAllPages && !hasProviderJumpIndex) {
                catalogJumpRailLabels(filter.direction).toSet()
            } else {
                jumpTargets.map { it.first }.toSet()
            },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = 12.dp),
                onJump = { index ->
                    scope.launch {
                        jumpToSeriesIndex(index)
                    }
                },
                onUnavailableJump = if (!loadAllPages && !hasProviderJumpIndex) {
                    { label -> scope.launch { jumpToSeriesLabel(label) } }
                } else null
            )
        }
        }
        }
    }

    if (showFilter) {
        SeriesFilterSheet(
            initial = filter,
            initialLocked = appPreferences.lockedSeriesFilters.containsKey(persistenceKey),
            libraries = libraryOptions,
            onDismiss = { showFilter = false },
            onApply = { nextFilter, locked ->
                val nextFilters = appPreferences.lockedSeriesFilters.toMutableMap().apply {
                    if (locked) put(persistenceKey, nextFilter.toStorageValue()) else remove(persistenceKey)
                }
                onAppPreferencesChange(appPreferences.copy(lockedSeriesFilters = nextFilters))
                filter = nextFilter
                showFilter = false
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookFilterSheet(
    initial: BookBrowseFilter,
    initialLocked: Boolean,
    onDismiss: () -> Unit,
    onApply: (BookBrowseFilter, Boolean) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var locked by remember(initialLocked) { mutableStateOf(initialLocked) }
    val readStatusLabels = preferenceChoiceLabels(
        BookReadFilter.entries.toList(),
        BookReadFilter::labelRes
    )
    val formatLabels = preferenceChoiceLabels(
        BookFormatFilter.entries.toList(),
        BookFormatFilter::labelRes
    )
    val sortLabels = preferenceChoiceLabels(
        BookSortOption.entries.toList(),
        BookSortOption::labelRes
    )
    val directionLabels = preferenceChoiceLabels(
        SortDirection.entries.toList(),
        SortDirection::labelRes
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.browse_filter_books), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.browse_filter_books_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = draft.title.orEmpty(),
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.browse_title_contains)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.author.orEmpty(),
                onValueChange = { draft = draft.copy(author = it) },
                label = { Text(stringResource(R.string.browse_author_contains)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.series.orEmpty(),
                onValueChange = { draft = draft.copy(series = it) },
                label = { Text(stringResource(R.string.browse_series_contains)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.browse_reading_status), style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = BookReadFilter.entries,
                selected = draft.readStatus,
                label = { readStatusLabels.getValue(it) },
                onSelected = { draft = draft.copy(readStatus = it) }
            )
            Text(stringResource(R.string.browse_format), style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = BookFormatFilter.entries,
                selected = draft.format,
                label = { formatLabels.getValue(it) },
                onSelected = { draft = draft.copy(format = it) }
            )
            Text(stringResource(R.string.browse_sort), style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = BookSortOption.entries,
                selected = draft.sort,
                label = { sortLabels.getValue(it) },
                onSelected = { draft = draft.copy(sort = it) }
            )
            if (draft.sort != BookSortOption.SERVER_DEFAULT) {
                FilterChoiceRow(
                    options = SortDirection.entries,
                    selected = draft.direction,
                    label = { directionLabels.getValue(it) },
                    onSelected = { draft = draft.copy(direction = it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.browse_lock_filter), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.browse_lock_filter_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = locked, onCheckedChange = { locked = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = BookBrowseFilter() }) { Text(stringResource(R.string.browse_reset)) }
                Button(onClick = { onApply(draft, locked) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.browse_apply)) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SeriesFilterSheet(
    initial: SeriesCatalogFilter,
    initialLocked: Boolean,
    libraries: List<LibrarySummary>,
    onDismiss: () -> Unit,
    onApply: (SeriesCatalogFilter, Boolean) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var locked by remember(initialLocked) { mutableStateOf(initialLocked) }
    val completionLabels = preferenceChoiceLabels(
        SeriesCompletionFilter.entries.toList(),
        SeriesCompletionFilter::labelRes
    )
    val seriesSortLabels = preferenceChoiceLabels(
        SeriesSortOption.entries.toList(),
        SeriesSortOption::labelRes
    )
    val directionLabels = preferenceChoiceLabels(
        SortDirection.entries.toList(),
        SortDirection::labelRes
    )
    val allLibrariesLabel = stringResource(R.string.browse_all_libraries)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.browse_filter_series), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.browse_filter_series_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = draft.author.orEmpty(),
                onValueChange = { draft = draft.copy(author = it) },
                label = { Text(stringResource(R.string.browse_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.genre.orEmpty(),
                onValueChange = { draft = draft.copy(genre = it) },
                label = { Text(stringResource(R.string.browse_genre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (libraries.isNotEmpty()) {
                Text(stringResource(R.string.browse_library), style = MaterialTheme.typography.titleMedium)
                val libraryIds = listOf<String?>(null) + libraries.map { it.id }
                FilterChoiceRow(
                    options = libraryIds,
                    selected = draft.libraryId,
                    label = { id -> libraries.firstOrNull { it.id == id }?.name ?: allLibrariesLabel },
                    onSelected = { draft = draft.copy(libraryId = it) }
                )
            }
            Text(stringResource(R.string.browse_completion), style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = SeriesCompletionFilter.entries,
                selected = draft.completion,
                label = { completionLabels.getValue(it) },
                onSelected = { draft = draft.copy(completion = it) }
            )
            Text(stringResource(R.string.browse_sort), style = MaterialTheme.typography.titleMedium)
            FilterChoiceRow(
                options = SeriesSortOption.entries,
                selected = draft.sort,
                label = { seriesSortLabels.getValue(it) },
                onSelected = { draft = draft.copy(sort = it) }
            )
            FilterChoiceRow(
                options = SortDirection.entries,
                selected = draft.direction,
                label = { directionLabels.getValue(it) },
                onSelected = { draft = draft.copy(direction = it) }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.browse_lock_filter), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.browse_lock_filter_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = locked, onCheckedChange = { locked = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = SeriesCatalogFilter(query = draft.query) }) { Text(stringResource(R.string.browse_reset)) }
                Button(onClick = { onApply(draft, locked) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.browse_apply)) }
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
            if (total > 0) {
                stringResource(R.string.browse_authors_count, total)
            } else {
                stringResource(R.string.browse_browse_every_author)
            },
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
        if (isLoading) item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow(stringResource(R.string.browse_loading_authors)) }
        if (!isLoading && items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.browse_no_authors), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        label = stringResource(R.string.browse_cover_photo, author.name),
                        loader = imageLoader,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                    Text(author.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.browse_books_count, author.bookCount),
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
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?
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
                OrbitEyebrow(stringResource(R.string.browse_author))
                Text(author.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(
                        R.string.browse_books_count,
                        page?.total ?: author.bookCount
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (page == null) item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow(stringResource(R.string.browse_loading_books)) }
        if (page != null && page!!.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.browse_no_books), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onMarkAsUnread = { onMarkAsUnread(book) },
                onMarkAsStatus = onMarkAsStatus?.let { mark -> { statusBook, status -> mark(statusBook, status) } }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookAvailableFormatsLabel(
    book: BookSummary,
    modifier: Modifier = Modifier
) {
    val formats = bookAvailableFormatsForDisplay(book)
    val downloaded = downloadedFormatLabels(book).toSet()
    AvailableFormatsLabel(formats, "book-format-label-${book.id}", downloaded, modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AvailableFormatsLabel(
    formats: List<String>,
    testTagId: String,
    downloadedFormats: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    if (formats.isEmpty()) return
    val downloadedAltText = stringResource(R.string.browse_downloaded)
    val label = buildAnnotatedString {
        formats.forEachIndexed { index, format ->
            if (index > 0) append(" · ")
            if (format in downloadedFormats) {
                appendInlineContent("download-$format", downloadedAltText)
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                    append(format)
                }
            } else {
                append(format)
            }
        }
    }
    val inlineContent = downloadedFormats.associateWith { format ->
        "download-$format" to InlineTextContent(
            placeholder = Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
        }
    }.values.associate { (id, content) -> id to content }
    val availableFormatsDescription = stringResource(
        R.string.browse_available_formats_cd,
        formats.joinToString(", ")
    )
    val downloadedFormatsDescription = if (downloadedFormats.isNotEmpty()) {
        stringResource(
            R.string.browse_downloaded_locally_cd,
            downloadedFormats.joinToString(", ")
        )
    } else {
        null
    }
    Text(
        text = label,
        inlineContent = inlineContent,
        modifier = modifier
            .fillMaxWidth()
            .then(if (LocalReduceMotion.current) Modifier else Modifier.basicMarquee(iterations = Int.MAX_VALUE))
            .semantics {
                contentDescription = buildString {
                    append(availableFormatsDescription)
                    downloadedFormatsDescription?.let { append(" ").append(it) }
                }
            }
            .testTag(testTagId),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall
    )
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)? = null,
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
    val hasActions = enabled && (onMarkAsStatus != null || onMarkAsRead != null || onMarkAsUnread != null || (book.isDownloaded && onDeleteLocalCopy != null) || hasDownloadAction || isDownloading || downloadFailed)
    var showActions by remember(book.id) { mutableStateOf(false) }
    var showStatusMenu by remember(book.id) { mutableStateOf(false) }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.browse_selected)
    } else {
        null
    }
    val status = when {
        book.isLocalOnly -> stringResource(R.string.browse_local_only)
        book.isServerMissing -> stringResource(R.string.browse_missing_on_server)
        !enabled -> stringResource(R.string.browse_unavailable_offline)
        book.isRead && book.isDownloaded -> stringResource(R.string.browse_read_offline)
        book.isRead -> stringResource(R.string.browse_read)
        book.isDownloaded -> stringResource(R.string.browse_offline)
        book.progressPercent?.let { it > 0f } == true ->
            stringResource(R.string.browse_in_progress)
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
                selectedStateDescription?.let { stateDescription = it }
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
                                contentDescription = stringResource(R.string.browse_more_options_for, book.title)
                            )
                        }
                        DropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false }
                        ) {
                            onMarkAsStatus?.let { markAsStatus ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browse_mark_as)) },
                                    onClick = {
                                        showActions = false
                                        showStatusMenu = true
                                    }
                                )
                            }
                            if (onMarkAsStatus == null) onMarkAsRead?.let { markAsRead ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browse_mark_as_read)) },
                                    onClick = {
                                        showActions = false
                                        markAsRead()
                                    }
                                )
                            }
                            if (onMarkAsStatus == null) onMarkAsUnread?.let { markAsUnread ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browse_mark_as_unread)) },
                                    onClick = {
                                        showActions = false
                                        markAsUnread()
                                    }
                                )
                            }
                            if (isDownloading) {
                                onCancelDownload?.let { cancel ->
                                    DropdownMenuItem(text = { Text(stringResource(R.string.common_cancel)) }, onClick = { showActions = false; cancel() })
                                }
                            } else if (downloadFailed) {
                                if (!book.isServerMissing) onDownload?.let { retry ->
                                    DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (book.isDownloaded) {
                                                stringResource(R.string.browse_update_local)
                                            } else {
                                                stringResource(R.string.common_retry)
                                            }
                                        )
                                    },
                                    onClick = { showActions = false; retry() }
                                )
                                }
                                onClearFailedDownload?.let { clear ->
                                    DropdownMenuItem(text = { Text(stringResource(R.string.browse_clear)) }, onClick = { showActions = false; clear() })
                                }
                            } else if (!book.isDownloaded && !book.isServerMissing && downloadState?.isOfflineSnapshot != true) {
                                onDownload?.let { download ->
                                    DropdownMenuItem(text = { Text(stringResource(R.string.browse_download_local)) }, onClick = { showActions = false; download() })
                                }
                            }
                            if (book.isDownloaded) onDeleteLocalCopy?.let { deleteLocal -> DropdownMenuItem(text = { Text(stringResource(R.string.browse_delete_local)) }, onClick = { showActions = false; deleteLocal() }) }
                        }
                        onMarkAsStatus?.let { markAsStatus ->
                            BookDetailReadingStatusMenu(
                                expanded = showStatusMenu,
                                currentStatus = book.readStatus,
                                onDismissRequest = { showStatusMenu = false },
                                onStatusSelected = { status ->
                                    showStatusMenu = false
                                    markAsStatus(book, status)
                                }
                            )
                        }
                    }
                }
            }
            BookAvailableFormatsLabel(book)
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
private fun <T> preferenceChoiceLabels(
    values: List<T>,
    labelRes: (T) -> Int
): Map<T, String> {
    val labels = LinkedHashMap<T, String>(values.size)
    for (value in values) {
        labels[value] = stringResource(labelRes(value))
    }
    return labels
}

@Composable
internal fun OptionsScreen(
    preferences: AppPreferences,
    libraries: List<LibrarySummary> = emptyList(),
    serverUrl: String = "",
    profileId: String? = null,
    homeShelfPreferences: HomeShelfPreferences = HomeShelfPreferences(),
    onPreferencesChange: (AppPreferences) -> Unit,
    storageUsageLoader: suspend () -> StorageUsage = { StorageUsage() },
    onClearCache: suspend () -> Unit = {},
    offlineCacheAvailable: Boolean = true,
    offlineCacheStatusLoader: suspend () -> OfflineCacheStatus = { OfflineCacheStatus() },
    onStartOfflineCacheUpdate: suspend () -> Boolean = { false },
    onCancelOfflineCacheUpdate: suspend () -> Unit = {},
    onClearOfflineCache: suspend () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var openDialog by rememberSaveable { mutableStateOf<OptionsDialog?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<OptionsCategory?>(null) }
    BackHandler(enabled = selectedCategory != null) { selectedCategory = null }
    var subDestination by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = subDestination != null) { subDestination = null }
    var readingLibraryId by rememberSaveable {
        mutableStateOf(libraries.firstOrNull()?.id)
    }
    LaunchedEffect(libraries) {
        if (readingLibraryId !in libraries.map { it.id }) {
            readingLibraryId = libraries.firstOrNull()?.id
        }
    }
    var storageRefreshKey by rememberSaveable { mutableStateOf(0) }
    var isClearingCache by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var offlineCacheRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var automaticDownloadRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var showClearDownloadedBooks by remember { mutableStateOf(false) }
    var isClearingDownloadedBooks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var permissionRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionSpecs = remember(permissionRefreshKey) { appPermissionSpecs() }
    var languageTag by remember { mutableStateOf(AppLocale.readTag(context)) }
    val languageLabels = AppLocale.languages.associate { language ->
        language.tag to stringResource(language.labelRes)
    }
    val themeLabels = preferenceChoiceLabels(
        AppThemeMode.values().toList(),
        AppThemeMode::displayNameRes
    )
    val openingScreenLabels = preferenceChoiceLabels(
        DefaultOpeningScreen.values().toList(),
        DefaultOpeningScreen::displayNameRes
    )
    val libraryCardSizeLabels = preferenceChoiceLabels(
        LibraryCardSize.values().toList(),
        LibraryCardSize::displayNameRes
    )
    val cellularDownloadLabels = preferenceChoiceLabels(
        CellularDownloadPolicy.values().toList(),
        CellularDownloadPolicy::displayNameRes
    )
    val backgroundRefreshLabels = preferenceChoiceLabels(
        BackgroundRefreshNetworkPolicy.values().toList(),
        BackgroundRefreshNetworkPolicy::displayNameRes
    )
    val downloadStorageSelectionStore = remember(context) {
        DownloadStorageSelectionStore(context)
    }
    var downloadStorageSelection by remember {
        mutableStateOf(downloadStorageSelectionStore.read())
    }
    var downloadStorageMessage by remember { mutableStateOf<String?>(null) }
    val downloadFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { downloadStorageSelectionStore.select(uri) }
                }
                result.onSuccess { selection ->
                    downloadStorageSelection = selection
                    downloadStorageMessage = context.getString(
                        R.string.settings_download_location_new_downloads,
                        selection.displayName
                            ?: context.getString(R.string.settings_download_location_selected_folder)
                    )
                    storageRefreshKey += 1
                }.onFailure { error ->
                    downloadStorageMessage = error.message
                        ?: context.getString(R.string.settings_download_location_select_failed)
                }
            }
        }
    }
    val automaticProfileId = profileId ?: serverProfileId(serverUrl)
    val automaticStorageScopeId =
        downloadStorageScopeId(context, serverUrl, automaticProfileId) ?: "unresolved-account"
    val automaticOwner = AutomaticDownloadOwnerKey(
        profileId = automaticProfileId,
        serverUrl = serverUrl,
        storageScopeId = automaticStorageScopeId
    )
    val automaticPolicyStore = remember { AutomaticDownloadPolicyStore(context) }
    var automaticPolicy by remember(automaticProfileId, serverUrl, automaticStorageScopeId) {
        mutableStateOf(
            automaticPolicyStore.read(
                automaticProfileId,
                serverUrl,
                automaticStorageScopeId
            )
        )
    }
    val automaticStatus by produceState(
        initialValue = AutomaticDownloadStatusStore(context).read(
            automaticProfileId,
            automaticStorageScopeId
        ),
        automaticDownloadRefreshKey,
        automaticProfileId,
        automaticStorageScopeId
    ) {
        repeat(160) { attempt ->
            value = AutomaticDownloadStatusStore(context).read(
                automaticProfileId,
                automaticStorageScopeId
            )
            if (attempt > 1 && value.state != AutomaticDownloadRunState.RUNNING) return@produceState
            delay(750)
        }
    }
    val saveAutomaticPolicy: (AutomaticDownloadPolicy) -> Unit = { requested ->
        val requestedOwner = requested.ownerKey()
        val liveOwner = currentAutomaticDownloadOwnerKey(
            context = context,
            expectedProfileId = requested.profileId,
            serverUrl = requested.serverUrl
        )
        if (automaticDownloadOwnersMatch(requestedOwner, automaticOwner) &&
            liveOwner != null &&
            automaticDownloadOwnersMatch(requestedOwner, liveOwner)
        ) {
            val saved = automaticPolicyStore.save(requested)
            automaticPolicy = saved
            AutomaticDownloadScheduler.reconfigure(context, saved)
            automaticDownloadRefreshKey += 1
        } else {
            storageMessage = context.getString(R.string.settings_profile_changed)
        }
    }
    val storageUsage by produceState<StorageUsage?>(initialValue = null, storageRefreshKey) {
        value = runCatching { storageUsageLoader() }.getOrNull()
    }
    val portableStorageStatus by produceState<Pair<PortableStorageAvailability, Long?>?>(
        initialValue = null,
        downloadStorageSelection,
        storageRefreshKey
    ) {
        value = withContext(Dispatchers.IO) {
            downloadStorageSelectionStore.availability(downloadStorageSelection) to
                downloadStorageSelectionStore.availableBytes(downloadStorageSelection)
        }
    }
    val offlineCacheStatus by produceState(
        initialValue = OfflineCacheStatus(),
        offlineCacheRefreshKey,
        offlineCacheAvailable
    ) {
        if (!offlineCacheAvailable) {
            value = OfflineCacheStatus()
            return@produceState
        }
        do {
            value = runCatching { offlineCacheStatusLoader() }.getOrDefault(value)
            if (value.state == OfflineCacheRunState.RUNNING) delay(750) else break
        } while (true)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("options-list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
    ) {
        item(key = "options-intro") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedCategory != null || subDestination != null) {
                    IconButton(
                        onClick = { 
                            if (subDestination != null) {
                                subDestination = null 
                            } else {
                                selectedCategory = null 
                            }
                        },
                        modifier = Modifier.testTag("options-back")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.settings_back_to_settings)
                        )
                    }
                }
                OrbitEyebrow(stringResource(R.string.settings_options_eyebrow))
                val categoryForHeader = selectedCategory
                val headerTitle = when {
                    subDestination == "android_auto" -> "Android Auto"
                    categoryForHeader == OptionsCategory.APPEARANCE ->
                        stringResource(R.string.settings_interface_title)
                    categoryForHeader == null -> stringResource(R.string.settings_title)
                    else -> stringResource(categoryForHeader.titleRes)
                }
                val headerSummary = when {
                    subDestination == "android_auto" ->
                        "Configure playback speed buttons, skip increments, and vehicle controls"
                    categoryForHeader != null -> stringResource(categoryForHeader.summaryRes)
                    else -> stringResource(R.string.settings_choose_category_summary)
                }
                Text(
                    headerTitle,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    headerSummary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (selectedCategory == null) {
            OptionsCategory.values().forEach { category ->
                item(key = "category-${category.name}") {
                    ListItem(
                        headlineContent = { Text(stringResource(category.titleRes)) },
                        supportingContent = { Text(stringResource(category.summaryRes)) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                stringResource(category.titleRes)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = category }
                            .testTag("options-category-${category.name.lowercase()}")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (selectedCategory == OptionsCategory.GENERAL) item(key = "lock-orientation") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_lock_orientation),
                summary = stringResource(R.string.settings_lock_orientation_summary),
                checked = preferences.lockOrientation,
                testTag = "options-lock-orientation",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(lockOrientation = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.GENERAL) item(key = "immersive-reading-navigation") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_immersive_hide_nav),
                summary = stringResource(R.string.settings_immersive_hide_nav_summary),
                checked = preferences.hideNavigationBarWhileReading,
                testTag = "options-hide-navigation-bar-reading",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(hideNavigationBarWhileReading = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.GENERAL) item(key = "immersive-reading-status") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_immersive_hide_status),
                summary = stringResource(R.string.settings_immersive_hide_status_summary),
                checked = preferences.hideStatusBarWhileReading,
                testTag = "options-hide-status-bar-reading",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(hideStatusBarWhileReading = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.GENERAL) item(key = "language") {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.settings_language),
                value = languageLabels[languageTag] ?: languageTag,
                summary = stringResource(R.string.settings_language_summary),
                testTag = "options-language",
                onClick = { openDialog = OptionsDialog.LANGUAGE }
            )
        }
        if (selectedCategory == OptionsCategory.APPEARANCE) item(key = "theme") {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.settings_theme),
                value = themeLabels.getValue(preferences.themeMode),
                testTag = "options-theme",
                onClick = { openDialog = OptionsDialog.THEME }
            )
        }
        if (selectedCategory == OptionsCategory.GENERAL) item(key = "opening-screen") {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.settings_opening_screen),
                value = openingScreenLabels.getValue(preferences.defaultOpeningScreen),
                summary = stringResource(R.string.settings_opening_screen_summary),
                testTag = "options-opening-screen",
                onClick = { openDialog = OptionsDialog.OPENING_SCREEN }
            )
        }
        if (
            selectedCategory == OptionsCategory.GENERAL &&
            homeShelfPreferences.source == HomeShelfPreferenceSource.LOCAL_FALLBACK
        ) item(key = "local-on-deck") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_show_on_deck),
                summary = stringResource(R.string.settings_show_on_deck_summary),
                checked = preferences.showOnDeckWhenShelfSyncDisabled,
                testTag = "options-show-local-on-deck",
                onCheckedChange = {
                    onPreferencesChange(
                        preferences.copy(showOnDeckWhenShelfSyncDisabled = it)
                    )
                }
            )
        }
        if (selectedCategory == OptionsCategory.APPEARANCE) item(key = "reduce-motion") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_reduce_motion),
                summary = stringResource(R.string.settings_reduce_motion_summary),
                checked = preferences.reduceMotion,
                testTag = "options-reduce-motion",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(reduceMotion = it))
                }
            )
        }
if (selectedCategory == OptionsCategory.PLAYBACK && subDestination == null) item(key = "scale-progress-speed") {
            AppPreferenceSwitchRow(
                title = "Scale Progress by Playback Speed",
                summary = "Adjusts remaining time and progress bar tracking when listening above 1.0x speed.",
                checked = preferences.scaleProgressByPlaybackSpeed,
                testTag = "options-scale-progress-speed",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(scaleProgressByPlaybackSpeed = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.PLAYBACK && subDestination == null) item(key = "android-auto-menu-item") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { subDestination = "android_auto" }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("options-playback-android-auto"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OrbitEyebrow("Android Auto")
                Text("Dashboard controls", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Customize playback speed buttons, skip intervals, and steering wheel actions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (subDestination == "android_auto") item(key = "aa-skip-behavior-header") {
            Column(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitEyebrow("Android Auto Controls")
                Text("Steering wheel & skip behavior", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Define whether vehicle skip buttons jump chapters or perform timed skips.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (subDestination == "android_auto") item(key = "aa-vehicle-skip-mode") {
            // Placeholder or preference toggle for steering wheel skip mode (Chapters vs Time-skip)
            AppPreferenceSwitchRow(
                title = "Vehicle Skip Buttons Jump Chapters",
                summary = "When enabled, steering wheel next/previous skip chapters instead of jumping 10s/30s.",
                checked = false, // Map to your app preference key when ready
                testTag = "options-aa-vehicle-skip-chapters",
                onCheckedChange = { /* Save preference */ }
            )
        }
        if (selectedCategory == OptionsCategory.LIBRARY) item(key = "library-card-size") {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.settings_library_card_size),
                value = libraryCardSizeLabels.getValue(preferences.libraryCardSize),
                summary = stringResource(R.string.settings_library_card_size_summary),
                testTag = "options-library-card-size",
                onClick = { openDialog = OptionsDialog.LIBRARY_CARD_SIZE }
            )
        }
        if (selectedCategory == OptionsCategory.LIBRARY) item(key = "epub-image-minimum-size") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("options-epub-image-minimum-size")
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.settings_epub_image_min_size),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        R.string.settings_epub_image_min_size_summary,
                        preferences.epubImageMinimumDimensionPx
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                val imageMinimumSizeDescription =
                    stringResource(R.string.settings_epub_image_min_size_content_description)
                Slider(
                    value = preferences.epubImageMinimumDimensionPx.toFloat(),
                    onValueChange = { next ->
                        onPreferencesChange(
                            preferences.copy(
                                epubImageMinimumDimensionPx = normalizeEpubImageMinimumDimensionPx(
                                    next.roundToInt()
                                )
                            )
                        )
                    },
                    valueRange = EPUB_IMAGE_MINIMUM_DIMENSION_MIN_PX.toFloat()..
                        EPUB_IMAGE_MINIMUM_DIMENSION_MAX_PX.toFloat(),
                    steps = (EPUB_IMAGE_MINIMUM_DIMENSION_MAX_PX -
                        EPUB_IMAGE_MINIMUM_DIMENSION_MIN_PX) /
                        EPUB_IMAGE_MINIMUM_DIMENSION_STEP_PX - 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = imageMinimumSizeDescription
                        }
                )
            }
        }
        if (selectedCategory == OptionsCategory.LIBRARY) item(key = "reading-configuration") {
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
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "data-heading") {
            Column(
                modifier = Modifier.padding(start = 4.dp, top = 26.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrbitEyebrow(stringResource(R.string.settings_data_eyebrow))
                Text(
                    stringResource(R.string.settings_network_storage),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(R.string.settings_network_storage_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "cellular-downloads") {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.settings_cellular_downloads),
                value = cellularDownloadLabels.getValue(preferences.cellularDownloadPolicy),
                summary = stringResource(R.string.settings_cellular_downloads_summary),
                testTag = "options-cellular-downloads",
                onClick = { openDialog = OptionsDialog.CELLULAR_DOWNLOADS }
            )
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "download-location") {
            val availability = portableStorageStatus?.first
                ?: PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
            val availableBytes = portableStorageStatus?.second
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_download_location_title)) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (downloadStorageSelection.usesPortableFolder) {
                                downloadStorageSelection.displayName
                                    ?: stringResource(R.string.settings_download_location_selected_folder)
                            } else {
                                stringResource(R.string.settings_download_location_internal)
                            }
                        )
                        if (downloadStorageSelection.usesPortableFolder) {
                            Text(
                                when (availability) {
                                    PortableStorageAvailability.AVAILABLE -> availableBytes?.let {
                                        stringResource(
                                            R.string.settings_download_location_available,
                                            formatByteSize(it)
                                        )
                                    } ?: stringResource(R.string.settings_download_location_available_unknown)
                                    PortableStorageAvailability.PERMISSION_REVOKED ->
                                        stringResource(R.string.settings_download_location_permission_revoked)
                                    PortableStorageAvailability.TEMPORARILY_UNAVAILABLE ->
                                        stringResource(R.string.settings_download_location_unavailable)
                                    PortableStorageAvailability.NOT_CONFIGURED ->
                                        stringResource(R.string.settings_download_location_internal)
                                },
                                color = if (availability == PortableStorageAvailability.AVAILABLE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            stringResource(R.string.settings_download_location_existing_stay),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (downloadStorageSelection.usesPortableFolder) {
                            Text(
                                stringResource(R.string.settings_download_location_managed_subfolder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        downloadStorageMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(
                            onClick = {
                                downloadStorageMessage = null
                                downloadFolderLauncher.launch(
                                    downloadStorageSelection.treeUri?.let(Uri::parse)
                                )
                            },
                            modifier = Modifier.testTag("options-choose-download-folder")
                        ) { Text(stringResource(R.string.settings_download_location_choose_folder)) }
                        if (downloadStorageSelection.usesPortableFolder) {
                            TextButton(
                                onClick = {
                                    downloadStorageSelection =
                                        downloadStorageSelectionStore.useInternalStorage()
                                    downloadStorageMessage = context.getString(
                                        R.string.settings_download_location_new_downloads_internal
                                    )
                                    storageRefreshKey += 1
                                },
                                modifier = Modifier.testTag("options-use-internal-download-storage")
                            ) { Text(stringResource(R.string.settings_download_location_use_internal)) }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("options-download-location")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "storage") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_storage)) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val usage = storageUsage
                        val usageLine = if (usage != null) {
                            stringResource(
                                R.string.settings_storage_usage,
                                formatByteSize(usage.downloadedBytes),
                                formatByteSize(usage.cacheBytes)
                            )
                        } else {
                            stringResource(R.string.settings_storage_calculating)
                        }
                        Text(usageLine)
                        storageMessage?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            stringResource(R.string.settings_storage_clear_hint),
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
                        Text(
                            stringResource(
                                if (isClearingCache) {
                                    R.string.settings_clearing
                                } else {
                                    R.string.settings_clear_cache
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("options-storage")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE && offlineCacheAvailable) item(key = "offline-library-cache") {
            OfflineLibraryCacheConfiguration(
                preferences = preferences,
                libraries = libraries,
                status = offlineCacheStatus,
                onPreferencesChange = onPreferencesChange,
                onStart = {
                    scope.launch {
                        if (!onStartOfflineCacheUpdate()) {
                            storageMessage =
                                context.getString(R.string.settings_offline_cache_scope_required)
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
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE && !offlineCacheAvailable) item(key = "offline-library-cache-unavailable") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_offline_cache_title)) },
                supportingContent = { Text(stringResource(R.string.settings_offline_cache_unavailable)) },
                modifier = Modifier.fillMaxWidth().testTag("options-offline-cache-unavailable")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "automatic-book-downloads") {
            AutomaticBookDownloadConfiguration(
                policy = automaticPolicy,
                libraries = libraries,
                downloadLocationName = if (downloadStorageSelection.usesPortableFolder) {
                    downloadStorageSelection.displayName ?: "Selected SD-card folder"
                } else {
                    "Internal app storage"
                },
                status = automaticStatus,
                onPolicyChange = saveAutomaticPolicy,
                ownerIsCurrent = { candidateOwner ->
                    automaticDownloadOwnersMatch(candidateOwner, automaticOwner) &&
                        currentAutomaticDownloadOwnerKey(
                            context = context,
                            expectedProfileId = candidateOwner.profileId,
                            serverUrl = candidateOwner.serverUrl
                        )?.let { liveOwner ->
                            automaticDownloadOwnersMatch(candidateOwner, liveOwner)
                        } == true
                },
                onSyncNow = {
                    if (AutomaticDownloadScheduler.enqueueNow(context, automaticPolicy)) {
                        automaticDownloadRefreshKey += 1
                    }
                },
                onCancel = {
                    automaticPolicy = AutomaticDownloadScheduler.cancel(context, automaticPolicy)
                    automaticDownloadRefreshKey += 1
                }
            )
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "clear-downloaded-books") {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_remove_downloaded_books))
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_remove_downloaded_books_summary))
                },
                trailingContent = {
                    TextButton(
                        onClick = { showClearDownloadedBooks = true },
                        enabled = !isClearingDownloadedBooks,
                        modifier = Modifier.testTag("options-clear-downloaded-books")
                    ) {
                        Text(
                            stringResource(
                                if (isClearingDownloadedBooks) {
                                    R.string.settings_removing
                                } else {
                                    R.string.settings_remove
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (selectedCategory == OptionsCategory.DOWNLOADS_OFFLINE) item(key = "confirm-local-delete") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_confirm_local_delete),
                summary = stringResource(R.string.settings_confirm_local_delete_summary),
                checked = preferences.confirmDeleteLocalCopy,
                testTag = "options-confirm-local-delete",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(confirmDeleteLocalCopy = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.NETWORK_SYNC) item(key = "confirm-audiobook-seek") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_confirm_audiobook_seek),
                summary = stringResource(R.string.settings_confirm_audiobook_seek_summary),
                checked = preferences.confirmAudiobookSeek,
                testTag = "options-confirm-audiobook-seek",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(confirmAudiobookSeek = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.NETWORK_SYNC) item(key = "background-refresh") {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.settings_background_refresh),
                value = backgroundRefreshLabels.getValue(
                    preferences.backgroundRefreshNetworkPolicy
                ),
                summary = stringResource(R.string.settings_background_refresh_summary),
                testTag = "options-background-refresh",
                onClick = { openDialog = OptionsDialog.BACKGROUND_REFRESH }
            )
        }
        if (selectedCategory == OptionsCategory.NETWORK_SYNC) item(key = "pause-audiobook-interruptions") {
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_pause_audiobook),
                summary = stringResource(R.string.settings_pause_audiobook_summary),
                checked = preferences.pauseAudiobookForAudioInterruptions,
                testTag = "options-pause-audiobook-interruptions",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(pauseAudiobookForAudioInterruptions = it))
                }
            )
        }
        if (selectedCategory == OptionsCategory.PERMISSIONS) item(key = "permissions-heading") {
            Column(
                modifier = Modifier.padding(start = 4.dp, top = 18.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(R.string.settings_category_permissions),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(R.string.settings_permissions_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (selectedCategory == OptionsCategory.PERMISSIONS) {
            if (permissionSpecs.isEmpty()) {
                item(key = "permissions-none") {
                    Text(
                        stringResource(R.string.settings_permissions_none),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                permissionSpecs.forEach { spec ->
                    item(key = "permission-${spec.permission}") {
                        val enabled = isAppPermissionGranted(context, spec.permission)
                        ListItem(
                            headlineContent = { Text(stringResource(spec.titleRes)) },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(spec.summaryRes))
                                    Text(
                                        stringResource(
                                            if (enabled) {
                                                R.string.settings_permissions_enabled
                                            } else {
                                                R.string.settings_permissions_not_enabled
                                            }
                                        ),
                                        color = if (enabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(
                                        R.string.settings_permissions_open_system
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(spec.settingsIntent(context))
                                    }
                                }
                                .testTag("options-permission-${spec.permission}")
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    when (openDialog) {
        OptionsDialog.THEME -> AppPreferenceChoiceDialog(
            title = stringResource(R.string.settings_theme),
            choices = AppThemeMode.values().toList(),
            selected = preferences.themeMode,
            label = { themeLabels.getValue(it) },
            onSelect = {
                onPreferencesChange(preferences.copy(themeMode = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.OPENING_SCREEN -> AppPreferenceChoiceDialog(
            title = stringResource(R.string.settings_opening_screen),
            choices = DefaultOpeningScreen.values().toList(),
            selected = preferences.defaultOpeningScreen,
            label = { openingScreenLabels.getValue(it) },
            onSelect = {
                onPreferencesChange(preferences.copy(defaultOpeningScreen = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.LIBRARY_CARD_SIZE -> AppPreferenceChoiceDialog(
            title = stringResource(R.string.settings_library_card_size),
            choices = LibraryCardSize.values().toList(),
            selected = preferences.libraryCardSize,
            label = { libraryCardSizeLabels.getValue(it) },
            onSelect = {
                onPreferencesChange(preferences.copy(libraryCardSize = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.CELLULAR_DOWNLOADS -> AppPreferenceChoiceDialog(
            title = stringResource(R.string.settings_cellular_downloads),
            choices = CellularDownloadPolicy.values().toList(),
            selected = preferences.cellularDownloadPolicy,
            label = { cellularDownloadLabels.getValue(it) },
            onSelect = {
                onPreferencesChange(preferences.copy(cellularDownloadPolicy = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.BACKGROUND_REFRESH -> AppPreferenceChoiceDialog(
            title = stringResource(R.string.settings_background_refresh),
            choices = BackgroundRefreshNetworkPolicy.values().toList(),
            selected = preferences.backgroundRefreshNetworkPolicy,
            label = { backgroundRefreshLabels.getValue(it) },
            onSelect = {
                onPreferencesChange(preferences.copy(backgroundRefreshNetworkPolicy = it))
                openDialog = null
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.LANGUAGE -> AppPreferenceChoiceDialog(
            title = stringResource(R.string.settings_language),
            choices = AppLocale.choices,
            selected = languageTag,
            label = { tag -> languageLabels[tag] ?: tag },
            onSelect = { tag ->
                AppLocale.writeTag(context, tag)
                languageTag = tag
                openDialog = null
                context.findActivity()?.recreate()
            },
            onDismiss = { openDialog = null }
        )
        OptionsDialog.CLEAR_CACHE -> AlertDialog(
            onDismissRequest = { openDialog = null },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        openDialog = null
                        isClearingCache = true
                        storageMessage = null
                        scope.launch {
                            runCatching { onClearCache() }
                                .onSuccess {
                                    storageMessage =
                                        context.getString(R.string.settings_cache_cleared)
                                    storageRefreshKey += 1
                                }
                                .onFailure {
                                    storageMessage =
                                        context.getString(R.string.settings_cache_clear_failed)
                                }
                            isClearingCache = false
                        }
                    },
                    modifier = Modifier.testTag("confirm-clear-cache")
                ) { Text(stringResource(R.string.settings_clear_cache)) }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
        null -> Unit
    }

    if (showClearDownloadedBooks) {
        AlertDialog(
            onDismissRequest = { showClearDownloadedBooks = false },
            title = { Text(stringResource(R.string.settings_remove_downloaded_books_title)) },
            text = {
                Text(stringResource(R.string.settings_remove_downloaded_books_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDownloadedBooks = false
                        isClearingDownloadedBooks = true
                        scope.launch {
                            val result = clearLocalCopiesForServer(context, serverUrl)
                            storageMessage = buildString {
                                append(
                                    context.getString(
                                        R.string.settings_removed_files,
                                        result.removedCount,
                                        formatByteSize(result.removedBytes)
                                    )
                                )
                                if (result.keptActiveCount > 0) {
                                    append(
                                        context.getString(
                                            R.string.settings_removed_kept_active,
                                            result.keptActiveCount
                                        )
                                    )
                                }
                                if (result.failedCount > 0) {
                                    append(
                                        context.getString(
                                            R.string.settings_removed_failed,
                                            result.failedCount
                                        )
                                    )
                                }
                            }
                            isClearingDownloadedBooks = false
                            storageRefreshKey += 1
                        }
                    },
                    modifier = Modifier.testTag("confirm-clear-downloaded-books")
                ) { Text(stringResource(R.string.settings_remove_local_copies)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadedBooks = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutomaticBookDownloadConfiguration(
    policy: AutomaticDownloadPolicy,
    libraries: List<LibrarySummary>,
    downloadLocationName: String = "Internal app storage",
    status: AutomaticDownloadStatus,
    onPolicyChange: (AutomaticDownloadPolicy) -> Unit,
    ownerIsCurrent: (AutomaticDownloadOwnerKey) -> Boolean = { true },
    onSyncNow: () -> Unit,
    onCancel: () -> Unit
) {
    val ownerKey = policy.ownerKey()
    val context = LocalContext.current
    var reserveText by remember(ownerKey) {
        mutableStateOf(formatAutomaticDownloadGiB(policy.reserveBytes))
    }
    var maximumText by remember(ownerKey) {
        mutableStateOf(formatAutomaticDownloadGiB(policy.maximumBytes))
    }
    var limitError by remember(ownerKey) { mutableStateOf(false) }
    var activationError by remember(ownerKey) { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember(ownerKey) {
        mutableStateOf<AutomaticDownloadConfirmation?>(null)
    }
    val hasScope = policy.scope == AutomaticDownloadScope.ALL_LIBRARIES ||
        policy.selectedLibraryIds.isNotEmpty()
    val isRunning = status.state == AutomaticDownloadRunState.RUNNING
    fun policyWithEditedLimits(base: AutomaticDownloadPolicy): AutomaticDownloadPolicy? {
        val reserve = parseAutomaticDownloadGiB(reserveText)
        val maximum = parseAutomaticDownloadGiB(maximumText)
        if (reserve == null || maximum == null) {
            limitError = true
            return null
        }
        return base.copy(reserveBytes = reserve, maximumBytes = maximum)
    }

    fun requestRiskyChange(candidate: AutomaticDownloadPolicy) {
        val risks = automaticDownloadRiskyChanges(policy, candidate)
        if (automaticDownloadChangeRequiresConfirmation(policy, risks)) {
            pendingConfirmation = AutomaticDownloadConfirmation.RiskyChange(candidate, risks)
        } else {
            onPolicyChange(candidate)
        }
    }

    fun applyConfirmedPolicy(candidate: AutomaticDownloadPolicy) {
        val candidateOwner = candidate.ownerKey()
        if (!automaticDownloadOwnersMatch(candidateOwner, ownerKey) ||
            !ownerIsCurrent(candidateOwner)
        ) {
            pendingConfirmation = null
            activationError = context.getString(R.string.settings_profile_changed)
            return
        }
        onPolicyChange(candidate)
        reserveText = formatAutomaticDownloadGiB(candidate.reserveBytes)
        maximumText = formatAutomaticDownloadGiB(candidate.maximumBytes)
        pendingConfirmation = null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("options-automatic-book-downloads"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.settings_auto_download_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(R.string.settings_auto_download_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_auto_download_enable),
                summary = stringResource(R.string.settings_auto_download_enable_summary),
                checked = policy.enabled,
                testTag = "automatic-download-enabled",
                onCheckedChange = { enabled ->
                    activationError = null
                    if (!enabled) {
                        pendingConfirmation = null
                        onPolicyChange(policy.copy(enabled = false))
                    } else {
                        val candidate = policyWithEditedLimits(policy.copy(enabled = true))
                        when {
                            candidate == null -> Unit
                            !candidate.hasAutomaticDownloadScope() -> {
                                activationError = context.getString(
                                    R.string.settings_auto_download_scope_required
                                )
                            }
                            else -> {
                                pendingConfirmation = AutomaticDownloadConfirmation.EnableReview(candidate)
                            }
                        }
                    }
                }
            )
            activationError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("automatic-download-activation-error")
                )
            }
            Text(
                stringResource(R.string.settings_auto_download_scope),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = policy.scope == AutomaticDownloadScope.SELECTED_LIBRARIES,
                    onClick = {
                        onPolicyChange(policy.copy(scope = AutomaticDownloadScope.SELECTED_LIBRARIES))
                    },
                    label = {
                        Text(stringResource(R.string.settings_auto_download_scope_selected))
                    },
                    modifier = Modifier.testTag("automatic-download-scope-selected")
                )
                FilterChip(
                    selected = policy.scope == AutomaticDownloadScope.ALL_LIBRARIES,
                    onClick = {
                        onPolicyChange(policy.copy(scope = AutomaticDownloadScope.ALL_LIBRARIES))
                    },
                    label = { Text(stringResource(R.string.settings_auto_download_scope_all)) },
                    modifier = Modifier.testTag("automatic-download-scope-all")
                )
            }
            if (policy.scope == AutomaticDownloadScope.SELECTED_LIBRARIES) {
                if (libraries.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_no_libraries),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        libraries.forEach { library ->
                            FilterChip(
                                selected = library.id in policy.selectedLibraryIds,
                                onClick = {
                                    val next = if (library.id in policy.selectedLibraryIds) {
                                        policy.selectedLibraryIds - library.id
                                    } else {
                                        policy.selectedLibraryIds + library.id
                                    }
                                    onPolicyChange(policy.copy(selectedLibraryIds = next))
                                },
                                label = { Text(library.name) },
                                modifier = Modifier.testTag(
                                    "automatic-download-library-${library.id}"
                                )
                            )
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.settings_auto_download_new_libraries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.settings_auto_download_initial),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = policy.initialMode ==
                        AutomaticDownloadInitialMode.EXISTING_AND_FUTURE,
                    onClick = {
                        onPolicyChange(
                            policy.copy(
                                initialMode = AutomaticDownloadInitialMode.EXISTING_AND_FUTURE
                            )
                        )
                    },
                    label = {
                        Text(stringResource(R.string.settings_auto_download_existing_and_future))
                    }
                )
                FilterChip(
                    selected = policy.initialMode == AutomaticDownloadInitialMode.NEW_BOOKS_ONLY,
                    onClick = {
                        onPolicyChange(
                            policy.copy(initialMode = AutomaticDownloadInitialMode.NEW_BOOKS_ONLY)
                        )
                    },
                    label = { Text(stringResource(R.string.settings_auto_download_new_books)) }
                )
            }
            if (policy.initialMode == AutomaticDownloadInitialMode.NEW_BOOKS_ONLY) {
                Text(
                    stringResource(R.string.settings_auto_download_new_books_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_auto_download_every_copy),
                summary = stringResource(R.string.settings_auto_download_every_copy_summary),
                checked = policy.allReadableCopies,
                testTag = "automatic-download-all-copies",
                onCheckedChange = { onPolicyChange(policy.copy(allReadableCopies = it)) }
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_auto_download_unmetered),
                summary = stringResource(R.string.settings_auto_download_unmetered_summary),
                checked = policy.unmeteredOnly,
                testTag = "automatic-download-unmetered",
                onCheckedChange = { requestRiskyChange(policy.copy(unmeteredOnly = it)) }
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_auto_download_charging),
                summary = stringResource(R.string.settings_auto_download_charging_summary),
                checked = policy.chargingRequired,
                testTag = "automatic-download-charging",
                onCheckedChange = { requestRiskyChange(policy.copy(chargingRequired = it)) }
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.settings_auto_download_storage_limits),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_auto_download_zero_disables),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = reserveText,
                onValueChange = { reserveText = it; limitError = false },
                label = {
                    Text(stringResource(R.string.settings_auto_download_reserve_label))
                },
                suffix = { Text("GB") },
                singleLine = true,
                isError = limitError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("automatic-download-reserve")
            )
            OutlinedTextField(
                value = maximumText,
                onValueChange = { maximumText = it; limitError = false },
                label = {
                    Text(stringResource(R.string.settings_auto_download_maximum_label))
                },
                suffix = { Text("GB") },
                singleLine = true,
                isError = limitError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("automatic-download-maximum")
            )
            TextButton(
                onClick = {
                    val reserve = parseAutomaticDownloadGiB(reserveText)
                    val maximum = parseAutomaticDownloadGiB(maximumText)
                    if (reserve == null || maximum == null) {
                        limitError = true
                    } else {
                        onPolicyChange(
                            policy.copy(reserveBytes = reserve, maximumBytes = maximum)
                        )
                        reserveText = formatAutomaticDownloadGiB(reserve)
                        maximumText = formatAutomaticDownloadGiB(maximum)
                    }
                },
                modifier = Modifier.testTag("automatic-download-save-limits")
            ) { Text(stringResource(R.string.settings_auto_download_save_limits)) }
            if (limitError) {
                Text(
                    stringResource(R.string.settings_auto_download_limit_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_auto_download_removal),
                summary = stringResource(R.string.settings_auto_download_removal_summary),
                checked = policy.automaticRemovalEnabled,
                testTag = "automatic-download-removal",
                onCheckedChange = { enabled ->
                    val candidate = if (enabled) {
                        policyWithEditedLimits(policy.copy(automaticRemovalEnabled = true))
                    } else {
                        policy.copy(automaticRemovalEnabled = false)
                    }
                    if (candidate != null) requestRiskyChange(candidate)
                }
            )
            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            status.localizedMessage(context)?.let { message ->
                Text(
                    message,
                    color = if (status.state == AutomaticDownloadRunState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (status.checked > 0 || status.queued > 0 || status.failed > 0) {
                Text(
                    stringResource(
                        R.string.settings_auto_download_progress,
                        status.checked,
                        status.queued,
                        status.skipped,
                        status.failed
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSyncNow,
                    enabled = policy.enabled && hasScope && !isRunning,
                    modifier = Modifier.testTag("automatic-download-sync-now")
                ) { Text(stringResource(R.string.settings_auto_download_sync_next)) }
                if (isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("automatic-download-cancel")
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
            }
            Text(
                stringResource(R.string.settings_auto_download_pass_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    when (val confirmation = pendingConfirmation) {
        is AutomaticDownloadConfirmation.EnableReview -> {
            AlertDialog(
                onDismissRequest = { pendingConfirmation = null },
                title = { Text(stringResource(R.string.settings_auto_download_review_title)) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        automaticDownloadLocalizedReviewLines(
                            confirmation.policy,
                            libraries,
                            downloadLocationName
                        ).forEach { line ->
                            Text(line)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingConfirmation = AutomaticDownloadConfirmation.EnableConsent(
                                confirmation.policy
                            )
                        },
                        modifier = Modifier.testTag("automatic-download-review-continue")
                    ) { Text(stringResource(R.string.common_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirmation = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                modifier = Modifier.testTag("automatic-download-review-dialog")
            )
        }
        is AutomaticDownloadConfirmation.EnableConsent -> {
            AlertDialog(
                onDismissRequest = { pendingConfirmation = null },
                title = { Text(stringResource(R.string.settings_auto_download_consent_title)) },
                text = {
                    Text(stringResource(R.string.settings_auto_download_consent_text))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            applyConfirmedPolicy(confirmation.policy)
                        },
                        modifier = Modifier.testTag("automatic-download-confirm-enable")
                    ) { Text(stringResource(R.string.settings_auto_download_consent_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirmation = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                modifier = Modifier.testTag("automatic-download-consent-dialog")
            )
        }
        is AutomaticDownloadConfirmation.RiskyChange -> {
            AlertDialog(
                onDismissRequest = { pendingConfirmation = null },
                title = { Text(stringResource(R.string.settings_auto_download_risky_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        confirmation.risks.forEach { risk ->
                            Text(stringResource(risk.descriptionRes))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            applyConfirmedPolicy(confirmation.policy)
                        },
                        modifier = Modifier.testTag("automatic-download-confirm-risky-change")
                    ) { Text(stringResource(R.string.settings_auto_download_apply)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirmation = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                modifier = Modifier.testTag("automatic-download-risk-dialog")
            )
        }
        null -> Unit
    }
}

private sealed interface AutomaticDownloadConfirmation {
    val policy: AutomaticDownloadPolicy

    data class EnableReview(override val policy: AutomaticDownloadPolicy) :
        AutomaticDownloadConfirmation

    data class EnableConsent(override val policy: AutomaticDownloadPolicy) :
        AutomaticDownloadConfirmation

    data class RiskyChange(
        override val policy: AutomaticDownloadPolicy,
        val risks: List<AutomaticDownloadRisk>
    ) : AutomaticDownloadConfirmation
}

internal enum class AutomaticDownloadRisk(@StringRes val descriptionRes: Int) {
    AUTOMATIC_REMOVAL(R.string.settings_auto_download_risk_removal),
    METERED_NETWORK(R.string.settings_auto_download_risk_metered),
    NOT_CHARGING(R.string.settings_auto_download_risk_charging)
}

internal fun automaticDownloadRiskyChanges(
    current: AutomaticDownloadPolicy,
    candidate: AutomaticDownloadPolicy
): List<AutomaticDownloadRisk> = buildList {
    if (!current.automaticRemovalEnabled && candidate.automaticRemovalEnabled) {
        add(AutomaticDownloadRisk.AUTOMATIC_REMOVAL)
    }
    if (current.unmeteredOnly && !candidate.unmeteredOnly) {
        add(AutomaticDownloadRisk.METERED_NETWORK)
    }
    if (current.chargingRequired && !candidate.chargingRequired) {
        add(AutomaticDownloadRisk.NOT_CHARGING)
    }
}

internal fun automaticDownloadChangeRequiresConfirmation(
    current: AutomaticDownloadPolicy,
    risks: List<AutomaticDownloadRisk>
): Boolean = AutomaticDownloadRisk.AUTOMATIC_REMOVAL in risks ||
    (current.enabled && risks.isNotEmpty())

internal fun AutomaticDownloadPolicy.hasAutomaticDownloadScope(): Boolean =
    scope == AutomaticDownloadScope.ALL_LIBRARIES || selectedLibraryIds.isNotEmpty()

@Composable
private fun automaticDownloadLocalizedReviewLines(
    policy: AutomaticDownloadPolicy,
    libraries: List<LibrarySummary>,
    downloadLocationName: String
): List<String> {
    val libraryNames = libraries.associate { it.id to it.name }
    val scopeLine = if (policy.scope == AutomaticDownloadScope.ALL_LIBRARIES) {
        stringResource(R.string.settings_auto_download_review_libraries_all)
    } else {
        val selected = policy.selectedLibraryIds.sorted().map { libraryNames[it] ?: it }
        stringResource(
            R.string.settings_auto_download_review_libraries_selected,
            selected.joinToString()
        )
    }
    val maximumLimit = localizedAutomaticDownloadLimit(policy.maximumBytes)
    val reserveLimit = localizedAutomaticDownloadLimit(policy.reserveBytes)
    return listOf(
        scopeLine,
        stringResource(
            if (policy.initialMode == AutomaticDownloadInitialMode.EXISTING_AND_FUTURE) {
                R.string.settings_auto_download_review_books_existing
            } else {
                R.string.settings_auto_download_review_books_new
            }
        ),
        stringResource(
            if (policy.allReadableCopies) {
                R.string.settings_auto_download_review_copies_all
            } else {
                R.string.settings_auto_download_review_copies_preferred
            }
        ),
        stringResource(
            if (policy.unmeteredOnly) {
                R.string.settings_auto_download_review_network_unmetered
            } else {
                R.string.settings_auto_download_review_network_any
            }
        ),
        stringResource(
            if (policy.chargingRequired) {
                R.string.settings_auto_download_review_power_charging
            } else {
                R.string.settings_auto_download_review_power_any
            }
        ),
        stringResource(
            R.string.settings_auto_download_review_location,
            downloadLocationName
        ),
        stringResource(
            R.string.settings_auto_download_review_max_storage,
            maximumLimit
        ),
        stringResource(
            R.string.settings_auto_download_review_reserved_space,
            reserveLimit
        ),
        stringResource(
            if (policy.automaticRemovalEnabled) {
                R.string.settings_auto_download_review_cleanup_enabled
            } else {
                R.string.settings_auto_download_review_cleanup_disabled
            }
        )
    )
}

@Composable
private fun localizedAutomaticDownloadLimit(bytes: Long): String = if (bytes <= 0L) {
    stringResource(R.string.settings_auto_download_review_no_limit)
} else {
    stringResource(
        R.string.settings_auto_download_review_limit_gb,
        formatAutomaticDownloadGiB(bytes)
    )
}

internal fun automaticDownloadReviewLines(
    policy: AutomaticDownloadPolicy,
    libraries: List<LibrarySummary>,
    downloadLocationName: String = "Internal app storage"
): List<String> {
    val libraryNames = libraries.associate { it.id to it.name }
    val scopeSummary = if (policy.scope == AutomaticDownloadScope.ALL_LIBRARIES) {
        "Libraries: All current and future libraries"
    } else {
        val selected = policy.selectedLibraryIds.sorted().map { libraryNames[it] ?: it }
        "Libraries: ${selected.joinToString()}"
    }
    return listOf(
        scopeSummary,
        "Books: " + if (policy.initialMode == AutomaticDownloadInitialMode.EXISTING_AND_FUTURE) {
            "Existing and future books"
        } else {
            "New books only"
        },
        "Copies per book: " + if (policy.allReadableCopies) {
            "Every readable copy"
        } else {
            "Preferred readable copy"
        },
        "Network: " + if (policy.unmeteredOnly) {
            "Unmetered only"
        } else {
            "Any connected network (cellular policy still applies)"
        },
        "Power: " + if (policy.chargingRequired) "Only while charging" else "Charging not required",
        "Download location: $downloadLocationName",
        "Maximum local-book storage: ${formatAutomaticDownloadLimit(policy.maximumBytes)}",
        "Reserved minimum free space: ${formatAutomaticDownloadLimit(policy.reserveBytes)}",
        "Automatic cleanup: " + if (policy.automaticRemovalEnabled) "Enabled" else "Disabled"
    )
}

private fun formatAutomaticDownloadLimit(bytes: Long): String =
    if (bytes <= 0L) "No limit" else "${formatAutomaticDownloadGiB(bytes)} GB"

internal fun parseAutomaticDownloadGiB(value: String): Long? {
    val gib = value.trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 0.0..2048.0 }
        ?: return null
    val bytes = (gib * 1024.0 * 1024.0 * 1024.0).toLong()
    return bytes.takeIf { gib == 0.0 || it > 0L }
}

internal fun formatAutomaticDownloadGiB(bytes: Long): String {
    return BigDecimal.valueOf(bytes.coerceAtLeast(0L))
        .divide(BigDecimal.valueOf(1024L * 1024L * 1024L), 6, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
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
    val context = LocalContext.current
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
            Text(
                stringResource(R.string.settings_offline_cache_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(R.string.settings_offline_cache_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.settings_libraries),
                style = MaterialTheme.typography.titleMedium
            )
            if (libraries.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_libraries),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                title = stringResource(R.string.settings_offline_cache_book_details),
                summary = stringResource(R.string.settings_offline_cache_book_details_summary),
                checked = preferences.offlineCacheDetailsEnabled,
                testTag = "offline-cache-details",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(offlineCacheDetailsEnabled = it))
                }
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_offline_cache_covers),
                summary = stringResource(R.string.settings_offline_cache_covers_summary),
                checked = preferences.offlineCacheCoversEnabled,
                testTag = "offline-cache-covers",
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(offlineCacheCoversEnabled = it))
                }
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_offline_cache_auto_refresh),
                summary = stringResource(R.string.settings_offline_cache_auto_refresh_summary),
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
                    Text(
                        stringResource(
                            R.string.settings_offline_cache_progress,
                            status.processed,
                            status.total
                        )
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.settings_offline_cache_refreshing))
                }
            } else if (status.state != OfflineCacheRunState.IDLE) {
                val checkedFallback = stringResource(
                    R.string.settings_offline_cache_books_checked,
                    status.processed
                )
                Text(
                    status.localizedMessage(context) ?: checkedFallback,
                    color = if (status.state == OfflineCacheRunState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (status.unavailable > 0 || status.failed > 0) {
                    Text(
                        stringResource(
                            R.string.settings_offline_cache_unavailable_failed,
                            status.unavailable,
                            status.failed
                        ),
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
                ) { Text(stringResource(R.string.settings_offline_cache_update_now)) }
                if (isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("offline-cache-cancel")
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
                TextButton(
                    onClick = onClear,
                    enabled = !isRunning,
                    modifier = Modifier.testTag("offline-cache-clear")
                ) { Text(stringResource(R.string.settings_offline_cache_clear)) }
            }
        }
    }
}

private enum class PageMarginSide(@StringRes val labelRes: Int) {
    TOP(R.string.settings_margin_top),
    BOTTOM(R.string.settings_margin_bottom),
    LEFT(R.string.settings_margin_left),
    RIGHT(R.string.settings_margin_right)
}

@Composable
private fun ReaderPageMarginsSettings(
    padding: EpubPaddingPercentages,
    testTagPrefix: String,
    onPaddingChange: (EpubPaddingPercentages) -> Unit
) {
    Text(
        stringResource(R.string.settings_page_margins),
        style = MaterialTheme.typography.titleMedium
    )
    PageMarginSide.values().forEach { side ->
        val margin = when (side) {
            PageMarginSide.TOP -> padding.top
            PageMarginSide.BOTTOM -> padding.bottom
            PageMarginSide.LEFT -> padding.left
            PageMarginSide.RIGHT -> padding.right
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val sideLabel = stringResource(side.labelRes)
            Text(
                stringResource(R.string.settings_margin_percent, sideLabel, margin.toInt()),
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = margin,
                onValueChange = { next ->
                    val updated = when (side) {
                        PageMarginSide.TOP -> padding.copy(top = next)
                        PageMarginSide.BOTTOM -> padding.copy(bottom = next)
                        PageMarginSide.LEFT -> padding.copy(left = next)
                        PageMarginSide.RIGHT -> padding.copy(right = next)
                    }
                    onPaddingChange(updated)
                },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.testTag("$testTagPrefix-margin-${side.name.lowercase()}")
            )
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
        OrbitEyebrow(stringResource(R.string.settings_reading_config_eyebrow))
        Text(
            stringResource(R.string.settings_library_reader_profile),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            stringResource(R.string.settings_library_reader_profile_summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { libraryMenuExpanded = true },
                enabled = libraries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("options-reading-library")
            ) {
                val noLibrariesLabel = stringResource(R.string.settings_no_libraries)
                Text(
                    selectedLibrary?.name ?: noLibrariesLabel,
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
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_volume_page_navigation),
                summary = stringResource(R.string.settings_volume_page_navigation_summary),
                checked = value.volumeButtonPageNavigation,
                testTag = "options-reading-volume-button-navigation",
                onCheckedChange = { enabled ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(volumeButtonPageNavigation = enabled)
                    )
                }
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_swap_volume_buttons),
                summary = stringResource(R.string.settings_swap_volume_buttons_summary),
                checked = value.reverseVolumeButtonNavigation,
                enabled = value.volumeButtonPageNavigation,
                testTag = "options-reading-reverse-volume-button-navigation",
                onCheckedChange = { reversed ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(reverseVolumeButtonNavigation = reversed)
                    )
                }
            )
            Text(
                stringResource(R.string.settings_reading_direction),
                style = MaterialTheme.typography.titleMedium
            )
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
                        label = {
                            Text(stringResource(direction.displayNameRes))
                        },
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
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_show_page_progress),
                summary = stringResource(R.string.settings_show_page_progress_summary),
                checked = value.showPageProgress,
                testTag = "options-reading-show-page-progress",
                onCheckedChange = { enabled ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(showPageProgress = enabled)
                    )
                }
            )
            AppPreferenceSwitchRow(
                title = stringResource(R.string.settings_prefer_server_progress),
                summary = stringResource(R.string.settings_prefer_server_progress_summary),
                checked = value.preferServerProgressOnReopen,
                testTag = "options-reading-prefer-server-progress-on-reopen",
                onCheckedChange = { enabled ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(preferServerProgressOnReopen = enabled)
                    )
                }
            )
            ReaderAppearanceSettings(
                value = value,
                onPreferencesChange = { next ->
                    onPreferencesChange(selectedLibrary.id, next)
                },
                testTagPrefix = "options-reading",
                showOptionsTheme = true,
                showBackgroundTheme = true
            )
            ReaderTextColorSettings(
                value = value,
                onPreferencesChange = { next ->
                    onPreferencesChange(selectedLibrary.id, next)
                },
                testTagPrefix = "options-reading"
            )
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
                    stringResource(
                        R.string.settings_text_size,
                        formatEpubFontScale(value.fontScale)
                    ),
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
            ReaderPageMarginsSettings(
                padding = value.padding,
                testTagPrefix = "options-reading",
                onPaddingChange = { padding ->
                    onPreferencesChange(selectedLibrary.id, value.copy(padding = padding))
                }
            )
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
                },
                joinFacingPages = value.joinPdfFacingPages,
                onJoinFacingPagesChange = { enabled ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(joinPdfFacingPages = enabled)
                    )
                },
                invertColors = value.invertPdfColors,
                onInvertColorsChange = { enabled ->
                    onPreferencesChange(
                        selectedLibrary.id,
                        value.copy(invertPdfColors = enabled)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.settings_epub_text),
                style = MaterialTheme.typography.titleMedium
            )
            EpubTypesettingSettings(
                value = value,
                testTagPrefix = "options-reading-epub",
                onPreferencesChange = { next -> onPreferencesChange(selectedLibrary.id, next) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.settings_unfolded_screens),
                style = MaterialTheme.typography.titleMedium
            )
            ReaderSpreadSettings(
                value = value,
                layoutMode = ReaderLayoutMode.PAGINATED,
                testTagPrefix = "options-reading",
                onPreferencesChange = { next -> onPreferencesChange(selectedLibrary.id, next) }
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** EPUB text alignment and hyphenation, shared by library settings and the in-book options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpubTypesettingSettings(
    value: LibraryReaderPreferences,
    testTagPrefix: String,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit
) {
    Text(
        stringResource(R.string.settings_text_alignment),
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EpubTextAlignment.values().forEach { alignment ->
            FilterChip(
                selected = value.textAlignment == alignment,
                onClick = { onPreferencesChange(value.copy(textAlignment = alignment)) },
                label = { Text(stringResource(alignment.displayNameRes)) },
                modifier = Modifier.testTag("$testTagPrefix-text-align-${alignment.name.lowercase()}")
            )
        }
    }
    Text(
        stringResource(R.string.settings_hyphenation),
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EpubHyphenation.values().forEach { hyphenation ->
            FilterChip(
                selected = value.hyphenation == hyphenation,
                onClick = { onPreferencesChange(value.copy(hyphenation = hyphenation)) },
                label = { Text(stringResource(hyphenation.displayNameRes)) },
                modifier = Modifier.testTag("$testTagPrefix-hyphenation-${hyphenation.name.lowercase()}")
            )
        }
    }
    Text(
        stringResource(
            if (value.hyphenation == EpubHyphenation.AUTO) {
                R.string.settings_hyphenation_auto_note
            } else {
                R.string.settings_hyphenation_note
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ReaderFormatLayoutSettings(
    formatLabel: String,
    layoutMode: ReaderLayoutMode,
    pageGapDp: Float,
    testTagPrefix: String,
    onLayoutModeChange: (ReaderLayoutMode) -> Unit,
    onPageGapChange: (Float) -> Unit,
    joinFacingPages: Boolean? = null,
    onJoinFacingPagesChange: ((Boolean) -> Unit)? = null,
    invertColors: Boolean? = null,
    onInvertColorsChange: ((Boolean) -> Unit)? = null
) {
    ReaderLayoutModeSettings(
        formatLabel = formatLabel,
        layoutMode = layoutMode,
        testTagPrefix = testTagPrefix,
        onLayoutModeChange = onLayoutModeChange
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.settings_continuous_page_gap, pageGapDp.toInt()),
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
    if (joinFacingPages != null && onJoinFacingPagesChange != null) {
        AppPreferenceSwitchRow(
            title = stringResource(R.string.settings_remove_page_gap),
            summary = stringResource(R.string.settings_remove_page_gap_summary),
            checked = joinFacingPages,
            enabled = layoutMode == ReaderLayoutMode.PAGINATED,
            testTag = "$testTagPrefix-join-facing-pages",
            onCheckedChange = onJoinFacingPagesChange
        )
    }
    if (invertColors != null && onInvertColorsChange != null) {
        AppPreferenceSwitchRow(
            title = stringResource(R.string.settings_invert_pdf_colors),
            summary = stringResource(R.string.settings_invert_pdf_colors_summary),
            checked = invertColors,
            testTag = "$testTagPrefix-invert-colors",
            onCheckedChange = onInvertColorsChange
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
    Text(
        stringResource(R.string.settings_format_layout, formatLabel),
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderLayoutMode.values().forEach { mode ->
            FilterChip(
                selected = layoutMode == mode,
                onClick = { onLayoutModeChange(mode) },
                label = { Text(stringResource(mode.displayNameRes)) },
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
    Text(
        stringResource(R.string.settings_tap_zones),
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderTapZoneLayout.values().forEach { layout ->
            FilterChip(
                selected = value.tapZoneLayout == layout,
                onClick = { onPreferencesChange(value.copy(tapZoneLayout = layout)) },
                label = { Text(stringResource(layout.displayNameRes)) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-layout-${layout.name.lowercase()}"
                )
            )
        }
    }
    Text(
        stringResource(R.string.settings_tap_zone_inversion),
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderTapZoneInvertMode.values().forEach { invertMode ->
            FilterChip(
                selected = value.tapZoneInvertMode == invertMode,
                onClick = { onPreferencesChange(value.copy(tapZoneInvertMode = invertMode)) },
                label = { Text(stringResource(invertMode.displayNameRes)) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-invert-${invertMode.name.lowercase()}"
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderThemeChips(
    selected: EpubReaderTheme,
    presetSelected: Boolean,
    testTagPrefix: String,
    onSelected: (EpubReaderTheme) -> Unit,
    showCustom: Boolean = false,
    customSelected: Boolean = false,
    onCustomSelected: () -> Unit = {}
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EPUB_THEME_OPTIONS.forEach { theme ->
            FilterChip(
                selected = presetSelected && selected == theme,
                onClick = { onSelected(theme) },
                label = { Text(stringResource(theme.labelRes)) },
                modifier = Modifier.testTag(
                    "$testTagPrefix-${theme.name.lowercase()}"
                )
            )
        }
        if (showCustom) {
            FilterChip(
                selected = customSelected,
                onClick = onCustomSelected,
                label = { Text(stringResource(R.string.sheet_custom_background_custom)) },
                modifier = Modifier.testTag("$testTagPrefix-custom")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderTextColorSettings(
    value: LibraryReaderPreferences,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit,
    testTagPrefix: String
) {
    var customTextColorDialogVisible by remember { mutableStateOf(false) }
    val systemDarkMode = isSystemInDarkTheme()
    val defaultTextColor = defaultReaderTextColor(
        customBackgroundActive = activeCustomReaderBackground(value) != null,
        presetTextColor = cssHexColorInt(value.backgroundTheme.foregroundCss),
        systemDarkMode = systemDarkMode
    )
    Text(
        stringResource(R.string.sheet_text_color),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        stringResource(R.string.sheet_text_color_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = !value.useCustomTextColor,
            onClick = {
                if (value.useCustomTextColor) {
                    onPreferencesChange(value.copy(useCustomTextColor = false))
                }
            },
            label = { Text(stringResource(R.string.sheet_text_color_default)) },
            modifier = Modifier.testTag("$testTagPrefix-text-color-default")
        )
        FilterChip(
            selected = value.useCustomTextColor,
            onClick = {
                when {
                    value.customTextColor == null ->
                        customTextColorDialogVisible = true
                    !value.useCustomTextColor ->
                        onPreferencesChange(value.copy(useCustomTextColor = true))
                }
            },
            label = { Text(stringResource(R.string.sheet_text_color_custom)) },
            modifier = Modifier.testTag("$testTagPrefix-text-color-custom")
        )
    }
    if (value.useCustomTextColor) {
        AppPreferenceSelectionRow(
            title = stringResource(R.string.sheet_text_color_custom),
            value = value.customTextColor?.let(::customReaderBackgroundCss)
                ?: stringResource(R.string.sheet_text_color_default),
            summary = stringResource(R.string.sheet_text_color_custom_summary),
            testTag = "$testTagPrefix-text-color",
            onClick = { customTextColorDialogVisible = true }
        )
    }
    if (customTextColorDialogVisible) {
        ReaderCustomTextColorDialog(
            initialColor = value.customTextColor,
            pageBackgroundColor = readerContentBackgroundColor(value),
            defaultTextColor = defaultTextColor,
            testTagPrefix = testTagPrefix,
            onApply = { color ->
                onPreferencesChange(
                    value.copy(
                        useCustomTextColor = true,
                        customTextColor = color
                    )
                )
                customTextColorDialogVisible = false
            },
            onReset = {
                onPreferencesChange(
                    value.copy(
                        useCustomTextColor = false,
                        customTextColor = null
                    )
                )
                customTextColorDialogVisible = false
            },
            onDismiss = { customTextColorDialogVisible = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderAppearanceSettings(
    value: LibraryReaderPreferences,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit,
    testTagPrefix: String,
    showOptionsTheme: Boolean,
    showBackgroundTheme: Boolean
) {
    var customBackgroundDialogVisible by remember { mutableStateOf(false) }

    if (showOptionsTheme) {
        Text(
            stringResource(R.string.sheet_reader_theme_options),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.sheet_reader_theme_options_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        ReaderThemeChips(
            selected = value.theme,
            presetSelected = true,
            testTagPrefix = "$testTagPrefix-theme",
            onSelected = { theme -> onPreferencesChange(value.copy(theme = theme)) }
        )
    }

    if (showBackgroundTheme) {
        if (showOptionsTheme) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        }
        Text(
            stringResource(R.string.sheet_reading_background),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.sheet_reading_background_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        ReaderThemeChips(
            selected = value.backgroundTheme,
            presetSelected = !value.useCustomBackground,
            testTagPrefix = "$testTagPrefix-background-theme",
            onSelected = { theme ->
                onPreferencesChange(
                    value.copy(
                        backgroundTheme = theme,
                        useCustomBackground = false
                    )
                )
            },
            showCustom = true,
            customSelected = value.useCustomBackground,
            onCustomSelected = {
                when {
                    value.customBackgroundColor == null ->
                        customBackgroundDialogVisible = true
                    !value.useCustomBackground ->
                        onPreferencesChange(value.copy(useCustomBackground = true))
                }
            }
        )
        if (value.useCustomBackground) {
            AppPreferenceSelectionRow(
                title = stringResource(R.string.sheet_custom_background),
                value = value.customBackgroundColor?.let(::customReaderBackgroundCss)
                    ?: stringResource(R.string.sheet_custom_background_default),
                summary = stringResource(R.string.sheet_custom_background_summary),
                testTag = "$testTagPrefix-custom-background",
                onClick = { customBackgroundDialogVisible = true }
            )
        }
        if (customBackgroundDialogVisible) {
            ReaderCustomBackgroundDialog(
                initialColor = value.customBackgroundColor,
                testTagPrefix = testTagPrefix,
                onApply = { color ->
                    onPreferencesChange(
                        value.copy(
                            useCustomBackground = true,
                            customBackgroundColor = color
                        )
                    )
                    customBackgroundDialogVisible = false
                },
                onReset = {
                    onPreferencesChange(
                        value.copy(
                            useCustomBackground = false,
                            customBackgroundColor = null
                        )
                    )
                    customBackgroundDialogVisible = false
                },
                onDismiss = { customBackgroundDialogVisible = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderConfigurationControls(
    value: LibraryReaderPreferences,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit,
    showTapZoneTutorialAfterChanges: Boolean = true,
    onShowTapZoneTutorialAfterChangesChange: (Boolean) -> Unit = {},
    testTagPrefix: String = "reader-options-reading",
    format: ReaderConfigurationFormat = ReaderConfigurationFormat.EPUB,
    onCustomFontRequest: () -> Unit = {},
    onCustomFontRemove: () -> Unit = {}
) {
    AppPreferenceSwitchRow(
        title = stringResource(R.string.settings_volume_page_navigation),
        summary = stringResource(R.string.settings_volume_page_navigation_summary),
        checked = value.volumeButtonPageNavigation,
        testTag = "$testTagPrefix-volume-button-navigation",
        onCheckedChange = { enabled ->
            onPreferencesChange(value.copy(volumeButtonPageNavigation = enabled))
        }
    )
    AppPreferenceSwitchRow(
        title = stringResource(R.string.settings_swap_volume_buttons),
        summary = stringResource(R.string.settings_swap_volume_buttons_summary),
        checked = value.reverseVolumeButtonNavigation,
        enabled = value.volumeButtonPageNavigation,
        testTag = "$testTagPrefix-reverse-volume-button-navigation",
        onCheckedChange = { reversed ->
            onPreferencesChange(value.copy(reverseVolumeButtonNavigation = reversed))
        }
    )
    Text(
        stringResource(R.string.settings_reading_direction),
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryReadingDirection.values().forEach { direction ->
            FilterChip(
                selected = value.readingDirection == direction,
                onClick = { onPreferencesChange(value.copy(readingDirection = direction)) },
                label = { Text(stringResource(direction.displayNameRes)) },
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
    if (format != ReaderConfigurationFormat.PDF) {
        AppPreferenceSwitchRow(
            title = stringResource(R.string.settings_show_page_progress),
            summary = stringResource(R.string.settings_show_page_progress_summary),
            checked = value.showPageProgress,
            testTag = "$testTagPrefix-show-page-progress",
            onCheckedChange = { enabled ->
                onPreferencesChange(value.copy(showPageProgress = enabled))
            }
        )
    }
    AppPreferenceSwitchRow(
        title = stringResource(format.tapZoneTutorialPreferenceTitleRes()),
        summary = stringResource(R.string.settings_tap_zone_tutorial_summary),
        checked = showTapZoneTutorialAfterChanges,
        testTag = "$testTagPrefix-tap-zone-tutorial-after-changes",
        onCheckedChange = onShowTapZoneTutorialAfterChangesChange
    )
    ReaderAppearanceSettings(
        value = value,
        onPreferencesChange = onPreferencesChange,
        testTagPrefix = testTagPrefix,
        showOptionsTheme = format != ReaderConfigurationFormat.COMIC,
        showBackgroundTheme = format == ReaderConfigurationFormat.EPUB
    )
    if (format == ReaderConfigurationFormat.EPUB) {
        ReaderTextColorSettings(
            value = value,
            onPreferencesChange = onPreferencesChange,
            testTagPrefix = testTagPrefix
        )
        AppPreferenceSwitchRow(
            title = stringResource(R.string.settings_prefer_server_progress),
            summary = stringResource(R.string.settings_prefer_server_progress_summary),
            checked = value.preferServerProgressOnReopen,
            testTag = "$testTagPrefix-prefer-server-progress-on-reopen",
            onCheckedChange = { enabled ->
                onPreferencesChange(value.copy(preferServerProgressOnReopen = enabled))
            }
        )
        var fontMenuExpanded by remember { mutableStateOf(false) }
        val customFontLabel = value.customFont
            ?.takeIf { value.fontFamily == EpubReaderFontFamily.CUSTOM }
            ?.displayName
        val selectedFontLabel = if (customFontLabel != null) {
            customFontLabel
        } else {
            stringResource(value.fontFamily.displayNameRes)
        }
        Text(
            stringResource(R.string.settings_fonts),
            style = MaterialTheme.typography.titleMedium
        )
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
                    stringResource(R.string.settings_normal_fonts),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EPUB_NORMAL_FONT_FAMILY_OPTIONS.forEach { fontFamily ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(fontFamily.displayNameRes))
                        },
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
                    stringResource(R.string.settings_accessibility_fonts),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EPUB_ACCESSIBILITY_FONT_FAMILY_OPTIONS.forEach { fontFamily ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(fontFamily.displayNameRes))
                        },
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
                    stringResource(R.string.settings_custom_font),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_choose_custom_font)) },
                    onClick = {
                        fontMenuExpanded = false
                        onCustomFontRequest()
                    },
                    modifier = Modifier.testTag("$testTagPrefix-font-family-custom-choose")
                )
                if (value.customFont != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    R.string.settings_remove_custom_font,
                                    value.customFont.displayName
                                )
                            )
                        },
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
                stringResource(
                    R.string.settings_line_spacing,
                    formatEpubLineSpacing(value.lineSpacing)
                ),
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
                stringResource(
                    R.string.settings_word_spacing,
                    formatEpubWordSpacing(value.wordSpacing)
                ),
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
        EpubTypesettingSettings(value, testTagPrefix, onPreferencesChange)
    }
    if (format != ReaderConfigurationFormat.COMIC) {
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
                stringResource(
                    R.string.settings_text_size,
                    formatEpubFontScale(value.fontScale)
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = { onPreferencesChange(value.copy(fontScale = value.fontScale + 0.1f)) },
                modifier = Modifier.testTag("$testTagPrefix-font-increase")
            ) { Text("A+") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    when (format) {
        ReaderConfigurationFormat.EPUB -> {
            ReaderLayoutModeSettings(
                formatLabel = "EPUB",
                layoutMode = value.epubLayoutMode,
                testTagPrefix = "$testTagPrefix-epub",
                onLayoutModeChange = { onPreferencesChange(value.copy(epubLayoutMode = it)) }
            )
            ReaderSpreadSettings(
                value = value,
                layoutMode = value.epubLayoutMode,
                testTagPrefix = "$testTagPrefix-epub",
                onPreferencesChange = onPreferencesChange
            )
            ReaderPageMarginsSettings(
                padding = value.padding,
                testTagPrefix = testTagPrefix,
                onPaddingChange = { padding ->
                    onPreferencesChange(value.copy(padding = padding))
                }
            )
        }
        ReaderConfigurationFormat.PDF -> ReaderFormatLayoutSettings(
            formatLabel = "PDF",
            layoutMode = value.pdfLayoutMode,
            pageGapDp = value.pdfPageGapDp,
            testTagPrefix = "$testTagPrefix-pdf",
            onLayoutModeChange = { onPreferencesChange(value.copy(pdfLayoutMode = it)) },
            onPageGapChange = { onPreferencesChange(value.copy(pdfPageGapDp = it)) },
            joinFacingPages = value.joinPdfFacingPages,
            onJoinFacingPagesChange = { onPreferencesChange(value.copy(joinPdfFacingPages = it)) },
            invertColors = value.invertPdfColors,
            onInvertColorsChange = { onPreferencesChange(value.copy(invertPdfColors = it)) }
        )
        ReaderConfigurationFormat.COMIC -> ReaderFormatLayoutSettings(
            formatLabel = "CBR/CBZ",
            layoutMode = value.comicLayoutMode,
            pageGapDp = value.comicPageGapDp,
            testTagPrefix = "$testTagPrefix-comic",
            onLayoutModeChange = { onPreferencesChange(value.copy(comicLayoutMode = it)) },
            onPageGapChange = { onPreferencesChange(value.copy(comicPageGapDp = it)) }
        )
    }
}

@Composable
private fun ReaderSpreadSettings(
    value: LibraryReaderPreferences,
    layoutMode: ReaderLayoutMode,
    testTagPrefix: String,
    onPreferencesChange: (LibraryReaderPreferences) -> Unit
) {
    val spreadEnabled = value.twoPageSpread == ReaderSpreadMode.AUTO
    AppPreferenceSwitchRow(
        title = stringResource(R.string.settings_two_page_spread),
        summary = stringResource(
            if (layoutMode == ReaderLayoutMode.PAGINATED) {
                R.string.settings_two_page_spread_summary_paginated
            } else {
                R.string.settings_two_page_spread_summary_other
            }
        ),
        checked = spreadEnabled,
        testTag = "$testTagPrefix-two-page-spread",
        onCheckedChange = { enabled ->
            onPreferencesChange(
                value.copy(twoPageSpread = if (enabled) ReaderSpreadMode.AUTO else ReaderSpreadMode.OFF)
            )
        }
    )
}

@Composable
private fun RgbComponentField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun ReaderCustomBackgroundDialog(
    initialColor: Int?,
    testTagPrefix: String,
    onApply: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    fun initialComponent(shift: Int): String =
        initialColor?.let { ((it shr shift) and 0xFF).toString() }.orEmpty()

    var red by remember(initialColor) { mutableStateOf(initialComponent(16)) }
    var green by remember(initialColor) { mutableStateOf(initialComponent(8)) }
    var blue by remember(initialColor) { mutableStateOf(initialComponent(0)) }
    val redValue = parseRgbComponent(red)
    val greenValue = parseRgbComponent(green)
    val blueValue = parseRgbComponent(blue)
    val color = if (redValue != null && greenValue != null && blueValue != null) {
        customReaderBackgroundColor(redValue, greenValue, blueValue)
    } else {
        null
    }
    val hasAnyInput = red.isNotEmpty() || green.isNotEmpty() || blue.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sheet_custom_background_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RgbComponentField(
                        label = stringResource(R.string.sheet_custom_background_red),
                        value = red,
                        onValueChange = { red = it },
                        modifier = Modifier.weight(1f).testTag("$testTagPrefix-custom-background-red")
                    )
                    RgbComponentField(
                        label = stringResource(R.string.sheet_custom_background_green),
                        value = green,
                        onValueChange = { green = it },
                        modifier = Modifier.weight(1f).testTag("$testTagPrefix-custom-background-green")
                    )
                    RgbComponentField(
                        label = stringResource(R.string.sheet_custom_background_blue),
                        value = blue,
                        onValueChange = { blue = it },
                        modifier = Modifier.weight(1f).testTag("$testTagPrefix-custom-background-blue")
                    )
                }
                Text(
                    stringResource(R.string.sheet_custom_background_preview),
                    style = MaterialTheme.typography.titleSmall
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            color?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (color != null) {
                        Text(
                            text = customReaderBackgroundCss(color),
                            color = Color(customReaderBackgroundTextColor(color)),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (color == null && hasAnyInput) {
                    Text(
                        stringResource(R.string.sheet_custom_background_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { color?.let(onApply) },
                enabled = color != null
            ) {
                Text(stringResource(R.string.sheet_custom_background_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.sheet_custom_background_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

@Composable
private fun ReaderCustomTextColorDialog(
    initialColor: Int?,
    pageBackgroundColor: Int,
    defaultTextColor: Int,
    testTagPrefix: String,
    onApply: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    fun initialComponent(shift: Int): String =
        initialColor?.let { ((it shr shift) and 0xFF).toString() }.orEmpty()

    var red by remember(initialColor) { mutableStateOf(initialComponent(16)) }
    var green by remember(initialColor) { mutableStateOf(initialComponent(8)) }
    var blue by remember(initialColor) { mutableStateOf(initialComponent(0)) }
    val redValue = parseRgbComponent(red)
    val greenValue = parseRgbComponent(green)
    val blueValue = parseRgbComponent(blue)
    val color = if (redValue != null && greenValue != null && blueValue != null) {
        customReaderBackgroundColor(redValue, greenValue, blueValue)
    } else {
        null
    }
    val hasAnyInput = red.isNotEmpty() || green.isNotEmpty() || blue.isNotEmpty()
    val previewTextColor = color ?: defaultTextColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sheet_text_color_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RgbComponentField(
                        label = stringResource(R.string.sheet_custom_background_red),
                        value = red,
                        onValueChange = { red = it },
                        modifier = Modifier.weight(1f).testTag("$testTagPrefix-text-color-red")
                    )
                    RgbComponentField(
                        label = stringResource(R.string.sheet_custom_background_green),
                        value = green,
                        onValueChange = { green = it },
                        modifier = Modifier.weight(1f).testTag("$testTagPrefix-text-color-green")
                    )
                    RgbComponentField(
                        label = stringResource(R.string.sheet_custom_background_blue),
                        value = blue,
                        onValueChange = { blue = it },
                        modifier = Modifier.weight(1f).testTag("$testTagPrefix-text-color-blue")
                    )
                }
                Text(
                    stringResource(R.string.sheet_text_color_preview),
                    style = MaterialTheme.typography.titleSmall
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(pageBackgroundColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.sheet_text_color_sample),
                        color = Color(previewTextColor),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (color == null && hasAnyInput) {
                    Text(
                        stringResource(R.string.sheet_custom_background_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { color?.let(onApply) },
                enabled = color != null
            ) {
                Text(stringResource(R.string.sheet_custom_background_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.sheet_custom_background_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
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
                OrbitEyebrow(stringResource(R.string.settings_about_eyebrow))
                Text(
                    stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(R.string.settings_about_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.settings_about_description))
            }
        }
        item {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.settings_about_build_info),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        R.string.settings_about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                )
                Text(
                    stringResource(R.string.settings_about_connected_server),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(state.serverUrl, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_about_updates),
                    style = MaterialTheme.typography.titleMedium
                )
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
                    Text(stringResource(R.string.settings_about_check_updates))
                }
                val updateStatusRes = when (releaseCheckStatus) {
                    ReleaseCheckStatus.UP_TO_DATE -> R.string.settings_about_up_to_date
                    ReleaseCheckStatus.UPDATE_AVAILABLE ->
                        R.string.settings_about_update_available
                    ReleaseCheckStatus.ERROR -> R.string.settings_about_update_error
                    else -> null
                }
                updateStatusRes?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_about_not_official),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.settings_about_not_official_body))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_about_acknowledgements),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.settings_about_acknowledgements_body))
                Text(stringResource(R.string.settings_about_acknowledgements_libraries))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_about_project_links),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { openLink("https://github.com/van-geaux/lagrange-reader") }) {
                    Text(stringResource(R.string.settings_about_link_github))
                }
                TextButton(onClick = { openLink("https://github.com/BookOrbit") }) {
                    Text(stringResource(R.string.settings_about_link_bookorbit))
                }
                TextButton(onClick = { openLink("https://readium.org/") }) {
                    Text(stringResource(R.string.settings_about_link_readium))
                }
                TextButton(onClick = { openLink("https://github.com/van-geaux/lagrange-reader/blob/main/LICENSE") }) {
                    Text(stringResource(R.string.settings_about_link_license))
                }
                TextButton(onClick = { openLink("https://github.com/van-geaux/lagrange-reader/blob/main/THIRD_PARTY_NOTICES.md") }) {
                    Text(stringResource(R.string.settings_about_link_notices))
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
    val cacheKey = url?.let(::catalogImageCacheKey)
    val bitmap by produceState<Bitmap?>(initialValue = cacheKey?.let(coverBitmapCache::get), url) {
        value = url?.let { imageUrl -> loadCatalogBitmap(imageUrl, loader) }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(label.substringAfterLast(" ").take(1).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun catalogImageCacheKey(url: String): String = url

private suspend fun loadCatalogBitmap(
    url: String,
    loader: suspend (String) -> ByteArray?
): Bitmap? {
    val key = catalogImageCacheKey(url)
    coverBitmapCache.get(key)?.let { return it }
    val lock = coverLoadLocks[(key.hashCode() and Int.MAX_VALUE) % coverLoadLocks.size]
    return lock.withLock {
        coverBitmapCache.get(key)?.let { return@withLock it }
        val bytes = loadCatalogImageWithRetry(url, loader) ?: return@withLock null
        val bitmap = try {
            withContext(Dispatchers.Default) {
                decodeCoverBitmap(bytes, targetWidth = 256, targetHeight = 384)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        bitmap?.also {
            coverBitmapCache.put(key, bitmap)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RefreshableHomeFeed(
    state: BrowserState,
    localOnDeckEnabled: Boolean,
    modifier: Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    localBooksLoader: suspend () -> List<BookSummary>,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    onLocalBooksSelected: () -> Unit,
    onHomeSectionSelected: (HomeSection) -> Unit
) {
    val downloadedLocalBooks by produceState<List<BookSummary>?>(
        initialValue = null,
        state.serverUrl,
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
            localOnDeckEnabled = localOnDeckEnabled,
            books = state.homeBooks,
            downloadedLocalBooks = downloadedLocalBooks,
            modifier = Modifier.fillMaxSize(),
            coverLoader = coverLoader,
            seriesCatalogLoader = seriesCatalogLoader,
            onBookSelected = onBookSelected,
            onSeriesSelected = onSeriesSelected,
            onRemoveFromCurrentlyReading = onRemoveFromCurrentlyReading,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onClearFailedDownload = onClearFailedDownload,
            onDeleteLocalCopy = onDeleteLocalCopy,
            onMarkAsRead = onMarkAsRead,
            onMarkAsUnread = onMarkAsUnread,
            onMarkAsStatus = onMarkAsStatus,
            onHomeSectionSelected = onHomeSectionSelected,
            onLocalBooksSelected = onLocalBooksSelected
        )
    }
}

@Composable
private fun HomeFeed(
    state: BrowserState,
    localOnDeckEnabled: Boolean = true,
    books: List<BookSummary> = state.books,
    downloadedLocalBooks: List<BookSummary>? = null,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    seriesCatalogLoader: (suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage)? = null,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    onLocalBooksSelected: (() -> Unit)? = null,
    onHomeSectionSelected: ((HomeSection) -> Unit)? = null,
    localBooksLibraryId: String? = null,
    showHeader: Boolean = false
) {
    val libraryScopedBooks = remember(books, localBooksLibraryId) {
        homeFeedBooks(books, localBooksLibraryId)
    }
    // Provider Home shelves are server-wide; filter them before rendering a
    // library Recommended feed instead of reusing aggregate contents.
    val serverShelves = homeFeedShelves(state.homeShelves, localBooksLibraryId)
    val hiddenSections = serverShelves.hiddenSections
    val onDeckEnabled = state.homeShelfPreferences.effectiveOnDeckEnabled(
        localFallbackEnabled = localOnDeckEnabled,
        hasProviderOwnedShelves = serverShelves.isServerProvided
    )
    val currentlyReadingAll = remember(libraryScopedBooks, serverShelves) {
        if (serverShelves.isServerProvided) {
            sortCurrentlyReadingBooks(
                serverShelves.booksBySection[HomeSection.CURRENTLY_READING].orEmpty()
            )
        } else {
            currentlyReadingBooks(libraryScopedBooks, limit = null)
        }
    }
    val onDeckAll = remember(
        libraryScopedBooks,
        serverShelves,
        onDeckEnabled
    ) {
        if (!onDeckEnabled) emptyList()
        else if (serverShelves.providesBooks(HomeSection.ON_DECK)) {
            serverShelves.booksBySection[HomeSection.ON_DECK].orEmpty()
        } else generatedOnDeckBooks(
            books = libraryScopedBooks,
            enabled = true,
            limit = null
        )
    }
    val wantToReadAll = remember(libraryScopedBooks, serverShelves) {
        if (serverShelves.isServerProvided) serverShelves.booksBySection[HomeSection.WANT_TO_READ].orEmpty()
        else wantToReadBooks(libraryScopedBooks, limit = null)
    }
    val currentlyReading = currentlyReadingAll.take(HOME_PREVIEW_LIMIT)
    val onDeck = onDeckAll.take(HOME_PREVIEW_LIMIT)
    val onDeckSeriesAll = if (serverShelves.isServerProvided) {
        val seriesBooks = serverShelves.seriesBySection[HomeSection.ON_DECK].orEmpty()
        seriesBooks.map { book -> (book.seriesName ?: book.title) to book }
    } else emptyList()
    val wantToRead = wantToReadAll.take(HOME_PREVIEW_LIMIT)
    val recentlyAddedBooksAll = if (serverShelves.isServerProvided) {
        serverShelves.booksBySection[HomeSection.RECENTLY_ADDED_BOOKS].orEmpty()
    } else {
        libraryScopedBooks.sortedWith(
            compareByDescending<BookSummary> { it.addedAtMillis != null }
                .thenByDescending { it.addedAtMillis ?: 0L }
        )
    }
    val preferBookOrbitImportSequence = !serverShelves.isServerProvided
    val fallbackRecentSeries = remember(libraryScopedBooks, preferBookOrbitImportSequence) {
        homeSeriesSummaries(
            books = libraryScopedBooks,
            useUpdatedAt = false,
            preferBookOrbitImportSequence = preferBookOrbitImportSequence
        )
    }
    val authoritativeRecentSeries by produceState<List<SeriesSummary>?>(
        initialValue = null,
        libraryScopedBooks,
        seriesCatalogLoader,
        serverShelves.isServerProvided
    ) {
        value = if (serverShelves.isServerProvided) null else seriesCatalogLoader?.let { loader ->
            runCatching {
                loadCompleteSeriesCatalog { page ->
                    loader(
                        SeriesCatalogFilter(
                            sort = SeriesSortOption.LAST_ADDED,
                            direction = SortDirection.DESCENDING
                        ),
                        page
                    )
                }.items
            }.getOrNull()
        }
    }
    val recentlyReleasedBooksAll = if (serverShelves.isServerProvided) {
        serverShelves.booksBySection[HomeSection.RECENTLY_RELEASED_BOOKS].orEmpty()
    } else emptyList()
    fun providerSeriesShelf(section: HomeSection): List<Pair<String, BookSummary>> {
        val seriesBooks = serverShelves.seriesBySection[section].orEmpty()
        return seriesBooks.map { book -> (book.seriesName ?: book.title) to book }
    }
    val recentSeriesAll = if (serverShelves.isServerProvided) {
        providerSeriesShelf(HomeSection.RECENTLY_ADDED_SERIES)
    } else {
        homeSeriesShelfItems(
            series = authoritativeRecentSeries ?: fallbackRecentSeries,
            books = libraryScopedBooks,
            limit = Int.MAX_VALUE,
            preferBookOrbitImportSequence = true
        )
    }
    val updatedSeriesAll = if (serverShelves.isServerProvided) {
        providerSeriesShelf(HomeSection.RECENTLY_UPDATED_SERIES)
    } else recentSeries(libraryScopedBooks, useUpdatedAt = true, limit = null)
    val recentlyReadAll = if (serverShelves.isServerProvided) {
        serverShelves.booksBySection[HomeSection.RECENTLY_READ].orEmpty()
    } else recentlyReadBooks(libraryScopedBooks, limit = null)
    val recentlyAddedBooks = recentlyAddedBooksAll.take(HOME_PREVIEW_LIMIT)
    val recentlyReleasedBooks = recentlyReleasedBooksAll.take(HOME_PREVIEW_LIMIT)
    val recentSeries = recentSeriesAll.take(HOME_PREVIEW_LIMIT)
    val updatedSeries = updatedSeriesAll.take(HOME_PREVIEW_LIMIT)
    val recentlyRead = recentlyReadAll.take(HOME_PREVIEW_LIMIT)
    val localBooks = remember(libraryScopedBooks, downloadedLocalBooks, localBooksLibraryId) {
        homeLocalBooksPreview(
            catalogHomeBooks = libraryScopedBooks,
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
                    Text(stringResource(R.string.browse_home), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name
                            ?: stringResource(R.string.browse_your_library),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (currentlyReading.isNotEmpty()) {
            item {
                BookShelf(
                    title = if (serverShelves.isServerProvided) {
                        stringResource(R.string.browse_continue_reading)
                    } else {
                        stringResource(R.string.browse_currently_reading)
                    },
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
                    onMarkAsUnread = availableMarkAsUnread,
                    onMarkAsStatus = onMarkAsStatus,
                    onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.CURRENTLY_READING) }
                )
            }
        }
        if (onDeckSeriesAll.isNotEmpty()) item {
            SeriesShelf(stringResource(R.string.browse_on_deck), onDeckSeriesAll.take(HOME_PREVIEW_LIMIT), coverLoader, onSeriesSelected, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.ON_DECK) })
        } else if (onDeck.isNotEmpty()) item {
            BookShelf(stringResource(R.string.browse_on_deck), onDeck, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread, onMarkAsStatus = onMarkAsStatus, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.ON_DECK) })
        }
        if (HomeSection.WANT_TO_READ !in hiddenSections && wantToRead.isNotEmpty()) item {
            BookShelf(stringResource(R.string.browse_want_to_read), wantToRead, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread, onMarkAsStatus = onMarkAsStatus, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.WANT_TO_READ) })
        }
        if (HomeSection.RECENTLY_ADDED_BOOKS !in hiddenSections && recentlyAddedBooks.isNotEmpty()) item {
            BookShelf(stringResource(R.string.browse_recently_added_books), recentlyAddedBooks, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread, onMarkAsStatus = onMarkAsStatus, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.RECENTLY_ADDED_BOOKS) })
        }
        if (HomeSection.RECENTLY_RELEASED_BOOKS !in hiddenSections && recentlyReleasedBooks.isNotEmpty()) item {
            BookShelf(stringResource(R.string.browse_recently_released_books), recentlyReleasedBooks, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread, onMarkAsStatus = onMarkAsStatus, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.RECENTLY_RELEASED_BOOKS) })
        }
        if (recentSeries.isNotEmpty()) item { SeriesShelf(stringResource(R.string.browse_recently_added_series), recentSeries, coverLoader, onSeriesSelected, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.RECENTLY_ADDED_SERIES) }) }
        if (updatedSeries.isNotEmpty()) item { SeriesShelf(stringResource(R.string.browse_recently_updated_series), updatedSeries, coverLoader, onSeriesSelected, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.RECENTLY_UPDATED_SERIES) }) }
        if (HomeSection.RECENTLY_READ !in hiddenSections && recentlyRead.isNotEmpty()) item {
            BookShelf(stringResource(R.string.browse_recently_read_books), recentlyRead, coverLoader, onBookSelected, state = state, onDownload = onDownload, onCancelDownload = onCancelDownload, onClearFailedDownload = onClearFailedDownload, onDeleteLocalCopy = onDeleteLocalCopy, onMarkAsRead = availableMarkAsRead, onMarkAsUnread = availableMarkAsUnread, onMarkAsStatus = onMarkAsStatus, onSeeAll = { onHomeSectionSelected?.invoke(HomeSection.RECENTLY_READ) })
        }
        if (localBooks.isNotEmpty()) item {
            BookShelf(
                title = stringResource(R.string.browse_local_books),
                books = localBooks,
                coverLoader = coverLoader,
                onBookSelected = onBookSelected,
                state = state,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onClearFailedDownload = onClearFailedDownload,
                onDeleteLocalCopy = onDeleteLocalCopy,
                onMarkAsRead = availableMarkAsRead,
                onMarkAsUnread = availableMarkAsUnread, onMarkAsStatus = onMarkAsStatus,
                onSeeAll = onLocalBooksSelected
            )
        }
        if (state.isLoadingBooks) item { LoadingFeedRow(stringResource(R.string.browse_loading_books)) }
        val hasServerHomeContent = serverShelves.booksBySection.values.any { it.isNotEmpty() } ||
            serverShelves.seriesBySection.values.any { it.isNotEmpty() }
        if (!state.isLoadingBooks && state.books.isEmpty() && !hasServerHomeContent) {
            item {
                Text(
                    stringResource(R.string.browse_no_books_in_library),
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)? = null,
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
                    onMarkAsUnread = onMarkAsUnread?.let { mark -> { mark(book) } },
                    onMarkAsStatus = onMarkAsStatus?.let { mark -> { statusBook, status -> mark(statusBook, status) } },
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
    onSeriesSelected: (String) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfTitle(title, onSeeAll)
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
            TextButton(
                onClick = onSeeAll,
                modifier = Modifier.testTag("home_see_all_${title.lowercase().replace(' ', '_')}")
            ) { Text(stringResource(R.string.browse_see_all)) }
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)? = null,
    onDeleteLocalCopy: (() -> Unit)? = null,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    val isBookCard = displayTitle == book.title
    val fileId = book.fileId
    val isDownloading = fileId != null && fileId in (downloadState?.downloadingFileIds.orEmpty())
    val failed = fileId != null && fileId in (downloadState?.failedDownloadFileIds.orEmpty())
    var showActions by remember(book.id) { mutableStateOf(false) }
    var showStatusMenu by remember(book.id) { mutableStateOf(false) }
    val hasActions = onRemoveFromCurrentlyReading != null || onMarkAsStatus != null || onMarkAsRead != null || onMarkAsUnread != null || (book.isDownloaded && onDeleteLocalCopy != null) || (onDownload != null && !book.isServerMissing) || isDownloading || failed
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
                            contentDescription = stringResource(R.string.browse_more_options_for, book.title)
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false }
                    ) {
                        onMarkAsStatus?.let { markAsStatus ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_mark_as)) },
                                onClick = {
                                    showActions = false
                                    showStatusMenu = true
                                }
                            )
                        }
                        if (onMarkAsStatus == null) onMarkAsRead?.let { markAsRead ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_mark_as_read)) },
                                onClick = {
                                    showActions = false
                                    markAsRead()
                                }
                            )
                        }
                        if (onMarkAsStatus == null) onMarkAsUnread?.let { markAsUnread ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_mark_as_unread)) },
                                onClick = {
                                    showActions = false
                                    markAsUnread()
                                }
                            )
                        }
                        onRemoveFromCurrentlyReading?.let { remove ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_remove_from_currently_reading)) },
                                onClick = {
                                    showActions = false
                                    remove()
                                }
                            )
                        }
                        if (isDownloading) onCancelDownload?.let { cancel -> DropdownMenuItem(text = { Text(stringResource(R.string.common_cancel)) }, onClick = { showActions = false; cancel() }) }
                        else if (failed) {
                            if (!book.isServerMissing) onDownload?.let { retry -> DropdownMenuItem(text = { Text(stringResource(R.string.common_retry)) }, onClick = { showActions = false; retry() }) }
                            onClearFailedDownload?.let { clear -> DropdownMenuItem(text = { Text(stringResource(R.string.browse_clear)) }, onClick = { showActions = false; clear() }) }
                        } else if (!book.isDownloaded && !book.isServerMissing && downloadState?.isOfflineSnapshot != true) onDownload?.let { download -> DropdownMenuItem(text = { Text(stringResource(R.string.browse_download_local)) }, onClick = { showActions = false; download() }) }
                        if (book.isDownloaded) onDeleteLocalCopy?.let { deleteLocal -> DropdownMenuItem(text = { Text(stringResource(R.string.browse_delete_local)) }, onClick = { showActions = false; deleteLocal() }) }
                    }
                    onMarkAsStatus?.let { markAsStatus ->
                        BookDetailReadingStatusMenu(
                            expanded = showStatusMenu,
                            currentStatus = book.readStatus,
                            onDismissRequest = { showStatusMenu = false },
                            onStatusSelected = { status ->
                                showStatusMenu = false
                                markAsStatus(book, status)
                            }
                        )
                    }
                }
            }
        }
        BookAvailableFormatsLabel(book)
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(book.coverAspectRatio.widthToHeight),
            content = {
                BookCover(book, coverLoader)
                overlay()
                if (book.isUnread() && !book.isServerMissing) {
                    val markerSize = minOf(28.dp, maxWidth * 0.18f).coerceAtLeast(20.dp)
                    val unreadMarkerDescription = stringResource(R.string.browse_unread_book)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(markerSize)
                            .testTag("unread-book-marker")
                            .semantics { contentDescription = unreadMarkerDescription }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val fold = size.width * 0.56f
                            val marker = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(size.width - fold, 0f)
                                lineTo(size.width, fold)
                                close()
                            }
                            drawPath(path = marker, color = Color(0xFFFFC107))
                            drawLine(
                                color = Color(0xFF8D6E00),
                                start = Offset(size.width - fold, 0f),
                                end = Offset(size.width, fold),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
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
    val coverDescription = stringResource(R.string.browse_cover_for, book.title)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(book.coverAspectRatio.widthToHeight)
            .clip(MaterialTheme.shapes.small)
            .background(Brush.linearGradient(colors))
            .semantics { contentDescription = coverDescription },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = coverDescription,
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
            val missingBookDescription =
                stringResource(R.string.browse_missing_cd, book.title)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCCFFC107))
                    .semantics {
                        contentDescription = missingBookDescription
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.browse_missing),
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    onClearFailedDownload: (BookSummary) -> Unit = {}
) {
    LibraryBookList(
        title = stringResource(R.string.browse_search_results),
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
        onMarkAsStatus = onMarkAsStatus,
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
    onLoadMoreLibraryBooks: () -> Unit,
    libraryBookCatalogAvailable: Boolean,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    catalogImageLoader: suspend (String) -> ByteArray?,
    localBooksLoader: suspend () -> List<BookSummary>,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onSeriesNameSelected: (String) -> Unit,
    onRemoveFromCurrentlyReading: (BookSummary) -> Unit,
    onDownload: ((BookSummary) -> Unit)? = null,
    onBulkDownload: ((String, List<BookSummary>) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDeleteLocalCopies: (List<BookSummary>) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit,
    onLocalBooksSelected: () -> Unit,
    onHomeSectionSelected: (HomeSection) -> Unit
) {
    val downloadedLocalBooks by produceState<List<BookSummary>?>(
        initialValue = null,
        state.localBooksRevision,
        state.selectedLibraryId
    ) {
        value = runCatching { localBooksLoader() }.getOrNull()
    }
    LaunchedEffect(tab, state.selectedLibraryId) {
        if (tab == LibraryTab.BROWSE && state.books.isEmpty() && !state.isCatalogComplete && !state.isCatalogSyncing) {
            onLoadMoreLibraryBooks()
        }
    }
    val libraryDownloadCandidates = remember(state.books, state.selectedLibraryId) {
        booksDownloadableForLibrary(state.books, state.selectedLibraryId)
    }
    var showLibraryFileSelection by remember(state.selectedLibraryId) { mutableStateOf(false) }
    val browseOptionsExpanded = appPreferences.libraryBrowseOptionsExpanded[state.selectedLibraryId] ?: false
    val libraryDownloadScopeLabel = stringResource(R.string.browse_the_selected_library)
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
            title = stringResource(R.string.browse_select_library_downloads),
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
            title = { Text(stringResource(R.string.browse_download_library_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.browse_download_files_message,
                        libraryFrozenSelection.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { libraryDownloadConfirmStep = 2 },
                    modifier = Modifier.testTag("confirm-download-library-step1")
                ) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { libraryDownloadConfirmStep = 0 }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    } else if (libraryDownloadConfirmStep == 2) {
        AlertDialog(
            onDismissRequest = { libraryDownloadConfirmStep = 0 },
            title = { Text(stringResource(R.string.browse_are_you_sure)) },
            text = {
                Text(stringResource(R.string.browse_download_library_full_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryDownloadConfirmStep = 0
                        onBulkDownload?.invoke(libraryDownloadScopeLabel, libraryFrozenSelection)
                    },
                    modifier = Modifier.testTag("confirm-download-library-step2")
                ) { Text(stringResource(R.string.browse_download_library)) }
            },
            dismissButton = {
                TextButton(onClick = { libraryDownloadConfirmStep = 0 }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    pendingLibraryLocalDeletes?.let { books ->
        AlertDialog(
            onDismissRequest = { pendingLibraryLocalDeletes = null },
            title = {
                Text(
                    if (books.size == 1) {
                        stringResource(R.string.browse_delete_local_copy_title)
                    } else {
                        stringResource(R.string.browse_delete_local_copies_title, books.size)
                    }
                )
            },
            text = {
                Text(stringResource(R.string.browse_delete_files_full_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLibraryLocalDeletes = null
                        onDeleteLocalCopies(books)
                    },
                    modifier = Modifier.testTag("confirm-delete-library-local-copies")
                ) { Text(stringResource(R.string.browse_delete_local)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLibraryLocalDeletes = null }) { Text(stringResource(R.string.common_cancel)) }
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
                    text = { Text(stringResource(R.string.browse_recommended)) }
                )
                Tab(
                    selected = tab == LibraryTab.BROWSE,
                    onClick = { onTabChange(LibraryTab.BROWSE) },
                    text = { Text(stringResource(R.string.browse_browse)) }
                )
            }
            when (tab) {
                LibraryTab.RECOMMENDED -> HomeFeed(
                    state = state,
                    localOnDeckEnabled = appPreferences.showOnDeckWhenShelfSyncDisabled,
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
                    onMarkAsStatus = onMarkAsStatus,
                    onHomeSectionSelected = onHomeSectionSelected,
                    onLocalBooksSelected = onLocalBooksSelected,
                    localBooksLibraryId = state.selectedLibraryId,
                    showHeader = false
                )
                LibraryTab.BROWSE -> if (!libraryBookCatalogAvailable) {
                    SeriesCatalogScreen(
                        query = "",
                        initialFilter = SeriesCatalogFilter(libraryId = state.selectedLibraryId),
                        persistenceKey = "library-series-${state.selectedLibraryId}",
                        appPreferences = appPreferences,
                        onAppPreferencesChange = onAppPreferencesChange,
                        libraryOptions = state.libraries,
                        books = state.books,
                        loadAllPages = false,
                        modifier = Modifier.weight(1f),
                        loader = seriesCatalogLoader,
                        imageLoader = catalogImageLoader,
                        onSeriesSelected = { series ->
                            onSeriesNameSelected(series.name)
                            onSeriesSelected(series.id)
                        }
                    )
                } else LibraryBrowseScreen(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onLoadMoreLibraryBooks = onLoadMoreLibraryBooks,
                    coverLoader = coverLoader,
                    onBookSelected = onBookSelected,
                    onSeriesSelected = onSeriesSelected,
                    onDownload = onDownload,
                    onCancelDownload = onCancelDownload,
                    onClearFailedDownload = onClearFailedDownload,
                    onDeleteLocalCopy = onDeleteLocalCopy,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                    appPreferences = appPreferences,
                    onAppPreferencesChange = onAppPreferencesChange,
                    browseOptionsExpanded = browseOptionsExpanded,
                    onBrowseOptionsExpandedChange = { expanded ->
                        state.selectedLibraryId?.let { libraryId ->
                            onAppPreferencesChange(
                                appPreferences.copy(
                                    libraryBrowseOptionsExpanded = appPreferences.libraryBrowseOptionsExpanded +
                                        (libraryId to expanded)
                                )
                            )
                        }
                    },
                    headerContent = {
                        val showDownload = onBulkDownload != null &&
                            state.isCatalogComplete &&
                            !state.isCatalogSyncing &&
                            hasSelectableBulkDownloads(libraryDownloadCandidates)
                        if ((browseOptionsExpanded &&
                                (showDownload || libraryLocalCopies.isNotEmpty())) ||
                            libraryDownloadProgress != null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (browseOptionsExpanded) {
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
                                        ) { Text(stringResource(R.string.browse_download_library)) }
                                    }
                                    if (libraryLocalCopies.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { pendingLibraryLocalDeletes = libraryLocalCopies },
                                            enabled = libraryActiveFileIds.isEmpty(),
                                            modifier = Modifier.testTag("delete-library-local-copies")
                                        ) { Text(stringResource(R.string.browse_delete_local_books)) }
                                    }
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
        if (isRefreshing || pullState.isRefreshing) {
            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("pull_to_refresh_indicator")
            )
        }
    }
}

@Composable
private fun HomeSeriesSectionScreen(
    section: HomeSection,
    books: List<BookSummary>,
    state: BrowserState,
    serverWide: Boolean,
    recentBooksPageLoader: suspend (String, HomeSection, Int) -> LibraryBooksPage,
    seriesCatalogLoader: suspend (SeriesCatalogFilter, Int) -> SeriesCatalogPage,
    modifier: Modifier,
    imageLoader: suspend (String) -> ByteArray?,
    onSeriesSelected: (SeriesSummary) -> Unit
) {
    var sourceBooks by remember(books, section) { mutableStateOf(books) }
    var authoritativeSeries by remember(books, section, serverWide) {
        mutableStateOf<List<SeriesSummary>?>(null)
    }
    LaunchedEffect(books, section) {
        if (serverWide && section == HomeSection.RECENTLY_ADDED_SERIES) {
            authoritativeSeries = runCatching {
                loadCompleteSeriesCatalog { page ->
                    seriesCatalogLoader(
                        SeriesCatalogFilter(
                            sort = SeriesSortOption.LAST_ADDED,
                            direction = SortDirection.DESCENDING
                        ),
                        page
                    )
                }.items
            }.getOrNull()
            return@LaunchedEffect
        }
        // The Home preview is built from the complete cached catalog. Replacing it
        // with page zero from each library made See all lose series in large libraries.
        if (section == HomeSection.RECENTLY_UPDATED_SERIES) {
            sourceBooks = homeSeriesSeeAllSourceBooks(section, books, emptyList())
            return@LaunchedEffect
        }
        val libraryIds = if (serverWide) {
            state.libraries.map { it.id }
        } else {
            listOfNotNull(state.selectedLibraryId)
        }
        val loadedBooks = libraryIds.flatMap { libraryId ->
            runCatching { recentBooksPageLoader(libraryId, section, 0).items }.getOrDefault(emptyList())
        }.distinctBy { it.id to it.fileId }
        sourceBooks = homeSeriesSeeAllSourceBooks(section, books, loadedBooks)
    }
    val preferBookOrbitImportSequence =
        section == HomeSection.RECENTLY_ADDED_SERIES && !state.homeShelves.isServerProvided
    val fallbackSeries = remember(sourceBooks, section, preferBookOrbitImportSequence) {
        homeSeriesSummaries(
            books = sourceBooks,
            useUpdatedAt = section == HomeSection.RECENTLY_UPDATED_SERIES,
            preferBookOrbitImportSequence = preferBookOrbitImportSequence
        )
    }
    val series = if (
        authoritativeSeries != null &&
        serverWide &&
        section == HomeSection.RECENTLY_ADDED_SERIES
    ) {
        homeSeriesPreview(
            series = authoritativeSeries!!,
            books = books,
            limit = Int.MAX_VALUE,
            preferBookOrbitImportSequence = preferBookOrbitImportSequence
        )
    } else {
        authoritativeSeries ?: fallbackSeries
    }
    val displaySeries = remember(series, books) { enrichSeriesAvailableFormats(series, books) }
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = LocalLibraryCardSize.current.gridMinSize),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CATALOG_GRID_PADDING),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (series.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val sectionTitle = stringResource(section.titleRes)
                Text(
                    stringResource(R.string.browse_no_section_found, sectionTitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        gridItems(displaySeries, key = { "home-series-${section.name}-${it.id}" }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("series_card_${item.id}")
                    .clickable { onSeriesSelected(item) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CatalogImage(
                        url = item.coverUrl,
                        label = stringResource(R.string.browse_cover_for, item.name),
                        loader = imageLoader,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                    )
                    AvailableFormatsLabel(
                        item.availableFormats,
                        "series-format-label-${item.id}"
                    )
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.authors.isNotEmpty()) {
                        Text(item.authors.joinToString(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        stringResource(
                            R.string.browse_series_books_read,
                            item.bookCount,
                            item.readCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun homeSectionLibraryIds(
    serverWide: Boolean,
    libraries: List<LibrarySummary>,
    selectedLibraryId: String?
): List<String> = if (serverWide) {
    libraries.map { it.id }.ifEmpty { listOfNotNull(selectedLibraryId) }
} else {
    listOfNotNull(selectedLibraryId)
}

@Composable
internal fun HomeSectionScreen(
    section: HomeSection,
    books: List<BookSummary>,
    state: BrowserState,
    localOnDeckEnabled: Boolean = true,
    serverWide: Boolean,
    recentBooksPageLoader: suspend (String, HomeSection, Int) -> LibraryBooksPage,
    modifier: Modifier,
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?
) {
    val scopedServerShelves = homeFeedShelves(
        state.homeShelves,
        state.selectedLibraryId.takeUnless { serverWide }
    )
    val onDeckEnabled = state.homeShelfPreferences.effectiveOnDeckEnabled(
        localFallbackEnabled = localOnDeckEnabled,
        hasProviderOwnedShelves = scopedServerShelves.isServerProvided
    )
    val localSectionBooks = remember(
        books,
        section,
        onDeckEnabled,
        scopedServerShelves
    ) {
        when (section) {
            HomeSection.CURRENTLY_READING -> currentlyReadingBooks(books, limit = null)
            HomeSection.ON_DECK -> when {
                !onDeckEnabled -> emptyList()
                scopedServerShelves.providesBooks(HomeSection.ON_DECK) ->
                    scopedServerShelves.booksBySection[HomeSection.ON_DECK].orEmpty()
                else -> generatedOnDeckBooks(books = books, enabled = true, limit = null)
            }
            HomeSection.WANT_TO_READ -> wantToReadBooks(books, limit = null)
            HomeSection.RECENTLY_ADDED_BOOKS -> books.sortedWith(
                compareByDescending<BookSummary> { it.addedAtMillis != null }
                    .thenByDescending { it.addedAtMillis ?: 0L }
            )
            HomeSection.RECENTLY_RELEASED_BOOKS -> emptyList()
            HomeSection.RECENTLY_READ -> recentlyReadBooks(books, limit = null)
            HomeSection.RECENTLY_ADDED_SERIES,
            HomeSection.RECENTLY_UPDATED_SERIES -> emptyList()
        }
    }
    var sectionBooks by remember(localSectionBooks, section) { mutableStateOf(localSectionBooks) }
    var nextPage by remember(books, section) { mutableStateOf(1) }
    var canLoadMore by remember(books, section) { mutableStateOf(false) }
    val loadMoreScope = rememberCoroutineScope()
    LaunchedEffect(books, section) {
        if (section in setOf(
                HomeSection.RECENTLY_ADDED_BOOKS,
                HomeSection.RECENTLY_UPDATED_SERIES,
                HomeSection.RECENTLY_READ
            )
        ) {
            val libraryIds = homeSectionLibraryIds(
                serverWide = serverWide,
                libraries = state.libraries,
                selectedLibraryId = state.selectedLibraryId
            )
            val loaded = loadCompleteRecentBooks(
                libraryIds = libraryIds,
                section = section,
                pageLoader = recentBooksPageLoader
            )
            canLoadMore = false
            nextPage = 1
            sectionBooks = when (section) {
                HomeSection.RECENTLY_ADDED_BOOKS -> loaded.sortedByDescending { it.addedAtMillis ?: 0L }
                HomeSection.RECENTLY_UPDATED_SERIES -> loaded.sortedByDescending { it.updatedAtMillis ?: 0L }
                else -> recentlyReadBooks(loaded, limit = null)
            }
        }
    }
    val sectionTitle = stringResource(section.titleRes)
    LibraryBooks(
        state = state.copy(
            books = sectionBooks,
            booksTotal = sectionBooks.size,
            booksSeriesTotal = null,
            isLoadingBooks = false
        ),
        modifier = modifier,
        coverLoader = coverLoader,
        onBookSelected = onBookSelected,
        titleOverride = sectionTitle,
        emptyMessage = stringResource(R.string.browse_no_section_books_found, sectionTitle),
        allowSeriesCollapse = false,
        totalBooks = sectionBooks.size,
        jumpRailEnabled = false,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread,
        onMarkAsStatus = onMarkAsStatus,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onClearFailedDownload = onClearFailedDownload,
        onDeleteLocalCopy = onDeleteLocalCopy,
        onLoadMore = if (canLoadMore) {
            {
                loadMoreScope.launch {
                    val libraryIds = if (serverWide) {
            state.libraries.map { it.id }
        } else {
            listOfNotNull(state.selectedLibraryId)
        }
                    val pages = libraryIds.mapNotNull { libraryId ->
                        runCatching { recentBooksPageLoader(libraryId, section, nextPage) }.getOrNull()
                    }
                    val incoming = pages.flatMap { it.items }
                    sectionBooks = (sectionBooks + incoming).distinctBy { it.id to it.fileId }.let { loaded ->
                        when (section) {
                            HomeSection.RECENTLY_ADDED_BOOKS -> loaded.sortedByDescending { it.addedAtMillis ?: 0L }
                            HomeSection.RECENTLY_UPDATED_SERIES -> loaded.sortedByDescending { it.updatedAtMillis ?: 0L }
                            else -> recentlyReadBooks(loaded, limit = null)
                        }
                    }
                    canLoadMore = pages.any { page -> page.total == null || (page.page ?: nextPage) * (page.size ?: page.items.size) + page.items.size < page.total }
                    nextPage += 1
                }
            }
        } else null
    )
}

internal suspend fun loadCompleteRecentBooks(
    libraryIds: List<String>,
    section: HomeSection,
    pageLoader: suspend (String, HomeSection, Int) -> LibraryBooksPage
): List<BookSummary> {
    val loaded = buildList {
        libraryIds.forEach { libraryId ->
            var pageNumber = 0
            var pagesLoaded = 0
            while (pagesLoaded++ < 1_000) {
                val page = runCatching { pageLoader(libraryId, section, pageNumber) }.getOrNull() ?: break
                addAll(page.items)
                val currentPage = page.page ?: pageNumber
                val pageSize = page.size ?: page.items.size
                val hasMore = when {
                    page.items.isEmpty() -> false
                    page.total != null && pageSize > 0 ->
                        (currentPage * pageSize) + page.items.size < page.total
                    else -> pageSize > 0 && page.items.size >= pageSize
                }
                if (!hasMore) break
                pageNumber = currentPage + 1
            }
        }
    }
    return loaded.distinctBy { it.id to it.fileId }
}

@Composable
private fun LibraryBrowseScreen(
    state: BrowserState,
    modifier: Modifier,
    onLoadMoreLibraryBooks: () -> Unit = {},
    coverLoader: suspend (BookSummary) -> ByteArray?,
    onBookSelected: (BookSummary) -> Unit,
    onSeriesSelected: (String) -> Unit,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onMarkAsRead: (BookSummary) -> Unit,
    onMarkAsUnread: (BookSummary) -> Unit,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit,
    browseOptionsExpanded: Boolean,
    onBrowseOptionsExpandedChange: (Boolean) -> Unit,
    headerContent: (@Composable () -> Unit)? = null
) {
    val libraryId = state.selectedLibraryId
    val persistenceKey = "library:${libraryId ?: "all"}"
    val lockedFilter = appPreferences.lockedBookFilters[persistenceKey]?.let(::bookBrowseFilterFromStorage)
    var filter by remember(libraryId, lockedFilter) { mutableStateOf(lockedFilter ?: BookBrowseFilter()) }
    var showFilter by rememberSaveable(libraryId) { mutableStateOf(false) }
    val books = remember(state.books, filter) { filterAndSortLocalBooks(state.books, filter) }
    val total = if (filter.isActive) books.size else state.booksTotal ?: books.size
    val seriesTotal = state.booksSeriesTotal.takeUnless { filter.isActive }

    if (!state.isCatalogComplete && !state.isCatalogSyncing) {
        Text(
            text = stringResource(R.string.browse_incremental_catalog_available),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }

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
        browseOptionsExpanded = browseOptionsExpanded,
        onBrowseOptionsExpandedChange = onBrowseOptionsExpandedChange,
        jumpRailEnabled = state.isCatalogComplete,
        onLoadMore = onLoadMoreLibraryBooks.takeIf { !state.isCatalogComplete },
        serverJumpBuckets = state.libraryJumpBuckets.takeIf { filter == BookBrowseFilter() }.orEmpty(),
        onFilterClick = { showFilter = true },
        headerContent = headerContent
    )
    if (showFilter) {
        BookFilterSheet(
            initial = filter,
            initialLocked = appPreferences.lockedBookFilters.containsKey(persistenceKey),
            onDismiss = { showFilter = false },
            onApply = { nextFilter, locked ->
                val nextFilters = appPreferences.lockedBookFilters.toMutableMap().apply {
                    if (locked) put(persistenceKey, nextFilter.toStorageValue()) else remove(persistenceKey)
                }
                onAppPreferencesChange(appPreferences.copy(lockedBookFilters = nextFilters))
                filter = nextFilter
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
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?
) {
    val context = LocalContext.current
    val libraryId = state.selectedLibraryId
    var loadError by remember(libraryId, genre) { mutableStateOf<String?>(null) }
    val books by produceState<List<BookSummary>?>(initialValue = null, libraryId, genre) {
        loadError = null
        if (libraryId == null) {
            value = emptyList()
            loadError = context.getString(R.string.browse_select_library_for_genre)
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
            loadError = error.message
                ?: context.getString(R.string.browse_genre_load_failed)
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
        emptyMessage = stringResource(R.string.browse_genre_no_books, genre),
        allowSeriesCollapse = false,
        totalBooks = filteredBooks.size,
        filter = BookBrowseFilter(genre = genre),
        jumpRailEnabled = false,
        onMarkAsRead = onMarkAsRead,
        onMarkAsUnread = onMarkAsUnread,
        onMarkAsStatus = onMarkAsStatus
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
    emptyMessage: String? = null,
    allowSeriesCollapse: Boolean = true,
    totalBooks: Int? = null,
    totalSeries: Int? = null,
    filter: BookBrowseFilter? = null,
    browseOptionsExpanded: Boolean = false,
    onBrowseOptionsExpandedChange: ((Boolean) -> Unit)? = null,
    jumpRailEnabled: Boolean = true,
    serverJumpBuckets: List<LibraryJumpBucket> = emptyList(),
    onFilterClick: (() -> Unit)? = null,
    onMarkAsRead: ((BookSummary) -> Unit)? = null,
    onMarkAsUnread: ((BookSummary) -> Unit)? = null,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)? = null,
    onDownload: ((BookSummary) -> Unit)? = null,
    onCancelDownload: ((BookSummary) -> Unit)? = null,
    onClearFailedDownload: ((BookSummary) -> Unit)? = null,
    onDeleteLocalCopy: ((BookSummary) -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    confirmDeleteLocalCopy: Boolean = true,
    onDeleteLocalCopies: ((List<BookSummary>) -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    val title = titleOverride
        ?: state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.name
        ?: stringResource(R.string.browse_library)
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.browse_no_books)
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
            browseOptionsExpanded = browseOptionsExpanded,
            onBrowseOptionsExpandedChange = onBrowseOptionsExpandedChange,
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
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingFeedRow(stringResource(R.string.browse_loading_books)) }
        }
        if (state.isCatalogSyncing) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingFeedRow(
                    if (state.isCatalogComplete) {
                        stringResource(R.string.browse_updating_cached_catalog)
                    } else {
                        stringResource(R.string.browse_loading_books)
                    }
                )
            }
        }
        if (!state.isLoadingBooks && state.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(resolvedEmptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        gridItems(displayedBooks, key = { (book, seriesKey) -> localBookItemKey(book, seriesKey) }) { (book, seriesKey) ->
            val unavailableOffline = state.isOfflineSnapshot && !book.isDownloaded
            BookPosterCard(
                book = book,
                coverLoader = coverLoader,
                enabled = !unavailableOffline,
                displayTitle = if (seriesKey != null) {
                    book.seriesName ?: stringResource(R.string.browse_series)
                } else {
                    book.title
                },
                supportingText = seriesKey?.let { key ->
                    stringResource(seriesBookCountLabelRes(seriesBookCounts[key] ?: 1))
                },
                onMarkAsRead = if (seriesKey == null && !state.isOfflineSnapshot) {
                    onMarkAsRead?.let { mark -> { mark(book) } }
                } else null,
                onMarkAsUnread = if (seriesKey == null && !state.isOfflineSnapshot) {
                    onMarkAsUnread?.let { mark -> { mark(book) } }
                } else null,
                onMarkAsStatus = if (seriesKey == null && !state.isOfflineSnapshot) {
                    onMarkAsStatus?.let { mark -> { statusBook, status -> mark(statusBook, status) } }
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
    onLoadMore?.let { loadMore ->
        OutlinedButton(
            onClick = loadMore,
            modifier = Modifier.fillMaxWidth().padding(horizontal = CATALOG_GRID_PADDING)
        ) { Text(stringResource(R.string.browse_load_more)) }
    }
    pendingLocalDeletes?.let { books ->
        AlertDialog(
            onDismissRequest = { pendingLocalDeletes = null },
            title = {
                Text(
                    if (books.size == 1) {
                        stringResource(R.string.browse_delete_local_copy_title)
                    } else {
                        stringResource(R.string.browse_delete_local_copies_title, books.size)
                    }
                )
            },
            text = {
                Text(
                    if (books.size == 1) {
                        stringResource(R.string.browse_delete_file_full_message)
                    } else {
                        stringResource(R.string.browse_delete_files_full_message)
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
                ) { Text(stringResource(R.string.browse_delete_local)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalDeletes = null }) { Text(stringResource(R.string.common_cancel)) }
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
    browseOptionsExpanded: Boolean,
    onBrowseOptionsExpandedChange: ((Boolean) -> Unit)?,
    onToggleSeriesCollapse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_books_toolbar")
            .padding(
                start = CATALOG_GRID_PADDING,
                top = CATALOG_GRID_PADDING,
                end = CATALOG_GRID_PADDING
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedBooks.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.browse_selected_count, selectedBooks.size),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            selectedBooks.forEach { onMarkAsRead?.invoke(it) }
                            onClearSelection()
                        }
                    ) { Text(stringResource(R.string.browse_mark_read)) }
                    TextButton(
                        onClick = {
                            selectedBooks.forEach { onMarkAsUnread?.invoke(it) }
                            onClearSelection()
                        }
                    ) { Text(stringResource(R.string.browse_mark_unread)) }
                    if (onDeleteLocalCopies != null) {
                        TextButton(onClick = { onDeleteLocalCopies(selectedBooks) }) {
                            Text(stringResource(R.string.browse_delete_local))
                        }
                    }
                    TextButton(onClick = onClearSelection) { Text(stringResource(R.string.browse_clear_selection)) }
                }
            }
        } else if (onBrowseOptionsExpandedChange == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibraryBooksCount(
                    bookCount = bookCount,
                    seriesCount = seriesCount,
                    modifier = Modifier.weight(1f)
                )
                LibraryBooksFilterAndCollapse(
                    filterActive = filterActive,
                    onFilterClick = onFilterClick,
                    showSeriesCollapse = showSeriesCollapse,
                    seriesCollapsed = seriesCollapsed,
                    onToggleSeriesCollapse = onToggleSeriesCollapse
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryBooksCount(
                    bookCount = bookCount,
                    seriesCount = seriesCount,
                    modifier = Modifier.weight(1f)
                )
                val browseOptionsContentDescription = stringResource(
                    if (browseOptionsExpanded) {
                        R.string.browse_collapse_browse_options
                    } else {
                        R.string.browse_expand_browse_options
                    }
                )
                val browseOptionsStateDescription = stringResource(
                    if (browseOptionsExpanded) {
                        R.string.browse_expanded
                    } else {
                        R.string.browse_collapsed
                    }
                )
                Row(
                    modifier = Modifier
                        .clickable { onBrowseOptionsExpandedChange(!browseOptionsExpanded) }
                        .testTag("library_browse_options_toggle")
                        .semantics {
                            contentDescription = browseOptionsContentDescription
                            stateDescription = browseOptionsStateDescription
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(stringResource(R.string.browse_browse_options), style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (browseOptionsExpanded) 180f else 0f)
                    )
                }
            }
            if (browseOptionsExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LibraryBooksFilterAndCollapse(
                        filterActive = filterActive,
                        onFilterClick = onFilterClick,
                        showSeriesCollapse = showSeriesCollapse,
                        seriesCollapsed = seriesCollapsed,
                        onToggleSeriesCollapse = onToggleSeriesCollapse
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryBooksCount(
    bookCount: Int,
    seriesCount: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            when {
                seriesCount != null && seriesCount > 0 -> stringResource(
                    R.string.browse_books_and_series_count,
                    bookCount,
                    seriesCount
                )
                bookCount == 1 -> stringResource(R.string.browse_book_count_one, bookCount)
                else -> stringResource(R.string.browse_books_count, bookCount)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryBooksFilterAndCollapse(
    filterActive: Boolean,
    onFilterClick: (() -> Unit)?,
    showSeriesCollapse: Boolean,
    seriesCollapsed: Boolean,
    onToggleSeriesCollapse: () -> Unit
) {
    if (onFilterClick != null) {
        OutlinedButton(onClick = onFilterClick) {
            Text(
                if (filterActive) {
                    stringResource(R.string.browse_filter_active)
                } else {
                    stringResource(R.string.browse_filter)
                }
            )
        }
    }
    if (showSeriesCollapse) {
        val seriesCollapseLabel = stringResource(
            if (seriesCollapsed) {
                R.string.browse_expand_series
            } else {
                R.string.browse_collapse_series
            }
        )
        TextButton(
            onClick = onToggleSeriesCollapse,
            modifier = Modifier.semantics {
                contentDescription = seriesCollapseLabel
            }
        ) {
            Text(seriesCollapseLabel)
        }
    }
}

@Composable
private fun LibraryJumpRail(
    targets: List<Pair<Char, Int>>,
    direction: SortDirection,
    modifier: Modifier,
    enabledLabels: Set<Char>? = null,
    onJump: (Int) -> Unit,
    onUnavailableJump: ((Char) -> Unit)? = null
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
            val canLoad = index == null && enabledLabels?.contains(label) == true && onUnavailableJump != null
            val jumpDescription = stringResource(R.string.browse_jump_to, label.toString())
            val unavailableDescription = stringResource(
                R.string.browse_jump_unavailable,
                label.toString()
            )
            Box(
                modifier = Modifier.size(20.dp).then(
                    if (index != null) {
                        Modifier
                            .clickable { onJump(index) }
                            .semantics { contentDescription = jumpDescription }
                    } else if (canLoad) {
                        Modifier
                            .clickable { onUnavailableJump?.invoke(label) }
                            .semantics { contentDescription = jumpDescription }
                    } else {
                        Modifier.semantics {
                            contentDescription = unavailableDescription
                            disabled()
                        }
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index != null || canLoad) {
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
    val progress: Float?,
    val isQueued: Boolean = false,
    val filename: String? = null
)

internal fun downloadTransferItemKey(row: DownloadTransferRow): String =
    "download-transfer-${row.fileId}"

internal fun downloadTransferRows(state: BrowserState): List<DownloadTransferRow> {
    val booksByFileId = (state.books + state.homeBooks)
        .filter { it.fileId != null }
        .associateBy { it.fileId!! }
    val fileIds = (
        state.downloadingFileIds +
            state.queuedDownloadFileIds +
            state.failedDownloadFileIds +
            state.downloadMetadataByFileId.keys +
            state.downloadBooksByFileId.keys
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
            filename = metadata?.filename ?: book.filename,
            isActive = fileId in state.downloadingFileIds,
            isQueued = fileId in state.queuedDownloadFileIds,
            isFailed = fileId in state.failedDownloadFileIds,
            progress = state.downloadProgressByFileId[fileId]
        )
    }
}

internal fun activeDownloadCount(rows: List<DownloadTransferRow>): Int = rows.count { it.isActive }

internal fun downloadProgressLabel(isQueued: Boolean, progress: Float?): String =
    if (isQueued) {
        progress?.let { "Waiting · ${(it * 100).toInt()}%" } ?: "Waiting…"
    } else {
        progress?.let { "Downloading · ${(it * 100).toInt()}%" } ?: "Downloading…"
    }

internal fun localBookDownloadFilename(book: BookSummary, state: BrowserState): String? {
    val candidateFileIds = state.downloadingFileIds.asSequence().sorted().filter { fileId ->
        fileId == book.fileId ||
            state.downloadBooksByFileId[fileId]?.id == book.id ||
            state.downloadMetadataByFileId[fileId]?.bookId == book.id
    }
    return candidateFileIds.mapNotNull { fileId ->
        state.downloadMetadataByFileId[fileId]?.filename
            ?: state.downloadBooksByFileId[fileId]?.filename
            ?: book.filename.takeIf { fileId == book.fileId }
    }.firstOrNull { it.isNotBlank() }
}

internal fun localBookHasActiveDownload(book: BookSummary, state: BrowserState): Boolean =
    state.downloadingFileIds.any { fileId ->
        fileId == book.fileId ||
            state.downloadBooksByFileId[fileId]?.id == book.id ||
            state.downloadMetadataByFileId[fileId]?.bookId == book.id
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadTransfersSection(
    rows: List<DownloadTransferRow>,
    onDownload: (BookSummary) -> Unit,
    onCancelDownload: (BookSummary) -> Unit,
    onClearFailedDownload: (BookSummary) -> Unit,
    onClearAllFailedDownloads: () -> Unit
) {
    val failedRows = rows.filter { it.isFailed && !it.isActive }
    val activeCount = activeDownloadCount(rows)
    val waitingCount = rows.count { it.isQueued }
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("local-downloads-section")
    ) {
        val sectionMaxHeight = maxHeight
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.browse_downloads), style = MaterialTheme.typography.titleLarge)
                    if (activeCount > 0 || waitingCount > 0) {
                        val transferCountLabel = listOfNotNull(
                            if (activeCount > 0) {
                                stringResource(R.string.browse_downloading_count, activeCount)
                            } else {
                                null
                            },
                            if (waitingCount > 0) {
                                stringResource(R.string.browse_waiting_count, waitingCount)
                            } else {
                                null
                            }
                        ).joinToString(" · ")
                        Text(
                            text = transferCountLabel,
                            modifier = Modifier.testTag("local-downloads-active-count"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (failedRows.isNotEmpty()) {
                        TextButton(onClick = onClearAllFailedDownloads) { Text(stringResource(R.string.browse_clear_all)) }
                    }
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.testTag("local-downloads-toggle")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) {
                                stringResource(R.string.browse_collapse_downloads)
                            } else {
                                stringResource(R.string.browse_expand_downloads)
                            }
                        )
                    }
                }
            }
            if (isExpanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sectionMaxHeight / 2),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = rows.filter { it.isActive || it.isQueued } + failedRows,
                        key = ::downloadTransferItemKey
                    ) { row ->
                        DownloadTransferRowItem(
                            row = row,
                            onAction = {
                                if (row.isActive || row.isQueued) onCancelDownload(row.book) else onDownload(row.book)
                            },
                            actionLabel = stringResource(
                                if (row.isActive || row.isQueued) {
                                    R.string.common_cancel
                                } else {
                                    R.string.common_retry
                                }
                            ),
                            onClear = if (row.isActive || row.isQueued) null else ({ onClearFailedDownload(row.book) })
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@StringRes
internal fun downloadProgressLabelRes(isQueued: Boolean, hasProgress: Boolean): Int = when {
    isQueued && hasProgress -> R.string.browse_waiting_progress
    isQueued -> R.string.browse_waiting
    hasProgress -> R.string.browse_downloading_progress
    else -> R.string.browse_downloading_ellipsis
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadTransferRowItem(
    row: DownloadTransferRow,
    onAction: () -> Unit,
    actionLabel: String,
    onClear: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("download-row-${row.fileId}")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                row.book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("download-row-${row.fileId}-title")
                    .basicMarquee(iterations = Int.MAX_VALUE),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            row.filename?.takeIf { it.isNotBlank() }?.let { filename ->
                Text(
                    text = filename,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download-row-${row.fileId}-filename"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (row.isActive || row.isQueued) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        row.progress?.let { progress ->
                            Text(
                                stringResource(
                                    downloadProgressLabelRes(row.isQueued, true),
                                    (progress * 100).toInt()
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().testTag("download-row-${row.fileId}-progress")
                            )
                        } ?: run {
                            Text(
                                stringResource(downloadProgressLabelRes(row.isQueued, false)),
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().testTag("download-row-${row.fileId}-progress")
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.testTag("download-row-${row.fileId}-action")
                    ) { Text(actionLabel) }
                }
            } else {
                Text(stringResource(R.string.browse_download_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.testTag("download-row-${row.fileId}-action")
                    ) { Text(actionLabel) }
                    onClear?.let { clear ->
                        TextButton(onClick = clear) { Text(stringResource(R.string.browse_clear)) }
                    }
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
    onMarkAsUnread: (BookSummary) -> Unit,
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
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
    val persistenceKey = "local:${libraryId ?: "all"}"
    val lockedFilter = appPreferences.lockedBookFilters[persistenceKey]?.let(::bookBrowseFilterFromStorage)
    var filter by remember(libraryId, lockedFilter) { mutableStateOf(lockedFilter ?: BookBrowseFilter()) }
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
            val localLibraryName = state.libraries.firstOrNull { it.id == libraryId }?.name
            val localBooksTitle = if (localLibraryName != null) {
                stringResource(R.string.browse_local_books_title, localLibraryName)
            } else {
                stringResource(R.string.browse_local_books)
            }
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
                titleOverride = localBooksTitle,
                emptyMessage = stringResource(R.string.browse_no_local_books),
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
            initialLocked = appPreferences.lockedBookFilters.containsKey(persistenceKey),
            onDismiss = { showFilter = false },
            onApply = { nextFilter, locked ->
                val nextFilters = appPreferences.lockedBookFilters.toMutableMap().apply {
                    if (locked) put(persistenceKey, nextFilter.toStorageValue()) else remove(persistenceKey)
                }
                onAppPreferencesChange(appPreferences.copy(lockedBookFilters = nextFilters))
                filter = nextFilter
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
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    onClearFailedDownload: (BookSummary) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall) }
        if (isLoading) item { LoadingFeedRow(stringResource(R.string.browse_loading_books)) }
        if (!isLoading && books.isEmpty()) {
            item { Text(stringResource(R.string.browse_no_books), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(books, key = { localBookItemKey(it, null) }) { book ->
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
                onMarkAsUnread = onMarkAsUnread,
                onMarkAsStatus = onMarkAsStatus
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
    onMarkAsUnread: (BookSummary) -> Unit,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)? = null
) {
    val fileId = book.fileId
    val isDownloading = localBookHasActiveDownload(book, state)
    val downloadFilename = localBookDownloadFilename(book, state)
    val failed = fileId != null && fileId in state.failedDownloadFileIds
    val unavailableOffline = state.isOfflineSnapshot && !book.isDownloaded
    val hasActions = !state.isOfflineSnapshot
    val canOpenDetails = !isDownloading && !unavailableOffline
    var showActions by remember(book.id) { mutableStateOf(false) }
    var showStatusMenu by remember(book.id) { mutableStateOf(false) }
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
                downloadFilename?.takeIf { it.isNotBlank() }?.let { filename ->
                    Text(
                        text = filename,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local-book-${fileId}-downloading-filename"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                BookAvailableFormatsLabel(book)
                Text(nativeBookStatus(book, state.isOfflineSnapshot), style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onBookSelected(book) },
                        enabled = !isDownloading && !unavailableOffline
                    ) {
                        Text(
                            if (unavailableOffline) {
                                stringResource(R.string.browse_unavailable_offline)
                            } else {
                                stringResource(R.string.browse_details)
                            }
                        )
                    }
                    when {
                        book.isDownloaded -> OutlinedButton(
                            onClick = { onDeleteLocalCopy(book) },
                            enabled = !isDownloading
                        ) { Text(stringResource(R.string.browse_delete_local)) }
                        isDownloading -> OutlinedButton(onClick = { onCancelDownload(book) }) { Text(stringResource(R.string.common_cancel)) }
                        fileId != null && !book.isServerMissing && !state.isOfflineSnapshot -> OutlinedButton(onClick = { onDownload(book) }) {
                            Text(
                                if (failed) {
                                    stringResource(R.string.common_retry)
                                } else {
                                    stringResource(R.string.browse_download)
                                }
                            )
                        }
                    }
                }
            }
            if (hasActions) {
                Box {
                    IconButton(onClick = { showActions = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.browse_more_options_for, book.title)
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false }
                    ) {
                        onMarkAsStatus?.let { markAsStatus ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browse_mark_as)) },
                                onClick = {
                                    showActions = false
                                    showStatusMenu = true
                                }
                            )
                        }
                        if (onMarkAsStatus == null) DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_mark_as_read)) },
                            onClick = {
                                showActions = false
                                onMarkAsRead(book)
                            }
                        )
                        if (onMarkAsStatus == null) DropdownMenuItem(
                            text = { Text(stringResource(R.string.browse_mark_as_unread)) },
                            onClick = {
                                showActions = false
                                onMarkAsUnread(book)
                            }
                        )
                        when {
                            isDownloading -> DropdownMenuItem(text = { Text(stringResource(R.string.common_cancel)) }, onClick = { showActions = false; onCancelDownload(book) })
                            failed -> {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (book.isDownloaded) {
                                                stringResource(R.string.browse_update_local)
                                            } else {
                                                stringResource(R.string.common_retry)
                                            }
                                        )
                                    },
                                    onClick = { showActions = false; onDownload(book) }
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.browse_clear)) }, onClick = { showActions = false; onClearFailedDownload(book) })
                            }
                            book.isDownloaded -> DropdownMenuItem(text = { Text(stringResource(R.string.browse_delete_local)) }, onClick = { showActions = false; onDeleteLocalCopy(book) })
                            fileId != null && !book.isServerMissing && !state.isOfflineSnapshot -> DropdownMenuItem(text = { Text(stringResource(R.string.browse_download_local)) }, onClick = { showActions = false; onDownload(book) })
                        }
                    }
                    onMarkAsStatus?.let { markAsStatus ->
                        BookDetailReadingStatusMenu(
                            expanded = showStatusMenu,
                            currentStatus = book.readStatus,
                            onDismissRequest = { showStatusMenu = false },
                            onStatusSelected = { status ->
                                showStatusMenu = false
                                markAsStatus(book, status)
                            }
                        )
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
    epubImageLibrarySourceLoader: suspend (BookSummary, Boolean) -> EpubImageLibrarySourceResult,
    onDownloadForEpubImageLibrary: (BookSummary, (BookSummary) -> Unit) -> Unit,
    appPreferences: AppPreferences,
    sessionHistoryLoader: suspend (BookSummary) -> List<AudiobookSessionEvent>,
    onSessionHistoryEntryClick: (BookSummary, Long) -> Unit,
    onClearSessionHistory: (BookSummary) -> Unit,
    serverReadingSessionsLoader: suspend (String) -> BookReadingSessionsResult,
    serverReadingAttemptsLoader: suspend (String) -> ReadingAttemptsResult,
    onBookUserRatingChange: suspend (BookSummary, Int?) -> BookDetailInfo?,
    userRatingAvailable: Boolean = true,
    seriesDetailLoader: suspend (String) -> SeriesDetailInfo?,
    onRead: (BookSummary) -> Unit,
    onPreview: (BookSummary) -> Unit,
    onDownload: (BookSummary) -> Unit,
    onDownloadSingleFile: (BookSummary) -> Unit = onDownload,
    onCancelDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    onDeleteSingleLocalCopy: (BookSummary) -> Unit = onDeleteLocalCopy,
    onMarkAsStatus: ((BookSummary, BookReadStatus) -> Unit)?,
    readingStatusOptions: List<BookReadStatus> = BOOK_READ_STATUS_OPTIONS,
    onSeriesSelected: (String) -> Unit,
    onAuthorSelected: (String) -> Unit,
    onBookSelected: (BookSummary) -> Unit,
    onGenreSelected: (String) -> Unit
) {
    val context = LocalContext.current
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
    var fileDetailsGroupKey by rememberSaveable(book.id, currentBook.fileId) { mutableStateOf<String?>(null) }
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
    val availableFilesForPresentation = detail.availableFiles.map { option ->
        option.fileId?.takeIf { it in state.localFilePathOverrides }?.let { fileId ->
            option.copy(book = option.book.copy(localPath = state.localFilePathOverrides[fileId]))
        } ?: option
    }
    val selectedFile = availableFilesForPresentation.firstOrNull { it.fileId == selectedFileId }
    val selectedGroup = availableFileGroups(availableFilesForPresentation).firstOrNull { group ->
        selectedFile?.fileId in group.options.map { it.fileId }
    }
    val selectedGroupIsMultipart = selectedGroup?.let(::availableFileGroupIsMultipart) == true
    val selectedGroupProgress = selectedGroup?.let(::availableFileGroupDownloadProgress)
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
    var showEpubImageLibrary by rememberSaveable(displayBook.id, displayBook.fileId) {
        mutableStateOf(false)
    }
    var showEpubRemoteConsent by rememberSaveable(displayBook.id, displayBook.fileId) {
        mutableStateOf(false)
    }
    var epubImageCatalog by remember(displayBook.id, displayBook.fileId) {
        mutableStateOf<EpubImageCatalog?>(null)
    }
    var epubImageLibraryMessage by remember(displayBook.id, displayBook.fileId) {
        mutableStateOf<String?>(null)
    }
    var isLoadingEpubImageLibrary by remember(displayBook.id, displayBook.fileId) {
        mutableStateOf(false)
    }
    val epubImageLibraryScope = rememberCoroutineScope()
    fun openEpubImageLibrary(
        bookToOpen: BookSummary = displayBook,
        allowRemoteCache: Boolean
    ) {
        if (isLoadingEpubImageLibrary) return
        epubImageLibraryScope.launch {
            isLoadingEpubImageLibrary = true
            epubImageLibraryMessage = null
            epubImageCatalog = null
            val source = try {
                epubImageLibrarySourceLoader(bookToOpen, allowRemoteCache)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                EpubImageLibrarySourceResult.Error(
                    context.getString(R.string.browse_epub_prepare_failed)
                )
            }
            when (source) {
                is EpubImageLibrarySourceResult.Ready -> {
                    val catalog = try {
                        withContext(Dispatchers.IO) {
                            EpubImageLibraryScanner.scan(
                                source.file,
                                appPreferences.epubImageMinimumDimensionPx
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        null
                    }
                    if (catalog == null) {
                        epubImageLibraryMessage =
                            context.getString(R.string.browse_epub_inspect_failed)
                    } else if (catalog.entries.isEmpty()) {
                        epubImageLibraryMessage = context.getString(
                            R.string.browse_no_raster_images,
                            appPreferences.epubImageMinimumDimensionPx,
                            appPreferences.epubImageMinimumDimensionPx
                        )
                    } else {
                        epubImageCatalog = catalog
                        showEpubImageLibrary = true
                    }
                }
                EpubImageLibrarySourceResult.RemoteConsentRequired -> {
                    if (state.isOfflineSnapshot) {
                        epubImageLibraryMessage =
                            context.getString(R.string.browse_epub_not_downloaded_offline)
                    } else {
                        showEpubRemoteConsent = true
                    }
                }
                is EpubImageLibrarySourceResult.Unavailable -> {
                    epubImageLibraryMessage = source.message.ifBlank {
                        context.getString(R.string.browse_epub_not_local)
                    }
                }
                is EpubImageLibrarySourceResult.Error -> {
                    epubImageLibraryMessage = source.message
                }
            }
            isLoadingEpubImageLibrary = false
        }
    }
    val selectedGroupFileIds = selectedGroup?.options.orEmpty().mapNotNull { it.fileId }.toSet()
    val isDownloading = selectedGroupFileIds.any {
        it in state.downloadingFileIds || it in state.queuedDownloadFileIds
    }
    val groupDownloadFailed = selectedGroupFileIds.any { it in state.failedDownloadFileIds }
    val groupPermissionDenied = selectedGroupFileIds.any { it in state.permissionDeniedDownloadFileIds }
    fun cancelSelectedGroupDownloads() {
        val fileIdsToCancel = selectedGroup?.let {
            availableFileGroupFileIdsToCancel(
                group = it,
                downloadingFileIds = state.downloadingFileIds,
                queuedFileIds = state.queuedDownloadFileIds
            )
        }.orEmpty()
        if (fileIdsToCancel.isEmpty()) {
            onCancelDownload(displayBook)
        } else {
            fileIdsToCancel.forEach { fileId ->
                selectedGroup?.options?.firstOrNull { it.fileId == fileId }?.book?.let(onCancelDownload)
            }
        }
    }
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
    val publisherLabel = stringResource(R.string.browse_publisher)
    val publishedLabel = stringResource(R.string.browse_published)
    val languageLabel = stringResource(R.string.browse_language)
    val pagesLabel = stringResource(R.string.browse_pages)
    val libraryLabel = stringResource(R.string.browse_library)
    val formatLabel = stringResource(R.string.browse_format)
    val durationLabel = stringResource(R.string.browse_duration)
    val fileSizeLabel = stringResource(R.string.browse_file_size)
    val filesLabel = stringResource(R.string.browse_files)
    val offlineLabel = stringResource(R.string.browse_offline)
    val availableLabel = stringResource(R.string.browse_available)
    val publicationMetadata = buildList {
        detail.publisher?.let { add(publisherLabel to it) }
        detail.publishedDate?.let { add(publishedLabel to it) }
        detail.language?.let { add(languageLabel to it) }
        detail.pageCount?.let { add(pagesLabel to it.toString()) }
    }
    val identifierMetadata = buildList {
        detail.isbn13?.let { add("ISBN-13" to it) }
        detail.isbn10?.let { add("ISBN-10" to it) }
    }
    val fileMetadata = buildList {
        detail.libraryName?.let { add(libraryLabel to it) }
        displayBook.format?.let { add(formatLabel to it.uppercase()) }
        detail.durationSeconds?.takeIf { it > 0 }?.let {
            add(durationLabel to formatDetailDuration(it))
        }
        detail.totalSizeBytes?.takeIf { it > 0 }?.let {
            add(fileSizeLabel to formatFileSize(it))
        }
        if (detail.fileCount > 1) add(filesLabel to detail.fileCount.toString())
        if (displayBook.isDownloaded) add(offlineLabel to availableLabel)
    }
    val readingProgressStatusRes = bookDetailReadingProgressStatusRes(displayBook)
    val readingProgressLabel = if (readingProgressStatusRes != null) {
        val statusLabel = stringResource(readingProgressStatusRes)
        val progressPercent = bookDetailProgressPercentText(displayBook)
        if (progressPercent != null) {
            stringResource(R.string.browse_reading_progress, statusLabel, progressPercent)
        } else {
            statusLabel
        }
    } else {
        null
    }

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
            val openCoverDescription = stringResource(
                R.string.browse_open_fullscreen_cover,
                displayBook.title
            )
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
                                contentDescription = openCoverDescription
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
                    if (userRatingAvailable) BookUserRatingStars(
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
                        val openSeriesDescription = stringResource(
                            R.string.browse_open_series,
                            seriesName
                        )
                        OrbitEyebrow(
                            text = "$seriesName${displayBook.seriesIndex?.let(::formatSeriesIndex)?.let { " #$it" }.orEmpty()}  ›",
                            modifier = if (seriesKey != null) {
                                Modifier
                                    .clickable { onSeriesSelected(seriesKey) }
                                    .semantics {
                                        contentDescription = openSeriesDescription
                                    }
                            } else {
                                Modifier
                            }
                        )
                    }
                    displayBook.seriesIndex?.let { index ->
                        Text(
                            stringResource(R.string.browse_book_number, formatSeriesIndex(index)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExpandableBookTitle(displayBook.title)
                    detail.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    displayBook.author?.let { authorName ->
                        val openAuthorDescription = stringResource(
                            R.string.browse_open_author,
                            authorName
                        )
                        Text(
                            text = stringResource(R.string.browse_by_authors, authorName),
                            modifier = Modifier
                                .clickable { onAuthorSelected(authorName) }
                                .semantics { contentDescription = openAuthorDescription },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    detail.narrators.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            stringResource(R.string.browse_narrated_by, it.joinToString()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        bookDetailIdentityStatus(
                            displayBook,
                            state.isOfflineSnapshot,
                            selectedGroupProgress,
                            isDownloading
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    selectedGroupProgress?.takeIf { selectedGroupIsMultipart }?.let { progress ->
                        Text(
                            bookDetailGroupProgressLabel(progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("book-detail-group-progress")
                        )
                    }
                }
            }
        }
        if (seriesNeighbors.total > 1) {
            item {
                SeriesNeighborNavigation(
                    seriesName = displayBook.seriesName
                        ?: stringResource(R.string.browse_series),
                    neighbors = seriesNeighbors,
                    onBookSelected = onBookSelected
                )
            }
        }
        item {
            val actionState = bookDetailActionState(
                isDownloaded = selectedGroupProgress?.state == AvailableFileGroupDownloadState.COMPLETE ||
                    (selectedGroupProgress == null && displayBook.isDownloaded),
                isDownloading = isDownloading,
                downloadFailed = groupDownloadFailed || downloadFailed,
                isPartial = selectedGroupProgress?.state == AvailableFileGroupDownloadState.PARTIAL,
                permissionDenied = groupPermissionDenied || permissionDenied,
                hasDownloadUpdate = displayBook.hasDownloadUpdate,
                isOfflineSnapshot = state.isOfflineSnapshot,
                isServerMissing = displayBook.isServerMissing
            )
            val statusActionLabel = stringResource(R.string.browse_mark_as)
            var showActionMenu by rememberSaveable(displayBook.id) { mutableStateOf(false) }
            var showStatusMenu by rememberSaveable(displayBook.id) { mutableStateOf(false) }
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelLarge
            fun actionWidth(label: String, minimumDp: Float): Float = with(density) {
                val textWidth = textMeasurer.measure(label, style = labelStyle, maxLines = 1).size.width.toDp()
                maxOf(minimumDp.dp, 8.dp + 24.dp + 2.dp + textWidth).value
            }
            val primaryActionLabel = stringResource(bookDetailPrimaryActionLabelRes(displayBook))
            val readWidth = actionWidth(primaryActionLabel, 72f)
            val previewWidth = actionWidth(stringResource(R.string.browse_preview), 84f)
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
                    val transferOverflowRes = actionState.overflowTransferRes
                        ?: actionState.inlineTransfer?.descriptionRes
                    val transferOverflowLabel = transferOverflowRes
                        ?.takeIf {
                            actionState.overflowTransferRes != null || !layout.showInlineTransferAction
                        }
                        ?.let { stringResource(it) }
                    val transferOverflowCancels = actionState.overflowTransferIsCancel ||
                        (
                            actionState.overflowTransferRes == null &&
                                actionState.inlineTransfer ==
                                BookDetailInlineTransfer.CANCEL_DOWNLOAD
                            )
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
                                    label = stringResource(R.string.browse_cd_audiobook_history),
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
                            label = stringResource(R.string.browse_preview),
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
                                label = stringResource(transfer.descriptionRes),
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
                                        cancelSelectedGroupDownloads()
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
                                    options = readingStatusOptions,
                                    onDismissRequest = { showStatusMenu = false },
                                    onStatusSelected = { status ->
                                        showStatusMenu = false
                                        onMarkAsStatus?.invoke(displayBook, status)
                                    }
                                    )
                                }
                            }
                        }
                        if (layout.showMore) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                DetailActionTile(
                                    label = stringResource(R.string.browse_more_book_actions),
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
                                                    if (transferOverflowCancels) Icons.Default.Close else Icons.Default.Download,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showActionMenu = false
                                                if (transferOverflowCancels) {
                                                    cancelSelectedGroupDownloads()
                                                } else {
                                                    onDownload(displayBook)
                                                }
                                            }
                                        )
                                    }
                                    if (actionState.showDeleteLocal) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.browse_delete_local)) },
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
                                        options = readingStatusOptions,
                                        onDismissRequest = { showStatusMenu = false },
                                        onStatusSelected = { status ->
                                            showStatusMenu = false
                                            onMarkAsStatus?.invoke(displayBook, status)
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
                    val transferVerb = if (displayBook.isDownloaded && !selectedGroupIsMultipart) {
                        stringResource(R.string.browse_updating_local)
                    } else {
                        stringResource(R.string.browse_downloading)
                    }
                    Text(
                        downloadProgress?.let { progressValue ->
                            stringResource(
                                R.string.browse_progress_percent,
                                transferVerb,
                                (progressValue * 100).toInt()
                            )
                        } ?: stringResource(R.string.browse_progress_ellipsis, transferVerb),
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
                        stringResource(R.string.browse_cancel_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (downloadFailed) {
            item {
                Text(
                    if (displayBook.isDownloaded) {
                        stringResource(R.string.browse_update_failed)
                    } else {
                        stringResource(R.string.browse_download_failed_hint)
                    },
                    modifier = Modifier.testTag("book-download-status"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (availableFilesForPresentation.isNotEmpty()) {
            item {
                BookDetailAvailableFileSummary(
                    options = availableFilesForPresentation,
                    selectedFileId = selectedFileId,
                    onOpenSheet = { showAvailableFileSheet = true },
                    onOpenEpubImageLibrary = if (shouldShowEpubImageLibrary(displayBook)) {
                        { openEpubImageLibrary(allowRemoteCache = false) }
                    } else {
                        null
                    }
                )
            }
        }
        detail.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            item {
                ExpandableDescription(
                    stringResource(R.string.browse_synopsis),
                    plainText(synopsis)
                )
            }
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
                    title = stringResource(R.string.browse_genres),
                    labels = detail.genres,
                    onLabelClick = onGenreSelected
                )
            }
        }
        if (detail.tags.isNotEmpty()) {
            item {
                DetailLabelGroup(
                    title = stringResource(R.string.browse_tags),
                    labels = detail.tags
                )
            }
        }
        if (publicationMetadata.isNotEmpty() || identifierMetadata.isNotEmpty() || fileMetadata.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.browse_book_details),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (publicationMetadata.isNotEmpty()) {
                        DetailMetadataGroup(
                            stringResource(R.string.browse_publication),
                            publicationMetadata
                        )
                    }
                    if (identifierMetadata.isNotEmpty()) {
                        DetailMetadataGroup(
                            stringResource(R.string.browse_identifiers),
                            identifierMetadata
                        )
                    }
                    if (fileMetadata.isNotEmpty()) {
                        DetailMetadataGroup(
                            stringResource(R.string.browse_library_and_file),
                            fileMetadata
                        )
                    }
                }
            }
        }
    }
    }
    if (showEpubRemoteConsent) {
        AlertDialog(
            onDismissRequest = { showEpubRemoteConsent = false },
            title = { Text(stringResource(R.string.browse_download_for_image_library_title)) },
            text = {
                Text(stringResource(R.string.browse_image_library_consent_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEpubRemoteConsent = false
                        onDownloadForEpubImageLibrary(displayBook) { downloadedBook ->
                            openEpubImageLibrary(
                                bookToOpen = downloadedBook,
                                allowRemoteCache = false
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.browse_yes_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEpubRemoteConsent = false }) {
                    Text(stringResource(R.string.browse_no))
                }
            }
        )
    }
    epubImageLibraryMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { epubImageLibraryMessage = null },
            title = { Text(stringResource(R.string.browse_image_library_unavailable)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { epubImageLibraryMessage = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
    if (showEpubImageLibrary) {
        epubImageCatalog?.let { catalog ->
            EpubImageLibraryViewer(
                title = displayBook.title,
                catalog = catalog,
                onDismiss = {
                    showEpubImageLibrary = false
                    epubImageCatalog = null
                }
            )
        }
    }
    if (showAvailableFileSheet && availableFilesForPresentation.size > 1) {
        BookDetailAvailableFileSheet(
            options = availableFilesForPresentation,
            selectedFileId = selectedFileId,
            onFileSelected = {
                hasAppliedInitialFileSelection = true
                selectedFileId = it
            },
            onGroupLongPressed = { fileDetailsGroupKey = it.key },
            onDismissRequest = { showAvailableFileSheet = false }
        )
    }
    fileDetailsGroupKey?.let { key ->
        availableFilesForPresentation.let { options ->
            availableFileGroups(options).firstOrNull { it.key == key }?.let { group ->
                BookDetailAvailableFileDetails(
                    group = group,
                    onDownload = onDownloadSingleFile,
                    onDeleteLocalCopy = onDeleteSingleLocalCopy,
                    downloadingFileIds = state.downloadingFileIds,
                    queuedDownloadFileIds = state.queuedDownloadFileIds,
                    isOffline = state.isOfflineSnapshot,
                    onDismissRequest = { fileDetailsGroupKey = null }
                )
            }
        }
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
            title = { Text(stringResource(R.string.browse_clear_session_history_title)) },
            text = { Text(stringResource(R.string.browse_clear_session_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSessionHistory = false
                        sessionHistory = emptyList()
                        onClearSessionHistory(displayBook)
                    }
                ) {
                    Text(stringResource(R.string.browse_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessionHistory = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                Text(stringResource(R.string.browse_session_history), style = MaterialTheme.typography.titleLarge)
                Text(
                    bookTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearClick) {
                Text(stringResource(R.string.browse_clear))
            }
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.testTag("audiobook-session-history-close")
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browse_close_session_history))
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
                    stringResource(R.string.browse_local_listening_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.browse_collapse_local_history)
                    } else {
                        stringResource(R.string.browse_expand_local_history)
                    }
                )
            }
            if (expanded) {
                Text(
                    stringResource(R.string.browse_local_history_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (events.isEmpty()) {
                    Text(stringResource(R.string.browse_no_local_history), style = MaterialTheme.typography.bodyMedium)
                } else {
                    events.forEachIndexed { index, event ->
                        ListItem(
                            modifier = Modifier
                                .clickable { onEventClick(event) }
                                .testTag("audiobook-session-history-event-$index"),
                            headlineContent = {
                                Text(
                                    if (event.type == AudiobookSessionEventType.PLAY) {
                                        stringResource(R.string.browse_played)
                                    } else {
                                        stringResource(R.string.browse_paused)
                                    }
                                )
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
                    stringResource(R.string.browse_server_reading_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.browse_collapse_server_history)
                    } else {
                        stringResource(R.string.browse_expand_server_history)
                    }
                )
            }
            if (expanded) {
                Text(
                    stringResource(R.string.browse_server_history_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    isLoading -> Text(stringResource(R.string.browse_loading_server_history), style = MaterialTheme.typography.bodyMedium)
                    sessions?.status == ServerReadingHistoryStatus.UNSUPPORTED &&
                        attempts?.status == ServerReadingHistoryStatus.UNSUPPORTED ->
                        Text(stringResource(R.string.browse_server_history_unsupported), style = MaterialTheme.typography.bodyMedium)
                    sessions?.status == ServerReadingHistoryStatus.ERROR &&
                        attempts?.status == ServerReadingHistoryStatus.ERROR ->
                        Text(stringResource(R.string.browse_server_history_failed), style = MaterialTheme.typography.bodyMedium)
                    sessions?.items.isNullOrEmpty() && attempts?.items.isNullOrEmpty() ->
                        Text(stringResource(R.string.browse_no_server_history), style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        sessions?.items.orEmpty().forEach { session ->
                            ListItem(
                                headlineContent = {
                                    val sessionDuration =
                                        formatServerReadingDuration(session.durationSeconds)
                                    Text(
                                        stringResource(
                                            R.string.browse_reading_session,
                                            sessionDuration
                                        )
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            formatServerDateRange(session.startedAt, session.endedAt),
                                            session.progressDelta?.let {
                                                stringResource(
                                                    R.string.browse_progress_change,
                                                    formatServerProgress(it)
                                                )
                                            },
                                            session.endProgress?.let {
                                                stringResource(
                                                    R.string.browse_ended_at,
                                                    formatServerProgress(it)
                                                )
                                            },
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
                                    val outcomeLabel =
                                        formatReadingAttemptOutcome(attempt.outcome)
                                    Text(
                                        stringResource(
                                            R.string.browse_reading_attempt,
                                            outcomeLabel
                                        )
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            formatServerDateRange(attempt.startedOn, attempt.endedOn),
                                            attempt.totalSessions?.let {
                                                stringResource(
                                                    R.string.browse_sessions_count,
                                                    it
                                                )
                                            },
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

@Composable
private fun formatServerReadingDuration(seconds: Long?): String =
    seconds?.takeIf { it >= 0 }?.let(::formatDetailDuration)
        ?: stringResource(R.string.browse_duration_unavailable)

private fun formatServerProgress(progress: Double): String =
    "${"%.1f".format(Locale.US, progress.coerceIn(0.0, 100.0))}%"

private fun formatServerDateRange(start: String?, end: String?): String? =
    listOfNotNull(start?.takeIf { it.isNotBlank() }, end?.takeIf { it.isNotBlank() })
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" → ")

@Composable
private fun formatReadingAttemptOutcome(outcome: ReadingAttemptOutcome?): String = when (outcome) {
    null -> stringResource(R.string.browse_in_progress)
    ReadingAttemptOutcome.COMPLETED -> stringResource(R.string.browse_completed)
    ReadingAttemptOutcome.SKIMMED -> stringResource(R.string.browse_skimmed)
    ReadingAttemptOutcome.ABANDONED -> stringResource(R.string.browse_abandoned)
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
            val currentRatingDescription = stringResource(
                R.string.browse_clear_star_rating,
                star
            )
            val setRatingDescription = stringResource(
                R.string.browse_set_star_rating,
                star
            )
            val ratingStateDescription = stringResource(
                if (isCurrentRating) {
                    R.string.browse_selected
                } else {
                    R.string.browse_not_selected
                }
            )
            IconButton(
                onClick = { onRatingSelected(star) },
                enabled = enabled,
                modifier = Modifier
                    .size(23.dp)
                    .testTag("book-detail-user-rating-$star")
                    .semantics {
                        contentDescription = if (isCurrentRating) {
                            currentRatingDescription
                        } else {
                            setRatingDescription
                        }
                        stateDescription = ratingStateDescription
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

internal fun availableFileSummaryName(
    option: BookFileOption?,
    label: AvailableFileLabel?,
    group: AvailableFileGroup?
): String = group?.takeIf { it.options.size > 1 }?.groupName
    ?: availableFilename(option, label)

@Composable
private fun availableFileMetadata(option: BookFileOption?, label: AvailableFileLabel?): String {
    val parts = mutableListOf<String>()
    label?.metadata?.takeIf { it.isNotBlank() }?.let { value ->
        val segments = value.split(" · ")
        parts.add(
            if (segments.size > 1) {
                "${segments.drop(1).joinToString(" · ")} · ${segments.first()}"
            } else {
                value
            }
        )
    }
    option?.localPath?.takeIf(String::isNotBlank)?.let { localPath ->
        parts.add(
            stringResource(
                if (localPath.isContentLocator()) {
                    R.string.browse_downloaded_to_sd_card
                } else {
                    R.string.browse_available_offline
                }
            )
        )
    }
    return parts.joinToString(" · ")
}

@Composable
private fun availableFileLabelsLocalized(
    options: List<BookFileOption>
): Map<String, AvailableFileLabel> {
    val unknownFormat = stringResource(availableFileUnknownFormatRes())
    val unnamedFile = stringResource(availableFileUnnamedRes())
    val sizeUnavailable = stringResource(availableFileSizeUnavailableRes())
    val baseLabels = LinkedHashMap<BookFileOption, AvailableFileLabel>()
    for (option in options) {
        val format = option.format
            ?.substringAfterLast('/')
            ?.uppercase(Locale.US)
            ?: unknownFormat
        val filename = option.filename?.takeIf { it.isNotBlank() } ?: unnamedFile
        val role = stringResource(availableFileRoleLabelRes(option.role))
        val size = option.sizeBytes?.let(::formatByteSize) ?: sizeUnavailable
        baseLabels[option] = AvailableFileLabel(
            title = "$format · $filename",
            metadata = "$role · $size"
        )
    }
    val duplicateKeys = baseLabels.values.groupingBy { it }.eachCount()
    val labels = LinkedHashMap<String, AvailableFileLabel>()
    for (option in options) {
        val fileId = option.fileId ?: continue
        val label = baseLabels.getValue(option)
        val title = if (duplicateKeys[label] == 1) {
            label.title
        } else {
            label.title + stringResource(
                availableFileDuplicateSuffixRes(),
                fileId.takeLast(6)
            )
        }
        labels[fileId] = label.copy(title = title)
    }
    return labels
}

@Composable
private fun availableFileGroupLabelLocalized(group: AvailableFileGroup): AvailableFileLabel {
    val option = group.options.firstOrNull()
    val format = option?.format
        ?.substringAfterLast('/')
        ?.uppercase(Locale.US)
        ?: option?.filename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)
        ?: stringResource(availableFileUnknownFormatRes())
    val count = group.options.size
    val countLabel = if (count == 1) {
        stringResource(availableFileCountOneRes())
    } else {
        stringResource(availableFileCountManyRes(), count)
    }
    val sizeLabel = group.totalSizeBytes?.let(::formatByteSize)
        ?: stringResource(availableFileSizeUnavailableRes())
    return AvailableFileLabel(
        title = "$format · ${group.groupName}",
        metadata = "$countLabel · $sizeLabel"
    )
}

@Composable
private fun availableFilenameLocalized(
    option: BookFileOption?,
    label: AvailableFileLabel?
): String {
    val unknownName = stringResource(availableFileUnknownNameRes())
    val filename = option?.filename?.takeIf { it.isNotBlank() }
    if (filename != null) return filename
    val labelled = label?.title?.substringAfter(" · ", unknownName)
    return labelled ?: unknownName
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookDetailAvailableFileSummary(
    options: List<BookFileOption>,
    selectedFileId: String?,
    onOpenSheet: () -> Unit,
    onOpenEpubImageLibrary: (() -> Unit)? = null
) {
    val labels = availableFileLabelsLocalized(options)
    val groups = availableFileGroups(options)
    val selectedOption = options.firstOrNull { it.fileId == selectedFileId } ?: options.firstOrNull()
    val selectedGroup = groups.firstOrNull { group ->
        selectedOption?.fileId in group.options.map { it.fileId }
    }
    val selectedGroupLabel = selectedGroup
        ?.takeIf { it.options.size > 1 }
        ?.let { group -> availableFileGroupLabelLocalized(group) }
    val selectedLabel = selectedGroupLabel ?: selectedOption?.fileId?.let(labels::get)
    val selectedSummaryName = selectedGroup?.takeIf { it.options.size > 1 }?.groupName
        ?: availableFilenameLocalized(selectedOption, selectedLabel)
    val canChooseFile = options.size > 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
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
                        text = selectedSummaryName,
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
                        contentDescription = stringResource(R.string.browse_cd_choose_file)
                    )
                }
            }
        }
        if (selectedOption?.mediaKind == MediaKind.EPUB) {
            onOpenEpubImageLibrary?.let { onOpen ->
                IconButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("book-detail-image-library")
                ) {
                    Icon(
                        imageVector = Icons.Default.CollectionsBookmark,
                        contentDescription = stringResource(R.string.browse_cd_open_image_library)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BookDetailAvailableFileSheet(
    options: List<BookFileOption>,
    selectedFileId: String?,
    onFileSelected: (String?) -> Unit,
    onGroupLongPressed: (AvailableFileGroup) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val labels = availableFileLabelsLocalized(options)
    val groups = availableFileGroups(options)
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
                stringResource(R.string.browse_available_files),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            groups.forEachIndexed { index, group ->
                val option = group.options.first()
                val selectedOption = group.options.firstOrNull { it.fileId == selectedFileId }
                val selectedFile = selectedOption
                    ?: group.options.firstOrNull { it.role.equals("primary", ignoreCase = true) }
                    ?: option
                val label = selectedFile.fileId?.let(labels::get)
                val groupLabel = availableFileGroupLabelLocalized(group)
                val isSelected = selectedOption != null
                val title = if (group.options.size > 1) {
                    stringResource(
                        R.string.browse_file_group_entry_title,
                        availableFileType(option, option.fileId?.let(labels::get)),
                        group.options.size,
                        group.groupName
                    )
                } else {
                    availableFilenameLocalized(option, option.fileId?.let(labels::get))
                }
                val groupProgressDescription = if (group.options.size > 1) {
                    bookDetailGroupProgressLabel(availableFileGroupDownloadProgress(group))
                } else {
                    null
                }
                val singleFileMetadata = if (group.options.size <= 1) {
                    availableFileMetadata(selectedFile, label)
                } else {
                    ""
                }
                val metadata = buildList {
                    if (group.options.size > 1) {
                        add(
                            listOf(
                                groupLabel.metadata ?: group.groupName,
                                groupProgressDescription
                            ).joinToString(" · ")
                        )
                    } else {
                        group.groupingPath?.let(::add)
                        singleFileMetadata.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.joinToString(" · ")
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onFileSelected(selectedFile.fileId) },
                            onLongClick = { onGroupLongPressed(group) }
                        )
                        .testTag("book-detail-available-file-group-${group.key}"),
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
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (group.options.size > 1) {
                                IconButton(onClick = { onGroupLongPressed(group) }) {
                                    Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.browse_view_files))
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.browse_selected))
                            }
                        }
                    }
                )
                if (index < groups.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun singleFileDownloadBook(option: BookFileOption): BookSummary =
    option.book.copy(filename = option.filename ?: option.book.filename)

internal enum class GroupedFileAction(@StringRes val labelRes: Int) {
    DOWNLOAD(R.string.browse_not_downloaded),
    DOWNLOADING(R.string.browse_downloading),
    DELETE(R.string.browse_downloaded)
}

internal fun groupedFileAction(
    option: BookFileOption,
    downloadingFileIds: Set<String>,
    queuedDownloadFileIds: Set<String>
): GroupedFileAction = when {
    option.fileId in downloadingFileIds || option.fileId in queuedDownloadFileIds ->
        GroupedFileAction.DOWNLOADING
    !option.localPath.isNullOrBlank() -> GroupedFileAction.DELETE
    else -> GroupedFileAction.DOWNLOAD
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookDetailAvailableFileDetails(
    group: AvailableFileGroup,
    onDownload: (BookSummary) -> Unit,
    onDeleteLocalCopy: (BookSummary) -> Unit,
    downloadingFileIds: Set<String>,
    queuedDownloadFileIds: Set<String>,
    isOffline: Boolean,
    onDismissRequest: () -> Unit
) {
    val progress = availableFileGroupDownloadProgress(group)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(
                    R.string.browse_file_group_title,
                    group.groupName,
                    group.options.size
                )
            )
        },
        text = {
            Column {
                Text(bookDetailGroupProgressLabel(progress))
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(group.options, key = { index, option ->
                        option.fileId ?: option.filename?.ifBlank { "file-$index" } ?: "file-$index"
                    }) { _, option ->
                        val fileLabel = option.filename
                            ?: option.fileId
                            ?: stringResource(R.string.browse_unknown_file)
                        val action = groupedFileAction(
                            option = option,
                            downloadingFileIds = downloadingFileIds,
                            queuedDownloadFileIds = queuedDownloadFileIds
                        )
                        ListItem(
                            headlineContent = {
                                Text(
                                    fileLabel,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        stringResource(action.labelRes),
                                        option.sizeBytes?.let { size -> formatFileSize(size) }
                                    ).joinToString(" · ")
                                )
                            },
                            trailingContent = {
                                when (action) {
                                    GroupedFileAction.DOWNLOAD -> OutlinedButton(
                                        onClick = { onDownload(singleFileDownloadBook(option)) },
                                        enabled = !isOffline
                                    ) { Text(stringResource(R.string.browse_download)) }
                                    GroupedFileAction.DOWNLOADING -> Text(
                                        stringResource(R.string.browse_downloading),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    GroupedFileAction.DELETE -> OutlinedButton(
                                        onClick = { onDeleteLocalCopy(singleFileDownloadBook(option)) },
                                        enabled = !isOffline
                                    ) { Text(stringResource(R.string.browse_delete)) }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.common_close)) }
        },
        modifier = Modifier.testTag("book-detail-available-file-details")
    )
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
        Text(stringResource(R.string.browse_other_versions), style = MaterialTheme.typography.titleLarge)
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

private enum class SeriesNeighborDirection(
    @StringRes val labelRes: Int,
    @StringRes val emptyLabelRes: Int
) {
    PREVIOUS(R.string.browse_previous, R.string.browse_start_of_series),
    NEXT(R.string.browse_next, R.string.browse_end_of_series)
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
        OrbitEyebrow(stringResource(R.string.browse_series_navigation))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SeriesNeighborButton(
                direction = SeriesNeighborDirection.PREVIOUS,
                book = neighbors.previous,
                seriesName = seriesName,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                modifier = Modifier.weight(1f).testTag("series-previous-book"),
                onClick = onBookSelected
            )
            SeriesNeighborButton(
                direction = SeriesNeighborDirection.NEXT,
                book = neighbors.next,
                seriesName = seriesName,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.weight(1f).testTag("series-next-book"),
                onClick = onBookSelected
            )
        }
    }
}

@Composable
private fun SeriesNeighborButton(
    direction: SeriesNeighborDirection,
    book: BookSummary?,
    seriesName: String,
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
    val directionLabel = stringResource(direction.labelRes)
    val emptyLabel = stringResource(direction.emptyLabelRes)
    val neighborDescription = if (bookLabel != null) {
        stringResource(
            R.string.browse_neighbor_book_cd,
            directionLabel,
            seriesName,
            bookLabel
        )
    } else {
        stringResource(R.string.browse_neighbor_none_cd, directionLabel, seriesName)
    }
    Card(
        onClick = { book?.let(onClick) },
        enabled = book != null,
        modifier = modifier
            .height(46.dp)
            .semantics {
                contentDescription = neighborDescription
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
            if (direction == SeriesNeighborDirection.PREVIOUS) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (direction == SeriesNeighborDirection.PREVIOUS) {
                    Alignment.Start
                } else {
                    Alignment.End
                }
            ) {
                Text(directionLabel, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = bookLabel ?: emptyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (direction == SeriesNeighborDirection.NEXT) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun BookDetailReadingStatusMenu(
    expanded: Boolean,
    currentStatus: BookReadStatus?,
    options: List<BookReadStatus> = BOOK_READ_STATUS_OPTIONS,
    onDismissRequest: () -> Unit,
    onStatusSelected: (BookReadStatus) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("book-detail-status-menu")
    ) {
        options.forEach { status ->
            DropdownMenuItem(
                text = { Text(stringResource(status.displayLabelRes())) },
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

@StringRes
internal fun bookDetailPrimaryActionLabelRes(book: BookSummary): Int =
    if (book.mediaKind == MediaKind.AUDIO) R.string.browse_play else R.string.browse_read

internal fun bookDetailReadingProgressLabel(book: BookSummary): String? {
    val progress = normalizeStoredProgressPercent(book.progressPercent?.takeIf { it.isFinite() })
    val completed = book.isRead || progress?.let { it >= 99.5f } == true
    val currentlyReading = !completed && book.hasReadingActivity() && book.isStillInProgress()
    if (!completed && !currentlyReading) return null

    val status = if (completed) "Read" else "Reading"
    val percentage = progress?.let(::formatBookDetailProgressPercent) ?: return status
    return "$status \u00B7 $percentage%"
}

@StringRes
internal fun bookDetailReadingProgressStatusRes(book: BookSummary): Int? {
    val progress = normalizeStoredProgressPercent(book.progressPercent?.takeIf { it.isFinite() })
    val completed = book.isRead || progress?.let { it >= 99.5f } == true
    val currentlyReading = !completed && book.hasReadingActivity() && book.isStillInProgress()
    if (!completed && !currentlyReading) return null
    return if (completed) R.string.browse_read else R.string.browse_reading
}

internal fun bookDetailProgressPercentText(book: BookSummary): String? {
    val progress = normalizeStoredProgressPercent(book.progressPercent?.takeIf { it.isFinite() })
    return progress?.let(::formatBookDetailProgressPercent)
}

private fun formatBookDetailProgressPercent(progress: Float): String =
    String.format(java.util.Locale.US, "%.2f", progress.coerceIn(0f, 100f))
        .trimEnd('0')
        .trimEnd('.')

internal enum class BookDetailInlineTransfer(
    val contentDescription: String,
    @StringRes val descriptionRes: Int
) {
    DOWNLOAD("Download", R.string.browse_download),
    DOWNLOAD_REMAINING("Download remaining", R.string.browse_download_remaining),
    RETRY_DOWNLOAD("Retry download", R.string.browse_retry_download),
    CANCEL_DOWNLOAD("Cancel download", R.string.browse_cancel_download)
}

internal data class BookDetailActionState(
    val inlineTransfer: BookDetailInlineTransfer?,
    val overflowTransferLabel: String?,
    val showDeleteLocal: Boolean,
    @StringRes val overflowTransferRes: Int? = null,
    val overflowTransferIsCancel: Boolean = false
) {
    val hasFixedOverflow: Boolean
        get() = overflowTransferLabel != null || showDeleteLocal
}

internal fun bookDetailActionState(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadFailed: Boolean,
    isPartial: Boolean = false,
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
            isPartial -> BookDetailInlineTransfer.DOWNLOAD_REMAINING
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
        showDeleteLocal = true,
        overflowTransferRes = when {
            isDownloading -> R.string.browse_cancel_update
            (hasDownloadUpdate || downloadFailed) && !isOfflineSnapshot ->
                R.string.browse_update_local
            else -> null
        },
        overflowTransferIsCancel = isDownloading
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
        Text(stringResource(R.string.browse_metadata_sources), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            links.forEach { (providerId, url) ->
                val name = providerDisplayName(providerId.provider)
                val providerPageDescription = stringResource(
                    R.string.browse_open_provider_page,
                    name
                )
                Card(
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            runCatching { context.startActivity(intent) }
                        }
                        .semantics { contentDescription = providerPageDescription },
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
                val filterDescription = stringResource(R.string.browse_filter_by, title, label)
                Card(
                    modifier = if (onLabelClick != null) {
                        Modifier
                            .clickable { onLabelClick(label) }
                            .semantics { contentDescription = filterDescription }
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
                Text(
                    if (expanded) {
                        stringResource(R.string.browse_collapse)
                    } else {
                        stringResource(R.string.browse_expand_title)
                    }
                )
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
            showExportResult(
                CoverExportResult(
                    false,
                    context.getString(R.string.browse_cover_export_permission)
                )
            )
        }
    }
    fun requestExport() {
        val bytes = coverBytes
        if (bytes == null) {
            showExportResult(
                CoverExportResult(
                    false,
                    context.getString(R.string.browse_cover_export_unavailable)
                )
            )
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
    val fullscreenCoverDescription = stringResource(
        R.string.browse_fullscreen_cover_description,
        book.title
    )

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
                    contentDescription = fullscreenCoverDescription
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
                        text = { Text(stringResource(R.string.browse_download)) },
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

private const val UNKNOWN_FORMAT_TITLE = "Unknown format"

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
                        ?: UNKNOWN_FORMAT_TITLE
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
        Text(stringResource(R.string.browse_separate_by), style = MaterialTheme.typography.titleMedium)
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
                label = { Text(stringResource(R.string.browse_library)) },
                modifier = Modifier.testTag("series-group-by-library")
            )
            FilterChip(
                selected = groupingMode == SeriesGroupingMode.FORMAT,
                onClick = {
                    onGroupingModeChange(
                        toggledSeriesGroupingMode(groupingMode, SeriesGroupingMode.FORMAT)
                    )
                },
                label = { Text(stringResource(R.string.browse_file_format)) },
                modifier = Modifier.testTag("series-group-by-format")
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SeriesDetails(
    seriesKey: String,
    seriesNameHint: String? = null,
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
    val seriesDownloadScopeLabel = stringResource(R.string.browse_this_series)
    val localBooks = books
        .filter { (it.seriesId ?: it.seriesName) == seriesKey }
        .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
    val seriesDisplayName = localBooks.firstOrNull()?.seriesName
        ?.takeUnless { it == seriesKey }
        ?: seriesNameHint?.takeUnless { it == seriesKey }
        ?: stringResource(R.string.browse_series)
    val localDetail = SeriesDetailInfo(
        id = seriesKey,
        name = seriesDisplayName,
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
    val displayedDetail = detail.copy(
        name = detail.name.takeUnless { it.isBlank() || it == seriesKey || it == "Series" } ?: seriesDisplayName,
        books = detail.books.map { book ->
            if (book.seriesName.isNullOrBlank() || book.seriesName == seriesKey || book.seriesName == "Series") {
                book.copy(seriesName = seriesDisplayName)
            } else {
                book
            }
        }
    )
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            reloadKey += 1
        }
    }
    val bookSections = seriesBookSections(displayedDetail.books, libraries, groupingMode)
    val completion = if (displayedDetail.bookCount > 0) displayedDetail.readCount.toFloat() / displayedDetail.bookCount else 0f
    val seriesDownloadCandidates = seriesSelectableFiles(
        booksWithLocalFilePathOverrides(
            books = displayedDetail.books,
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
            title = { Text(stringResource(R.string.browse_download_series_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.browse_download_files_message,
                        seriesFrozenSelection.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSeriesDownloadConfirm = false
                        onBulkDownload?.invoke(seriesDownloadScopeLabel, seriesFrozenSelection)
                    },
                    modifier = Modifier.testTag("confirm-download-series")
                ) { Text(stringResource(R.string.browse_download_series)) }
            },
            dismissButton = {
                TextButton(onClick = { showSeriesDownloadConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    pendingSeriesLocalDeletes?.let { booksToDelete ->
        AlertDialog(
            onDismissRequest = { pendingSeriesLocalDeletes = null },
            title = {
                Text(
                    if (booksToDelete.size == 1) {
                        stringResource(R.string.browse_delete_local_copy_title)
                    } else {
                        stringResource(
                            R.string.browse_delete_local_copies_title,
                            booksToDelete.size
                        )
                    }
                )
            },
            text = {
                Text(stringResource(R.string.browse_delete_files_full_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSeriesLocalDeletes = null
                        onDeleteLocalCopies(booksToDelete)
                    },
                    modifier = Modifier.testTag("confirm-delete-series-local-copies")
                ) { Text(stringResource(R.string.browse_delete_local)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSeriesLocalDeletes = null }) { Text(stringResource(R.string.common_cancel)) }
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
                OrbitEyebrow(stringResource(R.string.browse_series))
                Text(displayedDetail.name, style = MaterialTheme.typography.headlineSmall)
                displayedDetail.authors.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        stringResource(R.string.browse_by_authors, it.joinToString()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(
                        R.string.browse_series_books_read,
                        displayedDetail.bookCount,
                        displayedDetail.readCount
                    ),
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
                            ) { Text(stringResource(R.string.browse_download_series)) }
                        }
                        if (seriesLocalCopies.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { pendingSeriesLocalDeletes = seriesLocalCopies },
                                enabled = seriesActiveFileIds.isEmpty(),
                                modifier = Modifier.testTag("delete-series-local-copies")
                            ) { Text(stringResource(R.string.browse_delete_local_books)) }
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
                        stringResource(
                            R.string.browse_possible_missing_positions,
                            detail.possibleGaps.joinToString { formatSeriesIndex(it) }
                        ),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        detail.firstBook?.let { first ->
            first.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ExpandableDescription(
                        stringResource(R.string.browse_about_this_series),
                        plainText(synopsis)
                    )
                }
            }
            if (first.genres.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DetailLabelGroup(
                        stringResource(R.string.browse_genres),
                        first.genres,
                        onGenreSelected
                    )
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
                    DetailLabelGroup(stringResource(R.string.browse_tags), first.tags)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.browse_books), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        }
        bookSections.forEach { section ->
            section.title?.let { title ->
                item(
                    key = "series-section-${section.key}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    val sectionLabel = if (title == UNKNOWN_FORMAT_TITLE) {
                        stringResource(R.string.browse_unknown_format)
                    } else {
                        title
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("series-section-${section.key}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sectionLabel, style = MaterialTheme.typography.titleMedium)
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
    val expandedStateDescription = stringResource(
        if (expanded) R.string.browse_expanded else R.string.browse_collapsed
    )
    val descriptionContentDescription = stringResource(
        R.string.browse_description_suffix,
        title
    )
    val toggleContentDescription = stringResource(
        if (expanded) R.string.browse_collapse_title else R.string.browse_expand_title_arg,
        title
    )

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
                                contentDescription = descriptionContentDescription
                                stateDescription = expandedStateDescription
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
                    contentDescription = toggleContentDescription
                }
            ) {
                Text(
                    if (expanded) {
                        stringResource(R.string.browse_collapse)
                    } else {
                        stringResource(R.string.browse_expand)
                    }
                )
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

internal fun onDeckBooks(
    books: List<BookSummary>,
    limit: Int? = HOME_PREVIEW_LIMIT
): List<BookSummary> {
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
        .let { result -> limit?.let(result::take) ?: result }
}

internal fun wantToReadBooks(
    books: List<BookSummary>,
    limit: Int? = HOME_PREVIEW_LIMIT
): List<BookSummary> {
    return books
        .filter { it.readStatus == BookReadStatus.WANT_TO_READ }
        .sortedWith(readingStateShelfComparator())
        .let { result -> limit?.let(result::take) ?: result }
}

internal fun currentlyReadingBooks(
    books: List<BookSummary>,
    limit: Int? = HOME_PREVIEW_LIMIT
): List<BookSummary> {
    return books
        .filter {
            it.readStatus == BookReadStatus.READING ||
                it.readStatus == BookReadStatus.REREADING
        }
        .let(::sortCurrentlyReadingBooks)
        .let { result -> limit?.let(result::take) ?: result }
}

internal fun generatedOnDeckBooks(
    books: List<BookSummary>,
    enabled: Boolean,
    limit: Int? = HOME_PREVIEW_LIMIT
): List<BookSummary> = if (enabled) onDeckBooks(books, limit) else emptyList()

/** Keeps the full Home catalog behind the Recently updated series preview and See all views. */
internal fun homeSeriesSeeAllSourceBooks(
    section: HomeSection,
    completeCatalog: List<BookSummary>,
    fetchedRecentPage: List<BookSummary>
): List<BookSummary> = if (section == HomeSection.RECENTLY_UPDATED_SERIES) {
    completeCatalog
} else {
    fetchedRecentPage
}

internal fun sortCurrentlyReadingBooks(books: List<BookSummary>): List<BookSummary> =
    books.sortedWith(
        compareByDescending<BookSummary> { it.lastReadAtMillis != null }
            .thenByDescending { it.lastReadAtMillis ?: 0L }
    )

internal fun recentlyReadBooks(
    books: List<BookSummary>,
    limit: Int? = HOME_PREVIEW_LIMIT
): List<BookSummary> {
    return books
        .filter {
            it.readStatus == BookReadStatus.READ ||
                it.readStatus == BookReadStatus.SKIMMED
        }
        .sortedWith(readingStateShelfComparator())
        .let { result -> limit?.let(result::take) ?: result }
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

internal fun homeSeriesSummaries(
    books: List<BookSummary>,
    useUpdatedAt: Boolean,
    preferBookOrbitImportSequence: Boolean = false
): List<SeriesSummary> {
    val grouped = books
        .filter { !it.seriesName.isNullOrBlank() }
        .groupBy { it.seriesId ?: it.seriesName.orEmpty() }
    return grouped.mapNotNull { (seriesKey, members) ->
        val introductionOrder = seriesIntroductionOrder(members, preferBookOrbitImportSequence)
        val representative = (
            if (useUpdatedAt) {
                members.maxByOrNull { it.updatedAtMillis ?: 0L }
            } else if (introductionOrder.importSequence != null) {
                members.minByOrNull { it.bookOrbitImportSequence() ?: Long.MAX_VALUE }
            } else {
                members.minByOrNull { it.addedAtMillis ?: Long.MAX_VALUE }
            }
        ) ?: return@mapNotNull null
        val timestamp = if (useUpdatedAt) representative.updatedAtMillis else representative.addedAtMillis
        if (timestamp == null && (useUpdatedAt || introductionOrder.importSequence == null)) return@mapNotNull null
        val name = representative.seriesName.orEmpty()
        val summary = SeriesSummary(
            id = seriesKey,
            name = name,
            authors = members.mapNotNull { it.author?.takeIf(String::isNotBlank) }.distinct(),
            bookCount = members.size,
            readCount = members.count { it.isRead || it.readStatus in setOf(BookReadStatus.READ, BookReadStatus.SKIMMED) },
            coverUrl = representative.coverUrl,
            lastAddedAtMillis = timestamp,
            availableFormats = normalizedAvailableFormats(
                members.flatMap { book ->
                    book.availableFormats.map { it to MediaKind.UNKNOWN }
                        .ifEmpty { listOf(book.format to book.mediaKind) }
                }
            )
        )
        summary to if (useUpdatedAt) {
            SeriesIntroductionOrder(fallbackAddedAtMillis = timestamp)
        } else {
            introductionOrder
        }
    }.sortedWith { (leftSummary, leftOrder), (rightSummary, rightOrder) ->
        compareSeriesIntroductionOrder(leftOrder, rightOrder)
            .takeUnless { it == 0 }
            ?: compareValues(leftSummary.name.lowercase(), rightSummary.name.lowercase())
    }.map { (summary, _) -> summary }
}

internal fun homeSeriesPreview(
    series: List<SeriesSummary>,
    books: List<BookSummary>? = null,
    limit: Int = HOME_PREVIEW_LIMIT,
    preferBookOrbitImportSequence: Boolean = false
): List<SeriesSummary> {
    val uniqueSeries = series.groupBy { it.id }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.lastAddedAtMillis ?: 0L } }
    val introductionOrderBySeries = books?.let { sourceBooks ->
        seriesIntroductionOrdersByKey(sourceBooks, preferBookOrbitImportSequence)
    }.orEmpty()
    return uniqueSeries
        .mapNotNull { summary ->
            val order = if (books == null) {
                SeriesIntroductionOrder(fallbackAddedAtMillis = summary.lastAddedAtMillis)
            } else {
                introductionOrderBySeries[summary.id]
                    ?: introductionOrderBySeries[summary.name.trim()]
                    ?: return@mapNotNull null
            }
            if (order.importSequence == null && order.fallbackAddedAtMillis == null) null else summary to order
        }
        .sortedWith { (leftSummary, leftOrder), (rightSummary, rightOrder) ->
            compareSeriesIntroductionOrder(leftOrder, rightOrder)
                .takeUnless { it == 0 }
                ?: compareValues(leftSummary.name.lowercase(), rightSummary.name.lowercase())
        }
        .map { (summary, _) -> summary }
        .take(limit)
}

internal fun homeSeriesShelfItems(
    series: List<SeriesSummary>,
    books: List<BookSummary>,
    limit: Int = Int.MAX_VALUE,
    preferBookOrbitImportSequence: Boolean = false
): List<Pair<String, BookSummary>> = homeSeriesPreview(
    enrichSeriesAvailableFormats(series, books),
    books = books,
    limit = limit,
    preferBookOrbitImportSequence = preferBookOrbitImportSequence
).map { item -> item.name to item.asShelfBook() }

private data class SeriesIntroductionOrder(
    val importSequence: Long? = null,
    val fallbackAddedAtMillis: Long? = null
)

private fun BookSummary.bookOrbitImportSequence(): Long? = id.toLongOrNull()?.takeIf { it >= 0L }

private fun seriesIntroductionOrder(
    members: List<BookSummary>,
    preferBookOrbitImportSequence: Boolean
): SeriesIntroductionOrder = SeriesIntroductionOrder(
    importSequence = if (preferBookOrbitImportSequence) {
        members.mapNotNull(BookSummary::bookOrbitImportSequence).minOrNull()
    } else {
        null
    },
    fallbackAddedAtMillis = members.mapNotNull(BookSummary::addedAtMillis).minOrNull()
)

private fun seriesIntroductionOrdersByKey(
    books: List<BookSummary>,
    preferBookOrbitImportSequence: Boolean
): Map<String, SeriesIntroductionOrder> {
    val membersByKey = mutableMapOf<String, MutableList<BookSummary>>()
    books.forEach { book ->
        val keys = listOfNotNull(
            book.seriesId?.takeIf(String::isNotBlank),
            book.seriesName?.trim()?.takeIf(String::isNotBlank)
        ).distinct()
        keys.forEach { key -> membersByKey.getOrPut(key) { mutableListOf() }.add(book) }
    }
    return membersByKey.mapValues { (_, members) ->
        seriesIntroductionOrder(members, preferBookOrbitImportSequence)
    }
}

private fun compareSeriesIntroductionOrder(
    left: SeriesIntroductionOrder,
    right: SeriesIntroductionOrder
): Int = when {
    left.importSequence != null && right.importSequence != null ->
        compareValues(right.importSequence, left.importSequence)
    left.importSequence != null -> -1
    right.importSequence != null -> 1
    else -> compareValues(
        right.fallbackAddedAtMillis ?: Long.MIN_VALUE,
        left.fallbackAddedAtMillis ?: Long.MIN_VALUE
    )
}

private fun SeriesSummary.asShelfBook(): BookSummary = BookSummary(
    libraryId = "series",
    id = id,
    fileId = null,
    title = name,
    coverUrl = coverUrl,
    seriesId = id,
    seriesName = name,
    author = authors.joinToString().takeIf { it.isNotBlank() },
    availableFormats = availableFormats
)

internal fun recentSeries(
    books: List<BookSummary>,
    useUpdatedAt: Boolean,
    limit: Int? = HOME_PREVIEW_LIMIT
): List<Pair<String, BookSummary>> {
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
        .let { result -> limit?.let(result::take) ?: result }
}

@Composable
private fun nativeBookStatus(book: BookSummary, offline: Boolean): String {
    return when {
        book.isDownloaded -> stringResource(R.string.browse_downloaded)
        offline -> stringResource(R.string.browse_online_only)
        book.isRead -> stringResource(R.string.browse_read)
        !book.progressLabel.isNullOrBlank() -> book.progressLabel
        !book.format.isNullOrBlank() -> book.format.uppercase()
        else -> book.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Composable
internal fun bookDetailGroupProgressLabel(progress: AvailableFileGroupDownloadProgress): String {
    val files = stringResource(
        R.string.browse_group_progress_files,
        progress.downloadedFileCount,
        progress.totalFileCount
    )
    val bytes = progress.totalKnownBytes?.let { total ->
        "${formatFileSize(progress.downloadedKnownBytes ?: 0L)} / ${formatFileSize(total)}"
    }
    return listOfNotNull(files, bytes).joinToString(" · ")
}

@Composable
private fun bookDetailIdentityStatus(
    book: BookSummary,
    offline: Boolean,
    groupProgress: AvailableFileGroupDownloadProgress? = null,
    isDownloading: Boolean = false
): String {
    val format = book.format?.takeIf { it.isNotBlank() }?.uppercase()
        ?: book.mediaKind.name.lowercase().replaceFirstChar { it.uppercase() }
    val downloadingLabel = stringResource(R.string.browse_downloading)
    val downloadedLabel = stringResource(R.string.browse_downloaded)
    val downloadState = groupProgress?.state?.let {
        when (it) {
            AvailableFileGroupDownloadState.NOT_DOWNLOADED ->
                stringResource(R.string.browse_not_downloaded)
            AvailableFileGroupDownloadState.PARTIAL ->
                stringResource(R.string.browse_partially_downloaded)
            AvailableFileGroupDownloadState.COMPLETE -> downloadedLabel
        }
    }
    return when {
        groupProgress != null && isDownloading -> stringResource(
            R.string.browse_format_status,
            format,
            downloadingLabel
        )
        downloadState != null -> stringResource(
            R.string.browse_format_status,
            format,
            downloadState
        )
        book.isDownloaded -> stringResource(
            R.string.browse_format_status,
            format,
            downloadedLabel
        )
        offline -> stringResource(R.string.browse_online_only)
        else -> format
    }
}
