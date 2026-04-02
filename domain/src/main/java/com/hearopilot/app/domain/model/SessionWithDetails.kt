package com.hearopilot.app.domain.model

/**
 * Aggregates a transcription session with all its related data.
 *
 * Used primarily for displaying session details in the UI, providing
 * a complete view of the session's content.
 *
 * @property session The session metadata
 * @property segments All transcription segments belonging to this session
 * @property insights All LLM insights generated for this session
 */
data class SessionWithDetails(
    val session: TranscriptionSession,
    val segments: List<TranscriptionSegment>,
    val insights: List<LlmInsight>
) {
    /**
     * Get the full transcription text by concatenating all complete segments.
     */
    val fullTranscription: String
        get() = segments
            .filter { it.isComplete }
            .joinToString(" ") { it.text }
            .trim()

    /**
     * Count of complete segments (excludes partial segments).
     */
    val completeSegmentCount: Int
        get() = segments.count { it.isComplete }
}
