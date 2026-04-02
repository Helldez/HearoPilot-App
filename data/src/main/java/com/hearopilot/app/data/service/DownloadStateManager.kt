package com.hearopilot.app.data.service

import com.hearopilot.app.domain.model.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for sharing download state between DownloadService and ViewModels.
 *
 * Architecture:
 * - DownloadService updates the state as download progresses
 * - SetupViewModel observes the state to update UI
 * - Thread-safe via StateFlow
 *
 * Usage:
 * - Service: Call updateSttState() or updateLlmState() to update progress
 * - ViewModel: Observe sttDownloadState or llmDownloadState
 */
@Singleton
class DownloadStateManager @Inject constructor() {

    private val _sttDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val sttDownloadState: StateFlow<DownloadState> = _sttDownloadState.asStateFlow()

    private val _llmDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val llmDownloadState: StateFlow<DownloadState> = _llmDownloadState.asStateFlow()

    /**
     * Update STT download state.
     * Called by DownloadService during STT download.
     */
    fun updateSttState(state: DownloadState) {
        _sttDownloadState.value = state
    }

    /**
     * Update LLM download state.
     * Called by DownloadService during LLM download.
     */
    fun updateLlmState(state: DownloadState) {
        _llmDownloadState.value = state
    }

    /**
     * Reset STT state to Idle.
     */
    fun resetSttState() {
        _sttDownloadState.value = DownloadState.Idle
    }

    /**
     * Reset LLM state to Idle.
     */
    fun resetLlmState() {
        _llmDownloadState.value = DownloadState.Idle
    }

    /**
     * Reset all states to Idle.
     */
    fun resetAll() {
        _sttDownloadState.value = DownloadState.Idle
        _llmDownloadState.value = DownloadState.Idle
    }
}
