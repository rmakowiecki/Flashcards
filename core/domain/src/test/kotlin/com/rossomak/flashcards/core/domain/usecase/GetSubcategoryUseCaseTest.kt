package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetSubcategoryUseCaseTest {

    private val repository = FakeFlashcardRepository()
    private val getSubcategory = GetSubcategoryUseCase(repository)

    private val coroutines = Subcategory(
        id = "coroutines",
        name = "Coroutines",
        categoryId = "android",
        categoryName = "Android",
        order = 0,
        cardCount = 1,
    )

    @Test
    fun `returns the fetched subcategory`() = runTest {
        repository.subcategoriesByIdsToReturn = Result.success(listOf(coroutines))

        getSubcategory(coroutines.id).getOrThrow() shouldBe coroutines
    }

    @Test
    fun `a missing subcategory fails with NoSuchElementException`() = runTest {
        repository.subcategoriesByIdsToReturn = Result.success(emptyList())

        getSubcategory(coroutines.id).exceptionOrNull().shouldBeInstanceOf<NoSuchElementException>()
    }
}
