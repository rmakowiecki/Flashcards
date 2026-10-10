package com.rossomak.flashcards.core.ui.format

import io.kotest.matchers.shouldBe
import java.util.Locale
import org.junit.Test

class XpAmountFormatTest {

    @Test
    fun `a gain gets an explicit plus and the locale's grouping`() {
        formatSignedXp(GAIN, Locale.US) shouldBe "+3,680"
    }

    @Test
    fun `a locale with another separator groups with it`() {
        formatSignedXp(GAIN, Locale.GERMANY) shouldBe "+3.680"
    }

    @Test
    fun `a loss gets a real minus sign`() {
        formatSignedXp(-LOSS, Locale.US) shouldBe "−240"
    }

    @Test
    fun `zero has no sign`() {
        formatSignedXp(0, Locale.US) shouldBe "0"
    }

    @Test
    fun `a factor is grouped without a plus`() {
        formatXpFactor(GAIN, Locale.US) shouldBe "3,680"
    }

    @Test
    fun `a negative factor gets a real minus sign`() {
        formatXpFactor(-LOSS, Locale.US) shouldBe "−240"
    }

    private companion object {
        const val GAIN = 3_680
        const val LOSS = 240
    }
}
