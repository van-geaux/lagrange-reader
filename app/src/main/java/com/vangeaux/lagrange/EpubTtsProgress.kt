package com.vangeaux.lagrange

import kotlin.math.abs

private const val EPUB_TTS_PROGRESS_INTERVAL_MILLIS = 15_000L
private const val EPUB_TTS_PROGRESS_DELTA_PERCENT = 0.2f

internal fun shouldQueueEpubTtsProgress(
    lastQueuedAtMillis: Long,
    lastQueuedPercent: Float?,
    lastQueuedChapter: Int,
    nowMillis: Long,
    percent: Float,
    chapter: Int
): Boolean {
    if (lastQueuedPercent == null || chapter != lastQueuedChapter) return true
    if (abs(percent - lastQueuedPercent) >= EPUB_TTS_PROGRESS_DELTA_PERCENT) return true
    return nowMillis - lastQueuedAtMillis >= EPUB_TTS_PROGRESS_INTERVAL_MILLIS
}
