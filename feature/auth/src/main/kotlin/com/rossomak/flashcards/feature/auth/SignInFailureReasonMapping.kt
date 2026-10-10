package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import com.rossomak.flashcards.core.domain.model.SignInFailureReason
import com.rossomak.flashcards.feature.auth.LoginFailureReason.AccountExistsWithDifferentProvider
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown

/**
 * Offline wins, as for the account picker: the connection is the more actionable problem. An email clash points to
 * the provider other than [attemptedProvider].
 */
internal fun SignInFailureReason.toLoginFailureReason(
    isInternetAvailable: Boolean,
    attemptedProvider: AuthProvider,
): LoginFailureReason = when {
    !isInternetAvailable -> NoConnection
    else -> when (this) {
        SignInFailureReason.NoConnection -> NoConnection
        SignInFailureReason.AccountExistsWithDifferentProvider ->
            AccountExistsWithDifferentProvider(existingProvider = attemptedProvider.other())
        SignInFailureReason.Unknown -> Unknown
    }
}

private fun AuthProvider.other(): AuthProvider = when (this) {
    Google -> GitHub
    GitHub -> Google
}
