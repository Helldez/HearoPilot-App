package com.hearopilot.app.domain.model

/**
 * Identifies which LLM GGUF quantization variant to download and use.
 *
 * The variant is persisted in [AppSettings] so the user can switch at any time;
 * re-downloading the model is required after switching.
 */
enum class LlmModelVariant {
    /**
     * 8-bit quantization — higher accuracy, ~1 GB on disk.
     * Suited to flagship devices with ≥ 10 GB RAM and a modern CPU (Cortex-A78 / Oryon or newer).
     */
    Q8_0,

    /**
     * 4-bit non-linear quantization — ~35 % smaller (~650 MB), lower CPU load and battery use.
     * Recommended for mid-range and older devices (e.g. Snapdragon 865 / Cortex-A77 era).
     */
    IQ4_NL,

    /**
     * Qwen 3.5 0.8B Q8_0 — alternative model architecture, ~870 MB on disk.
     * Beta: experimental, available only via manual download in Settings.
     * Not recommended automatically by [com.hearopilot.app.data.device.DeviceTierDetector].
     */
    QWEN3_5_Q8_0
}
