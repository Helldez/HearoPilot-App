package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for saving a transcription segment.
 *
 * Typically called automatically when STT produces a complete segment
 * during an active recording session.
 *
 * @property transcriptionRepository Repository for transcription data
 */
class SaveSegmentUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * Save a transcription segment to persistent storage.
     *
     * @param segment The segment to save
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(segment: TranscriptionSegment): Result<Unit> {
        return transcriptionRepository.saveSegment(segment)
    }
}
