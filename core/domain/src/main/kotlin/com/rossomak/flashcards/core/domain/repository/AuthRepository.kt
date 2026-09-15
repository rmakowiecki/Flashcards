package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AuthUser

interface AuthRepository {
    fun getCurrentUser(): AuthUser?
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>
    suspend fun signInAnonymously(): Result<AuthUser>
    fun signOut()
}
