package com.hearopilot.app.domain.model

/**
 * Strategy for generating LLM insights during a recording session.
 *
 * - REAL_TIME: Insights are generated periodically during recording (current behavior).
 * - END_OF_SESSION: A single comprehensive analysis is generated after recording stops,
 *   using a chunked map-reduce pipeline over the full transcript.
 */
enum class InsightStrategy { REAL_TIME, END_OF_SESSION }
