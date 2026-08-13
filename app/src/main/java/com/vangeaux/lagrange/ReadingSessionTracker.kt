package com.vangeaux.lagrange

import java.util.UUID
import kotlin.math.max

data class ReadingSessionPayload(
    val sessionId: String,
    val serverUrl: String = "",
    val fileId: String = "",
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Long,
    val progressDelta: Double? = null,
    val endProgress: Double? = null,
    val updatedAtMillis: Long = endedAtMillis
)

internal class ReadingSessionTracker(
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val minSessionMillis: Long = DEFAULT_MIN_SESSION_MILLIS
) {
    private var sessionId: String? = null
    private var startedAtMillis: Long = 0L
    private var activeStartMillis: Long? = null
    private var activeMillis: Long = 0L
    private var lastActivityMillis: Long = 0L
    private var startProgress: Double? = null

    fun start(progressPercent: Float?, atMillis: Long) {
        if (sessionId != null) return
        sessionId = sessionIdFactory()
        startedAtMillis = atMillis
        activeStartMillis = atMillis
        activeMillis = 0L
        lastActivityMillis = atMillis
        startProgress = normalizeProgress(progressPercent)
    }

    /**
     * Records foreground activity and returns a completed payload when inactivity rolled over
     * the previous session. The caller should enqueue every returned payload.
     */
    fun activity(progressPercent: Float?, atMillis: Long): List<ReadingSessionPayload> {
        if (sessionId == null) {
            start(progressPercent, atMillis)
            return emptyList()
        }

        val safeAtMillis = max(atMillis, lastActivityMillis)
        if (safeAtMillis - lastActivityMillis >= idleTimeoutMillis) {
            val rolloverAtMillis = lastActivityMillis + idleTimeoutMillis
            val completed = endInternal(progressPercent, rolloverAtMillis)
            start(progressPercent, safeAtMillis)
            return listOfNotNull(completed)
        }

        accumulateUntil(safeAtMillis)
        lastActivityMillis = safeAtMillis
        return emptyList()
    }

    fun pause(atMillis: Long, progressPercent: Float? = null): ReadingSessionPayload? =
        end(progressPercent, atMillis)

    fun resume(progressPercent: Float?, atMillis: Long): List<ReadingSessionPayload> {
        if (sessionId == null) {
            start(progressPercent, atMillis)
            return emptyList()
        }
        if (activeStartMillis != null) {
            return activity(progressPercent, atMillis)
        }

        val safeAtMillis = max(atMillis, lastActivityMillis)
        if (safeAtMillis - lastActivityMillis >= idleTimeoutMillis) {
            val rolloverAtMillis = lastActivityMillis + idleTimeoutMillis
            val completed = endInternal(progressPercent, rolloverAtMillis)
            start(progressPercent, safeAtMillis)
            return listOfNotNull(completed)
        }

        activeStartMillis = safeAtMillis
        lastActivityMillis = safeAtMillis
        return emptyList()
    }

    fun end(progressPercent: Float?, atMillis: Long): ReadingSessionPayload? {
        if (sessionId == null) return null
        val safeAtMillis = max(atMillis, lastActivityMillis)
        val endAtMillis = if (safeAtMillis - lastActivityMillis >= idleTimeoutMillis) {
            lastActivityMillis + idleTimeoutMillis
        } else {
            safeAtMillis
        }
        return endInternal(progressPercent, endAtMillis)
    }

    private fun endInternal(progressPercent: Float?, endAtMillis: Long): ReadingSessionPayload? {
        val activeStart = activeStartMillis
        if (activeStart != null) {
            activeMillis += (endAtMillis - activeStart).coerceAtLeast(0L)
        }

        val currentSessionId = sessionId
        val payload = if (
            currentSessionId != null &&
            activeMillis >= minSessionMillis
        ) {
            val normalizedEndProgress = normalizeProgress(progressPercent)
            ReadingSessionPayload(
                sessionId = currentSessionId,
                startedAtMillis = startedAtMillis,
                endedAtMillis = endAtMillis,
                durationSeconds = activeMillis / 1_000L,
                progressDelta = startProgress?.let { start ->
                    normalizedEndProgress?.minus(start)
                },
                endProgress = normalizedEndProgress,
                updatedAtMillis = endAtMillis
            )
        } else {
            null
        }

        sessionId = null
        activeStartMillis = null
        activeMillis = 0L
        lastActivityMillis = 0L
        startProgress = null
        startedAtMillis = 0L
        return payload
    }

    private fun accumulateUntil(atMillis: Long) {
        val activeStart = activeStartMillis ?: return
        activeMillis += (atMillis - activeStart).coerceAtLeast(0L)
        activeStartMillis = atMillis
    }

    private fun normalizeProgress(value: Float?): Double? = value
        ?.takeIf { it.isFinite() }
        ?.coerceIn(0f, 100f)
        ?.toDouble()

    private companion object {
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = 5 * 60 * 1_000L
        const val DEFAULT_MIN_SESSION_MILLIS = 10 * 1_000L
    }
}
