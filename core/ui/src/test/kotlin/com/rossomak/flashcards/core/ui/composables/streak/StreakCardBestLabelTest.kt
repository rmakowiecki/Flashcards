package com.rossomak.flashcards.core.ui.composables.streak

import androidx.compose.ui.text.AnnotatedString
import com.rossomak.flashcards.core.designsystem.theme.EmphasizedSpanStyle
import io.kotest.matchers.shouldBe
import org.junit.Test

class StreakCardBestLabelTest {

    @Test
    fun `emphasizeValue emphasizes the value and its unit letters`() {
        val label = emphasizeValue(text = "Best: 14d", value = "14")

        label.text shouldBe "Best: 14d"
        label.spanStyles shouldBe listOf(AnnotatedString.Range(EmphasizedSpanStyle, start = 6, end = 9))
    }

    @Test
    fun `emphasizeValue stops at the first character after the unit that is not a letter`() {
        val label = emphasizeValue(text = "14d best", value = "14")

        label.spanStyles shouldBe listOf(AnnotatedString.Range(EmphasizedSpanStyle, start = 0, end = 3))
    }

    @Test
    fun `emphasizeValue with no unit emphasizes the value only`() {
        val label = emphasizeValue(text = "Best: 14", value = "14")

        label.spanStyles shouldBe listOf(AnnotatedString.Range(EmphasizedSpanStyle, start = 6, end = 8))
    }

    @Test
    fun `emphasizeValue with the value missing returns the text plain`() {
        val label = emphasizeValue(text = "Best: 14d", value = "7")

        label shouldBe AnnotatedString("Best: 14d")
    }
}
