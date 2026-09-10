package com.vangeaux.lagrange

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Terminal (or near-terminal) outcome of a single book download attempt, independent of how
 * the transfer was executed (in-process coroutine or a WorkManager worker).
 */
internal sealed interface DownloadOutcome {
    data class Success(val localFile: File) : DownloadOutcome
    data object Canceled : DownloadOutcome
    data object AuthRequired : DownloadOutcome
    data object PermissionDenied : DownloadOutcome
    data class Failed(val error: Throwable) : DownloadOutcome
}

internal data class ScheduledDownload(
    val book: BookSummary,
    val fileId: String
)

/**
 * Executes book downloads and reports their lifecycle back to [AppCoordinator]. The coordinator
 * treats implementations as the execution authority for a download: it only mutates its own UI
 * projection (`BrowserState`) from the callbacks this interface invokes.
 */
internal interface DownloadScheduler {
    /** Starts (or, for durable implementations, enqueues) a download for [fileId]. */
    fun start(
        scope: CoroutineScope,
        serverUrl: String,
        book: BookSummary,
        fileId: String,
        cellularConsentGranted: Boolean,
        onProgress: (Float?) -> Unit,
        onOutcome: suspend (DownloadOutcome) -> Unit
    )

    /** Durably records an ordered group before allowing its first physical transfer to start. */
    fun startBatch(
        scope: CoroutineScope,
        serverUrl: String,
        downloads: List<ScheduledDownload>,
        cellularConsentGranted: Boolean,
        onProgress: (String, Float?) -> Unit,
        onOutcome: suspend (String, DownloadOutcome) -> Unit
    ) {
        downloads.forEach { download ->
            start(
                scope = scope,
                serverUrl = serverUrl,
                book = download.book,
                fileId = download.fileId,
                cellularConsentGranted = cellularConsentGranted,
                onProgress = { progress -> onProgress(download.fileId, progress) },
                onOutcome = { outcome -> onOutcome(download.fileId, outcome) }
            )
        }
    }

    /** Cancels an in-flight download for [fileId], if any. */
    fun cancel(serverUrl: String, fileId: String)

    /** Cancels every in-flight download tracked by this scheduler. */
    fun cancelAll()

    /**
     * Re-attaches to any downloads that are still running (or durably queued) for [serverUrl],
     * re-delivering progress/outcome callbacks as they occur. Returns active download books
     * keyed by fileId so the caller can restore identity and `downloadingFileIds` immediately,
     * waiting for the first progress update. Implementations that do not persist state beyond
     * process lifetime (e.g. in-memory schedulers) may simply return an empty map.
     */
    suspend fun reconcile(
        scope: CoroutineScope,
        serverUrl: String,
        bookForFileId: (String) -> BookSummary?,
        onProgress: (String, Float?) -> Unit,
        onOutcome: suspend (String, DownloadOutcome) -> Unit
    ): Map<String, BookSummary>
}

/**
 * Default [DownloadScheduler] that reproduces the historical in-process behavior: a lazily
 * started coroutine per download, tracked only in memory. This is what tests exercise (via
 * [AppCoordinator]'s default constructor argument) and what backs the app when no durable
 * scheduler is supplied. It intentionally cannot survive process death, matching the previous
 * `activeDownloads` map behavior.
 */
internal class InProcessDownloadScheduler(
    private val repository: BookOrbitDataSource
) : DownloadScheduler {
    private data class PendingDownload(
        val serverUrl: String,
        val book: BookSummary,
        val fileId: String,
        val onProgress: (Float?) -> Unit,
        val onOutcome: suspend (DownloadOutcome) -> Unit
    )

    private val pending = ArrayDeque<PendingDownload>()
    private var activeFileId: String? = null
    private var activeJob: Job? = null

    override fun start(
        scope: CoroutineScope,
        serverUrl: String,
        book: BookSummary,
        fileId: String,
        cellularConsentGranted: Boolean,
        onProgress: (Float?) -> Unit,
        onOutcome: suspend (DownloadOutcome) -> Unit
    ) {
        if (activeFileId == fileId || pending.any { it.fileId == fileId }) return
        pending.addLast(PendingDownload(serverUrl, book, fileId, onProgress, onOutcome))
        startNext(scope)
    }

    private fun startNext(scope: CoroutineScope) {
        if (activeJob != null) return
        val next = pending.removeFirstOrNull() ?: return
        activeFileId = next.fileId
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching {
                repository.downloadBook(next.book) { progress -> next.onProgress(progress) }
            }
            result
                .onSuccess { next.onOutcome(DownloadOutcome.Success(it)) }
                .onFailure { error ->
                    when (error) {
                        is CancellationException -> next.onOutcome(DownloadOutcome.Canceled)
                        is AuthenticationRequiredException -> next.onOutcome(DownloadOutcome.AuthRequired)
                        is HttpRequestException -> if (error.code == 403) {
                            next.onOutcome(DownloadOutcome.PermissionDenied)
                        } else {
                            next.onOutcome(DownloadOutcome.Failed(error))
                        }
                        else -> next.onOutcome(DownloadOutcome.Failed(error))
                    }
                }
            activeFileId = null
            activeJob = null
            startNext(scope)
        }
        activeJob = job
        job.start()
    }

    override fun cancel(serverUrl: String, fileId: String) {
        if (activeFileId == fileId) {
            activeJob?.cancel()
        } else {
            pending.removeAll { it.serverUrl == serverUrl && it.fileId == fileId }
        }
    }

    override fun cancelAll() {
        pending.clear()
        activeJob?.cancel()
    }

    override suspend fun reconcile(
        scope: CoroutineScope,
        serverUrl: String,
        bookForFileId: (String) -> BookSummary?,
        onProgress: (String, Float?) -> Unit,
        onOutcome: suspend (String, DownloadOutcome) -> Unit
    ): Map<String, BookSummary> = emptyMap()
}
