package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason

/** One-shot snackbar messages for the Account screen, never screen state. */
sealed interface AccountMessage {
    data object OpenLinkFailed : AccountMessage
    data object NoEmailApp : AccountMessage
    data class DeletionFailed(val reason: AccountDeletionFailureReason) : AccountMessage
}
