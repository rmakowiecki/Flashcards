package com.rossomak.flashcards.web.navigation

import com.rossomak.flashcards.web.navigation.WebRoute.DeleteAccount
import com.rossomak.flashcards.web.navigation.WebRoute.Home
import com.rossomak.flashcards.web.navigation.WebRoute.PrivacyPolicy
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class WebRouteTest {

    @Test
    fun `root path opens home`() {
        WebRoute.fromPath("/") shouldBe Home
    }

    @Test
    fun `empty path opens home`() {
        WebRoute.fromPath("") shouldBe Home
    }

    @Test
    fun `privacy path opens the privacy policy`() {
        WebRoute.fromPath(PRIVACY_PATH) shouldBe PrivacyPolicy
    }

    @Test
    fun `delete account path opens the account deletion page`() {
        WebRoute.fromPath("/delete-account") shouldBe DeleteAccount
    }

    @Test
    fun `trailing slash is ignored`() {
        WebRoute.fromPath("$PRIVACY_PATH/") shouldBe PrivacyPolicy
    }

    @Test
    fun `html suffix of the old static pages is ignored`() {
        WebRoute.fromPath("$PRIVACY_PATH.html") shouldBe PrivacyPolicy
    }

    @Test
    fun `unknown path falls back to home`() {
        WebRoute.fromPath("/no-such-page") shouldBe Home
    }

    @Test
    fun `every route parses back from its own path`() {
        listOf(Home, PrivacyPolicy, DeleteAccount).forEach { route -> WebRoute.fromPath(route.path) shouldBe route }
    }

    private companion object {
        const val PRIVACY_PATH = "/privacy"
    }
}
