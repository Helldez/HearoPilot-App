package com.hearopilot.app.data.di

import android.content.Context
import androidx.room.Room
import com.hearopilot.app.data.database.AppDatabase
import com.hearopilot.app.data.database.dao.LlmInsightDao
import com.hearopilot.app.data.database.dao.SearchDao
import com.hearopilot.app.data.database.dao.TranscriptionSegmentDao
import com.hearopilot.app.data.database.dao.TranscriptionSessionDao
import com.hearopilot.app.data.repository.TranscriptionRepositoryImpl
import com.hearopilot.app.domain.repository.TranscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database and repository dependencies.
 *
 * All database-related dependencies are scoped as Singleton to ensure
 * a single database instance throughout the app lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provide the Room database instance.
     *
     * Includes proper database migrations to preserve user data across schema updates.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()
    }

    /**
     * Provide TranscriptionSessionDao from the database.
     */
    @Provides
    @Singleton
    fun provideTranscriptionSessionDao(database: AppDatabase): TranscriptionSessionDao {
        return database.transcriptionSessionDao()
    }

    /**
     * Provide TranscriptionSegmentDao from the database.
     */
    @Provides
    @Singleton
    fun provideTranscriptionSegmentDao(database: AppDatabase): TranscriptionSegmentDao {
        return database.transcriptionSegmentDao()
    }

    /**
     * Provide LlmInsightDao from the database.
     */
    @Provides
    @Singleton
    fun provideLlmInsightDao(database: AppDatabase): LlmInsightDao {
        return database.llmInsightDao()
    }

    /**
     * Provide TranscriptionRepository implementation.
     */
    @Provides
    @Singleton
    fun provideSearchDao(database: AppDatabase): SearchDao {
        return database.searchDao()
    }

    @Provides
    @Singleton
    fun provideTranscriptionRepository(
        sessionDao: TranscriptionSessionDao,
        segmentDao: TranscriptionSegmentDao,
        insightDao: LlmInsightDao,
        searchDao: SearchDao
    ): TranscriptionRepository {
        return TranscriptionRepositoryImpl(
            sessionDao = sessionDao,
            segmentDao = segmentDao,
            insightDao = insightDao,
            searchDao = searchDao
        )
    }
}
