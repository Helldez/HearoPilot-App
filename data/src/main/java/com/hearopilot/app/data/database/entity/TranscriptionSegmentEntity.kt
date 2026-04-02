package com.hearopilot.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single transcription segment.
 *
 * Segments belong to a specific session via foreign key relationship.
 * When a session is deleted, all its segments are automatically deleted (CASCADE).
 */
@Entity(
    tableName = "transcription_segments",
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
data class TranscriptionSegmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_complete")
    val isComplete: Boolean
)
