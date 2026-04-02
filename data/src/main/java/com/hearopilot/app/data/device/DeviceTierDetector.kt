package com.hearopilot.app.data.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.hearopilot.app.domain.model.LlmModelVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the device hardware tier and recommends an [LlmModelVariant] accordingly.
 *
 * Two signals are used — both readable without root permissions:
 *  1. Total RAM via [ActivityManager.MemoryInfo].
 *  2. Android SDK version via [Build.VERSION.SDK_INT].
 *
 * Decision: RAM > [HIGH_END_RAM_BYTES] AND SDK > [MIN_SDK_FOR_HIGH_END] → Q8_0, otherwise IQ4_NL.
 *
 * Rationale: devices with more than 8 GB RAM running Android 14+ are virtually always
 * equipped with a modern SoC capable of sustained batch LLM inference without significant
 * thermal degradation. The SDK guard filters out older flagships whose thermal management
 * may not be optimised for sustained CPU-intensive workloads.
 */
@Singleton
class DeviceTierDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceTierDetector"

        // Devices with RAM strictly above this threshold are candidates for Q8_0.
        private const val HIGH_END_RAM_BYTES = 8L * 1_024 * 1_024 * 1_024

        // Minimum SDK version (exclusive) required alongside the RAM check.
        // API 33 = Android 13; devices must be running Android 14+ (API 34+).
        private const val MIN_SDK_FOR_HIGH_END = 33
    }

    /**
     * Returns the recommended [LlmModelVariant] for this device.
     * Fast synchronous call (< 5 ms); safe to call from any thread.
     */
    fun detectRecommendedVariant(): LlmModelVariant {
        val ramBytes = totalRamBytes()
        val sdk = Build.VERSION.SDK_INT

        Log.i(TAG, "RAM: ${ramBytes / 1_073_741_824} GB ($ramBytes bytes), SDK: $sdk")

        val isHighEnd = ramBytes > HIGH_END_RAM_BYTES && sdk > MIN_SDK_FOR_HIGH_END
        return if (isHighEnd) {
            Log.i(TAG, "Recommended: Q8_0 (RAM > 8 GB and SDK > 33)")
            LlmModelVariant.Q8_0
        } else {
            Log.i(TAG, "Recommended: IQ4_NL (RAM or SDK below threshold)")
            LlmModelVariant.IQ4_NL
        }
    }

    private fun totalRamBytes(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
        return memInfo.totalMem
    }
}
