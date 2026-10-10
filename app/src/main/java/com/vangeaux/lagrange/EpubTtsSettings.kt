package com.vangeaux.lagrange

import kotlin.math.roundToInt

internal const val EPUB_TTS_RATE_MIN_HUNDREDTHS = 50
internal const val EPUB_TTS_RATE_MAX_HUNDREDTHS = 200
internal const val EPUB_TTS_PAUSE_MIN_MILLIS = 0
internal const val EPUB_TTS_PAUSE_MAX_MILLIS = 2_000

internal data class EpubTtsPauseSettings(
    val enabled: Boolean = true,
    // A non-zero comma value necessarily creates a new Android TTS utterance. Engines may add
    // substantially more boundary latency than the requested silence, so zero is the only
    // portable default that does not claim false millisecond precision.
    val commaMillis: Int = 0,
    val semicolonMillis: Int = 200,
    val colonMillis: Int = 175,
    val emDashMillis: Int = 200,
    val ellipsisMillis: Int = 250,
    val parenthesesMillis: Int = 100
) {
    fun normalized(): EpubTtsPauseSettings = copy(
        commaMillis = normalizeEpubTtsPauseMillis(commaMillis),
        semicolonMillis = normalizeEpubTtsPauseMillis(semicolonMillis),
        colonMillis = normalizeEpubTtsPauseMillis(colonMillis),
        emDashMillis = normalizeEpubTtsPauseMillis(emDashMillis),
        ellipsisMillis = normalizeEpubTtsPauseMillis(ellipsisMillis),
        parenthesesMillis = normalizeEpubTtsPauseMillis(parenthesesMillis)
    )
}

internal data class EpubTtsImageSettings(
    val readDescriptions: Boolean = false,
    val readResourceNames: Boolean = false
)

internal data class EpubTtsSettings(
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val pauses: EpubTtsPauseSettings = EpubTtsPauseSettings(enabled = false),
    val images: EpubTtsImageSettings = EpubTtsImageSettings(),
    val showBookTitleOnLockScreen: Boolean = true,
    val voiceIds: Map<String, String> = emptyMap()
) {
    fun normalized(): EpubTtsSettings = copy(
        speed = normalizeEpubTtsPlaybackSpeed(speed),
        pitch = normalizeEpubTtsPitch(pitch),
        pauses = pauses.normalized(),
        voiceIds = voiceIds
            .mapKeys { normalizeEpubTtsVoiceLanguageKey(it.key) }
            .filterKeys(String::isNotBlank)
            .mapValues { it.value.trim() }
            .filterValues(String::isNotBlank)
    )
}

internal fun normalizeEpubTtsVoiceLanguageKey(value: String): String {
    val trimmed = value.trim().lowercase()
    return if (trimmed.startsWith("language(") && trimmed.endsWith(")")) {
        trimmed.removePrefix("language(").removeSuffix(")")
    } else {
        trimmed
    }
}

internal fun normalizeEpubTtsPlaybackSpeed(value: Float): Float =
    normalizeEpubTtsRate(value)

internal fun normalizeEpubTtsPitch(value: Float): Float =
    normalizeEpubTtsRate(value)

private fun normalizeEpubTtsRate(value: Float): Float {
    val safeValue = value.takeIf(Float::isFinite) ?: 1f
    return (safeValue * 100f).roundToInt()
        .coerceIn(EPUB_TTS_RATE_MIN_HUNDREDTHS, EPUB_TTS_RATE_MAX_HUNDREDTHS) / 100f
}

internal fun normalizeEpubTtsPauseMillis(value: Int): Int =
    value.coerceIn(EPUB_TTS_PAUSE_MIN_MILLIS, EPUB_TTS_PAUSE_MAX_MILLIS)

internal fun parseEpubTtsRate(value: String): Float? {
    return parsePlaybackRate(
        value = value,
        minHundredths = EPUB_TTS_RATE_MIN_HUNDREDTHS,
        maxHundredths = EPUB_TTS_RATE_MAX_HUNDREDTHS
    )
}

internal fun parseEpubTtsPauseMillis(value: String): Int? =
    value.trim().toIntOrNull()
        ?.takeIf { it in EPUB_TTS_PAUSE_MIN_MILLIS..EPUB_TTS_PAUSE_MAX_MILLIS }
