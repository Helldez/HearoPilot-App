package com.hearopilot.app.data.datasource

import com.hearopilot.app.domain.model.LlmSamplerConfig
import kotlinx.coroutines.flow.Flow

/**
 * Data source interface for Local LLM operations.
 *
 * Provides low-level text generation functionality using llama.cpp.
 */
interface LlmDataSource {
    /**
     * Load an LLM model into memory.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @param systemPrompt System prompt to set model behavior (optional)
     * @param nThreadsHint Number of inference threads; -1 = engine default (auto from CPU count).
     *   Pass a lower value (e.g. 2) for long sessions on RAM-constrained devices.
     * @return Result indicating success or failure
     */
    suspend fun loadModel(modelPath: String, systemPrompt: String? = null, nThreadsHint: Int = -1): Result<Unit>

    /**
     * Send a prompt to the LLM and stream the response.
     *
     * @param prompt Input prompt text
     * @param maxTokens Maximum number of tokens to generate. Callers should
     *   pass a mode-appropriate value (e.g. 256 for translation, 512+ for analysis).
     * @return Flow of generated tokens
     */
    fun sendPrompt(prompt: String, maxTokens: Int): Flow<String>

    /**
     * Update the system prompt without reloading the model.
     *
     * @param systemPrompt New system prompt to set
     * @return Result indicating success or failure
     */
    suspend fun updateSystemPrompt(systemPrompt: String): Result<Unit>

    /**
     * Update the sampler configuration without reloading the model.
     *
     * @param config New sampler configuration to apply
     * @return Result indicating success or failure
     */
    suspend fun updateSamplerConfig(config: LlmSamplerConfig): Result<Unit>

    /**
     * Clean up model and release resources.
     */
    suspend fun cleanup()
}
