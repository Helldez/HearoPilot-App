package com.hearopilot.app.domain.model

/**
 * LLM sampler configuration.
 *
 * All defaults match the llama.cpp defaults used in ai_chat.cpp, except [temperature]
 * which is intentionally low (0.3) for deterministic, structured JSON output.
 *
 * @property temperature Randomness of token selection. Lower = more deterministic (0.0–2.0).
 * @property topK Limits sampling to the top-K most probable tokens (1–100). 0 = disabled.
 * @property topP Nucleus sampling: considers tokens until cumulative probability >= topP (0.0–1.0).
 * @property minP Minimum probability threshold relative to the most probable token (0.0–1.0).
 * @property repeatPenalty Penalty applied to recently generated tokens to reduce repetition (0.0–2.0).
 *   1.0 = disabled (no penalty).
 */
data class LlmSamplerConfig(
    val temperature: Float = 0.3f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.0f
)
