package com.hearopilot.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hearopilot.app.domain.service.LlmProcessingServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [LlmProcessingServiceController].
 *
 * Starts and stops [LlmProcessingService] via explicit intents so that long-running
 * LLM inference (batch or history insight pipelines) continues when the app is
 * backgrounded.
 */
@Singleton
class LlmProcessingServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmProcessingServiceController {

    companion object {
        private const val TAG = "LlmProcessingController"
    }

    override fun startProcessing() {
        Log.d(TAG, "Starting LLM processing service")
        val intent = Intent(context, LlmProcessingService::class.java).apply {
            action = LlmProcessingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    override fun stopProcessing() {
        Log.d(TAG, "Stopping LLM processing service")
        val intent = Intent(context, LlmProcessingService::class.java).apply {
            action = LlmProcessingService.ACTION_STOP
        }
        // startService is sufficient here — the service handles ACTION_STOP itself.
        context.startService(intent)
    }
}
