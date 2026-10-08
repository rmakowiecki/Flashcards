package com.rossomak.flashcards.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gets a Google ID token from Credential Manager. A failure carries the raw platform throwable;
 * classifying and logging it is the caller's job. A coroutine cancellation propagates.
 */
class GoogleSignInLauncher(private val context: Context) {

    suspend fun launch(): Result<String> {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            return Result.failure(IllegalStateException("Missing GOOGLE_WEB_CLIENT_ID. Add it to local.properties."))
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = CredentialManager.create(context).getCredential(
                context = context,
                request = request
            )
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            Result.success(credential.idToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (
            // Every failure must come back as a Result: an exception escaping here would leave the
            // screen stuck on "Signing in…".
            @Suppress("TooGenericExceptionCaught") exception: Exception,
        ) {
            Result.failure(exception)
        }
    }
}
