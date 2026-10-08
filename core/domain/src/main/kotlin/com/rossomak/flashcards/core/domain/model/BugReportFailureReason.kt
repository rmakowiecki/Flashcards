package com.rossomak.flashcards.core.domain.model

/** Why a Bug Report was not sent. */
sealed interface BugReportFailureReason {

    /** Offline or timed out. */
    data object NoConnection : BugReportFailureReason

    data object ServiceError : BugReportFailureReason

    /** Checked on the device, before any request. */
    data object InvalidDescription : BugReportFailureReason
}
