package com.hearopilot.app.data.util

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.util.Log

/**
 * Debug-only performance logger for battery, RAM and native heap snapshots.
 *
 * Intended to measure real on-device resource usage during long recording sessions.
 * All reads are synchronous and cheap (no I/O); safe to call from any thread.
 *
 * Logcat tag: PerfLogger
 * Filter with: adb logcat -s PerfLogger
 */
object PerfLogger {

    private const val TAG = "PerfLogger"

    /**
     * Logs a full resource snapshot: battery level + current draw + RAM + native heap.
     *
     * @param context Application context
     * @param label   Short description of the snapshot point (e.g. "heartbeat", "llm-start")
     */
    fun logSnapshot(context: Context, label: String) {
        val battery = readBattery(context)
        val mem = readMemory(context)
        Log.i(
            TAG,
            "[$label] " +
            "battery=${battery.levelPct}% current=${battery.currentUa}µA " +
            "| RAM used=${mem.usedRamMb}MB avail=${mem.availRamMb}MB total=${mem.totalRamMb}MB " +
            "| nativeHeap=${mem.nativeHeapMb}MB jvmHeap=${mem.jvmHeapMb}MB"
        )
    }

    /**
     * Logs the result of a single LLM inference call.
     *
     * @param durationMs     Wall-clock time from first token request to last token received
     * @param tokenCount     Number of tokens emitted by the model
     * @param batteryBefore  Battery % captured just before the call
     * @param batteryAfter   Battery % captured just after the call
     */
    fun logInference(
        context: Context,
        durationMs: Long,
        tokenCount: Int,
        batteryBefore: Int,
        batteryAfter: Int
    ) {
        val tokPerSec = if (durationMs > 0) tokenCount * 1000f / durationMs else 0f
        val mem = readMemory(context)
        Log.i(
            TAG,
            "[llm-done] duration=${durationMs}ms tokens=$tokenCount " +
            "speed=${"%.1f".format(tokPerSec)}tok/s " +
            "battery=${batteryBefore}%→${batteryAfter}% Δ=${batteryAfter - batteryBefore}% " +
            "| nativeHeap=${mem.nativeHeapMb}MB"
        )
    }

    /** Returns current battery level in percent (0–100). */
    fun getBatteryPct(context: Context): Int = readBattery(context).levelPct

    // ─── Private helpers ──────────────────────────────────────────────────────

    private data class BatterySnapshot(
        val levelPct: Int,
        // Instantaneous current in µA. Negative = discharging, positive = charging.
        // Returns Int.MIN_VALUE if the device does not expose this counter.
        val currentUa: Long
    )

    private data class MemSnapshot(
        val availRamMb: Long,
        val totalRamMb: Long,
        val usedRamMb: Long,
        // malloc()'d native heap for this process (does NOT include mmap'd regions).
        val nativeHeapMb: Long,
        val jvmHeapMb: Long
    )

    private fun readBattery(context: Context): BatterySnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return BatterySnapshot(levelPct = level, currentUa = current)
    }

    private fun readMemory(context: Context): MemSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val availMb = memInfo.availMem / 1_048_576L
        val totalMb = memInfo.totalMem / 1_048_576L
        val usedMb  = totalMb - availMb

        // getNativeHeapAllocatedSize() tracks malloc()/free() via the native allocator.
        // Note: mmap'd + mlock'd regions (e.g. GGUF weights) appear here only if
        // llama.cpp wraps the mmap in its own allocator; otherwise use Debug.MemoryInfo
        // for PSS-based accounting.
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1_048_576L

        val rt = Runtime.getRuntime()
        val jvmMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L

        return MemSnapshot(
            availRamMb  = availMb,
            totalRamMb  = totalMb,
            usedRamMb   = usedMb,
            nativeHeapMb = nativeMb,
            jvmHeapMb   = jvmMb
        )
    }
}
