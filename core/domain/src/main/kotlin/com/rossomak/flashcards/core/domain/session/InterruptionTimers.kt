package com.rossomak.flashcards.core.domain.session

import com.rossomak.flashcards.core.domain.model.InterruptionEpisode
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The timers behind a session coordinator's audio interruptions: the blip that may outlast its
 * grace period, and the tail the capture gate stays closed after a blip ends
 * ([InterruptionEpisode.CAPTURE_GATE_TAIL]). Starting either again restarts its wait. The reducers
 * decide when to start and cancel; this only keeps the time.
 */
internal class InterruptionTimers(
    private val onBlipElapsed: () -> Unit,
    private val onGateTailElapsed: () -> Unit = {},
) {
    private var blipJob: Job? = null
    private var gateTailJob: Job? = null

    fun startBlip(scope: CoroutineScope, duration: Duration) {
        blipJob?.cancel()
        blipJob = scope.launch {
            delay(duration)
            onBlipElapsed()
        }
    }

    fun cancelBlip() {
        blipJob?.cancel()
    }

    fun startGateTail(scope: CoroutineScope) {
        gateTailJob?.cancel()
        gateTailJob = scope.launch {
            delay(InterruptionEpisode.CAPTURE_GATE_TAIL)
            onGateTailElapsed()
        }
    }

    fun cancelGateTail() {
        gateTailJob?.cancel()
    }

    fun cancelAll() {
        cancelBlip()
        cancelGateTail()
    }
}
