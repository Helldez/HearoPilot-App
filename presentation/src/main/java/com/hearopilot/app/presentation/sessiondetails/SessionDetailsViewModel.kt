package com.hearopilot.app.presentation.sessiondetails

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.service.LlmProcessingServiceController
import com.hearopilot.app.domain.usecase.llm.GenerateHistoryInsightUseCase
import com.hearopilot.app.domain.usecase.llm.InitializeLlmUseCase
import com.hearopilot.app.domain.usecase.transcription.DeleteSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.GetSessionDetailsUseCase
import com.hearopilot.app.domain.usecase.transcription.RenameSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.UpdateInsightContentUseCase
import com.hearopilot.app.domain.usecase.transcription.UpdateSegmentTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the session details screen.
 *
 * Displays complete information about a single transcription session
 * including all segments and insights.
 *
 * @property getSessionDetailsUseCase Use case for retrieving session details
 * @property deleteSessionUseCase Use case for deleting the session
 * @property savedStateHandle Navigation arguments (contains sessionId)
 */
@HiltViewModel
class SessionDetailsViewModel @Inject constructor(
    private val getSessionDetailsUseCase: GetSessionDetailsUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val renameSessionUseCase: RenameSessionUseCase,
    private val updateSegmentTextUseCase: UpdateSegmentTextUseCase,
    private val updateInsightContentUseCase: UpdateInsightContentUseCase,
    private val generateHistoryInsightUseCase: GenerateHistoryInsightUseCase,
    private val initializeLlmUseCase: InitializeLlmUseCase,
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository,
    private val llmProcessingServiceController: LlmProcessingServiceController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "SessionDetailsVM"
    }

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"]) {
        "Session ID is required"
    }

    private val _uiState = MutableStateFlow(
        SessionDetailsUiState(
            highlightId = savedStateHandle["highlightId"],
            initialTab = savedStateHandle["initialTab"] ?: 0
        )
    )
    val uiState: StateFlow<SessionDetailsUiState> = _uiState.asStateFlow()

    // Tracks the active history-insight generation job so it can be cancelled.
    private var historyInsightJob: Job? = null
    // ID of the copy session created during history insight generation.
    // Stored so it can be deleted if the user cancels mid-pipeline.
    private var pendingHistorySessionId: String? = null

    init {
        loadSessionDetails()
        checkHistoryInsightCoachmark()
    }

    /**
     * Shows the history-insight coachmark on first visit, then immediately persists
     * the "shown" flag so it never appears again on subsequent visits.
     */
    private fun checkHistoryInsightCoachmark() {
        viewModelScope.launch {
            val alreadyShown = settingsRepository.getSettings().first().hasShownHistoryInsightCoachmark
            if (!alreadyShown) {
                _uiState.update { it.copy(showHistoryInsightCoachmark = true) }
                settingsRepository.markHistoryInsightCoachmarkShown()
            }
        }
    }

    /**
     * Hides the history-insight coachmark (user dismissed it or auto-dismiss fired).
     */
    fun dismissHistoryInsightCoachmark() {
        _uiState.update { it.copy(showHistoryInsightCoachmark = false) }
    }

    /**
     * Load session details from the repository.
     */
    /**
     * Load session details from the repository.
     */
    private fun loadSessionDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getSessionDetailsUseCase(sessionId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load session: ${e.message}"
                        )
                    }
                }
                .collect { details ->
                    val items = if (details != null) createSessionItems(details) else emptyList()
                    
                    _uiState.update {
                        it.copy(
                            sessionDetails = details,
                            sessionItems = items,
                            isLoading = false,
                            error = if (details == null) "Session not found" else null
                        )
                    }
                }
        }
    }

    /**
     * Merges segments and insights into a single chronological list
     * using a two-pointer approach based on timestamps.
     */
    private fun createSessionItems(details: com.hearopilot.app.domain.model.SessionWithDetails): List<SessionDetailItem> {
        val segments = details.segments.sortedBy { it.timestamp }
        val insights = details.insights.sortedBy { it.timestamp }

        // For END_OF_SESSION strategy:
        //  - Final insights (sourceSegmentIds empty) go to the top so the user sees the summary first.
        //  - Intermediate insights (sourceSegmentIds non-empty) are interleaved chronologically with
        //    segments, positioned after the last segment they cover.
        if (details.session.insightStrategy == com.hearopilot.app.domain.model.InsightStrategy.END_OF_SESSION) {
            val finalInsights = insights.filter { it.sourceSegmentIds.isEmpty() }
            val intermediateInsights = insights.filter { it.sourceSegmentIds.isNotEmpty() }

            if (intermediateInsights.isEmpty()) {
                // No intermediates: all insights at top, all segments below (legacy / single-chunk).
                return finalInsights.map { SessionDetailItem.Insight(it) } +
                        segments.map { SessionDetailItem.Transcription(it) }
            }

            // Build a set for O(1) lookup: last segment ID of each intermediate insight.
            // An intermediate insight is placed immediately after the last segment in its chunk.
            val lastSegmentIdToInsight = intermediateInsights
                .filter { it.sourceSegmentIds.isNotEmpty() }
                .associateBy { it.sourceSegmentIds.last() }

            val body = mutableListOf<SessionDetailItem>()
            for (segment in segments) {
                body.add(SessionDetailItem.Transcription(segment))
                lastSegmentIdToInsight[segment.id]?.let { body.add(SessionDetailItem.Insight(it)) }
            }

            return finalInsights.map { SessionDetailItem.Insight(it) } + body
        }

        val items = mutableListOf<SessionDetailItem>()
        var si = 0
        var ii = 0

        while (si < segments.size && ii < insights.size) {
            if (segments[si].timestamp <= insights[ii].timestamp) {
                items.add(SessionDetailItem.Transcription(segments[si++]))
            } else {
                items.add(SessionDetailItem.Insight(insights[ii++]))
            }
        }
        while (si < segments.size) {
            items.add(SessionDetailItem.Transcription(segments[si++]))
        }
        while (ii < insights.size) {
            items.add(SessionDetailItem.Insight(insights[ii++]))
        }

        return items
    }

    /**
     * Show the rename dialog.
     */
    fun showRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = true) }
    }

    /**
     * Hide the rename dialog without saving.
     */
    fun hideRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false) }
    }

    /**
     * Rename this session.
     *
     * @param newName New name; blank clears the name
     */
    fun renameSession(newName: String) {
        viewModelScope.launch {
            renameSessionUseCase(sessionId, newName)
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = "Failed to rename session: ${e.message}")
                    }
                }
            _uiState.update { it.copy(showRenameDialog = false) }
            // sessionDetails refreshes automatically via the existing Flow
        }
    }

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    /**
     * Hide delete confirmation dialog.
     */
    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    /**
     * Delete this session and navigate back.
     *
     * @param onDeleted Callback invoked after successful deletion
     */
    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
                .onSuccess {
                    onDeleted()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = "Failed to delete session: ${e.message}",
                            showDeleteConfirmation = false
                        )
                    }
                }
        }
    }

    /**
     * Export session as plain text.
     *
     * Returns the full transcription text.
     */
    fun exportAsText(): String {
        val details = _uiState.value.sessionDetails ?: return ""
        val session = details.session

        return buildString {
            appendLine("=".repeat(50))
            appendLine("TRANSCRIPTION SESSION")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Name: ${session.name ?: "(Unnamed)"}")
            appendLine("Date: ${formatTimestamp(session.createdAt)}")
            appendLine("Segments: ${details.completeSegmentCount}")
            appendLine("Insights: ${details.insights.size}")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("TRANSCRIPTION")
            appendLine("=".repeat(50))
            appendLine()
            appendLine(details.fullTranscription)

            if (details.insights.isNotEmpty()) {
                appendLine()
                appendLine("=".repeat(50))
                appendLine("LLM INSIGHTS")
                appendLine("=".repeat(50))
                appendLine()
                details.insights.forEachIndexed { index, insight ->
                    appendLine("${index + 1}. ${insight.content}")
                    appendLine()
                }
            }
        }
    }

    // ========== Segment Editing ==========

    /** Open edit dialog for a transcription segment. */
    fun showEditSegmentDialog(segment: com.hearopilot.app.domain.model.TranscriptionSegment) {
        _uiState.update { it.copy(editingSegment = segment) }
    }

    /** Dismiss segment edit dialog without saving. */
    fun hideEditSegmentDialog() {
        _uiState.update { it.copy(editingSegment = null) }
    }

    /** Persist updated segment text. */
    fun saveSegmentText(segmentId: String, newText: String) {
        viewModelScope.launch {
            updateSegmentTextUseCase(segmentId, newText)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Failed to update transcription: ${e.message}") }
                }
            _uiState.update { it.copy(editingSegment = null) }
        }
    }

    // ========== Insight Editing (unified) ==========

    /** Open the unified edit dialog for title, content, and tasks of an insight. */
    fun showEditInsightFullDialog(insight: com.hearopilot.app.domain.model.LlmInsight) {
        _uiState.update { it.copy(editingInsightFull = insight) }
    }

    /** Dismiss the unified insight edit dialog without saving. */
    fun hideEditInsightFullDialog() {
        _uiState.update { it.copy(editingInsightFull = null) }
    }

    /**
     * Persist updated title, content, and tasks for an insight.
     *
     * @param insightId ID of the insight to update.
     * @param newTitle Updated title; blank clears it.
     * @param patchedContent Full updated content string (already patched by the caller).
     * @param tasksText Newline-delimited task strings; empty string clears all tasks.
     */
    fun saveInsightFull(insightId: String, newTitle: String, patchedContent: String, tasksText: String) {
        viewModelScope.launch {
            val taskLines = tasksText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val tasksJson = if (taskLines.isEmpty()) null
                            else org.json.JSONArray().apply { taskLines.forEach { put(it) } }.toString()

            updateInsightContentUseCase.updateTitle(insightId, newTitle.ifBlank { null })
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to update insight: ${e.message}") } }
            updateInsightContentUseCase.updateContent(insightId, patchedContent)
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to update insight: ${e.message}") } }
            updateInsightContentUseCase.updateTasks(insightId, tasksJson)
                .onFailure { e -> _uiState.update { it.copy(error = "Failed to update insight: ${e.message}") } }

            _uiState.update { it.copy(editingInsightFull = null) }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ========== History Insight Generation ==========

    /** Show the confirmation dialog for generating an AI insight copy. */
    fun showHistoryInsightConfirm() {
        // Pre-populate the language selector with the session's saved outputLanguage (if any),
        // so returning users see their original choice; new sessions default to device locale.
        val sessionOutputLanguage = _uiState.value.sessionDetails?.session?.outputLanguage ?: ""
        _uiState.update {
            it.copy(
                showHistoryInsightConfirm = true,
                historyInsightOutputLanguage = sessionOutputLanguage
            )
        }
    }

    /** Dismiss the confirmation dialog without starting generation. */
    fun hideHistoryInsightConfirm() {
        _uiState.update { it.copy(showHistoryInsightConfirm = false) }
    }

    /** Update the output language chosen in the history insight confirm dialog. */
    fun setHistoryInsightOutputLanguage(code: String) {
        _uiState.update { it.copy(historyInsightOutputLanguage = code) }
    }

    /**
     * Generate an AI insight copy of this session.
     *
     * Creates a new session with the same content and a batch insight produced by
     * [GenerateHistoryInsightUseCase]. Progress is streamed via [uiState].
     *
     * @param newSessionName Name to assign to the new copy session (localized by the caller).
     * @param onNavigateToSession Callback invoked with the new session ID upon success.
     */
    fun generateHistoryInsight(newSessionName: String?, modelNotDownloadedError: String, onNavigateToSession: (String) -> Unit) {
        _uiState.update {
            it.copy(
                showHistoryInsightConfirm = false,
                isGeneratingHistoryInsight = true,
                historyInsightProgress = BatchInsightProgress.Idle
            )
        }

        historyInsightJob = viewModelScope.launch {
            // Protect inference from being killed when app is backgrounded.
            llmProcessingServiceController.startProcessing()
            try {
                // Signal that the LLM is being loaded — blocks all TopAppBar actions in the UI.
                _uiState.update { it.copy(isInitializingLlm = true) }

                // Ensure the LLM repository has a model path set before the batch pipeline runs.
                // When the user arrives at session details without going through the recording screen,
                // initialize() was never called and reloadModel() inside GenerateBatchInsightUseCase
                // would fail with "reloadModel called before initialize".
                // loadImmediately = false defers actual model loading to the first reloadModel() call.
                val settings = settingsRepository.getSettings().first()
                if (settings.llmModelPath.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isInitializingLlm = false,
                            isGeneratingHistoryInsight = false,
                            error = modelNotDownloadedError
                        )
                    }
                    return@launch
                }
                // Apply conservative threads before loading if the device was previously
                // detected as constrained (cached in DataStore across sessions).
                if (settings.memoryConstrainedDetected) {
                    Log.d(TAG, "History insight: applying conservative threads (memoryConstrainedDetected=true from DataStore)")
                    llmRepository.useConservativeThreads()
                }
                // Post-load check and per-chunk learning are handled inside
                // GenerateBatchInsightUseCase (checkAndCacheMemoryConstraint + recordConstrainedInference).
                initializeLlmUseCase(settings.llmModelPath, loadImmediately = false)
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isInitializingLlm = false,
                                isGeneratingHistoryInsight = false,
                                error = e.message ?: "Failed to initialize LLM"
                            )
                        }
                        return@launch
                    }

                // LLM initialized — pipeline is now running; clear the init flag.
                _uiState.update { it.copy(isInitializingLlm = false) }

                val outputLanguage = _uiState.value.historyInsightOutputLanguage
                val result = generateHistoryInsightUseCase(
                    sourceSessionId = sessionId,
                    newSessionName = newSessionName,
                    overrideOutputLanguage = outputLanguage.ifBlank { null },
                    onProgress = { progress ->
                        _uiState.update { it.copy(historyInsightProgress = progress) }
                    },
                    onSessionCreated = { newId ->
                        pendingHistorySessionId = newId
                    },
                    onChunkInsight = { chunkInsight ->
                        _uiState.update { state ->
                            state.copy(
                                intermediateHistoryInsights = state.intermediateHistoryInsights + chunkInsight
                            )
                        }
                    },
                    onFinalInsight = { finalInsight ->
                        _uiState.update { it.copy(finalHistoryInsight = finalInsight) }
                    }
                )

                // Release LLM from RAM — it is no longer needed after the pipeline completes.
                try {
                    llmRepository.cleanup()
                } catch (e: Exception) {
                    android.util.Log.e("SessionDetailsVM", "Error releasing LLM after history insight", e)
                }

                pendingHistorySessionId = null
                historyInsightJob = null

                result.onSuccess { newSessionId ->
                    _uiState.update {
                        it.copy(
                            isGeneratingHistoryInsight = false,
                            intermediateHistoryInsights = emptyList(),
                            finalHistoryInsight = null
                        )
                    }
                    // A blank session ID signals a silent skip (transcript too short).
                    if (newSessionId.isNotBlank()) {
                        onNavigateToSession(newSessionId)
                    }
                }.onFailure { e ->
                    // CancellationException is handled by cancelHistoryInsight(); suppress here.
                    if (e !is CancellationException) {
                        _uiState.update {
                            it.copy(
                                isGeneratingHistoryInsight = false,
                                intermediateHistoryInsights = emptyList(),
                                finalHistoryInsight = null,
                                error = e.message ?: "Failed to generate insight"
                            )
                        }
                    }
                }
            } finally {
                // Always release the foreground token and clear init flag, even on cancellation.
                _uiState.update { it.copy(isInitializingLlm = false) }
                llmProcessingServiceController.stopProcessing()
            }
        }
    }

    /**
     * Cancel an in-progress history insight generation.
     *
     * Cancels the pipeline coroutine and deletes the partial copy session that was
     * created before the cancellation, leaving the original session untouched.
     */
    fun cancelHistoryInsight() {
        historyInsightJob?.cancel()
        historyInsightJob = null

        val partialId = pendingHistorySessionId
        pendingHistorySessionId = null

        viewModelScope.launch {
            // Delete the partial copy session so it does not appear in history.
            if (partialId != null) {
                deleteSessionUseCase(partialId)
                    .onFailure { e ->
                        android.util.Log.e("SessionDetailsVM", "Failed to delete partial session on cancel: ${e.message}")
                    }
            }
            // Release the LLM if it was already loaded during the cancelled pipeline.
            try {
                llmRepository.cleanup()
            } catch (e: Exception) {
                android.util.Log.e("SessionDetailsVM", "Error releasing LLM on cancel", e)
            }
            _uiState.update {
                it.copy(
                    isInitializingLlm = false,
                    isGeneratingHistoryInsight = false,
                    historyInsightProgress = BatchInsightProgress.Idle,
                    intermediateHistoryInsights = emptyList(),
                    finalHistoryInsight = null
                )
            }
        }
    }

    /**
     * Format timestamp to readable date/time.
     */
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }
}
