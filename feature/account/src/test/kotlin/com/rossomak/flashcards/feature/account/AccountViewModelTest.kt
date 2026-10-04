package com.rossomak.flashcards.feature.account

import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(): AccountViewModel = AccountViewModel()

    @Test
    fun `the state starts with no user data and no dialog`() {
        val viewModel = createViewModel()

        viewModel.state.value shouldBe AccountScreenState()
    }
}
