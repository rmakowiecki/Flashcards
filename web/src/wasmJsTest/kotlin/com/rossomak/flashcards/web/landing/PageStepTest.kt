package com.rossomak.flashcards.web.landing

import com.rossomak.flashcards.web.landing.PageStep.First
import com.rossomak.flashcards.web.landing.PageStep.Last
import com.rossomak.flashcards.web.landing.PageStep.Next
import com.rossomak.flashcards.web.landing.PageStep.Previous
import com.rossomak.flashcards.web.landing.WheelInput.Glide
import com.rossomak.flashcards.web.landing.WheelInput.Notch
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

    @Test
    fun `a chrome mouse notch scrolls by its delta, eased`() {
        WheelInput.from(deltaY = 100.0, deltaMode = DOM_DELTA_PIXEL, wheelDeltaY = -120.0, pageSizePx = PAGE_SIZE_PX) shouldBe Notch(100f)
        WheelInput.from(deltaY = -100.0, deltaMode = DOM_DELTA_PIXEL, wheelDeltaY = 120.0, pageSizePx = PAGE_SIZE_PX) shouldBe Notch(-100f)
    }

    @Test
    fun `a firefox mouse notch in lines scrolls by its lines in pixels`() {
        WheelInput.from(deltaY = 3.0, deltaMode = DOM_DELTA_LINE, wheelDeltaY = 0.0, pageSizePx = PAGE_SIZE_PX) shouldBe Notch(120f)
    }

    @Test
    fun `a wheel scrolling by pages scrolls whole pages`() {
        WheelInput.from(deltaY = 1.0, deltaMode = DOM_DELTA_PAGE, wheelDeltaY = 0.0, pageSizePx = PAGE_SIZE_PX) shouldBe Notch(PAGE_SIZE_PX)
    }

    @Test
    fun `a trackpad scroll is a glide that follows it`() {
        WheelInput.from(deltaY = 12.0, deltaMode = DOM_DELTA_PIXEL, wheelDeltaY = -36.0, pageSizePx = PAGE_SIZE_PX) shouldBe Glide(12f)
    }

    @Test
    fun `a pixel scroll without a legacy delta is a glide`() {
        WheelInput.from(deltaY = -7.5, deltaMode = DOM_DELTA_PIXEL, wheelDeltaY = 0.0, pageSizePx = PAGE_SIZE_PX) shouldBe Glide(-7.5f)
    }

    @Test
    fun `an event without vertical movement is ignored`() {
        WheelInput.from(deltaY = 0.0, deltaMode = DOM_DELTA_PIXEL, wheelDeltaY = 0.0, pageSizePx = PAGE_SIZE_PX).shouldBeNull()
    }

    private companion object {
        const val PAGE_COUNT = 10
        const val LAST_PAGE = PAGE_COUNT - 1
        const val DOM_DELTA_PIXEL = 0
        const val DOM_DELTA_LINE = 1
        const val DOM_DELTA_PAGE = 2
        const val PAGE_SIZE_PX = 1124f
    }
}
