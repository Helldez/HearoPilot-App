package com.hearopilot.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.hearopilot.app.data.service.DownloadStateManager
import com.hearopilot.app.domain.model.DownloadState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (DATA_SYNC type) that keeps model downloads alive when the app
 * is sent to the background.
 *
 * Architecture:
 * - Started by [com.hearopilot.app.data.service.AndroidDownloadManager] when a download begins.
 * - Observes [DownloadStateManager] to reflect real-time progress in the notification.
 * - Stops itself automatically when neither STT nor LLM is in the Downloading state.
 *
 * Play Store compliance:
 * - Uses FOREGROUND_SERVICE_DATA_SYNC type (API 34 requirement for data-sync work).
 * - Shows a persistent, low-importance progress notification; cannot be dismissed.
 * - Declares POST_NOTIFICATIONS in manifest (required for API 33+).
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownloadService"

        const val ACTION_START = "com.hearopilot.app.action.START_DOWNLOAD"
        const val ACTION_STOP = "com.hearopilot.app.action.STOP_DOWNLOAD"
    }

    @Inject
    lateinit var notificationManager: ModelDownloadNotificationManager

    @Inject
    lateinit var downloadStateManager: DownloadStateManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationManager.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDownloadForeground()
            ACTION_STOP -> stopSelf()
        }
        // Do not restart if killed — the ViewModel will decide whether to retry.
        return START_NOT_STICKY
    }

    private fun startDownloadForeground() {
        val notification = notificationManager.createNotification(percentage = 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ModelDownloadNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ModelDownloadNotificationManager.NOTIFICATION_ID, notification)
        }

        // Observe both download state flows and update the notification with the
        // combined progress. Stop the service when no download is active.
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            combine(
                downloadStateManager.sttDownloadState,
                downloadStateManager.llmDownloadState
            ) { sttState, llmState -> Pair(sttState, llmState) }
                .collect { (sttState, llmState) ->
                    val sttActive = sttState is DownloadState.Downloading
                    val llmActive = llmState is DownloadState.Downloading

                    if (!sttActive && !llmActive) {
                        Log.d(TAG, "No active downloads — stopping service")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }

                    // Use the active download's percentage for the notification.
                    val percentage = when {
                        sttActive -> (sttState as DownloadState.Downloading).progress.percentage
                        llmActive -> (llmState as DownloadState.Downloading).progress.percentage
                        else -> 0
                    }

                    val updated = notificationManager.createNotification(percentage)
                    notificationManager.updateNotification(
                        ModelDownloadNotificationManager.NOTIFICATION_ID,
                        updated
                    )

                    Log.v(TAG, "Download progress: $percentage%")
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        observerJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
