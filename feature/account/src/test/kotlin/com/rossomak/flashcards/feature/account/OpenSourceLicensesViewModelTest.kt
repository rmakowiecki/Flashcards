package com.rossomak.flashcards.feature.account

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rossomak.flashcards.feature.account.OpenSourceLicensesMessage.OpenLinkFailed
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenSourceLicensesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle = SavedStateHandle()

    private fun createViewModel() = OpenSourceLicensesViewModel(savedStateHandle)

    @Test
    fun `no library is selected at first`() = runTest(mainDispatcherRule.testDispatcher) {
        createViewModel().state.value shouldBe OpenSourceLicensesScreenState(selectedLibraryId = null)
    }

    @Test
    fun `clicking a library selects it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onLibraryClick(LIBRARY_ID)
        advanceUntilIdle()

        viewModel.state.value shouldBe OpenSourceLicensesScreenState(selectedLibraryId = LIBRARY_ID)
    }

    @Test
    fun `dismissing the detail clears the selection`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onLibraryClick(LIBRARY_ID)
        advanceUntilIdle()

        viewModel.onDetailDismiss()
        advanceUntilIdle()

        viewModel.state.value shouldBe OpenSourceLicensesScreenState(selectedLibraryId = null)
    }

    @Test
    fun `the selection survives a recreated view model`() = runTest(mainDispatcherRule.testDispatcher) {
        createViewModel().onLibraryClick(LIBRARY_ID)

        createViewModel().state.value shouldBe OpenSourceLicensesScreenState(selectedLibraryId = LIBRARY_ID)
    }

    @Test
    fun `a failed link launch emits the failure message`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.messages.test {
            viewModel.onOpenLinkFailed()

            awaitItem() shouldBe OpenLinkFailed
        }
    }

    private companion object {
        const val LIBRARY_ID = "com.example:lib"
    }
}
