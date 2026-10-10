package com.rossomak.flashcards.core.data.source

import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInResult
import kotlinx.coroutines.flow.Flow

interface AuthRemoteDataSource {

    fun getCurrentUser(): AuthUser?

    fun observeAuthUser(): Flow<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): SignInResult

    suspend fun signInWithGitHub(): SignInResult

    suspend fun signInAnonymously(): Result<AuthUser>

    fun signOut()
}
