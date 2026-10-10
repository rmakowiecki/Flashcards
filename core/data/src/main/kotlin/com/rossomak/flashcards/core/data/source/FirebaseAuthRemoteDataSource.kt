package com.rossomak.flashcards.core.data.source

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GithubAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.activity.CurrentActivityHolder
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
    private val currentActivityHolder: CurrentActivityHolder,
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
     * Firebase runs GitHub's OAuth flow in a Custom Tab and signs in directly, so no token comes back
     * first. Only the `user:email` scope is requested, so the primary email is known even when it is
     * private on GitHub. The GitHub access token Firebase returns is never read.
     */
    override suspend fun signInWithGitHub(): SignInResult {
        // Read before any suspension: the caller is on the main thread, where the holder is written.
        val activity = currentActivityHolder.currentActivity ?: run {
            loge { "GitHub sign-in failed: no resumed Activity to open the Custom Tab from" }
            return Failed(Unknown)
        }
        val gitHubFlow = startGitHubFlow(activity)
        return completeSignIn(
            attempt = { gitHubFlow.await() },
            signInAfterCredentialInUse = ::signInWithUpdatedCredential,
        )
    }

    /**
     * Starts the Custom Tab flow, linking a Guest (CONTEXT.md) instead of signing in. Only the
     * returned task outlives this call, so the Activity is not held while the User is in the browser.
     */
    private fun startGitHubFlow(activity: Activity): Task<AuthResult> {
        val provider = OAuthProvider.newBuilder(GithubAuthProvider.PROVIDER_ID, firebaseAuth)
            .setScopes(listOf(GITHUB_EMAIL_SCOPE))
            .build()
        val guest = firebaseAuth.currentUser?.takeIf { it.isAnonymous }
        return when (guest) {
            null -> firebaseAuth.startActivityForSignInWithProvider(activity, provider)
            else -> guest.startActivityForLinkWithProvider(activity, provider)
        }
    }

    /**
     * Guest (CONTEXT.md): a Guest is linked, so the real account inherits the anonymous uid and
     * everything written under it. Otherwise this signs in. [completeSignIn] handles what follows.
     */
    private suspend fun signInOrLinkGuest(
        signIn: suspend () -> AuthResult,
        linkGuest: suspend (guest: FirebaseUser) -> AuthResult,
        signInAfterCredentialInUse: suspend (collision: FirebaseAuthUserCollisionException) -> AuthResult,
    ): SignInResult {
        val guest = firebaseAuth.currentUser?.takeIf { it.isAnonymous }
        return completeSignIn(
            attempt = { if (guest == null) signIn() else linkGuest(guest) },
            signInAfterCredentialInUse = signInAfterCredentialInUse,
        )
    }

    /**
     * Runs a sign-in or Guest link [attempt]. A link that fails because the sign-in already belongs
     * to a different existing User (a returning User on a new device) discards the Guest session
     * outright, not merged, and falls back to [signInAfterCredentialInUse]. Any other collision is a
     * failure, and a Guest stays signed in as a Guest.
     */
    private suspend fun completeSignIn(
        attempt: suspend () -> AuthResult,
        signInAfterCredentialInUse: suspend (collision: FirebaseAuthUserCollisionException) -> AuthResult,
    ): SignInResult = try {
        val result = attemptOrSignInAfterCredentialInUse(attempt, signInAfterCredentialInUse)
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
        // Closing the Custom Tab is the User's choice, not an error worth logging.
        exception.toSignInResult().also { result ->
            if (result is Failed) loge(exception) { "Sign-in failed" }
        }
    }

    private suspend fun attemptOrSignInAfterCredentialInUse(
        attempt: suspend () -> AuthResult,
        signInAfterCredentialInUse: suspend (collision: FirebaseAuthUserCollisionException) -> AuthResult,
    ): AuthResult = try {
        attempt()
    } catch (collision: FirebaseAuthUserCollisionException) {
        if (collision.errorCode != ERROR_CREDENTIAL_ALREADY_IN_USE) throw collision
        signInAfterCredentialInUse(collision)
    }

    /**
     * A provider flow cannot be replayed, so the credential Firebase attaches to the collision is the
     * only way to finish the sign-in. Without one, the collision is the failure.
     */
    private suspend fun signInWithUpdatedCredential(collision: FirebaseAuthUserCollisionException): AuthResult {
        val credential = collision.updatedCredential ?: throw collision
        return firebaseAuth.signInWithCredential(credential).await()
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
        const val GITHUB_EMAIL_SCOPE = "user:email"
        val SIGN_IN_PROVIDERS = mapOf(
            GoogleAuthProvider.PROVIDER_ID to AuthProvider.Google,
            GithubAuthProvider.PROVIDER_ID to AuthProvider.GitHub,
        )
        val PHOTO_SIZE_SUFFIX = Regex("=s(\\d+)-c$")
    }
}
