package com.rossomak.flashcards.debugtools.darktheme

import com.rossomak.flashcards.core.designsystem.theme.darkFlashcardsColors
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test

class DarkThemeTunerKnobsTest {

    private val tunedColors = DarkThemeTunerKnobs().toColors()

    @Test
    fun `default knobs reproduce the shipped dark color scheme`() {
        // ColorScheme has no structural equals; its toString lists every slot.
        val shipped = darkFlashcardsColors.colorScheme.toString()

        shipped shouldStartWith COLOR_SCHEME_PREFIX
        tunedColors.colorScheme.toString() shouldBe shipped
    }

    @Test
    fun `default knobs reproduce the shipped dark brand colors`() {
        tunedColors.brand shouldBe darkFlashcardsColors.brand
    }

    @Test
    fun `default knobs reproduce the shipped dark semantic colors`() {
        tunedColors.semantic shouldBe darkFlashcardsColors.semantic
    }

    private companion object {
        const val COLOR_SCHEME_PREFIX = "ColorScheme(primary="
    }
}
