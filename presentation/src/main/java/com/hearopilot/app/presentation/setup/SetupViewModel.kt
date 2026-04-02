package com.hearopilot.app.presentation.setup

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearopilot.app.data.config.modelConfigForVariant
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.data.device.DeviceTierDetector
import com.hearopilot.app.data.service.AndroidDownloadManager
import com.hearopilot.app.data.service.DownloadStateManager
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.LlmModelVariant
import com.hearopilot.app.domain.model.OnboardingStep
import com.hearopilot.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Setup/Onboarding flow.
 *
 * Manages multi-step onboarding with:
 * - Welcome screen
 * - STT model setup
 * - LLM model download with resume capability
 * - Progress tracking with speed and ETA
 * - Background download via Android DownloadManager (Play Store compliant)
 *
 * Architecture:
 * - Uses Android's DownloadManager for downloads
 * - AndroidDownloadManager monitors progress and updates DownloadStateManager
 * - ViewModel observes download state from DownloadStateManager
 * - UI updates automatically via StateFlow
 *
 * Benefits:
 * - Downloads survive app restarts and device reboots
 * - Battery optimized via Job Scheduler
 * - Automatic retry and pause/resume on network changes
 * - System-managed notifications
 *
 * @property modelDownloadManager Manager for checking model status
 * @property androidDownloadManager Android DownloadManager wrapper
 * @property downloadStateManager Shared state manager
 * @property context Application context for shared preferences
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager,
    private val androidDownloadManager: AndroidDownloadManager,
    private val downloadStateManager: DownloadStateManager,
    private val deviceTierDetector: DeviceTierDetector,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    // Observe download states from DownloadStateManager (updated by AndroidDownloadManager)
    val sttDownloadState: StateFlow<DownloadState> = downloadStateManager.sttDownloadState
    val llmDownloadState: StateFlow<DownloadState> = downloadStateManager.llmDownloadState

    private val _isSttReady = MutableStateFlow(false)
    val isSttReady: StateFlow<Boolean> = _isSttReady.asStateFlow()

    private val _isLlmReady = MutableStateFlow(false)
    val isLlmReady: StateFlow<Boolean> = _isLlmReady.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(false)
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    companion object {
        private const val TAG = "SetupViewModel"
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_STT_READY = "stt_ready"
    }

    init {
        checkOnboardingStatus()
        observeDownloadStates()
    }

    /**
     * Check if onboarding has been completed before.
     */
    private fun checkOnboardingStatus() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

        _isSttReady.value = modelDownloadManager.isSttModelDownloaded()
        // Check both variants so the flag is correct regardless of which one was downloaded.
        _isLlmReady.value = LlmModelVariant.entries.any { variant ->
            modelDownloadManager.isLlmModelDownloaded(modelConfigForVariant(variant).llmFilename)
        }

        if (onboardingComplete) {
            // Re-verify STT model files still exist on disk
            if (!_isSttReady.value) {
                Log.w(TAG, "Onboarding was completed but STT model files are missing, restarting STT download step")
                _isOnboardingComplete.value = false
                _currentStep.value = OnboardingStep.STT_DOWNLOAD
            } else {
                _isOnboardingComplete.value = true
                _currentStep.value = OnboardingStep.COMPLETED
                Log.i(TAG, "Onboarding already completed")
            }
        } else {
            Log.i(TAG, "Starting onboarding flow")
        }
    }

    /**
     * Observe download states and react to completion.
     */
    private fun observeDownloadStates() {
        // Observe STT download completion
        viewModelScope.launch {
            sttDownloadState.collect { state ->
                when (state) {
                    is DownloadState.Completed -> {
                        _isSttReady.value = true
                        Log.i(TAG, "STT download completed, proceeding to next step")
                        // Auto-proceed after short delay
                        kotlinx.coroutines.delay(1000)
                        proceedToNextStep()
                    }
                    else -> Unit
                }
            }
        }

        // Observe LLM download completion
        viewModelScope.launch {
            llmDownloadState.collect { state ->
                when (state) {
                    is DownloadState.Completed -> {
                        _isLlmReady.value = true
                        Log.i(TAG, "LLM download completed, completing onboarding")
                        // Persist the downloaded variant and its file path to DataStore so that
                        // MainViewModel can find the model on first launch after onboarding.
                        persistLlmModelSettings()
                        // Auto-complete onboarding after download
                        kotlinx.coroutines.delay(1000)
                        completeOnboarding()
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Navigate to the previous onboarding step.
     * Only valid when [canGoBack] returns true.
     */
    fun goToPreviousStep() {
        _currentStep.value = when (_currentStep.value) {
            OnboardingStep.LANGUAGES -> OnboardingStep.WELCOME
            OnboardingStep.STT_DOWNLOAD -> OnboardingStep.LANGUAGES
            else -> _currentStep.value
        }
    }

    /**
     * Whether the user can navigate backward from the current step.
     * Back is blocked once STT download has started to avoid leaving a partial download
     * with no way to resume it.
     */
    fun canGoBack(): Boolean = when (_currentStep.value) {
        OnboardingStep.LANGUAGES -> true
        OnboardingStep.STT_DOWNLOAD -> sttDownloadState.value is DownloadState.Idle
        else -> false
    }

    /**
     * Move to next onboarding step.
     */
    fun proceedToNextStep() {
        _currentStep.value = when (_currentStep.value) {
            OnboardingStep.WELCOME -> OnboardingStep.LANGUAGES
            OnboardingStep.LANGUAGES -> OnboardingStep.STT_DOWNLOAD
            OnboardingStep.STT_DOWNLOAD -> OnboardingStep.LLM_DOWNLOAD
            OnboardingStep.LLM_DOWNLOAD -> {
                completeOnboarding()
                OnboardingStep.COMPLETED
            }
            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED
        }
    }

    /**
     * Start STT model download via Android DownloadManager.
     * Downloads survive app restart and are battery optimized.
     */
    fun startSttDownload() {
        Log.i(TAG, "Starting STT download via Android DownloadManager")
        androidDownloadManager.startSttDownload()
    }

    /**
     * Retry failed STT download.
     */
    fun retrySttDownload() {
        Log.i(TAG, "Retrying STT download")
        androidDownloadManager.cancelSttDownload()
        startSttDownload()
    }

    /**
     * Start LLM model download for the device-recommended variant (onboarding).
     * Persists the recommended variant to settings before downloading so that
     * [persistLlmModelSettings] can read it back consistently on completion.
     */
    fun startLlmDownload() {
        val variant = deviceTierDetector.detectRecommendedVariant()
        Log.i(TAG, "Starting LLM download for recommended variant: $variant")
        viewModelScope.launch {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(llmModelVariant = variant))
        }
        androidDownloadManager.startLlmDownload(variant)
    }

    /**
     * Retry failed LLM download, resuming from partial file if available.
     */
    fun retryLlmDownload() {
        viewModelScope.launch {
            val variant = settingsRepository.getSettings().first().llmModelVariant
            Log.i(TAG, "Retrying LLM download for variant: $variant")
            androidDownloadManager.resumeLlmDownload(variant)
        }
    }

    /**
     * Skip LLM download (user can download later from settings).
     */
    fun skipLlmDownload() {
        Log.i(TAG, "Skipping LLM download")
        completeOnboarding()
    }

    /**
     * Persist the downloaded LLM variant and its file path to DataStore.
     *
     * Must be called after a successful download so that MainViewModel can resolve
     * the model path from settings on first launch, regardless of which variant
     * (Q8_0 or IQ4_NL) was downloaded during onboarding.
     */
    private suspend fun persistLlmModelSettings() {
        val current = settingsRepository.getSettings().first()
        val variant = current.llmModelVariant
        val config = modelConfigForVariant(variant)
        val path = modelDownloadManager.getLlmModelPath(config.llmFilename)
        if (path != null) {
            settingsRepository.updateSettings(current.copy(llmModelPath = path))
            Log.i(TAG, "Persisted LLM settings: variant=$variant, path=$path")
        } else {
            Log.w(TAG, "LLM path not found after download for variant=$variant")
        }
    }

    /**
     * Complete onboarding and save status.
     */
    private fun completeOnboarding() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()

        // Reset download states so the standalone download screen (post-onboarding)
        // always starts in Idle and never shows stale Completed/Error UI.
        downloadStateManager.resetAll()

        _isOnboardingComplete.value = true
        _currentStep.value = OnboardingStep.COMPLETED
        Log.i(TAG, "Onboarding completed")
    }
}
