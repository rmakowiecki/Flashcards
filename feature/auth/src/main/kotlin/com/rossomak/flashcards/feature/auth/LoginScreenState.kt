package com.rossomak.flashcards.feature.auth

data class LoginScreenState(
    val isSigningIn: Boolean = false,
    val failureReason: LoginFailureReason? = null,
)
