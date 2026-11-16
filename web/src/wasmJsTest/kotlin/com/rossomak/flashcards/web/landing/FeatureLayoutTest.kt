package com.rossomak.flashcards.web.landing

import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.web.landing.FeatureLayout.SideBySide
import com.rossomak.flashcards.web.landing.FeatureLayout.Stacked
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class FeatureLayoutTest {

    @Test
    fun `a window at the breakpoint lays features side by side`() {
        FeatureLayout.forWindowWidth(840.dp) shouldBe SideBySide
        FeatureLayout.forWindowWidth(1600.dp) shouldBe SideBySide
    }

    @Test
    fun `a window under the breakpoint stacks features`() {
        FeatureLayout.forWindowWidth(839.dp) shouldBe Stacked
        FeatureLayout.forWindowWidth(390.dp) shouldBe Stacked
    }
}
