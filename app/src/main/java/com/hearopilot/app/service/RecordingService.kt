package com.hearopilot.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.hearopilot.app.data.service.WakeLockManager
import com.hearopilot.app.domain.model.RecordingSessionState
import dagger.hilt.android.AndroidEntryPoint
import com.hearopilot.app.data.util.PerfLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for maintaining recording when screen is off.
 *
 * Architecture:
 * - Does NOT manage AudioRecord directly
 * - Observes transcription flow from RecordingSessionManager
 * - Updates notification with real-time transcription progress
 * - Maintains wake lock to keep CPU active during processing
 *
 * Lifecycle:
 * - Started by MainViewModel via RecordingServiceController
 * - Stopped when user taps Stop button or ViewModel stops recording
 * - Service stops itself when app is killed (prevents orphaned recordings)
 */
@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START_RECORDING = "com.hearopilot.app.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.hearopilot.app.action.STOP_RECORDING"

        // Interval between periodic performance snapshots logged to PerfLogger.
        private const val PERF_HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    @Inject
    lateinit var sessionManager: RecordingSessionManager

    @Inject
    lateinit var notificationManager: RecordingNotificationManager

    @Inject
    lateinit var wakeLockManager: WakeLockManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectionJob: Job? = null
    private var perfHeartbeatJob: Job? = null
    private var startTime: Long = 0
    private var segmentCount: Int = 0
    private var lastSegmentText: String = ""

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationManager.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_START_RECORDING -> {
                Log.d(TAG, "Starting recording session")
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                Log.d(TAG, "Stopping recording session")
                stopRecording()
            }
        }

        // Don't restart service if killed - prevents orphaned recordings
        return Service.START_NOT_STICKY
    }

    private fun startRecording() {
        // Start foreground service with notification
        val notification = notificationManager.createRecordingNotification(
            segmentCount = 0,
            durationMs = 0,
            lastSegmentText = ""
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RecordingNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(RecordingNotificationManager.NOTIFICATION_ID, notification)
        }

        // Acquire wake lock to keep CPU active
        wakeLockManager.acquireWakeLock()

        // Initialize tracking
        startTime = System.currentTimeMillis()
        segmentCount = 0
        lastSegmentText = ""

        // Log an initial snapshot, then repeat every PERF_HEARTBEAT_INTERVAL_MS.
        PerfLogger.logSnapshot(this, "session-start")
        perfHeartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(PERF_HEARTBEAT_INTERVAL_MS)
                val elapsedMin = (System.currentTimeMillis() - startTime) / 60_000
                PerfLogger.logSnapshot(this@RecordingService, "heartbeat t+${elapsedMin}min")
            }
        }

        // Observe transcription flow from SessionManager
        val transcriptionFlow = sessionManager.getTranscriptionFlow()
        if (transcriptionFlow == null) {
            Log.e(TAG, "No transcription flow registered - stopping service")
            stopRecording()
            return
        }

        collectionJob = serviceScope.launch {
            transcriptionFlow
                .catch { e ->
                    Log.e(TAG, "Error collecting transcription flow", e)
                }
                .collect { segment ->
                    // Update tracking
                    segmentCount++
                    lastSegmentText = segment.text
                    val durationMs = System.currentTimeMillis() - startTime

                    // Update notification
                    val updatedNotification = notificationManager.createRecordingNotification(
                        segmentCount = segmentCount,
                        durationMs = durationMs,
                        lastSegmentText = lastSegmentText
                    )
                    notificationManager.updateNotification(
                        RecordingNotificationManager.NOTIFICATION_ID,
                        updatedNotification
                    )

                    // Update session state
                    sessionManager.updateState(
                        RecordingSessionState.Recording(
                            segmentCount = segmentCount,
                            durationMs = durationMs,
                            lastSegmentText = lastSegmentText
                        )
                    )

                    Log.d(TAG, "Segment collected: #$segmentCount - ${segment.text}")
                }
        }
    }

    private fun stopRecording() {
        // Cancel flow collection and perf heartbeat
        collectionJob?.cancel()
        collectionJob = null
        perfHeartbeatJob?.cancel()
        perfHeartbeatJob = null
        PerfLogger.logSnapshot(this, "session-end")

        // Release wake lock
        wakeLockManager.releaseWakeLock()

        // Update session state
        sessionManager.updateState(RecordingSessionState.Stopping)

        // Stop foreground service and remove notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Recording session stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        // Ensure cleanup
        collectionJob?.cancel()
        wakeLockManager.releaseWakeLock()
        sessionManager.clearSession()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        // Not a bound service
        return null
    }
}
