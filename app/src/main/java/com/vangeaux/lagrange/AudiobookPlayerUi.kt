package com.vangeaux.lagrange

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

internal fun shouldShowAudiobookPreviewBanner(launchMode: ReaderLaunchMode): Boolean =
    launchMode == ReaderLaunchMode.PREVIEW

internal const val AUDIOBOOK_PREVIEW_MODE_LABEL = "Preview mode · Tap to enable listening progress"

private data class AudioPlayerSnapshot(
    val playWhenReady: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val speed: Float
)

private fun audioPlayerSnapshot(session: ReadiumAudioPlaybackService.Session): AudioPlayerSnapshot =
    AudioPlayerSnapshot(
        playWhenReady = session.player.playWhenReady,
        positionMs = session.absolutePositionMs(),
        durationMs = session.totalDurationMs().takeIf { it > 0L },
        speed = session.player.playbackParameters.speed
    )

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
    val preparingSession by controller.preparingSession.collectAsState()
    val currentSession = session
    if (currentSession == null) {
        val preparing = preparingSession ?: return
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics {
                    contentDescription = "Preparing audiobook player for ${preparing.book.title}"
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
                    book = preparing.book,
                    coverLoader = controller::loadCover
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        preparing.book.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Preparing audiobook…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(40.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            controller.close()
                            onClosed(preparing.book, preparing.launchMode)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close audiobook player")
                }
            }
        }
        return
    }
    val current = requireNotNull(currentSession)
    val playback by produceState(audioPlayerSnapshot(current), current.player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                value = audioPlayerSnapshot(current)
            }
        }
        current.player.addListener(listener)
        try {
            while (isActive) {
                value = audioPlayerSnapshot(current)
                delay(500L)
            }
        } finally {
            current.player.removeListener(listener)
        }
    }
    val positionMs = playback.positionMs
    val durationMs = playback.durationMs
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
                            current.seekToAbsolutePosition(seekPositionMs.toLong())
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
                                        current.seekToAbsolutePosition(chapter.startMs)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            current.seekToAbsolutePosition(
                                (current.absolutePositionMs() - 10_000L).coerceAtLeast(0L)
                            )
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Skip back 10 seconds")
                    }
                    IconButton(
                        onClick = {
                            if (playback.playWhenReady) current.player.pause() else current.player.play()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (playback.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playback.playWhenReady) "Pause audiobook" else "Play audiobook"
                        )
                    }
                    IconButton(
                        onClick = {
                            val target = current.absolutePositionMs() + 30_000L
                            current.seekToAbsolutePosition(
                                playback.durationMs?.let(target::coerceAtMost) ?: target
                            )
                        },
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
                            Text("${formatPlaybackSpeed(playback.speed.toDouble())}×")
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
                                        controller.setPlaybackSpeed(current.player, speed.toFloat())
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
