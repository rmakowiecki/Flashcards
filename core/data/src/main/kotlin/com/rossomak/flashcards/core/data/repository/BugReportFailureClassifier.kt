package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.domain.model.BugReportFailureReason

/**
 * A dead network reaches the callable as the Functions SDK's own exception with an `IOException`
 * as the cause, so [isConnectionFailure] searches the cause chain. Everything else means the
 * service was reached; no `FirebaseFunctionsException` code is inspected.
 */
internal fun Throwable.toBugReportFailureReason(): BugReportFailureReason =
    if (isConnectionFailure()) BugReportFailureReason.NoConnection else BugReportFailureReason.ServiceError
