package com.rossomak.flashcards.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.BugReport
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Blocker
import com.rossomak.flashcards.core.domain.model.BugReportSeverity.Minor
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.account.ReportBugDestination.Back
import com.rossomak.flashcards.feature.account.ReportBugDialog.Severity
import com.rossomak.flashcards.feature.account.ReportBugMessage.ReportFailed
import com.rossomak.flashcards.feature.account.ReportBugMessage.ReportNoConnection
import com.rossomak.flashcards.feature.account.ReportBugMessage.ReportSent
import com.rossomak.flashcards.feature.account.ReportBugSubmissionStatus.Sending
import kotlinx.coroutines.launch

@Composable
fun ReportBugScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportBugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            Back -> onNavigateBack()
        }
    }

    val reportSentMessage = stringResource(R.string.report_bug_sent_message)
    val reportFailedMessage = stringResource(R.string.report_bug_failed_message)
    val reportNoConnectionMessage = stringResource(R.string.report_bug_no_connection_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            ReportSent -> reportSentMessage
            ReportFailed -> reportFailedMessage
            ReportNoConnection -> reportNoConnectionMessage
        }
        snackbarScope.launch {
            snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        }
    }

    // Same as the close icon.
    BackHandler(onBack = viewModel::onCloseClick)

    ReportBugContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onCloseClick = viewModel::onCloseClick,
        onDescriptionChange = viewModel::onDescriptionChange,
        onSendClick = viewModel::onSendClick,
        onDialogEvent = viewModel::onDialogEvent,
    )
}

@Suppress("LongParameterList")
@Composable
private fun ReportBugContent(
    modifier: Modifier = Modifier,
    state: ReportBugScreenState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onCloseClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onDialogEvent: (ReportBugDialogEvent) -> Unit,
) {
    ReportBugDialogHost(
        activeDialog = state.activeDialog,
        onDialogEvent = onDialogEvent,
    )

    // imePadding on the Scaffold lifts the snackbar above the keyboard too.
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            ReportBugTopBar(
                state = state,
                onCloseClick = onCloseClick,
                onSendClick = onSendClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ReportBugForm(
                state = state,
                onSeverityClick = { onDialogEvent(Open(Severity(draftState = state.severity))) },
                onDescriptionChange = onDescriptionChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportBugTopBar(
    state: ReportBugScreenState,
    onCloseClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.report_bug_title),
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                IconButton(onClick = onCloseClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(CoreUiR.string.common_close_cd),
                    )
                }
            },
            actions = {
                SendAction(
                    isSending = state.submissionStatus == Sending,
                    enabled = state.canSend,
                    onClick = onSendClick,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** While [isSending] a spinner replaces the label at the same size. */
@Composable
private fun SendAction(
    isSending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (isSending) {
        val sendingDescription = stringResource(R.string.report_bug_sending_cd)
        Box(
            modifier = Modifier
                .height(MaterialTheme.sizes.buttonHeightSmall)
                .padding(horizontal = MaterialTheme.spacing.normal),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(ButtonDefaults.IconSize)
                    .semantics {
                        contentDescription = sendingDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                strokeWidth = MaterialTheme.sizes.actionProgressStroke,
            )
        }
    } else {
        FlashcardsTextButton(
            text = stringResource(R.string.report_bug_send_button),
            onClick = onClick,
            size = FlashcardsComponentSize.Small,
            enabled = enabled,
        )
    }
}

@PreviewLightDark
@Composable
private fun ReportBugContentEmptyPreview() {
    FlashcardsTheme {
        ReportBugContent(
            state = ReportBugScreenState(),
            onCloseClick = {},
            onDescriptionChange = {},
            onSendClick = {},
            onDialogEvent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ReportBugContentSendingPreview() {
    FlashcardsTheme {
        ReportBugContent(
            state = ReportBugScreenState(
                draftText = "The timer freezes after I rotate the phone during a rated session.",
                severity = Minor,
                submissionStatus = Sending,
            ),
            onCloseClick = {},
            onDescriptionChange = {},
            onSendClick = {},
            onDialogEvent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ReportBugContentOverLimitPreview() {
    FlashcardsTheme {
        ReportBugContent(
            state = ReportBugScreenState(
                draftText = "x".repeat(BugReport.MAX_DESCRIPTION_LENGTH + 1),
                severity = Blocker,
            ),
            onCloseClick = {},
            onDescriptionChange = {},
            onSendClick = {},
            onDialogEvent = {},
        )
    }
}
