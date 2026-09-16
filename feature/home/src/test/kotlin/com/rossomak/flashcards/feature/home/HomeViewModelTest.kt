package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.core.domain.repository.FakeFlashcardRepository
import com.rossomak.flashcards.core.domain.repository.FakeUserFavoritesRepository
import com.rossomak.flashcards.core.domain.usecase.ObserveFavoriteItemsUseCase
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flashcardRepository = FakeFlashcardRepository()
    private val userFavoritesRepository = FakeUserFavoritesRepository()
    private val observeFavoriteItems = ObserveFavoriteItemsUseCase(userFavoritesRepository, flashcardRepository)

    private fun createViewModel(): HomeViewModel = HomeViewModel(observeFavoriteItems)

    @Test
    fun `initial state exposes the default home screen state`() {
        val viewModel = createViewModel()

        viewModel.state.value shouldBe HomeScreenState()
    }
}
