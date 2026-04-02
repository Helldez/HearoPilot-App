package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.model.SessionWithDetails
import com.hearopilot.app.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving a session with all its details.
 *
 * Returns a complete view of the session including all segments and insights,
 * suitable for displaying in a details screen.
 *
 * @property transcriptionRepository Repository for transcription data
 */
class GetSessionDetailsUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * Get session details including all segments and insights.
     *
     * @param sessionId The session ID to retrieve
     * @return Flow emitting session details or null if not found
     */
    operator fun invoke(sessionId: String): Flow<SessionWithDetails?> {
        return transcriptionRepository.getSessionWithDetails(sessionId)
    }
}
