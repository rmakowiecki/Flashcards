package com.rossomak.flashcards.feature.study.chrome

import com.rossomak.flashcards.core.domain.model.Flashcard
import io.kotest.matchers.shouldBe
import org.junit.Test

class StudySessionCardTitleTest {

    private fun flashcard(id: String, subcategoryId: String): Flashcard = Flashcard(
        id = id,
        subcategoryId = subcategoryId,
        tags = listOf("General"),
        question = "question-$id",
        answer = "answer-$id",
        difficulty = 5,
        questionCode = null,
        answerCode = null,
        questionSpoken = null,
        answerSpoken = null,
        extendedContext = null,
    )

    @Test
    fun `combines the category and the current card's own subcategory name`() {
        val title = studySessionCardTitle(
            categoryName = "Android",
            currentCard = flashcard(id = "card-1", subcategoryId = "android-compose"),
            subcategoryNameById = mapOf("android-compose" to "Compose"),
            separator = " · ",
        )

        title shouldBe "Android · Compose"
    }

    @Test
    fun `falls back to just the category name when the current card's subcategory isn't known`() {
        val title = studySessionCardTitle(
            categoryName = "Android",
            currentCard = flashcard(id = "card-1", subcategoryId = "android-coroutines"),
            subcategoryNameById = mapOf("android-compose" to "Compose"),
            separator = " · ",
        )

        title shouldBe "Android"
    }

    @Test
    fun `falls back to just the category name when there is no current card`() {
        val title = studySessionCardTitle(
            categoryName = "Android",
            currentCard = null,
            subcategoryNameById = mapOf("android-compose" to "Compose"),
            separator = " · ",
        )

        title shouldBe "Android"
    }
}
