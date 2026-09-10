package com.vangeaux.lagrange

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.lifecycle.Observer
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stable, per-file unique WorkManager work name. Same file never runs as two concurrent works. */
internal fun downloadUniqueWorkName(serverUrl: String, fileId: String): String =
    "$DOWNLOAD_WORK_NAME_PREFIX:$serverUrl:$fileId"

internal fun downloadFileTag(fileId: String): String = "$DOWNLOAD_FILE_TAG_PREFIX:$fileId"

internal fun downloadServerTag(serverUrl: String): String = "$DOWNLOAD_SERVER_TAG_PREFIX:$serverUrl"

internal fun nextDownloadQueueSequence(nowMillis: Long, previous: Long): Long =
    maxOf(nowMillis * 1_000L, previous + 1L)

internal class DownloadProgressThrottler(
    private val minIntervalMillis: Long
) {
    private var hasEmitted = false
    private var lastPercent: Int? = null
    private var lastEmittedAtMillis = 0L

    @Synchronized
    fun shouldEmit(progress: Float?, nowMillis: Long): Boolean {
        val percent = progress?.coerceIn(0f, 1f)?.times(100f)?.toInt()
        if (hasEmitted && percent == lastPercent) return false
        if (hasEmitted && percent != 100 && nowMillis - lastEmittedAtMillis < minIntervalMillis) {
            return false
        }
        hasEmitted = true
        lastPercent = percent
        lastEmittedAtMillis = nowMillis
        return true
    }
}

internal class DownloadObserverRegistry {
    private val workIds = ConcurrentHashMap.newKeySet<UUID>()

    fun register(workId: UUID): Boolean = workIds.add(workId)

    fun retire(workId: UUID) {
        workIds.remove(workId)
    }
}

internal class DownloadTransferGate {
    private val mutex = Mutex()

    suspend fun <T> withPermit(block: suspend () -> T): T = mutex.withLock { block() }
}

private val physicalDownloadGate = DownloadTransferGate()

internal fun downloadAttemptBookSummary(attempt: DownloadAttempt): BookSummary = BookSummary(
    libraryId = "",
    id = attempt.bookId,
    fileId = attempt.fileId,
    title = attempt.title,
    filename = attempt.filename,
    format = attempt.mimeType,
    mediaKind = attempt.mediaKind,
    localPath = attempt.existingLocalPath,
    updatedAtMillis = attempt.sourceUpdatedAtMillis
)

private const val DOWNLOAD_WORK_NAME_PREFIX = "bookorbit-download"
private const val DOWNLOAD_FILE_TAG_PREFIX = "bookorbit-download-file"
private const val DOWNLOAD_SERVER_TAG_PREFIX = "bookorbit-download-server"
internal const val DOWNLOAD_TAG = "bookorbit-download-all"

private const val KEY_SERVER_URL = "server-url"
private const val KEY_FILE_ID = "file-id"
private const val KEY_BOOK_ID = "book-id"
private const val KEY_LIBRARY_ID = "library-id"
private const val KEY_TITLE = "title"
private const val KEY_FILENAME = "filename"
private const val KEY_MEDIA_KIND = "media-kind"
private const val KEY_FORMAT = "format"
private const val KEY_UPDATED_AT = "updated-at"
private const val KEY_CELLULAR_CONSENT_GRANTED = "cellular-consent-granted"
internal const val KEY_PROGRESS = "progress"
internal const val KEY_OUTCOME = "outcome"
internal const val KEY_ERROR_MESSAGE = "error-message"
internal const val KEY_LOCAL_PATH = "local-path"

internal const val OUTCOME_AUTH_REQUIRED = "auth_required"
internal const val OUTCOME_POLICY_BLOCKED = "policy_blocked"
internal const val OUTCOME_PERMISSION_DENIED = "permission_denied"
internal const val OUTCOME_FAILED = "failed"

/**
 * Builds the input [androidx.work.Data] and [OneTimeWorkRequest] for downloading a single book.
 * Kept separate from enqueueing so it can be unit tested without touching a real WorkManager.
 */
internal fun downloadWorkRequest(
    serverUrl: String,
    book: BookSummary,
    fileId: String,
    cellularConsentGranted: Boolean
): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<BookDownloadWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setInputData(
            workDataOf(
                KEY_SERVER_URL to serverUrl,
                KEY_FILE_ID to fileId,
                KEY_BOOK_ID to book.id,
                KEY_LIBRARY_ID to book.libraryId,
                KEY_TITLE to book.title,
                KEY_FILENAME to book.filename,
                KEY_MEDIA_KIND to book.mediaKind.name,
                KEY_FORMAT to book.format,
                KEY_UPDATED_AT to (book.updatedAtMillis ?: -1L),
                KEY_CELLULAR_CONSENT_GRANTED to cellularConsentGranted
            )
        )
        .addTag(DOWNLOAD_TAG)
        .addTag(downloadFileTag(fileId))
        .addTag(downloadServerTag(serverUrl))
        .build()

/**
 * Foreground [CoroutineWorker] that performs a single book download by delegating the actual
 * transfer to [BookOrbitRepository.downloadBook]. Byte-range resume is out of scope: on retry
 * this simply calls into the repository again, which reuses its own `.part` staging/integrity
 * logic.
 */
class BookDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val serverUrl = inputData.getString(KEY_SERVER_URL)
        val fileId = inputData.getString(KEY_FILE_ID)
        val bookId = inputData.getString(KEY_BOOK_ID)
        val title = inputData.getString(KEY_TITLE)
        if (serverUrl.isNullOrBlank() || fileId.isNullOrBlank() || bookId.isNullOrBlank() || title.isNullOrBlank()) {
            return Result.failure()
        }
        val book = BookSummary(
            libraryId = inputData.getString(KEY_LIBRARY_ID).orEmpty(),
            id = bookId,
            fileId = fileId,
            title = title,
            filename = inputData.getString(KEY_FILENAME),
            format = inputData.getString(KEY_FORMAT),
            mediaKind = runCatching {
                MediaKind.valueOf(inputData.getString(KEY_MEDIA_KIND) ?: MediaKind.UNKNOWN.name)
            }.getOrDefault(MediaKind.UNKNOWN),
            updatedAtMillis = inputData.getLong(KEY_UPDATED_AT, -1L).takeIf { it >= 0L }
        )

        val repository = BookOrbitRepository(applicationContext)
        val progressThrottler = DownloadProgressThrottler(minIntervalMillis = 500L)

        // The session may have changed servers between enqueue and execution.
        if (repository.getServerUrl() != serverUrl) {
            finishQueueEntry(serverUrl, fileId)
            return Result.failure()
        }

        // Re-check the cellular policy against the *current* network at execution time. A
        // pre-enqueue "ask" confirmation from the user does not carry forward if conditions
        // changed; we never silently transfer over cellular without a fresh START decision.
        val policy = AppPreferencesStore(applicationContext).read().cellularDownloadPolicy
        val isCellularOrMetered = applicationContext.isActiveCellularOrMeteredNetwork()
        if (!backgroundDownloadMayStart(
                policy = policy,
                isCellularOrMetered = isCellularOrMetered,
                cellularConsentGranted = inputData.getBoolean(KEY_CELLULAR_CONSENT_GRANTED, false)
            )
        ) {
            finishQueueEntry(serverUrl, fileId)
            return Result.failure(
                workDataOf(
                    KEY_OUTCOME to OUTCOME_POLICY_BLOCKED,
                    KEY_ERROR_MESSAGE to "Cellular download policy requires confirmation on the current network."
                )
            )
        }

        setForeground(foregroundInfo(applicationContext, title, progressPercent = null))

        return try {
            coroutineScope {
                val localFile = physicalDownloadGate.withPermit {
                    repository.downloadBook(book) { progress ->
                        if (!progressThrottler.shouldEmit(progress, System.currentTimeMillis())) {
                            return@downloadBook
                        }
                        launch {
                            val percent = progress?.let { (it * 100f).toInt().coerceIn(0, 100) }
                            setProgress(workDataOf(KEY_PROGRESS to (progress ?: -1f)))
                            runCatching { setForeground(foregroundInfo(applicationContext, title, percent)) }
                        }
                    }
                }
                finishQueueEntry(serverUrl, fileId)
                Result.success(workDataOf(KEY_LOCAL_PATH to localFile.absolutePath))
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                runCatching { repository.clearInterruptedDownload(fileId) }
                finishQueueEntry(serverUrl, fileId)
            }
            throw cancellation
        } catch (auth: AuthenticationRequiredException) {
            finishQueueEntry(serverUrl, fileId)
            Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_AUTH_REQUIRED))
        } catch (io: UnknownHostException) {
            Result.retry()
        } catch (io: SocketTimeoutException) {
            Result.retry()
        } catch (io: SSLException) {
            Result.retry()
        } catch (http: HttpRequestException) {
            if (http.code == 403) {
                finishQueueEntry(serverUrl, fileId)
                Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_PERMISSION_DENIED))
            } else if (http.code >= 500 || http.code == 408 || http.code == 429) {
                Result.retry()
            } else {
                finishQueueEntry(serverUrl, fileId)
                Result.failure(
                    workDataOf(
                        KEY_OUTCOME to OUTCOME_FAILED,
                        KEY_ERROR_MESSAGE to (http.message ?: "Download failed for $title.")
                    )
                )
            }
        } catch (io: IOException) {
            Result.retry()
        } catch (error: Throwable) {
            finishQueueEntry(serverUrl, fileId)
            Result.failure(
                workDataOf(
                    KEY_OUTCOME to OUTCOME_FAILED,
                    KEY_ERROR_MESSAGE to (error.message ?: "Download failed for $title.")
                )
            )
        } finally {
            clearDownloadNotification(applicationContext, fileId, id.toString())
        }
    }

    private suspend fun finishQueueEntry(serverUrl: String, fileId: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            DownloadStore(applicationContext).removeQueuedDownload(serverUrl, fileId)
            DownloadQueuePump.enqueueNext(applicationContext, serverUrl, ignoredWorkId = id)
        }
    }

    private fun foregroundInfo(context: Context, title: String, progressPercent: Int?): ForegroundInfo {
        ensureNotificationChannel(context)
        val fileId = inputData.getString(KEY_FILE_ID).orEmpty()
        val notification = buildDownloadNotification(
            context = context,
            title = title,
            fileId = fileId,
            serverUrl = inputData.getString(KEY_SERVER_URL).orEmpty(),
            progress = progressPercent?.div(100f)
        )
        refreshDownloadNotificationSummary(context)
        val notificationId = downloadNotificationId(fileId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DOWNLOAD_NOTIFICATION_CHANNEL_ID) != null) return
        runCatching { manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "Book downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while a book is downloading."
            }
        ) }
    }
}

private object DownloadQueuePump {
    private val mutex = Mutex()
    private val observersByServer = ConcurrentHashMap<String, (UUID) -> Unit>()

    fun registerObserver(serverUrl: String, observer: (UUID) -> Unit) {
        observersByServer[serverUrl] = observer
    }

    fun clearObservers() {
        observersByServer.clear()
    }

    suspend fun enqueueNext(
        context: Context,
        serverUrl: String,
        ignoredWorkId: UUID? = null
    ): UUID? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val workManager = WorkManager.getInstance(context)
            val hasActiveTransfer = runCatching {
                workManager.getWorkInfosByTag(downloadServerTag(serverUrl)).get()
            }.getOrDefault(emptyList()).any { info ->
                !info.state.isFinished && info.id != ignoredWorkId
            }
            if (hasActiveTransfer) return@withLock null

            val entry = DownloadStore(context).nextQueuedDownload(serverUrl) ?: run {
                observersByServer.remove(serverUrl)
                return@withLock null
            }
            val book = BookSummary(
                libraryId = entry.libraryId,
                id = entry.bookId,
                fileId = entry.fileId,
                title = entry.title,
                filename = entry.filename,
                format = entry.mimeType,
                mediaKind = entry.mediaKind,
                updatedAtMillis = entry.sourceUpdatedAtMillis
            )
            val request = downloadWorkRequest(
                serverUrl = serverUrl,
                book = book,
                fileId = entry.fileId,
                cellularConsentGranted = entry.cellularConsentGranted
            )
            workManager.enqueueUniqueWork(
                downloadUniqueWorkName(serverUrl, entry.fileId),
                ExistingWorkPolicy.KEEP,
                request
            )
            observersByServer[serverUrl]?.invoke(request.id)
            request.id
        }
    }
}

/**
 * [DownloadScheduler] backed by WorkManager. Downloads survive process death: `reconcile`
 * re-attaches observers to any work that is still enqueued/running so the UI can restore
 * `downloadingFileIds`/progress after the app reopens, instead of trusting an in-memory map.
 */
internal class WorkManagerDownloadScheduler(
    private val context: Context
) : DownloadScheduler {
    private val workManager get() = WorkManager.getInstance(context)
    private val downloadStore by lazy { DownloadStore(context) }
    private val sequence = AtomicLong()
    private val observerRegistry = DownloadObserverRegistry()
    private val callbacksByFileId = ConcurrentHashMap<String, DownloadCallbacks>()
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class DownloadCallbacks(
        val onProgress: (Float?) -> Unit,
        val onOutcome: suspend (DownloadOutcome) -> Unit
    )

    override fun start(
        scope: CoroutineScope,
        serverUrl: String,
        book: BookSummary,
        fileId: String,
        cellularConsentGranted: Boolean,
        onProgress: (Float?) -> Unit,
        onOutcome: suspend (DownloadOutcome) -> Unit
    ) {
        startBatch(
            scope = scope,
            serverUrl = serverUrl,
            downloads = listOf(ScheduledDownload(book, fileId)),
            cellularConsentGranted = cellularConsentGranted,
            onProgress = { _, progress -> onProgress(progress) },
            onOutcome = { _, outcome -> onOutcome(outcome) }
        )
    }

    override fun startBatch(
        scope: CoroutineScope,
        serverUrl: String,
        downloads: List<ScheduledDownload>,
        cellularConsentGranted: Boolean,
        onProgress: (String, Float?) -> Unit,
        onOutcome: suspend (String, DownloadOutcome) -> Unit
    ) {
        if (downloads.isEmpty()) return
        DownloadQueuePump.registerObserver(serverUrl) { workId ->
            scope.launch { observe(scope, serverUrl, workId) }
        }
        val queuedEntries = downloads.map { download ->
            val book = download.book
            val fileId = download.fileId
            callbacksByFileId[fileId] = DownloadCallbacks(
                onProgress = { progress -> onProgress(fileId, progress) },
                onOutcome = { outcome -> onOutcome(fileId, outcome) }
            )
            DownloadQueueEntry(
                serverUrl = serverUrl,
                fileId = fileId,
                bookId = book.id,
                libraryId = book.libraryId,
                title = book.title,
                filename = book.filename,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                cellularConsentGranted = cellularConsentGranted,
                sequence = sequence.updateAndGet { previous ->
                    nextDownloadQueueSequence(System.currentTimeMillis(), previous)
                }
            )
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    downloads.forEach { download ->
                        val book = download.book
                        val fileId = download.fileId
                        val existing = downloadStore.find(serverUrl, fileId)
                        val target = existing?.localPath?.let(::File)
                            ?: downloadStore.downloadTarget(fileId, book.title, book.mediaKind, book.format)
                        downloadStore.saveAttempt(
                            DownloadAttempt(
                                serverUrl = serverUrl,
                                fileId = fileId,
                                bookId = book.id,
                                title = book.title,
                                filename = book.filename,
                                targetPath = target.absolutePath,
                                existingLocalPath = existing?.localPath,
                                mediaKind = book.mediaKind,
                                mimeType = book.format,
                                sourceUpdatedAtMillis = book.updatedAtMillis
                            )
                        )
                    }
                    downloadStore.enqueueDownloads(queuedEntries)
                }
                val workId = DownloadQueuePump.enqueueNext(context, serverUrl)
                if (workId != null) {
                    observe(scope, serverUrl, workId)
                }
            } catch (error: Throwable) {
                downloads.forEach { download -> onOutcome(download.fileId, DownloadOutcome.Failed(error)) }
            }
        }
    }

    override fun cancel(serverUrl: String, fileId: String) {
        workManager.cancelUniqueWork(downloadUniqueWorkName(serverUrl, fileId))
        callbacksByFileId.remove(fileId)
        maintenanceScope.launch {
            downloadStore.removeQueuedDownload(serverUrl, fileId)
            DownloadQueuePump.enqueueNext(context, serverUrl)
        }
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(DOWNLOAD_TAG)
        callbacksByFileId.clear()
        DownloadQueuePump.clearObservers()
        maintenanceScope.launch {
            downloadStore.clearDownloadQueue()
            workManager.cancelAllWorkByTag(DOWNLOAD_TAG)
        }
    }

    override suspend fun reconcile(
        scope: CoroutineScope,
        serverUrl: String,
        bookForFileId: (String) -> BookSummary?,
        onProgress: (String, Float?) -> Unit,
        onOutcome: suspend (String, DownloadOutcome) -> Unit
    ): Map<String, BookSummary> {
        DownloadQueuePump.registerObserver(serverUrl) { workId ->
            scope.launch { observe(scope, serverUrl, workId) }
        }
        val (attemptsByFileId, queued) = withContext(Dispatchers.IO) {
            val attempts = runCatching {
                downloadStore.readAttempts(serverUrl).associateBy { it.fileId }
            }.getOrDefault(emptyMap())
            val queue = runCatching { downloadStore.readDownloadQueue(serverUrl) }
                .getOrDefault(emptyList())
            attempts to queue
        }
        val active = linkedMapOf<String, BookSummary>()
        queued.forEach { entry ->
            callbacksByFileId[entry.fileId] = DownloadCallbacks(
                onProgress = { progress -> onProgress(entry.fileId, progress) },
                onOutcome = { outcome -> onOutcome(entry.fileId, outcome) }
            )
            active[entry.fileId] = attemptsByFileId[entry.fileId]?.let(::downloadAttemptBookSummary)
                ?: BookSummary(
                    libraryId = entry.libraryId,
                    id = entry.bookId,
                    fileId = entry.fileId,
                    title = entry.title,
                    filename = entry.filename,
                    format = entry.mimeType,
                    mediaKind = entry.mediaKind,
                    updatedAtMillis = entry.sourceUpdatedAtMillis
                )
        }
        val startedWorkId = DownloadQueuePump.enqueueNext(context, serverUrl)
        val infos = withContext(Dispatchers.IO) {
            runCatching { workManager.getWorkInfosByTag(DOWNLOAD_TAG).get() }
                .getOrDefault(emptyList())
        }
        for (info in infos) {
            if (info.state.isFinished) continue
            if (downloadServerTag(serverUrl) !in info.tags) continue
            val fileId = info.tags.firstOrNull { it.startsWith(DOWNLOAD_FILE_TAG_PREFIX) }
                ?.removePrefix("$DOWNLOAD_FILE_TAG_PREFIX:")
                ?: continue
            active[fileId] = attemptsByFileId[fileId]?.let(::downloadAttemptBookSummary)
                ?: bookForFileId(fileId)
                ?: BookSummary(
                    libraryId = "",
                    id = "download-$fileId",
                    fileId = fileId,
                    title = "File $fileId"
                )
            callbacksByFileId[fileId] = DownloadCallbacks(
                onProgress = { progress -> onProgress(fileId, progress) },
                onOutcome = { outcome -> onOutcome(fileId, outcome) }
            )
            observe(scope = scope, serverUrl = serverUrl, workId = info.id)
        }
        if (startedWorkId != null) {
            observe(scope = scope, serverUrl = serverUrl, workId = startedWorkId)
        }
        return active
    }

    private fun observe(
        scope: CoroutineScope,
        serverUrl: String,
        workId: UUID,
    ) {
        if (!observerRegistry.register(workId)) return
        val liveData = workManager.getWorkInfoByIdLiveData(workId)
        lateinit var observer: Observer<WorkInfo>
        observer = object : Observer<WorkInfo> {
            override fun onChanged(info: WorkInfo) {
                scope.launch {
                    val fileId = info.tags.firstOrNull { it.startsWith(DOWNLOAD_FILE_TAG_PREFIX) }
                        ?.removePrefix("$DOWNLOAD_FILE_TAG_PREFIX:")
                    val callbacks = fileId?.let(callbacksByFileId::get)
                    if (callbacks != null) {
                        deliver(info, callbacks.onProgress, callbacks.onOutcome)
                    }
                    if (info.state.isFinished) {
                        liveData.removeObserver(observer)
                        observerRegistry.retire(workId)
                        fileId?.let(callbacksByFileId::remove)
                        observeActive(scope, serverUrl)
                    }
                }
            }
        }
        liveData.observeForever(observer)
    }

    private fun observeActive(scope: CoroutineScope, serverUrl: String) {
        scope.launch {
            val infos = withContext(Dispatchers.IO) {
                runCatching {
                    workManager.getWorkInfosByTag(downloadServerTag(serverUrl)).get()
                }.getOrDefault(emptyList())
            }
            infos.filter { !it.state.isFinished }.forEach { info ->
                observe(scope, serverUrl, info.id)
            }
        }
    }

    private suspend fun deliver(
        info: WorkInfo?,
        onProgress: (Float?) -> Unit,
        onOutcome: suspend (DownloadOutcome) -> Unit
    ) {
        if (info == null) return
        val rawProgress = info.progress.getFloat(KEY_PROGRESS, -2f)
        if (rawProgress != -2f) {
            onProgress(rawProgress.takeIf { it >= 0f })
        }
        when (info.state) {
            WorkInfo.State.SUCCEEDED -> {
                val localPath = info.outputData.getString(KEY_LOCAL_PATH)
                onOutcome(DownloadOutcome.Success(File(localPath.orEmpty())))
            }
            WorkInfo.State.CANCELLED -> onOutcome(DownloadOutcome.Canceled)
            WorkInfo.State.FAILED -> {
                when (info.outputData.getString(KEY_OUTCOME)) {
                    OUTCOME_AUTH_REQUIRED -> onOutcome(DownloadOutcome.AuthRequired)
                    OUTCOME_PERMISSION_DENIED -> onOutcome(DownloadOutcome.PermissionDenied)
                    else -> {
                        val message = info.outputData.getString(KEY_ERROR_MESSAGE)
                            ?: "Download failed."
                        onOutcome(DownloadOutcome.Failed(UserFacingException(message)))
                    }
                }
            }
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> Unit
        }
    }
}
