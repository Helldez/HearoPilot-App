package com.hearopilot.app.di

import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.repository.SttRepository
import com.hearopilot.app.domain.service.RecordingServiceController
import com.hearopilot.app.domain.usecase.llm.InitializeLlmUseCase
import com.hearopilot.app.domain.usecase.stt.StartSttStreamingUseCase
import com.hearopilot.app.domain.usecase.stt.StopSttStreamingUseCase
import com.hearopilot.app.domain.usecase.sync.SyncSttLlmUseCase
import com.hearopilot.app.service.RecordingServiceControllerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing domain layer use cases and services.
 * Changed to SingletonComponent to support service injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideSyncSttLlmUseCase(
        llmRepository: LlmRepository,
        settingsRepository: SettingsRepository,
        resourceProvider: ResourceProvider
    ): SyncSttLlmUseCase {
        return SyncSttLlmUseCase(llmRepository, settingsRepository, resourceProvider)
    }

    @Provides
    fun provideStartSttStreamingUseCase(
        sttRepository: SttRepository
    ): StartSttStreamingUseCase {
        return StartSttStreamingUseCase(sttRepository)
    }

    @Provides
    fun provideStopSttStreamingUseCase(
        sttRepository: SttRepository
    ): StopSttStreamingUseCase {
        return StopSttStreamingUseCase(sttRepository)
    }

    @Provides
    fun provideInitializeLlmUseCase(
        llmRepository: LlmRepository,
        settingsRepository: SettingsRepository
    ): InitializeLlmUseCase {
        return InitializeLlmUseCase(llmRepository, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideRecordingServiceController(
        impl: RecordingServiceControllerImpl
    ): RecordingServiceController {
        return impl
    }
}
