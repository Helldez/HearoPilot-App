package com.hearopilot.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hearopilot.app.data.database.entity.LlmInsightEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for LLM insight operations.
 *
 * Provides operations for managing insights within sessions.
 */
@Dao
interface LlmInsightDao {

    /**
     * Insert a new insight.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: LlmInsightEntity)

    /**
     * Get all insights for a specific session ordered by timestamp.
     */
    @Query("SELECT * FROM llm_insights WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getInsightsBySession(sessionId: String): Flow<List<LlmInsightEntity>>

    /**
     * Get count of insights in a session.
     */
    @Query("SELECT COUNT(*) FROM llm_insights WHERE session_id = :sessionId")
    suspend fun getInsightCount(sessionId: String): Int

    /**
     * Update the content of an existing insight.
     */
    @Query("UPDATE llm_insights SET content = :newContent WHERE id = :insightId")
    suspend fun updateContent(insightId: String, newContent: String)

    /**
     * Update the tasks JSON of an existing insight.
     */
    @Query("UPDATE llm_insights SET tasks = :newTasks WHERE id = :insightId")
    suspend fun updateTasks(insightId: String, newTasks: String?)

    /**
     * Update the title of an existing insight.
     */
    @Query("UPDATE llm_insights SET title = :newTitle WHERE id = :insightId")
    suspend fun updateTitle(insightId: String, newTitle: String?)

    /**
     * Delete all insights for a session (usually handled by CASCADE).
     */
    @Query("DELETE FROM llm_insights WHERE session_id = :sessionId")
    suspend fun deleteInsightsBySession(sessionId: String)

    /**
     * Sum of all insight content + tasks lengths across all sessions, in bytes.
     * Used to compute total on-device insight storage.
     */
    @Query("SELECT COALESCE(SUM(LENGTH(content) + COALESCE(LENGTH(tasks), 0)), 0) FROM llm_insights")
    fun getTotalContentBytes(): Flow<Long>
}
