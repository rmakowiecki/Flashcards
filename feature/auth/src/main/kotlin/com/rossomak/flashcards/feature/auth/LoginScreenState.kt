package com.rossomak.flashcards.feature.auth

import com.rossomak.flashcards.feature.auth.LoginPhase.Idle

data class LoginScreenState(
    val phase: LoginPhase = Idle,
)
