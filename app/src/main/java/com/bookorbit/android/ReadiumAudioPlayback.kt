package com.bookorbit.android

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
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
import kotlinx.coroutines.withContext
import org.readium.adapter.exoplayer.audio.ExoPlayerEngineProvider
import org.readium.adapter.exoplayer.audio.ExoPlayerPreferences
import org.readium.adapter.exoplayer.audio.ExoPlayerSettings
import org.readium.navigator.media.audio.AudioNavigator
import org.readium.navigator.media.audio.AudioNavigatorFactory
import org.readium.navigator.media.common.DefaultMediaMetadataProvider
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

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

@OptIn(ExperimentalReadiumApi::class)
internal sealed interface ReadiumAudioOpenResult {
    data class Opened(
        val publication: Publication,
        val navigator: BookOrbitAudioNavigator
    ) : ReadiumAudioOpenResult

    data class Error(val message: String) : ReadiumAudioOpenResult
}

@OptIn(ExperimentalReadiumApi::class)
internal suspend fun openReadiumAudio(
    application: Application,
    book: BookSummary,
    file: File,
    initialPositionMs: Long
): ReadiumAudioOpenResult = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        return@withContext ReadiumAudioOpenResult.Error("The audiobook file is unavailable.")
    }
    val mediaType = book.format?.let(::readiumAudioMediaType)
        ?: readiumAudioMediaType(file.name)
        ?: return@withContext ReadiumAudioOpenResult.Error("This audiobook format is not supported yet.")
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(application.contentResolver, httpClient)
    val asset = assetRetriever.retrieve(file, mediaType).getOrNull()
        ?: return@withContext ReadiumAudioOpenResult.Error("Readium could not read this audiobook file.")
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
        ReadiumAudioOpenResult.Opened(publication, navigator)
    }
}

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReadiumAudioPlaybackService : MediaSessionService() {
    internal data class Session(
        val book: BookSummary,
        val launchMode: ReaderLaunchMode,
        val publication: Publication,
        val navigator: BookOrbitAudioNavigator,
        val mediaSession: MediaSession
    )

    internal inner class Binder : android.os.Binder() {
        private val mutableSession = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = mutableSession.asStateFlow()

        fun openSession(
            book: BookSummary,
            launchMode: ReaderLaunchMode,
            publication: Publication,
            navigator: BookOrbitAudioNavigator
        ) {
            closeSession()
            val mediaSession = MediaSession.Builder(applicationContext, navigator.asMedia3Player())
                .setId("${book.libraryId}:${book.id}:${book.fileId.orEmpty()}")
                .setSessionActivity(createSessionActivityIntent())
                .build()
            addSession(mediaSession)
            mutableSession.value = Session(book, launchMode, publication, navigator, mediaSession)
        }

        fun closeSession() {
            mutableSession.value?.let { current ->
                current.mediaSession.release()
                current.navigator.close()
                current.publication.close()
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
            application.bindService(serviceIntent(application), connection, 0)
            return result.await()
        }

        private fun serviceIntent(application: Application): Intent =
            Intent(SERVICE_INTERFACE).setClass(application, ReadiumAudioPlaybackService::class.java)
    }
}

@OptIn(ExperimentalReadiumApi::class)
class ReadiumAudioPlaybackController internal constructor(
    private val application: Application
) {
    private val binderDeferred = CompletableDeferred<ReadiumAudioPlaybackService.Binder>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var progressListener: ((BookSummary, Long, Float?, ReaderLaunchMode) -> Unit)? = null
    private var coverLoader: (suspend (BookSummary) -> ByteArray?)? = null
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
        file: File,
        initialPositionMs: Long,
        launchMode: ReaderLaunchMode,
        playWhenReady: Boolean = true
    ): ReadiumAudioOpenResult {
        ReadiumAudioPlaybackService.start(application)
        val serviceBinder = binder()
        serviceBinder.session.value?.takeIf { current ->
            current.book.libraryId == book.libraryId &&
                current.book.id == book.id &&
                current.book.fileId == book.fileId &&
                current.launchMode == launchMode
        }?.let { current ->
            withContext(Dispatchers.Main.immediate) {
                if (playWhenReady) current.navigator.play()
                startProgressUpdates(current)
            }
            return ReadiumAudioOpenResult.Opened(current.publication, current.navigator)
        }
        return when (val opened = openReadiumAudio(application, book, file, initialPositionMs)) {
            is ReadiumAudioOpenResult.Error -> opened
            is ReadiumAudioOpenResult.Opened -> {
                withContext(Dispatchers.Main.immediate) {
                    serviceBinder.openSession(book, launchMode, opened.publication, opened.navigator)
                    if (playWhenReady) opened.navigator.play()
                    startProgressUpdates(requireNotNull(serviceBinder.session.value))
                }
                opened
            }
        }
    }

    suspend fun close() {
        val serviceBinder = binder()
        withContext(Dispatchers.Main.immediate) {
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
            audioPlaybackPositionMs(session.navigator),
            audioPlaybackPercent(session.navigator),
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
