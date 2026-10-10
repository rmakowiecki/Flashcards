package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getCurrentUser(): AuthUser?

    /** Emits the signed-in user (`null` if signed out) immediately and on every auth change. */
    fun observeAuthUser(): Flow<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): SignInResult
    suspend fun signInAnonymously(): Result<AuthUser>
    fun signOut()
}
