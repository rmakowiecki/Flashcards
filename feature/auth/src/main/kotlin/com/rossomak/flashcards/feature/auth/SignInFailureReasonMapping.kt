package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.domain.model.SignInFailureReason
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown

/** Offline wins, as for the account picker: the connection is the more actionable problem. */
internal fun SignInFailureReason.toLoginFailureReason(isInternetAvailable: Boolean): LoginFailureReason = when {
    !isInternetAvailable -> NoConnection
    else -> when (this) {
        SignInFailureReason.NoConnection -> NoConnection
        SignInFailureReason.Unknown -> Unknown
    }
}
