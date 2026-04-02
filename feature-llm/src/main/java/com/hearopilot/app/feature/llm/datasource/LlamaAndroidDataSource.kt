package com.hearopilot.app.feature.llm.datasource

import android.util.Log
import com.arm.aichat.InferenceEngine
import com.hearopilot.app.data.datasource.LlmDataSource
import com.hearopilot.app.domain.model.LlmSamplerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * llama.android implementation of LlmDataSource.
 *
 * Wraps the InferenceEngine from llama.cpp-based library.
 *
 * @property inferenceEngine The llama.android inference engine
 */
class LlamaAndroidDataSource(
    private val inferenceEngine: InferenceEngine
) : LlmDataSource {

    companion object {
        private const val TAG = "LlamaAndroidDataSource"
    }

    override suspend fun loadModel(modelPath: String, systemPrompt: String?, nThreadsHint: Int): Result<Unit> = runCatching {
        // If the engine crashed to Error state (e.g. OOM during a previous inference),
        // attempt a cleanup to restore a loadable state before proceeding.
        // Without this, calling loadModel() in Error state throws immediately.
        if (inferenceEngine.state.value is InferenceEngine.State.Error) {
            Log.w(TAG, "Engine in Error state before loadModel; attempting cleanup recovery")
            try {
                inferenceEngine.cleanUp()
                delay(300) // Give the engine time to reset its internal state
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup during Error recovery threw: ${e.message}")
            }
        }

        // Wait for initialization (from InferenceEngineImpl.kt state machine)
        inferenceEngine.state.first { it !is InferenceEngine.State.Initializing }

        // After recovery, if still in Error, we cannot proceed — propagate the cause.
        val stateAfterRecovery = inferenceEngine.state.value
        if (stateAfterRecovery is InferenceEngine.State.Error) {
            throw stateAfterRecovery.exception
        }

        // Load the model
        inferenceEngine.loadModel(modelPath, nThreadsHint)

        // Wait for model to be ready
        inferenceEngine.state.first { it !is InferenceEngine.State.LoadingModel }

        // Check if we're in error state
        when (val currentState = inferenceEngine.state.value) {
            is InferenceEngine.State.Error -> throw currentState.exception
            is InferenceEngine.State.ModelReady -> {
                // Set system prompt (use provided or default)
                val promptToUse = systemPrompt ?: """
                    You are an AI assistant analyzing real-time speech transcriptions.
                    Respond in the same language as the transcription.
                    Provide concise insights, suggestions, or brief summaries.
                    Keep responses under 100 words. Be actionable and helpful.
                """.trimIndent()
                inferenceEngine.setSystemPrompt(promptToUse)
            }
            else -> throw IllegalStateException("Unexpected state: ${currentState}")
        }
    }

    override fun sendPrompt(prompt: String, maxTokens: Int): Flow<String> {
        // inferenceEngine.sendUserPrompt returns Flow<String> (token stream).
        // maxTokens is supplied by the caller (SyncSttLlmUseCase) based on recording mode.
        return inferenceEngine.sendUserPrompt(
            message = prompt,
            predictLength = maxTokens
        )
    }

    override suspend fun updateSystemPrompt(systemPrompt: String): Result<Unit> = runCatching {
        // Wait for model to be ready before updating
        inferenceEngine.state.first {
            it !is InferenceEngine.State.LoadingModel &&
            it !is InferenceEngine.State.Initializing
        }

        when (val currentState = inferenceEngine.state.value) {
            is InferenceEngine.State.Error -> throw currentState.exception
            is InferenceEngine.State.ModelReady -> {
                inferenceEngine.setSystemPrompt(systemPrompt)
            }
            else -> throw IllegalStateException("Cannot update system prompt in state: $currentState")
        }
    }

    override suspend fun updateSamplerConfig(config: LlmSamplerConfig): Result<Unit> = runCatching {
        inferenceEngine.setSamplerConfig(
            temperature = config.temperature,
            topK = config.topK,
            topP = config.topP,
            minP = config.minP,
            repeatPenalty = config.repeatPenalty
        )
    }

    override suspend fun cleanup() {
        inferenceEngine.cleanUp()
    }
}
