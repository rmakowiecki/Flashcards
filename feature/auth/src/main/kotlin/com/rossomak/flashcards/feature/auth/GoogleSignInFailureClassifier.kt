package com.rossomak.flashcards.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown

/** The User dismissed the account picker: not a failure to report. */
internal fun Throwable.isGoogleSignInCancellation(): Boolean = this is GetCredentialCancellationException

/** Only a missing Google account gets its own explanation; every other failure is [Unknown]. */
internal fun Throwable.toLoginFailureReason(): LoginFailureReason =
    if (this is NoCredentialException) NoCredentialAvailable else Unknown
