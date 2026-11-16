package com.rossomak.flashcards.web.landing

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SnapAssistTest {

    @Test
    fun `a page at rest stays on its page`() {
        SnapAssist.settlePage(position = 3f, direction = 0, pageCount = PAGE_COUNT) shouldBe 3
        SnapAssist.settlePage(position = 3f, direction = 1, pageCount = PAGE_COUNT) shouldBe 3
    }

    @Test
    fun `scrolling at rest between pages settles on the nearest one`() {
        SnapAssist.settlePage(position = 3.7f, direction = 0, pageCount = PAGE_COUNT) shouldBe 4
        SnapAssist.settlePage(position = 3.2f, direction = 0, pageCount = PAGE_COUNT) shouldBe 3
    }

    @Test
    fun `a small nudge settles back on the page it left`() {
        SnapAssist.settlePage(position = 3.1f, direction = 1, pageCount = PAGE_COUNT) shouldBe 3
        SnapAssist.settlePage(position = 2.9f, direction = -1, pageCount = PAGE_COUNT) shouldBe 3
    }

    @Test
    fun `a scroll well toward the next page settles there`() {
        SnapAssist.settlePage(position = 3.25f, direction = 1, pageCount = PAGE_COUNT) shouldBe 4
        SnapAssist.settlePage(position = 3.75f, direction = -1, pageCount = PAGE_COUNT) shouldBe 3
    }

    @Test
    fun `settling stays within the pages`() {
        SnapAssist.settlePage(position = 0f, direction = -1, pageCount = PAGE_COUNT) shouldBe 0
        SnapAssist.settlePage(position = LAST_PAGE.toFloat(), direction = 1, pageCount = PAGE_COUNT) shouldBe LAST_PAGE
    }

    private companion object {
        const val PAGE_COUNT = 10
        const val LAST_PAGE = PAGE_COUNT - 1
    }
}
