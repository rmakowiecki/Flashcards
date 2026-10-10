package com.rossomak.flashcards.core.data.repository

import java.io.IOException

/**
 * Maps a failed callable to a feature's own failure reason: [noConnection] when the service was never
 * reached, [serviceError] otherwise. The Firebase Functions SDK never surfaces a bare [IOException]: it
 * wraps a failed or timed-out request in its own exception with the [IOException] as the cause, so the
 * whole cause chain is searched. Everything else, including an HTTP 503, an entitlement rejection or a
 * protocol violation, means the service was reached.
 */
internal fun <Reason> Throwable.toFailureReason(noConnection: Reason, serviceError: Reason): Reason =
    if (isConnectionFailure()) noConnection else serviceError

/** The service was never reached: an [IOException] anywhere in the cause chain. */
internal fun Throwable.isConnectionFailure(): Boolean {
    // Bounded, since a malformed cause chain can loop back on itself.
    val causeChain = generateSequence(this) { throwable -> throwable.cause }.take(MAX_CAUSE_CHAIN_DEPTH)
    return causeChain.any { throwable -> throwable is IOException }
}

private const val MAX_CAUSE_CHAIN_DEPTH = 16
