package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.rossomak.flashcards.core.domain.model.AuthUser
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class FirebaseAuthRemoteDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthRemoteDataSource {

    override fun getCurrentUser(): AuthUser? = firebaseAuth.currentUser?.toAuthUser()

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val guest = firebaseAuth.currentUser?.takeIf { it.isAnonymous }
            // Guest (CONTEXT.md): link so the real account inherits the anonymous uid and everything
            // written under it. A collision (credential already tied to a different existing User —
            // returning user on a new device) means the Guest session is discarded outright, not
            // merged, so fall back to a plain sign-in.
            val result = if (guest != null) {
                try {
                    guest.linkWithCredential(credential).await()
                } catch (collision: FirebaseAuthUserCollisionException) {
                    firebaseAuth.signInWithCredential(credential).await()
                }
            } else {
                firebaseAuth.signInWithCredential(credential).await()
            }
            val user = result.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after sign-in"))
            Result.success(user.toAuthUser())
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun signInAnonymously(): Result<AuthUser> {
        return try {
            val result = firebaseAuth.signInAnonymously().await()
            val user = result.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after anonymous sign-in"))
            Result.success(user.toAuthUser())
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        isAnonymous = isAnonymous,
    )
}
