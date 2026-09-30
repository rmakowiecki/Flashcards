package com.rossomak.flashcards.feature.study.voice.data

import android.content.Context
import android.os.PowerManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Keeps the CPU awake while the session's voice player plays, including the silent pauses between
 * parts, which the study session coordinator times with coroutine delays that do not advance while
 * the CPU sleeps. What Media3's `ExoPlayer` does with `setWakeMode`, which a custom player does not
 * get. Separate from the capture lock of [VoiceCaptureSession].
 */
internal class PlaybackWakeLock(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Holds the lock while [isPlaying] is true and releases it when false. A state flow drops
     * repeated equal values, so continuous play emits `true` once; the lock is renewed on a timer
     * instead, every [RENEW_INTERVAL] and well inside [WAKE_LOCK_TIMEOUT].
     */
    suspend fun holdWhile(isPlaying: Flow<Boolean>) = isPlaying.renewWhileTrue(RENEW_INTERVAL, ::acquire, ::release)

    // Not reference-counted: acquire(timeout) on a held lock only resets its auto-release deadline, so
    // each call renews it, while the timeout still caps the damage if a release is ever missed.
    private fun acquire() {
        val lock = wakeLock ?: run {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
        }
        lock.acquire(WAKE_LOCK_TIMEOUT.inWholeMilliseconds)
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private companion object {
        const val WAKE_LOCK_TAG = "flashcards:readAloud"
        val WAKE_LOCK_TIMEOUT = 10.minutes
        val RENEW_INTERVAL = 5.minutes
    }
}

/**
 * Calls [acquire] now and again every [renewInterval] while the flow's latest value is true, and
 * [release] each time it turns false. Cancelling the collector does not release; the owner does.
 */
internal suspend fun Flow<Boolean>.renewWhileTrue(
    renewInterval: Duration,
    acquire: () -> Unit,
    release: () -> Unit,
) = distinctUntilChanged().collectLatest { isTrue ->
    if (isTrue) {
        while (true) {
            acquire()
            delay(renewInterval)
        }
    } else {
        release()
    }
}
