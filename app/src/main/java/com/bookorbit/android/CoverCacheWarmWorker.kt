package com.bookorbit.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit

class CoverCacheWarmWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val serverUrl = inputData.getString(KEY_SERVER_URL).orEmpty()
        val libraryId = inputData.getString(KEY_LIBRARY_ID).orEmpty()
        val startIndex = inputData.getInt(KEY_START_INDEX, 0)
        if (serverUrl.isBlank() || libraryId.isBlank()) return Result.success()

        return try {
            val nextIndex = BookOrbitRepository(applicationContext).warmCoverCacheBatch(
                expectedServerUrl = serverUrl,
                libraryId = libraryId,
                startIndex = startIndex,
                maxDownloads = DOWNLOADS_PER_BATCH
            )
            if (nextIndex != null && !isStopped) {
                enqueueContinuation(applicationContext, serverUrl, libraryId, nextIndex)
            }
            Result.success()
        } catch (_: AuthenticationRequiredException) {
            // A later authenticated catalog refresh starts a new warm-cache chain.
            Result.success()
        } catch (error: HttpRequestException) {
            if (error.code >= 500 || error.code == 408 || error.code == 429) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "bookorbit-cover-cache-warm"
        private const val WORK_NAME = "bookorbit-cover-cache-warm-selected-library"
        private const val KEY_SERVER_URL = "server-url"
        private const val KEY_LIBRARY_ID = "library-id"
        private const val KEY_START_INDEX = "start-index"
        private const val DOWNLOADS_PER_BATCH = 50

        fun enqueue(context: Context, serverUrl: String, libraryId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request(serverUrl, libraryId, startIndex = 0, initial = true)
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }

        private fun enqueueContinuation(
            context: Context,
            serverUrl: String,
            libraryId: String,
            startIndex: Int
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(serverUrl, libraryId, startIndex, initial = false)
            )
        }

        private fun request(
            serverUrl: String,
            libraryId: String,
            startIndex: Int,
            initial: Boolean
        ) = OneTimeWorkRequestBuilder<CoverCacheWarmWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    KEY_SERVER_URL to serverUrl,
                    KEY_LIBRARY_ID to libraryId,
                    KEY_START_INDEX to startIndex
                )
            )
            .addTag(TAG)
            .apply {
                if (initial) setInitialDelay(5, TimeUnit.SECONDS)
            }
            .build()

    }
}
