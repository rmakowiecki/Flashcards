package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.AuthProvider

/**
 * Everything the Account screen renders. A null [displayName] or [email] hides its header line, a null
 * [provider] hides the "Signed in with …" line, and a null [appVersion] hides the version row's trailing value, so a field that has not loaded never
 * shows as a blank. The version stays a domain model here: its text is composed where string
 * resources are available. While [isDeletingAccount] is true, the screen shows the deletion overlay and
 * takes no input.
 */
data class AccountScreenState(
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val provider: AuthProvider? = null,
    val appVersion: AppVersion? = null,
    val activeDialog: AccountDialog? = null,
    val isDeletingAccount: Boolean = false,
)
