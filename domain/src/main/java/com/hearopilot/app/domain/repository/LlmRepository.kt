package com.hearopilot.app.domain.repository

import com.hearopilot.app.domain.model.LlmSamplerConfig
import com.hearopilot.app.domain.model.ThermalThrottle
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Local Large Language Model operations.
 *
 * Provides text generation capabilities using llama.android (llama.cpp).
 */
interface LlmRepository {
    /**
     * Initialize the LLM engine with a model.
     *
     * @param modelPath Absolute path to the GGUF model file
     * @param systemPrompt System prompt to set model behavior (optional)
     * @param loadImmediately If false, stores path/prompt but defers model loading.
     *   Use for LONG_MEETING where the model will be loaded lazily before each inference.
     * @return Result indicating success or failure
     */
    suspend fun initialize(
        modelPath: String,
        systemPrompt: String? = null,
        loadImmediately: Boolean = true
    ): Result<Unit>

    /**
     * Reload the model using the last stored modelPath and systemPrompt.
     * Idempotent: if the model is already loaded, returns success immediately (no I/O).
     * Used by LONG_MEETING to reload after unloading between inferences.
     *
     * @return Result indicating success or failure
     */
    suspend fun reloadModel(): Result<Unit>

    /**
     * Returns true if available RAM is below the threshold needed to keep the LLM
     * loaded between inferences without risking kswapd pressure.
     * Used by LONG_MEETING to decide whether to unload after each inference.
     */
    fun isMemoryConstrained(): Boolean

    /**
     * Generate insight/response based on input text.
     *
     * @param text Input text (transcription) to analyze
     * @param systemPrompt System prompt instructions to include in this request (ensures small models remember instructions)
     * @param maxTokens Maximum number of tokens to generate; caller supplies a mode-appropriate budget
     * @return Flow of generated tokens. Collect to build complete response.
     */
    fun generateInsight(text: String, systemPrompt: String? = null, maxTokens: Int = 512): Flow<String>

    /**
     * Update the system prompt without reloading the model.
     *
     * @param systemPrompt New system prompt to set
     * @return Result indicating success or failure
     */
    suspend fun updateSystemPrompt(systemPrompt: String): Result<Unit>

    /**
     * Switch to conservative thread count (2 threads) for subsequent model loads.
     * Takes effect on the next [reloadModel] or [initialize] call.
     */
    fun useConservativeThreads()

    /**
     * Call immediately after a successful model load.
     * Checks RAM while the model is in memory (accurate reading) and updates the
     * persisted cached state:
     * - If constrained: persists true and enables conservative threads (sticky-true).
     * - If not constrained and previously detected: resets to false.
     * This is the only correct point to evaluate RAM pressure because the model weight
     * footprint (~650 MB–1 GB) is fully accounted for in the reading.
     */
    suspend fun checkAndCacheMemoryConstraint()

    /**
     * Call after each inference completes with the input prompt character count.
     * If the device is RAM-constrained at that moment, records [inputChars] as the
     * calibration point: future inferences with chars >= [inputChars] * safety margin
     * will proactively enable conservative threads before the next model load.
     * Takes the minimum with any previously recorded threshold (converges downward).
     */
    suspend fun recordConstrainedInference(inputChars: Int)

    /**
     * Returns true if [inputChars] meets or exceeds the learned char threshold above
     * which conservative threads should be proactively enabled before the next load.
     * Callers should invoke [useConservativeThreads] when this returns true.
     */
    fun isLargeContext(inputChars: Int): Boolean

    /**
     * Update the sampler configuration without reloading the model.
     * The new configuration takes effect on the next [generateInsight] call.
     *
     * @param config New sampler configuration to apply
     * @return Result indicating success or failure
     */
    suspend fun updateSamplerConfig(config: LlmSamplerConfig): Result<Unit>

    /**
     * Returns true from when inference is scheduled (before model reload) until the
     * flow completes or is cancelled. Covers the full inference window including
     * model loading (LONG_MEETING lazy load) so callers can reliably detect an
     * in-flight inference even before token generation begins.
     *
     * Set via [beginInference] / reset via [endInference] by the scheduling use case.
     * [generateInsight]'s onCompletion also resets it as a safety net for normal flow.
     */
    val isGenerating: Boolean

    /**
     * Marks the start of a scheduled inference cycle, including any preceding model
     * reload. Must be called before [reloadModel] so that [isGenerating] covers the
     * full window (model load + token generation). Pair with [endInference].
     */
    fun beginInference()

    /**
     * Resets [isGenerating] to false. Called in the finally block of the scheduling
     * use case to handle the case where the inference coroutine is cancelled before
     * [generateInsight] is ever collected (so onCompletion never fires).
     */
    fun endInference()

    /**
     * Clean up LLM resources and release memory.
     */
    suspend fun cleanup()

    /**
     * Flow of device thermal state signals.
     *
     * Emits [ThermalThrottle.Normal] by default. When the device is thermally stressed,
     * emits [ThermalThrottle.Reduced] so callers can increase the inference interval
     * to reduce CPU/GPU load and protect the battery.
     *
     * Backed by [android.os.PowerManager.OnThermalStatusChangedListener] on API 29+.
     */
    val thermalThrottleFlow: Flow<ThermalThrottle>
}
