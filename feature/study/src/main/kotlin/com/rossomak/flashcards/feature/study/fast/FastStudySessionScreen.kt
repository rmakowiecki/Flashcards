package com.rossomak.flashcards.feature.study.fast

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionBody
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogEvent
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogHost
import com.rossomak.flashcards.feature.study.chrome.StudySessionTopAppBar
import kotlinx.coroutines.launch

@Composable
fun FastStudySessionScreen(
    modifier: Modifier = Modifier,
    viewModel: FastStudySessionViewModel = hiltViewModel(),
    onNavigateToSummary: (StudySessionSummaryRoute) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is FastStudySessionDestination.Summary -> onNavigateToSummary(destination.route)
        }
    }

    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // System/predictive back must route through the same exit-confirmation flow as the top-bar X —
    // otherwise it pops straight to Preview without sealing a result or showing the mandatory
    // Summary screen.
    BackHandler {
        viewModel.onDialogEvent(Open(ExitSession))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) viewModel.onVoiceAutoStart() else viewModel.onVoiceAutoStartDeclined()
    }

    // Read-aloud off never sets isVoiceAutoStartPending (FastStudySessionViewModel.loadFlashcards),
    // so this effect — and the permission prompt it can trigger — never fires for a manual session.
    LaunchedEffect(state.isVoiceAutoStartPending) {
        if (!state.isVoiceAutoStartPending) return@LaunchedEffect
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onVoiceAutoStart()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val voicePlaybackUnavailableMessage = stringResource(R.string.study_session_voice_playback_unavailable_message)
    val openTtsSettingsAction = stringResource(R.string.study_session_open_tts_settings_button)

    LaunchedEffect(state.voiceError) {
        if (state.voiceError == null) return@LaunchedEffect
        launch {
            val result = snackbarHostState.showSnackbar(
                message = voicePlaybackUnavailableMessage,
                actionLabel = openTtsSettingsAction,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (ttsSettingsIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(ttsSettingsIntent)
                }
            }
            viewModel.onVoiceErrorDismissed()
        }
    }

    val curationErrorMessage = state.curationError?.let { stringResource(it) }
    LaunchedEffect(state.curationError) {
        val error = curationErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Short)
        viewModel.onCurationErrorDismissed()
    }

    FastStudySessionContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        actions = FastStudySessionActions(
            onShowAnswer = viewModel::onShowAnswer,
            onNextCard = viewModel::onNextCard,
            onVoicePlayPause = viewModel::onVoicePlayPause,
            onVoiceNext = viewModel::onVoiceNext,
            onVoicePrevious = viewModel::onVoicePrevious,
            onDialogEvent = viewModel::onDialogEvent,
        ),
    )
}

/** Bundles [FastStudySessionContent]'s callbacks so the composable stays under detekt's param cap. */
data class FastStudySessionActions(
    val onShowAnswer: () -> Unit,
    val onNextCard: () -> Unit,
    val onVoicePlayPause: () -> Unit,
    val onVoiceNext: () -> Unit,
    val onVoicePrevious: () -> Unit,
    val onDialogEvent: (StudySessionDialogEvent) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastStudySessionContent(
    modifier: Modifier = Modifier,
    state: FastStudySessionScreenState,
    snackbarHostState: SnackbarHostState,
    actions: FastStudySessionActions,
) {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = true),
    )

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetSwipeEnabled = false,
        sheetPeekHeight = when {
            state.isVoiceActive -> 176.dp
            state.isAnswerRevealed -> 160.dp
            else -> 112.dp
        },
        sheetDragHandle = {},
        topBar = {
            StudySessionTopAppBar(
                sessionTitle = state.sessionTitle,
                reportableCard = state.currentCard,
                counterText = if (state.flashcards.isNotEmpty()) {
                    stringResource(
                        R.string.fast_study_session_position_counter_label,
                        state.currentCardIndex + 1,
                        state.flashcards.size,
                    )
                } else {
                    null
                },
                onClose = { actions.onDialogEvent(Open(ExitSession)) },
                onReportProblem = { card ->
                    actions.onDialogEvent(Open(ReportCurrentCardProblem(cardId = card.id, subcategoryId = card.subcategoryId)))
                },
            )
        },
        sheetContent = {
            FastStudySessionSheetContent(
                state = state,
                onShowAnswer = actions.onShowAnswer,
                onNextCard = actions.onNextCard,
                onVoicePlayPause = actions.onVoicePlayPause,
                onVoiceNext = actions.onVoiceNext,
                onVoicePrevious = actions.onVoicePrevious,
                onVoiceSettingsCogClick = { actions.onDialogEvent(Open(SessionVoiceSettings())) },
            )
        },
    ) { innerPadding ->
        StudySessionBody(
            isLoading = state.isLoading,
            error = state.error?.let { stringResource(it) },
            flashcards = state.flashcards,
            currentCardIndex = state.currentCardIndex,
            isAnswerRevealed = state.isAnswerRevealed,
            innerPadding = innerPadding,
            onExtendedContextClick = { actions.onDialogEvent(Open(CurrentCardExtendedContext(it))) },
        )

        StudySessionDialogHost(
            activeDialog = state.activeDialog,
            onDialogEvent = actions.onDialogEvent,
        )
    }
}

@Composable
private fun FastStudySessionSheetContent(
    state: FastStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onNextCard: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
    onVoiceSettingsCogClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        if (state.isVoiceActive) {
            FastVoiceTransportControls(
                state = state,
                onShowAnswer = onShowAnswer,
                onVoicePlayPause = onVoicePlayPause,
                onVoiceNext = onVoiceNext,
                onVoicePrevious = onVoicePrevious,
                onVoiceSettingsCogClick = onVoiceSettingsCogClick,
            )
        } else if (!state.isAnswerRevealed) {
            Button(
                onClick = onShowAnswer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.study_session_show_answer_button))
            }
        } else {
            Button(
                onClick = onNextCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.fast_study_session_next_button))
            }
        }
    }
}

@Composable
private fun FastVoiceTransportControls(
    state: FastStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
    onVoiceSettingsCogClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVoiceSettingsCogClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.study_session_voice_settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onVoicePrevious,
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.study_session_previous_card_cd),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                FilledIconButton(
                    onClick = onVoicePlayPause,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (state.isVoicePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isVoicePlaying) {
                                R.string.study_session_voice_pause_cd
                            } else {
                                R.string.study_session_voice_play_cd
                            }
                        ),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                IconButton(
                    onClick = if (state.isAnswerRevealed) onVoiceNext else onShowAnswer,
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(
                            if (state.isAnswerRevealed) {
                                R.string.study_session_next_flashcard_cd
                            } else {
                                R.string.study_session_show_answer_cd
                            }
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun FastStudySessionVoiceActivePreview() {
    FastStudySessionContent(
        state = FastStudySessionScreenState(
            sessionTitle = "Compose",
            flashcards = emptyList(),
            isVoiceActive = true,
            isVoicePlaying = true,
            speechRate = 1.25f,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        actions = FastStudySessionActions(
            onShowAnswer = {},
            onNextCard = {},
            onVoicePlayPause = {},
            onVoiceNext = {},
            onVoicePrevious = {},
            onDialogEvent = {},
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun FastStudySessionManualRevealedPreview() {
    FastStudySessionContent(
        state = FastStudySessionScreenState(
            sessionTitle = "Compose",
            flashcards = listOf(
                com.rossomak.flashcards.core.domain.model.Flashcard(
                    id = "1",
                    subcategoryId = "compose",
                    tags = emptyList(),
                    question = "What is the difference between remember and rememberSaveable?",
                    answer = "remember persists state across recompositions only; rememberSaveable also survives " +
                        "configuration changes and process death by storing in a Bundle.",
                    difficulty = 2,
                    questionCode = null,
                    answerCode = null,
                    questionSpoken = null,
                    answerSpoken = null,
                    extendedContext = null,
                ),
            ),
            isAnswerRevealed = true,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        actions = FastStudySessionActions(
            onShowAnswer = {},
            onNextCard = {},
            onVoicePlayPause = {},
            onVoiceNext = {},
            onVoicePrevious = {},
            onDialogEvent = {},
        ),
    )
}
