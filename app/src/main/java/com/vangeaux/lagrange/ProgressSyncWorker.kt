package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.*

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProgressSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            when (resolveActiveProviderReadingProgressModule(applicationContext).syncPendingProgress()) {
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
