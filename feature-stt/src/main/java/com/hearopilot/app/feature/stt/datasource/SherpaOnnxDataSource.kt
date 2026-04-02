package com.hearopilot.app.feature.stt.datasource

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PerformanceHintManager
import android.os.Process
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import com.hearopilot.app.data.datasource.RecognitionResult
import com.hearopilot.app.data.datasource.SttDataSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
/**
 * Sherpa-ONNX implementation of SttDataSource.
 *
 * Handles audio recording, VAD (Voice Activity Detection), and ASR (Automatic Speech Recognition).
 * Uses Provider (unscoped) so a fresh recognizer and VAD are created each time initialize()
 * is called, and releaseModel() can free the ~670 MB native heap between sessions.
 *
 * @property context Android context
 * @property recognizerProvider Factory for creating a new OfflineRecognizer instance
 * @property vadProvider Factory for creating a new Vad instance
 * @property audioManager System AudioManager used to activate Bluetooth SCO when available
 */
class SherpaOnnxDataSource(
    private val context: Context,
    private val recognizerProvider: javax.inject.Provider<OfflineRecognizer>,
    private val vadProvider: javax.inject.Provider<Vad>,
    private val audioManager: AudioManager
) : SttDataSource {

    // Managed instances — null when model is not loaded, non-null during an active session.
    @Volatile private var activeRecognizer: OfflineRecognizer? = null
    @Volatile private var activeVad: Vad? = null

    // Serializes all recognizer.decode() calls. OfflineRecognizer is NOT thread-safe for
    // concurrent decode() invocations: even though each call uses its own OfflineStream,
    // the underlying ONNX InferenceSession has shared internal state that causes SIGSEGV
    // (fault addr 0x0/0x8 in libonnxruntime decode+128) when two decode() calls run in
    // parallel. The mutex ensures only one decode runs at a time across all call sites:
    // forced split, partial inference (async launch), VAD segment, and post-loop flush.
    private val decodeMutex = Mutex()

    // Lazily resolve the active instances; create via provider if not yet loaded.
    private val recognizer: OfflineRecognizer
        get() = activeRecognizer ?: recognizerProvider.get().also { activeRecognizer = it }
    private val vad: Vad
        get() = activeVad ?: vadProvider.get().also { activeVad = it }

    private companion object {
        private const val TAG = "SherpaOnnxDataSource"
        private const val SAMPLE_RATE_HZ = 16000
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val VAD_WINDOW_SIZE = 512
        private const val SPEECH_START_LOOKBACK_SAMPLES = 6400 // 0.4s
        private const val MIN_INFERENCE_INTERVAL_MS = 200L
        // Minimum new audio samples since last inference before triggering again.
        // The offline Parakeet TDT model produces better results with more audio context.
        // This threshold ensures consistent accuracy regardless of device inference speed.
        // 24000 samples = 1.5s at 16kHz
        private const val MIN_NEW_AUDIO_SAMPLES = 24000
        // Maximum audio samples passed to a single inference call.
        // The offline model re-decodes the entire buffer from speechStartOffset each call.
        // Without a cap, inference time grows linearly with segment length (O(n) per call,
        // O(n²) total), reaching 9s+ on 60s segments. Capping at 30s keeps inference
        // bounded while providing enough context for accurate partial results.
        // The final VAD segment uses its own buffer and is NOT affected by this cap.
        // 480000 samples = 30s at 16kHz
        private const val MAX_INFERENCE_AUDIO_SAMPLES = 480000
        // Audio samples to carry over from the end of a VAD segment into the next.
        // This provides context so the model doesn't start from zero after a silence gap.
        // 48000 samples = 3s at 16kHz
        private const val CONTEXT_CARRY_OVER_SAMPLES = 48000
        private const val INITIAL_BUFFER_CAPACITY = 160000 // ~10s pre-allocated
        private const val LOG_INTERVAL_READS = 50

    }

    /**
     * Pre-allocated growable float buffer. Avoids Float boxing overhead
     * from ArrayList<Float> which causes GC pressure and latency spikes.
     */
    private class SampleBuffer(initialCapacity: Int = INITIAL_BUFFER_CAPACITY) {
        private var data = FloatArray(initialCapacity)
        var size: Int = 0
            private set

        fun append(samples: FloatArray) {
            ensureCapacity(size + samples.size)
            samples.copyInto(data, size)
            size += samples.size
        }

        fun getRange(from: Int, to: Int): FloatArray = data.copyOfRange(from, to)

        fun clear() {
            size = 0
        }

        /**
         * Keep only the last [samplesToKeep] samples, shifting them to the start.
         * Used to carry over audio context across VAD segment boundaries.
         */
        fun keepTail(samplesToKeep: Int) {
            val toKeep = samplesToKeep.coerceAtMost(size)
            if (toKeep <= 0) {
                size = 0
                return
            }
            data.copyInto(data, 0, size - toKeep, size)
            size = toKeep
        }

        private fun ensureCapacity(minCapacity: Int) {
            if (minCapacity > data.size) {
                data = data.copyOf(maxOf(minCapacity, data.size * 2))
            }
        }
    }

    // Tracks whether we successfully activated Bluetooth SCO for this recording session.
    // Used to tear it down symmetrically in stopRecording().
    private var bluetoothScoActive = false

    /**
     * Attempts to activate Bluetooth SCO so Android routes the BT headset mic as the
     * audio input. If no BT device is connected, or if the SCO handshake times out,
     * the method returns false and recording proceeds with the built-in device mic.
     *
     * Must be called on a coroutine (suspends up to [BLUETOOTH_SCO_CONNECT_TIMEOUT_MS]).
     */
    /**
     * Routes audio input to a connected Bluetooth HFP/HSP headset using the modern
     * setCommunicationDevice() API (available since API 31, replacing the deprecated
     * startBluetoothSco()). Searches availableCommunicationDevices for a BT headset
     * input device and activates it synchronously — no broadcast receiver needed.
     *
     * Falls back to the built-in device mic silently if no BT headset is available.
     *
     * @return true if a BT headset was selected, false if falling back to device mic.
     */
    private fun activateBluetoothSco(): Boolean {
        val btDevice = audioManager.availableCommunicationDevices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        if (btDevice == null) {
            Log.d(TAG, "No Bluetooth headset available — using device mic")
            return false
        }
        val success = audioManager.setCommunicationDevice(btDevice)
        return if (success) {
            Log.i(TAG, "Bluetooth communication device set: ${btDevice.productName} (type=${btDevice.type})")
            true
        } else {
            Log.w(TAG, "setCommunicationDevice() failed — using device mic")
            false
        }
    }

    /**
     * Clears the communication device previously set by [activateBluetoothSco].
     * Safe to call even if activation was never performed.
     */
    private fun deactivateBluetoothSco() {
        if (bluetoothScoActive) {
            audioManager.clearCommunicationDevice()
            bluetoothScoActive = false
            Log.i(TAG, "Bluetooth communication device cleared")
        }
    }

    private var audioRecord: AudioRecord? = null
    // @Volatile ensures visibility across threads: stopRecording() is called from the
    // ViewModel/main thread while the recordingJob reads this on Dispatchers.IO.
    @Volatile private var isRecording = false

    // Diagnostic metrics
    private var metrics = AudioMetrics()
    private var recordingStartTimeMs = 0L

    override suspend fun initialize(modelPath: String): Result<Unit> = runCatching {
        // Eagerly create recognizer and VAD so any model-not-found error surfaces here
        // rather than mid-recording. If instances are already loaded (re-use without release)
        // the lazy properties return the existing ones.
        // Must run on IO: recognizerProvider.get() loads ~670 MB ONNX model from disk and
        // would block the main thread otherwise (Choreographer: Skipped 228 frames).
        withContext(Dispatchers.IO) {
            val r = recognizer  // triggers recognizerProvider.get() if not yet loaded
            val v = vad         // triggers vadProvider.get() if not yet loaded
            // Drain any leftover segments from the previous session before resetting the
            // RNN hidden state. vad.reset() only resets the model state, NOT the segments
            // queue — unconsumed segments from session N would cause isSpeechDetected() to
            // malfunction in session N+1, silently blocking all transcription output.
            while (!v.empty()) {
                v.pop()
            }
            v.reset()
        }
    }

    @SuppressLint("MissingPermission")
    override fun startRecording(): Flow<RecognitionResult> = channelFlow {
        // Ensure only one recording session at a time
        if (isRecording) {
            // Force cleanup from previous session (defensive recovery)
            Log.w(TAG, "Recording already in progress - forcing cleanup from previous session")
            stopRecording()
        }
        isRecording = true

        // Ensure both the recognizer and VAD are loaded before each recording session.
        // MainViewModel.initialize() is idempotent (runs once per ViewModel lifetime), so
        // startRecording() is the only guaranteed per-session entry point.
        //
        // recognizer: accesses the lazy property which calls recognizerProvider.get() if
        // activeRecognizer is null (e.g. after stopStreaming() → releaseModel()). Without
        // this, all four decode sites use `activeRecognizer ?: return@withLock null` and
        // silently produce no output — VAD detects speech but decode always returns null.
        // Must run on IO: recognizerProvider.get() loads ~670 MB ONNX model from disk.
        //
        // vad: drain any leftover segments from the previous session before resetting the
        // RNN hidden state. vad.reset() only resets the model state, NOT the segments
        // queue — unconsumed segments cause isSpeechDetected() to malfunction silently.
        withContext(Dispatchers.IO) {
            recognizer // triggers recognizerProvider.get() if activeRecognizer is null
            val v = vad
            while (!v.empty()) {
                v.pop()
            }
            v.reset()
        }

        // Route audio input to BT headset if available; falls back to device mic silently.
        bluetoothScoActive = activateBluetoothSco()

        // Reset metrics for new session
        metrics = AudioMetrics()
        recordingStartTimeMs = System.currentTimeMillis()

        Log.i(TAG, "Starting recording session (bluetoothSco=$bluetoothScoActive)")
        Log.i(TAG, "Device: ${metrics.deviceManufacturer} ${metrics.deviceModel}")
        Log.i(TAG, "CPU: ${metrics.cpuCores} cores, ${metrics.cpuArchitecture}")

        // Create a new channel for this recording session
        val samplesChannel = Channel<FloatArray>(Channel.UNLIMITED)

        // Audio recording coroutine (from Home.kt lines 117-136)
        val recordingJob = launch(Dispatchers.IO) {
            // Elevate priority to URGENT_AUDIO so the kernel prefers this thread
            // over background work when scheduling. Prevents audio buffer drops
            // on loaded devices (busy foreground apps, GC pauses, etc.).
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            // Capture thread info
            metrics.recordingThreadId = Thread.currentThread().id
            metrics.recordingThreadName = Thread.currentThread().name

            val audioSource = MediaRecorder.AudioSource.MIC
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val numBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, channelConfig, audioFormat)
            val actualBufferSize = numBytes * BUFFER_SIZE_MULTIPLIER

            Log.i(TAG, "Audio config: minBufferSize=${numBytes}bytes, actualBuffer=${actualBufferSize}bytes (${BUFFER_SIZE_MULTIPLIER}x)")
            Log.i(TAG, "Recording thread: ${metrics.recordingThreadName} (${metrics.recordingThreadId})")

            audioRecord = AudioRecord(
                audioSource,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                actualBufferSize
            )

            audioRecord?.startRecording()

            val bufferSize = (0.1 * SAMPLE_RATE_HZ).toInt() // 100ms read chunks
            val buffer = ShortArray(bufferSize)

            while (isRecording) {
                val readStartTime = System.currentTimeMillis()
                val ret = audioRecord?.read(buffer, 0, buffer.size)
                val readLatency = System.currentTimeMillis() - readStartTime

                if (ret != null && ret > 0) {
                    metrics.recordAudioRead(ret, readLatency, success = true)

                    val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                    // Validate samples before sending
                    if (samples.all { it.isFinite() }) {
                        samplesChannel.send(samples)

                        // Record channel state (approximate)
                        // Note: Channel.UNLIMITED doesn't expose size, but we can estimate
                        // by tracking sends vs receives
                    } else {
                        Log.w(TAG, "Invalid samples detected in audio read")
                    }
                } else {
                    metrics.recordAudioRead(0, readLatency, success = false)
                    Log.w(TAG, "Audio read failed or returned no data: ret=$ret")
                }

                if (metrics.totalAudioReads % LOG_INTERVAL_READS == 0) {
                    val elapsedSec = (System.currentTimeMillis() - recordingStartTimeMs) / 1000.0
                    Log.d(TAG, "Recording stats: ${metrics.totalAudioReads} reads, " +
                            "${metrics.totalSamplesRead / SAMPLE_RATE_HZ.toDouble()}s audio, " +
                            "avgLatency=${"%.1f".format(metrics.avgReadLatencyMs)}ms, " +
                            "elapsed=${elapsedSec}s")
                }
            }
            // Signal end
            samplesChannel.close()

            Log.i(TAG, "Recording job completed")
        }

        // VAD + ASR processing coroutine
        withContext(Dispatchers.Default) {
            // Hint the Android scheduler that this thread is performance-sensitive.
            // On big.LITTLE SoCs (Snapdragon, Dimensity, Exynos) without this hint
            // the scheduler may park the ASR thread on efficiency cores, causing
            // inference latency spikes. The 50ms target matches the 100ms audio read
            // cycle minus VAD overhead, keeping partial results timely.
            // minSdk = 32 guarantees PerformanceHintManager is always available (API 31+).
            val hintManager = context.getSystemService(PerformanceHintManager::class.java)
            val targetDurationNs = 50_000_000L // 50ms per VAD+ASR cycle
            hintManager?.createHintSession(
                intArrayOf(Process.myTid()),
                targetDurationNs
            )
            // session stays active for the lifetime of this withContext block

            metrics.processingThreadId = Thread.currentThread().id
            metrics.processingThreadName = Thread.currentThread().name
            Log.i(TAG, "Processing thread: ${metrics.processingThreadName} (${metrics.processingThreadId})")

            val buffer = SampleBuffer()
            var offset = 0
            var isSpeechStarted = false
            var startTime = System.currentTimeMillis()
            var speechStartOffset = 0
            var lastInferenceOffset = 0
            // Guard against a slow device queuing multiple concurrent partial inferences.
            // When the previous partial call is still running we skip the interval and let
            // audio accumulate, so the next call processes a larger (but fresher) chunk.
            // The decodeMutex (class-level) serializes the actual decode() calls so the
            // async partial inference child never races with synchronous decode sites.
            val isPartialInferenceBusy = AtomicBoolean(false)

            for (samples in samplesChannel) {
                buffer.append(samples)

                // VAD processing with fixed window size
                while (offset + VAD_WINDOW_SIZE <= buffer.size) {
                    val vadWindow = buffer.getRange(offset, offset + VAD_WINDOW_SIZE)
                    if (vadWindow.all { it.isFinite() }) {
                        try {
                            vad.acceptWaveform(vadWindow)
                            metrics.recordVadProcessing(success = true)
                        } catch (e: Exception) {
                            Log.e(TAG, "VAD acceptWaveform failed", e)
                            metrics.recordVadProcessing(success = false)
                        }
                    }
                    offset += VAD_WINDOW_SIZE

                    if (!isSpeechStarted && vad.isSpeechDetected()) {
                        isSpeechStarted = true
                        speechStartOffset = maxOf(0, offset - SPEECH_START_LOOKBACK_SAMPLES)
                        startTime = System.currentTimeMillis()
                        lastInferenceOffset = speechStartOffset
                        metrics.recordSpeechSegment()
                        Log.d(TAG, "Speech detected at offset $offset")
                    }
                }

                // Force a segment split when speech has been ongoing for too long without
                // VAD silence detection (e.g. continuous monologue with no 0.5s pause).
                // Without this, sherpa-onnx's internal circular buffer doubles every ~60s,
                // causing native memory growth and all audio flushing as one giant segment
                // only when recording stops.
                //
                // This block must come BEFORE the partial inference check below. On slow
                // devices the partial inference takes longer than MIN_NEW_AUDIO_SAMPLES worth
                // of audio, so isPartialInferenceBusy is almost always true in the same
                // iteration where the 30s threshold is first crossed — meaning the forced split
                // would never fire if evaluated after the partial inference check. By evaluating
                // it first we guarantee it wins the scheduling window.
                //
                // Must join any in-flight partial inference before calling decode() here.
                // OfflineRecognizer.decode() is NOT thread-safe for concurrent calls: streams
                // are independent objects but the underlying ONNX InferenceSession has shared
                // internal state that causes SIGSEGV (fault addr 0x0/0x8 in libonnxruntime)
                // when two decode() calls run simultaneously. The previous comment claiming
                // "parallel execution is safe" was incorrect — it has been disproved by crashes.
                val speechElapsedSamples = offset - speechStartOffset
                if (isSpeechStarted && speechElapsedSamples >= MAX_INFERENCE_AUDIO_SAMPLES) {
                    val cappedStart = maxOf(speechStartOffset, offset - MAX_INFERENCE_AUDIO_SAMPLES)
                    val audioSegment = buffer.getRange(cappedStart, offset)
                    try {
                        if (audioSegment.isNotEmpty() && audioSegment.all { it.isFinite() }) {
                            // Hold decodeMutex for the entire stream lifecycle: createStream()
                            // accesses the recognizer's shared ONNX session just like decode(),
                            // so releaseModel() must not free the native heap between the two.
                            val trimmedText = decodeMutex.withLock {
                                val r = activeRecognizer ?: return@withLock null
                                val stream = r.createStream()
                                stream.acceptWaveform(audioSegment, SAMPLE_RATE_HZ)
                                r.decode(stream)
                                val result = r.getResult(stream)
                                stream.release()
                                result.text.trim().takeIf { it.isNotBlank() }
                            }
                            if (trimmedText != null) {
                                send(RecognitionResult(trimmedText, isComplete = true))
                                Log.d(TAG, "Forced split after ${speechElapsedSamples / SAMPLE_RATE_HZ}s: ${trimmedText.take(50)}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Forced split inference failed", e)
                    }
                    isSpeechStarted = false
                    vad.reset()
                    buffer.keepTail(CONTEXT_CARRY_OVER_SAMPLES)
                    offset = buffer.size
                    speechStartOffset = 0
                    lastInferenceOffset = offset
                    startTime = System.currentTimeMillis()
                }

                // Trigger partial inference only when enough new audio has accumulated.
                // Runs in a child coroutine so VAD segment detection (end-of-speech) is
                // never blocked on slow devices. If the previous partial inference is still
                // running we skip this interval; audio keeps accumulating and will be picked
                // up in the next eligible cycle. isSpeechStarted is false after a forced
                // split above, so this block is a no-op in the same iteration.
                val elapsed = System.currentTimeMillis() - startTime
                val newAudioSamples = offset - lastInferenceOffset
                if (isSpeechStarted && elapsed > MIN_INFERENCE_INTERVAL_MS
                    && newAudioSamples >= MIN_NEW_AUDIO_SAMPLES
                    && isPartialInferenceBusy.compareAndSet(false, true)
                ) {
                    // Snapshot audio before launching — getRange returns a new FloatArray copy.
                    val cappedStart = maxOf(speechStartOffset, offset - MAX_INFERENCE_AUDIO_SAMPLES)
                    val audioSnapshot = buffer.getRange(cappedStart, offset)
                    val snapshotNewSamples = newAudioSamples
                    // Advance offset trackers immediately so the next iteration measures
                    // new audio relative to this snapshot, not the previous one.
                    lastInferenceOffset = offset
                    startTime = System.currentTimeMillis()

                    launch(Dispatchers.Default) {
                        val inferenceStart = System.currentTimeMillis()
                        try {
                            if (audioSnapshot.isNotEmpty() && audioSnapshot.all { it.isFinite() }) {
                                // Hold decodeMutex for the entire stream lifecycle (see forced-split
                                // comment above): createStream() and decode() both touch the ONNX
                                // InferenceSession; releaseModel() must not run between them.
                                val trimmedText = decodeMutex.withLock {
                                    val r = activeRecognizer ?: return@withLock null
                                    val stream = r.createStream()
                                    stream.acceptWaveform(audioSnapshot, SAMPLE_RATE_HZ)
                                    r.decode(stream)
                                    val result = r.getResult(stream)
                                    stream.release()
                                    result.text.trim().takeIf { it.isNotBlank() }
                                }
                                val inferenceLatency = System.currentTimeMillis() - inferenceStart
                                metrics.recordInference(inferenceLatency, success = trimmedText != null)
                                if (trimmedText != null) {
                                    send(RecognitionResult(trimmedText, isComplete = false))
                                    Log.d(TAG, "Partial result (${inferenceLatency}ms, " +
                                            "$snapshotNewSamples new samples): ${trimmedText.take(50)}")
                                }
                            } else {
                                val inferenceLatency = System.currentTimeMillis() - inferenceStart
                                metrics.recordInference(inferenceLatency, success = false)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "ASR partial inference failed", e)
                            val inferenceLatency = System.currentTimeMillis() - inferenceStart
                            metrics.recordInference(inferenceLatency, success = false)
                        } finally {
                            isPartialInferenceBusy.set(false)
                        }
                    }
                }

                // Process VAD segments (speech end detection).
                // Kept synchronous: vad.front() / vad.pop() are not thread-safe and
                // buffer.keepTail() must happen before the next audio chunk is processed.
                while (!vad.empty()) {
                    val segmentStart = System.currentTimeMillis()
                    try {
                        val vadSegment = vad.front()
                        if (vadSegment.samples.isNotEmpty() && vadSegment.samples.all { it.isFinite() }) {
                            // Hold decodeMutex for the entire stream lifecycle (see forced-split
                            // comment above): createStream() and decode() both touch the ONNX
                            // InferenceSession; releaseModel() must not run between them.
                            val trimmedText = decodeMutex.withLock {
                                val r = activeRecognizer ?: return@withLock null
                                val stream = r.createStream()
                                stream.acceptWaveform(vadSegment.samples, SAMPLE_RATE_HZ)
                                r.decode(stream)
                                val result = r.getResult(stream)
                                stream.release()
                                result.text.trim().takeIf { it.isNotBlank() }
                            }
                            val segmentLatency = System.currentTimeMillis() - segmentStart
                            metrics.recordInference(segmentLatency, success = trimmedText != null)
                            if (trimmedText != null) {
                                send(RecognitionResult(trimmedText, isComplete = true))
                                Log.d(TAG, "Final segment (${segmentLatency}ms): ${trimmedText.take(50)}")
                            }
                        }
                        vad.pop()
                    } catch (e: Exception) {
                        Log.e(TAG, "VAD segment processing failed", e)
                        val segmentLatency = System.currentTimeMillis() - segmentStart
                        metrics.recordInference(segmentLatency, success = false)

                        try {
                            vad.pop()
                        } catch (popError: Exception) {
                            Log.e(TAG, "Failed to pop VAD segment", popError)
                            break
                        }
                    }

                    isSpeechStarted = false
                    buffer.keepTail(CONTEXT_CARRY_OVER_SAMPLES)
                    offset = buffer.size
                    lastInferenceOffset = offset
                }
            }

            // Flush: recording stopped while speech was active (no end-of-speech silence).
            // Run one final inference on the remaining buffered audio and emit it as a
            // complete segment so the content is not silently discarded.
            if (isSpeechStarted && offset > speechStartOffset) {
                val cappedStart = maxOf(speechStartOffset, offset - MAX_INFERENCE_AUDIO_SAMPLES)
                val audioSegment = buffer.getRange(cappedStart, offset)
                if (audioSegment.isNotEmpty() && audioSegment.all { it.isFinite() }) {
                    try {
                        // Hold decodeMutex for the entire stream lifecycle (see forced-split
                        // comment above): createStream() and decode() both touch the ONNX
                        // InferenceSession; releaseModel() must not run between them.
                        val trimmedText = decodeMutex.withLock {
                            val r = activeRecognizer ?: return@withLock null
                            val stream = r.createStream()
                            stream.acceptWaveform(audioSegment, SAMPLE_RATE_HZ)
                            r.decode(stream)
                            val result = r.getResult(stream)
                            stream.release()
                            result.text.trim().takeIf { it.isNotBlank() }
                        }
                        if (trimmedText != null) {
                            send(RecognitionResult(trimmedText, isComplete = true))
                            Log.d(TAG, "Flush segment on stop: ${trimmedText.take(50)}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Flush segment inference failed", e)
                    }
                }
            }

            Log.i(TAG, "Processing completed, generating metrics report")
            AudioMetrics.logReport(metrics)
        }

        recordingJob.cancel()
    }

    override suspend fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        deactivateBluetoothSco()

        val recordingDuration = (System.currentTimeMillis() - recordingStartTimeMs) / 1000.0
        Log.i(TAG, "Recording stopped after ${recordingDuration}s")

        // Calculate and log audio drop rate
        val dropRate = metrics.calculateAudioDropRate(recordingDuration)
        if (dropRate > 0.1) {
            Log.w(TAG, "⚠️ Audio drop rate: ${"%.2f".format(dropRate)}% (samples lost!)")
        }
    }

    override suspend fun releaseModel() {
        // Acquire decodeMutex before releasing the native recognizer to prevent use-after-free.
        // JNI decode() calls cannot be cancelled cooperatively by coroutine cancellation:
        // once inside native code the JNI frame runs to completion regardless. If release()
        // frees the ONNX native heap while a decode() JNI call is still executing, the decode
        // reads freed memory → SIGSEGV (fault addr 0x0/0x8 in libonnxruntime decode+128).
        // Holding the mutex here guarantees release() only runs when no decode() is active,
        // which aligns model teardown with the Stop button click as the synchronization point.
        //
        // The Vad is NOT released: provideVad() returns SimulateStreamingAsr.vad (a static
        // singleton), so releasing it would null its native pointer permanently — vadProvider.get()
        // would return the same already-released object, causing a NPE in the next initialize().
        // Vad memory footprint is negligible; it is reset via vad.reset() in initialize().
        decodeMutex.withLock {
            activeRecognizer?.release()
            activeRecognizer = null
        }
        Log.i(TAG, "STT model released from native heap")
    }
}
