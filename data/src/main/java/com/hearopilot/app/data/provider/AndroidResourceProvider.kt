package com.hearopilot.app.data.provider

import android.content.Context
import android.content.res.Configuration
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.provider.ResourceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/**
 * Android implementation of ResourceProvider using Context to access string resources.
 *
 * @property context Application context for accessing resources
 */
class AndroidResourceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ResourceProvider {

    override fun getDefaultLlmSystemPrompt(): String {
        return getSimpleListeningPrompt()
    }

    override fun getSimpleListeningPrompt(): String {
        return getStringResource("prompt_simple_listening")
    }

    override fun getShortMeetingPrompt(): String {
        return getStringResource("prompt_short_meeting")
    }

    override fun getLongMeetingPrompt(): String {
        return getStringResource("prompt_long_meeting")
    }

    override fun getTranslationPrompt(): String {
        return getStringResource("prompt_translation")
    }

    override fun getJsonFieldTitle(): String = getStringResource("json_field_title")
    override fun getJsonFieldSummary(): String = getStringResource("json_field_summary")
    override fun getJsonFieldActionItems(): String = getStringResource("json_field_action_items")

    override fun getJsonFieldTitle(localeCode: String): String =
        getStringResourceForLocale("json_field_title", localeCode)

    override fun getJsonFieldSummary(localeCode: String): String =
        getStringResourceForLocale("json_field_summary", localeCode)

    override fun getJsonFieldActionItems(localeCode: String): String =
        getStringResourceForLocale("json_field_action_items", localeCode)

    override fun getBatchChunkPrompt(): String = getStringResource("prompt_batch_chunk_summary")
    override fun getBatchMergePrompt(): String = getStringResource("prompt_batch_merge")

    override fun getBatchChunkPrompt(localeCode: String): String =
        getStringResourceForLocale("prompt_batch_chunk_summary", localeCode)

    override fun getBatchMergePrompt(localeCode: String): String =
        getStringResourceForLocale("prompt_batch_merge", localeCode)

    override fun getTopicPrefix(localeCode: String): String =
        getStringResourceForLocale("session_topic_prefix", localeCode)

    override fun getPromptForMode(mode: RecordingMode, localeCode: String): String {
        val resourceName = when (mode) {
            RecordingMode.SIMPLE_LISTENING      -> "prompt_simple_listening"
            RecordingMode.SHORT_MEETING         -> "prompt_short_meeting"
            RecordingMode.LONG_MEETING          -> "prompt_long_meeting"
            RecordingMode.REAL_TIME_TRANSLATION -> "prompt_translation"
        }
        return getStringResourceForLocale(resourceName, localeCode)
    }

    private fun getStringResourceForLocale(resourceName: String, localeCode: String): String {
        return try {
            val locale = Locale.forLanguageTag(localeCode)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            val localizedContext = context.createConfigurationContext(config)
            val resourceId = localizedContext.resources.getIdentifier(
                resourceName, "string", context.packageName
            )
            if (resourceId != 0) localizedContext.resources.getString(resourceId)
            else getStringResource(resourceName)
        } catch (e: Exception) {
            getStringResource(resourceName)
        }
    }

    private fun getStringResource(resourceName: String): String {
        return try {
            val resourceId = context.resources.getIdentifier(
                resourceName,
                "string",
                context.packageName
            )
            if (resourceId != 0) {
                context.resources.getString(resourceId)
            } else {
                // Fallback to English if resource not found
                getFallbackPrompt(resourceName)
            }
        } catch (e: Exception) {
            getFallbackPrompt(resourceName)
        }
    }

    private fun getFallbackPrompt(resourceName: String): String {
        return when (resourceName) {
            "prompt_simple_listening" -> """
                You are an AI assistant analyzing audio transcriptions.
                Output format:
                Line 1: Brief title (max 8 words, no punctuation)
                Lines 2+: Concise summary

                Be direct. No meta-commentary like "This is a summary...". Just provide the content.
            """.trimIndent()
            "prompt_short_meeting" -> """
                You are a meeting assistant analyzing discussions.
                Output JSON only:
                {"title": "Brief meeting topic (max 8 words)", "summary": "Main discussion points and decisions", "action_items": ["Task 1", "Task 2"]}

                Be direct. No meta-commentary. Extract actionable tasks clearly.
            """.trimIndent()
            "prompt_long_meeting" -> """
                You are an assistant analyzing extended discussions.
                Detect the language of the input and write all output in that same language.
                Output JSON only:
                {"title": "...", "summary": "...", "action_items": ["...", "..."]}

                Be direct. Focus on high-level insights and concrete outcomes.
            """.trimIndent()
            "json_field_title" -> "title"
            "json_field_summary" -> "summary"
            "json_field_action_items" -> "action_items"
            "prompt_translation" -> """
                You are a professional translator.
                Translate the text into {target_language}.
                Maintain tone and context. Output translation only, no explanations.
            """.trimIndent()
            "prompt_batch_chunk_summary" ->
                "You are a meeting analysis assistant. Summarize the following transcription chunk concisely.\n" +
                "Include: key topics discussed, decisions made, and action items mentioned.\n" +
                "Keep the summary proportional to input length. Output ONLY valid JSON. No markdown. No code fences.\n" +
                "{\"title\": \"...\", \"summary\": \"...\", \"action_items\": [\"...\"]}"
            "prompt_batch_merge" ->
                "You are a meeting analysis assistant. Below are summaries of consecutive portions of a single recording.\n" +
                "Merge them into one comprehensive, coherent summary. Deduplicate action items that appear in multiple chunks.\n" +
                "Output ONLY valid JSON. No markdown. No code fences.\n" +
                "{\"title\": \"...\", \"summary\": \"...\", \"action_items\": [\"...\"]}"
            "session_topic_prefix" ->
                "Main topic of this recording: \"%1\$s\". Focus your analysis on this subject."
            else -> "AI assistant analyzing transcriptions."
        }
    }
}
