package com.rossomak.flashcards.feature.home

import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class RecentDateTextTest {

    private val originalDefaultLocale = Locale.getDefault()

    @Before
    fun setPolishDeviceLocale() {
        Locale.setDefault(Locale.forLanguageTag("pl-PL"))
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    @Test
    fun `a date in the current year is day then English month`() {
        recentDateText(LocalDate.of(2026, 10, 6), includesYear = false) shouldBe "6 Oct"
    }

    @Test
    fun `a date in another year ends with the year`() {
        recentDateText(LocalDate.of(2025, 10, 6), includesYear = true) shouldBe "6 Oct 2025"
    }

    @Test
    fun `September keeps its three letter name`() {
        recentDateText(LocalDate.of(2026, 9, 15), includesYear = false) shouldBe "15 Sep"
    }

    @Test
    fun `the day has no leading zero`() {
        recentDateText(LocalDate.of(2026, 1, 3), includesYear = false) shouldBe "3 Jan"
    }
}
