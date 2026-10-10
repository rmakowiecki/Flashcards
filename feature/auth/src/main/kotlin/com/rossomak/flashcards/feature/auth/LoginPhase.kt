package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.core.domain.model.AuthProvider

/** Where the Login screen is in signing the User in. */
sealed interface LoginPhase {
    /** Nothing in flight: the buttons can start a sign-in. */
    data object Idle : LoginPhase

    /** The [provider] whose sign-in is running: its account picker or Custom Tab is open, or Firebase is signing in. */
    data class SigningIn(val provider: AuthProvider) : LoginPhase

    /**
     * Sign-in with [provider] succeeded and the screen is leaving. Never goes back to [Idle]. That provider's button
     * keeps the signing-in label while the screen leaves.
     */
    data class SignedIn(val provider: AuthProvider) : LoginPhase
}
