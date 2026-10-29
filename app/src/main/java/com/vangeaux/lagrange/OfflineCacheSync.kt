package com.vangeaux.lagrange



import android.content.Context
import androidx.annotation.StringRes
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.vangeaux.lagrange.provider.resolveProviderBackgroundCacheModule
import com.vangeaux.lagrange.provider.resolveProviderBookCatalogModule
import com.vangeaux.lagrange.provider.resolveProviderRepository

data class OfflineCacheBatchResult(
    val nextIndex: Int?,
    val processed: Int,
    val downloaded: Int,
    val skipped: Int,
    val unavailable: Int,
    val failed: Int
)

enum class OfflineCacheRunState { IDLE, RUNNING, SUCCEEDED, PARTIAL, FAILED, CANCELLED }

data class OfflineCacheStatus(
    val state: OfflineCacheRunState = OfflineCacheRunState.IDLE,
    val processed: Int = 0,
    val total: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val unavailable: Int = 0,
    val failed: Int = 0,
    val lastSuccessAtMillis: Long? = null,
    val message: String? = null,
    @StringRes val messageRes: Int? = null
)

/**
 * UI-side localized variant of [message].
 *
 * [messageRes] is a transient writer hint and is not persisted, so a status restored from
 * preferences is mapped from [state] (plus the known failure texts) instead. Unknown failure
 * messages fall back to the stored English text.
 */
fun OfflineCacheStatus.localizedMessage(context: Context?): String? {
    if (context == null) return message
    val resId = messageRes ?: offlineCacheMessageResFromStoredState()
    if (resId == null) return message
    return runCatching { context.getString(resId) }.getOrNull() ?: message
}

private fun OfflineCacheStatus.offlineCacheMessageResFromStoredState(): Int? = when (state) {
    OfflineCacheRunState.CANCELLED -> R.string.error_offline_cache_cancelled
    OfflineCacheRunState.PARTIAL -> R.string.error_offline_cache_partial
    OfflineCacheRunState.SUCCEEDED -> R.string.error_offline_cache_up_to_date
    OfflineCacheRunState.FAILED -> when (message) {
        "Select Book details, Cover thumbnails, or both." ->
            R.string.error_offline_cache_select_content
        "The configured server changed while the cache was updating." ->
            R.string.error_offline_cache_server_changed
        "Sign in again to update the offline cache." ->
            R.string.error_offline_cache_sign_in
        else -> null
    }
    else -> null
}

class OfflineCacheStatusStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): OfflineCacheStatus = synchronized(lock) {
        OfflineCacheStatus(
            state = runCatching {
                OfflineCacheRunState.valueOf(preferences.getString(STATE, null).orEmpty())
            }.getOrDefault(OfflineCacheRunState.IDLE),
            processed = preferences.getInt(PROCESSED, 0),
            total = preferences.getInt(TOTAL, 0),
            downloaded = preferences.getInt(DOWNLOADED, 0),
            skipped = preferences.getInt(SKIPPED, 0),
            unavailable = preferences.getInt(UNAVAILABLE, 0),
            failed = preferences.getInt(FAILED, 0),
            lastSuccessAtMillis = preferences.getLong(LAST_SUCCESS, -1L).takeIf { it >= 0L },
            message = preferences.getString(MESSAGE, null)
        )
    }

    fun write(value: OfflineCacheStatus) = synchronized(lock) {
        preferences.edit()
            .putString(STATE, value.state.name)
            .putInt(PROCESSED, value.processed)
            .putInt(TOTAL, value.total)
            .putInt(DOWNLOADED, value.downloaded)
            .putInt(SKIPPED, value.skipped)
            .putInt(UNAVAILABLE, value.unavailable)
            .putInt(FAILED, value.failed)
            .putLong(LAST_SUCCESS, value.lastSuccessAtMillis ?: -1L)
            .putString(MESSAGE, value.message)
            .apply()
    }

    fun update(block: (OfflineCacheStatus) -> OfflineCacheStatus) = synchronized(lock) {
        write(block(read()))
    }

    private companion object {
        const val PREFERENCES_NAME = "offline_cache_status"
        const val STATE = "state"
        const val PROCESSED = "processed"
        const val TOTAL = "total"
        const val DOWNLOADED = "downloaded"
        const val SKIPPED = "skipped"
        const val UNAVAILABLE = "unavailable"
        const val FAILED = "failed"
        const val LAST_SUCCESS = "last_success"
        const val MESSAGE = "message"
        val lock = Any()
    }
}

class OfflineCacheSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val serverUrl = inputData.getString(KEY_SERVER_URL).orEmpty()
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val activeProfileId = ServerProfileStore(applicationContext).activeId().orEmpty()
        val libraryIds = offlineCacheLibraryIdsFromStorage(inputData.getString(KEY_LIBRARY_IDS)).toList()
        val libraryIndex = inputData.getInt(KEY_LIBRARY_INDEX, 0)
        val startIndex = inputData.getInt(KEY_START_INDEX, 0)
        val details = inputData.getBoolean(KEY_DETAILS, true)
        val covers = inputData.getBoolean(KEY_COVERS, false)
        val automatic = inputData.getBoolean(KEY_AUTOMATIC, false)
        if (serverUrl.isBlank() || profileId.isBlank() || profileId != activeProfileId ||
            libraryIds.isEmpty() || libraryIndex !in libraryIds.indices
        ) {
            if (profileId.isNotBlank() && profileId != activeProfileId) {
                fail(
                    applicationContext,
                    R.string.error_offline_cache_server_changed,
                    "The configured server/profile changed while the cache was updating."
                )
                return Result.success()
            }
            finish(applicationContext)
            return Result.success()
        }
        if (!details && !covers) {
            fail(
                applicationContext,
                R.string.error_offline_cache_select_content,
                "Select Book details, Cover thumbnails, or both."
            )
            return Result.success()
        }
        val repository = resolveProviderRepository(applicationContext, serverUrl)
        val catalogModule = resolveProviderBookCatalogModule(repository)
        val cacheModule = resolveProviderBackgroundCacheModule(repository)
        if (!cacheModule.isAvailable) {
            finish(applicationContext)
            return Result.success()
        }
        val statusStore = OfflineCacheStatusStore(applicationContext)
        val libraryId = libraryIds[libraryIndex]
        return try {
            if (startIndex == 0) {
                val catalog = catalogModule.refreshLibraryCatalog(libraryId)
                statusStore.update {
                    it.copy(total = maxOf(it.total, it.processed + catalog.items.size))
                }
            }
            val batch = cacheModule.warmOfflineCacheBatch(
                expectedServerUrl = serverUrl,
                libraryId = libraryId,
                startIndex = startIndex,
                maxItems = ITEMS_PER_BATCH,
                includeDetails = details,
                includeCovers = covers
            ) ?: return Result.success().also {
                fail(
                    applicationContext,
                    R.string.error_offline_cache_server_changed,
                    "The configured server changed while the cache was updating."
                )
            }
            statusStore.update { current ->
                current.copy(
                    processed = current.processed + batch.processed,
                    downloaded = current.downloaded + batch.downloaded,
                    skipped = current.skipped + batch.skipped,
                    unavailable = current.unavailable + batch.unavailable,
                    failed = current.failed + batch.failed
                )
            }
            when {
                isStopped -> Result.success()
                batch.nextIndex != null -> {
                    enqueueContinuation(
                        applicationContext, serverUrl, libraryIds, libraryIndex,
                        batch.nextIndex, details, covers, automatic, profileId
                    )
                    Result.success()
                }
                libraryIndex + 1 < libraryIds.size -> {
                    enqueueContinuation(
                        applicationContext, serverUrl, libraryIds, libraryIndex + 1,
                        0, details, covers, automatic, profileId
                    )
                    Result.success()
                }
                else -> Result.success().also { finish(applicationContext) }
            }
        } catch (_: AuthenticationRequiredException) {
            fail(
                applicationContext,
                R.string.error_offline_cache_sign_in,
                "Sign in again to update the offline cache."
            )
            Result.success()
        } catch (error: HttpRequestException) {
            if (isRetryableOfflineCacheError(error)) return Result.retry()
            statusStore.update { it.copy(failed = it.failed + 1) }
            if (libraryIndex + 1 < libraryIds.size) {
                enqueueContinuation(
                    applicationContext, serverUrl, libraryIds, libraryIndex + 1,
                    0, details, covers, automatic, profileId
                )
            } else {
                finish(applicationContext)
            }
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "bookorbit-offline-cache-sync"
        private const val WORK_NAME = "bookorbit-offline-cache-sync"
        private const val KEY_SERVER_URL = "server-url"
        private const val KEY_PROFILE_ID = "profile-id"
        private const val KEY_LIBRARY_IDS = "library-ids"
        private const val KEY_LIBRARY_INDEX = "library-index"
        private const val KEY_START_INDEX = "start-index"
        private const val KEY_DETAILS = "details"
        private const val KEY_COVERS = "covers"
        private const val KEY_AUTOMATIC = "automatic"
        private const val ITEMS_PER_BATCH = 25

        fun enqueueManual(context: Context, serverUrl: String, preferences: AppPreferences): Boolean =
            enqueue(context, serverUrl, preferences, automatic = false)

        internal fun enqueueAutomatic(context: Context, serverUrl: String, preferences: AppPreferences): Boolean =
            enqueue(context, serverUrl, preferences, automatic = true)

        private fun enqueue(
            context: Context,
            serverUrl: String,
            preferences: AppPreferences,
            automatic: Boolean
        ): Boolean {
            val libraries = preferences.offlineCacheLibraryIds
            if (serverUrl.isBlank() || libraries.isEmpty() ||
                (!preferences.offlineCacheDetailsEnabled && !preferences.offlineCacheCoversEnabled)
            ) return false
            val statusStore = OfflineCacheStatusStore(context)
            if (!automatic || statusStore.read().state != OfflineCacheRunState.RUNNING) {
                statusStore.write(OfflineCacheStatus(state = OfflineCacheRunState.RUNNING))
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (automatic) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                request(
                    serverUrl, libraries.toList().sorted(), 0, 0,
                    preferences.offlineCacheDetailsEnabled,
                    preferences.offlineCacheCoversEnabled,
                    automatic,
                    ServerProfileStore(context).activeId().orEmpty()
                )
            )
            return true
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
            OfflineCacheStatusStore(context).update {
                it.copy(
                    state = OfflineCacheRunState.CANCELLED,
                    message = localizedUiMessage(
                        context,
                        R.string.error_offline_cache_cancelled,
                        "Offline cache update cancelled."
                    ),
                    messageRes = R.string.error_offline_cache_cancelled
                )
            }
        }

        private fun enqueueContinuation(
            context: Context,
            serverUrl: String,
            libraryIds: List<String>,
            libraryIndex: Int,
            startIndex: Int,
            details: Boolean,
            covers: Boolean,
            automatic: Boolean,
            profileId: String
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(serverUrl, libraryIds, libraryIndex, startIndex, details, covers, automatic, profileId)
            )
        }

        private fun request(
            serverUrl: String,
            libraryIds: List<String>,
            libraryIndex: Int,
            startIndex: Int,
            details: Boolean,
            covers: Boolean,
            automatic: Boolean,
            profileId: String
        ) = OneTimeWorkRequestBuilder<OfflineCacheSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (automatic) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    KEY_SERVER_URL to serverUrl,
                    KEY_PROFILE_ID to profileId,
                    KEY_LIBRARY_IDS to offlineCacheLibraryIdsStorageValue(libraryIds.toSet()),
                    KEY_LIBRARY_INDEX to libraryIndex,
                    KEY_START_INDEX to startIndex,
                    KEY_DETAILS to details,
                    KEY_COVERS to covers,
                    KEY_AUTOMATIC to automatic
                )
            )
            .addTag(TAG)
            .build()

        private fun finish(context: Context) {
            val store = OfflineCacheStatusStore(context)
            store.update { current ->
                val partial = current.failed > 0
                current.copy(
                    state = if (partial) OfflineCacheRunState.PARTIAL else OfflineCacheRunState.SUCCEEDED,
                    lastSuccessAtMillis = System.currentTimeMillis(),
                    message = if (partial) {
                        localizedUiMessage(
                            context,
                            R.string.error_offline_cache_partial,
                            "Updated with some failures."
                        )
                    } else {
                        localizedUiMessage(
                            context,
                            R.string.error_offline_cache_up_to_date,
                            "Offline cache is up to date."
                        )
                    },
                    messageRes = if (partial) {
                        R.string.error_offline_cache_partial
                    } else {
                        R.string.error_offline_cache_up_to_date
                    }
                )
            }
        }

        private fun fail(context: Context, @StringRes messageRes: Int, message: String) {
            OfflineCacheStatusStore(context).update {
                it.copy(
                    state = OfflineCacheRunState.FAILED,
                    message = localizedUiMessage(context, messageRes, message),
                    messageRes = messageRes
                )
            }
        }
    }
}

class OfflineCacheAutoRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = AppPreferencesStore(applicationContext).read()
        if (!preferences.offlineCacheAutoRefreshEnabled) return Result.success()
        val serverUrl = ServerProfileStore(applicationContext).active()?.serverUrl.orEmpty()
        OfflineCacheSyncWorker.enqueueAutomatic(applicationContext, serverUrl, preferences)
        return Result.success()
    }
}

object OfflineCacheScheduler {
    private const val PERIODIC_WORK_NAME = "bookorbit-offline-cache-auto-refresh"

    fun reconfigure(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val preferences = AppPreferencesStore(context).read()
        if (
            !preferences.offlineCacheAutoRefreshEnabled ||
            preferences.offlineCacheLibraryIds.isEmpty() ||
            (!preferences.offlineCacheDetailsEnabled && !preferences.offlineCacheCoversEnabled)
        ) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<OfflineCacheAutoRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        OfflineCacheSyncWorker.cancel(context)
    }
}

internal fun isRetryableOfflineCacheError(error: Throwable): Boolean = when (error) {
    is HttpRequestException -> error.code >= 500 || error.code == 408 || error.code == 429
    is IOException -> true
    else -> false
}
