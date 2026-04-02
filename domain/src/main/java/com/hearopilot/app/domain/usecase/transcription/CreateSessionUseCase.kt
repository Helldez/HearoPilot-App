package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.TranscriptionSession
import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for creating a new transcription session.
 *
 * This is typically called when the user starts a new recording session.
 * The LLM context should be reset after creating a new session to ensure
 * isolation between sessions.
 *
 * @property transcriptionRepository Repository for transcription data
 */
class CreateSessionUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * Create a new transcription session.
     *
     * @param name Optional user-provided name (can be null or blank)
     * @param mode Recording mode determining insight frequency and format
     * @param outputLanguage Optional target language for translation mode
     * @param insightStrategy Whether to generate insights in real-time or at end of session
     * @param topic Optional main subject/topic for focused AI insights
     * @return Result containing the created session or an error
     */
    suspend operator fun invoke(
        name: String?,
        mode: RecordingMode = RecordingMode.SIMPLE_LISTENING,
        outputLanguage: String? = null,
        insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
        topic: String? = null
    ): Result<TranscriptionSession> {
        return transcriptionRepository.createSession(
            name = name?.takeIf { it.isNotBlank() },
            mode = mode,
            outputLanguage = outputLanguage,
            insightStrategy = insightStrategy,
            topic = topic?.takeIf { it.isNotBlank() }
        )
    }
}
