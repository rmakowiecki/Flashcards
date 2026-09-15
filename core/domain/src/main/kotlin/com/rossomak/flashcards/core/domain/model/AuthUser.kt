package com.rossomak.flashcards.core.domain.model

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    // Firebase Anonymous Auth session, pre-link (see docs/temp/onboarding-before-login-spec.md).
    // Defaults to false so existing call sites naming a real signed-in user are unaffected.
    val isAnonymous: Boolean = false,
)
