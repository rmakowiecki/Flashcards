package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.AuthProvider
import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import java.net.URLEncoder

private const val MANAGE_ACCOUNT_BASE_URL = "https://myaccount.google.com"

private const val GITHUB_PROFILE_SETTINGS_URL = "https://github.com/settings/profile"

/**
 * The signed-in User's account page at their sign-in [provider]. For Google, the email picks the right account when
 * several are signed in on the device. GitHub's settings page has no per-account parameter, so the email is ignored.
 */
internal fun manageAccountUrl(provider: AuthProvider, email: String?): String = when (provider) {
    GitHub -> GITHUB_PROFILE_SETTINGS_URL
    Google -> if (email.isNullOrBlank()) {
        MANAGE_ACCOUNT_BASE_URL
    } else {
        "$MANAGE_ACCOUNT_BASE_URL?authuser=${URLEncoder.encode(email, Charsets.UTF_8.name())}"
    }
}
