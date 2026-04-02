package com.hearopilot.app.data.config

import com.hearopilot.app.domain.model.LlmModelVariant

/**
 * Encapsulates all model download configuration: URLs, filenames, and related file lists.
 *
 * Separating configuration from [com.hearopilot.app.data.datasource.ModelDownloadManager]
 * allows model references to be changed (e.g. for different build variants or A/B tests)
 * without touching download infrastructure.
 */
data class ModelConfig(
    val llmUrl: String,
    val llmFilename: String,
    val sttBaseUrl: String,
    val sttFiles: List<String>
)

// STT config is shared across all LLM variants.
private const val STT_BASE_URL =
    "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"
private val STT_FILES = listOf(
    "encoder.int8.onnx",
    "decoder.int8.onnx",
    "joiner.int8.onnx",
    "tokens.txt"
)

/**
 * Q8_0 model configuration — higher accuracy, ~1 GB.
 * Recommended for flagship devices (≥ 10 GB RAM, Cortex-A78 / Oryon CPU or newer).
 *
 * LLM  : Gemma 3 1B Q8_0 (ggml-org, HuggingFace)
 * STT  : Sherpa-ONNX Nemo Parakeet TDT 0.6B Int8 (csukuangfj, HuggingFace)
 */
object DefaultModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q8_0.gguf?download=true"
    private const val LLM_FILENAME = "gemma-3-1b-it-Q8_0.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES
    )
}

/**
 * IQ4_NL model configuration — ~35 % smaller (~650 MB), lower CPU load and battery use.
 * Recommended for mid-range and older devices (Cortex-A77 / Snapdragon 865 era and below).
 *
 * LLM  : Gemma 3 1B IQ4_NL (unsloth, HuggingFace)
 * STT  : same as [DefaultModelConfig]
 */
object LowEndModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-IQ4_NL.gguf?download=true"
    private const val LLM_FILENAME = "gemma-3-1b-it-IQ4_NL.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES
    )
}

/**
 * Qwen 3.5 0.8B Q8_0 configuration — alternative model, ~870 MB.
 * Beta: available only via manual download in Settings; not auto-recommended by DeviceTierDetector.
 *
 * LLM  : Qwen 3.5 0.8B Q8_0 (unsloth, HuggingFace)
 * STT  : same as [DefaultModelConfig]
 */
object Qwen35ModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf?download=true"
    private const val LLM_FILENAME = "Qwen3.5-0.8B-Q8_0.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES
    )
}

/** Returns the [ModelConfig] that corresponds to [variant]. */
fun modelConfigForVariant(variant: LlmModelVariant): ModelConfig = when (variant) {
    LlmModelVariant.Q8_0         -> DefaultModelConfig.INSTANCE
    LlmModelVariant.IQ4_NL       -> LowEndModelConfig.INSTANCE
    LlmModelVariant.QWEN3_5_Q8_0 -> Qwen35ModelConfig.INSTANCE
}
