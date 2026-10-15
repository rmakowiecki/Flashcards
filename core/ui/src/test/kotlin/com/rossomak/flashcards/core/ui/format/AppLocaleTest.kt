package com.rossomak.flashcards.core.ui.format

import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppLocaleTest {

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
    fun `an XP amount groups with a comma whatever the device locale`() {
        formatSignedXp(AMOUNT, AppLocale) shouldBe "+1,200"
    }

    private companion object {
        const val AMOUNT = 1_200
    }
}
