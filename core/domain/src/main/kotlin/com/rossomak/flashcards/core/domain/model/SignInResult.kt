package com.rossomak.flashcards.core.domain.model

/** Not a `Result<AuthUser>`: the failure is a sealed type, not a `Throwable`. */
sealed interface SignInResult {

    data class SignedIn(val user: AuthUser) : SignInResult

    /** The User backed out of the provider's own sign-in UI: not a failure to report. */
    data object Cancelled : SignInResult

    data class Failed(val reason: SignInFailureReason) : SignInResult
}
