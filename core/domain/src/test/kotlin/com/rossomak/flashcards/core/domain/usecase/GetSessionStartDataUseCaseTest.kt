package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [GetSessionStartDataUseCase] is a pure fan-out/merge composition of [GetFlashcardsUseCase] and
 * [GetSubcategoryProgressDetailsUseCase] — every rule about *what* each one returns is pinned on their own
 * tests; this file only covers the composing itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetSessionStartDataUseCaseTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val useCase = GetSessionStartDataUseCase(
        GetFlashcardsUseCase(flashcardRepository),
        GetSubcategoryProgressDetailsUseCase(cardProgressRepository),
    )

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
    fun `issues exactly one flashcards read and one progress read per subcategory`() = runTest {
        val subcategoryIds = listOf("sub-1", "sub-2", "sub-3")
        subcategoryIds.forEach { id -> flashcardRepository.flashcardsBySubcategory[id] = Result.success(listOf(flashcard("card-$id", id))) }

        useCase(subcategoryIds)

        flashcardRepository.fetchedSubcategoryIds shouldBe subcategoryIds
        cardProgressRepository.requestedSubcategoryIds.toSet() shouldBe subcategoryIds.toSet()
        cardProgressRepository.requestedSubcategoryIds.size shouldBe subcategoryIds.size
    }

    @Test
    fun `merges flashcards from every subcategory into one list`() = runTest {
        flashcardRepository.flashcardsBySubcategory["sub-1"] = Result.success(listOf(flashcard("card-1", "sub-1")))
        flashcardRepository.flashcardsBySubcategory["sub-2"] = Result.success(listOf(flashcard("card-2", "sub-2")))

        val result = useCase(listOf("sub-1", "sub-2"))

        result.flashcardsResult.getOrThrow().map { it.id } shouldBe listOf("card-1", "card-2")
    }

    @Test
    fun `any subcategory's flashcard failure fails the whole flashcardsResult`() = runTest {
        val error = IllegalStateException("boom")
        flashcardRepository.flashcardsBySubcategory["sub-1"] = Result.success(listOf(flashcard("card-1", "sub-1")))
        flashcardRepository.flashcardsBySubcategory["sub-2"] = Result.failure(error)

        val result = useCase(listOf("sub-1", "sub-2"))

        result.flashcardsResult.isFailure shouldBe true
        result.flashcardsResult.exceptionOrNull() shouldBe error
    }

    @Test
    fun `merges prior progress entries from every subcategory into one map keyed by card id`() = runTest {
        val firstStudiedAt = Instant.parse("2026-09-06T10:00:00Z")
        cardProgressRepository.seed(
            SubcategoryProgressDetails(
                subcategoryId = "sub-1",
                categoryId = "cat-1",
                cards = mapOf("card-1" to CardProgressEntry(state = FlashcardStudyProgressState.Mastered, firstStudiedAt = firstStudiedAt, masteredAt = firstStudiedAt)),
            ),
        )
        cardProgressRepository.seed(
            SubcategoryProgressDetails(
                subcategoryId = "sub-2",
                categoryId = "cat-1",
                cards = mapOf("card-2" to CardProgressEntry(state = FlashcardStudyProgressState.Failed, firstStudiedAt = firstStudiedAt, masteredAt = null)),
            ),
        )

        val result = useCase(listOf("sub-1", "sub-2"))

        result.priorProgressByCardId.keys shouldBe setOf("card-1", "card-2")
        result.priorProgressByCardId.getValue("card-1").state shouldBe FlashcardStudyProgressState.Mastered
        result.priorProgressByCardId.getValue("card-2").state shouldBe FlashcardStudyProgressState.Failed
    }

    @Test
    fun `a failed progress read contributes no entries and never fails flashcardsResult`() = runTest {
        flashcardRepository.flashcardsBySubcategory["sub-1"] = Result.success(listOf(flashcard("card-1", "sub-1")))
        cardProgressRepository.resultToReturn = Result.failure(IllegalStateException("offline"))

        val result = useCase(listOf("sub-1"))

        result.flashcardsResult.isSuccess shouldBe true
        result.priorProgressByCardId shouldBe emptyMap()
    }

    @Test
    fun `a subcategory the user never studied contributes no entries`() = runTest {
        flashcardRepository.flashcardsBySubcategory["sub-1"] = Result.success(listOf(flashcard("card-1", "sub-1")))

        val result = useCase(listOf("sub-1"))

        result.priorProgressByCardId shouldBe emptyMap()
    }
}
