package com.hearopilot.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hearopilot.app.data.database.dao.LlmInsightDao
import com.hearopilot.app.data.database.dao.SearchDao
import com.hearopilot.app.data.database.dao.TranscriptionSegmentDao
import com.hearopilot.app.data.database.dao.TranscriptionSessionDao
import com.hearopilot.app.data.database.entity.LlmInsightEntity
import com.hearopilot.app.data.database.entity.TranscriptionSegmentEntity
import com.hearopilot.app.data.database.entity.TranscriptionSessionEntity

/**
 * Room database for the application.
 *
 * Manages persistent storage of transcription sessions, segments, and LLM insights.
 *
 * Database version 1:
 * - Initial schema with sessions, segments, and insights tables
 * - Foreign key relationships with CASCADE delete
 * - Indices on session_id columns for query performance
 *
 * Database version 2:
 * - Added recording_type column to transcription_sessions table
 * - Defaults to "SHORT" for backward compatibility
 *
 * Database version 3:
 * - Added mode, input_language, output_language to transcription_sessions
 * - Migrated recording_type to mode
 *
 * Database version 4:
 * - Added title and tasks columns to llm_insights table
 * - Support for structured AI output with titles and extracted tasks
 *
 * Database version 5:
 * - Added duration_ms column to transcription_sessions table
 * - Stores total recording duration in milliseconds (0 for pre-existing sessions)
 *
 * Database version 6:
 * - Added insight_strategy column (REAL_TIME/END_OF_SESSION) to transcription_sessions
 * - Added topic column (nullable text) to transcription_sessions
 */
@Database(
    entities = [
        TranscriptionSessionEntity::class,
        TranscriptionSegmentEntity::class,
        LlmInsightEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transcriptionSessionDao(): TranscriptionSessionDao
    abstract fun transcriptionSegmentDao(): TranscriptionSegmentDao
    abstract fun llmInsightDao(): LlmInsightDao
    abstract fun searchDao(): SearchDao

    companion object {
        const val DATABASE_NAME = "libellula_transcription.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE transcription_sessions ADD COLUMN recording_type TEXT NOT NULL DEFAULT 'SHORT'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns
                database.execSQL("ALTER TABLE transcription_sessions ADD COLUMN mode TEXT NOT NULL DEFAULT 'SIMPLE_LISTENING'")
                database.execSQL("ALTER TABLE transcription_sessions ADD COLUMN input_language TEXT NOT NULL DEFAULT 'it'")
                database.execSQL("ALTER TABLE transcription_sessions ADD COLUMN output_language TEXT")

                // Migrate existing data (recording_type -> mode)
                // Note: recording_type column remains but is unused by the Entity
                database.execSQL("""
                    UPDATE transcription_sessions
                    SET mode = CASE
                        WHEN recording_type = 'SHORT' THEN 'SHORT_MEETING'
                        WHEN recording_type = 'LONG' THEN 'LONG_MEETING'
                        ELSE 'SIMPLE_LISTENING'
                    END
                """)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add title and tasks columns to llm_insights
                database.execSQL("ALTER TABLE llm_insights ADD COLUMN title TEXT")
                database.execSQL("ALTER TABLE llm_insights ADD COLUMN tasks TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add duration_ms column to transcription_sessions; 0 = unknown for existing sessions
                database.execSQL(
                    "ALTER TABLE transcription_sessions ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add insight_strategy column; existing sessions default to REAL_TIME (legacy behavior)
                database.execSQL(
                    "ALTER TABLE transcription_sessions ADD COLUMN insight_strategy TEXT NOT NULL DEFAULT 'REAL_TIME'"
                )
                // Add topic column; existing sessions have no topic
                database.execSQL(
                    "ALTER TABLE transcription_sessions ADD COLUMN topic TEXT"
                )
            }
        }
    }
}
