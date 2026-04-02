package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Generates a single final LLM insight from accumulated unprocessed transcription content.
 *
 * Called when recording stops, provided that enough words have accumulated since the
 * last periodic inference call. This ensures no meaningful content is lost at the tail
 * of a session.
 *
 * Applies the same prompt and parsing logic as [com.hearopilot.app.domain.usecase.sync.SyncSttLlmUseCase]
 * but as a one-shot suspend call rather than a streaming Flow.
 *
 * @property llmRepository Repository for LLM inference
 * @property settingsRepository Repository for reading current app settings (prompts, intervals)
 */
class GenerateFinalInsightUseCase(
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository,
    private val resourceProvider: ResourceProvider
) {
    companion object {
        // Minimum word count in the pending buffer to justify a final inference call.
        // Below this threshold we assume the tail content is too sparse to be useful.
        private const val MIN_WORDS_FOR_FINAL_INSIGHT = 20

        // Max tokens allocated for the final insight call (same as periodic calls per mode).
        private const val MAX_TOKENS_SIMPLE    = 512
        private const val MAX_TOKENS_SHORT     = 600
        private const val MAX_TOKENS_LONG      = 768
        private const val MAX_TOKENS_TRANSLATE = 256

        // Maximum characters in the user prompt sent to the LLM.
        // Keeps the total prompt within typical on-device model context windows (4k–8k tokens).
        // The tail of the transcript is kept because it contains the most recent content.
        private const val MAX_USER_PROMPT_CHARS = 6_000
    }

    /**
     * Generate a final insight if [pendingText] contains enough content.
     *
     * @param pendingText Accumulated transcription text not yet covered by a periodic insight.
     * @param sessionId   Session to associate the insight with.
     * @param mode        Recording mode — drives prompt and output format.
     * @param outputLanguage BCP-47 target language code for translation mode; null otherwise.
     * @param topic Optional session topic injected into the system prompt for focused analysis.
     * @return [Result.success] with a [LlmInsight] if generated, or [Result.success] with null
     *         if [pendingText] has fewer than [MIN_WORDS_FOR_FINAL_INSIGHT] words.
     *         [Result.failure] on LLM error.
     */
    suspend operator fun invoke(
        pendingText: String,
        sessionId: String,
        mode: RecordingMode,
        outputLanguage: String?,
        topic: String? = null
    ): Result<LlmInsight?> {
        val wordCount = pendingText.trim()
            .split(Regex("\\s+"))
            .count { it.isNotEmpty() }

        if (wordCount < MIN_WORDS_FOR_FINAL_INSIGHT) {
            return Result.success(null) // Not enough content — skip
        }

        return try {
            val settings = settingsRepository.getSettings().first()
            val systemPrompt = buildSystemPrompt(mode, settings, outputLanguage, topic)
            // Cap to the most recent content so the prompt fits within the model's
            // context window even for very long sessions (e.g. 30-min recordings).
            val userPrompt = pendingText.trim().takeLast(MAX_USER_PROMPT_CHARS)
            val maxTokens = maxTokensForMode(mode)
            val insightTimestamp = System.currentTimeMillis()

            // When an explicit output language is set, override the json field names
            // with the locale-specific names from that language's prompt schema.
            // Parsing must use the same field names as the prompt that produced the output.
            val parseSettings = if (!outputLanguage.isNullOrEmpty() &&
                                    mode != RecordingMode.REAL_TIME_TRANSLATION) {
                settings.copy(
                    jsonFieldTitle       = resourceProvider.getJsonFieldTitle(outputLanguage),
                    jsonFieldSummary     = resourceProvider.getJsonFieldSummary(outputLanguage),
                    jsonFieldActionItems = resourceProvider.getJsonFieldActionItems(outputLanguage)
                )
            } else {
                settings
            }

            // Reload model if it was unloaded between sessions (no-op if already loaded).
            llmRepository.reloadModel().onFailure { e ->
                return Result.failure(e)
            }

            val builder = StringBuilder()
            llmRepository.generateInsight(userPrompt, systemPrompt, maxTokens)
                .collect { token -> builder.append(token) }

            val rawOutput = builder.toString().trim()
            if (rawOutput.isBlank()) {
                return Result.success(null)
            }

            val parsed = InsightOutputParser.parse(rawOutput, mode, parseSettings)
            Result.success(
                LlmInsight(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    title = parsed.title,
                    content = parsed.content,
                    tasks = parsed.tasks,
                    timestamp = insightTimestamp,
                    sourceSegmentIds = emptyList() // Final insight has no specific source IDs
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun buildSystemPrompt(
        mode: RecordingMode,
        settings: AppSettings,
        targetLangCode: String?,
        topic: String? = null
    ): String {
        val basePrompt = when (mode) {
            RecordingMode.REAL_TIME_TRANSLATION -> {
                val code = targetLangCode ?: settings.translationTargetLanguage
                val englishName = SupportedLanguages.getByCode(code)?.englishName ?: code
                settings.translationSystemPrompt.replace("{target_language}", englishName)
            }
            else -> {
                val storedPrompt = when (mode) {
                    RecordingMode.SIMPLE_LISTENING -> settings.simpleListeningSystemPrompt
                    RecordingMode.SHORT_MEETING    -> settings.shortMeetingSystemPrompt
                    RecordingMode.LONG_MEETING     -> settings.longMeetingSystemPrompt
                    else                           -> settings.simpleListeningSystemPrompt
                }
                if (!targetLangCode.isNullOrEmpty()) {
                    val defaultPrompt = settingsRepository.getDefaultPromptForMode(mode)
                    if (storedPrompt != defaultPrompt) storedPrompt
                    else resourceProvider.getPromptForMode(mode, targetLangCode)
                } else {
                    storedPrompt
                }
            }
        }
        return if (!topic.isNullOrBlank()) {
            val localeCode = if (!targetLangCode.isNullOrEmpty()) targetLangCode else "en"
            val prefix = resourceProvider.getTopicPrefix(localeCode).format(topic)
            "$prefix\n\n$basePrompt"
        } else {
            basePrompt
        }
    }

    private fun maxTokensForMode(mode: RecordingMode): Int = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> MAX_TOKENS_SIMPLE
        RecordingMode.SHORT_MEETING         -> MAX_TOKENS_SHORT
        RecordingMode.LONG_MEETING          -> MAX_TOKENS_LONG
        RecordingMode.REAL_TIME_TRANSLATION -> MAX_TOKENS_TRANSLATE
    }
}
