package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.model.TranscriptionSession
import com.hearopilot.app.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving all transcription sessions.
 *
 * Sessions are ordered by creation time (most recent first) to show
 * the latest recordings at the top of the list.
 *
 * @property transcriptionRepository Repository for transcription data
 */
class GetAllSessionsUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * Get all transcription sessions.
     *
     * @return Flow emitting list of sessions ordered by creation time (descending)
     */
    operator fun invoke(): Flow<List<TranscriptionSession>> {
        return transcriptionRepository.getAllSessions()
    }
}
