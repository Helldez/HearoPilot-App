package com.hearopilot.app.domain.model

/**
 * Type of recording session determining insight generation frequency.
 *
 * - SHORT: Brief recordings (≤10 minutes) with frequent insights
 * - LONG: Extended recordings (up to 3 hours) with configurable longer intervals
 */
enum class RecordingType {
    SHORT,
    LONG
}
