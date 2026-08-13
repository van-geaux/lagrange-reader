package com.vangeaux.lagrange

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.adapter.exoplayer.audio.ExoPlayerEngineProvider
import org.readium.adapter.exoplayer.audio.ExoPlayerEngine
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferences
import org.readium.adapter.exoplayer.audio.ExoPlayerSettings
import org.readium.navigator.media.audio.AudioNavigator
import org.readium.navigator.media.audio.AudioNavigatorFactory
import org.readium.navigator.media.common.DefaultMediaMetadataProvider
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import kotlin.coroutines.resume

internal const val AUDIO_OPEN_CANCELLED_MESSAGE = "Audiobook opening was cancelled."
internal const val AUDIO_SEEK_BACK_INCREMENT_MS = 10_000L
internal const val AUDIO_SEEK_FORWARD_INCREMENT_MS = 30_000L
internal const val AUDIO_SEEK_BACK_SESSION_ACTION = "com.vangeaux.lagrange.AUDIO_SEEK_BACK_10"
internal const val AUDIO_SEEK_FORWARD_SESSION_ACTION = "com.vangeaux.lagrange.AUDIO_SEEK_FORWARD_30"
private const val AUDIO_SERVICE_BIND_TIMEOUT_MILLIS = 10_000L
private const val AUDIO_ENGINE_PREPARATION_TIMEOUT_MILLIS = 30_000L

internal val audiobookSeekBackSessionCommand = SessionCommand(
    AUDIO_SEEK_BACK_SESSION_ACTION,
    Bundle()
)
internal val audiobookSeekForwardSessionCommand = SessionCommand(
    AUDIO_SEEK_FORWARD_SESSION_ACTION,
    Bundle()
)

internal fun audiobookReadiumEngineConfiguration(): ExoPlayerEngine.Configuration =
    ExoPlayerEngine.Configuration(
        seekBackwardIncrement = 10.seconds,
        seekForwardIncrement = 30.seconds
    )

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audiobookMediaButtonPreferences(): List<CommandButton> = listOf(
    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
        .setSessionCommand(audiobookSeekBackSessionCommand)
        .setDisplayName("Replay 10 seconds")
        .build(),
    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
        .setSessionCommand(audiobookSeekForwardSessionCommand)
        .setDisplayName("Forward 30 seconds")
        .build()
)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audiobookAvailableSessionCommands(): SessionCommands =
    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        .add(audiobookSeekBackSessionCommand)
        .add(audiobookSeekForwardSessionCommand)
        .build()

@androidx.annotation.OptIn(UnstableApi::class)
private fun audiobookCompactViewExtras(index: Int): Bundle = Bundle().apply {
    putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, index)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audiobookExternalPlayerCommands(
    commands: Player.Commands
): Player.Commands = commands.buildUpon()
    .removeAll(
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
    )
    .build()

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audiobookNotificationMediaButtons(
    playerCommands: Player.Commands,
    defaultButtons: List<CommandButton>
): ImmutableList<CommandButton> {
    val buttons = mutableListOf<CommandButton>()
    val seekButtons = audiobookMediaButtonPreferences()
    if (playerCommands.contains(Player.COMMAND_SEEK_BACK)) {
        buttons += seekButtons[0].withCompactViewIndex(0)
    }
    defaultButtons.firstOrNull { it.playerCommand == Player.COMMAND_PLAY_PAUSE }
        ?.let { playPause -> buttons += playPause.withCompactViewIndex(1) }
    if (playerCommands.contains(Player.COMMAND_SEEK_FORWARD)) {
        buttons += seekButtons[1].withCompactViewIndex(2)
    }
    return ImmutableList.copyOf(buttons)
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun CommandButton.withCompactViewIndex(index: Int): CommandButton =
    CommandButton.Builder(icon).apply {
        val command = sessionCommand
        if (command != null) {
            setSessionCommand(command)
        } else {
            setPlayerCommand(playerCommand)
        }
        setDisplayName(displayName)
        setEnabled(isEnabled)
        setExtras(audiobookCompactViewExtras(index))
    }.build()

@androidx.annotation.OptIn(UnstableApi::class)
private class AudiobookMediaNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        mediaSession: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> = audiobookNotificationMediaButtons(
        playerCommands = playerCommands,
        defaultButtons = super.getMediaButtons(
            mediaSession,
            playerCommands,
            customLayout,
            showPauseButton
        )
    )
}
@androidx.annotation.OptIn(UnstableApi::class)
internal class AudiobookMediaSessionCallback : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(audiobookAvailableSessionCommands())
        .setAvailablePlayerCommands(
            audiobookExternalPlayerCommands(
                session.player.availableCommands
            )
        )
        .setCustomLayout(audiobookMediaButtonPreferences())
        .build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> = when (customCommand) {
        audiobookSeekBackSessionCommand -> {
            session.player.seekBack()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        audiobookSeekForwardSessionCommand -> {
            session.player.seekForward()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        else -> super.onCustomCommand(session, controller, customCommand, args)
    }
}
@OptIn(ExperimentalReadiumApi::class)
internal typealias BookOrbitAudioNavigator = AudioNavigator<ExoPlayerSettings, ExoPlayerPreferences>

internal data class AudiobookMediaItemSpec(
    val fileId: String,
    val streamUrl: String,
    val mimeType: String,
    val title: String,
    val artist: String?
)

internal fun buildAudiobookMediaItemSpecs(files: List<BookFileOption>): List<AudiobookMediaItemSpec> =
    AudiobookTimeline.playableAudioFiles(files).mapNotNull { file ->
        val fileId = file.fileId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val streamUrl = file.book.streamUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val mimeType = file.format?.let(::media3AudioMimeType) ?: return@mapNotNull null
        AudiobookMediaItemSpec(
            fileId = fileId,
            streamUrl = streamUrl,
            mimeType = mimeType,
            title = file.filename ?: file.book.title,
            artist = file.book.author
        )
    }

internal fun buildAudiobookMediaItems(files: List<BookFileOption>): List<MediaItem> =
    buildAudiobookMediaItemSpecs(files).map { spec ->
        MediaItem.Builder()
            .setMediaId(spec.fileId)
            .setUri(spec.streamUrl)
            .setMimeType(spec.mimeType)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(spec.title)
                    .setArtist(spec.artist)
                    .build()
            )
            .build()
    }

internal fun resolveAbsolutePositionMs(
    audioFiles: List<BookFileOption>,
    currentFileId: String?,
    currentFilePositionMs: Long
): Long {
    val playableFiles = AudiobookTimeline.playableAudioFiles(audioFiles)
    val fileId = currentFileId ?: return currentFilePositionMs.coerceAtLeast(0L)
    return AudiobookTimeline.fileRelativeToAbsolute(playableFiles, fileId, currentFilePositionMs)
        ?: currentFilePositionMs.coerceAtLeast(0L)
}

internal fun resolveTotalDurationMs(audioFiles: List<BookFileOption>, fallbackDurationMs: Long): Long {
    val playableFiles = AudiobookTimeline.playableAudioFiles(audioFiles)
    return AudiobookTimeline.totalDurationMs(playableFiles) ?: fallbackDurationMs.coerceAtLeast(0L)
}

internal fun resolveSeekTarget(
    audioFiles: List<BookFileOption>,
    absolutePositionMs: Long
): AudiobookTimelinePosition? {
    val playableFiles = AudiobookTimeline.playableAudioFiles(audioFiles)
    if (playableFiles.size < 2) return null
    return AudiobookTimeline.absoluteToPosition(playableFiles, absolutePositionMs)
}

internal fun activeBookSummaryForActiveFile(
    book: BookSummary,
    audioFiles: List<BookFileOption>,
    currentFileId: String?
): BookSummary {
    val activeFile = AudiobookTimeline.playableAudioFiles(audioFiles)
        .firstOrNull { it.fileId == currentFileId }
        ?: return book
    return book.copy(
        fileId = activeFile.book.fileId,
        streamUrl = activeFile.book.streamUrl,
        downloadUrl = activeFile.book.downloadUrl,
        format = activeFile.book.format,
        localPath = activeFile.book.localPath
    )
}

internal fun absoluteProgressPercent(absolutePositionMs: Long, totalDurationMs: Long): Float? {
    if (totalDurationMs <= 0L) return null
    return ((absolutePositionMs.coerceAtLeast(0L).toDouble() / totalDurationMs.toDouble()) * 100.0)
        .coerceIn(0.0, 100.0)
        .toFloat()
}

@UnstableApi
internal suspend fun openDirectMedia3Audio(
    application: Application,
    book: BookSummary,
    streamUrl: String?,
    initialPositionMs: Long,
    audioFiles: List<BookFileOption> = emptyList(),
    headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    recoverAuthentication: suspend () -> Boolean
): ReadiumAudioOpenResult {
    val playlistItems = buildAudiobookMediaItems(audioFiles)
    if (playlistItems.size >= 2) {
        return preparePlaylistMedia3Audio(
            application = application,
            mediaItems = playlistItems,
            initialFileId = book.fileId,
            initialPositionMs = initialPositionMs,
            headersProvider = headersProvider,
            recoverAuthentication = recoverAuthentication
        )
    }
    val url = streamUrl?.takeIf { it.isNotBlank() }
        ?: return ReadiumAudioOpenResult.Error("The audiobook has no valid BookOrbit stream.")
    val mimeType = book.format?.let(::media3AudioMimeType)
        ?: return ReadiumAudioOpenResult.Error("This audiobook format is not supported yet.")
    return prepareDirectMedia3Audio(
        application = application,
        book = book,
        streamUrl = url,
        mimeType = mimeType,
        initialPositionMs = initialPositionMs,
        headersProvider = headersProvider,
        recoverAuthentication = recoverAuthentication
    )
}

@UnstableApi
private suspend fun preparePlaylistMedia3Audio(
    application: Application,
    mediaItems: List<MediaItem>,
    initialFileId: String?,
    initialPositionMs: Long,
    headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    recoverAuthentication: suspend () -> Boolean
): ReadiumAudioOpenResult = withContext(Dispatchers.Main.immediate) {
    val player = ExoPlayer.Builder(application)
        .setSeekBackIncrementMs(AUDIO_SEEK_BACK_INCREMENT_MS)
        .setSeekForwardIncrementMs(AUDIO_SEEK_FORWARD_INCREMENT_MS)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(application).setDataSourceFactory(
                AuthenticatedMedia3HttpDataSourceFactory(
                    headersProvider = headersProvider,
                    recoverAuthentication = recoverAuthentication
                )
            )
        )
        .build()
    val initialIndex = mediaItems.indexOfFirst { it.mediaId == initialFileId }
        .let { if (it >= 0) it else 0 }
    player.setMediaItems(mediaItems, initialIndex, initialPositionMs.coerceAtLeast(0L))
    player.prepare()

    try {
        val playbackError = awaitMedia3Ready(player)
        if (playbackError == null) {
            ReadiumAudioOpenResult.Opened(AudioPlaybackEngine.DirectMedia3(player))
        } else {
            player.release()
            ReadiumAudioOpenResult.Error(
                "Media3 could not open the audiobook stream from BookOrbit " +
                    "(${playbackError.errorCodeName})."
            )
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        player.release()
        throw cancelled
    } catch (error: Throwable) {
        player.release()
        throw error
    }
}

@UnstableApi
private suspend fun prepareDirectMedia3Audio(
    application: Application,
    book: BookSummary,
    streamUrl: String,
    mimeType: String,
    initialPositionMs: Long,
    headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    recoverAuthentication: suspend () -> Boolean
): ReadiumAudioOpenResult = withContext(Dispatchers.Main.immediate) {
    val player = ExoPlayer.Builder(application)
        .setSeekBackIncrementMs(AUDIO_SEEK_BACK_INCREMENT_MS)
        .setSeekForwardIncrementMs(AUDIO_SEEK_FORWARD_INCREMENT_MS)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(application).setDataSourceFactory(
                AuthenticatedMedia3HttpDataSourceFactory(
                    headersProvider = headersProvider,
                    recoverAuthentication = recoverAuthentication
                )
            )
        )
        .build()
    val mediaItem = MediaItem.Builder()
        .setUri(streamUrl)
        .setMimeType(mimeType)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(book.title)
                .setArtist(book.author)
                .build()
        )
        .build()
    player.setMediaItem(mediaItem)
    player.seekTo(initialPositionMs.coerceAtLeast(0L))
    player.prepare()

    try {
        val playbackError = awaitMedia3Ready(player)
        if (playbackError == null) {
            ReadiumAudioOpenResult.Opened(AudioPlaybackEngine.DirectMedia3(player))
        } else {
            player.release()
            ReadiumAudioOpenResult.Error(
                "Media3 could not open the audiobook stream from BookOrbit " +
                    "(${playbackError.errorCodeName})."
            )
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        player.release()
        throw cancelled
    } catch (error: Throwable) {
        player.release()
        throw error
    }
}

private suspend fun awaitMedia3Ready(player: Player): PlaybackException? {
    player.playerError?.let { return it }
    if (player.playbackState == Player.STATE_READY) return null
    return suspendCancellableCoroutine { continuation ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && continuation.isActive) {
                    player.removeListener(this)
                    continuation.resume(null)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (continuation.isActive) {
                    player.removeListener(this)
                    continuation.resume(error)
                }
            }
        }
        player.addListener(listener)
        player.playerError?.let { error ->
            if (continuation.isActive) {
                player.removeListener(listener)
                continuation.resume(error)
            }
        }
        if (player.playbackState == Player.STATE_READY && continuation.isActive) {
            player.removeListener(listener)
            continuation.resume(null)
        }
        continuation.invokeOnCancellation {
            player.removeListener(listener)
        }
    }
}

internal data class AudioBookDetailRequest(
    val sequence: Long,
    val book: BookSummary
)

internal enum class AudioPlaybackPreparationState {
    IDLE,
    PREPARING
}

internal data class AudioPreparingSession(
    val book: BookSummary,
    val launchMode: ReaderLaunchMode
)

internal fun readiumAudioMediaType(filenameOrFormat: String): MediaType? {
    val normalized = filenameOrFormat.trim().lowercase()
    val extension = normalized.substringAfterLast('.').substringAfterLast('/')
    return when (extension) {
        "m4a", "m4b", "mp4" -> MediaType.MP4
        "mp3", "mpeg" -> MediaType.MP3
        "aac" -> MediaType.AAC
        "aif", "aiff" -> MediaType.AIFF
        "flac" -> MediaType.FLAC
        "ogg", "oga" -> MediaType.OGG
        "opus" -> MediaType.OPUS
        "wav" -> MediaType.WAV
        "webm" -> MediaType.WEBM_AUDIO
        "x-m4b" -> MediaType.MP4
        else -> null
    }
}

internal fun media3AudioMimeType(filenameOrFormat: String): String? {
    val normalized = filenameOrFormat.trim().lowercase()
    val extension = normalized.substringAfterLast('.').substringAfterLast('/')
    return when (extension) {
        "m4a", "m4b", "mp4", "x-m4b" -> MimeTypes.AUDIO_MP4
        "mp3", "mpeg" -> MimeTypes.AUDIO_MPEG
        "aac" -> MimeTypes.AUDIO_AAC
        "aif", "aiff" -> "audio/aiff"
        "flac" -> MimeTypes.AUDIO_FLAC
        "ogg", "oga" -> MimeTypes.AUDIO_OGG
        "opus" -> MimeTypes.AUDIO_OPUS
        "wav" -> MimeTypes.AUDIO_WAV
        "webm" -> MimeTypes.AUDIO_WEBM
        else -> null
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal sealed interface AudioPlaybackEngine {
    val player: Player
    fun close()

    data class Readium(
        val publication: Publication,
        val navigator: BookOrbitAudioNavigator,
        override val player: Player
    ) : AudioPlaybackEngine {
        override fun close() {
            navigator.close()
            publication.close()
        }
    }

    data class DirectMedia3(
        override val player: ExoPlayer
    ) : AudioPlaybackEngine {
        override fun close() {
            player.release()
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal sealed interface ReadiumAudioOpenResult {
    data class Opened(
        val engine: AudioPlaybackEngine
    ) : ReadiumAudioOpenResult

    data class Error(val message: String) : ReadiumAudioOpenResult
}

@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class)
internal suspend fun openReadiumAudio(
    application: Application,
    book: BookSummary,
    file: File? = null,
    initialPositionMs: Long,
    httpClient: HttpClient = DefaultHttpClient()
): ReadiumAudioOpenResult = withContext(Dispatchers.IO) {
    val localFile = file?.takeIf { it.isFile && it.length() > 0L }
    if (localFile == null) {
        return@withContext ReadiumAudioOpenResult.Error(
            "The downloaded audiobook file is unavailable."
        )
    }
    val mediaType = book.format?.let(::readiumAudioMediaType)
        ?: localFile.name.let(::readiumAudioMediaType)
        ?: return@withContext ReadiumAudioOpenResult.Error("This audiobook format is not supported yet.")
    val assetRetriever = AssetRetriever(application.contentResolver, httpClient)
    val asset = assetRetriever.retrieve(localFile, mediaType).getOrNull()
        ?: return@withContext ReadiumAudioOpenResult.Error(
            "Readium could not read this audiobook file."
        )
    val parser = DefaultPublicationParser(
        context = application,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )
    val publication = PublicationOpener(parser).open(
        asset = asset,
        allowUserInteraction = false
    ).getOrNull()
        ?: return@withContext ReadiumAudioOpenResult.Error("Readium could not open this audiobook publication.")
    if (!publication.conformsTo(Publication.Profile.AUDIOBOOK)) {
        publication.close()
        return@withContext ReadiumAudioOpenResult.Error("Readium did not recognize this file as an audiobook.")
    }

    // Readium's ExoPlayer adapter creates a main-looper Player. Media3 requires every
    // subsequent configuration call to run on that same thread.
    try {
        withContext(Dispatchers.Main.immediate) main@{
            val engineProvider = ExoPlayerEngineProvider(
                application = application,
                metadataProvider = DefaultMediaMetadataProvider(
                    title = book.title,
                    author = book.author
                ),
                configuration = audiobookReadiumEngineConfiguration()
            )
            val factory = AudioNavigatorFactory(publication, engineProvider)
                ?: run {
                    publication.close()
                    return@main ReadiumAudioOpenResult.Error("Readium could not prepare this audiobook.")
                }
            val initialLocator = publication.readingOrder.firstOrNull()
                ?.let(publication::locatorFromLink)
                ?.copyWithLocations(fragments = listOf("t=${initialPositionMs.coerceAtLeast(0L).milliseconds.inWholeSeconds}"))
            val navigator = factory.createNavigator(
                initialLocator = initialLocator,
                initialPreferences = ExoPlayerPreferences()
            ).getOrNull()
                ?: run {
                    publication.close()
                    return@main ReadiumAudioOpenResult.Error("Readium could not initialize audiobook playback.")
                }
            ReadiumAudioOpenResult.Opened(
                AudioPlaybackEngine.Readium(
                    publication = publication,
                    navigator = navigator,
                    player = navigator.asMedia3Player()
                )
            )
        }
    } catch (error: Throwable) {
        withContext(NonCancellable) {
            runCatching {
                publication.close()
            }
        }
        throw error
    }
}

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReadiumAudioPlaybackService : MediaSessionService() {
    internal data class Session(
        val book: BookSummary,
        val launchMode: ReaderLaunchMode,
        val engine: AudioPlaybackEngine,
        val mediaSession: MediaSession,
        val audioFiles: List<BookFileOption> = emptyList()
    ) {
        val player: Player
            get() = engine.player

        fun currentFileId(): String? =
            player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: book.fileId

        fun activeBookSummary(): BookSummary =
            activeBookSummaryForActiveFile(book, audioFiles, currentFileId())

        fun currentFilePositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

        fun totalDurationMs(): Long = resolveTotalDurationMs(audioFiles, player.duration)

        fun absolutePositionMs(): Long =
            resolveAbsolutePositionMs(audioFiles, currentFileId(), currentFilePositionMs())

        fun seekToAbsolutePosition(ms: Long) {
            val target = resolveSeekTarget(audioFiles, ms)
            if (target == null) {
                player.seekTo(ms.coerceAtLeast(0L))
            } else {
                player.seekTo(target.fileIndex, target.positionMs)
            }
        }
    }

    private val mediaSessionCallback = AudiobookMediaSessionCallback()

    internal inner class Binder : android.os.Binder() {
        private val mutableSession = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = mutableSession.asStateFlow()

        fun openSession(
            book: BookSummary,
            launchMode: ReaderLaunchMode,
            engine: AudioPlaybackEngine,
            audioFiles: List<BookFileOption> = emptyList()
        ): Session {
            closeSession()
            var mediaSession: MediaSession? = null
            try {
                val createdSession = MediaSession.Builder(applicationContext, engine.player)
                    .setId("${book.libraryId}:${book.id}:${book.fileId.orEmpty()}")
                    .setSessionActivity(createSessionActivityIntent())
                    .setCallback(mediaSessionCallback)
                    .setCustomLayout(audiobookMediaButtonPreferences())
                    .build()
                mediaSession = createdSession
                addSession(createdSession)
                return Session(book, launchMode, engine, createdSession, audioFiles).also { session ->
                    mutableSession.value = session
                }
            } catch (error: Throwable) {
                mediaSession?.release()
                mutableSession.value = null
                throw error
            }
        }

        fun closeSession() {
            mutableSession.value?.let { current ->
                current.mediaSession.release()
                current.engine.close()
                mutableSession.value = null
            }
        }

        fun promotePreviewSession(): Session? {
            val current = mutableSession.value ?: return null
            if (current.launchMode != ReaderLaunchMode.PREVIEW) return current
            return current.copy(launchMode = ReaderLaunchMode.NORMAL).also { promoted ->
                mutableSession.value = promoted
            }
        }

        fun stop() {
            closeSession()
            ServiceCompat.stopForeground(
                this@ReadiumAudioPlaybackService,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            this@ReadiumAudioPlaybackService.stopSelf()
        }

        private fun createSessionActivityIntent(): PendingIntent {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this@ReadiumAudioPlaybackService, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getActivity(applicationContext, 0, intent, flags)
        }
    }

    private val binder by lazy { Binder() }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(AudiobookMediaNotificationProvider(this))
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
            binder
        } else {
            super.onBind(intent)
        }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        binder.session.value?.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Playback intentionally survives removal of the app task.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        binder.closeSession()
        super.onDestroy()
    }

    companion object {
        internal const val SERVICE_INTERFACE = "com.vangeaux.lagrange.READIUM_AUDIO_PLAYBACK"

        internal fun start(application: Application) {
            application.startService(serviceIntent(application))
        }

        internal fun stop(application: Application) {
            application.stopService(serviceIntent(application))
        }

        internal suspend fun bind(application: Application): Binder {
            val result = CompletableDeferred<Binder>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    result.complete(service as Binder)
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit

                override fun onNullBinding(name: ComponentName?) {
                    if (!result.isCompleted) {
                        result.completeExceptionally(
                            IllegalStateException("Could not bind the audiobook playback service.")
                        )
                    }
                }
            }
            val bound = application.bindService(
                serviceIntent(application),
                connection,
                Context.BIND_AUTO_CREATE
            )
            if (!bound && !result.isCompleted) {
                result.completeExceptionally(
                    IllegalStateException("Could not start the audiobook playback service binding.")
                )
            }
            return result.await()
        }

        private fun serviceIntent(application: Application): Intent =
            Intent(SERVICE_INTERFACE).setClass(application, ReadiumAudioPlaybackService::class.java)
    }
}

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class ReadiumAudioPlaybackController internal constructor(
    private val application: Application
) {
    private val preferencesStore = AppPreferencesStore(application)
    private val binderDeferred = CompletableDeferred<ReadiumAudioPlaybackService.Binder>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutablePreparationState = MutableStateFlow(AudioPlaybackPreparationState.IDLE)
    internal val preparationState: StateFlow<AudioPlaybackPreparationState> =
        mutablePreparationState.asStateFlow()
    private val mutablePreparingSession = MutableStateFlow<AudioPreparingSession?>(null)
    internal val preparingSession: StateFlow<AudioPreparingSession?> =
        mutablePreparingSession.asStateFlow()
    private var openGeneration = 0L
    private var progressJob: Job? = null
    private var progressListener: ((BookSummary, Long, Float?, ReaderLaunchMode) -> Unit)? = null
    private var sessionHistoryStore: AudiobookSessionHistoryStore? = null
    private var serverUrlProvider: (suspend () -> String?)? = null
    private var readingSessionReporter: ReadingSessionReporter? = null
    private var readingSessionFileId: String? = null
    private var playbackStateListener: Player.Listener? = null
    private var lastRecordedIsPlaying: Boolean? = null
    private var coverLoader: (suspend (BookSummary) -> ByteArray?)? = null
    private var streamingHeadersProvider: suspend (AbsoluteUrl) -> Map<String, String> = {
        emptyMap()
    }
    private var streamingAuthenticationRecovery: suspend () -> Boolean = { false }
    private val mutableBookDetailRequest = MutableStateFlow<AudioBookDetailRequest?>(null)
    internal val bookDetailRequest: StateFlow<AudioBookDetailRequest?> =
        mutableBookDetailRequest.asStateFlow()
    private var bookDetailRequestSequence = 0L

    fun setProgressListener(
        listener: (BookSummary, Long, Float?, ReaderLaunchMode) -> Unit
    ) {
        progressListener = listener
    }

    internal fun setSessionHistoryStore(
        store: AudiobookSessionHistoryStore,
        serverUrlProvider: suspend () -> String?
    ) {
        sessionHistoryStore = store
        this.serverUrlProvider = serverUrlProvider
    }

    fun setCoverLoader(loader: suspend (BookSummary) -> ByteArray?) {
        coverLoader = loader
    }

    internal fun setStreamingAuthentication(
        headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
        recoverAuthentication: suspend () -> Boolean
    ) {
        streamingHeadersProvider = headersProvider
        streamingAuthenticationRecovery = recoverAuthentication
    }

    internal suspend fun loadCover(book: BookSummary): ByteArray? = coverLoader?.invoke(book)

    internal fun requestBookDetail(book: BookSummary) {
        bookDetailRequestSequence += 1L
        mutableBookDetailRequest.value = AudioBookDetailRequest(bookDetailRequestSequence, book)
    }

    internal fun consumeBookDetailRequest(sequence: Long) {
        if (mutableBookDetailRequest.value?.sequence == sequence) {
            mutableBookDetailRequest.value = null
        }
    }

    internal suspend fun session(): StateFlow<ReadiumAudioPlaybackService.Session?> = binder().session

    internal fun pause() {
        scope.launch {
            binder().session.value?.player?.pause()
        }
    }

    internal fun enableReadingProgress() {
        scope.launch {
            val serviceBinder = binder()
            val session = serviceBinder.promotePreviewSession() ?: return@launch
            startProgressUpdates(session, recordInitialPlay = false)
            publishProgress(session)
        }
    }

    internal suspend fun open(
        book: BookSummary,
        file: File? = null,
        streamUrl: String? = null,
        initialPositionMs: Long,
        launchMode: ReaderLaunchMode,
        playWhenReady: Boolean = true,
        audioFiles: List<BookFileOption> = emptyList()
    ): ReadiumAudioOpenResult {
        val generation = withContext(Dispatchers.Main.immediate) {
            openGeneration += 1L
            mutablePreparationState.value = AudioPlaybackPreparationState.PREPARING
            mutablePreparingSession.value = AudioPreparingSession(book, launchMode)
            openGeneration
        }
        val serviceBinder = try {
            ReadiumAudioPlaybackService.start(application)
            withTimeoutOrNull(AUDIO_SERVICE_BIND_TIMEOUT_MILLIS) { binder() }
                ?: run {
                    resetPreparationIfCurrent(generation)
                    return ReadiumAudioOpenResult.Error(
                        "Timed out while connecting to audiobook playback."
                    )
                }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            resetPreparationIfCurrent(generation)
            throw cancelled
        } catch (error: Throwable) {
            resetPreparationIfCurrent(generation)
            throw error
        }
        val matchingSession = serviceBinder.session.value?.takeIf { current ->
            current.book.libraryId == book.libraryId &&
                current.book.id == book.id &&
                current.book.fileId == book.fileId &&
                current.launchMode == launchMode
        }
        if (matchingSession != null) {
            return try {
                withContext(Dispatchers.Main.immediate) {
                    applyPersistedAudioPlaybackSpeed(matchingSession.player)
                    if (playWhenReady) matchingSession.player.play()
                    startProgressUpdates(matchingSession, recordInitialPlay = playWhenReady)
                    mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                    mutablePreparingSession.value = null
                }
                ReadiumAudioOpenResult.Opened(matchingSession.engine)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                resetPreparationIfCurrent(generation)
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "Could not resume audiobook playback.", error)
                resetPreparationIfCurrent(generation)
                ReadiumAudioOpenResult.Error("Could not resume audiobook playback.")
            }
        }
        val opened = try {
            withTimeoutOrNull(AUDIO_ENGINE_PREPARATION_TIMEOUT_MILLIS) {
                if (file?.isFile == true) {
                    openReadiumAudio(
                        application = application,
                        book = book,
                        file = file,
                        initialPositionMs = initialPositionMs,
                        httpClient = DefaultHttpClient()
                    )
                } else {
                    openDirectMedia3Audio(
                        application = application,
                        book = book,
                        streamUrl = streamUrl,
                        initialPositionMs = initialPositionMs,
                        audioFiles = audioFiles,
                        headersProvider = streamingHeadersProvider,
                        recoverAuthentication = streamingAuthenticationRecovery
                    )
                }
            } ?: run {
                resetPreparationIfCurrent(generation)
                return ReadiumAudioOpenResult.Error(
                    "Timed out while preparing the audiobook."
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            resetPreparationIfCurrent(generation)
            throw cancelled
        } catch (error: Throwable) {
            resetPreparationIfCurrent(generation)
            throw error
        }
        return when (opened) {
            is ReadiumAudioOpenResult.Error -> {
                withContext(Dispatchers.Main.immediate) {
                    if (generation == openGeneration) {
                        mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                        mutablePreparingSession.value = null
                    }
                }
                opened
            }
            is ReadiumAudioOpenResult.Opened -> {
                try {
                    withContext(Dispatchers.Main.immediate) {
                        if (generation != openGeneration) {
                            opened.engine.close()
                            mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                            mutablePreparingSession.value = null
                            ReadiumAudioOpenResult.Error(AUDIO_OPEN_CANCELLED_MESSAGE)
                        } else {
                            applyPersistedAudioPlaybackSpeed(opened.engine.player)
                            endReadingSession(serviceBinder.session.value)
                            val session = serviceBinder.openSession(book, launchMode, opened.engine, audioFiles)
                            if (playWhenReady) opened.engine.player.play()
                            startProgressUpdates(session, recordInitialPlay = playWhenReady)
                            mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                            mutablePreparingSession.value = null
                            ReadiumAudioOpenResult.Opened(opened.engine)
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    closeOpenedEngineAfterFailure(serviceBinder, opened.engine)
                    resetPreparationIfCurrent(generation)
                    throw cancelled
                } catch (error: Throwable) {
                    Log.e(TAG, "Could not finalize audiobook playback.", error)
                    closeOpenedEngineAfterFailure(serviceBinder, opened.engine)
                    resetPreparationIfCurrent(generation)
                    ReadiumAudioOpenResult.Error("Could not start audiobook playback.")
                }
            }
        }
    }

    internal suspend fun restorePersistedSession(
        state: ReaderState,
        playWhenReady: Boolean
    ): Boolean {
        if (state.book.mediaKind != MediaKind.AUDIO) return false
        return try {
            when (
                open(
                    book = state.book,
                    file = state.localFile?.takeIf { it.isFile },
                    streamUrl = state.streamUrl,
                    initialPositionMs = if (state.launchMode == ReaderLaunchMode.PREVIEW) {
                        0L
                    } else {
                        state.lastKnownPosition
                    },
                    launchMode = state.launchMode,
                    playWhenReady = playWhenReady,
                    audioFiles = state.audioFiles
                )
            ) {
                is ReadiumAudioOpenResult.Opened -> true
                is ReadiumAudioOpenResult.Error -> false
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "Could not restore the persisted audiobook session.", error)
            false
        }
    }

    internal fun openFromSessionHistory(book: BookSummary, positionMs: Long) {
        scope.launch {
            val active = binder().session.value
            if (
                active != null &&
                active.book.id == book.id &&
                active.book.fileId == book.fileId &&
                active.launchMode == ReaderLaunchMode.NORMAL
            ) {
                withContext(Dispatchers.Main.immediate) {
                    active.player.seekTo(positionMs.coerceAtLeast(0L))
                    active.player.play()
                }
                return@launch
            }
            open(
                book = book,
                file = book.localPath?.let(::File)?.takeIf { it.isFile },
                streamUrl = book.streamUrl,
                initialPositionMs = positionMs,
                launchMode = ReaderLaunchMode.NORMAL,
                playWhenReady = true
            )
        }
    }

    internal fun setPlaybackSpeed(player: Player, speed: Float) {
        val normalized = normalizeAudioPlaybackSpeed(speed)
        player.setPlaybackSpeed(normalized)
        preferencesStore.saveAudioPlaybackSpeed(normalized)
    }

    private fun applyPersistedAudioPlaybackSpeed(player: Player) {
        player.setPlaybackSpeed(preferencesStore.readAudioPlaybackSpeed())
    }

    private suspend fun resetPreparationIfCurrent(generation: Long) {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            if (generation == openGeneration) {
                mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                mutablePreparingSession.value = null
            }
        }
    }

    private suspend fun closeOpenedEngineAfterFailure(
        serviceBinder: ReadiumAudioPlaybackService.Binder,
        engine: AudioPlaybackEngine
    ) {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            if (serviceBinder.session.value?.engine === engine) {
                serviceBinder.closeSession()
            } else {
                engine.close()
            }
        }
    }

    suspend fun close() {
        val serviceBinder = binder()
        withContext(Dispatchers.Main.immediate) {
            openGeneration += 1L
            mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
            mutablePreparingSession.value = null
            endReadingSession(serviceBinder.session.value)
            serviceBinder.session.value?.let(::publishProgress)
            progressJob?.cancel()
            progressJob = null
            playbackStateListener?.let { listener ->
                serviceBinder.session.value?.player?.removeListener(listener)
            }
            playbackStateListener = null
            lastRecordedIsPlaying = null
            serviceBinder.stop()
        }
    }

    private fun recordSessionEvent(
        session: ReadiumAudioPlaybackService.Session,
        isPlaying: Boolean
    ) {
        if (session.launchMode == ReaderLaunchMode.PREVIEW) return
        val store = sessionHistoryStore ?: return
        val serverUrlProvider = serverUrlProvider ?: return
        val activeBook = session.activeBookSummary()
        val positionMs = session.currentFilePositionMs()
        scope.launch(Dispatchers.IO) {
            val serverUrl = serverUrlProvider()?.takeIf { it.isNotBlank() } ?: return@launch
            runCatching {
                store.record(
                    serverUrl = serverUrl,
                    book = activeBook,
                    type = if (isPlaying) {
                        AudiobookSessionEventType.PLAY
                    } else {
                        AudiobookSessionEventType.PAUSE
                    },
                    positionMs = positionMs
                )
            }
        }
    }

    private fun startProgressUpdates(
        session: ReadiumAudioPlaybackService.Session,
        recordInitialPlay: Boolean
    ) {
        progressJob?.cancel()
        playbackStateListener?.let { listener -> session.player.removeListener(listener) }
        lastRecordedIsPlaying = session.player.isPlaying
        playbackStateListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (lastRecordedIsPlaying == isPlaying) return
                lastRecordedIsPlaying = isPlaying
                recordSessionEvent(session, isPlaying)
                if (isPlaying) {
                    readingSessionReporter(session)?.resume(progressPercent(session))
                } else {
                    readingSessionReporter(session)?.pause(progressPercent(session))
                }
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                if (session.player.isPlaying) {
                    recordSessionEvent(session, isPlaying = true)
                }
                val reporter = readingSessionReporter(session)
                if (session.player.isPlaying) {
                    reporter?.resume(progressPercent(session))
                }
                publishProgress(session)
            }
        }.also(session.player::addListener)
        if (recordInitialPlay && session.player.isPlaying) {
            recordSessionEvent(session, isPlaying = true)
            readingSessionReporter(session)?.resume(progressPercent(session))
        }
        progressJob = scope.launch {
            while (isActive) {
                if (session.player.isPlaying) {
                    readingSessionReporter(session)?.activity(progressPercent(session))
                }
                publishProgress(session)
                delay(1_500L)
            }
        }
    }

    private fun readingSessionReporter(
        session: ReadiumAudioPlaybackService.Session
    ): ReadingSessionReporter? {
        val activeFileId = session.currentFileId()
        if (session.launchMode == ReaderLaunchMode.PREVIEW || activeFileId.isNullOrBlank()) {
            return null
        }
        if (readingSessionReporter == null || readingSessionFileId != activeFileId) {
            readingSessionReporter?.end(progressPercent(session))
            readingSessionFileId = activeFileId
            readingSessionReporter = ReadingSessionReporter(
                context = application,
                fileId = activeFileId,
                enabled = true
            )
        }
        return readingSessionReporter
    }

    private fun endReadingSession(session: ReadiumAudioPlaybackService.Session?) {
        readingSessionReporter?.end(session?.let(::progressPercent))
        readingSessionReporter = null
        readingSessionFileId = null
    }

    private fun progressPercent(session: ReadiumAudioPlaybackService.Session): Float? =
        absoluteProgressPercent(session.absolutePositionMs(), session.totalDurationMs())

    private fun publishProgress(session: ReadiumAudioPlaybackService.Session) {
        progressListener?.invoke(
            session.activeBookSummary(),
            session.currentFilePositionMs(),
            progressPercent(session),
            session.launchMode
        )
    }

    private suspend fun binder(): ReadiumAudioPlaybackService.Binder {
        if (!binderDeferred.isCompleted) {
            try {
                binderDeferred.complete(ReadiumAudioPlaybackService.bind(application))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                binderDeferred.completeExceptionally(error)
            }
        }
        return binderDeferred.await()
    }

    private companion object {
        const val TAG = "ReadiumAudioPlayback"
    }
}
