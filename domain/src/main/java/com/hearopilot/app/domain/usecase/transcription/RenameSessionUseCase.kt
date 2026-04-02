package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for renaming a transcription session.
 *
 * Blank names are normalised to null (session becomes unnamed).
 *
 * @property transcriptionRepository Repository for transcription data
 */
class RenameSessionUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * Rename a session.
     *
     * @param sessionId The session to rename
     * @param newName The new name; blank or null clears the name
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(sessionId: String, newName: String?): Result<Unit> {
        return transcriptionRepository.renameSession(sessionId, newName)
    }
}
