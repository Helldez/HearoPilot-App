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
 * Singleton manager for recording notification creation and updates.
 *
 * Features:
 * - Shows segment count and formatted duration
 * - Displays last transcribed text in expanded view
 * - Stop action button
 * - Tap notification to open app
 * - Low importance to minimize distraction
 * - Ongoing notification (cannot be dismissed)
 */
@Singleton
class RecordingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_OPEN_APP = 1002
        private const val REQUEST_CODE_STOP = 1003
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Creates the notification channel for recording notifications.
     * Required for Android O (API 26) and above.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_recording_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_recording_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates a recording notification with current session info.
     *
     * @param segmentCount Number of transcription segments captured
     * @param durationMs Recording duration in milliseconds
     * @param lastSegmentText Text from the most recent transcription segment
     * @return The built notification
     */
    fun createRecordingNotification(
        segmentCount: Int,
        durationMs: Long,
        lastSegmentText: String
    ): Notification {
        // Format duration as MM:SS
        val minutes = (durationMs / 1000) / 60
        val seconds = (durationMs / 1000) % 60
        val durationText = String.format("%02d:%02d", minutes, seconds)

        // Create intent to open app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create intent to stop recording
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val contentText = "$segmentCount segments • $durationText"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_recording_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true) // Cannot be dismissed
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_stop,
                context.getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (lastSegmentText.isNotEmpty()) lastSegmentText else "Listening...")
            )
            .build()
    }

    /**
     * Updates an existing notification.
     *
     * @param notificationId The ID of the notification to update
     * @param notification The new notification content
     */
    fun updateNotification(notificationId: Int, notification: Notification) {
        notificationManager.notify(notificationId, notification)
    }
}
