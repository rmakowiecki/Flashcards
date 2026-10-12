package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetCategoryUseCaseTest {

    private val repository = FakeFlashcardRepository()
    private val getCategory = GetCategoryUseCase(repository)

    private val android = Category(
        id = "android",
        name = "Android",
        order = 0,
        subcategoryCount = 1,
        iconSvg = "<svg/>",
        color = "#3DDC84",
        featuredSubcategoryNames = emptyList(),
    )

    @Test
    fun `returns the fetched category`() = runTest {
        repository.categoriesByIdsToReturn = Result.success(listOf(android))

        getCategory(android.id).getOrThrow() shouldBe android
        repository.fetchedCategoryIds shouldContainExactly listOf(setOf(android.id))
    }

    @Test
    fun `a missing category fails with NoSuchElementException`() = runTest {
        repository.categoriesByIdsToReturn = Result.success(emptyList())

        getCategory(android.id).exceptionOrNull().shouldBeInstanceOf<NoSuchElementException>()
    }
}
