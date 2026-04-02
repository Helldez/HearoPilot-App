package com.hearopilot.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a transcription session.
 *
 * Each session represents an isolated recording context with its own
 * transcription segments and LLM insights.
 */
@Entity(tableName = "transcription_sessions")
data class TranscriptionSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_modified_at")
    val lastModifiedAt: Long,

    @ColumnInfo(name = "mode")
    val mode: String = "SIMPLE_LISTENING",

    @ColumnInfo(name = "input_language")
    val inputLanguage: String = "it",

    @ColumnInfo(name = "output_language")
    val outputLanguage: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "insight_strategy")
    val insightStrategy: String = "REAL_TIME",

    @ColumnInfo(name = "topic")
    val topic: String? = null
)
