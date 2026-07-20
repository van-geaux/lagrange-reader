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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

internal fun readiumAudioMediaType(filename: String): MediaType? =
    when (filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "m4a", "m4b", "mp4" -> MediaType.MP4
        "mp3" -> MediaType.MP3
        "aac" -> MediaType.AAC
        "aif", "aiff" -> MediaType.AIFF
        "flac" -> MediaType.FLAC
        "ogg", "oga" -> MediaType.OGG
        "opus" -> MediaType.OPUS
        "wav" -> MediaType.WAV
        "webm" -> MediaType.WEBM_AUDIO
        else -> null
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
    val mediaType = readiumAudioMediaType(file.name)
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
            return@withContext ReadiumAudioOpenResult.Error("Readium could not prepare this audiobook.")
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
            return@withContext ReadiumAudioOpenResult.Error("Readium could not initialize audiobook playback.")
        }
    ReadiumAudioOpenResult.Opened(publication, navigator)
}

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReadiumAudioPlaybackService : MediaSessionService() {
    internal data class Session(
        val book: BookSummary,
        val publication: Publication,
        val navigator: BookOrbitAudioNavigator,
        val mediaSession: MediaSession
    )

    internal inner class Binder : android.os.Binder() {
        private val mutableSession = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = mutableSession.asStateFlow()

        fun openSession(
            book: BookSummary,
            publication: Publication,
            navigator: BookOrbitAudioNavigator
        ) {
            closeSession()
            val mediaSession = MediaSession.Builder(applicationContext, navigator.asMedia3Player())
                .setId("${book.libraryId}:${book.id}:${book.fileId.orEmpty()}")
                .setSessionActivity(createSessionActivityIntent())
                .build()
            addSession(mediaSession)
            mutableSession.value = Session(book, publication, navigator, mediaSession)
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

    internal suspend fun session(): StateFlow<ReadiumAudioPlaybackService.Session?> = binder().session

    internal suspend fun open(
        book: BookSummary,
        file: File,
        initialPositionMs: Long,
        playWhenReady: Boolean = true
    ): ReadiumAudioOpenResult {
        ReadiumAudioPlaybackService.start(application)
        return when (val opened = openReadiumAudio(application, book, file, initialPositionMs)) {
            is ReadiumAudioOpenResult.Error -> opened
            is ReadiumAudioOpenResult.Opened -> {
                binder().openSession(book, opened.publication, opened.navigator)
                if (playWhenReady) opened.navigator.play()
                opened
            }
        }
    }

    suspend fun close() {
        binder().stop()
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
