package com.hearopilot.app.domain.service

import com.hearopilot.app.domain.model.RecordingSessionState
import com.hearopilot.app.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for controlling the recording service.
 * Provides a contract for starting/stopping recording service and observing session state.
 *
 * Android implementation will use foreground service with notifications.
 * Future iOS implementation will use appropriate platform mechanisms.
 */
interface RecordingServiceController {
    /**
     * Current state of the recording session.
     */
    val sessionState: StateFlow<RecordingSessionState>

    /**
     * Registers the shared transcription flow for service observation.
     * Must be called before starting the service.
     *
     * @param flow The shared transcription flow from the STT repository
     */
    fun registerTranscriptionFlow(flow: Flow<TranscriptionSegment>)

    /**
     * Starts the recording service.
     * On Android, this starts a foreground service with notification.
     */
    fun startService()

    /**
     * Stops the recording service.
     * Cleans up resources and removes notifications.
     */
    fun stopService()

    /**
     * Checks if the recording service is currently running.
     *
     * @return true if service is active, false otherwise
     */
    fun isServiceRunning(): Boolean
}
