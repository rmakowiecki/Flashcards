package com.rossomak.flashcards.feature.study.rated

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.composables.rating.FlashcardsRatingButtonRow
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
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.CurationSubmissionFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerCaptureUnavailable
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerConsentSaveFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerMicPermissionRevoked
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilencePause
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilenceSkip
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoicePlaybackUnavailable
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase
import kotlinx.coroutines.launch

@Composable
fun RatedStudySessionScreen(
    modifier: Modifier = Modifier,
    viewModel: RatedStudySessionViewModel = hiltViewModel(),
    onNavigateToSummary: (StudySessionSummaryRoute) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is RatedStudySessionDestination.Summary -> onNavigateToSummary(destination.route)
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

    val snackbarHostState = remember { SnackbarHostState() }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted -> viewModel.onMicPermissionResult(isGranted) }

    LaunchedEffect(state.isMicPermissionRequestPending) {
        if (!state.isMicPermissionRequestPending) return@LaunchedEffect
        val isAlreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (isAlreadyGranted) {
            viewModel.onMicPermissionResult(true)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resolveRatedStudySessionMessage(context = context, message = message),
                actionLabel = if (message == VoicePlaybackUnavailable) {
                    context.getString(R.string.study_session_open_tts_settings_button)
                } else {
                    null
                },
                duration = if (message == VoicePlaybackUnavailable) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (message == VoicePlaybackUnavailable && result == SnackbarResult.ActionPerformed) {
                val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (ttsSettingsIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(ttsSettingsIntent)
                }
            }
        }
    }

    RatedStudySessionContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onShowAnswer = viewModel::onShowAnswer,
        onAttemptRating = viewModel::onAttemptRating,
        onVoicePlayPause = viewModel::onVoicePlayPause,
        onVoiceNext = viewModel::onVoiceNext,
        onVoicePrevious = viewModel::onVoicePrevious,
        onDialogEvent = viewModel::onDialogEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatedStudySessionContent(
    modifier: Modifier = Modifier,
    state: RatedStudySessionScreenState,
    snackbarHostState: SnackbarHostState,
    onShowAnswer: () -> Unit,
    onAttemptRating: (FlashcardAttemptRating) -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
    onDialogEvent: (StudySessionDialogEvent) -> Unit,
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
            state.isVoiceMode -> 176.dp
            state.isAnswerRevealed -> 200.dp
            else -> 152.dp
        },
        sheetDragHandle = {},
        topBar = {
            StudySessionTopAppBar(
                sessionTitle = state.sessionTitle,
                reportableCard = state.currentCard,
                counterText = if (state.distinctCardCount > 0) {
                    stringResource(
                        R.string.rated_study_session_mastered_counter_label,
                        state.masteredCount,
                        state.distinctCardCount,
                    )
                } else {
                    null
                },
                onClose = { onDialogEvent(Open(ExitSession)) },
                onReportProblem = { card ->
                    onDialogEvent(Open(ReportCurrentCardProblem(cardId = card.id, subcategoryId = card.subcategoryId)))
                },
            )
        },
        sheetContent = {
            RatedStudySessionSheetContent(
                state = state,
                onShowAnswer = onShowAnswer,
                onAttemptRating = onAttemptRating,
                onVoicePlayPause = onVoicePlayPause,
                onVoiceNext = onVoiceNext,
                onVoicePrevious = onVoicePrevious,
                onVoiceSettingsCogClick = { onDialogEvent(Open(SessionVoiceSettings())) },
            )
        },
    ) { innerPadding ->
        StudySessionBody(
            isLoading = state.isLoading,
            error = state.error,
            flashcards = state.flashcards,
            currentCardIndex = state.currentCardIndex,
            isAnswerRevealed = state.isAnswerRevealed,
            innerPadding = innerPadding,
            onExtendedContextClick = { onDialogEvent(Open(CurrentCardExtendedContext(it))) },
        )

        StudySessionDialogHost(
            activeDialog = state.activeDialog,
            onDialogEvent = onDialogEvent,
        )
    }
}

@Composable
private fun RatedStudySessionSheetContent(
    state: RatedStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onAttemptRating: (FlashcardAttemptRating) -> Unit,
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
        if (state.isVoiceMode) {
            RatedVoiceAnswerHeader(state = state, onVoiceSettingsCogClick = onVoiceSettingsCogClick)
            RatedVoiceTranscript(state = state)
            RatedVoiceGradeFeedback(state = state)
            RatedVoiceTransportRow(
                state = state,
                onShowAnswer = onShowAnswer,
                onVoicePlayPause = onVoicePlayPause,
                onVoiceNext = onVoiceNext,
                onVoicePrevious = onVoicePrevious,
            )
        } else {
            if (!state.isAnswerRevealed) {
                Button(
                    onClick = onShowAnswer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.study_session_show_answer_button))
                }
            } else {
                AttemptRatingButtons(slots = state.attemptSlots, onAttemptRating = onAttemptRating)
            }
        }
    }
}

@Composable
private fun RatedVoiceAnswerHeader(
    state: RatedStudySessionScreenState,
    onVoiceSettingsCogClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isVoiceAnswerEnabled) {
            Text(
                text = stringResource(
                    when (state.voiceAnswerPhase) {
                        VoiceAnswerPhase.WaitingForQuestion -> R.string.study_session_voice_answer_waiting_label
                        VoiceAnswerPhase.Grading -> R.string.study_session_voice_answer_grading_label
                        VoiceAnswerPhase.SpeakingNotice -> R.string.study_session_voice_answer_feedback_label
                        else -> R.string.study_session_voice_answer_listening_label
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.voiceAnswerPhase == VoiceAnswerPhase.SpeechDetected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onVoiceSettingsCogClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.study_session_voice_settings_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Shown as soon as the sanitized transcript streams in (ADR-0028) — screen-on is a first-class
// case, not just a background/audio-only fallback, so the transcript should be readable the
// moment it arrives rather than waiting for the grade.
@Composable
private fun RatedVoiceTranscript(state: RatedStudySessionScreenState) {
    if (state.isVoiceAnswerEnabled &&
        !state.voiceAnswerSanitizedTranscript.isNullOrBlank() &&
        (state.voiceAnswerPhase == VoiceAnswerPhase.Grading || state.voiceAnswerPhase == VoiceAnswerPhase.SpeakingNotice)
    ) {
        Text(
            text = state.voiceAnswerSanitizedTranscript,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// Long-form feedback (grade percent + rationale). Plain bottom-sheet text for as long as SpeakingNotice is
// reading it aloud; lastVoiceAnswerGrade is only non-null for a round that actually graded, never
// for a silence-timeout skip or a grading/transcription failure (see observeVoiceState's reveal
// gating in the ViewModel for the same distinction).
@Composable
private fun RatedVoiceGradeFeedback(state: RatedStudySessionScreenState) {
    val grade = state.lastVoiceAnswerGrade
    if (state.voiceAnswerPhase == VoiceAnswerPhase.SpeakingNotice && grade != null) {
        Text(
            text = stringResource(R.string.study_session_voice_answer_grade_message, grade.gradePercent, grade.feedback),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RatedVoiceTransportRow(
    state: RatedStudySessionScreenState,
    onShowAnswer: () -> Unit,
    onVoicePlayPause: () -> Unit,
    onVoiceNext: () -> Unit,
    onVoicePrevious: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        // While voice-answering is actively listening/grading/speaking feedback, manual skip
        // controls must stay disabled: skipping to the answer here would start TtsPlayer reading
        // the answer aloud while VoiceAnswerController's mic is still hot (grading the TTS's own
        // voice), and skipping during SPEAKING_NOTICE would start the next question on the main
        // TTS engine while VoiceAnswerController's separate notice engine is still talking — two
        // overlapping voices.
        val busyStateSet = setOf(VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected, VoiceAnswerPhase.Grading, VoiceAnswerPhase.SpeakingNotice)
        val isVoiceAnswerBusy = state.isVoiceAnswerEnabled && state.voiceAnswerPhase in busyStateSet
        // Pause only needs to stay disabled for the narrower "answer listening" window — it
        // toggles the main TtsPlayer, which is a no-op while the mic is what's actually capturing
        // (LISTENING/SPEECH_DETECTED); re-enables the moment the answer (or its absence) has been
        // noted and GRADING/SPEAKING_NOTICE takes over.
        val isVoiceAnswerListening = state.isVoiceAnswerEnabled && state.voiceAnswerPhase in setOf(VoiceAnswerPhase.Listening, VoiceAnswerPhase.SpeechDetected)
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onVoicePrevious,
                enabled = state.isVoiceActive && state.currentCardIndex > 0 && !isVoiceAnswerBusy && !state.isVoiceAnswerPaused,
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
                enabled = state.isVoiceActive && !isVoiceAnswerListening,
            ) {
                Icon(
                    imageVector = if (state.isVoicePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (state.isVoicePlaying) R.string.study_session_voice_pause_cd else R.string.study_session_voice_play_cd)
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            IconButton(
                onClick = if (state.isVoiceAnswerEnabled || state.isAnswerRevealed) onVoiceNext else onShowAnswer,
                enabled = state.isVoiceActive && !isVoiceAnswerBusy && !state.isVoiceAnswerPaused,
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(
                        if (state.isVoiceAnswerEnabled || state.isAnswerRevealed) R.string.study_session_next_flashcard_cd else R.string.study_session_show_answer_cd
                    )
                )
            }
        }
    }
}

@Composable
private fun AttemptRatingButtons(
    slots: List<FlashcardsAttemptSlotState>,
    onAttemptRating: (FlashcardAttemptRating) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FlashcardsAttemptIndicator(slots = slots)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(CoreUiR.string.common_rating_prompt_label),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        FlashcardsRatingButtonRow(onRatingSelect = onAttemptRating)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RatedStudySessionVoiceActivePreview() {
    RatedStudySessionContent(
        state = RatedStudySessionScreenState(
            sessionTitle = "Compose",
            flashcards = emptyList(),
            isVoiceMode = true,
            isVoiceActive = true,
            isVoicePlaying = true,
            speechRate = 1.25f,
        ),
        snackbarHostState = remember { SnackbarHostState() },
        onShowAnswer = {},
        onAttemptRating = {},
        onVoicePlayPause = {},
        onVoiceNext = {},
        onVoicePrevious = {},
        onDialogEvent = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RatedStudySessionManualPreview() {
    RatedStudySessionContent(
        state = RatedStudySessionScreenState(
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
        onShowAnswer = {},
        onAttemptRating = {},
        onVoicePlayPause = {},
        onVoiceNext = {},
        onVoicePrevious = {},
        onDialogEvent = {},
    )
}

private fun resolveRatedStudySessionMessage(context: Context, message: RatedStudySessionMessage): String = when (message) {
    VoicePlaybackUnavailable -> context.getString(R.string.study_session_voice_playback_unavailable_message)
    VoiceAnswerConsentSaveFailed -> context.getString(R.string.study_session_voice_answer_consent_save_error_message)
    CurationSubmissionFailed -> context.getString(R.string.fast_study_session_report_failure_message)
    VoiceAnswerGradingFailed -> context.getString(R.string.study_session_voice_answer_error_message)
    VoiceAnswerSilenceSkip -> context.getString(R.string.study_session_voice_answer_skip_message)
    VoiceAnswerSilencePause -> context.getString(R.string.study_session_voice_answer_skip_pause_message)
    VoiceAnswerMicPermissionRevoked -> context.getString(R.string.study_session_voice_answer_mic_permission_revoked_message)
    VoiceAnswerCaptureUnavailable -> context.getString(R.string.study_session_voice_answer_capture_unavailable_message)
}
