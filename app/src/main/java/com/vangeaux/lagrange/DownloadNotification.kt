package com.vangeaux.lagrange

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager

internal const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "bookorbit-downloads"
internal const val DOWNLOAD_NOTIFICATION_GROUP = "bookorbit-active-downloads"
internal const val DOWNLOAD_NOTIFICATION_CANCEL_ACTION = "com.vangeaux.lagrange.CANCEL_DOWNLOAD"
internal const val DOWNLOAD_NOTIFICATION_FILE_ID_EXTRA = "file-id"
internal const val DOWNLOAD_NOTIFICATION_SERVER_URL_EXTRA = "server-url"
private const val DOWNLOAD_NOTIFICATION_ID_BASE = 40_000
private const val DOWNLOAD_SUMMARY_NOTIFICATION_ID = DOWNLOAD_NOTIFICATION_ID_BASE - 1

/** Stable notification identity for a file, independent of WorkManager UUIDs or process lifetime. */
internal fun downloadNotificationId(fileId: String): Int =
    DOWNLOAD_NOTIFICATION_ID_BASE + fileId.hashCode()

internal fun downloadNotificationTitle(title: String): String = "Downloading $title"

internal fun downloadNotificationProgress(progress: Float?): DownloadNotificationProgress =
    progress?.let { DownloadNotificationProgress.Determinate((it * 100f).toInt().coerceIn(0, 100)) }
        ?: DownloadNotificationProgress.Indeterminate

internal sealed interface DownloadNotificationProgress {
    data object Indeterminate : DownloadNotificationProgress
    data class Determinate(val percent: Int) : DownloadNotificationProgress
}

internal fun buildDownloadNotification(
    context: Context,
    title: String,
    fileId: String,
    serverUrl: String,
    progress: Float?
): Notification {
    val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    val contentIntent = PendingIntent.getActivity(
        context,
        downloadNotificationId(fileId),
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cancelIntent = PendingIntent.getBroadcast(
        context,
        downloadNotificationId(fileId),
        Intent(context, DownloadNotificationReceiver::class.java).apply {
            action = DOWNLOAD_NOTIFICATION_CANCEL_ACTION
            putExtra(DOWNLOAD_NOTIFICATION_FILE_ID_EXTRA, fileId)
            putExtra(DOWNLOAD_NOTIFICATION_SERVER_URL_EXTRA, serverUrl)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(downloadNotificationTitle(title))
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setGroup(DOWNLOAD_NOTIFICATION_GROUP)
        .setContentIntent(contentIntent)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
        .apply {
            when (val presentation = downloadNotificationProgress(progress)) {
                DownloadNotificationProgress.Indeterminate -> setProgress(0, 0, true)
                is DownloadNotificationProgress.Determinate -> setProgress(100, presentation.percent, false)
            }
        }
        .build()
}

/** Rebuilds the group summary from WorkManager's authoritative active work list. */
internal fun refreshDownloadNotificationSummary(context: Context, currentWorkId: String? = null) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val activeCount = runCatching {
        WorkManager.getInstance(context).getWorkInfosByTag(DOWNLOAD_TAG).get()
            .count { !it.state.isFinished && it.id.toString() != currentWorkId }
    }.getOrDefault(0)
    try {
        if (activeCount > 1) {
            val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            val contentIntent = PendingIntent.getActivity(
                context,
                DOWNLOAD_SUMMARY_NOTIFICATION_ID,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(
                DOWNLOAD_SUMMARY_NOTIFICATION_ID,
                NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Downloading $activeCount books")
                    .setContentText("Book downloads are in progress")
                    .setGroup(DOWNLOAD_NOTIFICATION_GROUP)
                    .setGroupSummary(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(contentIntent)
                    .build()
            )
        } else {
            manager.cancel(DOWNLOAD_SUMMARY_NOTIFICATION_ID)
        }
    } catch (_: SecurityException) {
        // POST_NOTIFICATIONS can be denied. Notifications are optional UI, never download control flow.
    }
}

internal fun clearDownloadNotification(context: Context, fileId: String, currentWorkId: String?) {
    runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(downloadNotificationId(fileId)) }
    refreshDownloadNotificationSummary(context, currentWorkId)
}

internal class DownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DOWNLOAD_NOTIFICATION_CANCEL_ACTION) return
        val serverUrl = intent.getStringExtra(DOWNLOAD_NOTIFICATION_SERVER_URL_EXTRA)
        val fileId = intent.getStringExtra(DOWNLOAD_NOTIFICATION_FILE_ID_EXTRA)
        if (serverUrl.isNullOrBlank() || fileId.isNullOrBlank()) return
        WorkManager.getInstance(context).cancelUniqueWork(downloadUniqueWorkName(serverUrl, fileId))
        context.getSystemService(NotificationManager::class.java)?.cancel(downloadNotificationId(fileId))
        refreshDownloadNotificationSummary(context)
    }
}
