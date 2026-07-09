package com.bookorbit.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ProgressSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            when (BookOrbitRepository(applicationContext).syncPendingProgress()) {
                SyncAttemptResult.Success,
                SyncAttemptResult.AuthenticationBlocked -> Result.success()
                SyncAttemptResult.TransientFailure -> Result.retry()
            }
        }.getOrElse {
            Result.retry()
        }
    }
}
