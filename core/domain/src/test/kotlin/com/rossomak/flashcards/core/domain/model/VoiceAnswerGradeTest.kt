package com.rossomak.flashcards.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class VoiceAnswerGradeTest {

    private fun grade(gradePercent: Int): VoiceAnswerGrade =
        voiceAnswerGrade(gradePercent = gradePercent)

    @Test
    fun `a grade below 30 percent maps to Failed`() {
        grade(29).toFlashcardAttemptRating() shouldBe FlashcardAttemptRating.Failed
    }

    @Test
    fun `a grade of exactly 30 percent maps to PartiallyCorrect`() {
        grade(30).toFlashcardAttemptRating() shouldBe FlashcardAttemptRating.PartiallyCorrect
    }

    @Test
    fun `a grade of 74 percent maps to PartiallyCorrect`() {
        grade(74).toFlashcardAttemptRating() shouldBe FlashcardAttemptRating.PartiallyCorrect
    }

    @Test
    fun `a grade of exactly 75 percent maps to Correct`() {
        grade(75).toFlashcardAttemptRating() shouldBe FlashcardAttemptRating.Correct
    }

    @Test
    fun `a grade of 100 percent maps to Correct`() {
        grade(100).toFlashcardAttemptRating() shouldBe FlashcardAttemptRating.Correct
    }
}
