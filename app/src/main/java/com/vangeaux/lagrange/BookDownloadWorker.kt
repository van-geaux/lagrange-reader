package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.BackoffPolicy
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stable, per-account, per-file WorkManager name. Account switches cannot replace other work. */
internal fun downloadUniqueWorkName(
    serverUrl: String,
    storageScopeId: String,
    fileId: String
): String = "$DOWNLOAD_WORK_NAME_PREFIX:$serverUrl:$storageScopeId:$fileId"

internal fun downloadFileTag(fileId: String): String = "$DOWNLOAD_FILE_TAG_PREFIX:$fileId"

internal fun downloadServerTag(serverUrl: String): String = "$DOWNLOAD_SERVER_TAG_PREFIX:$serverUrl"

internal fun downloadRequestTag(requestId: String): String = "$DOWNLOAD_REQUEST_TAG_PREFIX:$requestId"

internal fun downloadStorageScopeTag(storageScopeId: String): String =
    "$DOWNLOAD_STORAGE_SCOPE_TAG_PREFIX:$storageScopeId"

internal fun downloadPolicyGenerationTag(generation: Long): String =
    "$DOWNLOAD_POLICY_GENERATION_TAG_PREFIX:$generation"

internal fun downloadWorkTagsMatchRequest(tags: Set<String>, requestId: String): Boolean =
    if (requestId.isBlank()) {
        tags.none { it.startsWith("$DOWNLOAD_REQUEST_TAG_PREFIX:") }
    } else {
        downloadRequestTag(requestId) in tags
    }

internal fun downloadRequestIdFromTags(tags: Set<String>): String? = tags
    .firstOrNull { it.startsWith("$DOWNLOAD_REQUEST_TAG_PREFIX:") }
    ?.removePrefix("$DOWNLOAD_REQUEST_TAG_PREFIX:")

internal fun downloadFileIdFromTags(tags: Set<String>): String? = tags
    .firstOrNull { it.startsWith("$DOWNLOAD_FILE_TAG_PREFIX:") }
    ?.removePrefix("$DOWNLOAD_FILE_TAG_PREFIX:")

internal fun downloadStorageScopeIdFromTags(tags: Set<String>): String? = tags
    .firstOrNull { it.startsWith("$DOWNLOAD_STORAGE_SCOPE_TAG_PREFIX:") }
    ?.removePrefix("$DOWNLOAD_STORAGE_SCOPE_TAG_PREFIX:")
    ?.takeIf { it.isNotBlank() }

internal fun downloadWorkTagsOwnQueuedRequest(
    tags: Set<String>,
    queuedRequestIdsByFile: Map<String, String>
): Boolean {
    val fileId = downloadFileIdFromTags(tags) ?: return false
    val requestId = queuedRequestIdsByFile[fileId] ?: return false
    return downloadWorkTagsMatchRequest(tags, requestId)
}

internal fun downloadPolicyGenerationFromTags(tags: Set<String>): Long? = tags
    .firstOrNull { it.startsWith("$DOWNLOAD_POLICY_GENERATION_TAG_PREFIX:") }
    ?.removePrefix("$DOWNLOAD_POLICY_GENERATION_TAG_PREFIX:")
    ?.toLongOrNull()

internal fun downloadWorkMayBeCancelledThroughGeneration(
    tags: Set<String>,
    maximumGeneration: Long
): Boolean = downloadPolicyGenerationFromTags(tags)?.let { it <= maximumGeneration } ?: true

internal fun nextDownloadQueueSequence(nowMillis: Long, previous: Long): Long =
    maxOf(nowMillis * 1_000L, previous + 1L)

internal fun shouldEnforcePostDownloadCapacity(
    origin: DownloadOrigin,
    workerPolicyGeneration: Long?,
    currentPolicy: AutomaticDownloadPolicy
): Boolean {
    if (currentPolicy.maximumBytes <= 0L && currentPolicy.reserveBytes <= 0L) return false
    return when (origin) {
        DownloadOrigin.AUTOMATIC -> currentPolicy.enabled &&
            workerPolicyGeneration == currentPolicy.generation
        DownloadOrigin.MANUAL -> currentPolicy.automaticRemovalEnabled
    }
}

internal fun downloadExistingWorkPolicy(
    origin: DownloadOrigin,
    replacingRetiringOwner: Boolean
): ExistingWorkPolicy = if (origin == DownloadOrigin.MANUAL || replacingRetiringOwner) {
    ExistingWorkPolicy.REPLACE
} else {
    ExistingWorkPolicy.KEEP
}

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
    libraryId = attempt.libraryId,
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
private const val DOWNLOAD_REQUEST_TAG_PREFIX = "bookorbit-download-request"
private const val DOWNLOAD_STORAGE_SCOPE_TAG_PREFIX = "bookorbit-download-storage-scope"
private const val DOWNLOAD_POLICY_GENERATION_TAG_PREFIX = "bookorbit-download-policy-generation"
internal const val DOWNLOAD_TAG = "bookorbit-download-all"
internal const val AUTOMATIC_DOWNLOAD_TAG = "bookorbit-download-automatic"

private const val KEY_SERVER_URL = "server-url"
private const val KEY_REQUEST_ID = "request-id"
private const val KEY_PROFILE_ID = "profile-id"
private const val KEY_STORAGE_SCOPE_ID = "storage-scope-id"
private const val KEY_FILE_ID = "file-id"
private const val KEY_BOOK_ID = "book-id"
private const val KEY_LIBRARY_ID = "library-id"
private const val KEY_TITLE = "title"
private const val KEY_FILENAME = "filename"
private const val KEY_MEDIA_KIND = "media-kind"
private const val KEY_FORMAT = "format"
private const val KEY_UPDATED_AT = "updated-at"
private const val KEY_EXPECTED_SIZE = "expected-size"
private const val KEY_DOWNLOAD_ORIGIN = "download-origin"
private const val KEY_REQUIRES_UNMETERED = "requires-unmetered"
private const val KEY_REQUIRES_CHARGING = "requires-charging"
private const val KEY_POLICY_GENERATION = "policy-generation"
private const val KEY_CELLULAR_CONSENT_GRANTED = "cellular-consent-granted"
internal const val KEY_PROGRESS = "progress"
internal const val KEY_OUTCOME = "outcome"
internal const val KEY_ERROR_MESSAGE = "error-message"
internal const val KEY_LOCAL_PATH = "local-path"

internal const val OUTCOME_AUTH_REQUIRED = "auth_required"
internal const val OUTCOME_POLICY_BLOCKED = "policy_blocked"
internal const val OUTCOME_PERMISSION_DENIED = "permission_denied"
internal const val OUTCOME_FAILED = "failed"

internal fun isRetryableDownloadHttpCode(code: Int): Boolean =
    code >= 500 || code == 408 || code == 429

/**
 * Builds the input [androidx.work.Data] and [OneTimeWorkRequest] for downloading a single book.
 * Kept separate from enqueueing so it can be unit tested without touching a real WorkManager.
 */
internal fun downloadWorkRequest(
    serverUrl: String,
    requestId: String = "",
    profileId: String = "",
    storageScopeId: String = "",
    book: BookSummary,
    fileId: String,
    cellularConsentGranted: Boolean,
    expectedSizeBytes: Long? = null,
    origin: DownloadOrigin = DownloadOrigin.MANUAL,
    requiresUnmeteredNetwork: Boolean = false,
    requiresCharging: Boolean = false,
    policyGeneration: Long? = null
): OneTimeWorkRequest {
    require(storageScopeId.isNotBlank()) { "A stable download account scope is required." }
    val builder = OneTimeWorkRequestBuilder<BookDownloadWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (requiresUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresCharging(requiresCharging)
                .build()
        )
        .setInputData(
            workDataOf(
                KEY_SERVER_URL to serverUrl,
                KEY_REQUEST_ID to requestId,
                KEY_PROFILE_ID to profileId,
                KEY_STORAGE_SCOPE_ID to storageScopeId,
                KEY_FILE_ID to fileId,
                KEY_BOOK_ID to book.id,
                KEY_LIBRARY_ID to book.libraryId,
                KEY_TITLE to book.title,
                KEY_FILENAME to book.filename,
                KEY_MEDIA_KIND to book.mediaKind.name,
                KEY_FORMAT to book.format,
                KEY_UPDATED_AT to (book.updatedAtMillis ?: -1L),
                KEY_EXPECTED_SIZE to (expectedSizeBytes ?: -1L),
                KEY_DOWNLOAD_ORIGIN to origin.name,
                KEY_REQUIRES_UNMETERED to requiresUnmeteredNetwork,
                KEY_REQUIRES_CHARGING to requiresCharging,
                KEY_POLICY_GENERATION to (policyGeneration ?: -1L),
                KEY_CELLULAR_CONSENT_GRANTED to cellularConsentGranted
            )
        )
        .addTag(DOWNLOAD_TAG)
        .addTag(
            if (origin == DownloadOrigin.AUTOMATIC) {
                AUTOMATIC_DOWNLOAD_TAG
            } else {
                "$AUTOMATIC_DOWNLOAD_TAG-manual"
            }
        )
        .addTag(downloadFileTag(fileId))
        .addTag(downloadServerTag(serverUrl))
        .addTag(downloadStorageScopeTag(storageScopeId))
    if (requestId.isNotBlank()) builder.addTag(downloadRequestTag(requestId))
    if (policyGeneration != null) {
        builder.addTag(downloadPolicyGenerationTag(policyGeneration))
    }
    return builder.build()
}

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
        val requestId = inputData.getString(KEY_REQUEST_ID).orEmpty()
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val storageScopeId = inputData.getString(KEY_STORAGE_SCOPE_ID).orEmpty()
        val fileId = inputData.getString(KEY_FILE_ID)
        val bookId = inputData.getString(KEY_BOOK_ID)
        val title = inputData.getString(KEY_TITLE)
        if (serverUrl.isNullOrBlank() || profileId.isBlank() || storageScopeId.isBlank() ||
            fileId.isNullOrBlank() || bookId.isNullOrBlank() || title.isNullOrBlank()
        ) {
            return Result.failure()
        }
        if (downloadStorageScopeId(applicationContext, serverUrl, profileId) != storageScopeId) {
            return Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_AUTH_REQUIRED))
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
        val origin = runCatching {
            DownloadOrigin.valueOf(
                inputData.getString(KEY_DOWNLOAD_ORIGIN) ?: DownloadOrigin.MANUAL.name
            )
        }.getOrDefault(DownloadOrigin.MANUAL)
        val expectedSizeBytes = inputData.getLong(KEY_EXPECTED_SIZE, -1L).takeIf { it >= 0L }
        val policyGeneration = inputData.getLong(KEY_POLICY_GENERATION, -1L).takeIf { it >= 0L }

        val workerDownloadStore = DownloadStore(applicationContext)
        reconcileVerifiedCommitsWhenReaderInactive(
            applicationContext,
            workerDownloadStore,
            serverUrl,
            storageScopeId
        )
        val claimedAttempt = workerDownloadStore.claimQueuedDownload(
            serverUrl,
            fileId,
            requestId,
            storageScopeId
        )
        if (claimedAttempt == null) {
            finishQueueEntry(
                serverUrl,
                fileId,
                requestId,
                storageScopeId = storageScopeId,
                replaceRetiringFileId = fileId
            )
            return Result.success()
        }
        if (claimedAttempt.storageScopeId != storageScopeId) {
            return Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_AUTH_REQUIRED))
        }
        if (claimedAttempt.state == DownloadAttemptState.VERIFIED ||
            claimedAttempt.state == DownloadAttemptState.COMMITTING
        ) {
            return Result.retry()
        }

        if (origin == DownloadOrigin.AUTOMATIC) {
            val policy = AutomaticDownloadPolicyStore(applicationContext)
                .read(profileId, serverUrl, storageScopeId)
            val activeProfile = ServerProfileStore(applicationContext).active()
            if (!policy.enabled || policy.generation != policyGeneration ||
                activeProfile?.id != profileId ||
                !serverUrlsMatch(activeProfile.serverUrl, serverUrl)
            ) {
                finishQueueEntry(serverUrl, fileId, requestId)
                return Result.success()
            }
        }

        return try {
            setForeground(foregroundInfo(applicationContext, title, progressPercent = null))

            val repository = resolveProviderRepository(applicationContext, serverUrl)
            val downloadModule = resolveProviderDownloadModule(repository)
            val progressThrottler = DownloadProgressThrottler(minIntervalMillis = 500L)

        // The session may have changed servers between enqueue and execution.
        if (!serverUrlsMatch(repository.getServerUrl().orEmpty(), serverUrl)) {
            finishQueueEntry(serverUrl, fileId, requestId)
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
            finishQueueEntry(serverUrl, fileId, requestId)
            return Result.failure(
                workDataOf(
                    KEY_OUTCOME to OUTCOME_POLICY_BLOCKED,
                    KEY_ERROR_MESSAGE to "Cellular download policy requires confirmation on the current network."
                )
            )
        }


        if (expectedSizeBytes != null && automaticStorageLimitsApply(origin)) {
            val policy = AutomaticDownloadPolicyStore(applicationContext)
                .read(profileId, serverUrl, storageScopeId)
            if (downloadIsIndividuallyOverCap(expectedSizeBytes, origin, policy)) {
                suppressStorageBlocked(
                    origin, serverUrl, profileId, storageScopeId, fileId, book.updatedAtMillis
                )
                finishQueueEntry(serverUrl, fileId, requestId)
                return Result.failure(
                    workDataOf(
                        KEY_OUTCOME to OUTCOME_POLICY_BLOCKED,
                        KEY_ERROR_MESSAGE to "This file is larger than the maximum local-book storage limit."
                    )
                )
            }
            val existingBytes = DownloadStore(applicationContext).find(
                serverUrl,
                fileId,
                storageScopeId
            )
                ?.localPath
                ?.let(::File)
                ?.length()
                ?.coerceAtLeast(0L)
                ?: 0L
            val capacity = AutomaticDownloadStorage.ensureCapacity(
                context = applicationContext,
                serverUrl = serverUrl,
                profileId = profileId,
                policy = policy,
                incomingFinalBytes = (expectedSizeBytes - existingBytes).coerceAtLeast(0L),
                incomingPeakBytes = expectedSizeBytes,
                protectedFileIds = setOf(fileId),
                allowRemoval = true
            )
            if (!capacity.allowed) {
                suppressStorageBlocked(
                    origin, serverUrl, profileId, storageScopeId, fileId, book.updatedAtMillis
                )
                finishQueueEntry(serverUrl, fileId, requestId)
                return Result.failure(
                    workDataOf(
                        KEY_OUTCOME to OUTCOME_POLICY_BLOCKED,
                        KEY_ERROR_MESSAGE to (capacity.message ?: "The storage limit would be exceeded.")
                    )
                )
            }
        }

            coroutineScope {
                val localFile = physicalDownloadGate.withPermit {
                    downloadModule.downloadBook(book, storageScopeId) { progress ->
                        if (!progressThrottler.shouldEmit(progress, System.currentTimeMillis())) {
                            return@downloadBook
                        }
                        updateDownloadNotification(
                            context = applicationContext,
                            title = title,
                            fileId = fileId,
                            serverUrl = serverUrl,
                            storageScopeId = storageScopeId,
                            progress = progress
                        )
                        launch {
                            val percent = progress?.let { (it * 100f).toInt().coerceIn(0, 100) }
                            setProgress(workDataOf(KEY_PROGRESS to (progress ?: -1f)))
                            runCatching { setForeground(foregroundInfo(applicationContext, title, percent)) }
                        }
                    }
                }
                val cleanupPolicy = AutomaticDownloadPolicyStore(applicationContext)
                    .read(profileId, serverUrl, storageScopeId)
                if (shouldEnforcePostDownloadCapacity(origin, policyGeneration, cleanupPolicy)) {
                    val postDownloadCapacity = AutomaticDownloadStorage.ensureCapacity(
                        context = applicationContext,
                        serverUrl = serverUrl,
                        profileId = profileId,
                        policy = cleanupPolicy,
                        incomingFinalBytes = 0L,
                        incomingPeakBytes = 0L,
                        protectedFileIds = setOf(fileId),
                        allowRemoval = cleanupPolicy.automaticRemovalEnabled
                    )
                    if (!postDownloadCapacity.allowed && origin == DownloadOrigin.AUTOMATIC) {
                        suppressStorageBlocked(
                            origin,
                            serverUrl,
                            profileId,
                            storageScopeId,
                            fileId,
                            book.updatedAtMillis
                        )
                        removeCompletedDownloadAfterLimit(
                            serverUrl,
                            book,
                            fileId,
                            requestId,
                            storageScopeId
                        )
                        finishQueueEntry(serverUrl, fileId, requestId)
                        return@coroutineScope Result.failure(
                            workDataOf(
                                KEY_OUTCOME to OUTCOME_POLICY_BLOCKED,
                                KEY_ERROR_MESSAGE to (
                                    postDownloadCapacity.message
                                        ?: "The completed file could not fit within the storage limits."
                                    )
                            )
                        )
                    }
                }
                finishQueueEntry(
                    serverUrl,
                    fileId,
                    requestId,
                    successfulCompletion = true,
                    continueAutomatic = origin == DownloadOrigin.AUTOMATIC
                )
                Result.success(workDataOf(KEY_LOCAL_PATH to localFile.absolutePath))
            }
        } catch (cancellation: CancellationException) {
            // Cancellation retires this WorkSpec, not necessarily the durable logical request.
            // REPLACE installs a successor carrying the same request ID, while explicit user and
            // policy cancellation paths remove queue ownership themselves before pumping again.
            throw cancellation
        } catch (auth: AuthenticationRequiredException) {
            if (origin == DownloadOrigin.AUTOMATIC) {
                AutomaticDownloadStatusStore(applicationContext).write(
                    profileId,
                    storageScopeId,
                    AutomaticDownloadStatus(
                        state = AutomaticDownloadRunState.PAUSED,
                        message = "Automatic downloads are paused until you sign in again."
                    )
                )
            }
            // Keep the durable queue owner. A successful sign-in can pump the same request again
            // without losing its scan position or treating an authentication lapse as completion.
            Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_AUTH_REQUIRED))
        } catch (_: DownloadReplacementDeferredException) {
            // A verified stage is intentionally retained. This retry is not failure exhaustion:
            // the next run publishes it after the Activity/TTS lease has been released.
            Result.retry()
        } catch (io: UnknownHostException) {
            retryOrStopAutomatic(origin, serverUrl, fileId, io.message)
        } catch (io: SocketTimeoutException) {
            retryOrStopAutomatic(origin, serverUrl, fileId, io.message)
        } catch (io: SSLException) {
            retryOrStopAutomatic(origin, serverUrl, fileId, io.message)
        } catch (http: HttpRequestException) {
            if (http.code == 403) {
                finishQueueEntry(serverUrl, fileId, requestId)
                Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_PERMISSION_DENIED))
            } else if (isRetryableDownloadHttpCode(http.code)) {
                retryOrStopAutomatic(origin, serverUrl, fileId, http.message)
            } else {
                finishQueueEntry(serverUrl, fileId, requestId)
                Result.failure(
                    workDataOf(
                        KEY_OUTCOME to OUTCOME_FAILED,
                        KEY_ERROR_MESSAGE to (http.message ?: "Download failed for $title.")
                    )
                )
            }
        } catch (io: IOException) {
            retryOrStopAutomatic(origin, serverUrl, fileId, io.message)
        } catch (changed: LocalBookStoragePolicyChangedException) {
            retryOrStopAutomatic(origin, serverUrl, fileId, changed.message)
        } catch (storage: LocalBookStorageLimitException) {
            suppressStorageBlocked(
                origin, serverUrl, profileId, storageScopeId, fileId, book.updatedAtMillis
            )
            finishQueueEntry(serverUrl, fileId, requestId)
            Result.failure(
                workDataOf(
                    KEY_OUTCOME to OUTCOME_POLICY_BLOCKED,
                    KEY_ERROR_MESSAGE to storage.message
                )
            )
        } catch (error: Throwable) {
            finishQueueEntry(serverUrl, fileId, requestId)
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

    private suspend fun finishQueueEntry(
        serverUrl: String,
        fileId: String,
        requestId: String,
        successfulCompletion: Boolean = false,
        continueAutomatic: Boolean = false,
        storageScopeId: String = inputData.getString(KEY_STORAGE_SCOPE_ID).orEmpty(),
        replaceRetiringFileId: String? = null
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            val next = DownloadQueuePump.finishAndEnqueueNext(
                context = applicationContext,
                serverUrl = serverUrl,
                fileId = fileId,
                requestId = requestId,
                storageScopeId = storageScopeId,
                successfulCompletion = successfulCompletion,
                ignoredWorkId = id,
                replaceRetiringFileId = replaceRetiringFileId
            )
            if (continueAutomatic && next == null) {
                val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
                val generation = inputData.getLong(KEY_POLICY_GENERATION, -1L)
                val policy = AutomaticDownloadPolicyStore(applicationContext)
                    .read(profileId, serverUrl, storageScopeId)
                if (policy.enabled && policy.generation == generation) {
                    AutomaticDownloadScheduler.enqueueContinuation(applicationContext, policy)
                }
            }
        }
    }

    private fun suppressStorageBlocked(
        origin: DownloadOrigin,
        serverUrl: String,
        profileId: String,
        storageScopeId: String,
        fileId: String,
        sourceRevision: Long?
    ) {
        if (origin != DownloadOrigin.AUTOMATIC) return
        val generation = LocalBookStoragePolicyStore(applicationContext).read().generation
        AutomaticDownloadSuppressionStore(applicationContext).suppress(
            serverUrl = serverUrl,
            profileId = profileId,
            fileId = fileId,
            sourceRevision = sourceRevision,
            reason = AutomaticDownloadSuppressionReason.STORAGE_BLOCKED,
            storageGeneration = generation,
            storageScopeId = storageScopeId
        )
    }

    private suspend fun removeCompletedDownloadAfterLimit(
        serverUrl: String,
        book: BookSummary,
        fileId: String,
        requestId: String,
        storageScopeId: String
    ) = withContext(NonCancellable + Dispatchers.IO) {
        val store = DownloadStore(applicationContext)
        if (!store.deleteCompletedDownloadIfOwned(
                serverUrl,
                fileId,
                requestId,
                storageScopeId
            )
        ) {
            return@withContext
        }
        BookDetailCacheStore(applicationContext).remove(serverUrl, book.id, fileId)
        val replacement = store.readAll(serverUrl, storageScopeId)
            .firstOrNull { it.bookId == book.id && it.status == DownloadRecordStatus.COMPLETE }
            ?.localPath
        LibraryCatalogStore(applicationContext).updateLocalPath(serverUrl, book.id, replacement)
        BrowserSnapshotStore(applicationContext).updateLocalPath(serverUrl, book.id, replacement)
    }

    private suspend fun retryOrStopAutomatic(
        origin: DownloadOrigin,
        serverUrl: String,
        fileId: String,
        message: String?
    ): Result {
        if (runAttemptCount < 4) return Result.retry()
        if (origin == DownloadOrigin.AUTOMATIC) {
            val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
            val storageScopeId = inputData.getString(KEY_STORAGE_SCOPE_ID).orEmpty()
            val sourceRevision = inputData.getLong(KEY_UPDATED_AT, -1L).takeIf { it >= 0L }
            val generation = LocalBookStoragePolicyStore(applicationContext).read().generation
            AutomaticDownloadSuppressionStore(applicationContext).suppress(
                serverUrl = serverUrl,
                profileId = profileId,
                fileId = fileId,
                sourceRevision = sourceRevision,
                reason = AutomaticDownloadSuppressionReason.RETRY_EXHAUSTED,
                storageGeneration = generation,
                retryAfterMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24),
                storageScopeId = storageScopeId
            )
        }
        withContext(NonCancellable) {
            finishQueueEntry(
                serverUrl,
                fileId,
                inputData.getString(KEY_REQUEST_ID).orEmpty()
            )
        }
        return Result.failure(
            workDataOf(
                KEY_OUTCOME to OUTCOME_FAILED,
                KEY_ERROR_MESSAGE to (message ?: "Download stopped after five attempts.")
            )
        )
    }

    private fun foregroundInfo(context: Context, title: String, progressPercent: Int?): ForegroundInfo {
        ensureNotificationChannel(context)
        val fileId = inputData.getString(KEY_FILE_ID).orEmpty()
        val notification = buildDownloadNotification(
            context = context,
            title = title,
            fileId = fileId,
            serverUrl = inputData.getString(KEY_SERVER_URL).orEmpty(),
            storageScopeId = inputData.getString(KEY_STORAGE_SCOPE_ID).orEmpty(),
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

internal object DownloadQueuePump {
    private val mutex = Mutex()
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observersByScope = ConcurrentHashMap<String, (UUID) -> Unit>()

    private fun observerKey(serverUrl: String, storageScopeId: String): String =
        "$serverUrl\u0000$storageScopeId"

    fun registerObserver(
        serverUrl: String,
        storageScopeId: String,
        observer: (UUID) -> Unit
    ) {
        observersByScope[observerKey(serverUrl, storageScopeId)] = observer
    }

    fun clearObservers() {
        observersByScope.clear()
    }

    fun wakeVerifiedReplacements(
        context: Context,
        releasedLeases: List<LocalBookReaderLease>
    ) {
        recoveryScope.launch {
            val store = DownloadStore(context)
            val targets = verifiedReplacementWakeTargets(store.readAttempts(), releasedLeases)
            targets.forEach { target ->
                try {
                    mutex.withLock {
                        if (downloadStorageScopeId(context, target.serverUrl) != target.storageScopeId) {
                            return@withLock
                        }
                        val workManager = WorkManager.getInstance(context)
                        workManager.cancelUniqueWork(
                            downloadUniqueWorkName(
                                target.serverUrl,
                                target.storageScopeId,
                                target.fileId
                            )
                        ).result.get()
                        enqueueNextLocked(
                            context = context,
                            serverUrl = target.serverUrl,
                            storageScopeId = target.storageScopeId,
                            ignoredWorkId = null,
                            replaceRetiringFileId = target.fileId
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    delay(1_000L)
                    enqueueNext(context, target.serverUrl, target.storageScopeId)
                }
            }
        }
    }

    suspend fun enqueueNext(
        context: Context,
        serverUrl: String,
        storageScopeId: String? = null,
        ignoredWorkId: UUID? = null,
        replaceRetiringFileId: String? = null
    ): UUID? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val resolvedScope = storageScopeId
                ?: downloadStorageScopeId(context, serverUrl)
                ?: return@withLock null
            if (downloadStorageScopeId(context, serverUrl) != resolvedScope) {
                return@withLock null
            }
            enqueueNextLocked(
                context = context,
                serverUrl = serverUrl,
                storageScopeId = resolvedScope,
                ignoredWorkId = ignoredWorkId,
                replaceRetiringFileId = replaceRetiringFileId
            )
        }
    }

    suspend fun finishAndEnqueueNext(
        context: Context,
        serverUrl: String,
        fileId: String,
        requestId: String,
        storageScopeId: String,
        successfulCompletion: Boolean,
        ignoredWorkId: UUID,
        replaceRetiringFileId: String? = null
    ): UUID? = withContext(Dispatchers.IO) {
        mutex.withLock {
            DownloadStore(context).finishDownloadRequest(
                serverUrl = serverUrl,
                fileId = fileId,
                requestId = requestId,
                successfulCompletion = successfulCompletion,
                storageScopeId = storageScopeId
            )
            if (downloadStorageScopeId(context, serverUrl) == storageScopeId) {
                enqueueNextLocked(
                    context = context,
                    serverUrl = serverUrl,
                    storageScopeId = storageScopeId,
                    ignoredWorkId = ignoredWorkId,
                    replaceRetiringFileId = replaceRetiringFileId
                )
            } else {
                null
            }
        }
    }

    private suspend fun enqueueNextLocked(
        context: Context,
        serverUrl: String,
        storageScopeId: String,
        ignoredWorkId: UUID?,
        replaceRetiringFileId: String?
    ): UUID? {
        val workManager = WorkManager.getInstance(context)
        val store = DownloadStore(context)
        reconcileVerifiedCommitsWhenReaderInactive(
            context,
            store,
            serverUrl,
            storageScopeId
        )
        val queuedEntries = store.readDownloadQueue(serverUrl, storageScopeId)
        val queuedRequestIdsByFile = queuedEntries.associate { it.fileId to it.requestId }
        val workInfos = runCatching {
            workManager.getWorkInfosByTag(downloadServerTag(serverUrl)).get()
        }.getOrElse {
            recoveryScope.launch {
                delay(1_000L)
                enqueueNext(context.applicationContext, serverUrl, storageScopeId)
            }
            return null
        }
        val activeTransfers = workInfos.filter { info ->
            !info.state.isFinished && info.id != ignoredWorkId &&
                downloadStorageScopeIdFromTags(info.tags) == storageScopeId
        }
        val staleTransfers = activeTransfers.filterNot { info ->
            downloadWorkTagsOwnQueuedRequest(info.tags, queuedRequestIdsByFile)
        }
        staleTransfers.forEach { info ->
            workManager.cancelWorkById(info.id)
            val staleFileId = downloadFileIdFromTags(info.tags)
            if (staleFileId != null) {
                store.removeAttemptIfOwned(
                    serverUrl,
                    staleFileId,
                    downloadRequestIdFromTags(info.tags).orEmpty(),
                    storageScopeId = storageScopeId
                )
            }
        }
        val ownedTransfers = activeTransfers - staleTransfers.toSet()
        val entry = queuedEntries.minWithOrNull(
            compareBy<DownloadQueueEntry> { it.origin == DownloadOrigin.AUTOMATIC }
                .thenBy { it.sequence }
        ) ?: run {
            observersByScope.remove(observerKey(serverUrl, storageScopeId))
            return null
        }
        if (ownedTransfers.isNotEmpty()) {
            val preemptible = if (entry.origin == DownloadOrigin.MANUAL) {
                ownedTransfers.filter { info ->
                    AUTOMATIC_DOWNLOAD_TAG in info.tags &&
                        (info.state == WorkInfo.State.ENQUEUED ||
                            info.state == WorkInfo.State.BLOCKED)
                }
            } else {
                emptyList()
            }
            if (preemptible.isEmpty() || preemptible.size != ownedTransfers.size) {
                return null
            }
            preemptible.forEach { workManager.cancelWorkById(it.id) }
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
            requestId = entry.requestId,
            profileId = entry.profileId,
            storageScopeId = entry.storageScopeId,
            book = book,
            fileId = entry.fileId,
            cellularConsentGranted = entry.cellularConsentGranted,
            expectedSizeBytes = entry.expectedSizeBytes,
            origin = entry.origin,
            requiresUnmeteredNetwork = entry.requiresUnmeteredNetwork,
            requiresCharging = entry.requiresCharging,
            policyGeneration = entry.policyGeneration
        )
        val enqueue = workManager.enqueueUniqueWork(
                downloadUniqueWorkName(serverUrl, storageScopeId, entry.fileId),
                downloadExistingWorkPolicy(
                    entry.origin,
                    replaceRetiringFileId == entry.fileId || staleTransfers.any { info ->
                        downloadFileIdFromTags(info.tags) == entry.fileId
                    }
                ),
                request
            )
        val inserted = runCatching { enqueue.result.get() }.isSuccess
        if (!inserted) {
            recoveryScope.launch {
                delay(1_000L)
                enqueueNext(context.applicationContext, serverUrl, storageScopeId)
            }
            return null
        }
        observersByScope[observerKey(serverUrl, storageScopeId)]?.invoke(request.id)
        return request.id
    }
}

internal data class VerifiedReplacementWakeTarget(
    val serverUrl: String,
    val storageScopeId: String,
    val fileId: String
)

internal fun verifiedReplacementWakeTargets(
    attempts: List<DownloadAttempt>,
    releasedLeases: List<LocalBookReaderLease>
): Set<VerifiedReplacementWakeTarget> = attempts.asSequence()
    .filter {
        it.state == DownloadAttemptState.VERIFIED ||
            it.state == DownloadAttemptState.COMMITTING
    }
    .filter { attempt ->
        releasedLeases.any { lease ->
            localReaderLeaseBlocksReplacement(
                lease = lease,
                serverUrl = attempt.serverUrl,
                storageScopeId = attempt.storageScopeId,
                fileId = attempt.fileId,
                targetPath = attempt.targetPath
            )
        }
    }
    .map { VerifiedReplacementWakeTarget(it.serverUrl, it.storageScopeId, it.fileId) }
    .toSet()

internal suspend fun enqueueAutomaticDownloadBatch(
    context: Context,
    profileId: String,
    serverUrl: String,
    policy: AutomaticDownloadPolicy,
    candidates: List<AutomaticDownloadCandidate>
): Set<String> = withContext(Dispatchers.IO) {
    val store = DownloadStore(context)
    val queued = store.readDownloadQueue(serverUrl, policy.storageScopeId)
    val queuedIds = queued.mapTo(mutableSetOf()) { it.fileId }
    val remaining = (5 - queued.count { it.origin == DownloadOrigin.AUTOMATIC })
        .coerceAtLeast(0)
    if (remaining == 0) return@withContext emptySet()
    var previousSequence = queued.maxOfOrNull { it.sequence } ?: 0L
    val accepted = candidates
        .filter { it.book.fileId != null && it.book.fileId !in queuedIds }
        .distinctBy { it.book.fileId }
        .take(remaining)
    val requests = accepted.map { candidate ->
        val book = candidate.book
        val fileId = requireNotNull(book.fileId)
        val requestId = UUID.randomUUID().toString()
        val existing = store.find(serverUrl, fileId, policy.storageScopeId)
        val target = store.ownedTargetOrIsolated(
            serverUrl = serverUrl,
            fileId = fileId,
            title = book.title,
            mediaKind = book.mediaKind,
            formatHint = book.format,
            recordedPath = existing?.localPath,
            storageScopeId = policy.storageScopeId
        )
        val attempt = DownloadAttempt(
                serverUrl = serverUrl,
                requestId = requestId,
                profileId = profileId,
                storageScopeId = policy.storageScopeId,
                fileId = fileId,
                bookId = book.id,
                libraryId = book.libraryId,
                title = book.title,
                filename = book.filename,
                targetPath = target.absolutePath,
                existingLocalPath = existing?.localPath,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                expectedSizeBytes = candidate.expectedSizeBytes,
                origin = DownloadOrigin.AUTOMATIC,
                policyGeneration = policy.generation
            )
        previousSequence = nextDownloadQueueSequence(System.currentTimeMillis(), previousSequence)
        val entry = DownloadQueueEntry(
            serverUrl = serverUrl,
            requestId = requestId,
            profileId = profileId,
            storageScopeId = policy.storageScopeId,
            fileId = fileId,
            bookId = book.id,
            libraryId = book.libraryId,
            title = book.title,
            filename = book.filename,
            mediaKind = book.mediaKind,
            mimeType = book.format,
            sourceUpdatedAtMillis = book.updatedAtMillis,
            expectedSizeBytes = candidate.expectedSizeBytes,
            origin = DownloadOrigin.AUTOMATIC,
            requiresUnmeteredNetwork = policy.unmeteredOnly,
            requiresCharging = policy.chargingRequired,
            policyGeneration = policy.generation,
            cellularConsentGranted = !policy.unmeteredOnly,
            sequence = previousSequence
        )
        entry to attempt
    }

    val entries = store.enqueueDownloadsWithAttempts(
        requests,
        maximumAutomaticEntriesPerServer = 5
    )
    DownloadQueuePump.enqueueNext(context, serverUrl, policy.storageScopeId)
    entries.mapTo(mutableSetOf()) { it.fileId }
}

internal class DownloadRequestCancellationGate {
    private val mutationMutex = Mutex()
    private val cancelledRequestIds = ConcurrentHashMap.newKeySet<String>()

    fun cancel(requestIds: Collection<String>) {
        cancelledRequestIds.addAll(requestIds)
    }

    fun consumeCancellation(requestId: String): Boolean = cancelledRequestIds.remove(requestId)

    suspend fun <T> serializeMutation(block: suspend () -> T): T = mutationMutex.withLock {
        block()
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
    private val callbacksByDownload = ConcurrentHashMap<DownloadCallbackKey, DownloadCallbacks>()
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancellationGate = DownloadRequestCancellationGate()

    private data class DownloadCallbacks(
        val onProgress: (Float?) -> Unit,
        val onOutcome: suspend (DownloadOutcome) -> Unit
    )

    private data class CancellationOwner(
        val serverUrl: String,
        val storageScopeId: String,
        val fileId: String,
        val requestId: String,
        val workId: UUID? = null
    )

    internal data class DownloadCallbackKey(
        val serverUrl: String,
        val storageScopeId: String,
        val fileId: String,
        val requestId: String
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
        val activeProfile = ServerProfileStore(context).active()
        val activeProfileId = activeProfile?.id.orEmpty()
        val storageScopeId = downloadStorageScopeId(context, serverUrl, activeProfileId)
        if (activeProfile == null || !serverUrlsMatch(activeProfile.serverUrl, serverUrl) ||
            storageScopeId == null
        ) {
            scope.launch {
                val error = AuthenticationRequiredException()
                downloads.forEach { onOutcome(it.fileId, DownloadOutcome.Failed(error)) }
            }
            return
        }
        DownloadQueuePump.registerObserver(serverUrl, storageScopeId) { workId ->
            scope.launch { observe(scope, serverUrl, storageScopeId, workId) }
        }
        val queuedEntries = downloads.map { download ->
            val book = download.book
            val fileId = download.fileId
            val requestId = UUID.randomUUID().toString()
            AutomaticDownloadSuppressionStore(context).clear(
                serverUrl,
                activeProfileId,
                fileId,
                storageScopeId
            )
            callbacksByDownload[
                DownloadCallbackKey(serverUrl, storageScopeId, fileId, requestId)
            ] = DownloadCallbacks(
                onProgress = { progress -> onProgress(fileId, progress) },
                onOutcome = { outcome -> onOutcome(fileId, outcome) }
            )
            DownloadQueueEntry(
                serverUrl = serverUrl,
                requestId = requestId,
                profileId = activeProfileId,
                storageScopeId = storageScopeId,
                fileId = fileId,
                bookId = book.id,
                libraryId = book.libraryId,
                title = book.title,
                filename = book.filename,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                origin = DownloadOrigin.MANUAL,
                cellularConsentGranted = cellularConsentGranted,
                sequence = sequence.updateAndGet { previous ->
                    nextDownloadQueueSequence(System.currentTimeMillis(), previous)
                }
            )
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val requests = downloads.map { download ->
                        val book = download.book
                        val fileId = download.fileId
                        val requestId = queuedEntries.first { it.fileId == fileId }.requestId
                        val existing = downloadStore.find(serverUrl, fileId, storageScopeId)
                        val target = downloadStore.ownedTargetOrIsolated(
                            serverUrl = serverUrl,
                            fileId = fileId,
                            title = book.title,
                            mediaKind = book.mediaKind,
                            formatHint = book.format,
                            recordedPath = existing?.localPath,
                            storageScopeId = storageScopeId
                        )
                        val attempt = DownloadAttempt(
                                serverUrl = serverUrl,
                                requestId = requestId,
                                profileId = activeProfileId,
                                storageScopeId = storageScopeId,
                                fileId = fileId,
                                bookId = book.id,
                                libraryId = book.libraryId,
                                title = book.title,
                                filename = book.filename,
                                targetPath = target.absolutePath,
                                existingLocalPath = existing?.localPath,
                                mediaKind = book.mediaKind,
                                mimeType = book.format,
                                sourceUpdatedAtMillis = book.updatedAtMillis,
                                origin = DownloadOrigin.MANUAL
                            )
                        queuedEntries.first { it.fileId == fileId } to attempt
                    }
                    cancellationGate.serializeMutation {
                        val surviving = requests.filterNot { (entry, _) ->
                            cancellationGate.consumeCancellation(entry.requestId)
                        }
                        downloadStore.enqueueDownloadsWithAttempts(surviving)
                    }
                }
                val workId = DownloadQueuePump.enqueueNext(context, serverUrl, storageScopeId)
                if (workId != null) {
                    observe(scope, serverUrl, storageScopeId, workId)
                } else {
                    val requestedIds = downloads.mapTo(mutableSetOf()) { it.fileId }
                    val activeMatching = withContext(Dispatchers.IO) {
                        runCatching {
                            workManager.getWorkInfosByTag(downloadServerTag(serverUrl)).get()
                        }.getOrDefault(emptyList()).filter { info ->
                            !info.state.isFinished &&
                                downloadStorageScopeIdFromTags(info.tags) == storageScopeId &&
                                info.tags.any { tag ->
                                tag.startsWith("$DOWNLOAD_FILE_TAG_PREFIX:") &&
                                    tag.removePrefix("$DOWNLOAD_FILE_TAG_PREFIX:") in requestedIds
                            }
                        }
                    }
                    activeMatching.forEach { info ->
                        observe(scope, serverUrl, storageScopeId, info.id)
                    }
                }
            } catch (error: Throwable) {
                downloads.forEach { download -> onOutcome(download.fileId, DownloadOutcome.Failed(error)) }
            }
        }
    }

    override fun cancel(serverUrl: String, fileId: String) {
        val owners = captureCancellationOwners(serverUrl, fileId)
        cancellationGate.cancel(owners.map { it.requestId })
        owners.mapNotNull { it.workId }.distinct().forEach(workManager::cancelWorkById)
        callbacksByDownload.keys.removeIf { key ->
            owners.any {
                it.serverUrl == key.serverUrl &&
                    it.storageScopeId == key.storageScopeId && it.fileId == key.fileId &&
                    it.requestId == key.requestId
            }
        }
        maintenanceScope.launch {
            cancellationGate.serializeMutation {
                owners.distinctBy {
                    listOf(it.serverUrl, it.storageScopeId, it.fileId, it.requestId)
                }.forEach { owner ->
                    downloadStore.cancelDownloadRequestIfOwned(
                        owner.serverUrl,
                        owner.fileId,
                        owner.requestId,
                        owner.storageScopeId
                    )
                }
            }
            owners.map { it.serverUrl to it.storageScopeId }.toSet().forEach { ownerScope ->
                DownloadQueuePump.enqueueNext(context, ownerScope.first, ownerScope.second)
            }
        }
    }

    override fun cancelAll() {
        val owners = captureCancellationOwners()
        cancellationGate.cancel(owners.map { it.requestId })
        owners.mapNotNull { it.workId }.distinct().forEach(workManager::cancelWorkById)
        callbacksByDownload.keys.removeIf { key ->
            owners.any {
                it.serverUrl == key.serverUrl &&
                    it.storageScopeId == key.storageScopeId && it.fileId == key.fileId &&
                    it.requestId == key.requestId
            }
        }
        DownloadQueuePump.clearObservers()
        maintenanceScope.launch {
            cancellationGate.serializeMutation {
                owners.distinctBy {
                    listOf(it.serverUrl, it.storageScopeId, it.fileId, it.requestId)
                }.forEach { owner ->
                    downloadStore.cancelDownloadRequestIfOwned(
                        owner.serverUrl,
                        owner.fileId,
                        owner.requestId,
                        owner.storageScopeId
                    )
                }
            }
            owners.map { it.serverUrl to it.storageScopeId }.toSet().forEach { ownerScope ->
                DownloadQueuePump.enqueueNext(context, ownerScope.first, ownerScope.second)
            }
        }
    }

    private fun captureCancellationOwners(
        serverUrl: String? = null,
        fileId: String? = null
    ): List<CancellationOwner> {
        val requestedScope = serverUrl?.let { downloadStorageScopeId(context, it) }
        if (serverUrl != null && requestedScope == null) return emptyList()
        val callbackOwners = callbacksByDownload.keys
            .filter { key ->
                (serverUrl == null || key.serverUrl == serverUrl) &&
                    (requestedScope == null || key.storageScopeId == requestedScope) &&
                    (fileId == null || key.fileId == fileId)
            }
            .map { key ->
                CancellationOwner(
                    key.serverUrl,
                    key.storageScopeId,
                    key.fileId,
                    key.requestId
                )
            }
        return runBlocking(Dispatchers.IO) {
        val queuedOwners = downloadStore.readDownloadQueue()
            .filter { entry ->
                (serverUrl == null || entry.serverUrl == serverUrl) &&
                    (requestedScope == null || entry.storageScopeId == requestedScope) &&
                    (fileId == null || entry.fileId == fileId)
            }
            .map { entry ->
                CancellationOwner(
                    entry.serverUrl,
                    entry.storageScopeId,
                    entry.fileId,
                    entry.requestId
                )
            }
        val activeOwners = runCatching {
            workManager.getWorkInfosByTag(DOWNLOAD_TAG).get()
        }.getOrDefault(emptyList()).filter { info ->
            !info.state.isFinished &&
                (serverUrl == null || downloadServerTag(serverUrl) in info.tags) &&
                (requestedScope == null ||
                    downloadStorageScopeIdFromTags(info.tags) == requestedScope) &&
                (fileId == null || downloadFileTag(fileId) in info.tags)
        }.mapNotNull { info ->
            val ownerServer = info.tags.firstOrNull {
                it.startsWith("$DOWNLOAD_SERVER_TAG_PREFIX:")
            }?.removePrefix("$DOWNLOAD_SERVER_TAG_PREFIX:") ?: return@mapNotNull null
            val ownerFile = downloadFileIdFromTags(info.tags) ?: return@mapNotNull null
            CancellationOwner(
                ownerServer,
                downloadStorageScopeIdFromTags(info.tags) ?: return@mapNotNull null,
                ownerFile,
                downloadRequestIdFromTags(info.tags).orEmpty(),
                info.id
            )
        }
        callbackOwners + queuedOwners + activeOwners
        }
    }

    override suspend fun reconcile(
        scope: CoroutineScope,
        serverUrl: String,
        bookForFileId: (String) -> BookSummary?,
        onProgress: (String, Float?) -> Unit,
        onOutcome: suspend (String, DownloadOutcome) -> Unit
    ): Map<String, BookSummary> {
        val storageScopeId = downloadStorageScopeId(context, serverUrl) ?: return emptyMap()
        DownloadQueuePump.registerObserver(serverUrl, storageScopeId) { workId ->
            scope.launch { observe(scope, serverUrl, storageScopeId, workId) }
        }
        val (attemptsByFileId, queued) = withContext(Dispatchers.IO) {
            runCatching {
                reconcileVerifiedCommitsWhenReaderInactive(
                    context,
                    downloadStore,
                    serverUrl,
                    storageScopeId
                )
            }
            val attempts = runCatching {
                downloadStore.readAttempts(serverUrl, storageScopeId).associateBy { it.fileId }
            }.getOrDefault(emptyMap())
            val queue = runCatching { downloadStore.readDownloadQueue(serverUrl, storageScopeId) }
                .getOrDefault(emptyList())
            attempts to queue
        }
        val active = linkedMapOf<String, BookSummary>()
        queued.forEach { entry ->
            callbacksByDownload[
                DownloadCallbackKey(serverUrl, storageScopeId, entry.fileId, entry.requestId)
            ] = DownloadCallbacks(
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
        val startedWorkId = DownloadQueuePump.enqueueNext(context, serverUrl, storageScopeId)
        val infos = withContext(Dispatchers.IO) {
            runCatching { workManager.getWorkInfosByTag(DOWNLOAD_TAG).get() }
                .getOrDefault(emptyList())
        }
        for (info in infos) {
            if (info.state.isFinished) continue
            if (downloadServerTag(serverUrl) !in info.tags) continue
            if (downloadStorageScopeIdFromTags(info.tags) != storageScopeId) continue
            // Callbacks were registered from the durable queue above. An active Work whose
            // request ID is no longer queued is stale and must be observed only for retirement,
            // never allowed to complete the replacement's UI lifecycle.
            observe(
                scope = scope,
                serverUrl = serverUrl,
                storageScopeId = storageScopeId,
                workId = info.id
            )
        }
        if (startedWorkId != null) {
            observe(
                scope = scope,
                serverUrl = serverUrl,
                storageScopeId = storageScopeId,
                workId = startedWorkId
            )
        }
        return active
    }

    private fun observe(
        scope: CoroutineScope,
        serverUrl: String,
        storageScopeId: String,
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
                    val requestId = downloadRequestIdFromTags(info.tags).orEmpty()
                    val callbackKey = fileId?.let {
                        DownloadCallbackKey(serverUrl, storageScopeId, it, requestId)
                    }
                    val callbacks = callbackKey?.let(callbacksByDownload::get)
                    if (callbacks != null) {
                        deliver(info, callbacks.onProgress, callbacks.onOutcome)
                    }
                    if (info.state.isFinished) {
                        liveData.removeObserver(observer)
                        observerRegistry.retire(workId)
                        if (callbackKey != null && callbacks != null) {
                            callbacksByDownload.remove(callbackKey, callbacks)
                        }
                        observeActive(scope, serverUrl, storageScopeId)
                    }
                }
            }
        }
        liveData.observeForever(observer)
    }

    private fun observeActive(
        scope: CoroutineScope,
        serverUrl: String,
        storageScopeId: String
    ) {
        scope.launch {
            val infos = withContext(Dispatchers.IO) {
                runCatching {
                    workManager.getWorkInfosByTag(downloadServerTag(serverUrl)).get()
                }.getOrDefault(emptyList())
            }
            infos.filter {
                !it.state.isFinished &&
                    downloadStorageScopeIdFromTags(it.tags) == storageScopeId
            }.forEach { info ->
                observe(scope, serverUrl, storageScopeId, info.id)
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
