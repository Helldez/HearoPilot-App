package com.hearopilot.app.domain.model

/**
 * Represents a single search hit returned by [SearchTranscriptionsUseCase].
 *
 * @param sessionId    ID of the matched session.
 * @param sessionName  Display name of the session (may be null if auto-named).
 * @param mode         Recording mode of the session (used to pick accent color in UI).
 * @param createdAt    Session creation timestamp (epoch millis) for sorting/display.
 * @param snippet      ~120-char window of text centred on the match.
 * @param matchQuery   The raw query string; used by the UI to highlight matches.
 * @param matchSource  Where in the session the match was found.
 */
data class SearchResult(
    val sessionId: String,
    val sessionName: String?,
    val mode: RecordingMode,
    val createdAt: Long,
    val snippet: String,
    val matchQuery: String,
    val matchSource: SearchMatchSource,
    /** ID of the specific segment or insight that matched; null for SESSION_NAME matches. */
    val highlightId: String? = null
)

enum class SearchMatchSource {
    TRANSCRIPTION,
    INSIGHT,
    SESSION_NAME
}
