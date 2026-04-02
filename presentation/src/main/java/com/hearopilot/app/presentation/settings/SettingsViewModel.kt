package com.hearopilot.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearopilot.app.data.config.modelConfigForVariant
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.data.device.DeviceTierDetector
import com.hearopilot.app.data.service.AndroidDownloadManager
import com.hearopilot.app.data.service.DownloadStateManager
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmModelVariant
import com.hearopilot.app.domain.model.LlmSamplerConfig
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.ThemeMode
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.usecase.llm.UpdateSystemPromptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages app settings including LLM inference interval configuration.
 *
 * @property settingsRepository Repository for persisting settings
 * @property updateSystemPromptUseCase Use case for updating system prompt immediately
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateSystemPromptUseCase: UpdateSystemPromptUseCase,
    private val modelDownloadManager: ModelDownloadManager,
    private val androidDownloadManager: AndroidDownloadManager,
    private val downloadStateManager: DownloadStateManager,
    private val deviceTierDetector: DeviceTierDetector
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isLlmDownloaded = MutableStateFlow(false)
    val isLlmDownloaded: StateFlow<Boolean> = _isLlmDownloaded.asStateFlow()

    val llmDownloadState: StateFlow<DownloadState> = downloadStateManager.llmDownloadState

    // Tracks which variant is currently being downloaded so that llmModelPath is updated
    // to the correct file when the download completes, regardless of the selected variant.
    // Exposed to the UI so that only the downloading variant shows a spinner.
    private val _activeDownloadVariant = MutableStateFlow<LlmModelVariant?>(null)
    val activeDownloadVariant: StateFlow<LlmModelVariant?> = _activeDownloadVariant.asStateFlow()

    /**
     * The model variant recommended for this specific device, computed once on first access.
     * Reads /proc/cpuinfo and ActivityManager — fast synchronous call (~1 ms).
     */
    val recommendedVariant: LlmModelVariant by lazy { deviceTierDetector.detectRecommendedVariant() }

    companion object {
        // Minimum interval for translation mode (fast, real-time feel)
        const val MIN_INTERVAL_SECONDS = 10
        // Minimum interval for analysis modes (Simple Listening + Short Meeting)
        // A lower value would produce too-frequent, low-quality insights on short audio.
        const val MIN_INTERVAL_SECONDS_ANALYSIS = 30
        const val MAX_INTERVAL_SECONDS = 180 // 3 minutes
        const val DEFAULT_INTERVAL_SECONDS = 30

        // VAD parameter boundaries
        const val MIN_SILENCE_DURATION_MIN = 0.1F
        const val MIN_SILENCE_DURATION_MAX = 2.0F
        const val MIN_SILENCE_DURATION_DEFAULT = 0.5F

        const val MAX_SPEECH_DURATION_MIN = 3.0F
        const val MAX_SPEECH_DURATION_MAX = 30.0F
        const val MAX_SPEECH_DURATION_DEFAULT = 10.0F

        const val VAD_THRESHOLD_MIN = 0.2F
        const val VAD_THRESHOLD_MAX = 0.9F
        const val VAD_THRESHOLD_DEFAULT = 0.5F

        // LLM sampler parameter boundaries
        const val LLM_TEMPERATURE_MIN = 0.0F
        const val LLM_TEMPERATURE_MAX = 2.0F

        // top_k 0 = disabled; upper limit of 100 covers all practical use cases
        const val LLM_TOP_K_MIN = 0
        const val LLM_TOP_K_MAX = 100

        const val LLM_TOP_P_MIN = 0.0F
        const val LLM_TOP_P_MAX = 1.0F

        const val LLM_MIN_P_MIN = 0.0F
        const val LLM_MIN_P_MAX = 1.0F

        // 1.0 = no penalty; values > 1.0 discourage repetition
        const val LLM_REPEAT_PENALTY_MIN = 1.0F
        const val LLM_REPEAT_PENALTY_MAX = 2.0F
    }

    init {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { loadedSettings ->
                _settings.value = loadedSettings
                // Re-check download status whenever settings change (variant may have changed).
                val filename = modelConfigForVariant(loadedSettings.llmModelVariant).llmFilename
                _isLlmDownloaded.value = modelDownloadManager.isLlmModelDownloaded(filename)
            }
        }
        // Also re-check download status whenever the download state changes.
        viewModelScope.launch {
            downloadStateManager.llmDownloadState.collect { state ->
                val selectedFilename = modelConfigForVariant(_settings.value.llmModelVariant).llmFilename
                _isLlmDownloaded.value = modelDownloadManager.isLlmModelDownloaded(selectedFilename)
                // When a download completes, update llmModelPath to the downloaded variant's file.
                // Use activeDownloadVariant so non-selected variants also get their path stored.
                if (state is DownloadState.Completed) {
                    val downloadedVariant = _activeDownloadVariant.value ?: _settings.value.llmModelVariant
                    val filename = modelConfigForVariant(downloadedVariant).llmFilename
                    val path = modelDownloadManager.getLlmModelPath(filename)
                    if (path != null) {
                        val updated = _settings.value.copy(llmModelPath = path)
                        settingsRepository.updateSettings(updated)
                    }
                    _activeDownloadVariant.value = null
                }
            }
        }
    }

    /**
     * Start LLM model download for the currently selected variant.
     * Safe to call even if a partial file already exists (download resumes automatically).
     */
    fun startLlmDownload() {
        val variant = _settings.value.llmModelVariant
        _activeDownloadVariant.value = variant
        androidDownloadManager.startLlmDownload(variant)
    }

    /** Resume an interrupted LLM download for the currently selected variant. */
    fun retryLlmDownload() {
        val variant = _settings.value.llmModelVariant
        _activeDownloadVariant.value = variant
        androidDownloadManager.resumeLlmDownload(variant)
    }

    /**
     * Start LLM model download for a specific [variant], regardless of the current selection.
     * Allows the user to pre-download a non-selected variant so both are available on disk.
     * [llmModelPath] is updated to the downloaded file when the download completes.
     */
    fun downloadVariant(variant: LlmModelVariant) {
        _activeDownloadVariant.value = variant
        androidDownloadManager.startLlmDownload(variant)
    }

    /**
     * Update the LLM model variant preference and immediately switch [AppSettings.llmModelPath]
     * to the new variant's file if it is already downloaded.
     *
     * @param variant The chosen [LlmModelVariant]
     */
    fun updateLlmModelVariant(variant: LlmModelVariant) {
        viewModelScope.launch {
            val filename = modelConfigForVariant(variant).llmFilename
            val path = modelDownloadManager.getLlmModelPath(filename)
            val updated = _settings.value.copy(
                llmModelVariant = variant,
                llmModelPath = path ?: _settings.value.llmModelPath
            )
            settingsRepository.updateSettings(updated)
        }
    }

    /**
     * Returns true if the model file for [variant] is present on disk.
     * Used by the Settings UI to display a "Downloaded" badge on each variant option.
     */
    fun isVariantDownloaded(variant: LlmModelVariant): Boolean {
        val filename = modelConfigForVariant(variant).llmFilename
        return modelDownloadManager.isLlmModelDownloaded(filename)
    }

    /**
     * Update the LLM inference interval.
     *
     * @param intervalSeconds New interval in seconds (clamped to valid range)
     */
    fun updateLlmInterval(intervalSeconds: Int) {
        val clampedInterval = intervalSeconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)

        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                shortMeetingIntervalSeconds = clampedInterval
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Reset LLM interval to default value.
     */
    fun resetLlmInterval() {
        updateLlmInterval(DEFAULT_INTERVAL_SECONDS)
    }

    /**
     * Update the app theme mode.
     *
     * @param themeMode New theme mode (SYSTEM, LIGHT, or DARK)
     */
    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                themeMode = themeMode
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update the long recording insight interval.
     *
     * @param minutes New interval in minutes (must be 5, 10, or 15)
     */
    fun updateLongRecordingInterval(minutes: Int) {
        if (minutes !in listOf(3, 5, 10)) return // Valid long-meeting intervals in minutes

        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                longMeetingIntervalMinutes = minutes
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update the LLM system prompt for a specific mode.
     */
    fun updateModeSystemPrompt(mode: com.hearopilot.app.domain.model.RecordingMode, prompt: String) {
        viewModelScope.launch {
            val currentSettings = _settings.value
            val updatedSettings = when (mode) {
                com.hearopilot.app.domain.model.RecordingMode.SIMPLE_LISTENING -> currentSettings.copy(simpleListeningSystemPrompt = prompt)
                com.hearopilot.app.domain.model.RecordingMode.SHORT_MEETING -> currentSettings.copy(shortMeetingSystemPrompt = prompt)
                com.hearopilot.app.domain.model.RecordingMode.LONG_MEETING -> currentSettings.copy(longMeetingSystemPrompt = prompt)
                com.hearopilot.app.domain.model.RecordingMode.REAL_TIME_TRANSLATION -> currentSettings.copy(translationSystemPrompt = prompt)
            }
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update the interval for a specific mode.
     */
    fun updateModeInterval(mode: com.hearopilot.app.domain.model.RecordingMode, interval: Int) {
        viewModelScope.launch {
            val currentSettings = _settings.value
            val updatedSettings = when (mode) {
                com.hearopilot.app.domain.model.RecordingMode.SIMPLE_LISTENING -> currentSettings.copy(simpleListeningIntervalSeconds = interval)
                com.hearopilot.app.domain.model.RecordingMode.SHORT_MEETING -> currentSettings.copy(shortMeetingIntervalSeconds = interval)
                com.hearopilot.app.domain.model.RecordingMode.LONG_MEETING -> currentSettings.copy(longMeetingIntervalMinutes = interval) // Minutes for long meeting
                com.hearopilot.app.domain.model.RecordingMode.REAL_TIME_TRANSLATION -> currentSettings.copy(translationIntervalSeconds = interval)
            }
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update translation target language.
     */
    fun updateTranslationTargetLanguage(language: String) {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(translationTargetLanguage = language)
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Reset the system prompt for a specific mode back to the localized factory default.
     * This removes any user customization for that mode.
     */
    fun resetModeSystemPrompt(mode: com.hearopilot.app.domain.model.RecordingMode) {
        viewModelScope.launch {
            val defaultPrompt = settingsRepository.getDefaultPromptForMode(mode)
            val updatedSettings = when (mode) {
                com.hearopilot.app.domain.model.RecordingMode.SIMPLE_LISTENING ->
                    _settings.value.copy(simpleListeningSystemPrompt = defaultPrompt)
                com.hearopilot.app.domain.model.RecordingMode.SHORT_MEETING ->
                    _settings.value.copy(shortMeetingSystemPrompt = defaultPrompt)
                com.hearopilot.app.domain.model.RecordingMode.LONG_MEETING ->
                    _settings.value.copy(longMeetingSystemPrompt = defaultPrompt)
                com.hearopilot.app.domain.model.RecordingMode.REAL_TIME_TRANSLATION ->
                    _settings.value.copy(translationSystemPrompt = defaultPrompt)
            }
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update VAD minimum silence duration.
     *
     * @param duration Minimum silence duration in seconds (clamped to valid range)
     */
    fun updateVadMinSilenceDuration(duration: Float) {
        val clampedDuration = duration.coerceIn(MIN_SILENCE_DURATION_MIN, MIN_SILENCE_DURATION_MAX)

        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                vadMinSilenceDuration = clampedDuration
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update VAD maximum speech duration.
     *
     * @param duration Maximum speech duration in seconds (clamped to valid range)
     */
    fun updateVadMaxSpeechDuration(duration: Float) {
        val clampedDuration = duration.coerceIn(MAX_SPEECH_DURATION_MIN, MAX_SPEECH_DURATION_MAX)

        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                vadMaxSpeechDuration = clampedDuration
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update VAD detection threshold.
     *
     * @param threshold Detection threshold (clamped to valid range)
     */
    fun updateVadThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(VAD_THRESHOLD_MIN, VAD_THRESHOLD_MAX)

        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                vadThreshold = clampedThreshold
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Enable or disable the LLM model for recording sessions.
     *
     * When disabled, the LLM is never loaded during recording and no AI insights
     * are generated. This saves memory and battery on low-end devices.
     *
     * @param enabled Whether the LLM should be used during recording sessions
     */
    fun updateLlmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(llmEnabled = enabled)
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    /**
     * Update the default insight strategy for a specific recording mode.
     *
     * @param mode Recording mode to update the default strategy for
     * @param strategy New default strategy (REAL_TIME or END_OF_SESSION)
     */
    fun updateModeDefaultStrategy(mode: RecordingMode, strategy: InsightStrategy) {
        viewModelScope.launch {
            val updatedSettings = when (mode) {
                RecordingMode.SIMPLE_LISTENING -> _settings.value.copy(simpleListeningDefaultStrategy = strategy)
                RecordingMode.SHORT_MEETING -> _settings.value.copy(shortMeetingDefaultStrategy = strategy)
                RecordingMode.LONG_MEETING -> _settings.value.copy(longMeetingDefaultStrategy = strategy)
                RecordingMode.REAL_TIME_TRANSLATION -> _settings.value.copy(translationDefaultStrategy = strategy)
            }
            settingsRepository.updateSettings(updatedSettings)
        }
    }

    fun updateLlmTemperature(value: Float) {
        val clamped = value.coerceIn(LLM_TEMPERATURE_MIN, LLM_TEMPERATURE_MAX)
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _settings.value.copy(llmSamplerConfig = _settings.value.llmSamplerConfig.copy(temperature = clamped))
            )
        }
    }

    fun updateLlmTopK(value: Int) {
        val clamped = value.coerceIn(LLM_TOP_K_MIN, LLM_TOP_K_MAX)
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _settings.value.copy(llmSamplerConfig = _settings.value.llmSamplerConfig.copy(topK = clamped))
            )
        }
    }

    fun updateLlmTopP(value: Float) {
        val clamped = value.coerceIn(LLM_TOP_P_MIN, LLM_TOP_P_MAX)
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _settings.value.copy(llmSamplerConfig = _settings.value.llmSamplerConfig.copy(topP = clamped))
            )
        }
    }

    fun updateLlmMinP(value: Float) {
        val clamped = value.coerceIn(LLM_MIN_P_MIN, LLM_MIN_P_MAX)
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _settings.value.copy(llmSamplerConfig = _settings.value.llmSamplerConfig.copy(minP = clamped))
            )
        }
    }

    fun updateLlmRepeatPenalty(value: Float) {
        val clamped = value.coerceIn(LLM_REPEAT_PENALTY_MIN, LLM_REPEAT_PENALTY_MAX)
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _settings.value.copy(llmSamplerConfig = _settings.value.llmSamplerConfig.copy(repeatPenalty = clamped))
            )
        }
    }

    fun resetLlmSamplerConfig() {
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _settings.value.copy(llmSamplerConfig = LlmSamplerConfig())
            )
        }
    }

    /**
     * Reset all VAD parameters to default values.
     */
    fun resetVadParameters() {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                vadMinSilenceDuration = MIN_SILENCE_DURATION_DEFAULT,
                vadMaxSpeechDuration = MAX_SPEECH_DURATION_DEFAULT,
                vadThreshold = VAD_THRESHOLD_DEFAULT
            )
            settingsRepository.updateSettings(updatedSettings)
        }
    }
}
