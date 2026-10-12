package com.rossomak.flashcards.feature.auth

/** One-shot snackbar messages for Login, never screen state. */
sealed interface LoginMessage {
    data class SignInFailed(val reason: LoginFailureReason) : LoginMessage
}
