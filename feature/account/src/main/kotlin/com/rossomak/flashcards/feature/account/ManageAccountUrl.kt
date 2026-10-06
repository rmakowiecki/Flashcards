package com.rossomak.flashcards.feature.account

import java.net.URLEncoder

private const val MANAGE_ACCOUNT_BASE_URL = "https://myaccount.google.com"

/** The signed-in User's Google account page; the email picks the right account when several are signed in on the device. */
internal fun manageAccountUrl(email: String?): String = if (email.isNullOrBlank()) {
    MANAGE_ACCOUNT_BASE_URL
} else {
    "$MANAGE_ACCOUNT_BASE_URL?authuser=${URLEncoder.encode(email, Charsets.UTF_8.name())}"
}
