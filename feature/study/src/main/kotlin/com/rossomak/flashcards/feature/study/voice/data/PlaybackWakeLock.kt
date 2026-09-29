package com.rossomak.flashcards.feature.study.voice.data

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the CPU awake while the session's voice player plays, including the silent pauses between
 * parts, which the study session coordinator times with coroutine delays that do not advance while
 * the CPU sleeps. What Media3's `ExoPlayer` does with `setWakeMode`, which a custom player does not
 * get. Separate from the capture lock of [VoiceCaptureSession].
 */
internal class PlaybackWakeLock(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null

    // Not reference-counted: acquire(timeout) on a held lock only resets its auto-release deadline, so
    // each call renews it, while the timeout still caps the damage if a release is ever missed.
    fun acquire() {
        val lock = wakeLock ?: run {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
        }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private companion object {
        const val WAKE_LOCK_TAG = "flashcards:readAloud"
        const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L // renewed on every playing state update
    }
}
