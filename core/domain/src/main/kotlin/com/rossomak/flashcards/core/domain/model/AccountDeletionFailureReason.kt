package com.rossomak.flashcards.core.domain.model

/** Why an Account Deletion did not complete. Nothing on the device was touched and the User stays signed in. */
sealed interface AccountDeletionFailureReason {

    /** Offline or timed out. The request may still have reached the server. */
    data object NoConnection : AccountDeletionFailureReason

    data object ServiceError : AccountDeletionFailureReason
}
