package com.hearopilot.app.di

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.hearopilot.app.ui.SimulateStreamingAsr
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.service.RecordingNotificationManager
import com.hearopilot.app.service.RecordingSessionManager
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideOfflineRecognizer(
        @ApplicationContext context: Context,
        modelDownloadManager: ModelDownloadManager
    ): OfflineRecognizer {
        // Get STT model path from downloaded files
        val sttModelPath = modelDownloadManager.getSttModelPath()
            ?: throw IllegalStateException("STT model not downloaded. Please download the model first.")

        // Model type 40 = sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8
        val config = getOfflineModelConfig(type = 40)!!

        // Override model paths to use downloaded files instead of assets
        config.transducer.encoder = "$sttModelPath/encoder.int8.onnx"
        config.transducer.decoder = "$sttModelPath/decoder.int8.onnx"
        config.transducer.joiner = "$sttModelPath/joiner.int8.onnx"
        config.tokens = "$sttModelPath/tokens.txt"

        // Fixed 2 threads for STT to avoid CPU contention with the LLM engine
        // (which also uses multiple threads). On an 8-core device, 4 STT + 4 LLM
        // saturates all cores causing inference spikes up to 9s during LLM generation.
        // With 2 threads, STT leaves headroom for the LLM and OS scheduler.
        config.numThreads = 2
        Log.i("AppModule", "STT: ${config.numThreads} threads (fixed)")

        // Create recognizer WITHOUT assetManager (loading from file system)
        return OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(modelConfig = config)
        )
    }

    @Provides
    fun provideVad(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): Vad {
        // Read VAD parameters from settings
        val settings = runBlocking { settingsRepository.getSettings().first() }

        // Initialize using existing SimulateStreamingAsr logic with custom parameters
        SimulateStreamingAsr.initVad(
            assetManager = context.assets,
            minSilenceDuration = settings.vadMinSilenceDuration,
            maxSpeechDuration = settings.vadMaxSpeechDuration,
            threshold = settings.vadThreshold
        )
        return SimulateStreamingAsr.vad
    }

    @Provides
    @Singleton
    fun provideInferenceEngine(
        @ApplicationContext context: Context
    ): InferenceEngine {
        return com.arm.aichat.AiChat.getInferenceEngine(context)
    }

    @Provides
    @Singleton
    fun provideRecordingSessionManager(): RecordingSessionManager {
        return RecordingSessionManager()
    }

    @Provides
    @Singleton
    fun provideRecordingNotificationManager(
        @ApplicationContext context: Context
    ): RecordingNotificationManager {
        return RecordingNotificationManager(context)
    }
}
