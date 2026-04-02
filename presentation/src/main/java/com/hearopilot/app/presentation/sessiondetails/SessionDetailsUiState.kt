package com.hearopilot.app.presentation.sessiondetails

import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.SessionWithDetails
import com.hearopilot.app.domain.model.TranscriptionSegment

/**
 * UI state for the session details screen.
 *
 * @property sessionDetails Complete session data with segments and insights
 * @property sessionItems List of interleaved segments and insights for display
 * @property isLoading Whether data is being loaded
 * @property error Error message if loading failed
 * @property showDeleteConfirmation Whether to show delete confirmation dialog
 * @property showHistoryInsightConfirm Whether to show the "generate AI insight copy" confirmation dialog
 * @property isGeneratingHistoryInsight Whether the history insight pipeline is running
 * @property historyInsightProgress Current progress state of the batch pipeline
 */
data class SessionDetailsUiState(
    val sessionDetails: SessionWithDetails? = null,
    val sessionItems: List<SessionDetailItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val showRenameDialog: Boolean = false,
    // Edit dialogs
    val editingSegment: TranscriptionSegment? = null,
    /** Insight being edited in the unified title+content+tasks dialog. */
    val editingInsightFull: LlmInsight? = null,
    /** ID of the segment or insight to scroll to on first load (from search navigation). */
    val highlightId: String? = null,
    /** Initial tab index: 0 = Transcript, 1 = AI Insights. Used when navigating directly to the AI Insights tab. */
    val initialTab: Int = 0,
    /** Whether to show the one-time coachmark tooltip on the AutoAwesome icon. */
    val showHistoryInsightCoachmark: Boolean = false,
    /** Whether to show the confirmation dialog before generating a history insight copy. */
    val showHistoryInsightConfirm: Boolean = false,
    /** Whether the LLM is being initialized (loaded into memory) before the pipeline starts. */
    val isInitializingLlm: Boolean = false,
    /** Whether the history insight generation pipeline is currently running. */
    val isGeneratingHistoryInsight: Boolean = false,
    /** Current progress of the batch insight pipeline; Idle when not running. */
    val historyInsightProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    /**
     * BCP-47 output language selected by the user in the history insight confirm dialog.
     * Empty string = use device/session locale (auto). Populated when the confirm dialog opens.
     */
    val historyInsightOutputLanguage: String = "",
    /**
     * Intermediate (map-phase) chunk insights accumulated during history insight generation.
     * Populated progressively as each chunk completes; cleared when generation finishes or is cancelled.
     */
    val intermediateHistoryInsights: List<LlmInsight> = emptyList(),
    /**
     * The final (reduce-phase) merged insight from the history insight pipeline.
     * Set when the merge step completes; cleared when generation finishes or is cancelled.
     */
    val finalHistoryInsight: LlmInsight? = null
)

/**
 * Polymorphic item for the session details list.
 */
sealed interface SessionDetailItem {
    data class Transcription(val segment: TranscriptionSegment) : SessionDetailItem
    data class Insight(val insight: LlmInsight) : SessionDetailItem
}
