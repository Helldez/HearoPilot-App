package com.hearopilot.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an LLM-generated insight.
 *
 * Insights belong to a specific session via foreign key relationship.
 * When a session is deleted, all its insights are automatically deleted (CASCADE).
 *
 * The sourceSegmentIds is stored as a JSON string (comma-separated IDs).
 */
@Entity(
    tableName = "llm_insights",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"])
    ]
)
data class LlmInsightEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "tasks")
    val tasks: String? = null, // Stored as JSON array string

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source_segment_ids")
    val sourceSegmentIds: String // Stored as JSON array string
)
