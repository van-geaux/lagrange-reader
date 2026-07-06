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
            BookOrbitRepository(applicationContext).syncPendingProgress()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
