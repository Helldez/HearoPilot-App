package com.hearopilot.app.domain.model

/**
 * Application configuration settings.
 *
 * @property llmInferenceIntervalSeconds Interval in seconds between LLM inference calls (for SHORT recordings)
 * @property longRecordingInsightIntervalMinutes Interval in minutes between LLM inference calls (for LONG recordings)
 * @property sttModelPath Absolute path to the STT (Sherpa-ONNX) model directory
 * @property llmModelPath Absolute path to the LLM (GGUF) model file
 * @property autoStartLlm Whether to automatically initialize LLM on app start
 * @property themeMode App theme mode (SYSTEM/LIGHT/DARK)
 * @property vadMinSilenceDuration Minimum silence duration (seconds) to consider speech ended (0.25-2.0, default 0.5)
 * @property vadMaxSpeechDuration Maximum continuous speech duration (seconds) before forced segmentation (5.0-30.0, default 10.0)
 * @property vadThreshold VAD detection threshold (0.0-1.0, default 0.5). Lower = more sensitive
 */
data class AppSettings(
    // Mode-specific intervals
    val simpleListeningIntervalSeconds: Int = 60,
    val shortMeetingIntervalSeconds: Int = 60, // 60 s default gives the model enough context per inference
    val longMeetingIntervalMinutes: Int = 5, // Default: 5 min (options: 3, 5, 10)
    val translationIntervalSeconds: Int = 30, // 30 s default balances real-time feel with accuracy

    // Mode-specific System Prompts (loaded from localized resources via SettingsRepository)
    val simpleListeningSystemPrompt: String = "",
    val shortMeetingSystemPrompt: String = "",
    val longMeetingSystemPrompt: String = "",
    val translationSystemPrompt: String = "",

    // Translation settings — stores BCP-47 locale code (see SupportedLanguages)
    val translationTargetLanguage: String = SupportedLanguages.DEFAULT.code,

    // General settings
    val sttModelPath: String = "assets", // STT models are bundled in assets
    val llmModelPath: String = "",
    val llmEnabled: Boolean = true, // When false, LLM is never loaded during recording sessions
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // LLM model variant — determines which GGUF file is downloaded and used.
    // Default Q8_0 preserves backward compatibility for existing installs;
    // DeviceTierDetector surfaces the recommended variant in Settings UI.
    val llmModelVariant: LlmModelVariant = LlmModelVariant.Q8_0,

    // VAD parameters for better accuracy vs speed trade-off
    val vadMinSilenceDuration: Float = 0.5F,
    val vadMaxSpeechDuration: Float = 10.0F,
    val vadThreshold: Float = 0.5F,

    // JSON output field names — localized per device locale so the LLM prompt
    // schema uses the target language, reducing English bias in model output.
    val jsonFieldTitle: String = "title",
    val jsonFieldSummary: String = "summary",
    val jsonFieldActionItems: String = "action_items",

    // Per-mode default insight strategy — used to pre-populate NewSessionDialog.
    val simpleListeningDefaultStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val shortMeetingDefaultStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val longMeetingDefaultStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val translationDefaultStrategy: InsightStrategy = InsightStrategy.REAL_TIME,

    // LLM sampler configuration — controls token selection during generation.
    val llmSamplerConfig: LlmSamplerConfig = LlmSamplerConfig(),

    // One-time UI hints — set to true after the user has seen the coachmark.
    // Never reset by the user; persisted in DataStore.
    val hasShownHistoryInsightCoachmark: Boolean = false,

    // Adaptive conservative-threads state — persisted across sessions.
    //
    // memoryConstrainedDetected: cached result of the post-load isMemoryConstrained() check.
    // Sticky-true: once true, stays true until a future post-load check returns false.
    // Default false so fresh installs begin with the auto thread count.
    val memoryConstrainedDetected: Boolean = false,

    // conservativeThreadsCharThreshold: smallest input size (chars) at which a RAM-constrained
    // inference was observed, multiplied by a safety margin (see LlmRepositoryImpl).
    // Future inferences with chars >= threshold proactively use conservative threads.
    // Default Int.MAX_VALUE means "not yet calibrated" — no proactive throttling until learned.
    val conservativeThreadsCharThreshold: Int = Int.MAX_VALUE
)
