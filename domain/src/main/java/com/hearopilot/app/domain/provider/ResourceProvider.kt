package com.hearopilot.app.domain.provider

/**
 * Provider for accessing Android resources in a platform-agnostic way.
 *
 * This interface allows domain and presentation layers to access localized strings
 * without depending on Android framework directly, maintaining Clean Architecture.
 */
interface ResourceProvider {
    /**
     * Get the default LLM system prompt in the current device language.
     *
     * @return Localized default system prompt (for backward compatibility, returns simple listening prompt)
     */
    fun getDefaultLlmSystemPrompt(): String

    /**
     * Get localized system prompts for each recording mode.
     */
    fun getSimpleListeningPrompt(): String
    fun getShortMeetingPrompt(): String
    fun getLongMeetingPrompt(): String
    fun getTranslationPrompt(): String

    /**
     * Get the system prompt for a specific recording mode translated into the given locale.
     *
     * @param mode Recording mode determining which prompt key to load
     * @param localeCode BCP-47 locale code (e.g. "it", "de") for the desired language
     * @return Prompt string in the requested locale; falls back to device locale on error
     */
    fun getPromptForMode(mode: com.hearopilot.app.domain.model.RecordingMode, localeCode: String): String

    /**
     * JSON output field names for the device locale.
     * Used when no explicit output language is selected (auto/device language).
     */
    fun getJsonFieldTitle(): String
    fun getJsonFieldSummary(): String
    fun getJsonFieldActionItems(): String

    /**
     * JSON output field names for a specific locale.
     *
     * Each locale's prompt schema uses its own locale-specific field names
     * (e.g. Italian: "titolo", "riepilogo", "azioni"; English: "title", "summary", "action_items").
     * Parsing MUST use the same field names as the prompt that produced the output.
     *
     * @param localeCode BCP-47 locale code matching the active output language
     */
    fun getJsonFieldTitle(localeCode: String): String
    fun getJsonFieldSummary(localeCode: String): String
    fun getJsonFieldActionItems(localeCode: String): String

    /**
     * System prompts for the end-of-session batch insight pipeline.
     *
     * [getBatchChunkPrompt] is used during the map phase to summarize each transcript chunk.
     * [getBatchMergePrompt] is used during the reduce phase to merge chunk summaries.
     *
     * Both use English JSON field names ("title", "summary", "action_items") regardless of locale,
     * consistent with the overall prompt architecture.
     */
    fun getBatchChunkPrompt(): String
    fun getBatchMergePrompt(): String
    fun getBatchChunkPrompt(localeCode: String): String
    fun getBatchMergePrompt(localeCode: String): String

    /**
     * Returns the localized topic-prefix sentence injected before the system prompt when
     * the user sets a session topic. The returned string has one %1$s placeholder for the topic.
     *
     * @param localeCode BCP-47 locale code matching the active output language
     */
    fun getTopicPrefix(localeCode: String): String
}
