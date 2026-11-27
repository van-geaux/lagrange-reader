package com.vangeaux.lagrange

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice as PlatformVoice
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsDefaults
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsPreferencesEditor
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Try
import java.util.Locale

/** Uses Readium's Android engine normally, with an app-owned adapter for non-standard metadata. */
@OptIn(ExperimentalReadiumApi::class)
internal class QualityCompatibleAndroidTtsEngineProvider(
    private val context: Context,
    private val defaults: AndroidTtsDefaults = AndroidTtsDefaults(),
    private val onNonStandardVoiceMetadata: () -> Unit
) : TtsEngineProvider<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsPreferencesEditor,
    AndroidTtsEngine.Error, AndroidTtsEngine.Voice> {
    private val readium = AndroidTtsEngineProvider(context, defaults)

    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences
    ): Try<TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error,
        AndroidTtsEngine.Voice>, Error> {
        val initialized = CompletableDeferred<Boolean>()
        val platformEngine = TextToSpeech(context) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }
        val initializedSuccessfully = try {
            initialized.await()
        } catch (error: CancellationException) {
            platformEngine.shutdown()
            throw error
        }
        if (!initializedSuccessfully) {
            platformEngine.shutdown()
            return Try.failure(DebugError("Initialization of Android TTS service failed."))
        }
        val platformVoices = runCatching { platformEngine.voices.orEmpty() }.getOrDefault(emptySet())
        val converted = platformVoices.map { voice ->
            voice to compatibleAndroidTtsVoiceQuality(voice.quality)
        }
        if (converted.none { it.second.requiresConfirmation }) {
            platformEngine.shutdown()
            return readium.createEngine(publication, initialPreferences)
        }

        onNonStandardVoiceMetadata()
        val voices = converted.map { (voice, quality) ->
            AndroidTtsEngine.Voice(
                id = AndroidTtsEngine.Voice.Id(voice.name),
                language = Language(voice.locale),
                quality = quality.quality,
                requiresNetwork = voice.isNetworkConnectionRequired
            )
        }.toSet()
        val initialSettings = resolveSettings(publication, initialPreferences)
        return Try.success(
            CompatibleAndroidTtsEngine(platformEngine, platformVoices, voices, initialSettings, initialPreferences)
        )
    }

    private fun resolveSettings(
        publication: Publication,
        preferences: AndroidTtsPreferences
    ) = AndroidTtsSettings(
        language = preferences.language ?: publication.metadata.language ?: defaults.language ?: Language(Locale.getDefault()),
        overrideContentLanguage = preferences.language != null,
        pitch = preferences.pitch ?: defaults.pitch ?: 1.0,
        speed = preferences.speed ?: defaults.speed ?: 1.0,
        voices = preferences.voices ?: emptyMap()
    )

    override fun createPreferencesEditor(
        publication: Publication,
        initialPreferences: AndroidTtsPreferences
    ): AndroidTtsPreferencesEditor = readium.createPreferencesEditor(publication, initialPreferences)

    override fun createEmptyPreferences(): AndroidTtsPreferences = readium.createEmptyPreferences()

    override fun getPlaybackParameters(settings: AndroidTtsSettings): PlaybackParameters =
        readium.getPlaybackParameters(settings)

    override fun updatePlaybackParameters(
        previousPreferences: AndroidTtsPreferences,
        playbackParameters: PlaybackParameters
    ): AndroidTtsPreferences = readium.updatePlaybackParameters(previousPreferences, playbackParameters)

    override fun mapEngineError(error: AndroidTtsEngine.Error): PlaybackException = readium.mapEngineError(error)
}

@OptIn(ExperimentalReadiumApi::class)
private class CompatibleAndroidTtsEngine(
    private val platformEngine: TextToSpeech,
    private val platformVoices: Set<PlatformVoice>,
    override val voices: Set<AndroidTtsEngine.Voice>,
    initialSettings: AndroidTtsSettings,
    initialPreferences: AndroidTtsPreferences
) : TtsEngine<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsEngine.Error,
    AndroidTtsEngine.Voice> {
    private val currentPreferences = MutableStateFlow(initialPreferences)
    private val mutableSettings = MutableStateFlow(initialSettings)
    private var listener: TtsEngine.Listener<AndroidTtsEngine.Error>? = null
    private var closed = false

    override val settings: StateFlow<AndroidTtsSettings> = mutableSettings.asStateFlow()

    init {
        applyPitchAndSpeed(initialSettings)
    }

    override fun submitPreferences(preferences: AndroidTtsPreferences) {
        currentPreferences.value = currentPreferences.value.plus(preferences)
        val merged = currentPreferences.value
        mutableSettings.value = mutableSettings.value.copy(
            language = merged.language ?: mutableSettings.value.language,
            overrideContentLanguage = merged.language != null,
            pitch = merged.pitch ?: mutableSettings.value.pitch,
            speed = merged.speed ?: mutableSettings.value.speed,
            voices = merged.voices ?: mutableSettings.value.voices
        )
        applyPitchAndSpeed(mutableSettings.value)
    }

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        if (closed) return
        val configuration = mutableSettings.value
        val chosenLanguage = language
            .takeUnless { configuration.overrideContentLanguage }
            ?.takeIf { platformEngine.isLanguageAvailable(it.locale) >= TextToSpeech.LANG_AVAILABLE }
            ?: configuration.language.takeIf {
                platformEngine.isLanguageAvailable(it.locale) >= TextToSpeech.LANG_AVAILABLE
            }
            ?: platformEngine.defaultVoice?.locale?.let(::Language)
        if (chosenLanguage == null) {
            listener?.onError(requestId, AndroidTtsEngine.Error.Unknown)
            return
        }
        if (platformEngine.isLanguageAvailable(chosenLanguage.locale) < TextToSpeech.LANG_AVAILABLE) {
            listener?.onError(requestId, AndroidTtsEngine.Error.LanguageMissingData(chosenLanguage))
            return
        }
        val preferredId = configuration.voices[chosenLanguage]?.value
            ?: configuration.voices.entries.firstOrNull { it.key.locale.language == chosenLanguage.locale.language }?.value?.value
        val selectedVoice = preferredId?.let { id -> platformVoices.firstOrNull { it.name == id } }
        if (selectedVoice != null) platformEngine.voice = selectedVoice else platformEngine.language = chosenLanguage.locale
        platformEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                listener?.onStart(TtsEngine.RequestId(utteranceId))
            }
            override fun onDone(utteranceId: String) {
                listener?.onDone(TtsEngine.RequestId(utteranceId))
            }
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String) {
                listener?.onError(TtsEngine.RequestId(utteranceId), AndroidTtsEngine.Error.Unknown)
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                listener?.onError(TtsEngine.RequestId(utteranceId), errorFromPlatform(errorCode))
            }
            override fun onStop(utteranceId: String, interrupted: Boolean) {
                val id = TtsEngine.RequestId(utteranceId)
                if (interrupted) listener?.onInterrupted(id) else listener?.onFlushed(id)
            }
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                listener?.onRange(TtsEngine.RequestId(utteranceId), start until end)
            }
        })
        if (platformEngine.speak(text, TextToSpeech.QUEUE_ADD, null, requestId.value) != TextToSpeech.SUCCESS) {
            listener?.onError(requestId, AndroidTtsEngine.Error.Synthesis)
        }
    }

    override fun stop() {
        platformEngine.stop()
    }

    override fun setListener(listener: TtsEngine.Listener<AndroidTtsEngine.Error>?) {
        this.listener = listener
    }

    override fun close() {
        if (closed) return
        closed = true
        platformEngine.setOnUtteranceProgressListener(null)
        platformEngine.shutdown()
    }

    private fun applyPitchAndSpeed(settings: AndroidTtsSettings) {
        platformEngine.setPitch(settings.pitch.toFloat())
        platformEngine.setSpeechRate(settings.speed.toFloat())
    }

    private fun errorFromPlatform(code: Int): AndroidTtsEngine.Error = when (code) {
        TextToSpeech.ERROR_INVALID_REQUEST -> AndroidTtsEngine.Error.InvalidRequest
        TextToSpeech.ERROR_NETWORK -> AndroidTtsEngine.Error.Network
        TextToSpeech.ERROR_NETWORK_TIMEOUT -> AndroidTtsEngine.Error.NetworkTimeout
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> AndroidTtsEngine.Error.NotInstalledYet
        TextToSpeech.ERROR_OUTPUT -> AndroidTtsEngine.Error.Output
        TextToSpeech.ERROR_SERVICE -> AndroidTtsEngine.Error.Service
        TextToSpeech.ERROR_SYNTHESIS -> AndroidTtsEngine.Error.Synthesis
        else -> AndroidTtsEngine.Error.Unknown
    }
}
