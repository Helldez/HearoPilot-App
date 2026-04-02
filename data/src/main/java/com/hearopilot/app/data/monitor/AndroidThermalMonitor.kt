package com.hearopilot.app.data.monitor

import android.os.Build
import android.os.PowerManager
import com.hearopilot.app.domain.monitor.ThermalMonitor

/**
 * Android implementation of [ThermalMonitor] backed by [PowerManager.currentThermalStatus]
 * (API 29 / Android 10+).
 *
 * Returns false on older APIs so that callers can safely use it without API checks.
 */
class AndroidThermalMonitor(private val powerManager: PowerManager) : ThermalMonitor {

    override fun isOverheating(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        // THERMAL_STATUS_MODERATE (2) or above indicates the device is throttling or about to.
        return powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    }
}
