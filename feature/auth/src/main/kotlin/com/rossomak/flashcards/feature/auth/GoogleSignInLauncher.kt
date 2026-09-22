package com.rossomak.flashcards.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoAccountOnDevice
import com.rossomak.flashcards.feature.auth.LoginFailureReason.SignInFailed

/** Thrown when the user dismisses the account picker — not a [LoginFailureReason], never shown. */
data object GoogleSignInCancelled : Exception()

class GoogleSignInLauncher(private val context: Context) {

    suspend fun launch(): Result<String> {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            return Result.failure(SignInFailed(IllegalStateException("Missing GOOGLE_WEB_CLIENT_ID. Add it to local.properties.")))
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
        } catch (@Suppress("SwallowedException") exception: GetCredentialCancellationException) {
            Result.failure(GoogleSignInCancelled)
        } catch (exception: NoCredentialException) {
            loge(exception) { "No Google account on device" }
            Result.failure(NoAccountOnDevice)
        } catch (exception: GetCredentialException) {
            loge(exception) { "Credential Manager failed" }
            Result.failure(SignInFailed(exception))
        } catch (exception: GoogleIdTokenParsingException) {
            loge(exception) { "Failed to parse Google ID token" }
            Result.failure(SignInFailed(exception))
        }
    }
}
