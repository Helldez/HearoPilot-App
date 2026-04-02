package com.hearopilot.app.di

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import com.hearopilot.app.data.datasource.LlmDataSource
import com.hearopilot.app.data.config.DefaultModelConfig
import com.hearopilot.app.data.config.ModelConfig
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.data.datasource.SttDataSource
import com.hearopilot.app.data.monitor.AndroidThermalMonitor
import com.hearopilot.app.data.provider.AndroidResourceProvider
import com.hearopilot.app.data.repository.LlmRepositoryImpl
import com.hearopilot.app.data.repository.SettingsRepositoryImpl
import com.hearopilot.app.data.repository.SttRepositoryImpl
import com.hearopilot.app.domain.monitor.ThermalMonitor
import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.repository.SttRepository
import com.hearopilot.app.feature.llm.datasource.LlamaAndroidDataSource
import com.hearopilot.app.feature.stt.datasource.SherpaOnnxDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing data layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSttDataSource(
        @ApplicationContext context: Context,
        recognizerProvider: javax.inject.Provider<OfflineRecognizer>,
        vadProvider: javax.inject.Provider<Vad>
    ): SttDataSource {
        val audioManager = context.getSystemService(AudioManager::class.java)
        return SherpaOnnxDataSource(context, recognizerProvider, vadProvider, audioManager)
    }

    @Provides
    @Singleton
    fun provideLlmDataSource(
        inferenceEngine: InferenceEngine
    ): LlmDataSource {
        return LlamaAndroidDataSource(inferenceEngine)
    }

    @Provides
    @Singleton
    fun provideSttRepository(
        dataSource: SttDataSource
    ): SttRepository {
        return SttRepositoryImpl(dataSource)
    }

    @Provides
    @Singleton
    fun provideLlmRepository(
        dataSource: LlmDataSource,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): LlmRepository {
        return LlmRepositoryImpl(dataSource, settingsRepository, context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        resourceProvider: ResourceProvider
    ): SettingsRepository {
        return SettingsRepositoryImpl(context, resourceProvider)
    }

    @Provides
    @Singleton
    fun provideModelConfig(): ModelConfig = DefaultModelConfig.INSTANCE

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        modelConfig: ModelConfig
    ): ModelDownloadManager {
        return ModelDownloadManager(context, modelConfig)
    }

    @Provides
    @Singleton
    fun provideResourceProvider(
        @ApplicationContext context: Context
    ): ResourceProvider {
        return AndroidResourceProvider(context)
    }

    @Provides
    @Singleton
    fun provideThermalMonitor(
        @ApplicationContext context: Context
    ): ThermalMonitor {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return AndroidThermalMonitor(powerManager)
    }
}
