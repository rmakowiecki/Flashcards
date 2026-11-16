package com.rossomak.flashcards.web.landing

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LandingScaleTest {

    @Test
    fun `text keeps the type scale up to the reference width`() {
        LandingScale.fontScale(390.dp) shouldBe 1f
        LandingScale.fontScale(1280.dp) shouldBe 1f
    }

    @Test
    fun `text grows on wider windows up to a cap`() {
        LandingScale.fontScale(1600.dp) shouldBe 1.25f
        LandingScale.fontScale(4000.dp) shouldBe 1.45f
    }

    @Test
    fun `the hero logo takes about half a wide window`() {
        LandingScale.heroLogoWidth(windowWidth = 2000.dp, windowHeight = 1124.dp) shouldBe 899.2.dp
        LandingScale.heroLogoWidth(windowWidth = 1440.dp, windowHeight = 900.dp) shouldBe 662.4.dp
    }

    @Test
    fun `the hero logo fits a phone`() {
        LandingScale.heroLogoWidth(windowWidth = 390.dp, windowHeight = 844.dp) shouldBe 240.dp
        LandingScale.heroLogoWidth(windowWidth = 260.dp, windowHeight = 600.dp) shouldBe 212.dp
    }

    @Test
    fun `the logo is large on the hero and the closing page`() {
        LandingScale.headerProgress(position = 0f, pageCount = PAGE_COUNT) shouldBe 0f
        LandingScale.headerProgress(position = LAST_PAGE.toFloat(), pageCount = PAGE_COUNT) shouldBe 0f
    }

    @Test
    fun `the logo sits in the header on every feature page`() {
        for (page in 1 until LAST_PAGE) {
            LandingScale.headerProgress(position = page.toFloat(), pageCount = PAGE_COUNT) shouldBe 1f
        }
    }

    @Test
    fun `the logo follows the scroll between the hero and the first feature`() {
        LandingScale.headerProgress(position = 0.25f, pageCount = PAGE_COUNT) shouldBe 0.25f
        LandingScale.headerProgress(position = LAST_PAGE - 0.75f, pageCount = PAGE_COUNT) shouldBe 0.75f
    }

    private companion object {
        const val PAGE_COUNT = 10
        const val LAST_PAGE = PAGE_COUNT - 1
    }
}
