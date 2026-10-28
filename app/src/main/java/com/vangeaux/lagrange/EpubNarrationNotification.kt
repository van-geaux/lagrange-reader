package com.vangeaux.lagrange

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.session.MediaSession

internal const val EPUB_NARRATION_NOTIFICATION_ID = 4102
internal const val EPUB_NARRATION_MEDIA_NOTIFICATION_ID = 4103
internal const val EPUB_NARRATION_NOTIFICATION_CHANNEL_ID = "epub_narration_playback"

internal data class EpubNarrationNotificationActions(
    val previous: PendingIntent?,
    val playPause: PendingIntent,
    val next: PendingIntent?,
    val close: PendingIntent
)

internal fun buildEpubNarrationNotification(
    context: Context,
    title: String,
    detail: String,
    isPlaying: Boolean,
    contentIntent: PendingIntent,
    actions: EpubNarrationNotificationActions,
    visibility: Int = NotificationCompat.VISIBILITY_PUBLIC
): Notification {
    val views = RemoteViews(context.packageName, R.layout.notification_readalong).apply {
        setTextViewText(R.id.readalong_sentence, detail)
        actions.previous?.let {
            setImageViewResource(R.id.readalong_previous, android.R.drawable.ic_media_previous)
            setOnClickPendingIntent(R.id.readalong_previous, it)
        }
        setImageViewResource(
            R.id.readalong_play_pause,
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        setOnClickPendingIntent(R.id.readalong_play_pause, actions.playPause)
        actions.next?.let {
            setImageViewResource(R.id.readalong_next, android.R.drawable.ic_media_next)
            setOnClickPendingIntent(R.id.readalong_next, it)
        }
    }
    val builder = NotificationCompat.Builder(context, EPUB_NARRATION_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground))
        .setContentTitle(title)
        .setContentText(detail)
        .setContentIntent(contentIntent)
        .setCustomContentView(views)
        .setCustomBigContentView(views)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setOnlyAlertOnce(true)
        .setOngoing(isPlaying)
        .setVisibility(visibility)
        .setDeleteIntent(actions.close)

    actions.previous?.let {
        builder.addAction(
            android.R.drawable.ic_media_previous,
            context.getString(R.string.tts_notification_previous),
            it
        )
    }
    builder.addAction(
        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        context.getString(
            if (isPlaying) R.string.tts_notification_pause_narration
            else R.string.tts_notification_play_narration
        ),
        actions.playPause
    )
    actions.next?.let {
        builder.addAction(
            android.R.drawable.ic_media_next,
            context.getString(R.string.tts_notification_next),
            it
        )
    }
    builder.addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        context.getString(R.string.common_close),
        actions.close
    )
    return builder.build()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun buildEpubNarrationMediaAnchorNotification(
    context: Context,
    title: String,
    contentIntent: PendingIntent,
    mediaSession: MediaSession,
    actions: EpubNarrationNotificationActions,
    isPlaying: Boolean
): Notification {
    val builder = NotificationCompat.Builder(context, EPUB_NARRATION_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(title)
        .setContentText(context.getString(R.string.tts_notification_epub_narration))
        .setContentIntent(contentIntent)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setOnlyAlertOnce(true)
        .setOngoing(isPlaying)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    actions.previous?.let {
        builder.addAction(
            android.R.drawable.ic_media_previous,
            context.getString(R.string.tts_notification_previous_short),
            it
        )
    }
    builder.addAction(
        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        context.getString(
            if (isPlaying) R.string.tts_notification_pause_short
            else R.string.tts_notification_play_short
        ),
        actions.playPause
    )
    actions.next?.let {
        builder.addAction(
            android.R.drawable.ic_media_next,
            context.getString(R.string.tts_notification_next_short),
            it
        )
    }
    val compactIndices = buildList {
        if (actions.previous != null) add(0)
        add(if (actions.previous != null) 1 else 0)
        if (actions.next != null) add(if (actions.previous != null) 2 else 1)
    }
    return builder.setStyle(
        MediaNotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.getSessionCompatToken())
            .setShowActionsInCompactView(*compactIndices.toIntArray())
    ).build()
}

internal fun buildEpubNarrationCompatMediaAnchorNotification(
    context: Context,
    title: String,
    contentIntent: PendingIntent,
    mediaSessionToken: android.support.v4.media.session.MediaSessionCompat.Token,
    actions: EpubNarrationNotificationActions,
    isPlaying: Boolean
): Notification {
    val builder = NotificationCompat.Builder(context, EPUB_NARRATION_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(title)
        .setContentText(context.getString(R.string.tts_notification_epub_narration))
        .setContentIntent(contentIntent)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setOnlyAlertOnce(true)
        .setOngoing(isPlaying)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    actions.previous?.let {
        builder.addAction(
            android.R.drawable.ic_media_previous,
            context.getString(R.string.tts_notification_previous_short),
            it
        )
    }
    builder.addAction(
        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        context.getString(
            if (isPlaying) R.string.tts_notification_pause_short
            else R.string.tts_notification_play_short
        ),
        actions.playPause
    )
    actions.next?.let {
        builder.addAction(
            android.R.drawable.ic_media_next,
            context.getString(R.string.tts_notification_next_short),
            it
        )
    }
    val compactIndices = buildList {
        if (actions.previous != null) add(0)
        add(if (actions.previous != null) 1 else 0)
        if (actions.next != null) add(if (actions.previous != null) 2 else 1)
    }
    return builder.setStyle(
        MediaNotificationCompat.MediaStyle()
            .setMediaSession(mediaSessionToken)
            .setShowActionsInCompactView(*compactIndices.toIntArray())
    ).build()
}