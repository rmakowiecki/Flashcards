package com.rossomak.flashcards.core.domain.model

/** Not a `Result<Unit>`: the failure is a sealed type, not a `Throwable`. */
sealed interface AccountDeletionResult {

    /** The account is gone and the device signed out. */
    data object Deleted : AccountDeletionResult

    data class Failed(val reason: AccountDeletionFailureReason) : AccountDeletionResult
}
