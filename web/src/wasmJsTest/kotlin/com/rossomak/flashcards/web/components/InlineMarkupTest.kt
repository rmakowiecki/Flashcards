package com.rossomak.flashcards.web.components

import androidx.compose.ui.text.LinkAnnotation
import com.rossomak.flashcards.core.designsystem.theme.EmphasizedSpanStyle
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class InlineMarkupTest {

    @Test
    fun `plain text passes through unchanged`() {
        val text = parseInlineMarkup(PLAIN)

        text.text shouldBe PLAIN
        text.spanStyles.shouldBeEmpty()
    }

    @Test
    fun `strong text is emphasized without its tags`() {
        val text = parseInlineMarkup("<strong>$LEAD</strong> $PLAIN")

        text.text shouldBe "$LEAD $PLAIN"
        text.spanStyles.map { Triple(it.item, it.start, it.end) } shouldContainExactly listOf(Triple(EmphasizedSpanStyle, 0, LEAD.length))
    }

    @Test
    fun `anchor becomes a url link over its label`() {
        val text = parseInlineMarkup("See <a href=\"$URL\">$LABEL</a>.")

        text.text shouldBe "See $LABEL."
        val link = text.getLinkAnnotations(0, text.length).single()
        (link.item as LinkAnnotation.Url).url shouldBe URL
        link.start shouldBe LINK_START
        link.end shouldBe LINK_START + LABEL.length
    }

    @Test
    fun `unsupported tag throws`() {
        assertFailsWith<IllegalArgumentException> { parseInlineMarkup("<em>$PLAIN</em>") }
    }

    @Test
    fun `unclosed tag throws`() {
        assertFailsWith<IllegalArgumentException> { parseInlineMarkup("<strong>$PLAIN") }
    }

    @Test
    fun `mismatched closing tag throws`() {
        assertFailsWith<IllegalArgumentException> { parseInlineMarkup("<strong>$PLAIN</a>") }
    }

    private companion object {
        const val PLAIN = "Study data stays yours."
        const val LEAD = "Account data."
        const val URL = "/delete-account"
        const val LABEL = "account deletion page"
        const val LINK_START = 4
    }
}
