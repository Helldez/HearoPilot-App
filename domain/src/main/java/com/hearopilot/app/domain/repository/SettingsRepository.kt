package com.hearopilot.app.domain.repository

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.RecordingMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for application settings persistence.
 */
interface SettingsRepository {
    /**
     * Get settings as a Flow that emits whenever settings change.
     */
    fun getSettings(): Flow<AppSettings>

    /**
     * Update application settings.
     *
     * @param settings New settings to persist
     */
    suspend fun updateSettings(settings: AppSettings)

    /**
     * Get the default LLM system prompt in the current device language.
     *
     * @return Localized default system prompt
     */
    fun getDefaultSystemPrompt(): String

    /**
     * Get the default system prompt for a specific recording mode in the current device language.
     * Used to reset customized prompts back to the factory default.
     *
     * @param mode Recording mode
     * @return Localized default system prompt for the given mode
     */
    fun getDefaultPromptForMode(mode: RecordingMode): String

    /**
     * Marks the history-insight coachmark as shown so it is never displayed again.
     */
    suspend fun markHistoryInsightCoachmarkShown()
}
