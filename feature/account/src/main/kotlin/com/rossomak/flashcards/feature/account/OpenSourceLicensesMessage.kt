package com.rossomak.flashcards.feature.account

/** One-shot snackbar messages for the Open-source licenses screen, never screen state. */
sealed interface OpenSourceLicensesMessage {
    data object OpenLinkFailed : OpenSourceLicensesMessage
}
