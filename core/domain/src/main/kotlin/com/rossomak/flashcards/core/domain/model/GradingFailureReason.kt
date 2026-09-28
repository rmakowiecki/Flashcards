package com.rossomak.flashcards.core.domain.model

/**
 * Why a spoken answer could not be transcribed or graded. Non-string per the domain/UI string
 * split (AGENTS.md, "String Resources"): resolved to a string resource only where it is shown or
 * spoken.
 */
sealed interface GradingFailureReason {

    /** The grading service could not be reached: the device is offline or the request timed out. */
    data object NoConnection : GradingFailureReason

    /** The grading service was reached but did not return a grade. */
    data object ServiceError : GradingFailureReason
}
