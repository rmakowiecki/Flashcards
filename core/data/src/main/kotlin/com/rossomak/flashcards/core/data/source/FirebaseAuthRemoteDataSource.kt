package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.rossomak.flashcards.core.domain.model.AuthUser
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRemoteDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : AuthRemoteDataSource {

    override fun getCurrentUser(): AuthUser? = firebaseAuth.currentUser?.toAuthUser()

    // AuthStateListener fires immediately with the current user on registration, then again on
    // every sign-in/sign-out — a single listener backs both the initial value and later changes.
    override fun observeAuthUser(): Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.toAuthUser()) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

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
                } catch (@Suppress("SwallowedException") collision: FirebaseAuthUserCollisionException) {
                    firebaseAuth.signInWithCredential(credential).await()
                }
            } else {
                firebaseAuth.signInWithCredential(credential).await()
            }
            val user = result.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after sign-in"))
            Result.success(user.toAuthUser())
        } catch (exception: CancellationException) {
            throw exception
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
        } catch (exception: CancellationException) {
            throw exception
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
