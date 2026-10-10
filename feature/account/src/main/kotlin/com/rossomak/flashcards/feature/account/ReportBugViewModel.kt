package com.rossomak.flashcards.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.InvalidDescription
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.BugReportFailureReason.ServiceError
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
import com.rossomak.flashcards.feature.account.ReportBugSubmissionStatus.Delivered
import com.rossomak.flashcards.feature.account.ReportBugSubmissionStatus.Idle
import com.rossomak.flashcards.feature.account.ReportBugSubmissionStatus.Sending
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReportBugViewModel @Inject constructor(
    private val submitBugReport: SubmitBugReportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportBugScreenState())
    val state: StateFlow<ReportBugScreenState> = _state.asStateFlow()

    private val eventChannel = Channel<ReportBugDestination>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val _messages = MutableSharedFlow<ReportBugMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<ReportBugMessage> = _messages.asSharedFlow()

    private var hasLeft = false

    fun onDescriptionChange(text: String) {
        _state.update { if (it.isLocked) it else it.copy(draftText = text) }
    }

    /** Ignored while sending; asks first when there is text to lose. */
    fun onCloseClick() {
        with(_state.value) {
            when {
                submissionStatus == Sending -> Unit
                submissionStatus == Delivered || BugReport.descriptionLength(draftText) == 0 -> leave()
                else -> _state.update { it.copy(activeDialog = DiscardReport) }
            }
        }
    }

    fun onSendClick() {
        val report = _state.value
        val severity = report.severity?.takeIf { report.canSend } ?: return
        // Before launching, so a second Send tap sees the form as sending.
        _state.update { it.copy(submissionStatus = Sending) }
        viewModelScope.launch {
            when (val result = submitBugReport(SubmitBugReportUseCase.Params(report.draftText, severity))) {
                Sent -> {
                    _state.update { it.copy(submissionStatus = Delivered) }
                    _messages.tryEmit(ReportSent)
                    delay(SENT_CONFIRMATION_DURATION)
                    leave()
                }
                is Failed -> {
                    _state.update { it.copy(submissionStatus = Idle) }
                    _messages.tryEmit(result.reason.toMessage())
                }
            }
        }
    }

    fun onDialogEvent(event: ReportBugDialogEvent) {
        when (event) {
            is Open -> _state.update { if (it.isLocked) it else it.copy(activeDialog = event.dialog) }
            is DraftChange -> _state.update { it.copy(activeDialog = event.dialog) }
            Confirm -> confirmDialog()
            Dismiss -> _state.update { it.copy(activeDialog = null) }
        }
    }

    private fun confirmDialog() {
        when (val dialog = _state.value.activeDialog) {
            DiscardReport -> leave()
            // A null draft never replaces a chosen severity.
            is Severity -> _state.update { it.copy(severity = dialog.draftState ?: it.severity, activeDialog = null) }
            null -> Unit
        }
    }

    /** Emits [Back] once. */
    private fun leave() {
        if (hasLeft) return
        hasLeft = true
        viewModelScope.launch { eventChannel.send(Back) }
    }

    private fun BugReportFailureReason.toMessage(): ReportBugMessage = when (this) {
        NoConnection -> ReportNoConnection
        ServiceError, InvalidDescription -> ReportFailed
    }

    internal companion object {
        /** How long the confirmation shows before the screen closes. */
        val SENT_CONFIRMATION_DURATION: Duration = 2.seconds
    }
}
