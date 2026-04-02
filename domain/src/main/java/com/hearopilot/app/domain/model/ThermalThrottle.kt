package com.hearopilot.app.domain.model

/**
 * Represents the device thermal state as it affects LLM inference scheduling.
 *
 * Exposed as a Flow by [com.hearopilot.app.domain.repository.LlmRepository]
 * and consumed by [com.hearopilot.app.domain.usecase.sync.SyncSttLlmUseCase]
 * to multiply the inference interval when the device is running hot.
 *
 * KMP-ready: no Android dependencies.
 */
sealed class ThermalThrottle {
    /** Device is cool — run LLM at the configured interval. */
    data object Normal : ThermalThrottle()

    /**
     * Device is warm — multiply interval by [factor] to reduce LLM load.
     * @param factor Interval multiplier (e.g. 2.0f = run at half the frequency).
     */
    data class Reduced(val factor: Float) : ThermalThrottle()
}
