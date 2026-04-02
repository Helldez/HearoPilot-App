package com.hearopilot.app.data.repository

import com.hearopilot.app.data.datasource.SttDataSource
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.repository.SttRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of SttRepository that bridges data source to domain layer.
 *
 * @property sttDataSource Data source for STT operations
 */
class SttRepositoryImpl @Inject constructor(
    private val sttDataSource: SttDataSource
) : SttRepository {

    override suspend fun initialize(modelPath: String): Result<Unit> {
        return sttDataSource.initialize(modelPath)
    }

    override fun startStreaming(): Flow<TranscriptionSegment> {
        return sttDataSource.startRecording()
            .map { result ->
                TranscriptionSegment(
                    id = UUID.randomUUID().toString(),
                    sessionId = "", // Temporary - MainViewModel will replace with actual sessionId
                    text = result.text,
                    timestamp = System.currentTimeMillis(),
                    isComplete = result.isComplete
                )
            }
    }

    override suspend fun stopStreaming() {
        sttDataSource.stopRecording()
    }

    override suspend fun releaseModel() {
        sttDataSource.releaseModel()
    }
}
