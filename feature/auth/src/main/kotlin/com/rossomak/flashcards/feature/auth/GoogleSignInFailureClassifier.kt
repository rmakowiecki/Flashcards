package com.rossomak.flashcards.feature.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.rossomak.flashcards.core.common.isConnectionFailure
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoConnection
import com.rossomak.flashcards.feature.auth.LoginFailureReason.NoCredentialAvailable
import com.rossomak.flashcards.feature.auth.LoginFailureReason.Unknown

/** The User dismissed the account picker: not a failure to report. */
internal fun Throwable.isGoogleSignInCancellation(): Boolean = this is GetCredentialCancellationException

/**
 * Offline wins over everything: Credential Manager throws the same [NoCredentialException] for "no
 * network" as for "no Google account", and the connection is the more actionable problem anyway. A
 * connection failure in the cause chain still reads as [NoConnection] when the check said online,
 * since that check does not validate the network. Only then does a missing Google account get its
 * own explanation; every other failure is [Unknown].
 */
internal fun Throwable.toLoginFailureReason(isInternetAvailable: Boolean): LoginFailureReason = when {
    !isInternetAvailable || isConnectionFailure() -> NoConnection
    this is NoCredentialException -> NoCredentialAvailable
    else -> Unknown
}
