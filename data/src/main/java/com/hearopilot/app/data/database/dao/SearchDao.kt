package com.hearopilot.app.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ── Projection data classes (data layer only, never exposed to domain) ────────

data class SegmentSearchResult(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "snippet_text") val snippetText: String,
    @ColumnInfo(name = "session_name") val sessionName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "mode") val mode: String
)

data class InsightSearchResult(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "snippet_text") val snippetText: String,
    @ColumnInfo(name = "session_name") val sessionName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "mode") val mode: String
)

data class SessionNameSearchResult(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "session_name") val sessionName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "mode") val mode: String
)

// ── DAO ───────────────────────────────────────────────────────────────────────

/**
 * DAO for full-text search queries that JOIN across multiple tables.
 */
@Dao
interface SearchDao {

    /**
     * Find transcription segments whose text contains [query].
     * Returns one row per matching segment with the containing session metadata.
     */
    @Query("""
        SELECT seg.id              AS id,
               seg.session_id      AS session_id,
               seg.text            AS snippet_text,
               sess.name           AS session_name,
               sess.created_at     AS created_at,
               sess.mode           AS mode
        FROM   transcription_segments seg
        JOIN   transcription_sessions sess ON seg.session_id = sess.id
        WHERE  seg.text LIKE '%' || :query || '%'
        ORDER  BY sess.created_at DESC
    """)
    fun searchSegments(query: String): Flow<List<SegmentSearchResult>>

    /**
     * Find LLM insights whose title or content contains [query].
     * Returns one row per matching insight.
     */
    @Query("""
        SELECT ins.id                                  AS id,
               ins.session_id                          AS session_id,
               COALESCE(ins.title, ins.content)        AS snippet_text,
               sess.name                               AS session_name,
               sess.created_at                         AS created_at,
               sess.mode                               AS mode
        FROM   llm_insights ins
        JOIN   transcription_sessions sess ON ins.session_id = sess.id
        WHERE  ins.title   LIKE '%' || :query || '%'
           OR  ins.content LIKE '%' || :query || '%'
        ORDER  BY sess.created_at DESC
    """)
    fun searchInsights(query: String): Flow<List<InsightSearchResult>>

    /**
     * Find sessions whose name contains [query].
     */
    @Query("""
        SELECT id         AS session_id,
               name       AS session_name,
               created_at AS created_at,
               mode       AS mode
        FROM   transcription_sessions
        WHERE  name LIKE '%' || :query || '%'
        ORDER  BY created_at DESC
    """)
    fun searchSessionNames(query: String): Flow<List<SessionNameSearchResult>>
}
