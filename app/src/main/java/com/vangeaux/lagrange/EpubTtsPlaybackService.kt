package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.*

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Language

internal object EpubTtsAccountSession {
    private val epoch = AtomicLong(0L)

    fun currentEpoch(): Long = epoch.get()

    fun invalidate(): Long = epoch.incrementAndGet()

    fun isCurrent(value: Long): Boolean = epoch.get() == value
}

/** Serializes competing reader starts without relying on foreground-service Intent delivery order. */
internal object EpubTtsRequestSession {
    private val sequence = AtomicLong(0L)
    private val latestRequest = AtomicLong(0L)

    fun begin(): Long {
        val requestId = sequence.incrementAndGet()
        latestRequest.updateAndGet { current -> maxOf(current, requestId) }
        return requestId
    }

    fun cancel(requestId: Long) {
        val cancellationId = sequence.incrementAndGet()
        latestRequest.compareAndSet(requestId, cancellationId)
    }

    fun isCurrent(requestId: Long): Boolean = latestRequest.get() == requestId
}

internal data class EpubTtsSessionSpec(
    val ownerToken: String,
    val requestId: Long,
    val accountEpoch: Long,
    val readerKey: String,
    val libraryId: String,
    val bookId: String?,
    val fileId: String?,
    val filePath: String,
    val title: String,
    val initialLocator: Locator?,
    val selectionText: String? = null,
    val settings: EpubTtsSettings,
    val playWhenReady: Boolean
)

internal enum class EpubTtsFailureKind {
    LANGUAGE_DATA,
    NETWORK,
    GENERIC
}

internal data class EpubTtsServiceState(
    val ownerToken: String? = null,
    val readerKey: String? = null,
    val title: String? = null,
    val locator: Locator? = null,
    val utterance: String? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val settings: EpubTtsSettings = EpubTtsSettings(),
    val voiceLanguageTag: String? = null,
    val voices: List<EpubTtsVoice> = emptyList(),
    val failure: EpubTtsFailureKind? = null,
    val failureSerial: Long = 0L,
    val completedOwnerToken: String? = null,
    val completionSerial: Long = 0L
) {
    val hasSession: Boolean
        get() = readerKey != null && (isPreparing || failure == null)
}

internal data class EpubTtsVoice(
    val id: String,
    val languageTag: String,
    val quality: String,
    val requiresNetwork: Boolean
)

internal fun epubTtsMediaPlaybackState(state: EpubTtsServiceState): Int = when {
    state.failure != null -> PlaybackStateCompat.STATE_ERROR
    state.isPreparing -> PlaybackStateCompat.STATE_BUFFERING
    !state.hasSession -> PlaybackStateCompat.STATE_NONE
    state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
    else -> PlaybackStateCompat.STATE_PAUSED
}

internal fun epubTtsMediaPlaybackActions(state: EpubTtsServiceState): Long {
    if (state.readerKey == null) return 0L
    var actions = PlaybackStateCompat.ACTION_STOP
    if (state.failure == null && !state.isPreparing) {
        actions = actions or PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE
        if (state.canGoPrevious) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        if (state.canGoNext) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
    }
    return actions
}

internal fun epubTtsOwnerMatches(state: EpubTtsServiceState, ownerToken: String?): Boolean =
    ownerToken == null || state.ownerToken == ownerToken

internal fun epubTtsExposedTitle(state: EpubTtsServiceState): String =
    if (state.settings.showBookTitleOnLockScreen) {
        state.title ?: "Text to speech"
    } else {
        "Text to speech"
    }

internal fun epubTtsNotificationDetail(
    state: EpubTtsServiceState,
    isPlaying: Boolean
): String = when {
    state.failure != null -> "Text to speech needs attention"
    state.isPreparing -> "Preparing text to speech"
    !state.utterance.isNullOrBlank() -> state.utterance
    isPlaying -> "Reading aloud"
    else -> "Text to speech paused"
}

/** Owns EPUB speech independently of any Activity so rotation, lock and screen-off are harmless. */
@OptIn(ExperimentalReadiumApi::class)
class EpubTtsPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = PlaybackBinder()
    private var publication: Publication? = null
    private var navigator: AndroidTtsNavigator? = null
    private var openingJob: Job? = null
    private var playbackJob: Job? = null
    private var locationJob: Job? = null
    private val progressJobs = java.util.Collections.synchronizedSet(mutableSetOf<Job>())
    private var activeSpec: EpubTtsSessionSpec? = null
    private var preparedOwnerToken: String? = null
    private var preparedRequestId: Long? = null
    private lateinit var mediaSession: MediaSessionCompat
    private var generation = 0L
    private var lastQueuedAtMillis = 0L
    private var lastQueuedPercent: Float? = null
    private var lastQueuedChapter = -1
    private val playbackWakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:epub-tts"
        ).apply { setReferenceCounted(false) }
    }

    internal inner class PlaybackBinder : Binder() {
        private val mutableState = MutableStateFlow(EpubTtsServiceState())
        val state: StateFlow<EpubTtsServiceState> = mutableState.asStateFlow()

        fun open(spec: EpubTtsSessionSpec): Boolean {
            if (!isSpecCurrent(spec)) return false
            openSession(spec)
            return true
        }

        fun play(ownerToken: String? = null) {
            if (!isOwner(ownerToken)) return
            val active = navigator ?: return
            active.play()
            if (navigator !== active) return
            publish { it.copy(isPlaying = true) }
            updatePlaybackWakeLock(isPlaying = true)
            updateNotification(isPlayingOverride = true)
        }

        fun pause(ownerToken: String? = null) {
            if (!isOwner(ownerToken)) return
            val active = navigator ?: return
            active.pause()
            if (navigator !== active) return
            publish { it.copy(isPlaying = false) }
            updatePlaybackWakeLock(isPlaying = false)
            updateNotification(isPlayingOverride = false)
        }

        fun previous(ownerToken: String? = null) {
            if (!isOwner(ownerToken)) return
            val active = navigator ?: return
            val wasPlaying = mutableState.value.isPlaying
            active.skipToPreviousUtterance()
            if (navigator !== active) return
            if (wasPlaying) active.play() else active.pause()
            publishNavigatorCapabilities()
            updateNotification()
        }

        fun next(ownerToken: String? = null) {
            if (!isOwner(ownerToken)) return
            val active = navigator ?: return
            val wasPlaying = mutableState.value.isPlaying
            active.skipToNextUtterance()
            if (navigator !== active) return
            if (wasPlaying) active.play() else active.pause()
            publishNavigatorCapabilities()
            updateNotification()
        }

        fun setSettings(value: EpubTtsSettings, ownerToken: String? = null) {
            if (!isOwner(ownerToken)) return
            val normalized = value.normalized()
            val previous = mutableState.value.settings
            mutableState.value = mutableState.value.copy(settings = normalized)
            activeSpec = activeSpec?.copy(settings = normalized)
            navigator?.submitPreferences(
                AndroidTtsPreferences(
                    pitch = normalized.pitch.toDouble(),
                    speed = normalized.speed.toDouble(),
                    voices = normalized.voiceIds.mapKeys { (language, _) -> Language(language) }
                        .mapValues { (_, voiceId) -> AndroidTtsEngine.Voice.Id(voiceId) }
                )
            )
            if (previous.speed == normalized.speed &&
                previous.pitch == normalized.pitch &&
                previous.pauses != normalized.pauses
            ) {
                navigator?.let { active -> active.go(active.location.value.utteranceLocator) }
            }
            updateNotification()
        }

        fun stop(ownerToken: String? = null, requestId: Long? = null) {
            if (!isOwner(ownerToken)) return
            if (requestId != null && currentRequestId() != requestId) return
            currentRequestId()?.let(EpubTtsRequestSession::cancel)
            closeSession(removeNotification = true)
        }

        private fun isOwner(ownerToken: String?): Boolean =
            ownerToken == null || mutableState.value.ownerToken == ownerToken ||
                (mutableState.value.ownerToken == null && preparedOwnerToken == ownerToken)

        private fun currentRequestId(): Long? = preparedRequestId ?: activeSpec?.requestId

        internal fun publish(transform: (EpubTtsServiceState) -> EpubTtsServiceState) {
            mutableState.value = transform(mutableState.value)
            updateMediaSession(mutableState.value)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        mediaSession = MediaSessionCompat(this, "$packageName:epub-tts").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(appLaunchIntent())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = binder.play()

                override fun onPause() = binder.pause()

                override fun onSkipToPrevious() = binder.previous()

                override fun onSkipToNext() = binder.next()

                override fun onStop() = binder.stop()

                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    if (action == ACTION_STOP) binder.stop()
                }
            })
        }
        updateMediaSession(binder.state.value)
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_PREPARE -> prepareForeground(
                ownerToken = intent.getStringExtra(EXTRA_OWNER_TOKEN),
                requestId = intent.getLongExtra(EXTRA_REQUEST_ID, INVALID_REQUEST_ID),
                accountEpoch = intent.getLongExtra(EXTRA_ACCOUNT_EPOCH, INVALID_ACCOUNT_EPOCH),
                readerKey = intent.getStringExtra(EXTRA_READER_KEY),
                title = intent.getStringExtra(EXTRA_TITLE),
                startId = startId
            )
            ACTION_PLAY -> binder.play()
            ACTION_PAUSE -> binder.pause()
            ACTION_PREVIOUS -> binder.previous()
            ACTION_NEXT -> binder.next()
            ACTION_STOP -> binder.stop()
            ACTION_STOP_OWNER -> binder.stop(intent.getStringExtra(EXTRA_OWNER_TOKEN))
            else -> promoteToForeground("Preparing text to speech", isPlaying = false)
        }
        if (action != null && action in TRANSPORT_ACTIONS && binder.state.value.readerKey == null) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        closeSession(removeNotification = true)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        closeSession(removeNotification = true, stopService = false)
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        scope.cancel()
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private fun prepareForeground(
        ownerToken: String?,
        requestId: Long,
        accountEpoch: Long,
        readerKey: String?,
        title: String?,
        startId: Int
    ) {
        if (ownerToken.isNullOrBlank() || readerKey.isNullOrBlank() ||
            !EpubTtsRequestSession.isCurrent(requestId) ||
            !EpubTtsAccountSession.isCurrent(accountEpoch)
        ) {
            if (binder.state.value.readerKey != null) {
                updateNotification()
            } else {
                promoteToForeground("Preparing text to speech", isPlaying = false)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return
        }
        val state = binder.state.value
        if (state.ownerToken != ownerToken || state.readerKey != readerKey) {
            closeSession(removeNotification = false, stopService = false)
            preparedOwnerToken = ownerToken
            preparedRequestId = requestId
            binder.publish {
                EpubTtsServiceState(
                    ownerToken = ownerToken,
                    readerKey = readerKey,
                    title = title,
                    isPreparing = true,
                    settings = AppPreferencesStore(applicationContext).readEpubTtsSettings(),
                    failureSerial = it.failureSerial,
                    completionSerial = it.completionSerial
                )
            }
        } else {
            preparedOwnerToken = ownerToken
            preparedRequestId = requestId
        }
        promoteToForeground(title ?: "Text to speech", isPlaying = false)
    }

    private fun openSession(spec: EpubTtsSessionSpec) {
        if (!isSpecCurrent(spec)) return
        val current = activeSpec
        if (current?.requestId == spec.requestId && current.ownerToken == spec.ownerToken &&
            current.readerKey == spec.readerKey && navigator != null
        ) {
            activeSpec = spec
            binder.setSettings(spec.settings)
            spec.initialLocator?.let { navigator?.go(it) }
            if (spec.playWhenReady) binder.play() else binder.pause()
            return
        }
        closeSession(removeNotification = false, stopService = false)
        activeSpec = spec
        val requestGeneration = ++generation
        binder.publish {
            EpubTtsServiceState(
                ownerToken = spec.ownerToken,
                readerKey = spec.readerKey,
                title = spec.title,
                locator = spec.initialLocator,
                isPreparing = true,
                settings = spec.settings.normalized(),
                failureSerial = it.failureSerial,
                completionSerial = it.completionSerial
            )
        }
        promoteToForeground(spec.title, isPlaying = false)
        openingJob = scope.launch {
            val opened = when (val result = openReadiumEpub(this@EpubTtsPlaybackService, File(spec.filePath))) {
                is ReadiumEpubOpenResult.Opened -> result.publication
                is ReadiumEpubOpenResult.Error -> {
                    failSession(requestGeneration, EpubTtsFailureKind.GENERIC)
                    return@launch
                }
            }
            if (!isSessionCurrent(spec, requestGeneration)) {
                opened.close()
                closeStaleSession(spec)
                return@launch
            }
            val factory = TtsNavigatorFactory(
                application = application as Application,
                publication = opened,
                ttsEngineProvider = PunctuationPausingTtsEngineProvider(
                    applicationContext
                ) { binder.state.value.settings.pauses }
            )
            if (factory == null) {
                opened.close()
                failSession(requestGeneration, EpubTtsFailureKind.GENERIC)
                return@launch
            }
            val result = factory.createNavigator(
                listener = object : TtsNavigator.Listener {
                    override fun onStopRequested() {
                        closeSession(removeNotification = true)
                    }
                },
                initialLocator = spec.initialLocator,
                initialPreferences = AndroidTtsPreferences(
                    pitch = spec.settings.normalized().pitch.toDouble(),
                    speed = spec.settings.normalized().speed.toDouble(),
                    voices = spec.settings.normalized().voiceIds
                        .mapKeys { (language, _) -> Language(language) }
                        .mapValues { (_, voiceId) -> AndroidTtsEngine.Voice.Id(voiceId) }
                )
            )
            val created = result.getOrNull()
            if (created == null || !isSessionCurrent(spec, requestGeneration)) {
                created?.close()
                opened.close()
                if (created == null && isSessionCurrent(spec, requestGeneration)) {
                    failSession(requestGeneration, EpubTtsFailureKind.GENERIC)
                } else {
                    closeStaleSession(spec)
                }
                return@launch
            }
            seekTtsToSelection(created, spec.initialLocator, spec.selectionText)
            publication = opened
            navigator = created
            binder.publish {
                it.copy(
                    isPreparing = false,
                    settings = spec.settings.normalized(),
                    failure = null
                )
            }
            observe(created, opened, spec, requestGeneration)
            publishNavigatorCapabilities()
            if (spec.playWhenReady) binder.play(spec.ownerToken)
            updateNotification()
        }
    }

    private suspend fun seekTtsToSelection(
        active: AndroidTtsNavigator,
        initialLocator: Locator?,
        selectionText: String?
    ) {
        val target = selectionText?.normaliseTtsText() ?: return
        val targetSelector = initialLocator?.locations?.get("cssSelector") as? String
        repeat(100) {
            val location = active.location.value
            val currentSelector = location.utteranceLocator.locations["cssSelector"] as? String
            if (targetSelector != null && currentSelector != targetSelector) return
            if (location.utterance.normaliseTtsText().contains(target)) return
            if (!active.hasNextUtterance()) return
            active.skipToNextUtterance()
            delay(10)
        }
    }

    private fun String.normaliseTtsText(): String =
        lowercase().replace(Regex("\\s+"), " ").trim()

    private fun observe(
        active: AndroidTtsNavigator,
        openedPublication: Publication,
        spec: EpubTtsSessionSpec,
        requestGeneration: Long
    ) {
        playbackJob?.cancel()
        locationJob?.cancel()
        playbackJob = scope.launch {
            active.playback.collect { playback ->
                if (navigator !== active || generation != requestGeneration) return@collect
                if (!isSpecCurrent(spec)) {
                    closeStaleSession(spec)
                    return@collect
                }
                val state = playback.state as? TtsNavigator.State
                when (state) {
                    is TtsNavigator.State.Failure -> {
                        val engineError = (state.error as? TtsNavigator.Error.EngineError<*>)?.cause
                        val kind = when (engineError) {
                            is AndroidTtsEngine.Error.LanguageMissingData -> EpubTtsFailureKind.LANGUAGE_DATA
                            is AndroidTtsEngine.Error.Network,
                            is AndroidTtsEngine.Error.NetworkTimeout -> EpubTtsFailureKind.NETWORK
                            else -> EpubTtsFailureKind.GENERIC
                        }
                        failSession(requestGeneration, kind)
                    }
                    TtsNavigator.State.Ended -> {
                        ReadiumEpubTtsPositionStore(applicationContext).remove(spec.readerKey)
                        closeSession(
                            removeNotification = true,
                            completedOwnerToken = spec.ownerToken
                        )
                    }
                    TtsNavigator.State.Ready, null -> {
                        binder.publish { it.copy(isPlaying = playback.playWhenReady) }
                        updatePlaybackWakeLock(playback.playWhenReady)
                        publishNavigatorCapabilities()
                        updateNotification()
                    }
                }
            }
        }
        locationJob = scope.launch {
            active.location
                .distinctUntilChanged { previous, current ->
                    previous.utteranceLocator == current.utteranceLocator &&
                        previous.utterance == current.utterance
                }
                .collect { location ->
                    val locator = location.utteranceLocator
                    if (navigator !== active || generation != requestGeneration) return@collect
                    if (!isSpecCurrent(spec)) {
                        closeStaleSession(spec)
                        return@collect
                    }
                    ReadiumEpubTtsPositionStore(applicationContext).save(spec.readerKey, locator)
                    ReadiumEpubLocatorStore(applicationContext).save(spec.readerKey, locator)
                    if (binder.state.value.isPlaying) updatePlaybackWakeLock(isPlaying = true)
                    binder.publish {
                        it.copy(locator = locator, utterance = location.utterance)
                    }
                    publishNavigatorCapabilities()
                    queueServerProgress(spec, openedPublication, locator)
                }
        }
    }

    private fun queueServerProgress(
        spec: EpubTtsSessionSpec,
        openedPublication: Publication,
        locator: Locator
    ) {
        if (!isSpecCurrent(spec)) return
        val currentBookId = spec.bookId?.takeIf { it.isNotBlank() } ?: return
        val currentFileId = spec.fileId?.takeIf { it.isNotBlank() } ?: return
        val chapter = openedPublication.readingOrder.indexOfFirst { link ->
            link.url().isEquivalent(locator.href.removeFragment())
        }
        if (chapter < 0) return
        val percent = readiumOverallPercent(
            totalProgression = locator.locations.totalProgression,
            resourceProgression = locator.locations.progression,
            chapterIndex = chapter,
            chapterCount = openedPublication.readingOrder.size
        )
        val now = System.currentTimeMillis()
        if (!shouldQueueEpubTtsProgress(
                lastQueuedAtMillis = lastQueuedAtMillis,
                lastQueuedPercent = lastQueuedPercent,
                lastQueuedChapter = lastQueuedChapter,
                nowMillis = now,
                percent = percent,
                chapter = chapter
            )
        ) return
        lastQueuedAtMillis = now
        lastQueuedPercent = percent
        lastQueuedChapter = chapter
        val job = scope.launch(Dispatchers.IO) {
            if (!isSpecCurrent(spec)) return@launch
            runCatching {
                resolveActiveProviderReadingProgressModule(applicationContext).queueProgress(
                    book = BookSummary(
                        libraryId = spec.libraryId,
                        id = currentBookId,
                        fileId = currentFileId,
                        title = spec.title,
                        format = "epub",
                        mediaKind = MediaKind.EPUB,
                        localPath = spec.filePath,
                        readerLocatorJson = locator.toJSON().toString()
                    ),
                    position = 0L,
                    pageIndex = chapter,
                    progressPercent = percent
                )
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.w("EpubTts", "Komga progress synchronization failed", error)
                }
            }
        }
        progressJobs += job
        job.invokeOnCompletion { progressJobs -= job }
    }

    private fun failSession(requestGeneration: Long, kind: EpubTtsFailureKind) {
        if (generation != requestGeneration) return
        openingJob = null
        playbackJob?.cancel()
        playbackJob = null
        locationJob?.cancel()
        locationJob = null
        val failedNavigator = navigator
        navigator = null
        failedNavigator?.close()
        updatePlaybackWakeLock(isPlaying = false)
        publication?.close()
        publication = null
        binder.publish {
            it.copy(
                isPreparing = false,
                isPlaying = false,
                canGoPrevious = false,
                canGoNext = false,
                failure = kind,
                failureSerial = it.failureSerial + 1
            )
        }
        updateNotification()
    }

    private fun publishNavigatorCapabilities() {
        val active = navigator
        val language = active?.settings?.value?.language?.removeRegion()
        val voices = active?.voices.orEmpty()
            .filter { voice -> voice.language.removeRegion() == language }
            .map { voice ->
                EpubTtsVoice(
                    id = voice.id.value,
                    languageTag = voice.language.code,
                    quality = voice.quality.name,
                    requiresNetwork = voice.requiresNetwork
                )
            }
            .distinctBy(EpubTtsVoice::id)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.id })
        binder.publish {
            it.copy(
                canGoPrevious = active?.hasPreviousUtterance() == true,
                canGoNext = active?.hasNextUtterance() == true,
                voiceLanguageTag = language?.code,
                voices = voices
            )
        }
    }

    private fun isSpecCurrent(spec: EpubTtsSessionSpec): Boolean =
        EpubTtsAccountSession.isCurrent(spec.accountEpoch) &&
            EpubTtsRequestSession.isCurrent(spec.requestId)

    private fun isSessionCurrent(spec: EpubTtsSessionSpec, requestGeneration: Long): Boolean =
        generation == requestGeneration && activeSpec?.requestId == spec.requestId &&
            isSpecCurrent(spec)

    private fun closeStaleSession(spec: EpubTtsSessionSpec) {
        if (activeSpec?.requestId == spec.requestId || preparedRequestId == spec.requestId) {
            closeSession(removeNotification = true)
        }
    }

    private fun closeSession(
        removeNotification: Boolean,
        stopService: Boolean = true,
        completedOwnerToken: String? = null
    ) {
        generation += 1
        openingJob?.cancel()
        openingJob = null
        playbackJob?.cancel()
        playbackJob = null
        locationJob?.cancel()
        locationJob = null
        val pendingProgress = synchronized(progressJobs) { progressJobs.toList() }
        pendingProgress.forEach(Job::cancel)
        val closingNavigator = navigator
        navigator = null
        closingNavigator?.close()
        updatePlaybackWakeLock(isPlaying = false)
        publication?.close()
        publication = null
        activeSpec = null
        preparedOwnerToken = null
        preparedRequestId = null
        lastQueuedAtMillis = 0L
        lastQueuedPercent = null
        lastQueuedChapter = -1
        binder.publish {
            EpubTtsServiceState(
                failureSerial = it.failureSerial,
                completedOwnerToken = completedOwnerToken,
                completionSerial = if (completedOwnerToken != null) {
                    it.completionSerial + 1
                } else {
                    it.completionSerial
                }
            )
        }
        if (removeNotification) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

        }
        if (stopService) stopSelf()
    }

    private fun updateNotification(isPlayingOverride: Boolean? = null) {
        val state = binder.state.value
        updateMediaSession(state)
        val isPlaying = isPlayingOverride
            ?: navigator?.playback?.value?.playWhenReady
            ?: state.isPlaying
        promoteToForeground(state.title ?: "Text to speech", isPlaying)
    }

    private fun updateMediaSession(state: EpubTtsServiceState) {
        if (!::mediaSession.isInitialized) return
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(epubTtsMediaPlaybackActions(state))
            .setState(
                epubTtsMediaPlaybackState(state),
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                if (state.isPlaying) state.settings.speed else 0f
            )
            .apply {
                if (state.failure != null) {
                    setErrorMessage("Text to speech needs attention")
                }
                if (state.readerKey != null) {
                    addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            ACTION_STOP,
                            "Close text to speech",
                            android.R.drawable.ic_menu_close_clear_cancel
                        ).build()
                    )
                }
            }
            .build()
        mediaSession.setPlaybackState(playbackState)
        val exposedTitle = epubTtsExposedTitle(state)
        mediaSession.setMetadata(
            state.readerKey?.let {
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        exposedTitle
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "EPUB text to speech")
                    .build()
            }
        )
        mediaSession.isActive = state.readerKey != null
    }

    private fun updatePlaybackWakeLock(isPlaying: Boolean) {
        if (!isPlaying) {
            if (playbackWakeLock.isHeld) playbackWakeLock.release()
            return
        }
        // Refresh a bounded lease at each utterance. This survives device sleep without leaving
        // an indefinite wake lock behind if an engine or OEM service fails without a callback.
        if (playbackWakeLock.isHeld) playbackWakeLock.release()
        playbackWakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
    }

    private fun promoteToForeground(title: String, isPlaying: Boolean) {
        val state = binder.state.value
        val exposeTitle = state.settings.showBookTitleOnLockScreen
        val notificationTitle = if (exposeTitle) title else epubTtsExposedTitle(state)
        val stopIntent = serviceAction(ACTION_STOP, 4)
        val active = state.failure == null && !state.isPreparing && state.readerKey != null
        val actions = EpubNarrationNotificationActions(
            previous = serviceAction(ACTION_PREVIOUS, 1).takeIf { active && state.canGoPrevious },
            playPause = serviceAction(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 2),
            next = serviceAction(ACTION_NEXT, 3).takeIf { active && state.canGoNext },
            close = stopIntent
        )
        val notification = buildEpubNarrationNotification(
            context = this,
            title = notificationTitle,
            detail = epubTtsNotificationDetail(state, isPlaying),
            isPlaying = isPlaying && active,
            contentIntent = appLaunchIntent(),
            actions = actions,
            visibility = if (exposeTitle) {
                NotificationCompat.VISIBILITY_PUBLIC
            } else {
                NotificationCompat.VISIBILITY_PRIVATE
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                EPUB_NARRATION_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(EPUB_NARRATION_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                EPUB_NARRATION_NOTIFICATION_CHANNEL_ID,
                "EPUB narration",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps book narration playing while the screen is off"
                setShowBadge(false)
            }
        )
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, EpubTtsPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun appLaunchIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {

        private const val WAKE_LOCK_TIMEOUT_MILLIS = 10 * 60 * 1_000L
        private const val ACTION_PREPARE = "com.vangeaux.lagrange.tts.PREPARE"
        private const val ACTION_PLAY = "com.vangeaux.lagrange.tts.PLAY"
        private const val ACTION_PAUSE = "com.vangeaux.lagrange.tts.PAUSE"
        private const val ACTION_PREVIOUS = "com.vangeaux.lagrange.tts.PREVIOUS"
        private const val ACTION_NEXT = "com.vangeaux.lagrange.tts.NEXT"
        private const val ACTION_STOP = "com.vangeaux.lagrange.tts.STOP"
        private const val ACTION_STOP_OWNER = "com.vangeaux.lagrange.tts.STOP_OWNER"
        private const val EXTRA_OWNER_TOKEN = "epub_tts_owner_token"
        private const val EXTRA_REQUEST_ID = "epub_tts_request_id"
        private const val EXTRA_ACCOUNT_EPOCH = "epub_tts_account_epoch"
        private const val EXTRA_READER_KEY = "epub_tts_reader_key"
        private const val EXTRA_TITLE = "epub_tts_title"
        private const val INVALID_REQUEST_ID = Long.MIN_VALUE
        private const val INVALID_ACCOUNT_EPOCH = Long.MIN_VALUE
        @Volatile
        private var activeInstance: EpubTtsPlaybackService? = null
        private val TRANSPORT_ACTIONS = setOf(
            ACTION_PLAY,
            ACTION_PAUSE,
            ACTION_PREVIOUS,
            ACTION_NEXT,
            ACTION_STOP
        )

        fun start(
            context: Context,
            ownerToken: String,
            requestId: Long,
            accountEpoch: Long,
            readerKey: String,
            title: String
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, EpubTtsPlaybackService::class.java)
                    .setAction(ACTION_PREPARE)
                    .putExtra(EXTRA_OWNER_TOKEN, ownerToken)
                    .putExtra(EXTRA_REQUEST_ID, requestId)
                    .putExtra(EXTRA_ACCOUNT_EPOCH, accountEpoch)
                    .putExtra(EXTRA_READER_KEY, readerKey)
                    .putExtra(EXTRA_TITLE, title)
            )
        }

        /** Cancels a pending foreground start even when the Activity binder is not connected yet. */
        fun stop(context: Context, ownerToken: String, requestId: Long? = null) {
            val service = activeInstance
            if (service != null) {
                service.binder.stop(ownerToken, requestId)
            } else if (requestId == null) {
                context.stopService(Intent(context, EpubTtsPlaybackService::class.java))
            }
        }

        suspend fun stopAndAwait(context: Context) {
            val jobs = withContext(Dispatchers.Main.immediate) {
                val service = activeInstance
                if (service == null) {
                    context.stopService(Intent(context, EpubTtsPlaybackService::class.java))
                    emptyList()
                } else {
                    val pending = synchronized(service.progressJobs) {
                        service.progressJobs.toList()
                    }
                    service.closeSession(removeNotification = true)
                    pending
                }
            }
            jobs.joinAll()
        }
    }
}

internal class EpubTtsServiceConnection(
    context: Context,
    private val onConnected: (EpubTtsPlaybackService.PlaybackBinder) -> Unit,
    private val onDisconnected: () -> Unit
) : ServiceConnection {
    private val applicationContext = context.applicationContext
    private var isBound = false

    fun bind() {
        if (isBound) return
        isBound = applicationContext.bindService(
            Intent(applicationContext, EpubTtsPlaybackService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbind() {
        if (!isBound) return
        runCatching { applicationContext.unbindService(this) }
        isBound = false
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val playback = service as? EpubTtsPlaybackService.PlaybackBinder ?: return
        onConnected(playback)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        onDisconnected()
    }
}
