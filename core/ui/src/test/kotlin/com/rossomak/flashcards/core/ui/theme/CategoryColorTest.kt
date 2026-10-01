package com.rossomak.flashcards.core.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class CategoryColorTest {

    @Test
    fun `toCategoryColor with valid RRGGBB hex parses to opaque Color`() {
        val result = "#6B2FA0".toCategoryColor()

        result shouldBe Color(0xFF6B2FA0)
    }

    @Test
    fun `toCategoryColor with lowercase hex digits parses correctly`() {
        val result = "#0277bd".toCategoryColor()

        result shouldBe Color(0xFF0277BD)
    }

    @Test
    fun `toCategoryColor with missing hash throws`() {
        shouldThrow<IllegalArgumentException> { "6B2FA0".toCategoryColor() }
    }

    @Test
    fun `toCategoryColor with 8-digit ARGB hex throws`() {
        shouldThrow<IllegalArgumentException> { "#FF6B2FA0".toCategoryColor() }
    }

    @Test
    fun `toCategoryColor with non-hex characters throws`() {
        shouldThrow<IllegalArgumentException> { "#GGGGGG".toCategoryColor() }
    }

    @Test
    fun `toCategoryColor with empty string throws`() {
        shouldThrow<IllegalArgumentException> { "".toCategoryColor() }
    }

    @Test
    fun `toCategoryColorOrNull parses a valid hex`() {
        "#6B2FA0".toCategoryColorOrNull() shouldBe Color(0xFF6B2FA0)
    }

    @Test
    fun `toCategoryColorOrNull is null for a malformed hex`() {
        "#GGGGGG".toCategoryColorOrNull() shouldBe null
    }

    @Test
    fun `toCategoryColorOrNull is null for a null color`() {
        val noColor: String? = null

        noColor.toCategoryColorOrNull() shouldBe null
    }
}
