package com.hearopilot.app.presentation.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.usecase.transcription.CreateSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.DeleteSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.GetAllSessionsUseCase
import com.hearopilot.app.domain.usecase.transcription.GetTotalDataSizeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the sessions list screen.
 *
 * Manages the list of transcription sessions and handles creation/deletion operations.
 *
 * @property getAllSessionsUseCase Use case for retrieving all sessions
 * @property createSessionUseCase Use case for creating a new session
 * @property deleteSessionUseCase Use case for deleting a session
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val getAllSessionsUseCase: GetAllSessionsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val settingsRepository: SettingsRepository,
    private val getTotalDataSizeUseCase: GetTotalDataSizeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            getTotalDataSizeUseCase().collect { bytes ->
                _uiState.update { it.copy(totalDataSizeBytes = bytes) }
            }
        }
    }

    /**
     * Load all sessions from the repository.
     */
    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            getAllSessionsUseCase()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load sessions: ${e.message}"
                        )
                    }
                }
                .collect { sessions ->
                    _uiState.update {
                        it.copy(
                            sessions = sessions,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    /**
     * Show the new session dialog.
     */
    fun showNewSessionDialog() {
        _uiState.update { it.copy(showNewSessionDialog = true) }
    }

    /**
     * Hide the new session dialog.
     */
    fun hideNewSessionDialog() {
        _uiState.update { it.copy(showNewSessionDialog = false) }
    }

    /**
     * Create a new session and navigate to it.
     *
     * @param name Optional session name
     * @param mode Recording mode
     * @param outputLanguage Optional target language for translation mode
     * @param insightStrategy Whether to generate insights in real-time or at end of session
     * @param topic Optional main subject/topic for focused AI insights
     * @param onSessionCreated Callback invoked with the new session ID
     */
    fun createSession(
        name: String?,
        mode: RecordingMode,
        outputLanguage: String?,
        insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
        topic: String? = null,
        onSessionCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            createSessionUseCase(name, mode, outputLanguage, insightStrategy, topic)
                .onSuccess { session ->
                    _uiState.update { it.copy(showNewSessionDialog = false) }
                    onSessionCreated(session.id)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = "Failed to create session: ${e.message}",
                            showNewSessionDialog = false
                        )
                    }
                }
        }
    }

    /**
     * Delete a session.
     *
     * @param sessionId The session ID to delete
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = e.message ?: "Unknown error")
                    }
                }
            // Sessions list will automatically update via Flow
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
