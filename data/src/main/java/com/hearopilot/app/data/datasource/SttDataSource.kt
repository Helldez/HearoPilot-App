package com.hearopilot.app.data.datasource

import kotlinx.coroutines.flow.Flow

/**
 * Data source interface for Speech-To-Text operations.
 *
 * Provides low-level audio recording and recognition functionality.
 */
interface SttDataSource {
    /**
     * Initialize the STT engine with a model.
     *
     * @param modelPath Absolute path to the ONNX model directory
     * @return Result indicating success or failure
     */
    suspend fun initialize(modelPath: String): Result<Unit>

    /**
     * Start audio recording and real-time recognition.
     *
     * @return Flow of recognition results
     */
    fun startRecording(): Flow<RecognitionResult>

    /**
     * Stop audio recording and release resources.
     */
    suspend fun stopRecording()

    /**
     * Release the native STT model from memory (~670 MB footprint).
     *
     * Safe to call after stopRecording() completes. The model will be lazily
     * recreated on the next initialize() call.
     */
    suspend fun releaseModel()
}

/**
 * Result from speech recognition.
 *
 * @property text Recognized text
 * @property isComplete Whether this represents a complete utterance (speech segment ended)
 */
data class RecognitionResult(
    val text: String,
    val isComplete: Boolean
)
