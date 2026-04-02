package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns the total bytes of all transcription and insight text stored on-device.
 *
 * The value updates reactively whenever sessions, segments, or insights change.
 * Always emits 0 or more; never negative.
 */
class GetTotalDataSizeUseCase(
    private val repository: TranscriptionRepository
) {
    operator fun invoke(): Flow<Long> = repository.getTotalDataSizeBytes()
}
