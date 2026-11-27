package com.vangeaux.lagrange

import android.speech.tts.Voice
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
internal data class CompatibleAndroidTtsVoiceQuality(
    val quality: AndroidTtsEngine.Voice.Quality,
    val requiresConfirmation: Boolean
)

@OptIn(ExperimentalReadiumApi::class)
internal fun compatibleAndroidTtsVoiceQuality(rawQuality: Int): CompatibleAndroidTtsVoiceQuality {
    val quality = when (rawQuality) {
        Voice.QUALITY_VERY_HIGH -> AndroidTtsEngine.Voice.Quality.Highest
        Voice.QUALITY_HIGH -> AndroidTtsEngine.Voice.Quality.High
        Voice.QUALITY_NORMAL -> AndroidTtsEngine.Voice.Quality.Normal
        Voice.QUALITY_LOW -> AndroidTtsEngine.Voice.Quality.Low
        Voice.QUALITY_VERY_LOW -> AndroidTtsEngine.Voice.Quality.Lowest
        else -> AndroidTtsEngine.Voice.Quality.Normal
    }
    val requiresConfirmation = rawQuality !in 100..500 || rawQuality % 100 != 0
    return CompatibleAndroidTtsVoiceQuality(quality, requiresConfirmation)
}
