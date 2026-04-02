package com.hearopilot.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hearopilot.app.ui.MainActivity
import com.hearopilot.app.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the download-progress notification shown while AI models are being downloaded.
 *
 * A separate channel from the recording channel is used so users can control
 * download notifications independently via system notification settings.
 */
@Singleton
class ModelDownloadNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 1004
        private const val REQUEST_CODE_OPEN_APP = 1005
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Creates the notification channel for model download notifications.
     * No-op below Android O (API 26).
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_download_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_download_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds a download progress notification.
     *
     * @param percentage Overall download progress 0–100; -1 for indeterminate.
     * @param title      Notification content title (e.g. "Downloading STT model").
     */
    fun createNotification(percentage: Int, title: String? = null): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationTitle = title
            ?: context.getString(R.string.notification_download_title)

        val contentText = if (percentage in 0..99) {
            context.getString(R.string.notification_download_progress, percentage)
        } else {
            context.getString(R.string.notification_download_completing)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percentage.coerceIn(0, 100), percentage < 0)
            .build()
    }

    /**
     * Posts or updates the download notification.
     *
     * @param notificationId ID of the notification to update.
     * @param notification   The new notification content.
     */
    fun updateNotification(notificationId: Int, notification: Notification) {
        notificationManager.notify(notificationId, notification)
    }
}
