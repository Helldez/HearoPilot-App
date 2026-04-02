package com.hearopilot.app.domain.model

/**
 * Represents the state of a recording session.
 * Platform-agnostic model for recording lifecycle management.
 */
sealed class RecordingSessionState {
    /**
     * No recording session is active.
     */
    data object Idle : RecordingSessionState()

    /**
     * Recording session is active.
     *
     * @param segmentCount Number of transcription segments captured
     * @param durationMs Recording duration in milliseconds
     * @param lastSegmentText Text from the most recent transcription segment
     */
    data class Recording(
        val segmentCount: Int,
        val durationMs: Long,
        val lastSegmentText: String
    ) : RecordingSessionState()

    /**
     * Recording session is in the process of stopping.
     */
    data object Stopping : RecordingSessionState()
}
