package com.vangeaux.lagrange

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsPreferencesEditor
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Try
import kotlin.math.roundToInt

internal const val EPUB_TTS_MAX_CHUNKS_PER_UTTERANCE = 64
internal const val EPUB_TTS_MAX_TOTAL_PAUSE_MILLIS_PER_UTTERANCE = 15_000

internal data class EpubTtsSpeechChunk(
    val text: String,
    val sourceStart: Int,
    val pauseAfterMillis: Int
)

/** Splits without changing any source offsets, so Readium's range locations remain valid. */
internal fun epubTtsSpeechChunks(
    text: String,
    pauses: EpubTtsPauseSettings,
    playbackSpeed: Float = 1f
): List<EpubTtsSpeechChunk> {
    val normalized = pauses.normalized()
    if (text.isEmpty()) return listOf(EpubTtsSpeechChunk("", 0, 0))
    if (!normalized.enabled) return listOf(EpubTtsSpeechChunk(text, 0, 0))

    val chunks = mutableListOf<EpubTtsSpeechChunk>()
    var start = 0
    var totalPauseMillis = 0
    var splitEvents = 0
    text.forEachIndexed { index, character ->
        if (splitEvents >= EPUB_TTS_MAX_CHUNKS_PER_UTTERANCE - 1) return@forEachIndexed
        val requestedPause = when {
            character == ',' && isWhitespaceFollowedComma(text, index) -> normalized.commaMillis
            character == ';' -> normalized.semicolonMillis
            character == ':' && !isNonProseColon(text, index) -> normalized.colonMillis
            character == '\u2014' -> normalized.emDashMillis
            character == '\u2026' || isDotEllipsisEnd(text, index) -> normalized.ellipsisMillis
            character in "()[]{}" -> normalized.parenthesesMillis
            else -> 0
        }.let { baseMillis -> scaleEpubTtsPauseMillis(baseMillis, playbackSpeed) }
        val pause = requestedPause.coerceAtMost(
            EPUB_TTS_MAX_TOTAL_PAUSE_MILLIS_PER_UTTERANCE - totalPauseMillis
        )
        if (pause <= 0) return@forEachIndexed
        splitEvents += 1

        val candidate = text.substring(start, index + 1)
        // Do not ask an Android engine to synthesize an isolated punctuation mark. If this mark
        // immediately follows a split (for example "):"), attach its text and pause to the
        // preceding chunk so neither the mark nor its configured delay is lost.
        if (!candidate.any(Char::isLetterOrDigit)) {
            if (chunks.isNotEmpty()) {
                val previous = chunks.removeAt(chunks.lastIndex)
                chunks += previous.copy(
                    text = previous.text + candidate,
                    pauseAfterMillis = previous.pauseAfterMillis + pause
                )
                start = index + 1
                totalPauseMillis += pause
            }
            return@forEachIndexed
        }
        chunks += EpubTtsSpeechChunk(candidate, start, pause)
        start = index + 1
        totalPauseMillis += pause
    }
    if (start < text.length) {
        chunks += EpubTtsSpeechChunk(text.substring(start), start, 0)
    }
    return chunks.ifEmpty { listOf(EpubTtsSpeechChunk(text, 0, 0)) }
}

private fun isNonProseColon(text: String, index: Int): Boolean {
    val before = text.getOrNull(index - 1)
    val after = text.getOrNull(index + 1)
    return after == '/' || (before?.isDigit() == true && after?.isDigit() == true)
}

private fun isWhitespaceFollowedComma(text: String, index: Int): Boolean =
    text.getOrNull(index + 1)?.isWhitespace() == true

private fun isDotEllipsisEnd(text: String, index: Int): Boolean =
    text.getOrNull(index) == '.' &&
        text.getOrNull(index - 1) == '.' &&
        text.getOrNull(index - 2) == '.' &&
        text.getOrNull(index + 1) != '.'

internal fun scaleEpubTtsPauseMillis(baseMillis: Int, playbackSpeed: Float): Int {
    val normalizedBase = normalizeEpubTtsPauseMillis(baseMillis)
    if (normalizedBase == 0) return 0
    val normalizedSpeed = normalizeEpubTtsPlaybackSpeed(playbackSpeed)
    return (normalizedBase / normalizedSpeed).roundToInt()
}

@OptIn(ExperimentalReadiumApi::class)
internal class PunctuationPausingTtsEngineProvider(
    context: Context,
    private val pauseSettings: () -> EpubTtsPauseSettings
) : TtsEngineProvider<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsPreferencesEditor,
    AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {
    private val delegate = AndroidTtsEngineProvider(context)

    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences
    ): Try<TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error,
        AndroidTtsEngine.Voice>, Error> = delegate.createEngine(publication, initialPreferences)
        .map { engine -> PunctuationPausingTtsEngine(engine, pauseSettings) }

    override fun createPreferencesEditor(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences
    ): AndroidTtsPreferencesEditor = delegate.createPreferencesEditor(publication, initialPreferences)

    override fun createEmptyPreferences(): AndroidTtsPreferences = delegate.createEmptyPreferences()

    override fun getPlaybackParameters(settings: AndroidTtsSettings): PlaybackParameters =
        delegate.getPlaybackParameters(settings)

    override fun updatePlaybackParameters(
        previousPreferences: AndroidTtsPreferences,
        playbackParameters: PlaybackParameters
    ): AndroidTtsPreferences = delegate.updatePlaybackParameters(previousPreferences, playbackParameters)

    override fun mapEngineError(error: AndroidTtsEngine.Error): PlaybackException =
        delegate.mapEngineError(error)
}

@OptIn(ExperimentalReadiumApi::class)
internal class PunctuationPausingTtsEngine(
    private val delegate: TtsEngine<AndroidTtsSettings, AndroidTtsPreferences,
        AndroidTtsEngine.Error, AndroidTtsEngine.Voice>,
    private val pauseSettings: () -> EpubTtsPauseSettings,
    private val scope: CoroutineScope = MainScope()
) : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error,
    AndroidTtsEngine.Voice> {
    private data class Request(
        val externalId: TtsEngine.RequestId,
        val language: Language?,
        val chunks: List<EpubTtsSpeechChunk>,
        var chunkIndex: Int = 0,
        var started: Boolean = false
    )

    private var listener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null
    private var request: Request? = null
    private var transitionJob: Job? = null
    private var generation = 0L

    init {
        delegate.setListener(DelegateListener())
    }

    override val voices: Set<AndroidTtsEngine.Voice>
        get() = delegate.voices

    override val settings: StateFlow<AndroidTtsSettings>
        get() = delegate.settings

    override fun submitPreferences(preferences: AndroidTtsPreferences) {
        delegate.submitPreferences(preferences)
    }

    override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
        this.listener = listener
    }

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        check(request == null) { "A TTS request is already active." }
        generation += 1
        request = Request(
            requestId,
            language,
            epubTtsSpeechChunks(text, pauseSettings(), delegate.settings.value.speed.toFloat())
        )
        speakCurrentChunk(generation)
    }

    override fun stop() {
        val interrupted = request
        request = null
        generation += 1
        transitionJob?.cancel()
        transitionJob = null
        delegate.stop()
        interrupted?.let {
            if (it.started) listener?.onInterrupted(it.externalId)
            else listener?.onFlushed(it.externalId)
        }
    }

    override fun close() {
        request = null
        generation += 1
        transitionJob?.cancel()
        transitionJob = null
        delegate.close()
        scope.cancel()
    }

    private fun speakCurrentChunk(requestGeneration: Long) {
        val active = request ?: return
        if (requestGeneration != generation) return
        val chunk = active.chunks[active.chunkIndex]
        delegate.speak(internalId(active, active.chunkIndex), chunk.text, active.language)
    }

    private fun internalId(request: Request, index: Int): TtsEngine.RequestId =
        TtsEngine.RequestId("${request.externalId.value}:$generation:$index")

    private fun activeChunk(id: TtsEngine.RequestId): Pair<Request, EpubTtsSpeechChunk>? {
        val active = request ?: return null
        if (id != internalId(active, active.chunkIndex)) return null
        return active to active.chunks[active.chunkIndex]
    }

    private fun finishChunk(active: Request, chunk: EpubTtsSpeechChunk) {
        val requestGeneration = generation
        transitionJob?.cancel()
        transitionJob = scope.launch {
            if (chunk.pauseAfterMillis > 0) delay(chunk.pauseAfterMillis.toLong())
            if (request !== active || requestGeneration != generation) return@launch
            if (active.chunkIndex + 1 < active.chunks.size) {
                active.chunkIndex += 1
                speakCurrentChunk(requestGeneration)
            } else {
                request = null
                listener?.onDone(active.externalId)
            }
        }
    }

    private inner class DelegateListener : TtsEngine.Listener<AndroidTtsEngine.Error> {
        override fun onStart(requestId: TtsEngine.RequestId) {
            scope.launch {
                val (active) = activeChunk(requestId) ?: return@launch
                if (!active.started) {
                    active.started = true
                    listener?.onStart(active.externalId)
                }
            }
        }

        override fun onRange(requestId: TtsEngine.RequestId, range: IntRange) {
            scope.launch {
                val (active, chunk) = activeChunk(requestId) ?: return@launch
                listener?.onRange(
                    active.externalId,
                    (chunk.sourceStart + range.first)..(chunk.sourceStart + range.last)
                )
            }
        }

        override fun onInterrupted(requestId: TtsEngine.RequestId) {
            scope.launch {
                val (active) = activeChunk(requestId) ?: return@launch
                request = null
                listener?.onInterrupted(active.externalId)
            }
        }

        override fun onFlushed(requestId: TtsEngine.RequestId) {
            scope.launch {
                val (active) = activeChunk(requestId) ?: return@launch
                request = null
                listener?.onFlushed(active.externalId)
            }
        }

        override fun onDone(requestId: TtsEngine.RequestId) {
            scope.launch {
                val (active, chunk) = activeChunk(requestId) ?: return@launch
                finishChunk(active, chunk)
            }
        }

        override fun onError(requestId: TtsEngine.RequestId, error: AndroidTtsEngine.Error) {
            scope.launch {
                val (active) = activeChunk(requestId) ?: return@launch
                request = null
                listener?.onError(active.externalId, error)
            }
        }
    }
}
