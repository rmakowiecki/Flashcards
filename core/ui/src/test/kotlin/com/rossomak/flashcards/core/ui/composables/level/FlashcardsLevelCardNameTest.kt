package com.rossomak.flashcards.core.ui.composables.level

import io.kotest.matchers.shouldBe
import org.junit.Test

class FlashcardsLevelCardNameTest {

    @Test
    fun `levelCardName with a normal name returns it unchanged`() {
        levelCardName("Jane Doe") shouldBe "Jane Doe"
    }

    @Test
    fun `levelCardName with a padded name returns it trimmed`() {
        levelCardName("  Jane Doe \t") shouldBe "Jane Doe"
    }

    @Test
    fun `levelCardName with null returns null`() {
        levelCardName(null) shouldBe null
    }

    @Test
    fun `levelCardName with an empty name returns null`() {
        levelCardName("") shouldBe null
    }

    @Test
    fun `levelCardName with a whitespace-only name returns null`() {
        levelCardName(" \t\n ") shouldBe null
    }

    @Test
    fun `levelCardName with a non-breaking-space-only name returns null`() {
        levelCardName("\u00A0\u00A0") shouldBe null
    }
}
