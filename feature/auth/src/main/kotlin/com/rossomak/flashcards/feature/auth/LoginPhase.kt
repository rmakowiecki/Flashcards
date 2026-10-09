package com.rossomak.flashcards.feature.auth

/** Where the Login screen is in signing the User in. */
sealed interface LoginPhase {
    /** Nothing in flight: the button can start a sign-in. */
    data object Idle : LoginPhase

    /** The account picker is open, or its token is being exchanged for a Firebase sign-in. */
    data object SigningIn : LoginPhase

    /** Sign-in succeeded and the screen is leaving. Never goes back to [Idle]. */
    data object SignedIn : LoginPhase
}
