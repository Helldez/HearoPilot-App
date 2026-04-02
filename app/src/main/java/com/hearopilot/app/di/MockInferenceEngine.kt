package com.hearopilot.app.di

import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Mock implementation of InferenceEngine for testing without native libraries.
 *
 * Simulates LLM responses with realistic delays and token streaming.
 */
class MockInferenceEngine : InferenceEngine {

    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
    override val state: StateFlow<InferenceEngine.State> = _state

    private val mockResponses = listOf(
        "Based on the transcription, I notice you're discussing %s. Here's a brief summary: This is an important topic that requires careful consideration.",
        "The conversation about %s suggests several key points: 1) Clear communication is essential, 2) Consider all perspectives, 3) Plan next steps carefully.",
        "Analyzing your discussion on %s: The main themes involve decision-making and collaboration. Consider documenting these insights for future reference.",
        "Key takeaway from %s: Focus on actionable items and maintain clear objectives throughout the process.",
        "Summary of %s: The discussion highlights important considerations. Recommend following up with specific action items."
    )

    private var responseIndex = 0

    override suspend fun loadModel(pathToModel: String, nThreadsHint: Int) {
        _state.value = InferenceEngine.State.LoadingModel
        delay(500) // Simulate loading time
        _state.value = InferenceEngine.State.ModelReady
    }

    override suspend fun setSamplerConfig(
        temperature: Float, topK: Int, topP: Float, minP: Float, repeatPenalty: Float
    ) { /* no-op in mock */ }

    override suspend fun setSystemPrompt(systemPrompt: String) {
        _state.value = InferenceEngine.State.ProcessingSystemPrompt
        delay(100)
        _state.value = InferenceEngine.State.ModelReady
    }

    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
        _state.value = InferenceEngine.State.Generating

        // Extract topic from message (simplified)
        val topic = message.substringAfter("Transcription: \"")
            .substringBefore("\"")
            .take(30)

        // Get mock response
        val response = mockResponses[responseIndex % mockResponses.size]
            .format(if (topic.isNotBlank()) "\"$topic\"" else "the topic")

        responseIndex++

        // Simulate token-by-token generation
        val words = response.split(" ")
        for (word in words) {
            emit("$word ")
            delay(50) // Simulate realistic generation speed
        }

        _state.value = InferenceEngine.State.ModelReady
    }

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String {
        _state.value = InferenceEngine.State.Benchmarking
        delay(1000)
        _state.value = InferenceEngine.State.ModelReady
        return "Mock benchmark: pp=$pp, tg=$tg, pl=$pl, nr=$nr"
    }

    override fun cleanUp() {
        _state.value = InferenceEngine.State.UnloadingModel
        _state.value = InferenceEngine.State.Uninitialized
    }

    override fun destroy() {
        cleanUp()
    }
}
