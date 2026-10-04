package com.rossomak.flashcards.feature.account

/**
 * Everything the Account screen renders. A null [displayName] or [email] hides its header line, and a
 * null [appVersionLabel] hides the version row's trailing value, so a field that has not loaded
 * never shows as a blank.
 */
data class AccountScreenState(
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val appVersionLabel: String? = null,
    val activeDialog: AccountDialog? = null,
)
