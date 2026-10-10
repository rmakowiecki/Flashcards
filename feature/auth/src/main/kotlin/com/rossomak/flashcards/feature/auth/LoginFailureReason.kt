package com.rossomak.flashcards.feature.auth

/**
 * Why a sign-in failed. Non-string per the domain/UI string split (AGENTS.md, "String
 * Resources") — resolved to a string resource only at the presentation boundary that surfaces it.
 *
 * Cancellation (the user dismissing the account picker) is not a variant here — it is not an
 * error to report back to the user. See [isGoogleSignInCancellation].
 */
sealed interface LoginFailureReason {
    data object NoConnection : LoginFailureReason
    data object NoCredentialAvailable : LoginFailureReason
    data object Unknown : LoginFailureReason
}
