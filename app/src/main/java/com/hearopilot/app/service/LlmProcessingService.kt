package com.hearopilot.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service (DATA_SYNC type) that keeps LLM inference alive when the app
 * is sent to the background.
 *
 * Architecture:
 * - Started by [com.hearopilot.app.service.LlmProcessingServiceControllerImpl] before any
 *   long-running LLM inference (batch or history insight generation).
 * - Stopped by the same controller once inference completes or is cancelled.
 * - Does not orchestrate inference itself; it only holds the foreground token.
 *
 * Play Store compliance:
 * - Uses FOREGROUND_SERVICE_DATA_SYNC type (API 34 requirement).
 * - Shows a persistent, low-importance indeterminate-progress notification.
 * - Declares POST_NOTIFICATIONS in manifest (required for API 33+).
 */
@AndroidEntryPoint
class LlmProcessingService : Service() {

    companion object {
        private const val TAG = "LlmProcessingService"

        const val ACTION_START = "com.hearopilot.app.action.START_LLM_PROCESSING"
        const val ACTION_STOP = "com.hearopilot.app.action.STOP_LLM_PROCESSING"
    }

    @Inject
    lateinit var notificationManager: LlmProcessingNotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationManager.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProcessingForeground()
            ACTION_STOP -> {
                Log.d(TAG, "Stopping LLM processing service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // Do not restart if killed — the ViewModel will handle failure recovery.
        return START_NOT_STICKY
    }

    private fun startProcessingForeground() {
        Log.d(TAG, "Starting LLM processing foreground service")
        val notification = notificationManager.createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                LlmProcessingNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(LlmProcessingNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? = null
}
