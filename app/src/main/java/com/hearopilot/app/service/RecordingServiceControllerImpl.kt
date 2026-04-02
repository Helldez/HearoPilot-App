package com.hearopilot.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hearopilot.app.domain.model.RecordingSessionState
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.service.RecordingServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of RecordingServiceController.
 *
 * Responsibilities:
 * - Starts/stops RecordingService via Intent
 * - Exposes session state from RecordingSessionManager
 * - Delegates transcription flow registration to SessionManager
 * - Provides service running status check
 */
@Singleton
class RecordingServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: RecordingSessionManager
) : RecordingServiceController {

    companion object {
        private const val TAG = "RecordingServiceCtrl"
    }

    override val sessionState: StateFlow<RecordingSessionState>
        get() = sessionManager.sessionState

    override fun registerTranscriptionFlow(flow: Flow<TranscriptionSegment>) {
        Log.d(TAG, "Registering transcription flow")
        sessionManager.registerTranscriptionFlow(flow)
    }

    override fun startService() {
        Log.d(TAG, "Starting recording service")
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Recording service start command sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording service", e)
        }
    }

    override fun stopService() {
        Log.d(TAG, "Stopping recording service")
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }

        try {
            context.startService(intent)
            Log.d(TAG, "Recording service stop command sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording service", e)
        }
    }

    override fun isServiceRunning(): Boolean {
        return sessionState.value is RecordingSessionState.Recording
    }
}
