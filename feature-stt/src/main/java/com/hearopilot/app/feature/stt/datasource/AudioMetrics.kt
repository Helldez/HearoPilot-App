package com.hearopilot.app.feature.stt.datasource

import android.os.Build
import android.util.Log
import java.io.File
import kotlin.math.abs

/**
 * Diagnostic metrics for audio recording and STT processing.
 * Helps identify performance issues across different devices.
 */
data class AudioMetrics(
    // Audio capture metrics
    var totalAudioReads: Int = 0,
    var failedAudioReads: Int = 0,
    var totalSamplesRead: Long = 0,
    var minReadLatencyMs: Long = Long.MAX_VALUE,
    var maxReadLatencyMs: Long = 0,
    var avgReadLatencyMs: Double = 0.0,

    // Buffer health
    var channelOfferTimeouts: Int = 0,
    var channelCurrentSize: Int = 0,
    var maxChannelSize: Int = 0,

    // VAD metrics
    var vadWindowsProcessed: Int = 0,
    var vadFailures: Int = 0,
    var speechSegmentsDetected: Int = 0,

    // Inference metrics
    var inferenceCallsTotal: Int = 0,
    var inferenceCallsSuccess: Int = 0,
    var inferenceCallsFailure: Int = 0,
    var minInferenceTimeMs: Long = Long.MAX_VALUE,
    var maxInferenceTimeMs: Long = 0,
    var avgInferenceTimeMs: Double = 0.0,

    // Thread info (captured once at start)
    var recordingThreadId: Long = 0,
    var recordingThreadName: String = "",
    var processingThreadId: Long = 0,
    var processingThreadName: String = "",

    // Device info
    val deviceManufacturer: String = Build.MANUFACTURER,
    val deviceModel: String = Build.MODEL,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val cpuArchitecture: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
) {
    private val readLatencies = mutableListOf<Long>()
    private val inferenceLatencies = mutableListOf<Long>()

    /**
     * Record an audio read operation
     */
    fun recordAudioRead(samplesRead: Int, latencyMs: Long, success: Boolean) {
        totalAudioReads++
        if (success) {
            totalSamplesRead += samplesRead
            minReadLatencyMs = minOf(minReadLatencyMs, latencyMs)
            maxReadLatencyMs = maxOf(maxReadLatencyMs, latencyMs)
            readLatencies.add(latencyMs)
            avgReadLatencyMs = readLatencies.average()
        } else {
            failedAudioReads++
        }
    }

    /**
     * Record inference timing
     */
    fun recordInference(latencyMs: Long, success: Boolean) {
        inferenceCallsTotal++
        if (success) {
            inferenceCallsSuccess++
            minInferenceTimeMs = minOf(minInferenceTimeMs, latencyMs)
            maxInferenceTimeMs = maxOf(maxInferenceTimeMs, latencyMs)
            inferenceLatencies.add(latencyMs)
            avgInferenceTimeMs = inferenceLatencies.average()
        } else {
            inferenceCallsFailure++
        }
    }

    /**
     * Record VAD processing
     */
    fun recordVadProcessing(success: Boolean) {
        vadWindowsProcessed++
        if (!success) {
            vadFailures++
        }
    }

    /**
     * Record speech detection
     */
    fun recordSpeechSegment() {
        speechSegmentsDetected++
    }

    /**
     * Record channel buffer state
     */
    fun recordChannelState(currentSize: Int) {
        channelCurrentSize = currentSize
        maxChannelSize = maxOf(maxChannelSize, currentSize)
    }

    /**
     * Get CPU core information from /proc/cpuinfo
     */
    private fun getCpuInfo(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            // Extract interesting lines (processor type, frequencies)
            cpuInfo.lines()
                .filter {
                    it.contains("processor", ignoreCase = true) ||
                    it.contains("Hardware", ignoreCase = true) ||
                    it.contains("CPU part", ignoreCase = true)
                }
                .take(5)
                .joinToString("; ")
        } catch (e: Exception) {
            "Unable to read CPU info"
        }
    }

    /**
     * Calculate audio drop percentage (samples expected vs received)
     */
    fun calculateAudioDropRate(recordingDurationSeconds: Double): Double {
        val expectedSamples = (recordingDurationSeconds * 16000).toLong()
        return if (expectedSamples > 0) {
            val dropped = maxOf(0, expectedSamples - totalSamplesRead)
            (dropped.toDouble() / expectedSamples) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Check if we have buffer backlog issues
     */
    fun hasBufferBacklog(): Boolean {
        // If channel grows beyond 10 chunks, processing can't keep up
        return maxChannelSize > 10
    }

    /**
     * Check if we have audio read issues
     */
    fun hasAudioReadIssues(): Boolean {
        return failedAudioReads > 0 || maxReadLatencyMs > 150
    }

    /**
     * Check if we have inference performance issues
     */
    fun hasInferenceIssues(): Boolean {
        return avgInferenceTimeMs > 150 || inferenceCallsFailure > 0
    }

    /**
     * Generate detailed diagnostic report
     */
    fun generateReport(): String {
        return buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("STT PERFORMANCE DIAGNOSTICS")
            appendLine("═══════════════════════════════════════")
            appendLine()

            appendLine("📱 DEVICE INFO")
            appendLine("  Manufacturer: $deviceManufacturer")
            appendLine("  Model: $deviceModel")
            appendLine("  Android: $androidVersion")
            appendLine("  CPU Cores: $cpuCores")
            appendLine("  CPU Arch: $cpuArchitecture")
            appendLine("  CPU Info: ${getCpuInfo()}")
            appendLine()

            appendLine("🎙️ AUDIO CAPTURE")
            appendLine("  Total Reads: $totalAudioReads")
            appendLine("  Failed Reads: $failedAudioReads")
            appendLine("  Total Samples: $totalSamplesRead (${totalSamplesRead / 16000.0}s)")
            appendLine("  Read Latency: min=${minReadLatencyMs}ms avg=${"%.1f".format(avgReadLatencyMs)}ms max=${maxReadLatencyMs}ms")
            if (hasAudioReadIssues()) {
                appendLine("  ⚠️ WARNING: Audio read issues detected!")
            }
            appendLine()

            appendLine("📦 BUFFER HEALTH")
            appendLine("  Channel Max Size: $maxChannelSize chunks")
            appendLine("  Channel Timeouts: $channelOfferTimeouts")
            if (hasBufferBacklog()) {
                appendLine("  ⚠️ WARNING: Buffer backlog detected - processing too slow!")
            }
            appendLine()

            appendLine("🔍 VAD PROCESSING")
            appendLine("  Windows Processed: $vadWindowsProcessed")
            appendLine("  VAD Failures: $vadFailures")
            appendLine("  Speech Segments: $speechSegmentsDetected")
            appendLine()

            appendLine("🧠 INFERENCE")
            appendLine("  Total Calls: $inferenceCallsTotal")
            appendLine("  Success: $inferenceCallsSuccess")
            appendLine("  Failures: $inferenceCallsFailure")
            if (inferenceCallsSuccess > 0) {
                appendLine("  Inference Time: min=${minInferenceTimeMs}ms avg=${"%.1f".format(avgInferenceTimeMs)}ms max=${maxInferenceTimeMs}ms")
            }
            if (hasInferenceIssues()) {
                appendLine("  ⚠️ WARNING: Inference performance issues detected!")
            }
            appendLine()

            appendLine("🧵 THREAD INFO")
            appendLine("  Recording Thread: $recordingThreadName (ID: $recordingThreadId)")
            appendLine("  Processing Thread: $processingThreadName (ID: $processingThreadId)")
            appendLine()

            // Overall health assessment
            appendLine("📊 HEALTH ASSESSMENT")
            val issues = mutableListOf<String>()
            if (hasAudioReadIssues()) issues.add("Audio capture")
            if (hasBufferBacklog()) issues.add("Buffer backlog")
            if (hasInferenceIssues()) issues.add("Inference performance")
            if (vadFailures > 0) issues.add("VAD failures")

            if (issues.isEmpty()) {
                appendLine("  ✅ No issues detected")
            } else {
                appendLine("  ⚠️ Issues detected in: ${issues.joinToString(", ")}")
            }

            appendLine("═══════════════════════════════════════")
        }
    }

    companion object {
        private const val TAG = "AudioMetrics"

        /**
         * Log metrics report to logcat
         */
        fun logReport(metrics: AudioMetrics) {
            val report = metrics.generateReport()
            // Split by lines and log each with proper tag
            report.lines().forEach { line ->
                Log.i(TAG, line)
            }
        }
    }
}
