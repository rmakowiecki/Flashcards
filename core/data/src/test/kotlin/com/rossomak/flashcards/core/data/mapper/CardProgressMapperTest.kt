package com.rossomak.flashcards.core.data.mapper

import com.google.firebase.Timestamp
import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDetailsDto
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import io.kotest.matchers.shouldBe
import java.util.Date
import org.junit.Test

class CardProgressMapperTest {

    @Test
    fun `toDomain maps a card entry with both timestamps present`() {
        val firstStudiedAt = Timestamp(Date(1_000L))
        val masteredAt = Timestamp(Date(2_000L))
        val dto = SubcategoryProgressDetailsDto(
            categoryId = "cat-1",
            cards = mapOf(
                "card-1" to CardProgressEntryDto(
                    state = FlashcardStudyProgressState.Mastered.name,
                    firstStudiedAt = firstStudiedAt,
                    masteredAt = masteredAt,
                ),
            ),
        )

        val domain = dto.toDomain("sub-1")

        domain.subcategoryId shouldBe "sub-1"
        domain.categoryId shouldBe "cat-1"
        val entry = domain.cards.getValue("card-1")
        entry.state shouldBe FlashcardStudyProgressState.Mastered
        entry.firstStudiedAt shouldBe firstStudiedAt.toDate().toInstant()
        entry.masteredAt shouldBe masteredAt.toDate().toInstant()
    }

    @Test
    fun `toDomain maps a never-mastered card entry with a null masteredAt`() {
        val dto = SubcategoryProgressDetailsDto(
            categoryId = "cat-1",
            cards = mapOf(
                "card-1" to CardProgressEntryDto(
                    state = FlashcardStudyProgressState.Seen.name,
                    firstStudiedAt = Timestamp(Date(1_000L)),
                    masteredAt = null,
                ),
            ),
        )

        val domain = dto.toDomain("sub-1")

        domain.cards.getValue("card-1").masteredAt shouldBe null
    }

    @Test
    fun `toDomain drops an entry with an unrecognized state instead of crashing`() {
        val dto = SubcategoryProgressDetailsDto(
            categoryId = "cat-1",
            cards = mapOf("card-1" to CardProgressEntryDto(state = "NotAState", firstStudiedAt = Timestamp(Date(1_000L)))),
        )

        val domain = dto.toDomain("sub-1")

        domain.cards.keys shouldBe emptySet()
    }

    @Test
    fun `toDomain drops an entry with no firstStudiedAt instead of crashing`() {
        val dto = SubcategoryProgressDetailsDto(
            categoryId = "cat-1",
            cards = mapOf("card-1" to CardProgressEntryDto(state = FlashcardStudyProgressState.Seen.name, firstStudiedAt = null)),
        )

        val domain = dto.toDomain("sub-1")

        domain.cards.keys shouldBe emptySet()
    }
}
