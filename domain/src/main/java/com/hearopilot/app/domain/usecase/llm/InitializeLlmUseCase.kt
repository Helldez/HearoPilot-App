package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Use case to initialize the LLM engine with a model.
 *
 * @property llmRepository Repository for LLM operations
 * @property settingsRepository Repository for app settings (to get system prompt)
 */
class InitializeLlmUseCase(
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Initialize LLM with the specified model.
     * Uses system prompt from settings.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        modelPath: String,
        loadImmediately: Boolean = true
    ): Result<Unit> {
        val settings = settingsRepository.getSettings().first()
        // Initialize with default Simple Listening prompt.
        // The specific prompt will be set by SyncSttLlmUseCase when recording starts.
        return llmRepository.initialize(modelPath, settings.simpleListeningSystemPrompt, loadImmediately)
    }
}
