package com.rossomak.flashcards.core.data.source

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.rossomak.flashcards.core.common.isConnectionFailure
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.AccountExistsWithDifferentProvider
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.Unknown
import com.rossomak.flashcards.core.domain.model.SignInResult
import com.rossomak.flashcards.core.domain.model.SignInResult.Cancelled
import com.rossomak.flashcards.core.domain.model.SignInResult.Failed

/**
 * Maps a Firebase sign-in failure to the domain result, so no Firebase type crosses the data layer.
 * [FirebaseNetworkException] is not an `IOException` and usually has none in its cause chain, so it
 * is checked on its own. `ERROR_WEB_CONTEXT_ALREADY_PRESENTED`, a second provider flow while one is
 * open, is left to [Unknown]: the Login buttons are disabled while a sign-in runs.
 */
internal fun Throwable.toSignInResult(): SignInResult = when {
    this is FirebaseAuthException && errorCode == ERROR_WEB_CONTEXT_CANCELED -> Cancelled
    this is FirebaseAuthException && errorCode in EMAIL_CLASH_ERROR_CODES -> Failed(AccountExistsWithDifferentProvider)
    this is FirebaseNetworkException || isConnectionFailure() -> Failed(NoConnection)
    else -> Failed(Unknown)
}

/** The User closed the provider's Custom Tab. */
private const val ERROR_WEB_CONTEXT_CANCELED = "ERROR_WEB_CONTEXT_CANCELED"

/** A sign-in and a Guest link report the same email clash with different codes. */
private val EMAIL_CLASH_ERROR_CODES = setOf(
    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
    "ERROR_EMAIL_ALREADY_IN_USE",
)
