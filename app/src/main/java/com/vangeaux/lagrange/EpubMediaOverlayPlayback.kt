package com.vangeaux.lagrange

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class EpubMediaOverlayPlayableClip(
    val clip: EpubMediaOverlayClip,
    val audioFile: File
)

/** Retains narration ownership while the reader Activity is recreated for configuration changes. */
internal class EpubMediaOverlayPlaybackViewModel : ViewModel() {
    var player: ExoPlayer? = null
        private set
    var sessionBinder: ReadiumAudioPlaybackService.Binder? = null
        private set
    var playableClips: List<EpubMediaOverlayPlayableClip> = emptyList()
        private set
    var activeClip by mutableStateOf<EpubMediaOverlayClip?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speed by mutableStateOf(1f)
        private set

    fun attach(
        player: ExoPlayer,
        sessionBinder: ReadiumAudioPlaybackService.Binder,
        clips: List<EpubMediaOverlayPlayableClip>,
        activeClip: EpubMediaOverlayClip?
    ) {
        this.player = player
        this.sessionBinder = sessionBinder
        playableClips = clips
        this.activeClip = activeClip
        isPlaying = player.isPlaying
        speed = player.playbackParameters.speed
    }

    fun updateClip(clip: EpubMediaOverlayClip) {
        activeClip = clip
    }

    fun updatePlaying(isPlaying: Boolean) {
        this.isPlaying = isPlaying
    }

    fun updateSpeed(speed: Float) {
        this.speed = speed
    }

    fun closePlayback() {
        sessionBinder?.closeMediaOverlaySession()
        sessionBinder = null
        player?.release()
        player = null
        playableClips = emptyList()
        activeClip = null
        isPlaying = false
        speed = 1f
    }

    override fun onCleared() {
        closePlayback()
        super.onCleared()
    }
}

internal object EpubMediaOverlayResources {
    private const val MAX_AUDIO_ENTRY_BYTES = 256L * 1024L * 1024L
    private const val MAX_TOTAL_AUDIO_BYTES = 1024L * 1024L * 1024L

    suspend fun extract(
        epubFile: File,
        playlist: EpubMediaOverlayPlaylist,
        cacheDir: File,
        readerKey: String
    ): List<EpubMediaOverlayPlayableClip> = withContext(Dispatchers.IO) {
        if (playlist.items.isEmpty()) return@withContext emptyList()
        val key = MessageDigest.getInstance("SHA-256")
            .digest(readerKey.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
        val targetDir = File(cacheDir, "epub-media-overlay/$key-${epubFile.length()}-${epubFile.lastModified()}")
        if (!targetDir.isDirectory && !targetDir.mkdirs()) return@withContext emptyList()
        ZipFile(epubFile).use { zip ->
            val extracted = mutableMapOf<String, File>()
            var totalBytes = 0L
            playlist.items.mapNotNull { clip ->
                val audio = extracted[clip.audioHref] ?: run {
                    val entry = zip.getEntry(clip.audioHref)?.takeUnless { it.isDirectory } ?: return@mapNotNull null
                    if (entry.size <= 0L || entry.size > MAX_AUDIO_ENTRY_BYTES || totalBytes + entry.size > MAX_TOTAL_AUDIO_BYTES) {
                        return@mapNotNull null
                    }
                    val extension = clip.audioHref.substringAfterLast('.', "audio")
                        .takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
                        ?.lowercase() ?: "audio"
                    val destination = File(targetDir, "audio-${extracted.size}.$extension")
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                totalBytes += count
                                if (copied > MAX_AUDIO_ENTRY_BYTES || totalBytes > MAX_TOTAL_AUDIO_BYTES) {
                                    destination.delete()
                                    return@mapNotNull null
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    extracted[clip.audioHref] = destination
                    destination
                }
                EpubMediaOverlayPlayableClip(clip, audio)
            }
        }
    }
}

internal class EpubMediaOverlayPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences("epub_media_overlay_positions", Context.MODE_PRIVATE)

    fun read(readerKey: String): String? = readerKey.takeIf(String::isNotBlank)
        ?.let { preferences.getString(it, null) }

    fun write(readerKey: String, textHref: String, fragment: String?) {
        if (readerKey.isBlank()) return
        val value = fragment?.takeIf(String::isNotBlank)?.let { "$textHref#$it" } ?: textHref
        preferences.edit().putString(readerKey, value).apply()
    }
}

internal fun epubMediaOverlayStartIndex(
    playlist: List<EpubMediaOverlayPlayableClip>,
    savedLocation: String?
): Int {
    if (savedLocation.isNullOrBlank()) return 0
    val separator = savedLocation.indexOf('#')
    val href = if (separator >= 0) savedLocation.substring(0, separator) else savedLocation
    val fragment = if (separator >= 0) savedLocation.substring(separator + 1) else null
    return playlist.indexOfFirst { item ->
        item.clip.textHref == href && item.clip.textFragment == fragment
    }.takeIf { it >= 0 } ?: 0
}

internal fun epubMediaOverlayClipIndexForSelection(
    playlist: List<EpubMediaOverlayClip>,
    sectionIndex: Int,
    textFragment: String?
): Int? {
    val fragment = textFragment?.takeIf(String::isNotBlank) ?: return null
    return playlist.firstOrNull { it.sectionIndex == sectionIndex && it.textFragment == fragment }?.index
}

internal suspend fun prepareEpubMediaOverlayThenNavigateAndPlay(
    prepare: () -> Unit,
    navigate: suspend () -> Unit,
    play: () -> Unit
) {
    prepare()
    navigate()
    play()
}

internal fun epubMediaOverlayMediaItem(
    clip: EpubMediaOverlayPlayableClip,
    bookTitle: String
): MediaItem {
    return MediaItem.Builder()
        .setMediaId(clip.clip.index.toString())
        .setUri(clip.audioFile.toURI().toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Read-along · $bookTitle")
                .setArtist("EPUB narration")
                .build()
        )
        .setClippingConfiguration(epubMediaOverlayClippingConfiguration(clip.clip))
        .build()
}

internal fun epubMediaOverlayClippingConfiguration(clip: EpubMediaOverlayClip): MediaItem.ClippingConfiguration =
    MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs((clip.clipBeginSeconds * 1_000.0).toLong().coerceAtLeast(0L))
        .apply {
            clip.clipEndSeconds?.let { end ->
                setEndPositionMs((end * 1_000.0).toLong().coerceAtLeast(0L))
            }
        }
        .build()

internal fun createEpubMediaOverlayPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build(),
        true
    )
    .setHandleAudioBecomingNoisy(true)
    .build()

@Composable
internal fun EpubMediaOverlayControls(
    speed: Float,
    isPlaying: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var speedOverlayVisible by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Read-along", modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = { speedOverlayVisible = true },
                    modifier = Modifier
                        .testTag("epub-readalong-speed")
                        .semantics { contentDescription = "Select read-along speed" },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("${formatPlaybackSpeed(speed.toDouble())}×")
                }
                IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous narration sentence")
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.testTag("epub-readalong-play-pause")
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause narration" else "Play narration"
                    )
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next narration sentence")
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("epub-readalong-close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close read-along")
                }
            }
        }
        if (speedOverlayVisible) {
            AudiobookPlaybackSpeedOverlay(
                speed = speed,
                onSpeedChange = onSpeedChange,
                onDismiss = { speedOverlayVisible = false }
            )
        }
    }
}
