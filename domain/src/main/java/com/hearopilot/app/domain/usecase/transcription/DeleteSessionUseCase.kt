package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for deleting a transcription session.
 *
 * Deletes the session and all associated segments and insights (CASCADE delete).
 * This operation is irreversible.
 *
 * @property transcriptionRepository Repository for transcription data
 */
class DeleteSessionUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * Delete a session and all its data.
     *
     * @param sessionId The session ID to delete
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        return transcriptionRepository.deleteSession(sessionId)
    }
}
