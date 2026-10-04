package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.*

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class ReadingSessionReporter(
    context: Context,
    fileId: String?,
    enabled: Boolean,
    private val tracker: ReadingSessionTracker = ReadingSessionTracker()
) {
    private val fileId = fileId?.trim().orEmpty()
    private var enabled = enabled && this.fileId.isNotBlank()
    private val readingSessionModule = resolveActiveProviderReadingSessionModule(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(progressPercent: Float?, atMillis: Long = System.currentTimeMillis()) {
        if (!enabled) return
        tracker.start(progressPercent, atMillis)
    }

    fun enable(progressPercent: Float?, atMillis: Long = System.currentTimeMillis()) {
        if (fileId.isBlank()) return
        enabled = true
        tracker.start(progressPercent, atMillis)
    }

    fun activity(progressPercent: Float?, atMillis: Long = System.currentTimeMillis()) {
        if (!enabled) return
        enqueue(tracker.activity(progressPercent, atMillis))
    }

    fun pause(
        progressPercent: Float? = null,
        atMillis: Long = System.currentTimeMillis()
    ) {
        if (!enabled) return
        tracker.pause(atMillis, progressPercent)?.let(::enqueue)
    }

    fun resume(progressPercent: Float?, atMillis: Long = System.currentTimeMillis()) {
        if (!enabled) return
        enqueue(tracker.resume(progressPercent, atMillis))
    }

    fun end(progressPercent: Float?, atMillis: Long = System.currentTimeMillis()) {
        if (!enabled) return
        tracker.end(progressPercent, atMillis)?.let(::enqueue)
    }

    fun disable() {
        enabled = false
        scope.cancel()
    }

    private fun enqueue(payloads: List<ReadingSessionPayload>) {
        payloads.forEach { payload ->
            enqueue(payload)
        }
    }

    private fun enqueue(payload: ReadingSessionPayload) {
        scope.launch {
            runCatching {
                readingSessionModule.queueReadingSession(payload.copy(fileId = fileId))
            }.onFailure { error ->
                if (error !is UserFacingException) {
                    Log.w("ReadingSession", "Unable to report reading session", error)
                }
            }
        }
    }
}

internal class ReadingSessionReporterViewModel : ViewModel() {
    private var reporter: ReadingSessionReporter? = null
    private var reporterKey: String? = null

    fun reporter(context: Context, fileId: String?, enabled: Boolean): ReadingSessionReporter {
        val key = "${fileId.orEmpty()}|$enabled"
        reporter?.takeIf { reporterKey == key }?.let { return it }
        return ReadingSessionReporter(context.applicationContext, fileId, enabled).also {
            reporter = it
            reporterKey = key
        }
    }
}
