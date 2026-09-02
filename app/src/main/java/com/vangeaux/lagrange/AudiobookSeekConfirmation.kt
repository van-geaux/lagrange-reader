package com.vangeaux.lagrange

internal data class AudiobookSeekTransaction(
    val previousPositionMs: Long,
    val requestedPositionMs: Long
)

internal enum class AudiobookSeekResolution {
    KEEP_REQUESTED,
    RETURN_TO_PREVIOUS,
    DISMISS
}

internal fun resolveAudiobookSeekConfirmation(
    transaction: AudiobookSeekTransaction,
    resolution: AudiobookSeekResolution
): Long = when (resolution) {
    AudiobookSeekResolution.RETURN_TO_PREVIOUS -> transaction.previousPositionMs
    AudiobookSeekResolution.KEEP_REQUESTED,
    AudiobookSeekResolution.DISMISS -> transaction.requestedPositionMs
}

internal fun audiobookSeekTransactionOrNull(
    previousPositionMs: Long,
    requestedPositionMs: Long,
    confirmationEnabled: Boolean
): AudiobookSeekTransaction? =
    if (confirmationEnabled && previousPositionMs != requestedPositionMs) {
        AudiobookSeekTransaction(previousPositionMs, requestedPositionMs)
    } else {
        null
    }

internal fun audiobookChapterSeekTransaction(
    previousPositionMs: Long,
    chapterStartMs: Long,
    requestedChapterPositionMs: Long
): AudiobookSeekTransaction = AudiobookSeekTransaction(
    previousPositionMs = previousPositionMs,
    requestedPositionMs = chapterStartMs + requestedChapterPositionMs
)

internal fun audiobookSeekConfirmationFromStorage(value: Boolean?): Boolean = value ?: true
