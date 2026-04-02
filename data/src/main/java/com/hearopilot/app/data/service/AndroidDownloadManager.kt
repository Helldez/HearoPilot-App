package com.hearopilot.app.data.service

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.hearopilot.app.data.config.DefaultModelConfig
import com.hearopilot.app.data.config.LowEndModelConfig
import com.hearopilot.app.data.config.ModelConfig
import com.hearopilot.app.data.config.modelConfigForVariant
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.domain.model.DownloadProgress
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.LlmModelVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Action constants duplicated here to avoid a direct dependency on the :app module.
// The :data module must not reference app-layer classes such as ModelDownloadService.
private const val MODEL_DOWNLOAD_SERVICE_CLASS =
    "com.hearopilot.app.service.ModelDownloadService"
private const val ACTION_START_DOWNLOAD = "com.hearopilot.app.action.START_DOWNLOAD"
private const val ACTION_STOP_DOWNLOAD = "com.hearopilot.app.action.STOP_DOWNLOAD"

/**
 * Manager for model downloads.
 *
 * Both STT and LLM downloads use ModelDownloadManager (HttpURLConnection) directly:
 * - No Android DownloadManager API is used for new downloads, avoiding the
 *   MediaProvider restriction that blocks writes to private app directories on
 *   Android 12+ ("Inserting private file … is not allowed").
 * - ModelDownloadManager preserves .partial files on any failure so that every
 *   retry resumes byte-accurately instead of restarting from zero.
 *
 * Legacy Android DownloadManager entries (from previous app versions) are cleaned
 * up in resumePendingDownloads() without starting new DM downloads.
 */
@Singleton
class AndroidDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadStateManager: DownloadStateManager,
    private val modelDownloadManager: ModelDownloadManager
) {
    companion object {
        private const val TAG = "AndroidDownloadManager"

        // SharedPreferences keys kept only for cleaning up legacy DM entries
        private const val PREFS_NAME = "download_manager_prefs"
        private const val KEY_STT_DOWNLOAD_IDS = "stt_download_ids"
        private const val KEY_STT_CURRENT_FILE_INDEX = "stt_current_file_index"
        private const val KEY_LLM_DOWNLOAD_ID = "llm_download_id"
        private const val NO_DOWNLOAD_ID = -1L
    }

    // Kept only to cancel/clean up any legacy DM downloads on first run after update
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO)
    private var sttProgressJob: Job? = null
    private var llmProgressJob: Job? = null

    // Tracks a legacy DM download ID so resumeLlmDownload() can remove it before renaming
    private var llmDownloadId: Long? = null

    init {
        resumePendingDownloads()
    }

    // ── Foreground-service helpers ───────────────────────────────────────────

    /**
     * Starts [ModelDownloadService] as a foreground service so downloads survive
     * the app being backgrounded.
     *
     * Uses reflection-free string-based intent to avoid a circular :data → :app
     * dependency; the service class name is a stable internal constant.
     */
    private fun startDownloadService() {
        try {
            val serviceClass = Class.forName(MODEL_DOWNLOAD_SERVICE_CLASS)
            val intent = Intent(context, serviceClass).apply {
                action = ACTION_START_DOWNLOAD
            }
            context.startForegroundService(intent)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "ModelDownloadService not found — foreground protection unavailable", e)
        }
    }

    /**
     * Stops [ModelDownloadService] when no download is active.
     * Safe to call even if the service is already stopped.
     */
    private fun stopDownloadService() {
        try {
            val serviceClass = Class.forName(MODEL_DOWNLOAD_SERVICE_CLASS)
            val intent = Intent(context, serviceClass).apply {
                action = ACTION_STOP_DOWNLOAD
            }
            context.startService(intent)
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "ModelDownloadService not found — cannot send stop intent", e)
        }
    }

    /**
     * Cancel any legacy Android DownloadManager entries persisted by a previous app version,
     * and restore a resumable error state if a .partial LLM file exists on disk.
     */
    private fun resumePendingDownloads() {
        // Clean up legacy STT DM entry
        val savedSttId = prefs.getLong(KEY_STT_DOWNLOAD_IDS, NO_DOWNLOAD_ID)
        if (savedSttId != NO_DOWNLOAD_ID) {
            Log.i(TAG, "Removing legacy STT DM entry id=$savedSttId")
            downloadManager.remove(savedSttId)
            prefs.edit().remove(KEY_STT_DOWNLOAD_IDS).remove(KEY_STT_CURRENT_FILE_INDEX).apply()
        }

        // Clean up legacy LLM DM entry; remember the ID so resumeLlmDownload() can use it
        val savedLlmId = prefs.getLong(KEY_LLM_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (savedLlmId != NO_DOWNLOAD_ID) {
            Log.i(TAG, "Found legacy LLM DM entry id=$savedLlmId")
            llmDownloadId = savedLlmId
            prefs.edit().remove(KEY_LLM_DOWNLOAD_ID).apply()
        }

        // If there is a .partial LLM file for any known variant from an interrupted download,
        // restore an error state so the user can tap Retry to resume without restarting from zero.
        restorePartialLlmState(DefaultModelConfig.INSTANCE)
        restorePartialLlmState(LowEndModelConfig.INSTANCE)
    }

    /**
     * Restores a [DownloadState.Error] for [config]'s LLM variant if a .partial file exists
     * and the model is not yet fully downloaded. Only the first partial found sets state —
     * subsequent calls for an already-set error state are no-ops.
     */
    private fun restorePartialLlmState(config: ModelConfig) {
        if (modelDownloadManager.isLlmModelDownloaded(config.llmFilename)) return
        val partialFile = File(
            context.getExternalFilesDir(null),
            "models/${config.llmFilename}.partial"
        )
        if (partialFile.exists() && partialFile.length() > 0) {
            val mb = partialFile.length() / 1_000_000
            Log.i(TAG, "Found interrupted LLM partial (${config.llmFilename}, ${mb}MB), restoring error state")
            downloadStateManager.updateLlmState(
                DownloadState.Error("Download interrupted at ${mb}MB. Tap retry to resume.")
            )
        }
    }

    // ── STT ─────────────────────────────────────────────────────────────────

    /**
     * Start STT model download via ModelDownloadManager (HttpURLConnection).
     *
     * Downloads all 4 files sequentially with aggregated progress. Uses
     * ModelDownloadManager instead of Android DownloadManager to avoid the
     * MediaProvider private-path restriction on Android 12+.
     */
    fun startSttDownload() {
        Log.i(TAG, "Starting STT download via ModelDownloadManager")
        sttProgressJob?.cancel()

        downloadStateManager.updateSttState(
            DownloadState.Downloading(progress = DownloadProgress(0, 0, 0))
        )

        // Elevate to foreground so the download survives backgrounding.
        startDownloadService()

        sttProgressJob = scope.launch {
            try {
                modelDownloadManager.downloadSttModel().collect { p ->
                    downloadStateManager.updateSttState(
                        DownloadState.Downloading(
                            DownloadProgress(p.bytesDownloaded, p.totalBytes, p.percentage)
                        )
                    )
                }
                downloadStateManager.updateSttState(DownloadState.Completed)
            } catch (e: Exception) {
                Log.e(TAG, "STT download failed", e)
                downloadStateManager.updateSttState(
                    DownloadState.Error(e.message ?: "Download failed")
                )
            }
            // The service observes DownloadStateManager and stops itself when both
            // downloads become idle; no explicit stop needed here.
        }
    }

    /** Cancel an in-progress STT download. */
    fun cancelSttDownload() {
        sttProgressJob?.cancel()
        downloadStateManager.resetSttState()
        // State reset above triggers the service observer → service stops itself.
    }

    // ── LLM ─────────────────────────────────────────────────────────────────

    /**
     * Start LLM model download for the given [variant] via ModelDownloadManager
     * (HttpURLConnection + Range headers).
     *
     * ModelDownloadManager auto-detects a .partial file from a previous interrupted
     * download and resumes from that byte offset, so this function also covers the
     * "fresh start that silently resumes" case.
     *
     * @param variant The model variant to download; defaults to Q8_0 for backward compatibility.
     */
    fun startLlmDownload(variant: LlmModelVariant = LlmModelVariant.Q8_0) {
        val config = modelConfigForVariant(variant)
        Log.i(TAG, "Starting LLM download: ${config.llmFilename}")
        llmProgressJob?.cancel()

        downloadStateManager.updateLlmState(
            DownloadState.Downloading(progress = DownloadProgress(0, 0, 0))
        )

        // Elevate to foreground so the download survives backgrounding.
        startDownloadService()

        llmProgressJob = scope.launch {
            try {
                modelDownloadManager.downloadLlmModel(
                    url = config.llmUrl,
                    filename = config.llmFilename
                ).collect { p ->
                    downloadStateManager.updateLlmState(
                        DownloadState.Downloading(
                            DownloadProgress(p.bytesDownloaded, p.totalBytes, p.percentage)
                        )
                    )
                }
                downloadStateManager.updateLlmState(DownloadState.Completed)
            } catch (e: Exception) {
                Log.e(TAG, "LLM download failed, .partial file kept for resume", e)
                downloadStateManager.updateLlmState(
                    DownloadState.Error(e.message ?: "Download failed")
                )
            }
            // The service observes DownloadStateManager and stops itself when both
            // downloads become idle; no explicit stop needed here.
        }
    }

    /**
     * Resume LLM download on retry for the given [variant].
     *
     * Checks for a .partial file (kept by ModelDownloadManager on any failure) and
     * resumes byte-accurately via Range headers. Also handles the legacy .download_*
     * temp file left by the Android DownloadManager in previous app versions,
     * renaming it to .partial before calling DM.remove() so it is not deleted.
     *
     * ModelDownloadManager.downloadLlmModel() detects the .partial automatically,
     * so starting the download here is sufficient for all retry scenarios.
     *
     * @param variant The model variant to resume; defaults to Q8_0 for backward compatibility.
     */
    fun resumeLlmDownload(variant: LlmModelVariant = LlmModelVariant.Q8_0) {
        val config = modelConfigForVariant(variant)
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        val dmTempFile = File(modelsDir, ".download_${config.llmFilename}")
        val partialFile = File(modelsDir, "${config.llmFilename}.partial")

        // Rename DM temp file to .partial BEFORE calling downloadManager.remove(),
        // which would otherwise delete it by the path it stored internally.
        val partialBytes: Long = if (dmTempFile.exists() && dmTempFile.length() > 0) {
            val renamed = dmTempFile.renameTo(partialFile)
            if (!renamed) {
                try { dmTempFile.copyTo(partialFile, overwrite = true) } catch (_: Exception) {}
            }
            if (partialFile.exists()) partialFile.length() else 0L
        } else {
            partialFile.takeIf { it.exists() }?.length() ?: 0L
        }

        // Remove legacy DM entry (temp file already renamed, so remove() cannot delete it)
        llmDownloadId?.let { downloadManager.remove(it) }
        llmProgressJob?.cancel()
        llmDownloadId = null

        if (partialBytes > 0) {
            Log.i(TAG, "Resuming LLM from ${partialBytes / 1_000_000}MB partial file")
        } else {
            Log.i(TAG, "No partial LLM data found, starting fresh (resume-capable on next failure)")
        }

        // Emit initial state so the UI can show "Resuming from X MB" before the first
        // progress update arrives (only meaningful when partialBytes > 0).
        // Note: we launch the coroutine directly instead of calling startLlmDownload()
        // to avoid that function overwriting this initial state with DownloadProgress(0,0,0).
        downloadStateManager.updateLlmState(
            DownloadState.Downloading(DownloadProgress(partialBytes, 0, 0))
        )

        // Elevate to foreground so the resumed download survives backgrounding.
        startDownloadService()

        llmProgressJob = scope.launch {
            try {
                modelDownloadManager.downloadLlmModel(
                    url = config.llmUrl,
                    filename = config.llmFilename
                ).collect { p ->
                    downloadStateManager.updateLlmState(
                        DownloadState.Downloading(
                            DownloadProgress(p.bytesDownloaded, p.totalBytes, p.percentage)
                        )
                    )
                }
                downloadStateManager.updateLlmState(DownloadState.Completed)
            } catch (e: Exception) {
                Log.e(TAG, "LLM resume failed, .partial file kept for next retry", e)
                downloadStateManager.updateLlmState(
                    DownloadState.Error(e.message ?: "Download failed")
                )
            }
            // The service observes DownloadStateManager and stops itself when both
            // downloads become idle; no explicit stop needed here.
        }
    }

    /** Cancel an in-progress LLM download. */
    fun cancelLlmDownload() {
        llmProgressJob?.cancel()
        llmDownloadId?.let { downloadManager.remove(it) }
        llmDownloadId = null
        downloadStateManager.resetLlmState()
        // State reset above triggers the service observer → service stops itself.
    }

    /** Cancel all downloads and release resources. */
    fun cleanup() {
        sttProgressJob?.cancel()
        llmProgressJob?.cancel()
    }
}
