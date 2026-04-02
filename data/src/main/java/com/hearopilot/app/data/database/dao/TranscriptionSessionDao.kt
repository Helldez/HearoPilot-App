package com.hearopilot.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hearopilot.app.data.database.entity.TranscriptionSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for transcription session operations.
 *
 * Provides CRUD operations for managing transcription sessions.
 */
@Dao
interface TranscriptionSessionDao {

    /**
     * Insert a new session.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TranscriptionSessionEntity)

    /**
     * Update an existing session.
     */
    @Update
    suspend fun update(session: TranscriptionSessionEntity)

    /**
     * Get all sessions ordered by creation time (most recent first).
     */
    @Query("SELECT * FROM transcription_sessions ORDER BY created_at DESC")
    fun getAllSessions(): Flow<List<TranscriptionSessionEntity>>

    /**
     * Get a specific session by ID.
     */
    @Query("SELECT * FROM transcription_sessions WHERE id = :sessionId")
    fun getSession(sessionId: String): Flow<TranscriptionSessionEntity?>

    /**
     * Delete a specific session.
     *
     * CASCADE delete will automatically remove all associated segments and insights.
     */
    @Query("DELETE FROM transcription_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /**
     * Delete all sessions.
     *
     * CASCADE delete will automatically remove all associated data.
     */
    @Query("DELETE FROM transcription_sessions")
    suspend fun deleteAllSessions()

    /**
     * Update the name of a session.
     *
     * Passing null clears the name (session becomes unnamed).
     */
    @Query("UPDATE transcription_sessions SET name = :name WHERE id = :sessionId")
    suspend fun updateName(sessionId: String, name: String?)

    /**
     * Persist the total recording duration for a session.
     */
    @Query("UPDATE transcription_sessions SET duration_ms = :durationMs WHERE id = :sessionId")
    suspend fun updateDuration(sessionId: String, durationMs: Long)

    /**
     * Get session count (for debugging/stats).
     */
    @Query("SELECT COUNT(*) FROM transcription_sessions")
    suspend fun getSessionCount(): Int
}
