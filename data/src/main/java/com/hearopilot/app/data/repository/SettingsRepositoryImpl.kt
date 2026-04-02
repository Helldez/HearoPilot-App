package com.hearopilot.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmModelVariant
import com.hearopilot.app.domain.model.LlmSamplerConfig
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.ThemeMode
import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Implementation of SettingsRepository using DataStore for persistence.
 *
 * @property context Android context for DataStore access
 * @property resourceProvider Provider for accessing localized string resources
 */
class SettingsRepositoryImpl @Inject constructor(
    private val context: Context,
    private val resourceProvider: ResourceProvider
) : SettingsRepository {

    private object Keys {
        // Mode-specific intervals
        val SIMPLE_LISTENING_INTERVAL = intPreferencesKey("simple_listening_interval_seconds")
        val SHORT_MEETING_INTERVAL = intPreferencesKey("short_meeting_interval_seconds")
        val LONG_MEETING_INTERVAL = intPreferencesKey("long_meeting_interval_minutes")
        val TRANSLATION_INTERVAL = intPreferencesKey("translation_interval_seconds")

        // Mode-specific system prompts
        val SIMPLE_LISTENING_PROMPT = stringPreferencesKey("simple_listening_system_prompt")
        val SHORT_MEETING_PROMPT = stringPreferencesKey("short_meeting_system_prompt")
        val LONG_MEETING_PROMPT = stringPreferencesKey("long_meeting_system_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_system_prompt")

        // Translation settings
        val TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")

        // General settings
        val STT_MODEL_PATH = stringPreferencesKey("stt_model_path")
        val LLM_MODEL_PATH = stringPreferencesKey("llm_model_path")
        val AUTO_START_LLM = booleanPreferencesKey("auto_start_llm")
        val THEME_MODE = stringPreferencesKey("theme_mode")

        // VAD parameters
        val VAD_MIN_SILENCE_DURATION = floatPreferencesKey("vad_min_silence_duration")
        val VAD_MAX_SPEECH_DURATION = floatPreferencesKey("vad_max_speech_duration")
        val VAD_THRESHOLD = floatPreferencesKey("vad_threshold")

        // Per-mode default insight strategy
        val SIMPLE_LISTENING_DEFAULT_STRATEGY = stringPreferencesKey("simple_listening_default_strategy")
        val SHORT_MEETING_DEFAULT_STRATEGY = stringPreferencesKey("short_meeting_default_strategy")
        val LONG_MEETING_DEFAULT_STRATEGY = stringPreferencesKey("long_meeting_default_strategy")
        val TRANSLATION_DEFAULT_STRATEGY = stringPreferencesKey("translation_default_strategy")

        // LLM model variant selection (Q8_0 / IQ4_NL)
        val LLM_MODEL_VARIANT = stringPreferencesKey("llm_model_variant")

        // LLM sampler configuration
        val LLM_SAMPLER_TEMPERATURE = floatPreferencesKey("llm_sampler_temperature")
        val LLM_SAMPLER_TOP_K = intPreferencesKey("llm_sampler_top_k")
        val LLM_SAMPLER_TOP_P = floatPreferencesKey("llm_sampler_top_p")
        val LLM_SAMPLER_MIN_P = floatPreferencesKey("llm_sampler_min_p")
        val LLM_SAMPLER_REPEAT_PENALTY = floatPreferencesKey("llm_sampler_repeat_penalty")

        // One-time UI hints
        val HAS_SHOWN_HISTORY_INSIGHT_COACHMARK = booleanPreferencesKey("has_shown_history_insight_coachmark")
    }

    override fun getSettings(): Flow<AppSettings> {
        return context.dataStore.data.map { preferences ->
            val defaults = AppSettings() // Use defaults from data class

            AppSettings(
                // Mode-specific intervals
                simpleListeningIntervalSeconds = preferences[Keys.SIMPLE_LISTENING_INTERVAL]
                    ?: defaults.simpleListeningIntervalSeconds,
                shortMeetingIntervalSeconds = preferences[Keys.SHORT_MEETING_INTERVAL]
                    ?: defaults.shortMeetingIntervalSeconds,
                longMeetingIntervalMinutes = (preferences[Keys.LONG_MEETING_INTERVAL]
                    ?: defaults.longMeetingIntervalMinutes).let { stored ->
                    // Valid set: 3, 5, 10 minutes (UI options).
                    // Migrate any out-of-range stored value to the default.
                    if (stored in listOf(3, 5, 10)) stored else defaults.longMeetingIntervalMinutes
                },
                translationIntervalSeconds = preferences[Keys.TRANSLATION_INTERVAL]
                    ?: defaults.translationIntervalSeconds,

                // Mode-specific system prompts (load from localized resources)
                simpleListeningSystemPrompt = preferences[Keys.SIMPLE_LISTENING_PROMPT]
                    ?: resourceProvider.getSimpleListeningPrompt(),
                shortMeetingSystemPrompt = preferences[Keys.SHORT_MEETING_PROMPT]
                    ?: resourceProvider.getShortMeetingPrompt(),
                longMeetingSystemPrompt = preferences[Keys.LONG_MEETING_PROMPT]
                    ?: resourceProvider.getLongMeetingPrompt(),
                translationSystemPrompt = preferences[Keys.TRANSLATION_PROMPT]
                    ?: resourceProvider.getTranslationPrompt(),

                // Translation settings
                translationTargetLanguage = preferences[Keys.TRANSLATION_TARGET_LANGUAGE]
                    ?: defaults.translationTargetLanguage,

                // General settings
                sttModelPath = preferences[Keys.STT_MODEL_PATH] ?: defaults.sttModelPath,
                llmModelPath = preferences[Keys.LLM_MODEL_PATH] ?: defaults.llmModelPath,
                llmEnabled = preferences[Keys.AUTO_START_LLM] ?: defaults.llmEnabled,
                themeMode = try {
                    ThemeMode.valueOf(preferences[Keys.THEME_MODE] ?: "SYSTEM")
                } catch (e: IllegalArgumentException) {
                    defaults.themeMode
                },

                // VAD parameters
                vadMinSilenceDuration = preferences[Keys.VAD_MIN_SILENCE_DURATION] ?: defaults.vadMinSilenceDuration,
                vadMaxSpeechDuration = preferences[Keys.VAD_MAX_SPEECH_DURATION] ?: defaults.vadMaxSpeechDuration,
                vadThreshold = preferences[Keys.VAD_THRESHOLD] ?: defaults.vadThreshold,

                // JSON field names — always from locale resources, never persisted to DataStore
                jsonFieldTitle = resourceProvider.getJsonFieldTitle(),
                jsonFieldSummary = resourceProvider.getJsonFieldSummary(),
                jsonFieldActionItems = resourceProvider.getJsonFieldActionItems(),

                // LLM model variant
                llmModelVariant = try {
                    LlmModelVariant.valueOf(
                        preferences[Keys.LLM_MODEL_VARIANT] ?: LlmModelVariant.Q8_0.name
                    )
                } catch (e: IllegalArgumentException) {
                    LlmModelVariant.Q8_0
                },

                // LLM sampler configuration
                llmSamplerConfig = LlmSamplerConfig(
                    temperature = preferences[Keys.LLM_SAMPLER_TEMPERATURE] ?: defaults.llmSamplerConfig.temperature,
                    topK = preferences[Keys.LLM_SAMPLER_TOP_K] ?: defaults.llmSamplerConfig.topK,
                    topP = preferences[Keys.LLM_SAMPLER_TOP_P] ?: defaults.llmSamplerConfig.topP,
                    minP = preferences[Keys.LLM_SAMPLER_MIN_P] ?: defaults.llmSamplerConfig.minP,
                    repeatPenalty = preferences[Keys.LLM_SAMPLER_REPEAT_PENALTY] ?: defaults.llmSamplerConfig.repeatPenalty
                ),

                // One-time UI hints
                hasShownHistoryInsightCoachmark = preferences[Keys.HAS_SHOWN_HISTORY_INSIGHT_COACHMARK] ?: false,

                // Per-mode default insight strategy
                simpleListeningDefaultStrategy = parseStrategy(
                    preferences[Keys.SIMPLE_LISTENING_DEFAULT_STRATEGY],
                    InsightStrategy.REAL_TIME
                ),
                shortMeetingDefaultStrategy = parseStrategy(
                    preferences[Keys.SHORT_MEETING_DEFAULT_STRATEGY],
                    InsightStrategy.REAL_TIME
                ),
                longMeetingDefaultStrategy = parseStrategy(
                    preferences[Keys.LONG_MEETING_DEFAULT_STRATEGY],
                    InsightStrategy.REAL_TIME
                ),
                translationDefaultStrategy = parseStrategy(
                    preferences[Keys.TRANSLATION_DEFAULT_STRATEGY],
                    InsightStrategy.REAL_TIME
                )
            )
        }
    }

    private fun parseStrategy(stored: String?, default: InsightStrategy): InsightStrategy {
        return try {
            if (stored != null) InsightStrategy.valueOf(stored) else default
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    override suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            // Mode-specific intervals
            preferences[Keys.SIMPLE_LISTENING_INTERVAL] = settings.simpleListeningIntervalSeconds
            preferences[Keys.SHORT_MEETING_INTERVAL] = settings.shortMeetingIntervalSeconds
            preferences[Keys.LONG_MEETING_INTERVAL] = settings.longMeetingIntervalMinutes
            preferences[Keys.TRANSLATION_INTERVAL] = settings.translationIntervalSeconds

            // Mode-specific system prompts
            preferences[Keys.SIMPLE_LISTENING_PROMPT] = settings.simpleListeningSystemPrompt
            preferences[Keys.SHORT_MEETING_PROMPT] = settings.shortMeetingSystemPrompt
            preferences[Keys.LONG_MEETING_PROMPT] = settings.longMeetingSystemPrompt
            preferences[Keys.TRANSLATION_PROMPT] = settings.translationSystemPrompt

            // Translation settings
            preferences[Keys.TRANSLATION_TARGET_LANGUAGE] = settings.translationTargetLanguage

            // General settings
            if (settings.sttModelPath.isNotBlank()) {
                preferences[Keys.STT_MODEL_PATH] = settings.sttModelPath
            }
            if (settings.llmModelPath.isNotBlank()) {
                preferences[Keys.LLM_MODEL_PATH] = settings.llmModelPath
            }
            preferences[Keys.AUTO_START_LLM] = settings.llmEnabled
            preferences[Keys.THEME_MODE] = settings.themeMode.name

            // VAD parameters
            preferences[Keys.VAD_MIN_SILENCE_DURATION] = settings.vadMinSilenceDuration
            preferences[Keys.VAD_MAX_SPEECH_DURATION] = settings.vadMaxSpeechDuration
            preferences[Keys.VAD_THRESHOLD] = settings.vadThreshold

            // LLM model variant
            preferences[Keys.LLM_MODEL_VARIANT] = settings.llmModelVariant.name

            // LLM sampler configuration
            preferences[Keys.LLM_SAMPLER_TEMPERATURE] = settings.llmSamplerConfig.temperature
            preferences[Keys.LLM_SAMPLER_TOP_K] = settings.llmSamplerConfig.topK
            preferences[Keys.LLM_SAMPLER_TOP_P] = settings.llmSamplerConfig.topP
            preferences[Keys.LLM_SAMPLER_MIN_P] = settings.llmSamplerConfig.minP
            preferences[Keys.LLM_SAMPLER_REPEAT_PENALTY] = settings.llmSamplerConfig.repeatPenalty

            // Per-mode default insight strategy
            preferences[Keys.SIMPLE_LISTENING_DEFAULT_STRATEGY] = settings.simpleListeningDefaultStrategy.name
            preferences[Keys.SHORT_MEETING_DEFAULT_STRATEGY] = settings.shortMeetingDefaultStrategy.name
            preferences[Keys.LONG_MEETING_DEFAULT_STRATEGY] = settings.longMeetingDefaultStrategy.name
            preferences[Keys.TRANSLATION_DEFAULT_STRATEGY] = settings.translationDefaultStrategy.name
        }
    }

    override suspend fun markHistoryInsightCoachmarkShown() {
        context.dataStore.edit { preferences ->
            preferences[Keys.HAS_SHOWN_HISTORY_INSIGHT_COACHMARK] = true
        }
    }

    override fun getDefaultSystemPrompt(): String {
        return resourceProvider.getSimpleListeningPrompt()
    }

    override fun getDefaultPromptForMode(mode: RecordingMode): String = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> resourceProvider.getSimpleListeningPrompt()
        RecordingMode.SHORT_MEETING         -> resourceProvider.getShortMeetingPrompt()
        RecordingMode.LONG_MEETING          -> resourceProvider.getLongMeetingPrompt()
        RecordingMode.REAL_TIME_TRANSLATION -> resourceProvider.getTranslationPrompt()
    }
}
