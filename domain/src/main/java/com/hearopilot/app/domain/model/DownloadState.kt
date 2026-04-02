package com.hearopilot.app.domain.model

/**
 * Download progress information.
 *
 * @property bytesDownloaded Number of bytes downloaded so far
 * @property totalBytes Total size of the download in bytes
 * @property percentage Download completion percentage (0-100)
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percentage: Int
)

/**
 * Download state sealed class for type-safe state management.
 *
 * Represents the various states of model downloading process.
 */
sealed class DownloadState {
    /**
     * Initial idle state - ready to start download.
     */
    object Idle : DownloadState()

    /**
     * Download in progress.
     *
     * @property progress Current download progress
     * @property speedMbps Download speed in megabytes per second
     * @property etaSeconds Estimated time to completion in seconds
     */
    data class Downloading(
        val progress: DownloadProgress,
        val speedMbps: Float = 0f,
        val etaSeconds: Int = 0
    ) : DownloadState()

    /**
     * Download completed successfully.
     */
    object Completed : DownloadState()

    /**
     * Download failed with error.
     *
     * @property message Error message describing the failure
     */
    data class Error(val message: String) : DownloadState()
}
