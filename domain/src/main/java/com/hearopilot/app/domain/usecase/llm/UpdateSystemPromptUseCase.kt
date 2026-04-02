package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Use case to update the LLM system prompt.
 *
 * Updates both the persisted settings and the active LLM instance immediately,
 * ensuring changes take effect without requiring app restart.
 *
 * @property settingsRepository Repository for persisting the prompt
 * @property llmRepository Repository for updating the active LLM
 */
class UpdateSystemPromptUseCase(
    private val settingsRepository: SettingsRepository,
    private val llmRepository: LlmRepository
) {
    /**
     * Update the system prompt in both settings and active LLM.
     *
     * @param prompt The new system prompt text
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(prompt: String): Result<Unit> {
        return try {
            // Update persisted settings - default to Simple Listening for legacy calls
            val currentSettings = settingsRepository.getSettings().first()
            val updatedSettings = currentSettings.copy(simpleListeningSystemPrompt = prompt)
            settingsRepository.updateSettings(updatedSettings)

            // Update active LLM instance immediately (if initialized)
            llmRepository.updateSystemPrompt(prompt)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
