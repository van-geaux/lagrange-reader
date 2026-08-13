package com.vangeaux.lagrange

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class AnnotationMutationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            when (BookOrbitRepository(applicationContext).syncPendingAnnotationMutations()) {
                SyncAttemptResult.Success,
                SyncAttemptResult.AuthenticationBlocked -> Result.success()
                SyncAttemptResult.TransientFailure -> Result.retry()
            }
        }.getOrElse { Result.retry() }
    }
}
