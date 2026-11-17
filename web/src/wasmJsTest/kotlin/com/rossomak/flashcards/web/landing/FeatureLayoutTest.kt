package com.rossomak.flashcards.web.landing

import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.landing.FeatureLayout.Stacked
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class FeatureLayoutTest {

    @Test
    fun `a landscape window lays features side by side`() {
        FeatureLayout.forWindow(width = 1920.dp, height = 1080.dp) shouldBe SideBySide
        FeatureLayout.forWindow(width = 1200.dp, height = 1000.dp) shouldBe SideBySide
    }

    @Test
    fun `a square window lays features side by side`() {
        FeatureLayout.forWindow(width = 1000.dp, height = 1000.dp) shouldBe SideBySide
        FeatureLayout.forWindow(width = 900.dp, height = 1000.dp) shouldBe SideBySide
    }

    @Test
    fun `a landscape phone lays features side by side`() {
        FeatureLayout.forWindow(width = 844.dp, height = 390.dp) shouldBe SideBySide
    }

    @Test
    fun `a portrait monitor stacks features however wide it is`() {
        FeatureLayout.forWindow(width = 1269.dp, height = 2000.dp) shouldBe Stacked
    }

    @Test
    fun `a half-width desktop window stacks features`() {
        FeatureLayout.forWindow(width = 960.dp, height = 1080.dp) shouldBe Stacked
        FeatureLayout.forWindow(width = 890.dp, height = 1000.dp) shouldBe Stacked
    }

    @Test
    fun `a portrait phone stacks features`() {
        FeatureLayout.forWindow(width = 390.dp, height = 844.dp) shouldBe Stacked
    }

    @Test
    fun `the threshold can be tuned`() {
        FeatureLayout.forWindow(width = 1000.dp, height = 1000.dp, sideBySideMinAspectRatio = 1.2f) shouldBe Stacked
    }
}
