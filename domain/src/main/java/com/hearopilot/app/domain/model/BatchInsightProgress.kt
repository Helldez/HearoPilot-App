package com.hearopilot.app.domain.model

/**
 * Progress states for the end-of-session batch insight pipeline.
 *
 * Emitted by [com.hearopilot.app.domain.usecase.llm.GenerateBatchInsightUseCase]
 * as it processes transcript chunks in a map-reduce fashion.
 */
sealed class BatchInsightProgress {
    /** No batch processing active. */
    object Idle : BatchInsightProgress()

    /** Map phase: LLM is processing the Nth chunk out of [totalChunks]. */
    data class Mapping(val currentChunk: Int, val totalChunks: Int) : BatchInsightProgress()

    /** Reduce phase: merging per-chunk summaries into the final insight. */
    object Reducing : BatchInsightProgress()

    /** Pipeline completed successfully — final insight is now in the database. */
    object Complete : BatchInsightProgress()

    /** An unrecoverable error occurred during batch processing. */
    data class Error(val message: String) : BatchInsightProgress()
}
