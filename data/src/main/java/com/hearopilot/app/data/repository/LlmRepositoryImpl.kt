package com.hearopilot.app.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.hearopilot.app.data.datasource.LlmDataSource
import com.hearopilot.app.data.util.PerfLogger
import com.hearopilot.app.domain.model.LlmSamplerConfig
import com.hearopilot.app.domain.model.ThermalThrottle
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of LlmRepository that bridges data source to domain layer.
 *
 * Uses a single-threaded dispatcher for thread safety (llama.cpp requirement).
 *
 * Stateless inference: resets context (KV-cache) before every call so the
 * system prompt is always fresh and no chat history accumulates. This prevents
 * language drift, repetitive output, and context overflow in small models (2B)
 * that cannot maintain task fidelity across multi-turn conversations.
 *
 * @property llmDataSource Data source for LLM operations
 * @property context Application context for PowerManager access
 */
class LlmRepositoryImpl @Inject constructor(
    private val llmDataSource: LlmDataSource,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : LlmRepository {

    companion object {
        private const val TAG = "LlmRepositoryImpl"

        // Multiplier applied to ActivityManager.MemoryInfo.threshold to compute the free-RAM
        // floor below which we consider the device memory-constrained.
        //
        // `threshold` is the system's own low-memory level for the specific device (typically
        // 150-250 MB on modern Android). Unloading only when availMem < threshold would be
        // reactive (crisis already in progress), so we multiply to be proactive: we want to
        // free the LLM (~1.4 GB footprint) *before* kswapd starts reclaiming its mmap pages.
        //
        // threshold * 3 ≈ 0.45-0.75 GB on typical devices — frees the LLM only when RAM
        // is genuinely scarce (< ~2 GB free). A multiplier of 7 was too aggressive: on
        // high-end OEM devices (e.g. OnePlus) the system threshold can reach 600-700 MB,
        // causing the model to be freed after every batch chunk even with 4+ GB free.
        private const val LOW_MEMORY_THRESHOLD_MULTIPLIER = 3

        // Conservative thread count for long sessions on RAM-constrained devices.
        // 2 threads leave 2+ big cores free for STT, UI events, and system services,
        // eliminating the CPU saturation that causes ANR at the cost of ~1.6x slower inference.
        private const val N_THREADS_CONSERVATIVE = 2

        // Safety margin for the adaptive char threshold: when a constrained inference is
        // observed at X chars, the stored threshold is X * CHARS_THRESHOLD_SAFETY_MARGIN.
        // This means conservative threads are enabled 20% before the observed problem size,
        // giving headroom for variance in transcript length across sessions.
        private const val CHARS_THRESHOLD_SAFETY_MARGIN = 0.8
    }

    // Coroutine scope for async DataStore writes that must not block the LLM dispatcher.
    // Lives for the lifetime of this singleton.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache of adaptive thread config — populated from DataStore at construction
    // and kept in sync by checkAndCacheMemoryConstraint() / recordConstrainedInference().
    // @Volatile ensures visibility across coroutine dispatchers.
    @Volatile private var cachedMemoryConstrained: Boolean = false
    @Volatile private var cachedCharThreshold: Int = Int.MAX_VALUE

    init {
        // Load persisted adaptive state from DataStore at startup so the thread hint is
        // applied correctly on the very first model load without waiting for a ViewModel call.
        repositoryScope.launch {
            val settings = settingsRepository.getSettings().first()
            cachedMemoryConstrained = settings.memoryConstrainedDetected
            cachedCharThreshold = settings.conservativeThreadsCharThreshold
            if (cachedMemoryConstrained) {
                nThreadsHint = N_THREADS_CONSERVATIVE
                Log.i(TAG, "Restored conservative thread mode from DataStore (memoryConstrainedDetected=true)")
            }
        }
    }

    // Single-threaded dispatcher for llama.cpp thread safety.
    // Dispatchers.Default is used instead of IO because LLM inference is pure CPU work —
    // the Default pool is tuned for sustained CPU computation, while IO is for blocking I/O.
    private val singleThreadDispatcher: CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(1)

    // Thread count passed to llama.cpp prepare() on the next model load.
    // -1 = auto (engine picks from CPU count, capped at N_THREADS_MAX=4).
    // N_THREADS_CONSERVATIVE = 2, set via useConservativeThreads() on RAM-constrained
    // devices or when isLargeContext() returns true before a reload.
    // @Volatile ensures cross-dispatcher visibility.
    @Volatile private var nThreadsHint: Int = -1

    // True while generateInsight() is actively collecting tokens on singleThreadDispatcher.
    // @Volatile ensures cross-dispatcher visibility (generateInsight runs on singleThreadDispatcher,
    // isGenerating is read from Main in stopStreaming()).
    @Volatile override var isGenerating: Boolean = false
        private set

    private var isModelLoaded = false
    private var lastModelPath: String? = null
    private var lastSystemPrompt: String? = null

    override suspend fun initialize(
        modelPath: String,
        systemPrompt: String?,
        loadImmediately: Boolean
    ): Result<Unit> = withContext(singleThreadDispatcher) {
        lastModelPath = modelPath
        lastSystemPrompt = systemPrompt
        if (!loadImmediately) {
            // Defer loading — model will be loaded lazily before the first LONG_MEETING inference
            Log.i(TAG, "Model initialization deferred (lazy load mode)")
            return@withContext Result.success(Unit)
        }
        if (isModelLoaded) {
            // Model already loaded — two callers raced (normal init + post-download observer).
            // Treat as success so the second caller does not overwrite state.
            Log.i(TAG, "Model already loaded, skipping duplicate initialization")
            return@withContext Result.success(Unit)
        }
        val threadsDesc = if (nThreadsHint > 0) "$nThreadsHint (conservative)" else "auto (hint=$nThreadsHint)"
        Log.i(TAG, "Model load: threads=$threadsDesc cachedConstrained=$cachedMemoryConstrained charThreshold=$cachedCharThreshold")
        llmDataSource.loadModel(modelPath, systemPrompt, nThreadsHint).also { result ->
            if (result.isSuccess) {
                isModelLoaded = true
                Log.i(TAG, "Model initialized")
            }
        }
    }

    override fun useConservativeThreads() {
        if (nThreadsHint == N_THREADS_CONSERVATIVE) return
        nThreadsHint = N_THREADS_CONSERVATIVE
        Log.i(TAG, "Conservative thread mode enabled: next load will use $N_THREADS_CONSERVATIVE threads")
    }

    override suspend fun checkAndCacheMemoryConstraint() {
        val constrained = isMemoryConstrained()
        if (constrained == cachedMemoryConstrained) {
            // No change — ensure hint is consistent then skip DataStore write.
            if (constrained && nThreadsHint != N_THREADS_CONSERVATIVE) nThreadsHint = N_THREADS_CONSERVATIVE
            return
        }
        cachedMemoryConstrained = constrained
        nThreadsHint = if (constrained) N_THREADS_CONSERVATIVE else -1
        Log.i(TAG, "checkAndCacheMemoryConstraint: constrained=$constrained → nThreadsHint=$nThreadsHint")
        repositoryScope.launch {
            val current = settingsRepository.getSettings().first()
            if (current.memoryConstrainedDetected != constrained) {
                settingsRepository.updateSettings(current.copy(memoryConstrainedDetected = constrained))
            }
        }
    }

    override suspend fun recordConstrainedInference(inputChars: Int) {
        if (!isMemoryConstrained() || inputChars <= 0) return
        // Compute the new threshold with the safety margin applied.
        val newThreshold = (inputChars * CHARS_THRESHOLD_SAFETY_MARGIN).toInt()
        // Converge downward: take the minimum so the threshold tightens over time.
        if (newThreshold >= cachedCharThreshold) return
        cachedCharThreshold = newThreshold
        Log.i(TAG, "recordConstrainedInference: inputChars=$inputChars → new charThreshold=$newThreshold")
        repositoryScope.launch {
            val current = settingsRepository.getSettings().first()
            if (current.conservativeThreadsCharThreshold > newThreshold) {
                settingsRepository.updateSettings(
                    current.copy(conservativeThreadsCharThreshold = newThreshold)
                )
            }
        }
    }

    override fun isLargeContext(inputChars: Int): Boolean {
        val large = inputChars >= cachedCharThreshold
        if (large) {
            Log.i(TAG, "isLargeContext: inputChars=$inputChars >= threshold=$cachedCharThreshold → switching to conservative threads")
        }
        return large
    }

    override suspend fun reloadModel(): Result<Unit> = withContext(singleThreadDispatcher) {
        if (isModelLoaded) {
            // Already loaded — no I/O needed (device has enough free RAM)
            return@withContext Result.success(Unit)
        }
        val path = lastModelPath ?: return@withContext Result.failure(
            IllegalStateException("reloadModel called before initialize")
        )
        val threadsDesc = if (nThreadsHint > 0) "$nThreadsHint (conservative)" else "auto (hint=$nThreadsHint)"
        Log.i(TAG, "Model reload: threads=$threadsDesc cachedConstrained=$cachedMemoryConstrained charThreshold=$cachedCharThreshold")
        llmDataSource.loadModel(path, lastSystemPrompt, nThreadsHint).also { result ->
            if (result.isSuccess) {
                isModelLoaded = true
                Log.i(TAG, "Model reloaded")
            } else {
                Log.e(TAG, "Model reload failed: ${result.exceptionOrNull()}")
            }
        }
    }

    override fun isMemoryConstrained(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val floor = memInfo.threshold * LOW_MEMORY_THRESHOLD_MULTIPLIER
        val constrained = memInfo.availMem < floor
        Log.d(TAG, "isMemoryConstrained: availMem=${memInfo.availMem / 1_048_576}MB " +
            "threshold=${memInfo.threshold / 1_048_576}MB " +
            "floor=${floor / 1_048_576}MB → constrained=$constrained")
        return constrained
    }

    override fun beginInference() { isGenerating = true }

    override fun endInference() { isGenerating = false }

    override fun generateInsight(text: String, systemPrompt: String?, maxTokens: Int): Flow<String> = flow {
        // Stateless inference: reset context before each call so the system
        // prompt is always the dominant signal. The KV-cache clear + system
        // prompt re-encoding (~200 tokens) costs ~50-100ms — negligible vs
        // the 8-15s generation that follows. This eliminates:
        // - Language drift from accumulated English chat history
        // - Repetitive output from pattern reinforcement
        // - Context overflow risk (only system prompt + 1 exchange in context)

        // If the caller provides a system prompt (e.g. GenerateBatchInsightUseCase), use it;
        // otherwise fall back to the last prompt set via updateSystemPrompt / initialize.
        if (systemPrompt != null) {
            lastSystemPrompt = systemPrompt
        }
        llmDataSource.updateSystemPrompt(lastSystemPrompt ?: "")

        val prompt = buildPrompt(text)
        val estimatedTokens = text.length / 4
        val threadsDesc = if (nThreadsHint > 0) "$nThreadsHint (conservative)" else "auto"
        Log.d(TAG, "=== LLM INFERENCE === threads=$threadsDesc inputChars=${text.length}")
        Log.d(TAG, "System prompt:\n${lastSystemPrompt ?: "(none)"}")
        Log.d(TAG, "User prompt:\n$prompt")
        Log.d(TAG, "Tokens: ~$estimatedTokens input + $maxTokens output budget")

        val batteryBefore = PerfLogger.getBatteryPct(context)
        PerfLogger.logSnapshot(context, "llm-start")
        val inferenceStartMs = System.currentTimeMillis()
        var tokenCount = 0

        llmDataSource.sendPrompt(prompt, maxTokens)
            .collect { token ->
                tokenCount++
                emit(token)
            }

        val durationMs = System.currentTimeMillis() - inferenceStartMs
        PerfLogger.logInference(context, durationMs, tokenCount, batteryBefore, PerfLogger.getBatteryPct(context))
    }
    .onCompletion { isGenerating = false }
    .flowOn(singleThreadDispatcher)

    override suspend fun updateSamplerConfig(config: LlmSamplerConfig): Result<Unit> =
        withContext(singleThreadDispatcher) {
            llmDataSource.updateSamplerConfig(config)
        }

    override suspend fun updateSystemPrompt(systemPrompt: String): Result<Unit> =
        withContext(singleThreadDispatcher) {
            lastSystemPrompt = systemPrompt
            if (isModelLoaded) {
                llmDataSource.updateSystemPrompt(systemPrompt).also { result ->
                    if (result.isSuccess) {
                        Log.i(TAG, "System prompt updated successfully")
                    } else {
                        Log.e(TAG, "Failed to update system prompt: ${result.exceptionOrNull()}")
                    }
                }
            } else {
                Log.w(TAG, "Model not loaded, system prompt will be applied on next initialization")
                Result.success(Unit)
            }
        }

    override suspend fun cleanup() {
        withContext(singleThreadDispatcher) {
            if (!isModelLoaded) {
                Log.w(TAG, "cleanup called but model is not loaded, skipping")
                return@withContext
            }
            llmDataSource.cleanup()
            isModelLoaded = false
        }
    }

    /**
     * Emits thermal throttle signals based on [PowerManager] thermal status.
     *
     * minSdk = 34 guarantees the thermal API is always available — no version check needed.
     *
     * When the device reaches THERMAL_STATUS_SEVERE or above, emits [ThermalThrottle.Reduced]
     * with a 1.5x interval multiplier so callers reduce their inference frequency.
     *
     * The listener is automatically unregistered when the flow's collector is cancelled.
     */
    override val thermalThrottleFlow: Flow<ThermalThrottle> = callbackFlow {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Emit initial state immediately
        trySend(ThermalThrottle.Normal)

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            val throttle = when {
                // THERMAL_STATUS_SEVERE = 5, THERMAL_STATUS_CRITICAL = 6, THERMAL_STATUS_EMERGENCY = 7
                // Factor 1.5 extends the inference interval by 50% when hot,
                // balancing thermal protection with UX (2.0 was too aggressive).
                status >= PowerManager.THERMAL_STATUS_SEVERE ->
                    ThermalThrottle.Reduced(factor = 1.5f)
                else -> ThermalThrottle.Normal
            }
            trySend(throttle)
            Log.d(TAG, "Thermal status changed: $status → $throttle")
        }

        powerManager.addThermalStatusListener(listener)
        awaitClose { powerManager.removeThermalStatusListener(listener) }
    }

    private fun buildPrompt(transcription: String): String {
        // The system prompt is held in llama.cpp's KV-cache via updateSystemPrompt()
        // and must NOT be duplicated here — doing so would inflate context usage
        // and cause the model to receive conflicting/doubled instructions.
        return "Transcription: \"$transcription\""
    }
}
