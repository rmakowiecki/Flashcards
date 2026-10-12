package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.core.domain.model.AuthProvider.GitHub
import com.rossomak.flashcards.core.domain.model.AuthProvider.Google
import io.kotest.matchers.shouldBe
import org.junit.Test

class ManageAccountUrlTest {

    @Test
    fun `a missing email gives the bare account page`() {
        manageAccountUrl(Google, null) shouldBe BARE_URL
    }

    @Test
    fun `a blank email gives the bare account page`() {
        manageAccountUrl(Google, "  ") shouldBe BARE_URL
    }

    @Test
    fun `the email picks the account`() {
        manageAccountUrl(Google, USER_EMAIL) shouldBe "$BARE_URL?authuser=user%40example.com"
    }

    @Test
    fun `plus and at signs in the email are encoded`() {
        manageAccountUrl(Google, "a+b@example.com") shouldBe "$BARE_URL?authuser=a%2Bb%40example.com"
    }

    @Test
    fun `GitHub gives the GitHub profile settings whatever the email`() {
        listOf(null, "  ", USER_EMAIL).forEach { email ->
            manageAccountUrl(GitHub, email) shouldBe GITHUB_URL
        }
    }

    private companion object {
        const val BARE_URL = "https://myaccount.google.com"
        const val GITHUB_URL = "https://github.com/settings/profile"
        const val USER_EMAIL = "user@example.com"
    }
}
