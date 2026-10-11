package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteCategory
import com.rossomak.flashcards.core.domain.model.FavoriteItem.FavoriteSubcategory
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Resolved
import com.rossomak.flashcards.core.domain.model.FavoriteItemsResult.Unresolved
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val CATEGORY_ID = "android"
private const val SUBCATEGORY_ID = "compose"

class ObserveFavoriteItemsUseCaseTest {

    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val flashcardRepository = FakeFlashcardRepository()

    private val category = Category(
        id = CATEGORY_ID,
        name = "Android",
        order = 0,
        subcategoryCount = 3,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )
    private val subcategory = Subcategory(
        id = SUBCATEGORY_ID,
        name = "Compose",
        categoryId = CATEGORY_ID,
        categoryName = "Android",
        order = 0,
        cardCount = 10,
    )

    private fun createUseCase() = ObserveFavoriteItemsUseCase(userFavoritesRepository, flashcardRepository)

    @Test
    fun `fetched Favorites are Resolved newest first`() = runTest {
        userFavoritesRepository.setCategoryFavorite(CATEGORY_ID, isFavorite = true)
        userFavoritesRepository.setSubcategoryFavorite(SUBCATEGORY_ID, isFavorite = true)
        flashcardRepository.subcategoriesByIdsToReturn = Result.success(listOf(subcategory))
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(category))

        val result = createUseCase()().first()

        val items = result.shouldBeInstanceOf<Resolved>().items
        items.map { it::class } shouldBe listOf(FavoriteSubcategory::class, FavoriteCategory::class)
    }

    @Test
    fun `no Favorite ids are Resolved to an empty list without fetching`() = runTest {
        flashcardRepository.subcategoriesByIdsToReturn = Result.failure(IllegalStateException("must not be fetched"))
        flashcardRepository.categoriesByIdsToReturn = Result.failure(IllegalStateException("must not be fetched"))

        createUseCase()().first() shouldBe Resolved(emptyList())
    }

    @Test
    fun `a Subcategory fetch that runs out of retries is Unresolved`() = runTest {
        userFavoritesRepository.setSubcategoryFavorite(SUBCATEGORY_ID, isFavorite = true)
        flashcardRepository.subcategoriesByIdsToReturn = Result.failure(IllegalStateException("subcategories fetch failed"))
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(category))

        createUseCase()().first() shouldBe Unresolved
    }

    @Test
    fun `a Category fetch that runs out of retries is Unresolved`() = runTest {
        userFavoritesRepository.setCategoryFavorite(CATEGORY_ID, isFavorite = true)
        flashcardRepository.categoriesByIdsToReturn = Result.failure(IllegalStateException("categories fetch failed"))

        createUseCase()().first() shouldBe Unresolved
    }
}
