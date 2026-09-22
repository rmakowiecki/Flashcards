package com.rossomak.flashcards.feature.auth

/**
 * Why Google sign-in failed. Non-string per the domain/UI string split (AGENTS.md, "String
 * Resources") — resolved to a string resource only at the presentation boundary that surfaces it.
 * [SignInFailed.cause] is a raw caught-exception for logging only, never shown to a user.
 *
 * Extends [Exception] (rather than a plain sealed interface, unlike [Throwable]-free reason types
 * elsewhere) solely so it can travel as-is through [GoogleSignInLauncher.launch]'s `Result<String>`
 * failure channel without a separate wrapper exception.
 *
 * Cancellation (the user dismissing the account picker) is not a variant here — it is not an
 * error to report back to the user, so it never reaches [LoginScreenState.failureReason]. See
 * [GoogleSignInLauncher]'s cancellation handling.
 */
sealed class LoginFailureReason : Exception() {
    data object NoAccountOnDevice : LoginFailureReason()
    data class SignInFailed(override val cause: Throwable? = null) : LoginFailureReason()
}
