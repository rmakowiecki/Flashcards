package com.rossomak.flashcards.core.domain.model

/**
 * Why a sign-in failed. Non-string per the domain/UI string split (AGENTS.md, "String Resources"):
 * resolved to a string resource only at the presentation boundary that surfaces it.
 */
sealed interface SignInFailureReason {

    /** The sign-in service was not reached: the device is offline, or the request failed at the network level. */
    data object NoConnection : SignInFailureReason

    /** Any other failure. */
    data object Unknown : SignInFailureReason
}
