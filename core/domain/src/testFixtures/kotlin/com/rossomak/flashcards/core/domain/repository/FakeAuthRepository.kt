package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AuthUser
import kotlinx.coroutines.delay

class FakeAuthRepository : AuthRepository {
    var userToReturn: AuthUser? = null
    var signInResult: Result<AuthUser> = Result.failure(UnsupportedOperationException("not configured"))
    var signInAnonymouslyResult: Result<AuthUser> = Result.failure(UnsupportedOperationException("not configured"))
    var signInAnonymouslyDelayMs: Long = 0L
    var signInAnonymouslyCallCount: Int = 0

    override fun getCurrentUser(): AuthUser? = userToReturn

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
        signInResult.onSuccess { userToReturn = it }

    override suspend fun signInAnonymously(): Result<AuthUser> {
        signInAnonymouslyCallCount++
        if (signInAnonymouslyDelayMs > 0) delay(signInAnonymouslyDelayMs)
        return signInAnonymouslyResult.onSuccess { userToReturn = it }
    }

    override fun signOut() {
        userToReturn = null
    }
}
