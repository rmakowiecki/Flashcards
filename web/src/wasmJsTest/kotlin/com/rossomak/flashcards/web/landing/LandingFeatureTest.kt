package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.landing.ScreenshotVariant.Dark
import com.rossomak.flashcards.web.landing.ScreenshotVariant.Light
import com.rossomak.flashcards.web.resources.Res
import com.rossomak.flashcards.web.resources.screenshot_01_home_dark
import com.rossomak.flashcards.web.resources.screenshot_01_home_light
import com.rossomak.flashcards.web.resources.screenshot_08_session_complete_dark
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LandingFeatureTest {

    @Test
    fun `a dark browser scheme picks the dark screenshots`() {
        ScreenshotVariant.forColorScheme(prefersDark = true) shouldBe Dark
    }

    @Test
    fun `a light browser scheme picks the light screenshots`() {
        ScreenshotVariant.forColorScheme(prefersDark = false) shouldBe Light
    }

    @Test
    fun `each variant picks its own screenshot`() {
        LandingFeature.Home.screenshot(Light) shouldBe Res.drawable.screenshot_01_home_light
        LandingFeature.Home.screenshot(Dark) shouldBe Res.drawable.screenshot_01_home_dark
        LandingFeature.SessionComplete.screenshot(Dark) shouldBe Res.drawable.screenshot_08_session_complete_dark
    }

    @Test
    fun `features alternate the phone's side`() {
        LandingFeature.entries.map { feature -> feature.phoneOnEnd } shouldBe List(LandingFeature.entries.size) { index -> index % 2 == 0 }
    }
}
