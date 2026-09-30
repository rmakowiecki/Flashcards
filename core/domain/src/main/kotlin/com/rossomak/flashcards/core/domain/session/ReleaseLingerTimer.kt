package com.rossomak.flashcards.core.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The timer behind a session coordinator's release linger: after an advance hold is released, the
 * session stays on the held card for [RELEASE_LINGER], then [onElapsed] reports it. Starting again
 * restarts the wait. The reducers decide when to start and cancel; this only keeps the time.
 */
internal class ReleaseLingerTimer(private val onElapsed: () -> Unit) {

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            delay(RELEASE_LINGER)
            onElapsed()
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
