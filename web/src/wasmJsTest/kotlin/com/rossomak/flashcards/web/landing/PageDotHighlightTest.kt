package com.rossomak.flashcards.web.landing

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PageDotHighlightTest {

    @Test
    fun `the dot of the page on screen is fully highlighted`() {
        PageDotHighlight.nearness(position = 3f, page = 3) shouldBe 1f
    }

    @Test
    fun `the highlight is shared between two dots while the pages scroll`() {
        PageDotHighlight.nearness(position = 3.25f, page = 3) shouldBe (0.75f plusOrMinus TOLERANCE)
        PageDotHighlight.nearness(position = 3.25f, page = 4) shouldBe (0.25f plusOrMinus TOLERANCE)
    }

    @Test
    fun `a dot a page or more away has no highlight`() {
        PageDotHighlight.nearness(position = 3f, page = 5) shouldBe 0f
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
