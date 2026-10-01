package com.rossomak.flashcards.feature.home

import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(): HomeViewModel = HomeViewModel()

    @Test
    fun `initial state reports progress as unresolved`() {
        val viewModel = createViewModel()

        viewModel.state.value.isProgressResolved shouldBe false
    }
}
