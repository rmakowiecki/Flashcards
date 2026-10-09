package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.isConnectionFailure
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason

/** The Functions SDK wraps a dead network's `IOException` as the cause, so the whole cause chain is searched. */
internal fun Throwable.toBugReportFailureReason(): BugReportFailureReason =
    if (isConnectionFailure()) BugReportFailureReason.NoConnection else BugReportFailureReason.ServiceError
