package com.hearopilot.app.presentation.di

import com.hearopilot.app.domain.monitor.ThermalMonitor
import com.hearopilot.app.domain.provider.ResourceProvider
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.repository.TranscriptionRepository
import com.hearopilot.app.domain.usecase.llm.GenerateBatchInsightUseCase
import com.hearopilot.app.domain.usecase.llm.GenerateFinalInsightUseCase
import com.hearopilot.app.domain.usecase.llm.GenerateHistoryInsightUseCase
import com.hearopilot.app.domain.usecase.llm.UpdateSystemPromptUseCase
import com.hearopilot.app.domain.usecase.transcription.CreateSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.SearchTranscriptionsUseCase
import com.hearopilot.app.domain.usecase.transcription.DeleteSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.RenameSessionUseCase
import com.hearopilot.app.domain.usecase.transcription.GetAllSessionsUseCase
import com.hearopilot.app.domain.usecase.transcription.GetSessionDetailsUseCase
import com.hearopilot.app.domain.usecase.transcription.SaveInsightUseCase
import com.hearopilot.app.domain.usecase.transcription.SaveSegmentUseCase
import com.hearopilot.app.domain.usecase.transcription.UpdateInsightContentUseCase
import com.hearopilot.app.domain.usecase.transcription.GetTotalDataSizeUseCase
import com.hearopilot.app.domain.usecase.transcription.UpdateSessionDurationUseCase
import com.hearopilot.app.domain.usecase.transcription.UpdateSegmentTextUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Hilt module providing use case dependencies.
 *
 * Use cases are scoped to ViewModel lifecycle to ensure they're recreated
 * with each ViewModel instance.
 */
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideCreateSessionUseCase(
        repository: TranscriptionRepository
    ): CreateSessionUseCase {
        return CreateSessionUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideGetAllSessionsUseCase(
        repository: TranscriptionRepository
    ): GetAllSessionsUseCase {
        return GetAllSessionsUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideGetSessionDetailsUseCase(
        repository: TranscriptionRepository
    ): GetSessionDetailsUseCase {
        return GetSessionDetailsUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideSaveSegmentUseCase(
        repository: TranscriptionRepository
    ): SaveSegmentUseCase {
        return SaveSegmentUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideSaveInsightUseCase(
        repository: TranscriptionRepository
    ): SaveInsightUseCase {
        return SaveInsightUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideDeleteSessionUseCase(
        repository: TranscriptionRepository
    ): DeleteSessionUseCase {
        return DeleteSessionUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideRenameSessionUseCase(
        repository: TranscriptionRepository
    ): RenameSessionUseCase {
        return RenameSessionUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideUpdateSegmentTextUseCase(
        repository: TranscriptionRepository
    ): UpdateSegmentTextUseCase {
        return UpdateSegmentTextUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideUpdateInsightContentUseCase(
        repository: TranscriptionRepository
    ): UpdateInsightContentUseCase {
        return UpdateInsightContentUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideUpdateSystemPromptUseCase(
        settingsRepository: SettingsRepository,
        llmRepository: LlmRepository
    ): UpdateSystemPromptUseCase {
        return UpdateSystemPromptUseCase(settingsRepository, llmRepository)
    }

    @Provides
    @ViewModelScoped
    fun provideSearchTranscriptionsUseCase(
        repository: TranscriptionRepository
    ): SearchTranscriptionsUseCase {
        return SearchTranscriptionsUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideUpdateSessionDurationUseCase(
        repository: TranscriptionRepository
    ): UpdateSessionDurationUseCase {
        return UpdateSessionDurationUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideGetTotalDataSizeUseCase(
        repository: TranscriptionRepository
    ): GetTotalDataSizeUseCase {
        return GetTotalDataSizeUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideGenerateFinalInsightUseCase(
        llmRepository: LlmRepository,
        settingsRepository: SettingsRepository,
        resourceProvider: ResourceProvider
    ): GenerateFinalInsightUseCase {
        return GenerateFinalInsightUseCase(llmRepository, settingsRepository, resourceProvider)
    }

    @Provides
    @ViewModelScoped
    fun provideGenerateBatchInsightUseCase(
        llmRepository: LlmRepository,
        settingsRepository: SettingsRepository,
        resourceProvider: ResourceProvider,
        thermalMonitor: ThermalMonitor
    ): GenerateBatchInsightUseCase {
        return GenerateBatchInsightUseCase(llmRepository, settingsRepository, resourceProvider, thermalMonitor)
    }

    @Provides
    @ViewModelScoped
    fun provideGenerateHistoryInsightUseCase(
        transcriptionRepository: TranscriptionRepository,
        generateBatchInsightUseCase: GenerateBatchInsightUseCase
    ): GenerateHistoryInsightUseCase {
        return GenerateHistoryInsightUseCase(transcriptionRepository, generateBatchInsightUseCase)
    }
}
