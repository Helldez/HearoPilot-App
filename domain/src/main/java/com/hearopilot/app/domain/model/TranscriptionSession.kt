package com.hearopilot.app.domain.model

/**
 * Represents a transcription session - a single recording session with its own context.
 *
 * Each session maintains an isolated context for both STT segments and LLM insights.
 * When a new session is created, the LLM context is reset to prevent cross-contamination.
 *
 * @property id Unique identifier for this session (UUID)
 * @property name Optional user-provided name (e.g., "Meeting with client", "History lecture notes")
 * @property createdAt Unix timestamp (milliseconds) when this session was created
 * @property lastModifiedAt Unix timestamp (milliseconds) of last update (segment/insight added)
 * @property recordingMode Mode of recording (Simple, Short Meeting, Long Meeting, Translation)
 * @property durationMs Total recording duration in milliseconds (0 until the session is stopped)
 * @property insightStrategy Whether insights are generated periodically or in one batch at end of session
 * @property topic Optional main subject/topic for focused AI insights (e.g., "Q1 Budget Review")
 */
data class TranscriptionSession(
    val id: String,
    val name: String?,
    val createdAt: Long,
    val lastModifiedAt: Long,
    val mode: RecordingMode = RecordingMode.SIMPLE_LISTENING,
    val inputLanguage: String = "it",
    val outputLanguage: String? = null, // Target language for translation mode, null otherwise
    val durationMs: Long = 0L,
    val insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val topic: String? = null // Optional main subject for focused AI insights
)
