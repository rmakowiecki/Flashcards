package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.landing.PageStep.First
import com.rossomak.flashcards.web.landing.PageStep.Last
import com.rossomak.flashcards.web.landing.PageStep.Next
import com.rossomak.flashcards.web.landing.PageStep.Previous
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PageStepTest {

    @Test
    fun `next moves one page down`() {
        Next.from(page = 3, pageCount = PAGE_COUNT) shouldBe 4
    }

    @Test
    fun `previous moves one page up`() {
        Previous.from(page = 3, pageCount = PAGE_COUNT) shouldBe 2
    }

    @Test
    fun `next stays on the last page`() {
        Next.from(page = LAST_PAGE, pageCount = PAGE_COUNT) shouldBe LAST_PAGE
    }

    @Test
    fun `previous stays on the first page`() {
        Previous.from(page = 0, pageCount = PAGE_COUNT) shouldBe 0
    }

    @Test
    fun `first and last go to the ends`() {
        First.from(page = 5, pageCount = PAGE_COUNT) shouldBe 0
        Last.from(page = 5, pageCount = PAGE_COUNT) shouldBe LAST_PAGE
    }

    @Test
    fun `arrow and page keys step one page`() {
        PageStep.forKey("ArrowDown") shouldBe Next
        PageStep.forKey("PageDown") shouldBe Next
        PageStep.forKey("ArrowUp") shouldBe Previous
        PageStep.forKey("PageUp") shouldBe Previous
    }

    @Test
    fun `home and end keys go to the ends`() {
        PageStep.forKey("Home") shouldBe First
        PageStep.forKey("End") shouldBe Last
    }

    @Test
    fun `other keys are left to the browser`() {
        PageStep.forKey("a").shouldBeNull()
        PageStep.forKey(" ").shouldBeNull()
        PageStep.forKey("ArrowLeft").shouldBeNull()
    }

    private companion object {
        const val PAGE_COUNT = 10
        const val LAST_PAGE = PAGE_COUNT - 1
    }
}
