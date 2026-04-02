package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for updating the text of an existing transcription segment.
 */
class UpdateSegmentTextUseCase(
    private val repository: TranscriptionRepository
) {
    suspend operator fun invoke(segmentId: String, newText: String): Result<Unit> =
        repository.updateSegmentText(segmentId, newText)
}
