package com.hearopilot.app.service

import com.hearopilot.app.domain.model.RecordingSessionState
import com.hearopilot.app.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager that bridges ViewModel and Service to prevent AudioRecord duplication.
 *
 * Critical Component:
 * - ViewModel creates the STT stream using shareIn() and registers it here
 * - Service observes the same registered stream
 * - This ensures a single AudioRecord instance is shared between UI and Service
 *
 * Memory Efficiency:
 * - Stores only a reference to the Flow, NOT the transcription data
 * - Flow is hot (shared) and managed by ViewModel lifecycle
 */
@Singleton
class RecordingSessionManager @Inject constructor() {

    private val _sessionState = MutableStateFlow<RecordingSessionState>(RecordingSessionState.Idle)
    val sessionState: StateFlow<RecordingSessionState> = _sessionState.asStateFlow()

    private var transcriptionFlow: Flow<TranscriptionSegment>? = null

    /**
     * Registers the shared transcription flow from ViewModel.
     * Must be called after ViewModel creates the stream with shareIn().
     *
     * @param flow The shared transcription flow from SttRepository
     */
    fun registerTranscriptionFlow(flow: Flow<TranscriptionSegment>) {
        transcriptionFlow = flow
    }

    /**
     * Gets the registered transcription flow for Service observation.
     * Service collects this flow to update notifications.
     *
     * @return The registered transcription flow, or null if not registered
     */
    fun getTranscriptionFlow(): Flow<TranscriptionSegment>? {
        return transcriptionFlow
    }

    /**
     * Updates the current recording session state.
     * Called by ViewModel when state changes occur.
     *
     * @param state The new session state
     */
    fun updateState(state: RecordingSessionState) {
        _sessionState.value = state
    }

    /**
     * Clears the session data.
     * Called when recording stops to clean up resources.
     */
    fun clearSession() {
        transcriptionFlow = null
        _sessionState.value = RecordingSessionState.Idle
    }
}
