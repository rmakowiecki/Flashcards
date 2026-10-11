package com.rossomak.flashcards.core.domain.model

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean = false, // Firebase Anonymous Auth session
    /** Null when no supported sign-in provider is linked, as for a Guest. */
    val provider: AuthProvider? = null,
)
