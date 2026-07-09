package com.bookorbit.android

import kotlin.math.abs

internal data class ProgressSnapshot(
    val mediaKind: MediaKind,
    val positionMs: Long,
    val pageIndex: Int,
    val progressPercent: Float?,
    val observedAtMillis: Long
)

internal object ProgressQueuePolicy {
    fun shouldQueue(current: ProgressSnapshot, lastQueued: ProgressSnapshot?): Boolean {
        if (lastQueued == null) {
            return true
        }
        if (!isMeaningfullyDifferent(current, lastQueued)) {
            return false
        }
        if (current.mediaKind != MediaKind.AUDIO) {
            return true
        }
        val elapsedMillis = current.observedAtMillis - lastQueued.observedAtMillis
        val positionDeltaMillis = abs(current.positionMs - lastQueued.positionMs)
        return elapsedMillis >= MIN_AUDIO_PROGRESS_QUEUE_INTERVAL_MS ||
            positionDeltaMillis >= MIN_AUDIO_POSITION_DELTA_MS ||
            percentDelta(current, lastQueued) >= MIN_PERCENT_DELTA
    }

    fun isMeaningfullyDifferent(current: ProgressSnapshot, previous: ProgressSnapshot?): Boolean {
        previous ?: return true
        return current.pageIndex != previous.pageIndex ||
            abs(current.positionMs - previous.positionMs) >= MIN_POSITION_DELTA_MS ||
            percentDelta(current, previous) >= MIN_PERCENT_DELTA
    }

    private fun percentDelta(current: ProgressSnapshot, previous: ProgressSnapshot): Float {
        val currentValue = current.progressPercent ?: return 0f
        val previousValue = previous.progressPercent ?: return 0f
        return abs(normalizeProgressPercent(currentValue) - normalizeProgressPercent(previousValue))
    }

    private fun normalizeProgressPercent(value: Float): Float {
        return if (value in 0f..1f) value * 100f else value
    }

    private const val MIN_AUDIO_PROGRESS_QUEUE_INTERVAL_MS = 15_000L
    private const val MIN_AUDIO_POSITION_DELTA_MS = 15_000L
    private const val MIN_POSITION_DELTA_MS = 1_000L
    private const val MIN_PERCENT_DELTA = 1f
}
