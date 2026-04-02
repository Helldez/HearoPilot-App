package com.hearopilot.app.di

import com.hearopilot.app.domain.service.LlmProcessingServiceController
import com.hearopilot.app.service.LlmProcessingServiceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding service-layer domain interfaces to their Android implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindLlmProcessingServiceController(
        impl: LlmProcessingServiceControllerImpl
    ): LlmProcessingServiceController
}
