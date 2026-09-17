package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AuthUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {
    private val authUser = MutableStateFlow<AuthUser?>(null)

    var userToReturn: AuthUser?
        get() = authUser.value
        set(value) {
            authUser.value = value
        }
    var signInResult: Result<AuthUser> = Result.failure(UnsupportedOperationException("not configured"))
    var signInAnonymouslyResult: Result<AuthUser> = Result.failure(UnsupportedOperationException("not configured"))
    var signInAnonymouslyDelayMs: Long = 0L
    var signInAnonymouslyCallCount: Int = 0

    override fun getCurrentUser(): AuthUser? = userToReturn

    override fun observeAuthUser(): Flow<AuthUser?> = authUser

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
