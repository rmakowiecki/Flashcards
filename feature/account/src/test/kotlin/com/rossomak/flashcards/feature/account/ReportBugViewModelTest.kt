package com.rossomak.flashcards.feature.account

import app.cash.turbine.test
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.InvalidDescription
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.BugReportSeverity
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Blocker
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Minor
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult.Failed
import com.rossomak.flashcards.core.domain.model.BugReportSubmissionResult.Sent
import com.rossomak.flashcards.core.domain.usecase.SubmitBugReportUseCase
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Confirm
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Dismiss
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.DraftChange
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.feature.account.ReportBugDestination.Back
import com.rossomak.flashcards.feature.account.ReportBugDialog.DiscardReport
import com.rossomak.flashcards.feature.account.ReportBugDialog.Severity
import com.rossomak.flashcards.feature.account.ReportBugMessage.ReportFailed
import com.rossomak.flashcards.feature.account.ReportBugMessage.ReportNoConnection
import com.rossomak.flashcards.feature.account.ReportBugMessage.ReportSent
import com.rossomak.flashcards.testutil.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportBugViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val submitBugReport: SubmitBugReportUseCase = mockk()

    private fun createViewModel() = ReportBugViewModel(submitBugReport = submitBugReport)

    private val params = SubmitBugReportUseCase.Params(description = REPORT_TEXT, severity = REPORT_SEVERITY)
    private val filledState = ReportBugScreenState(draftText = REPORT_TEXT, severity = REPORT_SEVERITY)

    private fun ReportBugViewModel.fill(draftText: String = REPORT_TEXT, severity: BugReportSeverity? = REPORT_SEVERITY) {
        onDescriptionChange(draftText)
        severity?.let { pickSeverity(it) }
    }

    private fun ReportBugViewModel.pickSeverity(severity: BugReportSeverity) {
        onDialogEvent(Open(Severity(draftState = state.value.severity)))
        onDialogEvent(DraftChange(Severity(draftState = severity)))
        onDialogEvent(Confirm)
    }

    private fun stubSubmit(result: BugReportSubmissionResult) {
        coEvery { submitBugReport(any()) } returns result
    }

    private fun stubSubmitHeldBy(gate: CompletableDeferred<BugReportSubmissionResult>) {
        coEvery { submitBugReport(any()) } coAnswers { gate.await() }
    }

    @Test
    fun `the screen starts blank with no severity picked`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.value shouldBe ReportBugScreenState()
    }

    @Test
    fun `editing the text and picking the severity updates the state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDescriptionChange(REPORT_TEXT)
        viewModel.pickSeverity(Blocker)

        viewModel.state.value shouldBe ReportBugScreenState(draftText = REPORT_TEXT, severity = Blocker)
    }

    @Test
    fun `edits are ignored while sending`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmitHeldBy(CompletableDeferred())
        val viewModel = createViewModel()
        viewModel.fill()
        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.onDescriptionChange("another text")
        viewModel.onDialogEvent(Open(Severity(draftState = REPORT_SEVERITY)))

        viewModel.state.value shouldBe filledState.copy(isSending = true)
    }

    @Test
    fun `opening the severity dialog before any pick starts with no card ticked`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onDialogEvent(Open(Severity(draftState = null)))

        viewModel.state.value shouldBe ReportBugScreenState(activeDialog = Severity(draftState = null))
    }

    @Test
    fun `a card ticked in the severity dialog is only a draft until it is confirmed`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(Severity(draftState = null)))

        viewModel.onDialogEvent(DraftChange(Severity(draftState = Blocker)))

        viewModel.state.value shouldBe ReportBugScreenState(activeDialog = Severity(draftState = Blocker))
    }

    @Test
    fun `confirming the severity dialog applies the draft and closes it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onDialogEvent(Open(Severity(draftState = null)))
        viewModel.onDialogEvent(DraftChange(Severity(draftState = Blocker)))

        viewModel.onDialogEvent(Confirm)

        viewModel.state.value shouldBe ReportBugScreenState(severity = Blocker)
    }

    @Test
    fun `the severity dialog opens on the chosen severity and a new pick replaces it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.pickSeverity(Minor)

        viewModel.onDialogEvent(Open(Severity(draftState = viewModel.state.value.severity)))
        viewModel.state.value.activeDialog shouldBe Severity(draftState = Minor)

        viewModel.onDialogEvent(DraftChange(Severity(draftState = Blocker)))
        viewModel.onDialogEvent(Confirm)

        viewModel.state.value shouldBe ReportBugScreenState(severity = Blocker)
    }

    @Test
    fun `dismissing the severity dialog drops the draft and keeps the chosen severity`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.pickSeverity(Minor)
        viewModel.onDialogEvent(Open(Severity(draftState = Minor)))
        viewModel.onDialogEvent(DraftChange(Severity(draftState = Blocker)))

        viewModel.onDialogEvent(Dismiss)

        viewModel.state.value shouldBe ReportBugScreenState(severity = Minor)
    }

    @Test
    fun `confirming an empty severity draft never clears the chosen severity`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.pickSeverity(Minor)
        viewModel.onDialogEvent(Open(Severity(draftState = null)))

        viewModel.onDialogEvent(Confirm)

        viewModel.state.value shouldBe ReportBugScreenState(severity = Minor)
    }

    @Test
    fun `sending with no severity does nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.fill(severity = null)

        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.state.value shouldBe ReportBugScreenState(draftText = REPORT_TEXT)
        coVerify(exactly = 0) { submitBugReport(any()) }
    }

    @Test
    fun `sending a description below the minimum does nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val tooShort = "a".repeat(BugReport.MIN_DESCRIPTION_LENGTH - 1)
        viewModel.fill(draftText = tooShort)

        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.state.value shouldBe filledState.copy(draftText = tooShort)
        coVerify(exactly = 0) { submitBugReport(any()) }
    }

    @Test
    fun `sending a description above the maximum does nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val tooLong = "a".repeat(BugReport.MAX_DESCRIPTION_LENGTH + 1)
        viewModel.fill(draftText = tooLong)

        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.state.value shouldBe filledState.copy(draftText = tooLong)
        coVerify(exactly = 0) { submitBugReport(any()) }
    }

    @Test
    fun `a valid send marks the screen sending and submits the draft`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmitHeldBy(CompletableDeferred())
        val viewModel = createViewModel()
        viewModel.fill()

        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.state.value shouldBe filledState.copy(isSending = true)
        coVerify(exactly = 1) { submitBugReport(params) }
    }

    @Test
    fun `the untrimmed draft is what reaches the use case`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmitHeldBy(CompletableDeferred())
        val viewModel = createViewModel()
        val paddedText = "  $REPORT_TEXT  "
        viewModel.fill(draftText = paddedText)

        viewModel.onSendClick()
        advanceUntilIdle()

        coVerify(exactly = 1) { submitBugReport(params.copy(description = paddedText)) }
    }

    @Test
    fun `a sent report locks the form and emits ReportSent`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmit(Sent)
        val viewModel = createViewModel()
        viewModel.fill()

        viewModel.messages.test {
            viewModel.onSendClick()

            awaitItem() shouldBe ReportSent
        }
        viewModel.state.value shouldBe filledState.copy(isSent = true)
        coVerify(exactly = 1) { submitBugReport(params) }
    }

    @Test
    fun `the screen closes once the confirmation has been shown`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmit(Sent)
        val viewModel = createViewModel()
        viewModel.fill()

        viewModel.events.test {
            viewModel.onSendClick()
            advanceTimeBy(ReportBugViewModel.SENT_CONFIRMATION_DURATION - 1.milliseconds)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1.milliseconds)
            runCurrent()

            awaitItem() shouldBe Back
        }
        coVerify(exactly = 1) { submitBugReport(params) }
    }

    @Test
    fun `a lost connection keeps the form and emits ReportNoConnection`() = runTest(mainDispatcherRule.testDispatcher) {
        assertFailureKeepsFormAndEmits(NoConnection, ReportNoConnection)
    }

    @Test
    fun `a service error keeps the form and emits the generic ReportFailed`() = runTest(mainDispatcherRule.testDispatcher) {
        assertFailureKeepsFormAndEmits(ServiceError, ReportFailed)
    }

    @Test
    fun `an invalid description keeps the form and emits the generic ReportFailed`() = runTest(mainDispatcherRule.testDispatcher) {
        assertFailureKeepsFormAndEmits(InvalidDescription, ReportFailed)
    }

    private suspend fun assertFailureKeepsFormAndEmits(reason: BugReportFailureReason, expected: ReportBugMessage) {
        stubSubmit(Failed(reason))
        val viewModel = createViewModel()
        viewModel.fill()

        viewModel.messages.test {
            viewModel.onSendClick()

            awaitItem() shouldBe expected
        }
        viewModel.state.value shouldBe filledState
        coVerify(exactly = 1) { submitBugReport(params) }
    }

    @Test
    fun `a failed send can be retried`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmit(Failed(NoConnection))
        val viewModel = createViewModel()
        viewModel.fill()
        viewModel.onSendClick()
        advanceUntilIdle()

        stubSubmit(Sent)
        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.state.value shouldBe filledState.copy(isSent = true)
        coVerify(exactly = 2) { submitBugReport(params) }
    }

    @Test
    fun `a second Send while sending submits once`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<BugReportSubmissionResult>()
        stubSubmitHeldBy(gate)
        val viewModel = createViewModel()
        viewModel.fill()

        viewModel.onSendClick()
        viewModel.onSendClick()
        gate.complete(Sent)
        advanceUntilIdle()

        coVerify(exactly = 1) { submitBugReport(params) }
    }

    @Test
    fun `closing a blank screen leaves at once`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onCloseClick()

            awaitItem() shouldBe Back
        }
        viewModel.state.value shouldBe ReportBugScreenState()
    }

    @Test
    fun `closing a screen with only a severity and whitespace leaves at once`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.fill(draftText = "   ", severity = Blocker)

        viewModel.events.test {
            viewModel.onCloseClick()

            awaitItem() shouldBe Back
        }
        viewModel.state.value.activeDialog shouldBe null
    }

    @Test
    fun `closing a screen with text asks to discard it`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.fill()

        viewModel.events.test {
            viewModel.onCloseClick()

            expectNoEvents()
        }
        viewModel.state.value shouldBe filledState.copy(activeDialog = DiscardReport)
    }

    @Test
    fun `keeping the draft on the confirmation dismisses it and keeps the form`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.fill()
        viewModel.onCloseClick()

        viewModel.onDialogEvent(Dismiss)

        viewModel.state.value shouldBe filledState
    }

    @Test
    fun `confirming the discard leaves without sending`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.fill()
        viewModel.onCloseClick()

        viewModel.events.test {
            viewModel.onDialogEvent(Confirm)

            awaitItem() shouldBe Back
        }
        coVerify(exactly = 0) { submitBugReport(any()) }
    }

    @Test
    fun `closing while sending is ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmitHeldBy(CompletableDeferred())
        val viewModel = createViewModel()
        viewModel.fill()
        viewModel.onSendClick()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onCloseClick()

            expectNoEvents()
        }
        viewModel.state.value shouldBe filledState.copy(isSending = true)
        coVerify(exactly = 1) { submitBugReport(params) }
    }

    @Test
    fun `closing after the report was sent leaves at once without asking`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSubmit(Sent)
        val viewModel = createViewModel()
        viewModel.fill()
        viewModel.onSendClick()
        runCurrent()

        viewModel.events.test {
            viewModel.onCloseClick()

            awaitItem() shouldBe Back
        }
        viewModel.state.value.activeDialog shouldBe null
        coVerify(exactly = 1) { submitBugReport(params) }
    }

    private companion object {
        const val REPORT_TEXT = "The timer freezes after a rotation."
        val REPORT_SEVERITY = Minor
    }
}
