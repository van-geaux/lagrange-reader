package com.bookorbit.android

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.readium.adapter.exoplayer.audio.ExoPlayerEngineProvider
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

internal fun audiobookMediaButtonPreferences(): List<CommandButton> = listOf(
    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
        .build(),
    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .build()
)

@OptIn(ExperimentalReadiumApi::class)
internal typealias BookOrbitAudioNavigator = AudioNavigator<ExoPlayerSettings, ExoPlayerPreferences>

@OptIn(ExperimentalReadiumApi::class)
internal fun audioPlaybackPositionMs(navigator: BookOrbitAudioNavigator): Long {
    val playback = navigator.playback.value
    val preceding = navigator.readingOrder.items
        .take(playback.index)
        .mapNotNull { it.duration }
        .sumOf { it.inWholeMilliseconds }
    return preceding + playback.offset.inWholeMilliseconds
}

@UnstableApi
internal suspend fun openDirectMedia3Audio(
    application: Application,
    book: BookSummary,
    streamUrl: String?,
    initialPositionMs: Long,
    headersProvider: suspend (AbsoluteUrl) -> Map<String, String>,
    recoverAuthentication: suspend () -> Boolean
): ReadiumAudioOpenResult {
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
        continuation.invokeOnCancellation {
            player.removeListener(listener)
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal fun audioPlaybackPercent(navigator: BookOrbitAudioNavigator): Float? {
    val duration = navigator.readingOrder.duration?.inWholeMilliseconds?.takeIf { it > 0L }
        ?: return null
    return ((audioPlaybackPositionMs(navigator).toDouble() / duration.toDouble()) * 100.0)
        .coerceIn(0.0, 100.0)
        .toFloat()
}

@OptIn(ExperimentalReadiumApi::class)
internal fun seekAudioTo(navigator: BookOrbitAudioNavigator, absolutePositionMs: Long) {
    val readingOrder = navigator.readingOrder.items
    if (readingOrder.isEmpty()) return

    val totalDurationMs = navigator.readingOrder.duration?.inWholeMilliseconds
    val targetMs = absolutePositionMs
        .coerceAtLeast(0L)
        .let { position -> totalDurationMs?.let { position.coerceAtMost(it) } ?: position }
    var precedingMs = 0L
    var targetIndex = 0
    while (targetIndex < readingOrder.lastIndex) {
        val itemDurationMs = readingOrder[targetIndex].duration?.inWholeMilliseconds ?: 0L
        if (targetMs < precedingMs + itemDurationMs) break
        precedingMs += itemDurationMs
        targetIndex += 1
    }
    navigator.skipTo(targetIndex, (targetMs - precedingMs).coerceAtLeast(0L).milliseconds)
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
    withContext(Dispatchers.Main.immediate) main@{
        val engineProvider = ExoPlayerEngineProvider(
            application = application,
            metadataProvider = DefaultMediaMetadataProvider(
                title = book.title,
                author = book.author
            )
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
}

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReadiumAudioPlaybackService : MediaSessionService() {
    internal data class Session(
        val book: BookSummary,
        val launchMode: ReaderLaunchMode,
        val engine: AudioPlaybackEngine,
        val mediaSession: MediaSession
    ) {
        val player: Player
            get() = engine.player
    }

    internal inner class Binder : android.os.Binder() {
        private val mutableSession = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = mutableSession.asStateFlow()

        fun openSession(
            book: BookSummary,
            launchMode: ReaderLaunchMode,
            engine: AudioPlaybackEngine
        ) {
            closeSession()
            val mediaSession = MediaSession.Builder(applicationContext, engine.player)
                .setId("${book.libraryId}:${book.id}:${book.fileId.orEmpty()}")
                .setSessionActivity(createSessionActivityIntent())
                .setMediaButtonPreferences(audiobookMediaButtonPreferences())
                .build()
            addSession(mediaSession)
            mutableSession.value = Session(book, launchMode, engine, mediaSession)
        }

        fun closeSession() {
            mutableSession.value?.let { current ->
                current.mediaSession.release()
                current.engine.close()
                mutableSession.value = null
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
        internal const val SERVICE_INTERFACE = "com.bookorbit.android.READIUM_AUDIO_PLAYBACK"

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

    internal suspend fun open(
        book: BookSummary,
        file: File? = null,
        streamUrl: String? = null,
        initialPositionMs: Long,
        launchMode: ReaderLaunchMode,
        playWhenReady: Boolean = true
    ): ReadiumAudioOpenResult {
        val generation = withContext(Dispatchers.Main.immediate) {
            openGeneration += 1L
            mutablePreparationState.value = AudioPlaybackPreparationState.PREPARING
            mutablePreparingSession.value = AudioPreparingSession(book, launchMode)
            openGeneration
        }
        val serviceBinder = try {
            ReadiumAudioPlaybackService.start(application)
            binder()
        } catch (error: Throwable) {
            resetPreparationIfCurrent(generation)
            throw error
        }
        serviceBinder.session.value?.takeIf { current ->
            current.book.libraryId == book.libraryId &&
                current.book.id == book.id &&
                current.book.fileId == book.fileId &&
                current.launchMode == launchMode
        }?.let { current ->
            withContext(Dispatchers.Main.immediate) {
                applyPersistedAudioPlaybackSpeed(current.player)
                if (playWhenReady) current.player.play()
                startProgressUpdates(current)
                mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                mutablePreparingSession.value = null
            }
            return ReadiumAudioOpenResult.Opened(current.engine)
        }
        val opened = try {
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
                    headersProvider = streamingHeadersProvider,
                    recoverAuthentication = streamingAuthenticationRecovery
                )
            }
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
                withContext(Dispatchers.Main.immediate) {
                    if (generation != openGeneration) {
                        opened.engine.close()
                        mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                        mutablePreparingSession.value = null
                        ReadiumAudioOpenResult.Error(AUDIO_OPEN_CANCELLED_MESSAGE)
                    } else {
                        applyPersistedAudioPlaybackSpeed(opened.engine.player)
                        serviceBinder.openSession(book, launchMode, opened.engine)
                        if (playWhenReady) opened.engine.player.play()
                        startProgressUpdates(requireNotNull(serviceBinder.session.value))
                        mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                        mutablePreparingSession.value = null
                        ReadiumAudioOpenResult.Opened(opened.engine)
                    }
                }
            }
        }
    }

    internal suspend fun restorePersistedSession(
        state: ReaderState,
        playWhenReady: Boolean
    ): Boolean {
        if (state.book.mediaKind != MediaKind.AUDIO) return false
        return runCatching {
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
                    playWhenReady = playWhenReady
                )
            ) {
                is ReadiumAudioOpenResult.Opened -> true
                is ReadiumAudioOpenResult.Error -> false
            }
        }.getOrDefault(false)
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
        withContext(Dispatchers.Main.immediate) {
            if (generation == openGeneration) {
                mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
                mutablePreparingSession.value = null
            }
        }
    }

    suspend fun close() {
        val serviceBinder = binder()
        withContext(Dispatchers.Main.immediate) {
            openGeneration += 1L
            mutablePreparationState.value = AudioPlaybackPreparationState.IDLE
            mutablePreparingSession.value = null
            serviceBinder.session.value?.let(::publishProgress)
            progressJob?.cancel()
            progressJob = null
            serviceBinder.stop()
        }
    }

    private fun startProgressUpdates(session: ReadiumAudioPlaybackService.Session) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                publishProgress(session)
                delay(1_500L)
            }
        }
    }

    private fun publishProgress(session: ReadiumAudioPlaybackService.Session) {
        progressListener?.invoke(
            session.book,
            session.player.currentPosition.coerceAtLeast(0L),
            session.player.duration
                .takeIf { it > 0L }
                ?.let { duration ->
                    ((session.player.currentPosition.toDouble() / duration.toDouble()) * 100.0)
                        .coerceIn(0.0, 100.0)
                        .toFloat()
                },
            session.launchMode
        )
    }

    private suspend fun binder(): ReadiumAudioPlaybackService.Binder {
        if (!binderDeferred.isCompleted) {
            runCatching { ReadiumAudioPlaybackService.bind(application) }
                .onSuccess(binderDeferred::complete)
                .onFailure(binderDeferred::completeExceptionally)
        }
        return binderDeferred.await()
    }
}
