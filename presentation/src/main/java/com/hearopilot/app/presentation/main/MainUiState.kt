package com.hearopilot.app.presentation.main

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.TranscriptionSegment

/**
 * UI state for the main screen.
 *
 * @property isRecording Whether audio recording and STT is currently active
 * @property isFinalizingSession Whether a final LLM insight is being generated after stop.
 *   Recording is already stopped (isRecording = false) but the session has not fully
 *   concluded yet. The UI shows a shimmer placeholder while this is true.
 * @property completedSegments List of completed transcription segments (isComplete=true)
 * @property currentPartialSegment Current partial transcription segment being built (isComplete=false)
 * @property insights List of LLM-generated insights
 * @property settings Current application settings
 * @property error Error message to display, null if no error
 * @property llmStatus Status message for LLM (e.g., "Ready", "Processing", "Unavailable")
 * @property isDownloadingModel Whether LLM model is currently being downloaded
 * @property downloadProgress Download progress percentage (0-100)
 * @property insightStrategy The insight generation strategy for the current session
 * @property batchProgress Progress state of the end-of-session batch pipeline
 */
data class MainUiState(
    val isRecording: Boolean = false,
    val isFinalizingSession: Boolean = false,
    val insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val batchProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    val isInitializing: Boolean = true,
    val isMicPermissionDenied: Boolean = false,
    val completedSegments: List<TranscriptionSegment> = emptyList(),
    val currentPartialSegment: TranscriptionSegment? = null,
    val insights: List<LlmInsight> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val error: String? = null,
    val llmStatus: String = "Ready",
    val isLlmModelAvailable: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadSpeedMbps: Float = 0f,
    val downloadEtaSeconds: Int = 0,
    val recordingDurationMillis: Long = 0
) {
    /**
     * Returns all segments for display: completed + current partial (if exists).
     * Use this in UI to show the full transcription without duplicates.
     */
    val allSegments: List<TranscriptionSegment>
        get() = completedSegments + listOfNotNull(currentPartialSegment)

    /**
     * Returns insights sorted by timestamp (creation time) instead of arrival time.
     * This prevents insights from appearing out of order when longer insights
     * take more time to generate than shorter ones.
     */
    val sortedInsights: List<LlmInsight>
        get() = insights.sortedBy { it.timestamp }
}
