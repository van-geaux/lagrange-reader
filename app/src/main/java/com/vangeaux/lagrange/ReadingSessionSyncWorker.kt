package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.*

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class ReadingSessionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            when (resolveActiveProviderReadingSessionModule(applicationContext).syncPendingReadingSessions()) {
                SyncAttemptResult.Success,
                SyncAttemptResult.AuthenticationBlocked,
                SyncAttemptResult.Unsupported -> Result.success()
                SyncAttemptResult.TransientFailure -> Result.retry()
            }
        }.getOrElse {
            Result.retry()
        }
    }
}
