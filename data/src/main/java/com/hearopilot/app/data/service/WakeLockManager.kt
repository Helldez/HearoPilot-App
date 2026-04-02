package com.hearopilot.app.data.service

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton manager for wake lock handling during recording.
 *
 * Purpose:
 * - Keeps CPU active during LLM inference when screen is off
 * - Prevents system from sleeping during transcription and processing
 *
 * Safety:
 * - No fixed timeout: the WakeLock is always released explicitly via releaseWakeLock().
 *   RecordingService calls releaseWakeLock() in both stopRecording() and onDestroy(),
 *   guaranteeing release even if the service is killed. A 10-minute timeout caused
 *   crashes on sessions longer than 10 minutes because AudioRecord entered an invalid
 *   state (ERROR_DEAD_OBJECT) after the CPU was allowed to sleep mid-recording.
 * - PARTIAL_WAKE_LOCK: CPU stays on, screen can turn off
 */
@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WakeLockManager"
        private const val WAKE_LOCK_TAG = "Libellula::RecordingWakeLock"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Acquires a partial wake lock to keep CPU active.
     * Safe to call multiple times - only acquires if not already held.
     */
    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "Wake lock already held")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            // No timeout: released explicitly by releaseWakeLock() in RecordingService.
            // Both stopRecording() and onDestroy() call releaseWakeLock(), so the lock
            // is always freed even if the service is killed by the system.
            acquire()
            Log.d(TAG, "Wake lock acquired (no timeout)")
        }
    }

    /**
     * Releases the wake lock if held.
     * Safe to call multiple times.
     */
    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
            wakeLock = null
        }
    }

    /**
     * Checks if wake lock is currently held.
     *
     * @return true if wake lock is held, false otherwise
     */
    fun isHeld(): Boolean {
        return wakeLock?.isHeld == true
    }
}
