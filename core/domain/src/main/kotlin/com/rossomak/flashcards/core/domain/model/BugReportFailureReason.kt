package com.rossomak.flashcards.core.domain.model

/**
 * Why a Bug Report was not sent. Non-string per the domain/UI string split: resolved to a string
 * resource only where it is shown. Nothing renders a rate limit or a rejection differently from
 * [ServiceError], so neither has a variant.
 */
sealed interface BugReportFailureReason {

    /** The device is offline or the request timed out. */
    data object NoConnection : BugReportFailureReason

    /** The service was reached but did not accept the report. */
    data object ServiceError : BugReportFailureReason

    /** The description is outside [BugReport]'s limits. Checked on the device, before any request. */
    data object InvalidDescription : BugReportFailureReason
}
