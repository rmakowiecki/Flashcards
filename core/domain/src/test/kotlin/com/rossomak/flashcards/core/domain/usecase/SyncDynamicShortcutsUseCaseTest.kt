package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.Category
import com.rossomak.flashcards.core.domain.repository.FakeAppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val CATEGORY_ID = "android"
private const val UNFETCHED_CATEGORY_ID = "python"

class SyncDynamicShortcutsUseCaseTest {

    private val appShortcutsRepository = FakeAppShortcutsRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val flashcardRepository = FakeFlashcardRepository()
    private val observeFavoriteItems = ObserveFavoriteItemsUseCase(userFavoritesRepository, flashcardRepository)

    private val category = Category(
        id = CATEGORY_ID,
        name = "Android",
        order = 0,
        subcategoryCount = 3,
        iconSvg = null,
        color = null,
        featuredSubcategoryNames = emptyList(),
    )

    @Test
    fun `Favorites that cannot be fetched leave the shortcuts untouched and a later fetched emission syncs`() = runTest {
        userFavoritesRepository.setCategoryFavorite(CATEGORY_ID, isFavorite = true)
        flashcardRepository.categoriesByIdsToReturn = Result.failure(IllegalStateException("categories fetch failed"))
        val syncJob = launch { SyncDynamicShortcutsUseCase(observeFavoriteItems, appShortcutsRepository)() }
        advanceUntilIdle()
        appShortcutsRepository.syncedFavorites shouldBe emptyList()

        flashcardRepository.categoriesByIdsToReturn = Result.success(listOf(category))
        userFavoritesRepository.setCategoryFavorite(UNFETCHED_CATEGORY_ID, isFavorite = true)
        advanceUntilIdle()
        appShortcutsRepository.syncedFavorites.map { shortcuts -> shortcuts.map { it.id } } shouldBe listOf(listOf("category:$CATEGORY_ID"))

        syncJob.cancel()
    }
}
