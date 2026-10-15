package com.rossomak.flashcards.core.ui.format

import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class PercentFormatTest {

    private val originalDefaultLocale = Locale.getDefault()

    @Before
    fun setArabicDeviceLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    @Test
    fun `a percent uses ASCII digits and a tight percent sign whatever the device locale`() {
        formatPercent(PERCENT, AppLocale) shouldBe "58%"
    }

    @Test
    fun `every whole percent from zero to one hundred keeps its value`() {
        (0..FULL_PERCENT).forEach { percent ->
            formatPercent(percent, AppLocale) shouldBe "$percent%"
        }
    }

    private companion object {
        const val PERCENT = 58
        const val FULL_PERCENT = 100
    }
}
