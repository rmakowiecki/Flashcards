package com.rossomak.flashcards.feature.account

import io.kotest.matchers.shouldBe
import org.junit.Test

class ManageAccountUrlTest {

    @Test
    fun `a missing email gives the bare account page`() {
        manageAccountUrl(null) shouldBe BARE_URL
    }

    @Test
    fun `a blank email gives the bare account page`() {
        manageAccountUrl("  ") shouldBe BARE_URL
    }

    @Test
    fun `the email picks the account`() {
        manageAccountUrl("user@example.com") shouldBe "$BARE_URL?authuser=user%40example.com"
    }

    @Test
    fun `plus and at signs in the email are encoded`() {
        manageAccountUrl("a+b@example.com") shouldBe "$BARE_URL?authuser=a%2Bb%40example.com"
    }

    private companion object {
        const val BARE_URL = "https://myaccount.google.com"
    }
}
