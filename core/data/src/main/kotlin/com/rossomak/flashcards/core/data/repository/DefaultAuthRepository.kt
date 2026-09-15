package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource,
) : AuthRepository {

    override fun getCurrentUser(): AuthUser? = remoteDataSource.getCurrentUser()

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
        remoteDataSource.signInWithGoogleIdToken(idToken)

    override suspend fun signInAnonymously(): Result<AuthUser> = remoteDataSource.signInAnonymously()

    override fun signOut() = remoteDataSource.signOut()
}
