package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for updating the content and/or tasks of an existing LLM insight.
 */
class UpdateInsightContentUseCase(
    private val repository: TranscriptionRepository
) {
    /** Update the summary/content text of an insight. */
    suspend fun updateContent(insightId: String, newContent: String): Result<Unit> =
        repository.updateInsightContent(insightId, newContent)

    /** Update the tasks JSON of an insight. */
    suspend fun updateTasks(insightId: String, newTasks: String?): Result<Unit> =
        repository.updateInsightTasks(insightId, newTasks)

    /** Update the title of an insight. */
    suspend fun updateTitle(insightId: String, newTitle: String?): Result<Unit> =
        repository.updateInsightTitle(insightId, newTitle)
}
