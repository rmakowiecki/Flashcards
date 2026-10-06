package com.rossomak.flashcards.feature.account

/** One-shot snackbar messages for the Account screen, never screen state. */
sealed interface AccountMessage {
    data object OpenLinkFailed : AccountMessage
}
