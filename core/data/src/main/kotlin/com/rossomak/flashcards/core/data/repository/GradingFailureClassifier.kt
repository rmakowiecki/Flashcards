package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.isConnectionFailure
import com.rossomak.flashcards.core.domain.model.GradingFailureReason
import java.io.IOException

/**
 * The streamed grading callable never surfaces a bare [IOException]: the Firebase Functions SDK
 * wraps a failed or timed-out request in its own exception with the [IOException] as the cause, so
 * the whole cause chain is searched. Everything else, including an HTTP 503, an entitlement
 * rejection or a protocol violation, means the service was reached.
 */
internal fun Throwable.toGradingFailureReason(): GradingFailureReason =
    if (isConnectionFailure()) GradingFailureReason.NoConnection else GradingFailureReason.ServiceError
