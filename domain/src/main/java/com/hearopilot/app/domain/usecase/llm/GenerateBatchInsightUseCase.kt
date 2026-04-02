package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.monitor.ThermalMonitor
import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Generates a single comprehensive LLM insight at the end of a session using a
 * chunked map-reduce pipeline.
 *
 * Algorithm:
 * 1. Concatenate all segment texts ordered by timestamp.
 * 2. If total <= MAX_CHUNK_CHARS, run a single inference (skip chunking).
 * 3. Otherwise split at segment boundaries into chunks of <= MAX_CHUNK_CHARS.
 * 4. Map phase: call LLM for each chunk, emitting [BatchInsightProgress.Mapping] updates.
 *    For REAL_TIME_TRANSLATION, each chunk is translated and results are concatenated directly.
 * 5. Reduce phase: if > 1 chunk, merge summaries with the merge prompt, emitting
 *    [BatchInsightProgress.Reducing]. For translation mode, skip the merge LLM call.
 * 6. Parse final output via [InsightOutputParser] and return a single [LlmInsight].
 *
 * @property llmRepository Repository for LLM inference
 * @property settingsRepository Repository for reading current app settings
 * @property resourceProvider Provider for batch prompt strings
 */
class GenerateBatchInsightUseCase(
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository,
    private val resourceProvider: ResourceProvider,
    private val thermalMonitor: ThermalMonitor? = null
) {
    companion object {
        // Maximum characters sent per chunk in the map phase (~1 200 tokens at 4.14 chars/token).
        // Smaller chunks give the model a more focused context window, improving summary quality.
        private const val MAX_CHUNK_CHARS = 5_000

        // Maximum characters in the reduce-phase input (~2 000 tokens).
        private const val MAX_MERGE_INPUT_CHARS = 8_200

        // Max LLM output tokens per phase.
        private const val MAX_TOKENS_MAP = 400    // concise per-chunk output
        private const val MAX_TOKENS_REDUCE = 768 // comprehensive merged output
        private const val MAX_TOKENS_TRANSLATE = 256

        // Minimum word count to justify batch inference — mirrors GenerateFinalInsightUseCase.
        private const val MIN_WORDS_FOR_BATCH = 20

        // Cooling gap inserted between consecutive LLM runs when the device reports thermal
        // pressure (MODERATE status or above). Allows the SoC to re-enter thermal budget
        // without interrupting the pipeline; 1 s is enough to recover ~2-3 °C on most SoCs.
        private const val COOLING_GAP_MS = 1_000L
    }

    /**
     * Run the batch insight pipeline on the given [segments].
     *
     * @param segments All completed transcription segments for this session (ordered by timestamp).
     * @param sessionId Session to associate the resulting insight with.
     * @param mode Recording mode — drives prompt choice and parsing.
     * @param outputLanguage BCP-47 target language code for translation mode; null otherwise.
     * @param topic Optional session topic injected into system prompts for focused analysis.
     * @param onProgress Callback invoked at each pipeline state transition.
     * @param onChunkInsight Callback invoked for each intermediate chunk insight (map phase only).
     *        Intermediate insights have non-empty [LlmInsight.sourceSegmentIds] and a timestamp
     *        matching the last segment in the chunk. The caller is responsible for persisting them.
     * @return [Result.success] with an [LlmInsight] if generated, [Result.success] with null if
     *         the transcript is too short, or [Result.failure] on error.
     */
    suspend operator fun invoke(
        segments: List<TranscriptionSegment>,
        sessionId: String,
        mode: RecordingMode,
        outputLanguage: String?,
        topic: String? = null,
        onProgress: (BatchInsightProgress) -> Unit = {},
        onChunkInsight: suspend (LlmInsight) -> Unit = {}
    ): Result<LlmInsight?> {
        val fullText = segments.joinToString(" ") { it.text.trim() }.trim()

        val wordCount = fullText.split(Regex("\\s+")).count { it.isNotEmpty() }
        if (wordCount < MIN_WORDS_FOR_BATCH) {
            return Result.success(null)
        }

        return try {
            val settings = settingsRepository.getSettings().first()
            val parseSettings = resolveParseSettings(settings, mode, outputLanguage)

            // Reload model before starting — no-op if already loaded.
            // For lazy-init flows (END_OF_SESSION, history insight) this is the first actual load.
            llmRepository.reloadModel().onFailure { return Result.failure(it) }
            // Post-load check: model is now in memory so the RAM reading is accurate.
            llmRepository.checkAndCacheMemoryConstraint()

            val insight = if (mode == RecordingMode.REAL_TIME_TRANSLATION) {
                runTranslationBatch(fullText, sessionId, mode, settings, outputLanguage, onProgress)
            } else {
                runAnalysisBatch(segments, sessionId, mode, settings, parseSettings, outputLanguage, topic, onProgress, onChunkInsight)
            }

            onProgress(BatchInsightProgress.Complete)
            Result.success(insight)
        } catch (e: Exception) {
            onProgress(BatchInsightProgress.Error(e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    // ─── Translation batch ────────────────────────────────────────────────────

    private suspend fun runTranslationBatch(
        fullText: String,
        sessionId: String,
        mode: RecordingMode,
        settings: AppSettings,
        outputLanguage: String?,
        onProgress: (BatchInsightProgress) -> Unit
    ): LlmInsight? {
        val systemPrompt = buildSystemPrompt(mode, settings, outputLanguage, topic = null)
        // For translation, split the plain text since segment-level IDs are not needed.
        val chunks = splitTextIntoChunks(fullText)
        val translations = mutableListOf<String>()

        chunks.forEachIndexed { index, chunk ->
            onProgress(BatchInsightProgress.Mapping(index + 1, chunks.size))
            val translation = runInference(chunk, systemPrompt, MAX_TOKENS_TRANSLATE)
            if (translation.isNotBlank()) translations.add(translation)

            coolIfNeeded()
        }

        val combinedTranslation = translations.joinToString("\n\n")
        if (combinedTranslation.isBlank()) return null

        return LlmInsight(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            title = null,
            content = combinedTranslation,
            tasks = null,
            timestamp = System.currentTimeMillis(),
            sourceSegmentIds = emptyList()
        )
    }

    // ─── Analysis batch ───────────────────────────────────────────────────────

    private suspend fun runAnalysisBatch(
        segments: List<TranscriptionSegment>,
        sessionId: String,
        mode: RecordingMode,
        settings: AppSettings,
        parseSettings: AppSettings,
        outputLanguage: String?,
        topic: String?,
        onProgress: (BatchInsightProgress) -> Unit,
        onChunkInsight: suspend (LlmInsight) -> Unit
    ): LlmInsight? {
        // Use the same mode-specific system prompt as real-time inference (including topic prefix)
        // so the output format and style match exactly what the parser and UI expect.
        val modeSystemPrompt = buildSystemPrompt(mode, settings, outputLanguage, topic)
        val segmentChunks = splitSegmentsIntoChunks(segments)
        val chunkSummaries = mutableListOf<String>()

        // Map phase: summarize each chunk, emit intermediate insights.
        segmentChunks.forEachIndexed { index, chunkSegments ->
            onProgress(BatchInsightProgress.Mapping(index + 1, segmentChunks.size))
            val chunkText = chunkSegments.joinToString(" ") { it.text.trim() }
            val rawSummary = runInference(chunkText, modeSystemPrompt, MAX_TOKENS_MAP)
            if (rawSummary.isNotBlank()) {
                chunkSummaries.add(rawSummary)

                // Emit intermediate insight only when there are multiple chunks — a single chunk
                // produces only the final insight, so an intermediate would be redundant.
                if (segmentChunks.size > 1) {
                    val parsedChunk = InsightOutputParser.parse(rawSummary, mode, parseSettings)
                    onChunkInsight(
                        LlmInsight(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            title = parsedChunk.title,
                            content = parsedChunk.content,
                            tasks = parsedChunk.tasks,
                            // Timestamp matches the last segment in this chunk so that chronological
                            // interleaving in the session detail view places it correctly.
                            timestamp = chunkSegments.last().timestamp,
                            sourceSegmentIds = chunkSegments.map { it.id }
                        )
                    )
                }
            }

            coolIfNeeded()
        }

        if (chunkSummaries.isEmpty()) return null

        // Reduce phase: if only one chunk, skip merge; otherwise merge summaries.
        val finalOutput = if (chunkSummaries.size == 1) {
            println("LLM_FINAL [single-chunk] system_start=${modeSystemPrompt.take(50)}")
            println("LLM_FINAL [single-chunk] system_end=${modeSystemPrompt.takeLast(50)}")
            println("LLM_FINAL [single-chunk] summary_start=${chunkSummaries[0].take(50)}")
            println("LLM_FINAL [single-chunk] summary_end=${chunkSummaries[0].takeLast(50)}")
            chunkSummaries[0]
        } else {
            onProgress(BatchInsightProgress.Reducing)
            // Strip tasks/action_items from each chunk summary before merging: the reduce
            // prompt already asks the model to produce a final structured output with tasks,
            // so including per-chunk tasks in the merge input only wastes context budget.
            val mergeInput = chunkSummaries
                .map { summary -> stripTasksForMerge(summary, mode, parseSettings) }
                .joinToString("\n\n---\n\n")
                .take(MAX_MERGE_INPUT_CHARS)
            println("LLM_FINAL [reduce] chunks=${chunkSummaries.size}")
            println("LLM_FINAL [reduce] system_start=${modeSystemPrompt.take(50)}")
            println("LLM_FINAL [reduce] system_end=${modeSystemPrompt.takeLast(50)}")
            println("LLM_FINAL [reduce] merge_start=${mergeInput.take(50)}")
            println("LLM_FINAL [reduce] merge_end=${mergeInput.takeLast(50)}")
            runInference(mergeInput, modeSystemPrompt, MAX_TOKENS_REDUCE)
        }

        if (finalOutput.isBlank()) return null

        val parsed = InsightOutputParser.parse(finalOutput, mode, parseSettings)

        return LlmInsight(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            title = parsed.title,
            content = parsed.content,
            tasks = parsed.tasks,
            timestamp = System.currentTimeMillis(),
            // Empty list marks this as the final (reduce) insight, distinguishing it
            // from intermediate (map) insights that carry their source segment IDs.
            sourceSegmentIds = emptyList()
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun runInference(userPrompt: String, systemPrompt: String, maxTokens: Int): String {
        // Proactively switch to conservative threads if this context size has previously
        // caused RAM pressure on this device (applies before any freeModelIfConstrained reload).
        if (llmRepository.isLargeContext(userPrompt.length)) {
            llmRepository.useConservativeThreads()
        }
        llmRepository.reloadModel()
        val builder = StringBuilder()
        llmRepository.generateInsight(userPrompt, systemPrompt, maxTokens)
            .collect { token -> builder.append(token) }
        // Record the inference size for adaptive threshold learning.
        llmRepository.recordConstrainedInference(userPrompt.length)
        return InsightOutputParser.stripCodeFences(builder.toString().trim())
    }

    /**
     * Inserts a [COOLING_GAP_MS] delay when the device SoC is under thermal pressure.
     * This prevents sustained token-generation load from deep-throttling the CPU,
     * which would otherwise degrade token/s across the entire batch pipeline.
     * No-op when [thermalMonitor] is null or the device is not overheating.
     */
    private suspend fun coolIfNeeded() {
        if (thermalMonitor?.isOverheating() == true) {
            delay(COOLING_GAP_MS)
        }
    }

    /**
     * Split plain [text] into chunks of at most [MAX_CHUNK_CHARS] characters.
     * Used for translation mode where segment boundaries are irrelevant.
     */
    private fun splitTextIntoChunks(text: String): List<String> {
        if (text.length <= MAX_CHUNK_CHARS) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_CHUNK_CHARS, text.length)
            if (end < text.length) {
                val spaceIdx = text.lastIndexOf(' ', end)
                if (spaceIdx > start) end = spaceIdx
            }
            chunks.add(text.substring(start, end).trim())
            start = end
            if (start < text.length && text[start] == ' ') start++
        }
        return chunks
    }

    /**
     * Split [segments] into groups of at most [MAX_CHUNK_CHARS] combined characters.
     * Splits at segment boundaries so each chunk retains its source segment IDs and timestamps.
     */
    private fun splitSegmentsIntoChunks(segments: List<TranscriptionSegment>): List<List<TranscriptionSegment>> {
        if (segments.isEmpty()) return emptyList()

        val chunks = mutableListOf<MutableList<TranscriptionSegment>>()
        var currentChunk = mutableListOf<TranscriptionSegment>()
        var currentChunkChars = 0

        for (segment in segments) {
            val segLen = segment.text.trim().length + 1 // +1 for the joining space
            if (currentChunk.isNotEmpty() && currentChunkChars + segLen > MAX_CHUNK_CHARS) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentChunkChars = 0
            }
            currentChunk.add(segment)
            currentChunkChars += segLen
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)

        return chunks
    }

    private fun buildSystemPrompt(
        mode: RecordingMode,
        settings: AppSettings,
        outputLanguage: String?,
        topic: String?
    ): String {
        val base = when (mode) {
            RecordingMode.REAL_TIME_TRANSLATION -> {
                val code = outputLanguage ?: settings.translationTargetLanguage
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
                if (!outputLanguage.isNullOrEmpty()) {
                    val defaultPrompt = settingsRepository.getDefaultPromptForMode(mode)
                    if (storedPrompt != defaultPrompt) storedPrompt
                    else resourceProvider.getPromptForMode(mode, outputLanguage)
                } else {
                    storedPrompt
                }
            }
        }
        return prependTopic(base, topic, outputLanguage)
    }

    private fun prependTopic(prompt: String, topic: String?, outputLanguage: String?): String {
        return if (!topic.isNullOrBlank()) {
            val localeCode = if (!outputLanguage.isNullOrEmpty()) outputLanguage else "en"
            val prefix = resourceProvider.getTopicPrefix(localeCode).format(topic)
            "$prefix\n\n$prompt"
        } else {
            prompt
        }
    }

    /**
     * Returns a condensed version of [rawSummary] suitable for the reduce-phase merge input.
     *
     * For modes that include tasks (SHORT_MEETING, LONG_MEETING), strips the action_items
     * array from the chunk summary so the merged context stays within [MAX_MERGE_INPUT_CHARS].
     * The reduce phase's system prompt re-generates tasks from the merged summaries, so the
     * per-chunk tasks are redundant in the merge input.
     *
     * For SIMPLE_LISTENING the raw summary is returned unchanged (no tasks to strip).
     */
    private fun stripTasksForMerge(
        rawSummary: String,
        mode: RecordingMode,
        parseSettings: AppSettings
    ): String {
        if (mode == RecordingMode.SIMPLE_LISTENING) return rawSummary
        val parsed = InsightOutputParser.parse(rawSummary, mode, parseSettings)
        return if (parsed.title != null) "${parsed.title}\n\n${parsed.content}" else parsed.content
    }

    private fun resolveParseSettings(
        settings: AppSettings,
        mode: RecordingMode,
        outputLanguage: String?
    ): AppSettings {
        return if (!outputLanguage.isNullOrEmpty() && mode != RecordingMode.REAL_TIME_TRANSLATION) {
            settings.copy(
                jsonFieldTitle       = resourceProvider.getJsonFieldTitle(outputLanguage),
                jsonFieldSummary     = resourceProvider.getJsonFieldSummary(outputLanguage),
                jsonFieldActionItems = resourceProvider.getJsonFieldActionItems(outputLanguage)
            )
        } else {
            settings
        }
    }
}
