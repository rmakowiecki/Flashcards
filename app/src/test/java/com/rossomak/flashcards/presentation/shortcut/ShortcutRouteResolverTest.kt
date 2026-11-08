package com.rossomak.flashcards.presentation.shortcut

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.model.Subcategory
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.usecase.GetCategoryUseCase
import com.rossomak.flashcards.core.domain.usecase.GetSubcategoryUseCase
import com.rossomak.flashcards.feature.browse.details.category.CategoryDetailsRoute
import com.rossomak.flashcards.feature.browse.details.subcategory.SubcategoryDetailsRoute
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShortcutRouteResolverTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val resolver = shortcutRouteResolver(flashcardRepository)

    private val android = shortcutCategory(id = ANDROID_ID, name = ANDROID_NAME)
    private val coroutines = shortcutSubcategory(id = COROUTINES_ID, name = COROUTINES_NAME, categoryId = ANDROID_ID, categoryName = ANDROID_NAME)

    @Test
    fun `a category route resolves to its Category Details`() = runTest {
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(android))

        resolver.resolve(CATEGORY_ROUTE) shouldBe ShortcutDestination.ToCategoryDetails(
            CategoryDetailsRoute(categoryId = ANDROID_ID, categoryName = ANDROID_NAME),
        )
    }

    @Test
    fun `a subcategory route resolves to its Subcategory Details`() = runTest {
        flashcardRepository.subcategoriesByIdsToReturn = Result.success(listOf(coroutines))

        resolver.resolve(SUBCATEGORY_ROUTE) shouldBe ShortcutDestination.ToSubcategoryDetails(
            SubcategoryDetailsRoute(
                categoryId = ANDROID_ID,
                categoryName = ANDROID_NAME,
                subcategoryId = COROUTINES_ID,
                subcategoryName = COROUTINES_NAME,
            ),
        )
    }

    @Test
    fun `a deleted target resolves to nothing`() = runTest {
        flashcardRepository.categoriesByIdsToReturn = Result.success(emptyList())

        resolver.resolve(CATEGORY_ROUTE).shouldBeNull()
    }

    @Test
    fun `a subcategory under another category resolves to nothing`() = runTest {
        flashcardRepository.subcategoriesByIdsToReturn = Result.success(listOf(coroutines.copy(categoryId = "python")))

        resolver.resolve(SUBCATEGORY_ROUTE).shouldBeNull()
    }

    @Test
    fun `a malformed route resolves to nothing`() = runTest {
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(android))

        resolver.resolve("/study/android").shouldBeNull()
    }

    @Test
    fun `a lookup slower than the timeout resolves to nothing`() = runTest {
        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(android))
        flashcardRepository.byIdsFetchDelay = 2.seconds

        resolver.resolve(CATEGORY_ROUTE).shouldBeNull()
        testScheduler.currentTime shouldBe 1_000L
    }

    private companion object {
        const val ANDROID_ID = "android"
        const val ANDROID_NAME = "Android"
        const val COROUTINES_ID = "coroutines"
        const val COROUTINES_NAME = "Coroutines"
        const val CATEGORY_ROUTE = "/study/category/$ANDROID_ID"
        const val SUBCATEGORY_ROUTE = "/study/category/$ANDROID_ID/subcategory/$COROUTINES_ID"
    }
}

// Shared with the other tests that resolve a launcher shortcut.
internal fun shortcutRouteResolver(repository: FlashcardRepository) =
    ShortcutRouteResolver(GetCategoryUseCase(repository), GetSubcategoryUseCase(repository))

internal fun shortcutCategory(id: String, name: String) = Category(
    id = id,
    name = name,
    order = 0,
    subcategoryCount = 1,
    iconSvg = null,
    color = null,
    featuredSubcategoryNames = emptyList(),
)

internal fun shortcutSubcategory(id: String, name: String, categoryId: String, categoryName: String) = Subcategory(
    id = id,
    name = name,
    categoryId = categoryId,
    categoryName = categoryName,
    order = 0,
    cardCount = 1,
)
