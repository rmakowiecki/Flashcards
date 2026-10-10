package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.isConnectionFailure
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
