package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.repository.TranscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Generates an AI insight from an existing history session by:
 * 1. Loading all complete segments from the source session.
 * 2. Creating a new copy session with the same metadata (mode, topic, language).
 * 3. Copying segments to the new session.
 * 4. Running [GenerateBatchInsightUseCase] on the copied segments.
 * 5. Saving the resulting insight to the new session.
 *
 * The original session is never modified.
 *
 * @property transcriptionRepository Repository for session and segment persistence.
 * @property generateBatchInsightUseCase Map-reduce pipeline for generating the insight.
 */
class GenerateHistoryInsightUseCase(
    private val transcriptionRepository: TranscriptionRepository,
    private val generateBatchInsightUseCase: GenerateBatchInsightUseCase
) {
    companion object {
        // A session needs at least this many complete segments to justify a batch insight call.
        private const val MIN_COMPLETE_SEGMENTS = 1
        // Minimum total characters across all complete segments to justify running the pipeline.
        // Sessions with less content are silently skipped (no error shown to the user).
        private const val MIN_TOTAL_CHARS = 200
    }

    /**
     * Generate an AI insight for the given history session, producing a new copy session.
     *
     * @param sourceSessionId ID of the session to analyze.
     * @param newSessionName Name to assign to the new copy session (caller supplies localized string).
     * @param overrideOutputLanguage BCP-47 code chosen by the user at generation time.
     *        When non-null and non-blank it overrides the source session's stored outputLanguage,
     *        allowing insights from old recordings (which have no saved outputLanguage) to be
     *        generated in the user's preferred language.
     * @param onProgress Callback emitting pipeline progress updates.
     * @param onSessionCreated Callback invoked as soon as the copy session is created, passing its ID.
     *        The caller can use this to delete the partial session on cancellation.
     * @return [Result.success] with the new session ID, or [Result.failure] on any error.
     */
    suspend operator fun invoke(
        sourceSessionId: String,
        newSessionName: String?,
        overrideOutputLanguage: String? = null,
        onProgress: (BatchInsightProgress) -> Unit = {},
        onSessionCreated: (String) -> Unit = {},
        /** Called with each intermediate (map-phase) chunk insight as it completes. */
        onChunkInsight: (LlmInsight) -> Unit = {},
        /** Called with the final (reduce-phase) merged insight before it is persisted. */
        onFinalInsight: (LlmInsight) -> Unit = {}
    ): Result<String> {
        return try {
            // Load source session details (one-shot — not streaming).
            val sourceDetails = transcriptionRepository
                .getSessionWithDetails(sourceSessionId)
                .first()
                ?: return Result.failure(NoSuchElementException("Session not found: $sourceSessionId"))

            val completeSegments = sourceDetails.segments.filter { it.isComplete }
            if (completeSegments.size < MIN_COMPLETE_SEGMENTS) {
                return Result.failure(IllegalStateException("Not enough transcript content"))
            }

            // Silently skip if the total character count is below the threshold.
            // Returns a blank session ID as a sentinel so the caller can suppress the error.
            val totalChars = completeSegments.sumOf { it.text.trim().length }
            if (totalChars < MIN_TOTAL_CHARS) {
                return Result.success("")
            }

            val sourceSession = sourceDetails.session

            // Resolve effective output language: user override takes precedence, then session value.
            val effectiveOutputLanguage = overrideOutputLanguage?.takeIf { it.isNotBlank() }
                ?: sourceSession.outputLanguage

            // Create a new session that is a copy of the source, always using BATCH strategy.
            val newSession = transcriptionRepository.createSession(
                name = newSessionName,
                mode = sourceSession.mode,
                outputLanguage = effectiveOutputLanguage,
                insightStrategy = InsightStrategy.END_OF_SESSION,
                topic = sourceSession.topic
            ).getOrElse { return Result.failure(it) }

            // Notify caller of the new session ID so it can delete the partial session
            // if the user cancels the pipeline mid-way.
            onSessionCreated(newSession.id)

            // Copy segments to the new session, assigning fresh IDs to avoid primary-key conflicts.
            val copiedSegments = completeSegments.map { segment ->
                segment.copy(
                    id = UUID.randomUUID().toString(),
                    sessionId = newSession.id
                )
            }
            copiedSegments.forEach { segment ->
                transcriptionRepository.saveSegment(segment)
                    .onFailure { e -> return Result.failure(e) }
            }

            // Run the map-reduce batch pipeline on the copied segments.
            // Use session.topic if set; fall back to session.name so that renamed sessions
            // still benefit from a topic context (renaming updates name but not topic).
            val effectiveTopic = sourceSession.topic ?: sourceSession.name
            val insight = generateBatchInsightUseCase(
                segments = copiedSegments,
                sessionId = newSession.id,
                mode = sourceSession.mode,
                outputLanguage = effectiveOutputLanguage,
                topic = effectiveTopic,
                onProgress = onProgress,
                onChunkInsight = { chunkInsight ->
                    // Persist each intermediate (map-phase) insight immediately so it is
                    // visible in the session detail view interleaved with its source segments.
                    transcriptionRepository.saveInsight(chunkInsight)
                    // Notify the caller so it can display the chunk progressively in the UI.
                    onChunkInsight(chunkInsight)
                }
            ).getOrElse { return Result.failure(it) }

            // Persist the final (reduce) insight if the pipeline produced one (null = not enough words).
            insight?.let {
                // Notify the caller before persisting so the UI can pin the final insight immediately.
                onFinalInsight(it)
                transcriptionRepository.saveInsight(it)
                    .onFailure { e -> return Result.failure(e) }
            }

            Result.success(newSession.id)
        } catch (e: CancellationException) {
            // Propagate cancellation so the caller's coroutine stops cleanly.
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
