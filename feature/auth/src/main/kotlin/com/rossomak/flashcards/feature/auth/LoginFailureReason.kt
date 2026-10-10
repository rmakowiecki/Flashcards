package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.domain.model.AuthProvider

/**
 * Why a sign-in failed. Non-string per the domain/UI string split (AGENTS.md, "String
 * Resources") — resolved to a string resource only at the presentation boundary that surfaces it.
 *
 * Cancellation (the user dismissing the account picker or closing GitHub's Custom Tab) is not a
 * variant here — it is not an error to report back to the user. See [isGoogleSignInCancellation].
 */
sealed interface LoginFailureReason {
    data object NoConnection : LoginFailureReason
    data object NoCredentialAvailable : LoginFailureReason
    data object Unknown : LoginFailureReason

    /**
     * The provider's email already belongs to a User who signs in with [existingProvider]. That is always the
     * provider the User did not tap: only two exist, and Firebase's email-enumeration protection hides which one
     * the email uses.
     */
    data class AccountExistsWithDifferentProvider(val existingProvider: AuthProvider) : LoginFailureReason
}
