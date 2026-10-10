package com.vangeaux.lagrange

import android.content.Context
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vangeaux.lagrange.provider.resolveProviderBookCatalogModule
import com.vangeaux.lagrange.provider.resolveProviderBookDetailModule
import com.vangeaux.lagrange.provider.resolveProviderLibraryModule
import com.vangeaux.lagrange.provider.resolveProviderLoginModule
import com.vangeaux.lagrange.provider.resolveProviderRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val AUTOMATIC_DOWNLOAD_DEFAULT_RESERVE_BYTES = 2L * 1024L * 1024L * 1024L
internal const val AUTOMATIC_DOWNLOAD_DEFAULT_MAXIMUM_BYTES = 10L * 1024L * 1024L * 1024L
internal const val AUTOMATIC_DOWNLOAD_MAX_LIMIT_BYTES = 2L * 1024L * 1024L * 1024L * 1024L
internal const val UNKNOWN_DOWNLOAD_RESERVATION_WINDOW_BYTES = 32L * 1024L * 1024L

/**
 * Device-wide limits for managed local book files.  These deliberately live outside the
 * per-profile automatic-download policy: turning library synchronization off must not silently
 * turn storage protection off, and files from a second server still consume the same device.
 */
internal data class LocalBookStoragePolicy(
    val reserveBytes: Long = AUTOMATIC_DOWNLOAD_DEFAULT_RESERVE_BYTES,
    val maximumBytes: Long = AUTOMATIC_DOWNLOAD_DEFAULT_MAXIMUM_BYTES,
    val automaticRemovalEnabled: Boolean = false,
    val generation: Long = 0L
) {
    fun normalized(): LocalBookStoragePolicy = copy(
        reserveBytes = normalizeAutomaticDownloadLimit(reserveBytes),
        maximumBytes = normalizeAutomaticDownloadLimit(maximumBytes),
        generation = generation.coerceAtLeast(0L)
    )
}

internal class LocalBookStoragePolicyStore(context: Context) {
    private companion object {
        const val POLICY_KEY = "policy"
        val monitor = Any()
    }

    private val preferences = context.applicationContext.getSharedPreferences(
        "local_book_storage_policy",
        Context.MODE_PRIVATE
    )

    fun read(): LocalBookStoragePolicy = synchronized(monitor) { readUnlocked() }

    fun save(requested: LocalBookStoragePolicy): LocalBookStoragePolicy = synchronized(monitor) {
        val normalized = requested.normalized()
        val previous = readUnlocked()
        val changed = previous.reserveBytes != normalized.reserveBytes ||
            previous.maximumBytes != normalized.maximumBytes ||
            previous.automaticRemovalEnabled != normalized.automaticRemovalEnabled
        val saved = normalized.copy(
            generation = if (changed) previous.generation + 1L else previous.generation
        )
        preferences.edit().putString(POLICY_KEY, saved.toJson().toString()).apply()
        saved
    }

    /** Imports the old profile-scoped values exactly once. */
    fun migrateIfAbsent(legacy: LocalBookStoragePolicy): LocalBookStoragePolicy =
        synchronized(monitor) {
            if (preferences.contains(POLICY_KEY)) return@synchronized readUnlocked()
            val migrated = legacy.normalized().copy(generation = 1L)
            preferences.edit().putString(POLICY_KEY, migrated.toJson().toString()).apply()
            migrated
        }

    private fun readUnlocked(): LocalBookStoragePolicy {
        val stored = preferences.getString(POLICY_KEY, null) ?: return LocalBookStoragePolicy()
        return runCatching {
            val value = JSONObject(stored)
            LocalBookStoragePolicy(
                reserveBytes = value.optLong(
                    "reserveBytes",
                    AUTOMATIC_DOWNLOAD_DEFAULT_RESERVE_BYTES
                ),
                maximumBytes = value.optLong(
                    "maximumBytes",
                    AUTOMATIC_DOWNLOAD_DEFAULT_MAXIMUM_BYTES
                ),
                automaticRemovalEnabled = value.optBoolean("automaticRemovalEnabled"),
                generation = value.optLong("generation")
            ).normalized()
        }.getOrDefault(LocalBookStoragePolicy())
    }

    private fun LocalBookStoragePolicy.toJson(): JSONObject = JSONObject()
        .put("reserveBytes", reserveBytes)
        .put("maximumBytes", maximumBytes)
        .put("automaticRemovalEnabled", automaticRemovalEnabled)
        .put("generation", generation)
}

internal enum class AutomaticDownloadScope {
    SELECTED_LIBRARIES,
    ALL_LIBRARIES
}

internal enum class AutomaticDownloadInitialMode {
    EXISTING_AND_FUTURE,
    NEW_BOOKS_ONLY
}

internal data class AutomaticDownloadPolicy(
    val profileId: String,
    val serverUrl: String,
    val storageScopeId: String = "",
    val enabled: Boolean = false,
    val scope: AutomaticDownloadScope = AutomaticDownloadScope.SELECTED_LIBRARIES,
    val selectedLibraryIds: Set<String> = emptySet(),
    val initialMode: AutomaticDownloadInitialMode =
        AutomaticDownloadInitialMode.EXISTING_AND_FUTURE,
    val allReadableCopies: Boolean = false,
    val unmeteredOnly: Boolean = true,
    val chargingRequired: Boolean = true,
    val reserveBytes: Long = AUTOMATIC_DOWNLOAD_DEFAULT_RESERVE_BYTES,
    val maximumBytes: Long = AUTOMATIC_DOWNLOAD_DEFAULT_MAXIMUM_BYTES,
    val automaticRemovalEnabled: Boolean = false,
    val generation: Long = 0L,
    val baselineGeneration: Long = 0L
) {
    fun normalized(): AutomaticDownloadPolicy = copy(
        profileId = profileId.trim(),
        serverUrl = normalizeServerUrl(serverUrl) ?: serverUrl.trim().trimEnd('/'),
        storageScopeId = storageScopeId.trim(),
        selectedLibraryIds = selectedLibraryIds.map(String::trim)
            .filter(String::isNotBlank)
            .toSet(),
        reserveBytes = normalizeAutomaticDownloadLimit(reserveBytes),
        maximumBytes = normalizeAutomaticDownloadLimit(maximumBytes),
        generation = generation.coerceAtLeast(0L),
        baselineGeneration = baselineGeneration.coerceAtLeast(0L)
    )

    fun selectedLibrariesChangedFrom(previous: AutomaticDownloadPolicy): Boolean =
        scope != previous.scope ||
            selectedLibraryIds != previous.selectedLibraryIds ||
            initialMode != previous.initialMode
}

private fun normalizeAutomaticDownloadLimit(value: Long): Long = if (value <= 0L) {
    0L
} else {
    value.coerceAtMost(AUTOMATIC_DOWNLOAD_MAX_LIMIT_BYTES)
}

internal class AutomaticDownloadPolicyStore(context: Context) {
    private companion object {
        val policyMonitor = Any()
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "automatic_download_policies",
        Context.MODE_PRIVATE
    )
    private val storagePolicyStore = LocalBookStoragePolicyStore(context)

    fun read(
        profileId: String,
        serverUrl: String,
        storageScopeId: String? = null
    ): AutomaticDownloadPolicy = synchronized(policyMonitor) {
        val scope = storageScopeId ?: downloadStorageScopeId(appContext, serverUrl, profileId)
            ?: "unresolved-account"
        readUnlocked(profileId, serverUrl, scope)
    }

    private fun readUnlocked(
        profileId: String,
        serverUrl: String,
        storageScopeId: String
    ): AutomaticDownloadPolicy {
        val fallback = AutomaticDownloadPolicy(
            profileId = profileId,
            serverUrl = serverUrl,
            storageScopeId = storageScopeId
        )
        val stored = preferences.getString(key(profileId, storageScopeId), null)
        if (stored == null) {
            val storage = storagePolicyStore.read()
            return fallback.copy(
                reserveBytes = storage.reserveBytes,
                maximumBytes = storage.maximumBytes,
                automaticRemovalEnabled = storage.automaticRemovalEnabled
            )
        }
        val parsed = runCatching {
            val value = JSONObject(stored)
            AutomaticDownloadPolicy(
                profileId = profileId,
                serverUrl = value.optString("serverUrl", serverUrl).ifBlank { serverUrl },
                storageScopeId = storageScopeId,
                enabled = value.optBoolean("enabled"),
                scope = runCatching {
                    AutomaticDownloadScope.valueOf(value.optString("scope"))
                }.getOrDefault(AutomaticDownloadScope.SELECTED_LIBRARIES),
                selectedLibraryIds = value.optJSONArray("selectedLibraryIds")
                    .toStringSet(),
                initialMode = runCatching {
                    AutomaticDownloadInitialMode.valueOf(value.optString("initialMode"))
                }.getOrDefault(AutomaticDownloadInitialMode.EXISTING_AND_FUTURE),
                allReadableCopies = value.optBoolean("allReadableCopies"),
                unmeteredOnly = value.optBoolean("unmeteredOnly", true),
                chargingRequired = value.optBoolean("chargingRequired", true),
                reserveBytes = value.optLong(
                    "reserveBytes",
                    AUTOMATIC_DOWNLOAD_DEFAULT_RESERVE_BYTES
                ),
                maximumBytes = value.optLong(
                    "maximumBytes",
                    AUTOMATIC_DOWNLOAD_DEFAULT_MAXIMUM_BYTES
                ),
                automaticRemovalEnabled = value.optBoolean("automaticRemovalEnabled"),
                generation = value.optLong("generation"),
                baselineGeneration = value.optLong("baselineGeneration")
            ).normalized()
        }.getOrDefault(fallback)
        val storage = storagePolicyStore.migrateIfAbsent(
            LocalBookStoragePolicy(
                reserveBytes = parsed.reserveBytes,
                maximumBytes = parsed.maximumBytes,
                automaticRemovalEnabled = parsed.automaticRemovalEnabled
            )
        )
        return parsed.copy(
            reserveBytes = storage.reserveBytes,
            maximumBytes = storage.maximumBytes,
            automaticRemovalEnabled = storage.automaticRemovalEnabled
        )
    }

    fun save(requested: AutomaticDownloadPolicy): AutomaticDownloadPolicy = runBlocking {
        localCopyDeletionGuard.withLock {
            synchronized(policyMonitor) {
                val normalized = requested.normalized()
                require(normalized.storageScopeId.isNotBlank() &&
                    normalized.storageScopeId != "unresolved-account") {
                    "Automatic downloads require a stable authenticated account identity."
                }
                val previous = readUnlocked(
                    normalized.profileId,
                    normalized.serverUrl,
                    normalized.storageScopeId
                )
                val storage = storagePolicyStore.save(
                    LocalBookStoragePolicy(
                        reserveBytes = normalized.reserveBytes,
                        maximumBytes = normalized.maximumBytes,
                        automaticRemovalEnabled = normalized.automaticRemovalEnabled
                    )
                )
                val next = normalized.copy(
                    reserveBytes = storage.reserveBytes,
                    maximumBytes = storage.maximumBytes,
                    automaticRemovalEnabled = storage.automaticRemovalEnabled,
                    generation = previous.generation + 1L,
                    baselineGeneration = if (normalized.selectedLibrariesChangedFrom(previous)) {
                        previous.baselineGeneration + 1L
                    } else {
                        previous.baselineGeneration
                    }
                )
                preferences.edit().putString(
                    key(next.profileId, next.storageScopeId),
                    next.toJson().toString()
                ).apply()
                next
            }
        }
    }

    fun disable(
        profileId: String,
        serverUrl: String,
        storageScopeId: String? = null
    ): AutomaticDownloadPolicy {
        val current = read(profileId, serverUrl, storageScopeId)
        if (current.storageScopeId == "unresolved-account") return current.copy(enabled = false)
        return save(
            current.copy(
                enabled = false
            )
        )
    }

    private fun key(profileId: String, storageScopeId: String): String = "policy_" + Base64.encodeToString(
        "$profileId\u0000$storageScopeId".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )

    private fun AutomaticDownloadPolicy.toJson(): JSONObject = JSONObject()
        .put("serverUrl", serverUrl)
        .put("storageScopeId", storageScopeId)
        .put("enabled", enabled)
        .put("scope", scope.name)
        .put("selectedLibraryIds", JSONArray(selectedLibraryIds.sorted()))
        .put("initialMode", initialMode.name)
        .put("allReadableCopies", allReadableCopies)
        .put("unmeteredOnly", unmeteredOnly)
        .put("chargingRequired", chargingRequired)
        .put("reserveBytes", reserveBytes)
        .put("maximumBytes", maximumBytes)
        .put("automaticRemovalEnabled", automaticRemovalEnabled)
        .put("generation", generation)
        .put("baselineGeneration", baselineGeneration)
}

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (index in 0 until length()) {
            optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

internal enum class AutomaticDownloadRunState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETE,
    FAILED,
    CANCELLED
}

internal data class AutomaticDownloadStatus(
    val state: AutomaticDownloadRunState = AutomaticDownloadRunState.IDLE,
    val checked: Int = 0,
    val queued: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val cursorLibraryId: String? = null,
    val lastRunAtMillis: Long? = null,
    val message: String? = null
)

internal class AutomaticDownloadStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "automatic_download_status",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun read(profileId: String, storageScopeId: String): AutomaticDownloadStatus {
        val value = preferences.getString(key(profileId, storageScopeId), null)
            ?: return AutomaticDownloadStatus()
        return runCatching {
            val json = JSONObject(value)
            AutomaticDownloadStatus(
                state = runCatching {
                    AutomaticDownloadRunState.valueOf(json.optString("state"))
                }.getOrDefault(AutomaticDownloadRunState.IDLE),
                checked = json.optInt("checked"),
                queued = json.optInt("queued"),
                skipped = json.optInt("skipped"),
                failed = json.optInt("failed"),
                cursorLibraryId = json.optString("cursorLibraryId").takeIf(String::isNotBlank),
                lastRunAtMillis = json.optLong("lastRunAtMillis", -1L).takeIf { it >= 0L },
                message = json.optString("message").takeIf(String::isNotBlank)
            )
        }.getOrDefault(AutomaticDownloadStatus())
    }

    @Synchronized
    fun write(profileId: String, storageScopeId: String, value: AutomaticDownloadStatus) {
        check(preferences.edit().putString(
            key(profileId, storageScopeId),
            JSONObject()
                .put("state", value.state.name)
                .put("checked", value.checked)
                .put("queued", value.queued)
                .put("skipped", value.skipped)
                .put("failed", value.failed)
                .put("cursorLibraryId", value.cursorLibraryId)
                .put("lastRunAtMillis", value.lastRunAtMillis)
                .put("message", value.message)
                .toString()
        ).commit()) { "Unable to persist automatic-download scan status." }
    }

    private fun key(profileId: String, storageScopeId: String): String = "status_" + Base64.encodeToString(
        "$profileId\u0000$storageScopeId".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )
}

internal enum class AutomaticDownloadSuppressionReason {
    CAPACITY_EVICTED,
    STORAGE_BLOCKED,
    USER_CLEARED,
    PERMANENT_FAILURE,
    RETRY_EXHAUSTED
}

/** Prevents a bounded library scan from immediately fetching a version storage cleanup removed. */
internal class AutomaticDownloadSuppressionStore(context: Context) {
    private companion object {
        const val UNKNOWN_REVISION = "<unknown>"
        val monitor = Any()
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "automatic_download_suppressions",
        Context.MODE_PRIVATE
    )

    fun suppress(
        serverUrl: String,
        profileId: String,
        fileId: String,
        sourceRevision: Long?,
        reason: AutomaticDownloadSuppressionReason,
        storageGeneration: Long,
        retryAfterMillis: Long? = null,
        storageScopeId: String? = null
    ) = synchronized(monitor) {
        check(preferences.edit().putString(
            key(serverUrl, profileId, fileId, storageScopeId),
            JSONObject()
                .put("sourceRevision", sourceRevision?.toString() ?: UNKNOWN_REVISION)
                .put("reason", reason.name)
                .put("storageGeneration", storageGeneration)
                .put("createdAtMillis", System.currentTimeMillis())
                .put("retryAfterMillis", retryAfterMillis)
                .toString()
        ).commit()) { "Unable to persist automatic-download suppression." }
    }

    fun isSuppressed(
        serverUrl: String,
        profileId: String,
        fileId: String,
        sourceRevision: Long?,
        storageGeneration: Long,
        storageScopeId: String? = null
    ): Boolean = synchronized(monitor) {
        val storageKey = key(serverUrl, profileId, fileId, storageScopeId)
        val raw = preferences.getString(storageKey, null) ?: return@synchronized false
        val value = runCatching { JSONObject(raw) }.getOrNull() ?: run {
            preferences.edit().remove(storageKey).commit()
            return@synchronized false
        }
        val sameRevision = value.optString("sourceRevision") ==
            (sourceRevision?.toString() ?: UNKNOWN_REVISION)
        val sameStorageGeneration = value.optLong("storageGeneration", -1L) == storageGeneration
        val retryAfter = value.optLong("retryAfterMillis", -1L).takeIf { it >= 0L }
        if (!sameRevision || !sameStorageGeneration ||
            (retryAfter != null && System.currentTimeMillis() >= retryAfter)
        ) {
            preferences.edit().remove(storageKey).commit()
            false
        } else {
            true
        }
    }

    fun clear(
        serverUrl: String,
        profileId: String,
        fileId: String,
        storageScopeId: String? = null
    ) = synchronized(monitor) {
        preferences.edit().remove(key(serverUrl, profileId, fileId, storageScopeId)).commit()
    }

    private fun key(
        serverUrl: String,
        profileId: String,
        fileId: String,
        explicitStorageScopeId: String?
    ): String {
        val normalized = normalizeServerUrl(serverUrl) ?: serverUrl.trim().trimEnd('/')
        val storageScopeId = explicitStorageScopeId
            ?: downloadStorageScopeId(appContext, serverUrl)
            ?: "unresolved-account"
        return Base64.encodeToString(
            "$normalized\u0000$profileId\u0000$storageScopeId\u0000$fileId"
                .toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
    }
}

internal data class LocalBookReaderLease(
    val ownerId: String,
    val serverUrl: String,
    val storageScopeId: String?,
    val fileId: String?,
    val localPath: String?
)

/**
 * Process-scoped reader protection. Leases never expire on a timer, so Android freezing a
 * background activity or foreground TTS service cannot make maintenance race ahead of it.
 * A lease from a different process instance is stale by construction because this app does not
 * host readers in a second process.
 */
internal class LocalBookReaderLeaseStore(context: Context) {
    private companion object {
        const val LEASES_KEY = "leases"
        val processInstanceId: String = java.util.UUID.randomUUID().toString()
        val monitor = Any()
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "local_book_reader_leases",
        Context.MODE_PRIVATE
    )

    fun acquire(
        ownerId: String,
        serverUrl: String,
        fileId: String?,
        localPath: String?,
        storageScopeId: String? = null
    ) = runBlocking {
        // Publication replacement takes the same guard, closing the check-then-open race for
        // foreground services that do not own a persisted ActiveReader session.
        localCopyDeletionGuard.withLock {
            synchronized(monitor) {
                val leases = readUnlocked()
                    .filterNot { it.optString("ownerId") == ownerId }
                    .toMutableList()
                leases += JSONObject()
                    .put("processInstanceId", processInstanceId)
                    .put("ownerId", ownerId)
                    .put("serverUrl", serverUrl)
                    .put("storageScopeId", storageScopeId)
                    .put("fileId", fileId)
                    .put("localPath", localPath)
                writeUnlocked(leases)
            }
        }
    }

    fun release(ownerId: String) {
        val released = synchronized(monitor) {
            val all = readUnlocked()
            val removed = all.filter {
                it.optString("processInstanceId") == processInstanceId &&
                    it.optString("ownerId") == ownerId
            }.mapNotNull { value ->
                value.optString("ownerId").takeIf(String::isNotBlank)?.let { leaseOwner ->
                    LocalBookReaderLease(
                        ownerId = leaseOwner,
                        serverUrl = value.optString("serverUrl"),
                        storageScopeId = value.optString("storageScopeId")
                            .takeIf(String::isNotBlank),
                        fileId = value.optString("fileId").takeIf(String::isNotBlank),
                        localPath = value.optString("localPath").takeIf(String::isNotBlank)
                    )
                }
            }
            writeUnlocked(all.filterNot { it.optString("ownerId") == ownerId })
            removed
        }
        if (released.isNotEmpty()) {
            DownloadQueuePump.wakeVerifiedReplacements(appContext, released)
        }
    }

    fun readLive(): List<LocalBookReaderLease> = synchronized(monitor) {
        val all = readUnlocked()
        val current = all.filter { it.optString("processInstanceId") == processInstanceId }
        if (current.size != all.size) writeUnlocked(current)
        current.mapNotNull { value ->
            value.optString("ownerId").takeIf(String::isNotBlank)?.let { ownerId ->
                LocalBookReaderLease(
                    ownerId = ownerId,
                    serverUrl = value.optString("serverUrl"),
                    storageScopeId = value.optString("storageScopeId")
                        .takeIf(String::isNotBlank),
                    fileId = value.optString("fileId").takeIf(String::isNotBlank),
                    localPath = value.optString("localPath").takeIf(String::isNotBlank)
                )
            }
        }
    }

    private fun readUnlocked(): List<JSONObject> {
        val raw = preferences.getString(LEASES_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(values: List<JSONObject>) {
        preferences.edit().putString(LEASES_KEY, JSONArray(values).toString()).apply()
    }
}

internal class DownloadReplacementDeferredException : IllegalStateException(
    "The current local copy is still open. The verified update will be installed after the reader closes."
)

private fun canonicalPathsMatch(first: String?, second: String?): Boolean {
    if (first.isNullOrBlank() || second.isNullOrBlank()) return false
    return runCatching { File(first).canonicalFile == File(second).canonicalFile }
        .getOrDefault(false)
}

internal fun localReaderLeaseBlocksReplacement(
    lease: LocalBookReaderLease,
    serverUrl: String,
    storageScopeId: String,
    fileId: String,
    targetPath: String
): Boolean {
    if (canonicalPathsMatch(lease.localPath, targetPath)) return true
    return lease.fileId == fileId &&
        lease.serverUrl.isNotBlank() && serverUrlsMatch(lease.serverUrl, serverUrl) &&
        (lease.storageScopeId == null || lease.storageScopeId == storageScopeId)
}

private suspend fun downloadReplacementIsActive(
    context: Context,
    serverUrl: String,
    storageScopeId: String,
    fileId: String,
    targetFile: File
): Boolean {
    if (!targetFile.isFile) return false
    val activeBook = ActiveReaderStore(context).read(serverUrl, storageScopeId)
    if (activeBook != null && (
            activeBook.fileId == fileId ||
                canonicalPathsMatch(activeBook.localPath, targetFile.absolutePath)
        )
    ) return true
    return LocalBookReaderLeaseStore(context).readLive().any { lease ->
        localReaderLeaseBlocksReplacement(
            lease,
            serverUrl,
            storageScopeId,
            fileId,
            targetFile.absolutePath
        )
    }
}

internal suspend fun verifyAndCommitDownloadWhenReaderInactive(
    context: Context,
    downloadStore: DownloadStore,
    serverUrl: String,
    storageScopeId: String,
    fileId: String,
    requestId: String,
    stagedFile: File,
    targetFile: File,
    record: DownloadRecord
): Boolean {
    // This computes the full-file digest. Keep it outside the lifecycle guard so opening a
    // reader never waits on hashing a large staged publication.
    val verified = downloadStore.markAttemptStateIfOwned(
        serverUrl,
        fileId,
        requestId,
        DownloadAttemptState.VERIFIED,
        stagedFile,
        storageScopeId
    )
    if (!verified) return false
    return localCopyDeletionGuard.withLock {
        if (downloadReplacementIsActive(context, serverUrl, storageScopeId, fileId, targetFile)) {
            throw DownloadReplacementDeferredException()
        }
        downloadStore.commitDownloadIfOwned(
            serverUrl = serverUrl,
            fileId = fileId,
            requestId = requestId,
            stagedFile = stagedFile,
            targetFile = targetFile,
            record = record,
            storageScopeId = storageScopeId
        )
    }
}

internal suspend fun reconcileVerifiedCommitsWhenReaderInactive(
    context: Context,
    downloadStore: DownloadStore,
    serverUrl: String,
    storageScopeId: String
): Set<String> {
    // Digest validation is intentionally outside the reader/open guard. Stages are private app
    // files and the request ID/path are rechecked under DownloadStore's mutex before publication.
    val prevalidated = downloadStore.prevalidateVerifiedCommits(serverUrl, storageScopeId)
    return localCopyDeletionGuard.withLock {
        downloadStore.reconcileVerifiedCommits(
            serverUrl,
            storageScopeId,
            prevalidated
        ) { attempt, target ->
            !downloadReplacementIsActive(
                context,
                attempt.serverUrl,
                attempt.storageScopeId,
                attempt.fileId,
                target
            )
        }
    }
}

@Entity(
    tableName = "automatic_download_observed_books",
    primaryKeys = ["storageScopeId", "libraryId", "bookId"]
)
internal data class AutomaticDownloadObservedBookEntity(
    val profileId: String,
    val storageScopeId: String,
    val libraryId: String,
    val bookId: String,
    val observedAtMillis: Long
)

@Entity(
    tableName = "automatic_download_scans",
    primaryKeys = ["storageScopeId", "libraryId"]
)
internal data class AutomaticDownloadScanEntity(
    val profileId: String,
    val storageScopeId: String,
    val libraryId: String,
    val nextPage: Int,
    val nextItemIndex: Int = 0,
    val baselineComplete: Boolean,
    val baselineGeneration: Long,
    val updatedAtMillis: Long
)

@Dao
internal interface AutomaticDownloadStateDao {
    @Query(
        "SELECT * FROM automatic_download_scans " +
            "WHERE storageScopeId = :storageScopeId AND libraryId = :libraryId LIMIT 1"
    )
    suspend fun readScan(storageScopeId: String, libraryId: String): AutomaticDownloadScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveScan(value: AutomaticDownloadScanEntity)

    @Query(
        "SELECT COUNT(*) > 0 FROM automatic_download_observed_books " +
            "WHERE storageScopeId = :storageScopeId AND libraryId = :libraryId AND bookId = :bookId"
    )
    suspend fun isObserved(storageScopeId: String, libraryId: String, bookId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun observe(values: List<AutomaticDownloadObservedBookEntity>)

    @Query(
        "DELETE FROM automatic_download_observed_books " +
            "WHERE storageScopeId = :storageScopeId AND libraryId = :libraryId"
    )
    suspend fun clearObserved(storageScopeId: String, libraryId: String)

    @Query("DELETE FROM automatic_download_scans WHERE storageScopeId = :storageScopeId")
    suspend fun clearScans(storageScopeId: String)

    @Query("DELETE FROM automatic_download_observed_books WHERE storageScopeId = :storageScopeId")
    suspend fun clearAllObserved(storageScopeId: String)
}

@Database(
    entities = [
        AutomaticDownloadObservedBookEntity::class,
        AutomaticDownloadScanEntity::class
    ],
    version = 2,
    exportSchema = false
)
internal abstract class AutomaticDownloadStateDatabase : RoomDatabase() {
    abstract fun stateDao(): AutomaticDownloadStateDao
}

internal class AutomaticDownloadStateStore(context: Context) {
    private val dao = database(context).stateDao()

    suspend fun readScan(
        profileId: String,
        storageScopeId: String,
        libraryId: String,
        baselineGeneration: Long
    ): AutomaticDownloadScanEntity {
        val existing = dao.readScan(storageScopeId, libraryId)
        if (existing != null && existing.baselineGeneration == baselineGeneration) return existing
        dao.clearObserved(storageScopeId, libraryId)
        return AutomaticDownloadScanEntity(
            profileId = profileId,
            storageScopeId = storageScopeId,
            libraryId = libraryId,
            nextPage = 0,
            baselineComplete = false,
            baselineGeneration = baselineGeneration,
            updatedAtMillis = System.currentTimeMillis()
        ).also { dao.saveScan(it) }
    }

    suspend fun saveScan(value: AutomaticDownloadScanEntity) = dao.saveScan(value)

    suspend fun isObserved(storageScopeId: String, libraryId: String, bookId: String): Boolean =
        dao.isObserved(storageScopeId, libraryId, bookId)

    suspend fun observe(
        profileId: String,
        storageScopeId: String,
        libraryId: String,
        bookIds: Collection<String>
    ) {
        val now = System.currentTimeMillis()
        dao.observe(bookIds.distinct().map { bookId ->
            AutomaticDownloadObservedBookEntity(profileId, storageScopeId, libraryId, bookId, now)
        })
    }

    suspend fun clearScans(storageScopeId: String) {
        dao.clearScans(storageScopeId)
        dao.clearAllObserved(storageScopeId)
    }

    private companion object {
        @Volatile
        private var instance: AutomaticDownloadStateDatabase? = null

        fun database(context: Context): AutomaticDownloadStateDatabase = instance
            ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutomaticDownloadStateDatabase::class.java,
                    "automatic-download-state.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

internal data class AutomaticStorageAdmission(
    val requiredReclaimBytes: Long,
    val maximumExceededByBytes: Long,
    val reserveShortfallBytes: Long
) {
    val allowedWithoutReclaim: Boolean get() = requiredReclaimBytes <= 0L
}

internal fun automaticStorageAdmission(
    currentDownloadedBytes: Long,
    availableBytes: Long,
    incomingFinalBytes: Long,
    incomingPeakBytes: Long,
    maximumBytes: Long,
    reserveBytes: Long
): AutomaticStorageAdmission {
    val maximumExceeded = if (maximumBytes <= 0L) {
        0L
    } else {
        saturatingAdd(
            currentDownloadedBytes.coerceAtLeast(0L),
            maxOf(
                incomingFinalBytes.coerceAtLeast(0L),
                incomingPeakBytes.coerceAtLeast(0L)
            )
        ).minus(maximumBytes).coerceAtLeast(0L)
    }
    val availableAfterPeak = availableBytes.coerceAtLeast(0L)
        .minus(incomingPeakBytes.coerceAtLeast(0L))
    val reserveShortfall = if (reserveBytes <= 0L) {
        0L
    } else {
        reserveBytes.minus(availableAfterPeak).coerceAtLeast(0L)
    }
    return AutomaticStorageAdmission(
        requiredReclaimBytes = maxOf(maximumExceeded, reserveShortfall),
        maximumExceededByBytes = maximumExceeded,
        reserveShortfallBytes = reserveShortfall
    )
}

private fun saturatingAdd(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

internal data class AutomaticEvictionCandidate(
    val bookId: String,
    val fileIds: Set<String>,
    val sizeBytes: Long,
    val completed: Boolean,
    val lastAccessedAtMillis: Long,
    val downloadedAtMillis: Long,
    val serverUrl: String = "",
    val profileId: String = "",
    val storageScopeId: String = "",
    val sourceRevisions: Map<String, Long?> = emptyMap(),
    val localPaths: Set<String> = emptySet()
)

internal fun downloadFileGroupIsProtected(
    fileIds: Set<String>,
    queuedFileIds: Set<String>,
    attemptFileIds: Set<String>,
    activeFileId: String?
): Boolean = fileIds.any { it in queuedFileIds || it in attemptFileIds || it == activeFileId }

internal fun groupDownloadedRecordsByBook(
    records: List<DownloadRecord>
): Map<String, List<DownloadRecord>> = records.groupBy { it.bookId }

internal fun selectAutomaticEvictions(
    candidates: List<AutomaticEvictionCandidate>,
    requiredBytes: Long
): List<AutomaticEvictionCandidate> {
    if (requiredBytes <= 0L) return emptyList()
    var reclaimed = 0L
    return buildList {
        candidates.sortedWith(
            compareByDescending<AutomaticEvictionCandidate> { it.completed }
                .thenBy { it.lastAccessedAtMillis }
                .thenBy { it.downloadedAtMillis }
                .thenBy { it.bookId }
        ).forEach { candidate ->
            if (reclaimed >= requiredBytes) return@forEach
            add(candidate)
            reclaimed = saturatingAdd(reclaimed, candidate.sizeBytes.coerceAtLeast(0L))
        }
    }
}

internal fun evictionPlanCanSatisfy(
    plan: List<AutomaticEvictionCandidate>,
    requiredBytes: Long
): Boolean = plan.fold(0L) { total, candidate ->
    saturatingAdd(total, candidate.sizeBytes.coerceAtLeast(0L))
} >= requiredBytes.coerceAtLeast(0L)

internal fun downloadIsIndividuallyOverCap(
    totalBytes: Long,
    origin: DownloadOrigin,
    policy: AutomaticDownloadPolicy
): Boolean = automaticStorageLimitsApply(origin) &&
    policy.maximumBytes > 0L && totalBytes > policy.maximumBytes

internal fun automaticStorageLimitsApply(origin: DownloadOrigin): Boolean =
    origin == DownloadOrigin.AUTOMATIC

internal data class AutomaticCapacityResult(
    val allowed: Boolean,
    val removedFileIds: Set<String> = emptySet(),
    val message: String? = null
)

internal data class DownloadStorageAllowance(
    val maximumAdditionalBytes: Long,
    val storageGeneration: Long
) {
    fun permits(bytesWritten: Long, nextChunkBytes: Int): Boolean {
        if (maximumAdditionalBytes == Long.MAX_VALUE) return true
        val next = saturatingAdd(bytesWritten.coerceAtLeast(0L), nextChunkBytes.toLong())
        return next <= maximumAdditionalBytes
    }
}

internal fun DownloadStorageAllowance.permitsCurrentPolicyAndReserve(
    context: Context,
    stagedFile: File,
    bytesWritten: Long,
    nextChunkBytes: Int,
    origin: DownloadOrigin = DownloadOrigin.AUTOMATIC
): Boolean {
    if (!automaticStorageLimitsApply(origin)) return true
    if (!permits(bytesWritten, nextChunkBytes)) return false
    val current = LocalBookStoragePolicyStore(context).read()
    if (current.generation != storageGeneration) {
        throw LocalBookStoragePolicyChangedException()
    }
    if (current.reserveBytes <= 0L) return true
    return stagedFile.usableSpace.coerceAtLeast(0L) - nextChunkBytes >= current.reserveBytes
}

internal class LocalBookStorageLimitException(message: String) : IllegalStateException(message)
internal class LocalBookStoragePolicyChangedException : IllegalStateException(
    "The local-book storage settings changed during the download."
)

internal fun downloadStorageAllowance(
    usage: StorageUsage,
    policy: LocalBookStoragePolicy
): DownloadStorageAllowance {
    val maximumHeadroom = if (policy.maximumBytes <= 0L) {
        Long.MAX_VALUE
    } else {
        (policy.maximumBytes - usage.downloadedBytes).coerceAtLeast(0L)
    }
    val reserveHeadroom = if (policy.reserveBytes <= 0L) {
        Long.MAX_VALUE
    } else {
        (usage.availableBytes - policy.reserveBytes).coerceAtLeast(0L)
    }
    return DownloadStorageAllowance(
        maximumAdditionalBytes = minOf(maximumHeadroom, reserveHeadroom),
        storageGeneration = policy.generation
    )
}

internal fun reservedDownloadStorageAllowance(
    usage: StorageUsage,
    policy: LocalBookStoragePolicy,
    otherReservedBytes: Long,
    requestedBytes: Long?
): DownloadStorageAllowance {
    val base = downloadStorageAllowance(usage, policy)
    val available = if (base.maximumAdditionalBytes == Long.MAX_VALUE) {
        Long.MAX_VALUE
    } else {
        (base.maximumAdditionalBytes - otherReservedBytes.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
    val granted = requestedBytes?.coerceAtLeast(0L)?.let { requested ->
        if (available == Long.MAX_VALUE) requested else minOf(requested, available)
    } ?: available
    return base.copy(maximumAdditionalBytes = granted)
}

/** Durable admission reservations close the race between concurrent transfer workers. */
internal class DownloadStorageReservationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "download_storage_reservations",
        Context.MODE_PRIVATE
    )

    private companion object {
        val mutex = kotlinx.coroutines.sync.Mutex()
        const val ENTRIES = "entries"
        val processInstanceId: String = java.util.UUID.randomUUID().toString()
    }

    suspend fun reserve(
        reservationId: String,
        stagedPath: String,
        usage: StorageUsage,
        policy: LocalBookStoragePolicy,
        requestedBytes: Long?,
        activeStagedPaths: Set<String>
    ): DownloadStorageAllowance = mutex.withLock {
        val entries = readUnlocked()
            .filter {
                it.processInstanceId == processInstanceId &&
                    it.stagedPath in activeStagedPaths
            }
            .filterNot { it.id == reservationId }
            .toMutableList()
        val otherReserved = entries.fold(0L) { total, entry ->
            saturatingAdd(total, entry.reservedBytes)
        }
        val allowance = reservedDownloadStorageAllowance(
            usage = usage,
            policy = policy,
            otherReservedBytes = otherReserved,
            requestedBytes = requestedBytes
        )
        entries += Entry(
            id = reservationId,
            processInstanceId = processInstanceId,
            stagedPath = stagedPath,
            reservedBytes = allowance.maximumAdditionalBytes,
            storageGeneration = policy.generation
        )
        writeUnlocked(entries)
        allowance
    }

    suspend fun release(reservationId: String) = mutex.withLock {
        val entries = readUnlocked()
        val remaining = entries.filterNot { it.id == reservationId }
        if (remaining.size != entries.size) writeUnlocked(remaining)
    }

    private fun readUnlocked(): List<Entry> = runCatching {
        val array = JSONArray(preferences.getString(ENTRIES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val id = value.optString("id").takeIf(String::isNotBlank) ?: continue
                val stagedPath = value.optString("stagedPath")
                    .takeIf(String::isNotBlank) ?: continue
                add(
                    Entry(
                        id = id,
                        processInstanceId = value.optString("processInstanceId"),
                        stagedPath = stagedPath,
                        reservedBytes = value.optLong("reservedBytes").coerceAtLeast(0L),
                        storageGeneration = value.optLong("storageGeneration").coerceAtLeast(0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeUnlocked(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("processInstanceId", entry.processInstanceId)
                    .put("stagedPath", entry.stagedPath)
                    .put("reservedBytes", entry.reservedBytes)
                    .put("storageGeneration", entry.storageGeneration)
            )
        }
        check(preferences.edit().putString(ENTRIES, array.toString()).commit()) {
            "Unable to persist the download storage reservation."
        }
    }

    private data class Entry(
        val id: String,
        val processInstanceId: String,
        val stagedPath: String,
        val reservedBytes: Long,
        val storageGeneration: Long
    )
}

internal fun evictionPolicyIsStillCurrent(
    expected: LocalBookStoragePolicy,
    current: LocalBookStoragePolicy
): Boolean = expected.automaticRemovalEnabled &&
    current.automaticRemovalEnabled &&
    expected.generation == current.generation

internal object AutomaticDownloadStorage {
    suspend fun prepareTransfer(
        context: Context,
        serverUrl: String,
        profileId: String,
        policy: AutomaticDownloadPolicy,
        declaredBytes: Long?,
        existingFinalBytes: Long,
        protectedFileIds: Set<String>,
        allowRemoval: Boolean,
        reservationId: String,
        stagedPath: String,
        origin: DownloadOrigin = DownloadOrigin.AUTOMATIC
    ): Pair<AutomaticCapacityResult, DownloadStorageAllowance> {
        if (!automaticStorageLimitsApply(origin)) {
            return AutomaticCapacityResult(true) to DownloadStorageAllowance(
                maximumAdditionalBytes = Long.MAX_VALUE,
                storageGeneration = LocalBookStoragePolicyStore(context).read().generation
            )
        }
        val expected = declaredBytes?.coerceAtLeast(0L)
        val capacity = if (expected == null) {
            // Unknown bodies start with whatever bounded headroom is already available. Do not
            // reject or evict merely because a full window is unavailable; EOF may arrive first.
            AutomaticCapacityResult(true)
        } else {
            ensureCapacity(
                context = context,
                serverUrl = serverUrl,
                profileId = profileId,
                policy = policy,
                incomingFinalBytes = (expected - existingFinalBytes.coerceAtLeast(0L))
                    .coerceAtLeast(0L),
                incomingPeakBytes = expected,
                protectedFileIds = protectedFileIds,
                allowRemoval = allowRemoval
            )
        }
        val storagePolicy = LocalBookStoragePolicyStore(context).read()
        val activeStagedPaths = DownloadStore(context).readAttempts()
            .mapNotNull { it.stagedPath }
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()
        val allowance = DownloadStorageReservationStore(context).reserve(
            reservationId = reservationId,
            stagedPath = File(stagedPath).canonicalPath,
            usage = AppStorageManager(context).usage(),
            policy = storagePolicy,
            requestedBytes = expected ?: UNKNOWN_DOWNLOAD_RESERVATION_WINDOW_BYTES,
            activeStagedPaths = activeStagedPaths
        )
        return capacity to allowance
    }

    suspend fun extendUnknownTransfer(
        context: Context,
        serverUrl: String,
        profileId: String,
        policy: AutomaticDownloadPolicy,
        bytesWritten: Long,
        protectedFileIds: Set<String>,
        reservationId: String,
        stagedPath: String
    ): Pair<AutomaticCapacityResult, DownloadStorageAllowance> {
        val storagePolicy = LocalBookStoragePolicyStore(context).read()
        val activeStagedPaths = DownloadStore(context).readAttempts()
            .mapNotNull { it.stagedPath }
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()
        val reservationStore = DownloadStorageReservationStore(context)
        var additional = reservationStore.reserve(
            reservationId = reservationId,
            stagedPath = File(stagedPath).canonicalPath,
            usage = AppStorageManager(context).usage(),
            policy = storagePolicy,
            requestedBytes = UNKNOWN_DOWNLOAD_RESERVATION_WINDOW_BYTES,
            activeStagedPaths = activeStagedPaths
        )
        var capacity = AutomaticCapacityResult(true)
        if (additional.maximumAdditionalBytes <= 0L) {
            // Request only proof of one more byte. If automatic removal is enabled this reclaims
            // at most the first sufficient eviction group, after which reservation grants up to
            // one bounded window. A partial final window remains valid when EOF arrives.
            capacity = ensureCapacity(
                context = context,
                serverUrl = serverUrl,
                profileId = profileId,
                policy = policy,
                incomingFinalBytes = 1L,
                incomingPeakBytes = 1L,
                protectedFileIds = protectedFileIds,
                allowRemoval = true
            )
            if (!capacity.allowed) {
                return capacity to DownloadStorageAllowance(0L, storagePolicy.generation)
            }
            additional = reservationStore.reserve(
                reservationId = reservationId,
                stagedPath = File(stagedPath).canonicalPath,
                usage = AppStorageManager(context).usage(),
                policy = LocalBookStoragePolicyStore(context).read(),
                requestedBytes = UNKNOWN_DOWNLOAD_RESERVATION_WINDOW_BYTES,
                activeStagedPaths = activeStagedPaths
            )
            if (additional.maximumAdditionalBytes <= 0L) {
                capacity = AutomaticCapacityResult(
                    allowed = false,
                    message = "No additional storage is available for this download."
                )
            }
        }
        return capacity to additional.copy(
            maximumAdditionalBytes = saturatingAdd(
                bytesWritten.coerceAtLeast(0L),
                additional.maximumAdditionalBytes
            )
        )
    }

    suspend fun releaseTransfer(context: Context, reservationId: String) {
        DownloadStorageReservationStore(context).release(reservationId)
    }

    suspend fun ensureCapacity(
        context: Context,
        serverUrl: String,
        profileId: String,
        policy: AutomaticDownloadPolicy,
        incomingFinalBytes: Long,
        incomingPeakBytes: Long,
        protectedFileIds: Set<String>,
        allowRemoval: Boolean
    ): AutomaticCapacityResult = withContext(Dispatchers.IO) {
        val storagePolicy = LocalBookStoragePolicyStore(context).read()
        val store = DownloadStore(context)
        val usage = AppStorageManager(context).usage()
        var admission = automaticStorageAdmission(
            currentDownloadedBytes = usage.downloadedBytes,
            availableBytes = usage.availableBytes,
            incomingFinalBytes = incomingFinalBytes,
            incomingPeakBytes = incomingPeakBytes,
            maximumBytes = storagePolicy.maximumBytes,
            reserveBytes = storagePolicy.reserveBytes
        )
        if (admission.allowedWithoutReclaim) return@withContext AutomaticCapacityResult(true)
        if (!allowRemoval || !storagePolicy.automaticRemovalEnabled) {
            return@withContext AutomaticCapacityResult(
                allowed = false,
                message = capacityMessage(admission)
            )
        }

        val queued = store.readDownloadQueue().mapTo(mutableSetOf()) {
            Triple(it.serverUrl, it.storageScopeId, it.fileId)
        }
        val attempts = store.readAttempts().mapTo(mutableSetOf()) {
            Triple(it.serverUrl, it.storageScopeId, it.fileId)
        }
        val records = store.readAll()
        val activeFileIds = ActiveReaderStore(context).readAll().mapNotNullTo(mutableSetOf()) { session ->
            session.book.fileId?.let { fileId ->
                Triple(normalizeServerUrl(session.serverUrl).orEmpty(), session.storageScopeId, fileId)
            }
        }
        val readerLeases = LocalBookReaderLeaseStore(context).readLive()
        val leasedPaths = readerLeases.mapNotNull { lease ->
            lease.localPath?.let(::File)?.let { runCatching { it.canonicalPath }.getOrNull() }
        }.toSet()
        val incomingProtected = protectedFileIds.mapTo(mutableSetOf()) {
            Triple(serverUrl, policy.storageScopeId, it)
        }
        val candidates = records.groupBy {
            listOf(it.serverUrl, it.profileId, it.storageScopeId, it.bookId)
        }
            .mapNotNull { (identity, bookRecords) ->
            val fileIds = bookRecords.mapTo(mutableSetOf()) { it.fileId }
            val protected = fileIds.any { fileId ->
                Triple(identity[0], identity[2], fileId) in queued ||
                    Triple(identity[0], identity[2], fileId) in attempts ||
                    Triple(identity[0], identity[2], fileId) in incomingProtected ||
                    Triple(normalizeServerUrl(identity[0]).orEmpty(), identity[2], fileId) in activeFileIds
            }
            if (protected) return@mapNotNull null
            val files = bookRecords.map { File(it.localPath) }.distinctBy { it.absolutePath }
            if (files.any { file ->
                    runCatching { file.canonicalPath in leasedPaths }.getOrDefault(false)
                } || readerLeases.any { lease ->
                    lease.serverUrl == identity[0] && lease.fileId in fileIds
                }
            ) return@mapNotNull null
            val size = files.sumOf { file -> file.length().coerceAtLeast(0L) }
            AutomaticEvictionCandidate(
                bookId = identity[3],
                fileIds = fileIds,
                sizeBytes = size,
                completed = bookRecords.all { it.lastKnownCompleted == true },
                lastAccessedAtMillis = bookRecords.mapNotNull { it.lastAccessedAtMillis }
                    .maxOrNull() ?: Long.MIN_VALUE,
                downloadedAtMillis = bookRecords.maxOfOrNull { it.downloadedAtMillis }
                    ?: Long.MIN_VALUE,
                serverUrl = identity[0],
                profileId = identity[1],
                storageScopeId = identity[2],
                sourceRevisions = bookRecords.associate { it.fileId to it.sourceUpdatedAtMillis },
                localPaths = files.mapTo(mutableSetOf()) { it.absolutePath }
            )
        }
        val plan = selectAutomaticEvictions(candidates, admission.requiredReclaimBytes)
        if (!evictionPlanCanSatisfy(plan, admission.requiredReclaimBytes)) {
            return@withContext AutomaticCapacityResult(
                allowed = false,
                message = capacityMessage(admission)
            )
        }
        val removed = mutableSetOf<String>()
        val suppressionStore = AutomaticDownloadSuppressionStore(context)
        for (candidate in plan) {
            var policyChanged = false
            val deletion = localCopyDeletionGuard.withLock {
                val latestPolicy = LocalBookStoragePolicyStore(context).read()
                if (!evictionPolicyIsStillCurrent(storagePolicy, latestPolicy)) {
                    policyChanged = true
                    return@withLock null
                }
                val latestActive = ActiveReaderStore(context)
                    .read(candidate.serverUrl, candidate.storageScopeId)?.fileId
                if (latestActive in candidate.fileIds) return@withLock null
                val latestLeases = LocalBookReaderLeaseStore(context).readLive()
                if (latestLeases.any { lease ->
                        (lease.serverUrl == candidate.serverUrl &&
                            lease.fileId in candidate.fileIds) ||
                            candidate.localPaths.any { recordPath ->
                                lease.localPath != null &&
                                    runCatching {
                                        File(recordPath).canonicalFile ==
                                            File(lease.localPath).canonicalFile
                                    }.getOrDefault(false)
                            }
                    }
                ) return@withLock null
                candidate.fileIds.forEach { fileId ->
                    suppressionStore.suppress(
                        serverUrl = candidate.serverUrl,
                        profileId = candidate.profileId,
                        fileId = fileId,
                        sourceRevision = candidate.sourceRevisions[fileId],
                        reason = AutomaticDownloadSuppressionReason.CAPACITY_EVICTED,
                        storageGeneration = storagePolicy.generation,
                        storageScopeId = candidate.storageScopeId
                    )
                }
                store.deleteGroupIfNotQueuedOrAttempted(
                    candidate.serverUrl,
                    candidate.fileIds,
                    candidate.storageScopeId
                )
            }
            if (policyChanged) {
                return@withContext AutomaticCapacityResult(
                    allowed = false,
                    removedFileIds = removed,
                    message = "The automatic-download policy changed before removal completed."
                )
            }
            if (deletion == null) continue
            if (deletion.protectedByTransfer) {
                candidate.fileIds.forEach { fileId ->
                    suppressionStore.clear(
                        candidate.serverUrl,
                        candidate.profileId,
                        fileId,
                        candidate.storageScopeId
                    )
                }
                continue
            }
            deletion.failedFileIds.forEach { fileId ->
                suppressionStore.clear(
                    candidate.serverUrl,
                    candidate.profileId,
                    fileId,
                    candidate.storageScopeId
                )
            }
            removed += deletion.deletedFileIds
            val groupRemoved = deletion.deletedFileIds.containsAll(candidate.fileIds)
            val candidateIsCurrentAccount = candidate.storageScopeId ==
                downloadStorageScopeId(context, candidate.serverUrl)
            if (groupRemoved && candidateIsCurrentAccount) {
                LibraryCatalogStore(context).updateLocalPath(
                    candidate.serverUrl,
                    candidate.bookId,
                    null
                )
                BrowserSnapshotStore(context).updateLocalPath(
                    candidate.serverUrl,
                    candidate.bookId,
                    null
                )
            }
            if (candidateIsCurrentAccount) {
                deletion.deletedFileIds.forEach { fileId ->
                    BookDetailCacheStore(context).remove(
                        candidate.serverUrl,
                        candidate.bookId,
                        fileId
                    )
                }
            }
        }
        val after = AppStorageManager(context).usage()
        admission = automaticStorageAdmission(
            currentDownloadedBytes = after.downloadedBytes,
            availableBytes = after.availableBytes,
            incomingFinalBytes = incomingFinalBytes,
            incomingPeakBytes = incomingPeakBytes,
            maximumBytes = storagePolicy.maximumBytes,
            reserveBytes = storagePolicy.reserveBytes
        )
        AutomaticCapacityResult(
            allowed = admission.allowedWithoutReclaim,
            removedFileIds = removed,
            message = capacityMessage(admission).takeUnless { admission.allowedWithoutReclaim }
        )
    }

    private fun capacityMessage(admission: AutomaticStorageAdmission): String = when {
        admission.reserveShortfallBytes > 0L && admission.maximumExceededByBytes > 0L ->
            "The free-space reserve and maximum local-book storage limit would be exceeded."
        admission.reserveShortfallBytes > 0L ->
            "The configured free-space reserve would be exceeded."
        else -> "The maximum local-book storage limit would be exceeded."
    }
}

internal data class AutomaticDownloadCandidate(
    val book: BookSummary,
    val expectedSizeBytes: Long?
)

internal data class AutomaticScanAdvance(
    val nextPage: Int,
    val nextLibraryIndex: Int,
    val cycleComplete: Boolean
)

internal fun automaticScanAdvance(
    currentPage: Int,
    currentLibraryIndex: Int,
    libraryCount: Int,
    pageFullyExamined: Boolean,
    lastPage: Boolean
): AutomaticScanAdvance {
    require(libraryCount > 0)
    val nextLibraryIndex = if (pageFullyExamined && lastPage) {
        (currentLibraryIndex + 1) % libraryCount
    } else {
        currentLibraryIndex
    }
    return AutomaticScanAdvance(
        nextPage = when {
            !pageFullyExamined -> currentPage
            lastPage -> 0
            else -> currentPage + 1
        },
        nextLibraryIndex = nextLibraryIndex,
        cycleComplete = pageFullyExamined && lastPage && nextLibraryIndex == 0
    )
}

internal fun automaticScanNextItemIndex(
    currentItemIndex: Int,
    fullyExaminedItems: Int,
    advancePastPage: Boolean,
    pageSize: Int
): Int = if (advancePastPage) {
    0
} else {
    (currentItemIndex.coerceAtLeast(0) + fullyExaminedItems.coerceAtLeast(0))
        .coerceAtMost(pageSize.coerceAtLeast(0))
}

internal fun automaticDurablyExaminedItemCount(
    candidateIdsByItem: List<Set<String>>,
    persistedQueueFileIds: Set<String>
): Int = candidateIdsByItem
    .takeWhile { candidateIds -> candidateIds.all(persistedQueueFileIds::contains) }
    .size

internal data class ClearLocalCopiesResult(
    val removedCount: Int,
    val removedBytes: Long,
    val keptActiveCount: Int,
    val failedCount: Int
)

internal suspend fun clearLocalCopiesForServer(
    context: Context,
    serverUrl: String
): ClearLocalCopiesResult = withContext(Dispatchers.IO) {
    val storageScopeId = downloadStorageScopeId(context, serverUrl)
        ?: return@withContext ClearLocalCopiesResult(0, 0L, 0, 0)
    val store = DownloadStore(context)
    var removedCount = 0
    var removedBytes = 0L
    var kept = 0
    var failed = 0
    val records = store.readAll(serverUrl, storageScopeId)
    val removedBookIds = mutableSetOf<String>()
    val suppressionStore = AutomaticDownloadSuppressionStore(context)
    val storageGeneration = LocalBookStoragePolicyStore(context).read().generation
    groupDownloadedRecordsByBook(records).forEach { (bookId, bookRecords) ->
        val fileIds = bookRecords.mapTo(mutableSetOf()) { it.fileId }
        val sizesByFileId = bookRecords.associate { record ->
            record.fileId to File(record.localPath).length().coerceAtLeast(0L)
        }
        val deletion = localCopyDeletionGuard.withLock {
            val currentActive = ActiveReaderStore(context).read(serverUrl, storageScopeId)?.fileId
            if (currentActive in fileIds) return@withLock null
            val liveLeases = LocalBookReaderLeaseStore(context).readLive()
            val protectedByLease = liveLeases.any { lease ->
                (lease.serverUrl == serverUrl && lease.fileId in fileIds) ||
                    bookRecords.any { record ->
                        lease.localPath != null && runCatching {
                            File(lease.localPath).canonicalFile == File(record.localPath).canonicalFile
                        }.getOrDefault(false)
                    }
            }
            if (protectedByLease) return@withLock null
            bookRecords.forEach { record ->
                suppressionStore.suppress(
                    serverUrl = record.serverUrl,
                    profileId = record.profileId,
                    fileId = record.fileId,
                    sourceRevision = record.sourceUpdatedAtMillis,
                    reason = AutomaticDownloadSuppressionReason.USER_CLEARED,
                    storageGeneration = storageGeneration,
                    storageScopeId = storageScopeId
                )
            }
            store.deleteGroupIfNotQueuedOrAttempted(serverUrl, fileIds, storageScopeId)
        }
        if (deletion == null || deletion.protectedByTransfer) {
            bookRecords.forEach { record ->
                suppressionStore.clear(
                    record.serverUrl,
                    record.profileId,
                    record.fileId,
                    storageScopeId
                )
            }
            kept += bookRecords.size
        } else {
            val removedRecords = bookRecords.filter { it.fileId in deletion.deletedFileIds }
            removedCount += removedRecords.size
            removedRecords.forEach { record ->
                removedBytes = saturatingAdd(
                    removedBytes,
                    sizesByFileId[record.fileId] ?: 0L
                )
                BookDetailCacheStore(context).remove(serverUrl, bookId, record.fileId)
            }
            failed += deletion.failedFileIds.size
            bookRecords.filter { it.fileId in deletion.failedFileIds }.forEach { record ->
                suppressionStore.clear(
                    record.serverUrl,
                    record.profileId,
                    record.fileId,
                    storageScopeId
                )
            }
            if (deletion.deletedFileIds.containsAll(fileIds)) removedBookIds += bookId
        }
    }
    val remainingBookIds = store.readAll(serverUrl, storageScopeId)
        .mapTo(mutableSetOf()) { it.bookId }
    removedBookIds.filterNot { it in remainingBookIds }.forEach { bookId ->
        LibraryCatalogStore(context).updateLocalPath(serverUrl, bookId, null)
        BrowserSnapshotStore(context).updateLocalPath(serverUrl, bookId, null)
    }
    ClearLocalCopiesResult(removedCount, removedBytes, kept, failed)
}

class AutomaticDownloadPlannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = plannerMutex.withLock { runPlanner() }

    private suspend fun runPlanner(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val serverUrl = inputData.getString(KEY_SERVER_URL).orEmpty()
        val storageScopeId = inputData.getString(KEY_STORAGE_SCOPE_ID).orEmpty()
        val requestedGeneration = inputData.getLong(KEY_GENERATION, -1L)
        if (profileId.isBlank() || serverUrl.isBlank() || storageScopeId.isBlank()) {
            return Result.failure()
        }
        val policyStore = AutomaticDownloadPolicyStore(applicationContext)
        val policy = policyStore.read(profileId, serverUrl, storageScopeId)
        val statusStore = AutomaticDownloadStatusStore(applicationContext)
        if (!policy.enabled || policy.generation != requestedGeneration) return Result.success()
        val activeProfile = ServerProfileStore(applicationContext).active()
        if (activeProfile?.id != profileId ||
            !serverUrlsMatch(activeProfile.serverUrl, serverUrl) ||
            downloadStorageScopeId(applicationContext, serverUrl, profileId) != storageScopeId
        ) {
            statusStore.write(
                profileId, storageScopeId,
                AutomaticDownloadStatus(
                    state = AutomaticDownloadRunState.PAUSED,
                    message = "Automatic downloads are paused until this server profile is active."
                )
            )
            return Result.success()
        }
        val repository = resolveProviderRepository(applicationContext, serverUrl)
        if (resolveProviderLoginModule(repository).getSessionState() != SessionState.Authenticated) {
            statusStore.write(
                profileId, storageScopeId,
                AutomaticDownloadStatus(
                    state = AutomaticDownloadRunState.PAUSED,
                    message = "Automatic downloads are paused until you sign in again."
                )
            )
            return Result.success()
        }
        // Remove older-generation queue entries before recovery. Pumping first could revive an
        // obsolete charging or network constraint and block all current work for this server.
        removeAutomaticTransfersThroughGeneration(
            context = applicationContext,
            profileId = policy.profileId,
            serverUrl = policy.serverUrl,
            storageScopeId = policy.storageScopeId,
            maximumGeneration = policy.generation - 1L
        )
        DownloadQueuePump.enqueueNext(applicationContext, serverUrl, storageScopeId)
        val cycleStatus = statusStore.read(profileId, storageScopeId).takeIf {
            it.state == AutomaticDownloadRunState.RUNNING
        } ?: AutomaticDownloadStatus()
        statusStore.write(
            profileId, storageScopeId,
            cycleStatus.copy(
                state = AutomaticDownloadRunState.RUNNING,
                message = "Scanning selected libraries."
            )
        )
        return try {
            val libraries = resolveProviderLibraryModule(repository).loadLibraries()
            val selected = when (policy.scope) {
                AutomaticDownloadScope.ALL_LIBRARIES -> libraries
                AutomaticDownloadScope.SELECTED_LIBRARIES ->
                    libraries.filter { it.id in policy.selectedLibraryIds }
            }
            if (selected.isEmpty()) {
                statusStore.write(
                    profileId, storageScopeId,
                    AutomaticDownloadStatus(
                        state = AutomaticDownloadRunState.PAUSED,
                        message = "Select at least one library."
                    )
                )
                return Result.success()
            }
            val previous = statusStore.read(profileId, storageScopeId)
            val libraryIndex = previous.cursorLibraryId
                ?.let { cursor -> selected.indexOfFirst { it.id == cursor } }
                ?.takeIf { it >= 0 }
                ?: 0
            val library = selected[libraryIndex]
            val stateStore = AutomaticDownloadStateStore(applicationContext)
            val scan = stateStore.readScan(
                profileId,
                storageScopeId,
                library.id,
                policy.baselineGeneration
            )
            val page = resolveProviderBookCatalogModule(repository)
                .loadBooksPage(library.id, scan.nextPage)
            val pageSize = page.size?.takeIf { it > 0 } ?: page.items.size.coerceAtLeast(1)
            val total = page.total
            val lastPage = page.items.isEmpty() || page.items.size < pageSize ||
                (total != null && (scan.nextPage + 1L) * pageSize >= total)
            var queued = 0
            var skipped = 0
            var failed = 0
            var checked = 0
            var examinedItems = 0
            var pageFullyExamined = true
            val baselineOnly = policy.initialMode == AutomaticDownloadInitialMode.NEW_BOOKS_ONLY &&
                !scan.baselineComplete
            if (baselineOnly) {
                stateStore.observe(
                    profileId,
                    storageScopeId,
                    library.id,
                    page.items.map { it.id }
                )
            } else {
                val candidates = mutableListOf<AutomaticDownloadCandidate>()
                val fullyExaminedCandidateIds = mutableListOf<Set<String>>()
                val requiredFileIdsByBook = mutableMapOf<String, Set<String>>()
                val suppressionStore = AutomaticDownloadSuppressionStore(applicationContext)
                val storageGeneration = LocalBookStoragePolicyStore(applicationContext)
                    .read().generation
                val handledFileIds = DownloadStore(applicationContext)
                    .readDownloadQueue(serverUrl, storageScopeId)
                    .mapTo(mutableSetOf()) { it.fileId }
                val completedFileIds = mutableSetOf<String>()
                bookLoop@ for (book in page.items.drop(scan.nextItemIndex)) {
                    if (candidates.size >= MAX_QUEUED_FILES_PER_RUN) {
                        pageFullyExamined = false
                        break
                    }
                    checked += 1
                    if (policy.initialMode == AutomaticDownloadInitialMode.NEW_BOOKS_ONLY &&
                        stateStore.isObserved(storageScopeId, library.id, book.id)
                    ) {
                        skipped += 1
                        fullyExaminedCandidateIds.add(emptySet())
                        continue
                    }
                    val detail = runCatching {
                        resolveProviderBookDetailModule(repository).loadBookDetail(book)
                    }.getOrNull()
                    if (detail == null) {
                        failed += 1
                        fullyExaminedCandidateIds.add(emptySet())
                        continue
                    }
                    val options = automaticDownloadOptions(detail, book, policy.allReadableCopies)
                    if (options.isEmpty()) {
                        skipped += 1
                        fullyExaminedCandidateIds.add(emptySet())
                        continue
                    }
                    requiredFileIdsByBook[book.id] = options.mapNotNull { it.fileId }.toSet()
                    var bookFullyExamined = true
                    val bookCandidateIds = mutableSetOf<String>()
                    for (option in options) {
                        val candidateBook = option.book.copy(
                            libraryId = book.libraryId,
                            id = book.id,
                            title = book.title,
                            author = book.author ?: option.book.author,
                            seriesId = book.seriesId ?: option.book.seriesId,
                            seriesName = book.seriesName ?: option.book.seriesName,
                            seriesIndex = book.seriesIndex ?: option.book.seriesIndex,
                            readStatus = book.readStatus,
                            isRead = book.isRead,
                            lastReadAtMillis = book.lastReadAtMillis,
                            filename = option.filename ?: option.book.filename
                        )
                        val optionFileId = candidateBook.fileId ?: continue
                        if (suppressionStore.isSuppressed(
                                serverUrl = serverUrl,
                                profileId = profileId,
                                fileId = optionFileId,
                                sourceRevision = candidateBook.updatedAtMillis,
                                storageGeneration = storageGeneration,
                                storageScopeId = storageScopeId
                            )
                        ) {
                            skipped += 1
                            continue
                        }
                        val existing = DownloadStore(applicationContext).find(
                            serverUrl,
                            optionFileId,
                            storageScopeId
                        )
                        if (existing != null &&
                            !downloadUpdateAvailable(candidateBook, existing) &&
                            downloadedFilePassesIntegrity(candidateBook, File(existing.localPath))
                        ) {
                            handledFileIds += optionFileId
                            completedFileIds += optionFileId
                            skipped += 1
                            continue
                        }
                        if (optionFileId in handledFileIds) {
                            skipped += 1
                            continue
                        }
                        if (candidates.size >= MAX_QUEUED_FILES_PER_RUN) {
                            pageFullyExamined = false
                            bookFullyExamined = false
                            break
                        }
                        val size = option.sizeBytes?.takeIf { it > 0L }
                        candidates += AutomaticDownloadCandidate(candidateBook, size)
                        bookCandidateIds += optionFileId
                    }
                    if (!bookFullyExamined) break@bookLoop
                    fullyExaminedCandidateIds.add(bookCandidateIds)
                }
                var enqueuedIds: Set<String> = emptySet()
                if (candidates.isNotEmpty()) {
                    val currentPolicy = policyStore.read(profileId, serverUrl, storageScopeId)
                    if (!currentPolicy.enabled ||
                        currentPolicy.generation != policy.generation ||
                        downloadStorageScopeId(
                            applicationContext,
                            serverUrl,
                            profileId
                        ) != storageScopeId
                    ) {
                        statusStore.write(
                            profileId, storageScopeId,
                            AutomaticDownloadStatus(
                                state = AutomaticDownloadRunState.CANCELLED,
                                message = "Automatic-download settings changed during this pass."
                            )
                        )
                        return Result.success()
                    }
                    enqueuedIds = enqueueAutomaticDownloadBatch(
                        context = applicationContext,
                        profileId = profileId,
                        serverUrl = serverUrl,
                        policy = policy,
                        candidates = candidates
                    )
                    queued = enqueuedIds.size
                    handledFileIds += enqueuedIds
                }
                examinedItems = automaticDurablyExaminedItemCount(
                    fullyExaminedCandidateIds,
                    enqueuedIds
                )
                if (examinedItems != fullyExaminedCandidateIds.size) {
                    pageFullyExamined = false
                }
                if (policy.initialMode == AutomaticDownloadInitialMode.NEW_BOOKS_ONLY) {
                    val newlyHandled = requiredFileIdsByBook
                        .filterValues { required -> required.isNotEmpty() && completedFileIds.containsAll(required) }
                        .keys
                    stateStore.observe(profileId, storageScopeId, library.id, newlyHandled)
                }
            }
            val advancePastPage = baselineOnly || pageFullyExamined
            val advance = automaticScanAdvance(
                currentPage = scan.nextPage,
                currentLibraryIndex = libraryIndex,
                libraryCount = selected.size,
                pageFullyExamined = advancePastPage,
                lastPage = lastPage
            )
            stateStore.saveScan(
                scan.copy(
                    nextPage = advance.nextPage,
                    nextItemIndex = automaticScanNextItemIndex(
                        currentItemIndex = scan.nextItemIndex,
                        fullyExaminedItems = examinedItems,
                        advancePastPage = advancePastPage,
                        pageSize = page.items.size
                    ),
                    baselineComplete = scan.baselineComplete || (baselineOnly && lastPage),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            val cycleComplete = advance.cycleComplete
            statusStore.write(
                profileId, storageScopeId,
                AutomaticDownloadStatus(
                    state = if (cycleComplete) {
                        AutomaticDownloadRunState.COMPLETE
                    } else {
                        AutomaticDownloadRunState.RUNNING
                    },
                    checked = previous.checked + if (baselineOnly) page.items.size else checked,
                    queued = previous.queued + queued,
                    skipped = previous.skipped + skipped,
                    failed = previous.failed + failed,
                    cursorLibraryId = selected[advance.nextLibraryIndex].id,
                    lastRunAtMillis = System.currentTimeMillis(),
                    message = when {
                        cycleComplete -> "Automatic-download scan completed."
                        baselineOnly -> "Recorded the current ${library.name} catalog as the new-books baseline."
                        queued > 0 -> "Queued $queued file${if (queued == 1) "" else "s"} from ${library.name}."
                        else -> "No eligible downloads found in this ${library.name} batch."
                    }
                )
            )
            val automaticQueueRemains = DownloadStore(applicationContext)
                .readDownloadQueue(serverUrl, storageScopeId)
                .any { it.origin == DownloadOrigin.AUTOMATIC }
            if (queued == 0 && !automaticQueueRemains && !cycleComplete) {
                AutomaticDownloadScheduler.enqueueContinuation(applicationContext, policy)
            }
            Result.success()
        } catch (error: AuthenticationRequiredException) {
            statusStore.write(
                profileId, storageScopeId,
                AutomaticDownloadStatus(
                    state = AutomaticDownloadRunState.PAUSED,
                    message = "Automatic downloads are paused until you sign in again."
                )
            )
            Result.success()
        } catch (error: Throwable) {
            statusStore.write(
                profileId, storageScopeId,
                AutomaticDownloadStatus(
                    state = AutomaticDownloadRunState.FAILED,
                    failed = 1,
                    lastRunAtMillis = System.currentTimeMillis(),
                    message = error.message ?: "Automatic download planning failed."
                )
            )
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    private companion object {
        val plannerMutex = kotlinx.coroutines.sync.Mutex()
        const val KEY_PROFILE_ID = "profile-id"
        const val KEY_SERVER_URL = "server-url"
        const val KEY_STORAGE_SCOPE_ID = "storage-scope-id"
        const val KEY_GENERATION = "generation"
        const val MAX_QUEUED_FILES_PER_RUN = 5

        fun input(
            profileId: String,
            serverUrl: String,
            storageScopeId: String,
            generation: Long
        ) = workDataOf(
            KEY_PROFILE_ID to profileId,
            KEY_SERVER_URL to serverUrl,
            KEY_STORAGE_SCOPE_ID to storageScopeId,
            KEY_GENERATION to generation
        )
    }

    internal object Requests {
        fun input(profileId: String, serverUrl: String, storageScopeId: String, generation: Long) =
            Companion.input(profileId, serverUrl, storageScopeId, generation)
    }
}

class AutomaticDownloadMaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val serverUrl = inputData.getString(KEY_SERVER_URL).orEmpty()
        val storageScopeId = inputData.getString(KEY_STORAGE_SCOPE_ID).orEmpty()
        val generation = inputData.getLong(KEY_GENERATION, -1L)
        if (profileId.isBlank() || serverUrl.isBlank() || storageScopeId.isBlank()) {
            return Result.failure()
        }
        val policy = AutomaticDownloadPolicyStore(applicationContext)
            .read(profileId, serverUrl, storageScopeId)
        if (policy.generation != generation ||
            downloadStorageScopeId(applicationContext, serverUrl, profileId) != storageScopeId
        ) {
            return Result.success()
        }
        val storagePolicy = LocalBookStoragePolicyStore(applicationContext).read()
        if (!storagePolicy.automaticRemovalEnabled) {
            return Result.success()
        }
        return runCatching {
            AutomaticDownloadStorage.ensureCapacity(
                context = applicationContext,
                serverUrl = serverUrl,
                profileId = profileId,
                policy = policy,
                incomingFinalBytes = 0L,
                incomingPeakBytes = 0L,
                protectedFileIds = emptySet(),
                allowRemoval = true
            )
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    internal companion object {
        private const val KEY_PROFILE_ID = "profile-id"
        private const val KEY_SERVER_URL = "server-url"
        private const val KEY_STORAGE_SCOPE_ID = "storage-scope-id"
        private const val KEY_GENERATION = "generation"

        fun input(policy: AutomaticDownloadPolicy) = workDataOf(
            KEY_PROFILE_ID to policy.profileId,
            KEY_SERVER_URL to policy.serverUrl,
            KEY_STORAGE_SCOPE_ID to policy.storageScopeId,
            KEY_GENERATION to policy.generation
        )
    }
}

internal fun automaticDownloadOptions(
    detail: BookDetailInfo,
    fallback: BookSummary,
    allReadableCopies: Boolean
): List<BookFileOption> {
    val readable = detail.availableFiles
        .filter { it.fileId != null && it.mediaKind != MediaKind.UNKNOWN }
        .distinctBy { it.fileId }
    if (allReadableCopies) return readable
    if (fallback.mediaKind == MediaKind.AUDIO) {
        return AudiobookTimeline.downloadableAudioFiles(readable)
    }
    val preferredId = detail.book.fileId ?: fallback.fileId
    return listOfNotNull(readable.firstOrNull { it.fileId == preferredId })
}

internal object AutomaticDownloadScheduler {
    private const val PERIODIC_WORK = "automatic-book-download-periodic"
    private const val PERIODIC_MAINTENANCE_WORK = "automatic-book-download-maintenance"
    private const val IMMEDIATE_MAINTENANCE_WORK = "automatic-book-download-maintenance-now"
    private const val MANUAL_WORK_PREFIX = "automatic-book-download-now"
    private const val CONTINUATION_WORK_PREFIX = "automatic-book-download-continuation"
    private const val TAG = "automatic-book-download"

    fun reconfigure(
        context: Context,
        policy: AutomaticDownloadPolicy,
        deferFirstPeriodicRun: Boolean = false
    ) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(continuationName(policy))
        if (!policy.enabled ||
            (policy.scope == AutomaticDownloadScope.SELECTED_LIBRARIES &&
                policy.selectedLibraryIds.isEmpty())
        ) {
            manager.cancelUniqueWork(periodicName(policy))
            cancelAutomaticTransfers(
                context = context,
                profileId = policy.profileId,
                serverUrl = policy.serverUrl,
                storageScopeId = policy.storageScopeId,
                maximumGeneration = policy.generation
            )
        } else {
            val requestBuilder =
                PeriodicWorkRequestBuilder<AutomaticDownloadPlannerWorker>(24, TimeUnit.HOURS)
                .setConstraints(policy.constraints())
                .setInputData(
                    AutomaticDownloadPlannerWorker.Requests.input(
                        policy.profileId,
                        policy.serverUrl,
                        policy.storageScopeId,
                        policy.generation
                    )
                )
                .addTag(TAG)
            if (deferFirstPeriodicRun) {
                requestBuilder.setInitialDelay(24, TimeUnit.HOURS)
            }
            val request = requestBuilder.build()
            manager.enqueueUniquePeriodicWork(
                periodicName(policy),
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            cancelStaleAutomaticTransfers(context, policy)
        }
        reconfigureMaintenance(manager, policy)
    }

    fun enqueueNow(context: Context, policy: AutomaticDownloadPolicy): Boolean {
        if (!policy.enabled ||
            (policy.scope == AutomaticDownloadScope.SELECTED_LIBRARIES &&
                policy.selectedLibraryIds.isEmpty())
        ) return false
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(continuationName(policy))
        AutomaticDownloadStatusStore(context).write(
            policy.profileId,
            policy.storageScopeId,
            AutomaticDownloadStatus(
                state = AutomaticDownloadRunState.RUNNING,
                message = "Waiting for the selected network and charging conditions."
            )
        )
        val request = OneTimeWorkRequestBuilder<AutomaticDownloadPlannerWorker>()
            .setConstraints(policy.constraints())
            .setInputData(
                AutomaticDownloadPlannerWorker.Requests.input(
                    policy.profileId,
                    policy.serverUrl,
                    policy.storageScopeId,
                    policy.generation
                )
            )
            .addTag(TAG)
            .build()
        manager.enqueueUniqueWork(
            "$MANUAL_WORK_PREFIX:${workIdentity(policy)}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        return true
    }

    fun enqueueContinuation(context: Context, policy: AutomaticDownloadPolicy): Boolean {
        if (!policy.enabled ||
            (policy.scope == AutomaticDownloadScope.SELECTED_LIBRARIES &&
                policy.selectedLibraryIds.isEmpty())
        ) return false
        val request = OneTimeWorkRequestBuilder<AutomaticDownloadPlannerWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setConstraints(policy.constraints())
            .setInputData(
                AutomaticDownloadPlannerWorker.Requests.input(
                    policy.profileId,
                    policy.serverUrl,
                    policy.storageScopeId,
                    policy.generation
                )
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            continuationName(policy),
            ExistingWorkPolicy.REPLACE,
            request
        )
        return true
    }

    fun cancel(context: Context, policy: AutomaticDownloadPolicy): AutomaticDownloadPolicy {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("$MANUAL_WORK_PREFIX:${workIdentity(policy)}")
        manager.cancelUniqueWork(continuationName(policy))
        val nextPolicy = AutomaticDownloadPolicyStore(context).save(policy)
        cancelAutomaticTransfers(
            context = context,
            profileId = policy.profileId,
            serverUrl = policy.serverUrl,
            storageScopeId = policy.storageScopeId,
            maximumGeneration = policy.generation
        )
        reconfigure(context, nextPolicy, deferFirstPeriodicRun = true)
        AutomaticDownloadStatusStore(context).write(
            policy.profileId,
            policy.storageScopeId,
            AutomaticDownloadStatus(
                state = AutomaticDownloadRunState.CANCELLED,
                message = "Automatic download planning cancelled. Completed files were kept."
            )
        )
        return nextPolicy
    }

    fun pauseForInactiveProfile(context: Context, policy: AutomaticDownloadPolicy) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("$MANUAL_WORK_PREFIX:${workIdentity(policy)}")
        manager.cancelUniqueWork(continuationName(policy))
        cancelAutomaticTransfers(
            context = context,
            profileId = policy.profileId,
            serverUrl = policy.serverUrl,
            storageScopeId = policy.storageScopeId,
            maximumGeneration = policy.generation
        )
        AutomaticDownloadStatusStore(context).write(
            policy.profileId,
            policy.storageScopeId,
            AutomaticDownloadStatus(
                state = AutomaticDownloadRunState.PAUSED,
                message = "Automatic downloads are paused while this server profile is inactive."
            )
        )
    }

    private fun AutomaticDownloadPolicy.constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(chargingRequired)
        .build()

    private fun periodicName(policy: AutomaticDownloadPolicy): String =
        "$PERIODIC_WORK:${workIdentity(policy)}"

    private fun continuationName(policy: AutomaticDownloadPolicy): String =
        "$CONTINUATION_WORK_PREFIX:${workIdentity(policy)}"

    private fun workIdentity(policy: AutomaticDownloadPolicy): String =
        "${policy.profileId}:${policy.storageScopeId}"

    private fun reconfigureMaintenance(
        manager: WorkManager,
        policy: AutomaticDownloadPolicy
    ) {
        val periodicName = "$PERIODIC_MAINTENANCE_WORK:${workIdentity(policy)}"
        val immediateName = "$IMMEDIATE_MAINTENANCE_WORK:${workIdentity(policy)}"
        if (!policy.automaticRemovalEnabled) {
            manager.cancelUniqueWork(periodicName)
            manager.cancelUniqueWork(immediateName)
            return
        }
        val periodic = PeriodicWorkRequestBuilder<AutomaticDownloadMaintenanceWorker>(
            24,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder().setRequiresCharging(policy.chargingRequired).build()
            )
            .setInputData(AutomaticDownloadMaintenanceWorker.input(policy))
            .addTag(TAG)
            .build()
        manager.enqueueUniquePeriodicWork(
            periodicName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
        val immediate = OneTimeWorkRequestBuilder<AutomaticDownloadMaintenanceWorker>()
            .setInputData(AutomaticDownloadMaintenanceWorker.input(policy))
            .addTag(TAG)
            .build()
        manager.enqueueUniqueWork(immediateName, ExistingWorkPolicy.REPLACE, immediate)
    }
}

private fun cancelStaleAutomaticTransfers(context: Context, policy: AutomaticDownloadPolicy) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        removeAutomaticTransfersThroughGeneration(
            context = context,
            profileId = policy.profileId,
            serverUrl = policy.serverUrl,
            storageScopeId = policy.storageScopeId,
            maximumGeneration = policy.generation - 1L
        )
        DownloadQueuePump.enqueueNext(context, policy.serverUrl, policy.storageScopeId)
    }
}

private fun cancelAutomaticTransfers(
    context: Context,
    profileId: String,
    serverUrl: String,
    storageScopeId: String,
    maximumGeneration: Long
) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        removeAutomaticTransfersThroughGeneration(
            context,
            profileId,
            serverUrl,
            storageScopeId,
            maximumGeneration
        )
        DownloadQueuePump.enqueueNext(context, serverUrl, storageScopeId)
    }
}

private suspend fun removeAutomaticTransfersThroughGeneration(
    context: Context,
    profileId: String,
    serverUrl: String,
    storageScopeId: String,
    maximumGeneration: Long
) = withContext(Dispatchers.IO) {
    if (maximumGeneration < 0L) return@withContext
    val store = DownloadStore(context)
    val removed = store.removeAutomaticQueueThroughGeneration(
        serverUrl,
        profileId,
        maximumGeneration,
        storageScopeId
    )
    val manager = WorkManager.getInstance(context)
    removed.forEach { entry ->
        val matchingWork = runCatching {
            manager.getWorkInfosByTag(downloadFileTag(entry.fileId)).get()
        }.getOrDefault(emptyList()).filter { info ->
            !info.state.isFinished &&
                AUTOMATIC_DOWNLOAD_TAG in info.tags &&
                downloadServerTag(entry.serverUrl) in info.tags &&
                downloadStorageScopeTag(entry.storageScopeId) in info.tags &&
                downloadWorkTagsMatchRequest(info.tags, entry.requestId)
        }
        matchingWork.forEach { manager.cancelWorkById(it.id) }
        store.removeAttemptIfOwned(
            entry.serverUrl,
            entry.fileId,
            entry.requestId,
            storageScopeId = storageScopeId
        )
    }
    // A process can die after queue removal but before the paired attempt cleanup. Retire those
    // exact old-generation orphans independently so they cannot protect storage forever.
    store.removeAutomaticAttemptsThroughGeneration(
        serverUrl = serverUrl,
        profileId = profileId,
        maximumGeneration = maximumGeneration,
        storageScopeId = storageScopeId
    )
    val remaining = store.readDownloadQueue(serverUrl, storageScopeId)
        .filter { it.origin == DownloadOrigin.AUTOMATIC }
    val activeAutomaticWork = runCatching {
        manager.getWorkInfosByTag(downloadServerTag(serverUrl)).get()
    }.getOrDefault(emptyList()).filter { info ->
        !info.state.isFinished &&
            AUTOMATIC_DOWNLOAD_TAG in info.tags &&
            downloadStorageScopeTag(storageScopeId) in info.tags
    }
    activeAutomaticWork.forEach { info ->
        val belongsToCleanupGeneration =
            downloadWorkMayBeCancelledThroughGeneration(info.tags, maximumGeneration)
        val requestId = downloadRequestIdFromTags(info.tags)
        val fileId = downloadFileIdFromTags(info.tags)
        val stillOwned = fileId != null && remaining.any { entry ->
            entry.fileId == fileId && if (requestId == null) {
                entry.requestId.isBlank()
            } else {
                entry.requestId == requestId
            }
        }
        if (belongsToCleanupGeneration && !stillOwned) manager.cancelWorkById(info.id)
    }
}

internal fun automaticQueueEntryIsStale(
    entry: DownloadQueueEntry,
    currentPolicy: AutomaticDownloadPolicy
): Boolean = entry.origin == DownloadOrigin.AUTOMATIC &&
    entry.profileId == currentPolicy.profileId &&
    entry.storageScopeId == currentPolicy.storageScopeId &&
    (entry.policyGeneration == null || entry.policyGeneration < currentPolicy.generation)
