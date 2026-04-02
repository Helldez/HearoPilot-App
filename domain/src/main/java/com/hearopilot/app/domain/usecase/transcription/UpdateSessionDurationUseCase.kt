package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for persisting the total recording duration of a session.
 *
 * Called once when recording stops. Stores the elapsed time so it can be
 * displayed in the session history list without recomputing from timestamps.
 *
 * @property transcriptionRepository Repository for transcription data
 */
class UpdateSessionDurationUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * @param sessionId The session whose duration is being stored
     * @param durationMs Total recording duration in milliseconds
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(sessionId: String, durationMs: Long): Result<Unit> {
        return transcriptionRepository.updateSessionDuration(sessionId, durationMs)
    }
}
