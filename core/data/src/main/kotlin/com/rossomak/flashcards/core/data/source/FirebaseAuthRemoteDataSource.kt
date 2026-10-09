package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GithubAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.Unknown
import com.rossomak.flashcards.core.domain.model.SignInResult
import com.rossomak.flashcards.core.domain.model.SignInResult.Failed
import com.rossomak.flashcards.core.domain.model.SignInResult.SignedIn
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

    override suspend fun signInWithGoogleIdToken(idToken: String): SignInResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return signInOrLinkGuest(
            signIn = { firebaseAuth.signInWithCredential(credential).await() },
            linkGuest = { guest -> guest.linkWithCredential(credential).await() },
            signInAfterCredentialInUse = { firebaseAuth.signInWithCredential(credential).await() },
        )
    }

    /**
     * Guest (CONTEXT.md): a Guest is linked, so the real account inherits the anonymous uid and
     * everything written under it. A link that fails because the sign-in already belongs to a
     * different existing User (a returning User on a new device) discards the Guest session outright,
     * not merged, and falls back to [signInAfterCredentialInUse]. Any other collision is a failure, and
     * the Guest stays signed in as a Guest. The link and its fallback live in [linkGuestOrSignIn].
     */
    private suspend fun signInOrLinkGuest(
        signIn: suspend () -> AuthResult,
        linkGuest: suspend (guest: FirebaseUser) -> AuthResult,
        signInAfterCredentialInUse: suspend (collision: FirebaseAuthUserCollisionException) -> AuthResult,
    ): SignInResult = try {
        val guest = firebaseAuth.currentUser?.takeIf { it.isAnonymous }
        val result = when (guest) {
            null -> signIn()
            else -> linkGuestOrSignIn(guest, linkGuest, signInAfterCredentialInUse)
        }
        when (val user = result.user) {
            null -> {
                loge { "Sign-in failed: Firebase user was null after sign-in" }
                Failed(Unknown)
            }
            else -> SignedIn(user.toAuthUser())
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        loge(exception) { "Sign-in failed" }
        exception.toSignInResult()
    }

    private suspend fun linkGuestOrSignIn(
        guest: FirebaseUser,
        linkGuest: suspend (guest: FirebaseUser) -> AuthResult,
        signInAfterCredentialInUse: suspend (collision: FirebaseAuthUserCollisionException) -> AuthResult,
    ): AuthResult = try {
        linkGuest(guest)
    } catch (collision: FirebaseAuthUserCollisionException) {
        if (collision.errorCode != ERROR_CREDENTIAL_ALREADY_IN_USE) throw collision
        signInAfterCredentialInUse(collision)
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

    /**
     * Linking a Guest to a sign-in provider can leave the user's own `displayName`, `email` and
     * `photoUrl` empty while the provider entry holds them, so each field falls back to that entry. A
     * blank value counts as empty.
     */
    private fun FirebaseUser.toAuthUser(): AuthUser {
        val providerProfile = providerData.firstOrNull { it.providerId in SIGN_IN_PROVIDERS }
        val ownPhotoUrl = photoUrl?.toString()?.takeUnless(String::isBlank)
        val providerPhotoUrl = providerProfile?.photoUrl?.toString()?.takeUnless(String::isBlank)
        return AuthUser(
            uid = uid,
            email = email?.takeUnless(String::isBlank) ?: providerProfile?.email?.takeUnless(String::isBlank),
            displayName = displayName?.takeUnless(String::isBlank)
                ?: providerProfile?.displayName?.takeUnless(String::isBlank),
            photoUrl = (ownPhotoUrl ?: providerPhotoUrl)?.withSharperPhoto(),
            isAnonymous = isAnonymous,
            provider = providerProfile?.let { profile -> SIGN_IN_PROVIDERS[profile.providerId] },
        )
    }

    /**
     * Google serves profile photos at 96 px by default, which is blurry on a larger avatar, so a
     * smaller size suffix is raised to [PHOTO_SIZE_PX]. A larger one and a URL without the suffix are
     * left alone.
     */
    private fun String.withSharperPhoto(): String = replace(PHOTO_SIZE_SUFFIX) { match ->
        val isSmaller = match.groupValues[1].toIntOrNull()?.let { it < PHOTO_SIZE_PX } ?: false
        if (isSmaller) "=s$PHOTO_SIZE_PX-c" else match.value
    }

    private companion object {
        const val PHOTO_SIZE_PX = 256
        const val ERROR_CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"
        val SIGN_IN_PROVIDERS = mapOf(
            GoogleAuthProvider.PROVIDER_ID to AuthProvider.Google,
            GithubAuthProvider.PROVIDER_ID to AuthProvider.GitHub,
        )
        val PHOTO_SIZE_SUFFIX = Regex("=s(\\d+)-c$")
    }
}
