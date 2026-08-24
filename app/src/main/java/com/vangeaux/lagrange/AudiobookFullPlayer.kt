package com.vangeaux.lagrange

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import android.content.res.Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
private data class FullAudioSnapshot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float
)

private fun fullAudioSnapshot(session: ReadiumAudioPlaybackService.Session): FullAudioSnapshot =
    FullAudioSnapshot(
        isPlaying = session.player.isPlaying,
        positionMs = session.absolutePositionMs(),
        durationMs = session.totalDurationMs(),
        speed = session.player.playbackParameters.speed
    )

internal fun audiobookChapterBounds(
    chapters: List<AudiobookChapter>,
    positionMs: Long,
    totalDurationMs: Long
): Pair<Long, Long>? {
    if (chapters.isEmpty() || totalDurationMs <= 0L) return null
    val index = chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
    val startMs = chapters[index].startMs.coerceIn(0L, totalDurationMs)
    val endMs = chapters.getOrNull(index + 1)?.startMs?.coerceIn(startMs, totalDurationMs)
        ?: totalDurationMs
    return startMs to endMs
}

internal fun audiobookChapterEndMs(
    chapters: List<AudiobookChapter>,
    positionMs: Long
): Long? = chapters.getOrNull(chapters.indexOfLast { it.startMs <= positionMs } + 1)?.startMs

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadiumFullAudioPlayer(
    controller: ReadiumAudioPlaybackController,
    onCollapse: () -> Unit,
    onBookDetails: (BookSummary) -> Unit,
    onClosePlayer: () -> Unit,
    loadSessionHistory: suspend (BookSummary) -> List<AudiobookSessionEvent>,
    clearSessionHistory: (BookSummary) -> Unit,
    loadServerReadingSessions: suspend (String) -> BookReadingSessionsResult,
    loadServerReadingAttempts: suspend (String) -> ReadingAttemptsResult
) {
    val playerLocked by controller.playerLocked.collectAsState()
    val playerScope = rememberCoroutineScope()
    val closePlayer = {
        playerScope.launch {
            controller.close()
            onClosePlayer()
        }
    }
    BackHandler(onBack = { if (!playerLocked) onCollapse() })
    val session by produceState<ReadiumAudioPlaybackService.Session?>(null, controller) {
        controller.session().collect { value = it }
    }
    val current = session
    if (current == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF242222)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Preparing audiobook player…", color = Color.White)
            }
        }
        return
    }
    val playback by produceState(fullAudioSnapshot(current), current.player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                value = fullAudioSnapshot(current)
            }
        }
        current.player.addListener(listener)
        try {
            while (isActive) {
                value = fullAudioSnapshot(current)
                delay(500L)
            }
        } finally {
            current.player.removeListener(listener)
        }
    }
    val chapters = current.book.audioChapters
    val bounds = audiobookChapterBounds(chapters, playback.positionMs, playback.durationMs)
    val activeChapterIndex = chapters.indexOfLast { it.startMs <= playback.positionMs }.coerceAtLeast(0)
    val chapterStartMs = bounds?.first ?: 0L
    val chapterEndMs = bounds?.second ?: 1L
    val chapterPositionMs = (playback.positionMs - chapterStartMs).coerceIn(0L, (chapterEndMs - chapterStartMs).coerceAtLeast(1L))
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        FullPlayerLandscape(
            controller = controller,
            current = current,
            playback = playback,
            chapters = chapters,
            activeChapterIndex = activeChapterIndex,
            chapterStartMs = chapterStartMs,
            chapterEndMs = chapterEndMs,
            chapterPositionMs = chapterPositionMs,
            chapterBoundsAvailable = bounds != null,
            playerLocked = playerLocked,
            onCollapse = onCollapse,
            onBookDetails = onBookDetails,
            onClosePlayer = onClosePlayer,
            loadSessionHistory = loadSessionHistory,
            clearSessionHistory = clearSessionHistory,
            loadServerReadingSessions = loadServerReadingSessions,
            loadServerReadingAttempts = loadServerReadingAttempts
        )
        return
    }
    val sleepTimer by controller.sleepTimer.collectAsState()
    val context = LocalContext.current
    val preferences = remember { AppPreferencesStore(context) }
    var skipBackSeconds by remember { mutableIntStateOf(preferences.readAudioSkipBackSeconds()) }
    var skipForwardSeconds by remember { mutableIntStateOf(preferences.readAudioSkipForwardSeconds()) }
    var intervalDirection by remember { mutableStateOf<SkipDirection?>(null) }
    var sleepMenuExpanded by remember { mutableStateOf(false) }
    var chapterMenuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showSessionHistory by remember { mutableStateOf(false) }
    var sessionHistory by remember(current.book.id, current.book.fileId) { mutableStateOf(emptyList<AudiobookSessionEvent>()) }
    var serverReadingSessions by remember(current.book.id, current.book.fileId) { mutableStateOf<BookReadingSessionsResult?>(null) }
    var serverReadingAttempts by remember(current.book.id, current.book.fileId) { mutableStateOf<ReadingAttemptsResult?>(null) }
    var loadingServerReadingHistory by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showCoverViewer by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var overallSeekPosition by remember { mutableFloatStateOf(playback.positionMs.toFloat()) }
    var chapterSeekPosition by remember { mutableFloatStateOf(chapterPositionMs.toFloat()) }
    var isSeekingOverall by remember { mutableStateOf(false) }
    var isSeekingChapter by remember { mutableStateOf(false) }
    LaunchedEffect(playback.positionMs, isSeekingOverall, isSeekingChapter) {
        if (!isSeekingOverall) overallSeekPosition = playback.positionMs.toFloat()
        if (!isSeekingChapter) chapterSeekPosition = chapterPositionMs.toFloat()
    }
    LaunchedEffect(showSessionHistory, current.book.id, current.book.fileId) {
        if (showSessionHistory) {
            sessionHistory = loadSessionHistory(current.book)
            loadingServerReadingHistory = true
            serverReadingSessions = loadServerReadingSessions(current.book.id)
            serverReadingAttempts = loadServerReadingAttempts(current.book.id)
            loadingServerReadingHistory = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF242222), contentColor = Color.White) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val groupScale = fullPlayerGroupScale(maxHeight.value, landscape = false)
            val contentPadding = (24f * groupScale).coerceAtLeast(16f).dp
            val verticalPadding = (8f * groupScale).coerceAtLeast(4f).dp
            val sectionGap = (8f * groupScale).coerceAtLeast(4f).dp
            val controlGap = (12f * groupScale).coerceAtLeast(6f).dp
            val coverFraction = (0.5f * groupScale).coerceAtLeast(0.36f)
            val primaryControlSize = (76f * groupScale).coerceAtLeast(64f).dp
            val secondaryControlSize = (76f * groupScale).coerceAtLeast(64f).dp
            val titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * groupScale
            )
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = contentPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse, enabled = !playerLocked) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse audiobook player")
                }
                Text(
                    text = if (current.book.isDownloaded) "LOCAL FILE" else "STREAM",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing
                )
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More audiobook actions")
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play session history") },
                            onClick = {
                                overflowExpanded = false
                                showSessionHistory = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (playerLocked) "Unlock player" else "Lock player") },
                            onClick = {
                                overflowExpanded = false
                                controller.setPlayerLocked(!playerLocked)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Close player") },
                            onClick = {
                                overflowExpanded = false
                                closePlayer()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val coverHeight = minOf(
                    maxHeight * coverFraction,
                    maxWidth / current.book.coverAspectRatio.widthToHeight
                )
                CompactAudiobookCover(
                    book = current.book,
                    coverLoader = controller::loadCover,
                    modifier = Modifier
                        .width(coverHeight * current.book.coverAspectRatio.widthToHeight)
                        .height(coverHeight),
                    coverHeight = null,
                    onClick = { showCoverViewer = true }
                )
            }
            Spacer(Modifier.height(sectionGap))
            Text(
                text = current.book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE)
                    .clickable { onBookDetails(current.book) },
                style = titleStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = current.book.seriesName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { series ->
                        val index = current.book.seriesIndex?.let(::formatFullPlayerSeriesIndex)
                        if (index == null) series else "$series · #$index"
                    }
                    .orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !current.book.seriesName.isNullOrBlank()) {
                        onBookDetails(current.book)
                    },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * groupScale
                ),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = chapters.getOrNull(activeChapterIndex)?.title ?: "Audiobook",
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * groupScale
                ),
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(sectionGap))
            AudioSeekBar(
                positionMs = overallSeekPosition,
                durationMs = playback.durationMs,
                onSeeking = { isSeekingOverall = true; overallSeekPosition = it },
                onSeekFinished = { current.seekToAbsolutePosition(overallSeekPosition.toLong()); isSeekingOverall = false },
                leading = formatPlaybackTime(overallSeekPosition.toLong()),
                trailing = "−${formatPlaybackTime((playback.durationMs - overallSeekPosition).toLong().coerceAtLeast(0L))}",
                label = "Book progress",
                layoutScale = groupScale,
                description = "Seek through audiobook"
            )
            AudioSeekBar(
                positionMs = chapterSeekPosition,
                durationMs = (chapterEndMs - chapterStartMs).coerceAtLeast(1L),
                onSeeking = { isSeekingChapter = true; chapterSeekPosition = it },
                onSeekFinished = {
                    current.seekToAbsolutePosition(chapterStartMs + chapterSeekPosition.toLong())
                    isSeekingChapter = false
                },
                leading = formatPlaybackTime(chapterSeekPosition.toLong()),
                trailing = "−${formatPlaybackTime((chapterEndMs - chapterStartMs - chapterSeekPosition).toLong().coerceAtLeast(0L))}",
                label = "Chapter progress",
                layoutScale = groupScale,
                description = "Seek through current chapter",
                enabled = bounds != null
            )
            Spacer(Modifier.height(controlGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!playerLocked) FullPlayerIconButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "Previous audiobook chapter",
                    onClick = {
                        val previous = chapters.getOrNull(activeChapterIndex - 1)?.startMs
                        if (previous != null) current.seekToAbsolutePosition(previous) else current.seekToAbsolutePosition(chapterStartMs)
                    }
                )
                if (!playerLocked) FullPlayerIconButton(
                    icon = Icons.Default.Replay10,
                    description = "Skip back $skipBackSeconds seconds",
                    onClick = {
                        current.seekToAbsolutePosition((current.absolutePositionMs() - skipBackSeconds * 1000L).coerceAtLeast(0L))
                    },
                    onLongClick = { intervalDirection = SkipDirection.BACK }
                )
                IconButton(
                    onClick = { if (playback.isPlaying) current.player.pause() else current.player.play() },
                    modifier = Modifier.size(primaryControlSize).background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause audiobook" else "Play audiobook",
                        modifier = Modifier.size((38f * groupScale).coerceAtLeast(32f).dp)
                    )
                }
                if (!playerLocked) FullPlayerIconButton(
                    icon = Icons.Default.Forward30,
                    description = "Skip forward $skipForwardSeconds seconds",
                    onClick = {
                        current.seekToAbsolutePosition(
                            (current.absolutePositionMs() + skipForwardSeconds * 1000L)
                                .coerceAtMost(playback.durationMs)
                        )
                    },
                    onLongClick = { intervalDirection = SkipDirection.FORWARD }
                )
                if (!playerLocked) FullPlayerIconButton(
                    icon = Icons.Default.SkipNext,
                    description = "Next audiobook chapter",
                    onClick = {
                        chapters.getOrNull(activeChapterIndex + 1)?.startMs?.let(current::seekToAbsolutePosition)
                    }
                )
            }
            Spacer(Modifier.height(controlGap))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box {
                    IconButton(
                        onClick = { showChapterList = true },
                        enabled = chapters.isNotEmpty(),
                        modifier = Modifier.size(secondaryControlSize)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            Text("Chapter", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(expanded = chapterMenuExpanded, onDismissRequest = { chapterMenuExpanded = false }) {
                        chapters.forEachIndexed { index, chapter ->
                            AudiobookChapterMenuItem(
                                index = index,
                                title = chapter.title,
                                selected = index == activeChapterIndex,
                                onClick = { chapterMenuExpanded = false; current.seekToAbsolutePosition(chapter.startMs) }
                            )
                        }
                    }
                }
                Box {
                    TextButton(
                        onClick = { speedMenuExpanded = true },
                        modifier = Modifier.width((104f * groupScale).coerceAtLeast(88f).dp).height(secondaryControlSize),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "${formatPlaybackSpeed(playback.speed.toDouble())}×",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text("Speed", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                        AUDIO_PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${formatPlaybackSpeed(speed.toDouble())}×") },
                                onClick = { speedMenuExpanded = false; controller.setPlaybackSpeed(current.player, speed) }
                            )
                        }
                    }
                }
                Box {
                    IconButton(
                        onClick = { sleepMenuExpanded = true },
                        modifier = Modifier.size(secondaryControlSize)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Bedtime, contentDescription = null)
                            Text("Sleep", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(expanded = sleepMenuExpanded, onDismissRequest = { sleepMenuExpanded = false }) {
                        AudioSleepTimerOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    val remaining = sleepTimer.remainingMs
                                    Text(
                                        if (option == sleepTimer.option && remaining != null) {
                                            "${option.label} · ${formatPlaybackTime(remaining)}"
                                        } else {
                                            option.label
                                        }
                                    )
                                },
                                onClick = { sleepMenuExpanded = false; controller.setSleepTimer(option) }
                            )
                        }
                    }
                }
            }
            if (sleepTimer.option != AudioSleepTimerOption.OFF) {
                Text(
                    text = "Sleep timer: ${sleepTimer.option.label} · ${sleepTimer.remainingMs?.let(::formatPlaybackTime).orEmpty()}",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        }
    }

    if (showSessionHistory) {
        FullPlayerSessionHistoryDialog(
            bookTitle = current.book.title,
            events = sessionHistory,
            isLoadingServerReadingHistory = loadingServerReadingHistory,
            serverReadingSessions = serverReadingSessions,
            serverReadingAttempts = serverReadingAttempts,
            onEventClick = { event ->
                current.seekToAbsolutePosition(event.positionMs)
                showSessionHistory = false
            },
            onClearClick = {
                sessionHistory = emptyList<AudiobookSessionEvent>()
                clearSessionHistory(current.book)
            },
            onCloseClick = { showSessionHistory = false }
        )
    }
    if (showChapterList) {
        FullPlayerChapterListSheet(
            chapters = chapters,
            activeChapterIndex = activeChapterIndex,
            onChapterSelected = { chapter ->
                current.seekToAbsolutePosition(chapter.startMs)
                showChapterList = false
            },
            onClose = { showChapterList = false }
        )
    }
    if (showCoverViewer) {
        FullScreenCoverViewer(
            book = current.book,
            coverLoader = controller::loadCover,
            onDismiss = { showCoverViewer = false }
        )
    }

    intervalDirection?.let { direction ->
        AlertDialog(
            onDismissRequest = { intervalDirection = null },
            title = { Text(if (direction == SkipDirection.BACK) "Rewind interval" else "Forward interval") },
            text = {
                Column {
                    AUDIO_SKIP_SECONDS_OPTIONS.forEach { seconds ->
                        TextButton(
                            onClick = {
                                if (direction == SkipDirection.BACK) {
                                    skipBackSeconds = seconds
                                    preferences.saveAudioSkipBackSeconds(seconds)
                                } else {
                                    skipForwardSeconds = seconds
                                    preferences.saveAudioSkipForwardSeconds(seconds)
                                }
                                intervalDirection = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$seconds seconds") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { intervalDirection = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FullPlayerSessionHistoryDialog(
    bookTitle: String,
    events: List<AudiobookSessionEvent>,
    isLoadingServerReadingHistory: Boolean,
    serverReadingSessions: BookReadingSessionsResult?,
    serverReadingAttempts: ReadingAttemptsResult?,
    onEventClick: (AudiobookSessionEvent) -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF242222),
            contentColor = Color.White
        ) {
            AudiobookSessionHistory(
                bookTitle = bookTitle,
                events = events,
                isLoadingServerReadingHistory = isLoadingServerReadingHistory,
                serverReadingSessions = serverReadingSessions,
                serverReadingAttempts = serverReadingAttempts,
                onEventClick = onEventClick,
                onClearClick = onClearClick,
                onCloseClick = onCloseClick,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullPlayerChapterListSheet(
    chapters: List<AudiobookChapter>,
    activeChapterIndex: Int,
    onChapterSelected: (AudiobookChapter) -> Unit,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = Modifier
            .fillMaxHeight()
            .testTag("audiobook-chapter-sheet"),
        shape = RectangleShape,
        containerColor = Color(0xFF242222),
        contentColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("audiobook-chapter-sheet-content")
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close chapter list")
                }
                Text(
                    text = "Chapters",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("audiobook-chapter-list"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    AudiobookChapterMenuItem(
                        index = index,
                        title = chapter.title,
                        selected = index == activeChapterIndex,
                        onClick = { onChapterSelected(chapter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookChapterMenuItem(
    index: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                "Chapter ${index + 1}",
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                title.ifBlank { "Chapter ${index + 1}" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayerLandscape(
    controller: ReadiumAudioPlaybackController,
    current: ReadiumAudioPlaybackService.Session,
    playback: FullAudioSnapshot,
    chapters: List<AudiobookChapter>,
    activeChapterIndex: Int,
    chapterStartMs: Long,
    chapterEndMs: Long,
    chapterPositionMs: Long,
    chapterBoundsAvailable: Boolean,
    playerLocked: Boolean,
    onCollapse: () -> Unit,
    onBookDetails: (BookSummary) -> Unit,
    onClosePlayer: () -> Unit,
    loadSessionHistory: suspend (BookSummary) -> List<AudiobookSessionEvent>,
    clearSessionHistory: (BookSummary) -> Unit,
    loadServerReadingSessions: suspend (String) -> BookReadingSessionsResult,
    loadServerReadingAttempts: suspend (String) -> ReadingAttemptsResult
) {
    val playerScope = rememberCoroutineScope()
    val closePlayer = {
        playerScope.launch {
            controller.close()
            onClosePlayer()
        }
    }
    BackHandler(onBack = { if (!playerLocked) onCollapse() })
    val context = LocalContext.current
    val preferences = remember { AppPreferencesStore(context) }
    val sleepTimer by controller.sleepTimer.collectAsState()
    var showCoverViewer by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var chapterMenuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var sleepMenuExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showSessionHistory by remember { mutableStateOf(false) }
    var sessionHistory by remember(current.book.id, current.book.fileId) { mutableStateOf(emptyList<AudiobookSessionEvent>()) }
    var serverReadingSessions by remember(current.book.id, current.book.fileId) { mutableStateOf<BookReadingSessionsResult?>(null) }
    var serverReadingAttempts by remember(current.book.id, current.book.fileId) { mutableStateOf<ReadingAttemptsResult?>(null) }
    var loadingServerReadingHistory by remember(current.book.id, current.book.fileId) { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var intervalDirection by remember { mutableStateOf<SkipDirection?>(null) }
    var skipBackSeconds by remember { mutableIntStateOf(preferences.readAudioSkipBackSeconds()) }
    var skipForwardSeconds by remember { mutableIntStateOf(preferences.readAudioSkipForwardSeconds()) }
    var overallPosition by remember { mutableFloatStateOf(playback.positionMs.toFloat()) }
    var chapterPosition by remember { mutableFloatStateOf(chapterPositionMs.toFloat()) }
    var isSeekingOverall by remember { mutableStateOf(false) }
    var isSeekingChapter by remember { mutableStateOf(false) }
    LaunchedEffect(playback.positionMs, isSeekingOverall, isSeekingChapter) {
        if (!isSeekingOverall) overallPosition = playback.positionMs.toFloat()
        if (!isSeekingChapter) chapterPosition = chapterPositionMs.toFloat()
    }
    LaunchedEffect(showSessionHistory, current.book.id, current.book.fileId) {
        if (showSessionHistory) {
            sessionHistory = loadSessionHistory(current.book)
            loadingServerReadingHistory = true
            serverReadingSessions = loadServerReadingSessions(current.book.id)
            serverReadingAttempts = loadServerReadingAttempts(current.book.id)
            loadingServerReadingHistory = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF242222),
        contentColor = Color.White
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val groupScale = fullPlayerGroupScale(maxHeight.value, landscape = true)
            val contentPadding = (16f * groupScale).coerceAtLeast(8f).dp
            val controlGap = (8f * groupScale).coerceAtLeast(2f).dp
            val secondaryControlSize = 64.dp
            val primaryControlSize = (72f * groupScale).coerceAtLeast(64f).dp
            val titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * groupScale
            )
            Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = contentPadding, vertical = (8f * groupScale).coerceAtLeast(2f).dp),
            horizontalArrangement = Arrangement.spacedBy((20f * groupScale).coerceAtLeast(10f).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val coverHeight = minOf(
                    maxHeight,
                    maxWidth / current.book.coverAspectRatio.widthToHeight
                )
                CompactAudiobookCover(
                    book = current.book,
                    coverLoader = controller::loadCover,
                    modifier = Modifier
                        .width(coverHeight * current.book.coverAspectRatio.widthToHeight)
                        .height(coverHeight),
                    coverHeight = null,
                    onClick = { showCoverViewer = true }
                )
            }
            Column(
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(controlGap)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse, enabled = !playerLocked) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse audiobook player")
                    }
                    Text(
                        text = if (current.book.isDownloaded) "LOCAL FILE" else "STREAM",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More audiobook actions")
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Play session history") },
                                onClick = {
                                    overflowExpanded = false
                                    showSessionHistory = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (playerLocked) "Unlock player" else "Lock player") },
                                onClick = {
                                    overflowExpanded = false
                                    controller.setPlayerLocked(!playerLocked)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close player") },
                                onClick = {
                                    overflowExpanded = false
                                    closePlayer()
                                }
                            )
                        }
                    }
                }
                Text(
                    text = current.book.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE)
                        .clickable { onBookDetails(current.book) },
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = current.book.seriesName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { series ->
                            val index = current.book.seriesIndex?.let(::formatFullPlayerSeriesIndex)
                            if (index == null) series else "$series · #$index"
                        }
                        .orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !current.book.seriesName.isNullOrBlank()) {
                            onBookDetails(current.book)
                        },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * groupScale
                    ),
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = chapters.getOrNull(activeChapterIndex)?.title ?: "Audiobook",
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * groupScale
                    ),
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height((8f * groupScale).coerceAtLeast(2f).dp))
                AudioSeekBar(
                    positionMs = overallPosition,
                    durationMs = playback.durationMs,
                    onSeeking = { isSeekingOverall = true; overallPosition = it },
                    onSeekFinished = { current.seekToAbsolutePosition(overallPosition.toLong()); isSeekingOverall = false },
                    leading = formatPlaybackTime(overallPosition.toLong()),
                    trailing = "−${formatPlaybackTime((playback.durationMs - overallPosition).toLong().coerceAtLeast(0L))}",
                    label = "Book progress",
                    layoutScale = groupScale,
                    description = "Seek through audiobook"
                )
                AudioSeekBar(
                    positionMs = chapterPosition,
                    durationMs = (chapterEndMs - chapterStartMs).coerceAtLeast(1L),
                    onSeeking = { isSeekingChapter = true; chapterPosition = it },
                    onSeekFinished = {
                        current.seekToAbsolutePosition(chapterStartMs + chapterPosition.toLong())
                        isSeekingChapter = false
                    },
                    leading = formatPlaybackTime(chapterPosition.toLong()),
                    trailing = "−${formatPlaybackTime((chapterEndMs - chapterStartMs - chapterPosition).toLong().coerceAtLeast(0L))}",
                    label = "Chapter progress",
                    layoutScale = groupScale,
                    description = "Seek through current chapter",
                    enabled = chapterBoundsAvailable
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!playerLocked) FullPlayerIconButton(
                        icon = Icons.Default.SkipPrevious,
                        description = "Previous audiobook chapter",
                        onClick = {
                            chapters.getOrNull(activeChapterIndex - 1)?.startMs?.let(current::seekToAbsolutePosition)
                        }
                    )
                    if (!playerLocked) FullPlayerIconButton(
                        icon = Icons.Default.Replay10,
                        description = "Skip back $skipBackSeconds seconds",
                        onClick = {
                            current.seekToAbsolutePosition((current.absolutePositionMs() - skipBackSeconds * 1000L).coerceAtLeast(0L))
                        },
                        onLongClick = { intervalDirection = SkipDirection.BACK }
                    )
                    IconButton(
                        onClick = { if (playback.isPlaying) current.player.pause() else current.player.play() },
                        modifier = Modifier.size(primaryControlSize).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Pause audiobook" else "Play audiobook",
                            modifier = Modifier.size((36f * groupScale).coerceAtLeast(30f).dp)
                        )
                    }
                    if (!playerLocked) FullPlayerIconButton(
                        icon = Icons.Default.Forward30,
                        description = "Skip forward $skipForwardSeconds seconds",
                        onClick = {
                            current.seekToAbsolutePosition((current.absolutePositionMs() + skipForwardSeconds * 1000L).coerceAtMost(playback.durationMs))
                        },
                        onLongClick = { intervalDirection = SkipDirection.FORWARD }
                    )
                    if (!playerLocked) FullPlayerIconButton(
                        icon = Icons.Default.SkipNext,
                        description = "Next audiobook chapter",
                        onClick = { chapters.getOrNull(activeChapterIndex + 1)?.startMs?.let(current::seekToAbsolutePosition) }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Box {
                        IconButton(onClick = { showChapterList = true }, enabled = chapters.isNotEmpty(), modifier = Modifier.size(secondaryControlSize)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                                Text("Chapter", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        DropdownMenu(expanded = chapterMenuExpanded, onDismissRequest = { chapterMenuExpanded = false }) {
                            chapters.forEachIndexed { index, chapter ->
                                AudiobookChapterMenuItem(
                                    index = index,
                                    title = chapter.title,
                                    selected = index == activeChapterIndex,
                                    onClick = { chapterMenuExpanded = false; current.seekToAbsolutePosition(chapter.startMs) }
                                )
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { speedMenuExpanded = true }, modifier = Modifier.width((96f * groupScale).coerceAtLeast(88f).dp).height(secondaryControlSize), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "${formatPlaybackSpeed(playback.speed.toDouble())}×",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text("Speed", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                            AUDIO_PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${formatPlaybackSpeed(speed.toDouble())}×") },
                                    onClick = { speedMenuExpanded = false; controller.setPlaybackSpeed(current.player, speed) }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { sleepMenuExpanded = true }, modifier = Modifier.size(secondaryControlSize)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Bedtime, contentDescription = null)
                                Text("Sleep", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        DropdownMenu(expanded = sleepMenuExpanded, onDismissRequest = { sleepMenuExpanded = false }) {
                            AudioSleepTimerOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { sleepMenuExpanded = false; controller.setSleepTimer(option) }
                                )
                            }
                        }
                    }
                }
                if (sleepTimer.option != AudioSleepTimerOption.OFF) {
                    Text(
                        text = "Sleep timer: ${sleepTimer.option.label} · ${sleepTimer.remainingMs?.let(::formatPlaybackTime).orEmpty()}",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        }
    }
    if (showSessionHistory) {
        FullPlayerSessionHistoryDialog(
            bookTitle = current.book.title,
            events = sessionHistory,
            isLoadingServerReadingHistory = loadingServerReadingHistory,
            serverReadingSessions = serverReadingSessions,
            serverReadingAttempts = serverReadingAttempts,
            onEventClick = { event ->
                current.seekToAbsolutePosition(event.positionMs)
                showSessionHistory = false
            },
            onClearClick = {
                sessionHistory = emptyList<AudiobookSessionEvent>()
                clearSessionHistory(current.book)
            },
            onCloseClick = { showSessionHistory = false }
        )
    }
    if (showChapterList) {
        FullPlayerChapterListSheet(
            chapters = chapters,
            activeChapterIndex = activeChapterIndex,
            onChapterSelected = { chapter ->
                current.seekToAbsolutePosition(chapter.startMs)
                showChapterList = false
            },
            onClose = { showChapterList = false }
        )
    }
    if (showCoverViewer) {
        FullScreenCoverViewer(book = current.book, coverLoader = controller::loadCover) {
            showCoverViewer = false
        }
    }
    intervalDirection?.let { direction ->
        AlertDialog(
            onDismissRequest = { intervalDirection = null },
            title = { Text(if (direction == SkipDirection.BACK) "Rewind interval" else "Forward interval") },
            text = {
                Column {
                    AUDIO_SKIP_SECONDS_OPTIONS.forEach { seconds ->
                        TextButton(
                            onClick = {
                                if (direction == SkipDirection.BACK) {
                                    skipBackSeconds = seconds
                                    preferences.saveAudioSkipBackSeconds(seconds)
                                } else {
                                    skipForwardSeconds = seconds
                                    preferences.saveAudioSkipForwardSeconds(seconds)
                                }
                                intervalDirection = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$seconds seconds") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { intervalDirection = null }) { Text("Cancel") } }
        )
    }
}

private enum class SkipDirection { BACK, FORWARD }

internal fun fullPlayerGroupScale(availableHeightDp: Float, landscape: Boolean): Float {
    if (!availableHeightDp.isFinite() || availableHeightDp <= 0f) return 1f
    val designHeightDp = if (landscape) 520f else 780f
    val minimumScale = if (landscape) 0.68f else 0.76f
    return (availableHeightDp / designHeightDp).coerceIn(minimumScale, 1f)
}

internal fun formatFullPlayerSeriesIndex(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun AudioSeekBar(
    positionMs: Float,
    durationMs: Long,
    onSeeking: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    leading: String,
    trailing: String,
    label: String,
    layoutScale: Float,
    description: String,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val timestampStyle = MaterialTheme.typography.labelMedium.copy(
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value * layoutScale).coerceAtLeast(10f).sp
        )
        val progressLabelStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = (MaterialTheme.typography.labelSmall.fontSize.value * layoutScale).coerceAtLeast(9f).sp
        )
        Box(modifier = Modifier.fillMaxWidth().height((20f * layoutScale).coerceAtLeast(16f).dp)) {
            Text(
                leading,
                modifier = Modifier.align(Alignment.CenterStart),
                style = timestampStyle
            )
            Text(
                label,
                modifier = Modifier.align(Alignment.Center),
                style = progressLabelStyle,
                color = Color.White.copy(alpha = 0.65f)
            )
            Text(
                trailing,
                modifier = Modifier.align(Alignment.CenterEnd),
                style = timestampStyle
            )
        }
        Slider(
            value = positionMs.coerceIn(0f, durationMs.toFloat()),
            onValueChange = onSeeking,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            enabled = enabled,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.42f),
                thumbColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth().height((32f * layoutScale).coerceAtLeast(24f).dp)
        )
    }
}
