package com.rossomak.flashcards.web.landing

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PageRevealTest {

    @Test
    fun `a page the pages head for is shown`() {
        PageReveal.shown(isTarget = true, distancePages = PART_WAY) shouldBe true
    }

    @Test
    fun `a page the pages head for is shown even before it enters the window`() {
        PageReveal.shown(isTarget = true, distancePages = 1f) shouldBe true
    }

    @Test
    fun `a page out of the window is hidden for its next reveal`() {
        PageReveal.shown(isTarget = false, distancePages = 1f) shouldBe false
    }

    @Test
    fun `a page partly in the window keeps its components as they are`() {
        PageReveal.shown(isTarget = false, distancePages = PART_WAY).shouldBeNull()
    }

    private companion object {
        const val PART_WAY = 0.4f
    }
}
