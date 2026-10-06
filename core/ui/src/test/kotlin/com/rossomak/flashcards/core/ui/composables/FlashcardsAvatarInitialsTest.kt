package com.rossomak.flashcards.core.ui.composables

import io.kotest.matchers.shouldBe
import org.junit.Test

class FlashcardsAvatarInitialsTest {

    @Test
    fun `avatarInitials with two words returns first letters uppercased`() {
        avatarInitials("jane doe") shouldBe "JD"
    }

    @Test
    fun `avatarInitials with one word returns a single letter`() {
        avatarInitials("Plato") shouldBe "P"
    }

    @Test
    fun `avatarInitials with three words uses the first and last word`() {
        avatarInitials("Ada King Lovelace") shouldBe "AL"
    }

    @Test
    fun `avatarInitials with a hyphenated word takes only its first letter`() {
        avatarInitials("Jean-Luc Picard") shouldBe "JP"
    }

    @Test
    fun `avatarInitials ignores leading trailing and repeated whitespace`() {
        avatarInitials("  jane \t  doe  ") shouldBe "JD"
    }

    @Test
    fun `avatarInitials treats a non-breaking space as a word separator`() {
        avatarInitials("Jane\u00A0Doe") shouldBe "JD"
    }

    @Test
    fun `avatarInitials with null returns null`() {
        avatarInitials(null) shouldBe null
    }

    @Test
    fun `avatarInitials with blank name returns null`() {
        avatarInitials(" \t ") shouldBe null
    }

    @Test
    fun `avatarInitials with a leading emoji keeps the whole surrogate pair`() {
        avatarInitials("😀 Bob") shouldBe "😀B"
    }

    @Test
    fun `avatarInitials with a leading symbol shows it as is`() {
        avatarInitials("@jane") shouldBe "@"
    }

    @Test
    fun `avatarInitials with a space-less CJK name returns its first character`() {
        avatarInitials("山田太郎") shouldBe "山"
    }
}
