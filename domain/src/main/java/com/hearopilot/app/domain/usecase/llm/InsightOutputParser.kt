package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.RecordingMode

/**
 * Shared output parser for LLM insight responses.
 *
 * Centralizes JSON parsing logic used by [SyncSttLlmUseCase], [GenerateFinalInsightUseCase],
 * and [GenerateBatchInsightUseCase] to avoid duplication and ensure consistent behavior.
 */
object InsightOutputParser {

    data class ParsedInsight(
        val title: String?,
        val content: String,
        val tasks: String? // JSON array string, e.g. ["Task A","Task B"]
    )

    /**
     * Parse LLM output based on recording mode.
     *
     * Strips Markdown code fences (```json ... ```) that small models sometimes emit
     * despite being instructed not to, before any field extraction.
     *
     * - SIMPLE_LISTENING: JSON with title and summary (no action_items)
     * - SHORT_MEETING / LONG_MEETING: JSON with title, summary, action_items
     * - REAL_TIME_TRANSLATION: raw translated text, no title
     */
    fun parse(rawOutput: String, mode: RecordingMode, settings: AppSettings): ParsedInsight {
        val cleaned = stripCodeFences(stripThinkingBlock(rawOutput))

        return when (mode) {
            RecordingMode.SIMPLE_LISTENING -> {
                try {
                    // Try locale-specific field names first; fall back to English if the model
                    // hallucinated English keys despite a localized prompt.
                    val title = extractJsonField(cleaned, settings.jsonFieldTitle)
                        ?: extractJsonField(cleaned, "title")
                        ?: "Summary"
                    val summary = extractJsonField(cleaned, settings.jsonFieldSummary)
                        ?: extractJsonField(cleaned, "summary")
                        ?: cleaned
                    ParsedInsight(title = title, content = summary, tasks = null)
                } catch (e: Exception) {
                    ParsedInsight(title = "Summary", content = cleaned, tasks = null)
                }
            }
            RecordingMode.SHORT_MEETING, RecordingMode.LONG_MEETING -> {
                try {
                    val title = extractJsonField(cleaned, settings.jsonFieldTitle)
                        ?: extractJsonField(cleaned, "title")
                        ?: "Meeting Notes"
                    val summary = extractJsonField(cleaned, settings.jsonFieldSummary)
                        ?: extractJsonField(cleaned, "summary")
                        ?: cleaned
                    val actionItems = extractJsonArray(cleaned, settings.jsonFieldActionItems)
                        .ifEmpty { extractJsonArray(cleaned, "action_items") }
                    val tasksJson = if (actionItems.isNotEmpty()) {
                        "[${actionItems.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}]"
                    } else null
                    ParsedInsight(title = title, content = summary, tasks = tasksJson)
                } catch (e: Exception) {
                    ParsedInsight(title = "Meeting Notes", content = cleaned, tasks = null)
                }
            }
            RecordingMode.REAL_TIME_TRANSLATION -> {
                ParsedInsight(title = null, content = cleaned, tasks = null)
            }
        }
    }

    /**
     * Strip reasoning/thinking blocks before parsing the JSON payload.
     *
     * Supported formats:
     *  - Qwen3.5: <think>...</think>
     *  - Gemma 4:  <|channel>thought\n...\n<channel|>
     *
     * NOTE: this is a *defensive fallback* only. The primary suppression for Qwen3.5
     * happens at the C++ layer in ai_chat.cpp by pre-filling an empty think block.
     * Gemma 4 thinking is stripped here because its Jinja2 template inserts <|think|>
     * automatically; a C++ suppression equivalent is not yet implemented.
     *
     * Reasoning tokens still consume the token budget even when stripped here,
     * degrading the quality of the JSON that follows them.
     */
    fun stripThinkingBlock(text: String): String {
        // Qwen3.5: <think>...</think>
        val qwenStart = text.indexOf("<think>")
        if (qwenStart != -1) {
            val qwenEnd = text.indexOf("</think>", startIndex = qwenStart)
            return if (qwenEnd == -1) {
                text.substring(0, qwenStart).trim()
            } else {
                (text.substring(0, qwenStart) + text.substring(qwenEnd + "</think>".length)).trim()
            }
        }
        // Gemma 4: <|channel>thought\n...\n<channel|>
        val gemmaStart = text.indexOf("<|channel>thought")
        if (gemmaStart != -1) {
            val gemmaEnd = text.indexOf("<channel|>", startIndex = gemmaStart)
            return if (gemmaEnd == -1) {
                text.substring(0, gemmaStart).trim()
            } else {
                (text.substring(0, gemmaStart) + text.substring(gemmaEnd + "<channel|>".length)).trim()
            }
        }
        return text
    }

    /**
     * Remove Markdown code fences that small models sometimes emit despite instructions.
     * Handles variants: ```json, ```JSON, ``` (plain), and leading/trailing whitespace.
     */
    fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        val codeFenceRegex = Regex("""^```[a-zA-Z]*\s*\n?([\s\S]*?)\n?```$""")
        val match = codeFenceRegex.find(trimmed)
        return match?.groupValues?.getOrNull(1)?.trim() ?: trimmed
    }

    /**
     * Extract a string field from a JSON-like string.
     *
     * Unlike a regex approach, this scanner handles unescaped inner quotes that small
     * models sometimes emit (e.g. `"variabili nascoste che "nascondono" le info"`).
     * A `"` is treated as the closing delimiter only when the next non-whitespace
     * character is `,` or `}` — indicating a JSON boundary.
     */
    fun extractJsonField(json: String, field: String): String? {
        val keyPattern = Regex(""""$field"\s*:\s*"""")
        val match = keyPattern.find(json) ?: return null
        var i = match.range.last + 1
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                sb.append(json[i + 1])
                i += 2
                continue
            }
            if (c == '"') {
                val rest = json.substring(i + 1).trimStart()
                if (rest.startsWith(',') || rest.startsWith('}')) return sb.toString()
                i++
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString().ifBlank { null }
    }

    /**
     * Extract array items from a JSON-like string.
     *
     * Splits on commas that appear outside of quoted strings, so action items that
     * contain commas (e.g. "Prepare report, review data") are kept as a single entry.
     */
    fun extractJsonArray(json: String, field: String): List<String> {
        val pattern = """"$field"\s*:\s*\[([^\]]*)\]""".toRegex()
        val arrayContent = pattern.find(json)?.groupValues?.getOrNull(1) ?: return emptyList()
        return splitJsonArrayItems(arrayContent)
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }

    /**
     * Split a raw JSON array content string (the part between [ and ]) on commas that
     * are outside of quoted strings. Handles escaped quotes inside strings.
     */
    private fun splitJsonArrayItems(arrayContent: String): List<String> {
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var i = 0
        while (i < arrayContent.length) {
            val c = arrayContent[i]
            when {
                c == '\\' && inString && i + 1 < arrayContent.length -> {
                    current.append(c)
                    current.append(arrayContent[i + 1])
                    i += 2
                    continue
                }
                c == '"' -> {
                    inString = !inString
                    current.append(c)
                }
                c == ',' && !inString -> {
                    items.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) items.add(current.toString())
        return items
    }
}
