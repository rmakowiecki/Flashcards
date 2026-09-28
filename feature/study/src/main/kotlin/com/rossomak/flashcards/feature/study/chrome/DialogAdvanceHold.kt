package com.rossomak.flashcards.feature.study.chrome

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a study-session dialog does to playback: it never pauses it. While a dialog is open the
 * session holds at its auto-advance point instead; once the dialog closes, a held session keeps the
 * old card on screen for [CLOSED_DIALOG_LINGER] before moving on. The coordinator decides what the
 * hold means against every other command: a pause, play or card change resolves it, and the late
 * release is then a no-op.
 */
internal class DialogAdvanceHold(
    private val scope: CoroutineScope,
    private val holdAdvance: () -> Unit,
    private val releaseAdvance: () -> Unit,
    private val isHeldAtAdvancePoint: () -> Boolean,
) {
    private var lingerJob: Job? = null

    /** A linger still pending from a dialog just closed is dropped, so its release cannot advance under this one. */
    fun onDialogOpen() {
        lingerJob?.cancel()
        holdAdvance()
    }

    fun onDialogClose() {
        lingerJob?.cancel()
        if (isHeldAtAdvancePoint()) {
            lingerJob = scope.launch {
                delay(CLOSED_DIALOG_LINGER)
                releaseAdvance()
            }
        } else {
            releaseAdvance()
        }
    }

    fun cancel() {
        lingerJob?.cancel()
    }

    private companion object {
        val CLOSED_DIALOG_LINGER = 500.milliseconds
    }
}
