package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRemoteDataSource {

    fun getCurrentUser(): AuthUser?

    fun observeAuthUser(): Flow<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>

    suspend fun signInAnonymously(): Result<AuthUser>

    fun signOut()
}
