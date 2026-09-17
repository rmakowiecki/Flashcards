package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getCurrentUser(): AuthUser?

    /** Emits the signed-in user (`null` if signed out) immediately and on every auth change. */
    fun observeAuthUser(): Flow<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>
    suspend fun signInAnonymously(): Result<AuthUser>
    fun signOut()
}
