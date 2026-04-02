package com.hearopilot.app.domain.service

/**
 * Platform-agnostic interface for controlling the LLM processing foreground service.
 *
 * Implementations start a foreground service with a DATA_SYNC notification so that
 * long-running LLM inference can continue when the app is sent to the background.
 */
interface LlmProcessingServiceController {

    /**
     * Start the LLM processing foreground service.
     * Call this before launching any LLM inference that should survive backgrounding.
     */
    fun startProcessing()

    /**
     * Stop the LLM processing foreground service.
     * Call this when inference completes or is cancelled.
     */
    fun stopProcessing()
}
