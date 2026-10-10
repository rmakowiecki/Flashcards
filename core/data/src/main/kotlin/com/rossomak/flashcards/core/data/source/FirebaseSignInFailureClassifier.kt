package com.rossomak.flashcards.core.data.source

import com.google.firebase.FirebaseNetworkException
import com.rossomak.flashcards.core.common.isConnectionFailure
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.SignInFailureReason.Unknown
import com.rossomak.flashcards.core.domain.model.SignInResult
import com.rossomak.flashcards.core.domain.model.SignInResult.Failed

/**
 * Maps a Firebase sign-in failure to the domain result, so no Firebase type crosses the data layer.
 * [FirebaseNetworkException] is not an `IOException` and usually has none in its cause chain, so it
 * is checked on its own.
 */
internal fun Throwable.toSignInResult(): SignInResult = when {
    this is FirebaseNetworkException || isConnectionFailure() -> Failed(NoConnection)
    else -> Failed(Unknown)
}
