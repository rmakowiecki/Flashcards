package com.rossomak.flashcards.web.landing

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class BalancedLineWidthTest {

    /** A paragraph of [wordCount] words, each [WORD] pixels wide with its space, wrapped greedily. */
    private fun lineCountOf(wordCount: Int): (Int) -> Int = { width ->
        val wordsPerLine = maxOf(width / WORD, 1)
        (wordCount + wordsPerLine - 1) / wordsPerLine
    }

    @Test
    fun `a paragraph with a short last line is narrowed to even lines`() {
        // 11 words at 1000px: 10 on the first line, 1 on the second; balanced, 6 and 5.
        balancedLineWidth(maxWidth = 1000, lineCountAt = lineCountOf(wordCount = 11)) shouldBe 6 * WORD
    }

    @Test
    fun `a paragraph keeps its line count`() {
        val lineCount = lineCountOf(wordCount = 23)
        lineCount(balancedLineWidth(maxWidth = 1000, lineCountAt = lineCount)) shouldBe lineCount(1000)
    }

    @Test
    fun `a single line keeps the full width`() {
        balancedLineWidth(maxWidth = 1000, lineCountAt = lineCountOf(wordCount = 4)) shouldBe 1000
    }

    @Test
    fun `two explicit lines that each fit narrowly are narrowed to that width`() {
        // Each line fits at 100px; below that, each wraps.
        val hardBreakLineCount: (Int) -> Int = { width -> if (width >= WORD) 2 else 4 }
        balancedLineWidth(maxWidth = 1000, lineCountAt = hardBreakLineCount) shouldBe WORD
    }

    private companion object {
        const val WORD = 100
    }
}
