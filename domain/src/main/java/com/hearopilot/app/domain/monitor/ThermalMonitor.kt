package com.hearopilot.app.domain.monitor

/**
 * Reports the current thermal pressure of the device SoC.
 *
 * Implementations read platform-specific thermal APIs; the domain layer only
 * depends on this interface so use cases remain Android-free.
 */
interface ThermalMonitor {
    /**
     * Returns true when the device is under thermal pressure (MODERATE status or above).
     * Always returns false on devices running Android 9 or earlier.
     */
    fun isOverheating(): Boolean
}
