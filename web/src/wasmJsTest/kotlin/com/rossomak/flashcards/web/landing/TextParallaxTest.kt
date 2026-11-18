package com.rossomak.flashcards.web.landing

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TextParallaxTest {

    @Test
    fun `text on screen sits on its page fully shown`() {
        TextParallax.translationFraction(0f) shouldBe 0f
        TextParallax.alpha(0f) shouldBe 1f
    }

    @Test
    fun `text lags behind its page by a fixed share either way`() {
        TextParallax.translationFraction(0.5f) shouldBe 0.5f * TextParallax.LAG
        TextParallax.translationFraction(-0.5f) shouldBe -0.5f * TextParallax.LAG
    }

    @Test
    fun `text moves slower than its page but still lags it`() {
        TextParallax.LAG shouldBeGreaterThan 0f
        TextParallax.LAG shouldBeLessThan 1f
    }

    @Test
    fun `text fades out as its page leaves and is gone before the next one settles`() {
        TextParallax.alpha(0.3f) shouldBeLessThan 1f
        TextParallax.alpha(0.8f) shouldBe 0f
        TextParallax.alpha(-0.8f) shouldBe 0f
        TextParallax.alpha(1f) shouldBe 0f
        TextParallax.alpha(-1f) shouldBe 0f
    }
}
