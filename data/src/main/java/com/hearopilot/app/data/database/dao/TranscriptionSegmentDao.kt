package com.hearopilot.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hearopilot.app.data.database.entity.TranscriptionSegmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for transcription segment operations.
 *
 * Provides operations for managing segments within sessions.
 */
@Dao
interface TranscriptionSegmentDao {

    /**
     * Insert a new segment.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: TranscriptionSegmentEntity)

    /**
     * Get all segments for a specific session ordered by timestamp.
     */
    @Query("SELECT * FROM transcription_segments WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getSegmentsBySession(sessionId: String): Flow<List<TranscriptionSegmentEntity>>

    /**
     * Get count of complete segments in a session.
     */
    @Query("SELECT COUNT(*) FROM transcription_segments WHERE session_id = :sessionId AND is_complete = 1")
    suspend fun getCompleteSegmentCount(sessionId: String): Int

    /**
     * Update the text of an existing segment.
     */
    @Query("UPDATE transcription_segments SET text = :newText WHERE id = :segmentId")
    suspend fun updateText(segmentId: String, newText: String)

    /**
     * Delete all segments for a session (usually handled by CASCADE).
     */
    @Query("DELETE FROM transcription_segments WHERE session_id = :sessionId")
    suspend fun deleteSegmentsBySession(sessionId: String)

    /**
     * Sum of all segment text lengths across all sessions, in bytes.
     * Used to compute total on-device transcript storage.
     */
    @Query("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM transcription_segments")
    fun getTotalTextBytes(): Flow<Long>
}
