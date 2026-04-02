package com.hearopilot.app.domain.usecase.sync

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.usecase.llm.InsightOutputParser
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.domain.model.ThermalThrottle
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates periodic LLM inference based on accumulated transcription.
 * This is the CORE use case connecting STT streaming with LLM inference.
 *
 * Non-blocking strategy:
 * - STT flow uses unlimited buffer to never block audio processing
 * - LLM inference runs in separate coroutine launched per interval
 * - Errors in LLM don't crash STT stream
 *
 * De-duplication strategy:
 * - Maintains list of completed segments (isComplete=true) - finalized phrases
 * - Maintains current partial segment (isComplete=false) - replaces previous partial
 * - Sends to LLM: "completed1 completed2 ... currentPartial" without repetitions
 * - Example: Instead of "A  A B  A B C", LLM receives "A B C"
 *
 * REFACTORED: No longer calls startStreaming() internally.
 * Instead accepts a Flow<TranscriptionSegment> to avoid duplicate streams.
 *
 * @property llmRepository Repository for LLM operations
 * @property settingsRepository Repository for app settings
 */
class SyncSttLlmUseCase(
    private val llmRepository: LlmRepository,
    private val settingsRepository: SettingsRepository,
    private val resourceProvider: ResourceProvider
) {
    companion object {
        // Max tokens the LLM is allowed to generate per inference call, by mode.
        // Translation needs fewer tokens (just the translated text).
        // Analysis modes need more tokens for a thorough, proportional response.
        private const val MAX_TOKENS_SIMPLE_LISTENING = 512
        private const val MAX_TOKENS_SHORT_MEETING    = 600
        private const val MAX_TOKENS_LONG_MEETING     = 768
        private const val MAX_TOKENS_TRANSLATION      = 256

        // Number of completed segments kept in the rolling context buffer.
        private const val MAX_CONTEXT_SEGMENTS = 3

        // Minimum new word count required before triggering LLM inference.
        // Prevents wasting inference calls on near-empty content (e.g. a single word
        // captured between intervals). Not applied to REAL_TIME_TRANSLATION because
        // even short phrases benefit from immediate translation.
        private const val MIN_NEW_WORDS_FOR_INFERENCE = 5

        // For intervals >= this threshold (10 min), trigger LLM 30 s early so the
        // insight is ready by the time the declared interval elapses. Thermal throttling
        // is also disabled for these modes because users expect insights on schedule.
        private const val LONG_INTERVAL_THRESHOLD_MS = 10 * 60 * 1000L
        private const val LONG_INTERVAL_EARLY_TRIGGER_MS = 30_000L

        // Maximum characters sent as the "Analyze" portion of a user prompt.
        // Gemma 3 1B has a 4096-token context window. Budget breakdown:
        //   system prompt             : ~150 tokens
        //   Context prefix (3 segs)   : ~150 tokens
        //   Analyze content           : ~2 900 tokens  (12 000 chars ÷ 4.14*)
        //   output budget (LONG_MTG)  :   768 tokens
        //   total                     : ~3 968 / 4 096 — ~128-token safety margin
        // * 4.14 chars/token measured on real Italian session logs.
        // Previous value was 6 000 (~1 450 tokens) which truncated the first
        // ~15-20 segments of a dense 20-min session.
        private const val MAX_USER_PROMPT_CHARS = 12_000
    }

    /**
     * Invoke the use case to start synchronized STT-LLM processing.
     *
     * @param transcriptionFlow Shared flow of transcription segments from STT
     * @param sessionId The session ID for this recording
     * @param mode The recording mode (Simple, Short, Long, Translation)
     * @param inputLanguage Language of the audio source
     * @param outputLanguage Target language BCP-47 code for translation (if applicable)
     * @param topic Optional session topic injected into the system prompt for focused analysis
     * @return Flow of LLM insights
     */
    operator fun invoke(
        transcriptionFlow: Flow<TranscriptionSegment>,
        sessionId: String,
        mode: RecordingMode,
        inputLanguage: String,
        outputLanguage: String?,
        topic: String? = null
    ): Flow<LlmInsight> = channelFlow {
        val settings = settingsRepository.getSettings().first()
        var baseIntervalMs = calculateIntervalMs(mode, settings)
        var thermalFactor = 1.0f
        var intervalMs = baseIntervalMs
        var currentSystemPrompt = buildSystemPrompt(mode, settings, outputLanguage, topic)
        var currentSamplerConfig = settings.llmSamplerConfig

        // When an explicit output language is chosen, parse JSON with that locale's field
        // names (e.g. Italian → "titolo"/"riepilogo"/"azioni") so the parser matches the
        // prompt schema. Falls back to device-locale names when outputLanguage is null/empty.
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

        // Initialize system prompt
        llmRepository.updateSystemPrompt(currentSystemPrompt)

        // Monitor settings changes (interval and system prompt are reactive)
        launch {
            settingsRepository.getSettings().collect { updatedSettings ->
                baseIntervalMs = calculateIntervalMs(mode, updatedSettings)
                intervalMs = (baseIntervalMs * thermalFactor).toLong()
                val newPrompt = buildSystemPrompt(mode, updatedSettings, outputLanguage, topic)

                if (newPrompt != currentSystemPrompt) {
                    currentSystemPrompt = newPrompt
                    llmRepository.updateSystemPrompt(currentSystemPrompt)
                }

                if (updatedSettings.llmSamplerConfig != currentSamplerConfig) {
                    currentSamplerConfig = updatedSettings.llmSamplerConfig
                    llmRepository.updateSamplerConfig(currentSamplerConfig)
                }
            }
        }

        // Monitor device thermal state and scale the inference interval accordingly.
        // For short intervals, ThermalThrottle.Reduced extends the interval so the LLM
        // fires less frequently, protecting battery when the device is hot.
        // For long intervals (>= 10 min) thermal throttling is intentionally skipped:
        // users who chose a 20-min interval expect insights on schedule and would not
        // accept a 30-min delay caused by thermal conditions.
        launch {
            llmRepository.thermalThrottleFlow.collect { throttle ->
                thermalFactor = when {
                    baseIntervalMs >= LONG_INTERVAL_THRESHOLD_MS -> 1.0f // never throttle long modes
                    throttle is ThermalThrottle.Reduced -> throttle.factor
                    else -> 1.0f
                }
                intervalMs = (baseIntervalMs * thermalFactor).toLong()
            }
        }

        // Rolling context buffer: last MAX_CONTEXT_SEGMENTS completed segments.
        // Helps the model resolve pronouns and references across intervals.
        // Not used for REAL_TIME_TRANSLATION (each segment is self-contained).
        val contextBuffer = mutableListOf<String>()

        val newSegmentsSinceLastLlm = mutableListOf<String>()
        var currentPartialSegment = ""
        var lastPartialSent = ""
        // Only track IDs of finalized (isComplete=true) segments — these are guaranteed
        // to be persisted in DB. Partial segment IDs are excluded to avoid orphan references.
        val completeSegmentIds = mutableListOf<String>()
        var lastInferenceTime = System.currentTimeMillis()
        // Guard against concurrent LLM calls: if the previous call is still running,
        // skip the current interval to avoid queuing inference requests on slow devices.
        // AtomicBoolean ensures visibility between the collect coroutine and the launch
        // child coroutine, which may run on different threads in the Default pool.
        val isLlmBusy = AtomicBoolean(false)

        val maxTokens = maxTokensForMode(mode)

        transcriptionFlow
            .collect { segment ->
                if (segment.text.isNotBlank()) {
                    if (segment.isComplete) {
                        // Track only finalized segments — partial IDs are excluded
                        completeSegmentIds.add(segment.id)
                        if (lastPartialSent.isNotBlank() && segment.text.startsWith(lastPartialSent)) {
                            val newPart = segment.text.substring(lastPartialSent.length).trim()
                            if (newPart.isNotBlank()) newSegmentsSinceLastLlm.add(newPart)
                        } else {
                            newSegmentsSinceLastLlm.add(segment.text)
                        }
                        currentPartialSegment = ""
                        lastPartialSent = ""

                        // Update rolling context buffer
                        contextBuffer.add(segment.text)
                        if (contextBuffer.size > MAX_CONTEXT_SEGMENTS) {
                            contextBuffer.removeAt(0)
                        }
                    } else {
                        currentPartialSegment = segment.text
                    }
                }

                val now = System.currentTimeMillis()
                val timeSinceLastInference = now - lastInferenceTime

                // For long intervals (>= 10 min) fire 30 s early so LLM generation
                // completes by the time the declared interval elapses for the user.
                val effectiveIntervalMs = if (intervalMs >= LONG_INTERVAL_THRESHOLD_MS)
                    intervalMs - LONG_INTERVAL_EARLY_TRIGGER_MS
                else
                    intervalMs

                if (timeSinceLastInference >= effectiveIntervalMs) {
                    // Skip if a previous LLM call is still running — prevents queuing
                    // inference requests on slow devices and wastes battery.
                    if (isLlmBusy.get()) return@collect

                    val deltaSegments = mutableListOf<String>()
                    deltaSegments.addAll(newSegmentsSinceLastLlm)
                    if (currentPartialSegment.isNotBlank()) {
                        deltaSegments.add(currentPartialSegment)
                    }

                    val newContent = deltaSegments.joinToString(" ").trim()
                    println("[SyncSttLlm] Trigger: ${deltaSegments.size} segments, ${newContent.length} chars accumulated")

                    // Skip if not enough new content (except REAL_TIME_TRANSLATION,
                    // where even short phrases should be translated immediately).
                    if (mode != RecordingMode.REAL_TIME_TRANSLATION) {
                        val wordCount = newContent.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                        if (wordCount < MIN_NEW_WORDS_FOR_INFERENCE) return@collect
                    }

                    if (newContent.isNotBlank()) {
                        lastInferenceTime = now
                        // Source IDs reference only complete segments (always persisted in DB)
                        val sourceIds = completeSegmentIds.toList()
                        lastPartialSent = currentPartialSegment
                        newSegmentsSinceLastLlm.clear()
                        completeSegmentIds.clear()

                        // Capture timestamp before LLM call so the insight
                        // reflects when generation started, not when it finished.
                        val insightTimestamp = System.currentTimeMillis()

                        // Build the prompt before launching so its char count is available
                        // for the isLargeContext() check before the model reload.
                        val finalPrompt = buildUserPrompt(mode, contextBuffer, newContent)

                        // Proactively switch to conservative threads if this context size has
                        // previously caused RAM pressure on this device (adaptive threshold).
                        if (llmRepository.isLargeContext(finalPrompt.length)) {
                            llmRepository.useConservativeThreads()
                        }

                        isLlmBusy.set(true)
                        launch {
                            try {
                                // Signal inference start before reloadModel so isGenerating covers
                                // the full window — including LONG_MEETING lazy model load (5-15 s).
                                // Without this, stopStreaming() could read isGenerating=false and
                                // skip the wait while the model is still loading, then generate a
                                // spurious final insight on top of this in-flight one.
                                llmRepository.beginInference()
                                // Reload model if it was unloaded after the previous LONG_MEETING
                                // inference. No-op when the model is already in memory.
                                llmRepository.reloadModel()
                                // Post-load check: model is in memory so RAM reading is accurate.
                                llmRepository.checkAndCacheMemoryConstraint()

                                val insightBuilder = StringBuilder()
                                llmRepository.generateInsight(finalPrompt, currentSystemPrompt, maxTokens)
                                    .collect { token -> insightBuilder.append(token) }

                                val rawOutput = insightBuilder.toString().trim()

                                if (rawOutput.isNotBlank()) {
                                    val parsed = parseInsightOutput(rawOutput, mode, parseSettings)
                                    send(LlmInsight(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        title = parsed.title,
                                        content = parsed.content,
                                        tasks = parsed.tasks,
                                        timestamp = insightTimestamp,
                                        sourceSegmentIds = sourceIds
                                    ))
                                }
                            } catch (e: Exception) {
                                // Re-throw CancellationException so the coroutine machinery
                                // can propagate cancellation correctly. Swallowing it would
                                // prevent the parent channelFlow from cancelling cleanly and
                                // could leave the LLM in an inconsistent state when Stop is
                                // pressed while inference is in progress.
                                if (e is CancellationException) throw e
                                println("LLM error: ${e.message}")
                            } finally {
                                // NonCancellable ensures cleanup always runs even when this
                                // coroutine is being cancelled (e.g. user pressed Stop mid-inference).
                                // Without it, the suspend calls inside finally would immediately
                                // throw CancellationException, skipping cleanup() and leaving the
                                // llama.cpp context in an inconsistent state for the next caller.
                                withContext(NonCancellable) {
                                    // Reset isGenerating here as a fallback for the case where the
                                    // coroutine was cancelled before generateInsight() was ever
                                    // collected (e.g. during reloadModel). In that path onCompletion
                                    // never fires, so isGenerating would otherwise stay true forever.
                                    llmRepository.endInference()
                                    // Record the inference size for adaptive threshold learning.
                                    llmRepository.recordConstrainedInference(finalPrompt.length)
                                    if (mode == RecordingMode.LONG_MEETING && llmRepository.isMemoryConstrained()) {
                                        // Free ~700 MB immediately; the model will be reloaded before
                                        // the next inference. On devices with sufficient RAM this branch
                                        // is never taken and the model stays warm between inferences.
                                        llmRepository.cleanup()
                                    }
                                    isLlmBusy.set(false)
                                }
                            }
                        }
                    }
                }
            }
    }

    // ─── Interval ───────────────────────────────────────────────────────────────

    private fun calculateIntervalMs(mode: RecordingMode, settings: AppSettings): Long {
        return when (mode) {
            RecordingMode.SIMPLE_LISTENING     -> settings.simpleListeningIntervalSeconds * 1000L
            RecordingMode.SHORT_MEETING        -> settings.shortMeetingIntervalSeconds * 1000L
            RecordingMode.LONG_MEETING         -> settings.longMeetingIntervalMinutes * 60 * 1000L
            RecordingMode.REAL_TIME_TRANSLATION -> settings.translationIntervalSeconds * 1000L
        }
    }

    // ─── Max tokens per mode ─────────────────────────────────────────────────────

    private fun maxTokensForMode(mode: RecordingMode): Int = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> MAX_TOKENS_SIMPLE_LISTENING
        RecordingMode.SHORT_MEETING         -> MAX_TOKENS_SHORT_MEETING
        RecordingMode.LONG_MEETING          -> MAX_TOKENS_LONG_MEETING
        RecordingMode.REAL_TIME_TRANSLATION -> MAX_TOKENS_TRANSLATION
    }

    // ─── System prompt ───────────────────────────────────────────────────────────

    /**
     * Returns the system prompt for the given mode, substituting {target_language}
     * with the English name of the target language (looked up from SupportedLanguages).
     *
     * If [topic] is provided it is prepended to the prompt so the LLM focuses its
     * analysis on that subject throughout the session.
     *
     * The stored value is always a BCP-47 code (e.g. "it"). The LLM prompt receives
     * the English name (e.g. "Italian") so the model understands it reliably.
     */
    private fun buildSystemPrompt(
        mode: RecordingMode,
        settings: AppSettings,
        targetLangCode: String?,
        topic: String? = null
    ): String {
        val basePrompt = when (mode) {
            RecordingMode.REAL_TIME_TRANSLATION -> {
                val code = targetLangCode ?: settings.translationTargetLanguage
                // Resolve BCP-47 code to English name; fall back to the raw code if unknown.
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
                    // User selected a specific output language: normally load the prompt
                    // translated into that locale. However, if the user has customised the
                    // prompt (stored value differs from the device-locale default), honour
                    // their customisation regardless of output language.
                    val defaultPrompt = settingsRepository.getDefaultPromptForMode(mode)
                    if (storedPrompt != defaultPrompt) storedPrompt
                    else resourceProvider.getPromptForMode(mode, targetLangCode)
                } else {
                    // No explicit language selected: use the stored prompt (device locale
                    // default or user-customised value).
                    storedPrompt
                }
            }
        }
        // Prepend topic context if provided so the model focuses on the stated subject.
        // Topic is passed verbatim — never translated — since the user typed it themselves.
        // The surrounding sentence is localized to the output language.
        return if (!topic.isNullOrBlank()) {
            val localeCode = if (!targetLangCode.isNullOrEmpty()) targetLangCode else "en"
            val prefix = resourceProvider.getTopicPrefix(localeCode).format(topic)
            "$prefix\n\n$basePrompt"
        } else {
            basePrompt
        }
    }

    // ─── User prompt ─────────────────────────────────────────────────────────────

    /**
     * Builds the user-facing prompt sent to the LLM for each inference interval.
     *
     * Analysis modes (Simple/Short/Long) include a rolling context prefix so the model
     * can resolve references across intervals.
     *
     * Translation mode receives ONLY the raw text — no Context/Analyze wrapper.
     * Adding such a wrapper causes small models to echo "Context:" or "Il contesto:"
     * instead of translating, because they interpret the structure as a conversation cue.
     */
    private fun buildUserPrompt(
        mode: RecordingMode,
        contextBuffer: List<String>,
        newContent: String
    ): String {
        if (mode == RecordingMode.REAL_TIME_TRANSLATION) {
            // Translation: deliver raw text only; the system prompt already specifies the language.
            // Cap to avoid exceeding the model's context window on rapid speech.
            return newContent.takeLast(MAX_USER_PROMPT_CHARS)
        }

        // Cap newContent to the most recent portion so the total prompt stays within
        // the model's context window regardless of how long the interval was.
        // The tail of the transcript is most relevant ("what just happened").
        val cappedContent = newContent.takeLast(MAX_USER_PROMPT_CHARS)
        if (newContent.length > MAX_USER_PROMPT_CHARS) {
            val dropped = newContent.length - MAX_USER_PROMPT_CHARS
            println("[SyncSttLlm] TRUNCATED: ${newContent.length} chars → $MAX_USER_PROMPT_CHARS (dropped $dropped chars from start)")
            println("[SyncSttLlm] First visible: \"${cappedContent.take(80)}\"")
        } else {
            println("[SyncSttLlm] No truncation: ${newContent.length} / $MAX_USER_PROMPT_CHARS chars")
            println("[SyncSttLlm] First visible: \"${cappedContent.take(80)}\"")
        }
        println("[SyncSttLlm] Last visible:  \"${cappedContent.takeLast(80)}\"")
        val contextText = contextBuffer.joinToString(" ")
        return if (contextText.isNotBlank()) {
            "Context: $contextText\n\nAnalyze: $cappedContent"
        } else {
            cappedContent
        }
    }

    // ─── Output parsing ──────────────────────────────────────────────────────────
    // Delegated to InsightOutputParser to avoid code duplication across use cases.

    private fun parseInsightOutput(
        rawOutput: String,
        mode: RecordingMode,
        settings: AppSettings
    ): InsightOutputParser.ParsedInsight = InsightOutputParser.parse(rawOutput, mode, settings)
}
