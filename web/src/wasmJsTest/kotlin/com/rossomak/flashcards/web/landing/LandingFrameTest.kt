package com.rossomak.flashcards.web.landing

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LandingFrameTest {

    @Test
    fun `a portrait phone is compact by its width`() {
        val frame = LandingFrame(width = 390.dp, height = 844.dp)
        frame.isCompactWidth shouldBe true
        frame.isCompactHeight shouldBe false
        frame.isCompact shouldBe true
    }

    @Test
    fun `a landscape phone is compact by its height`() {
        val frame = LandingFrame(width = 844.dp, height = 390.dp)
        frame.isCompactWidth shouldBe false
        frame.isCompactHeight shouldBe true
        frame.isCompact shouldBe true
    }

    @Test
    fun `a desktop window is not compact`() {
        LandingFrame(width = 1440.dp, height = 900.dp).isCompact shouldBe false
        LandingFrame(width = 600.dp, height = 480.dp).isCompact shouldBe false
    }

    @Test
    fun `a compact window gets the small page dots and a desktop window the regular ones`() {
        LandingFrame(width = 390.dp, height = 844.dp).dots shouldBe PageDotMetrics.Compact
        LandingFrame(width = 1440.dp, height = 900.dp).dots shouldBe PageDotMetrics.Regular
    }

    @Test
    fun `the compact page dots are smaller than the regular ones and content stays clear of them`() {
        val compact = PageDotMetrics.Compact
        val regular = PageDotMetrics.Regular
        (compact.slot < regular.slot) shouldBe true
        (compact.currentSize < regular.currentSize) shouldBe true
        val compactPillEdge = compact.slot + compact.pillPadding * 2 + compact.endPadding
        (compact.contentClearance > compactPillEdge) shouldBe true
    }

    @Test
    fun `a landscape phone keeps a small header and puts the large logo near the top`() {
        val frame = LandingFrame(width = 844.dp, height = 390.dp)
        frame.headerLogoWidth shouldBe 88.dp
        frame.headerMargin shouldBe 12.dp
        frame.heroLogoTop shouldBe 39.dp
        frame.heroLogoWidth shouldBe 195.dp
    }

    @Test
    fun `a desktop window keeps its header margin`() {
        LandingFrame(width = 1440.dp, height = 900.dp).headerMargin shouldBe 28.dp
    }
}
