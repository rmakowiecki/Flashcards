package com.rossomak.flashcards.feature.study.rated

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.rating.FlashcardsRatingButtonRow
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.StudySessionSummaryRoute
import com.rossomak.flashcards.feature.study.chrome.StudySessionBody
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogEvent
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogHost
import com.rossomak.flashcards.feature.study.chrome.StudySessionHeader
import com.rossomak.flashcards.feature.study.chrome.StudySessionProgress
import com.rossomak.flashcards.feature.study.chrome.studySessionCardTitle
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.CurationSubmissionFailed
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerCaptureUnavailable
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingOffline
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerGradingServiceError
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerMicPermissionRevoked
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilencePause
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoiceAnswerSilenceSkip
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionMessage.VoicePlaybackUnavailable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal val SHEET_PEEK_HEIGHT_VOICE: Dp = 176.dp
private val SHEET_PEEK_HEIGHT_MANUAL: Dp = 150.dp

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

    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resolveRatedStudySessionMessage(context = context, message = message),
                actionLabel = if (message == VoicePlaybackUnavailable) context.getString(R.string.study_session_open_tts_settings_button) else null,
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
        voiceBarsLevels = viewModel.voiceBarsLevels,
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
    voiceBarsLevels: StateFlow<ImmutableList<Float>>,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
    ) {
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            sheetSwipeEnabled = false,
            sheetPeekHeight = if (state.isVoiceMode) SHEET_PEEK_HEIGHT_VOICE else SHEET_PEEK_HEIGHT_MANUAL,
            sheetDragHandle = {},
            topBar = {
                StudySessionHeader(
                    title = studySessionCardTitle(
                        categoryName = state.categoryName,
                        currentCard = state.currentCard,
                        subcategoryNameById = state.subcategoryNameById,
                        separator = stringResource(CoreUiR.string.common_middle_dot_separator),
                    ),
                    reportableCard = state.currentCard,
                    progress = if (state.distinctCardCount > 0) {
                        StudySessionProgress(
                            label = stringResource(R.string.rated_study_session_progress_label),
                            completedCount = state.completedCount,
                            totalCount = state.distinctCardCount,
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
                if (state.isVoiceMode) {
                    RatedVoiceSheetContent(
                        state = state,
                        voiceBarsLevels = voiceBarsLevels,
                        onShowAnswer = onShowAnswer,
                        onVoicePlayPause = onVoicePlayPause,
                        onVoiceNext = onVoiceNext,
                        onVoicePrevious = onVoicePrevious,
                        onVoiceSettingsCogClick = { onDialogEvent(Open(SessionVoiceSettings())) },
                    )
                } else {
                    RatedManualSheetContent(
                        isAnswerRevealed = state.isAnswerRevealed,
                        onShowAnswer = onShowAnswer,
                        onAttemptRating = onAttemptRating,
                    )
                }
            },
        ) { innerPadding ->
            StudySessionBody(
                isLoading = state.isLoading,
                error = state.error,
                flashcards = state.flashcards,
                currentCardIndex = state.currentCardIndex,
                isAnswerRevealed = state.isAnswerRevealed,
                innerPadding = innerPadding,
                attemptSlots = state.attemptSlots,
                onExtendedContextClick = { onDialogEvent(Open(CurrentCardExtendedContext(it))) },
            )

            StudySessionDialogHost(
                activeDialog = state.activeDialog,
                onDialogEvent = onDialogEvent,
            )
        }
    }
}

@Composable
private fun RatedManualSheetContent(isAnswerRevealed: Boolean, onShowAnswer: () -> Unit, onAttemptRating: (FlashcardAttemptRating) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(SHEET_PEEK_HEIGHT_MANUAL)
            .padding(horizontal = MaterialTheme.spacing.normal)
            .padding(top = MaterialTheme.spacing.normal, bottom = MaterialTheme.spacing.medium),
    ) {
        if (!isAnswerRevealed) {
            Text(
                text = stringResource(R.string.study_session_show_answer_caption_message),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
            FlashcardsFilledButton(
                text = stringResource(R.string.study_session_show_answer_button),
                onClick = onShowAnswer,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Flip,
            )
        } else {
            AttemptRatingButtons(onAttemptRating = onAttemptRating)
        }
    }
}

private fun resolveRatedStudySessionMessage(context: Context, message: RatedStudySessionMessage): String = when (message) {
    VoicePlaybackUnavailable -> context.getString(R.string.study_session_voice_playback_unavailable_message)
    CurationSubmissionFailed -> context.getString(R.string.fast_study_session_report_failure_message)
    VoiceAnswerGradingOffline -> context.getString(R.string.study_session_voice_answer_offline_message)
    VoiceAnswerGradingServiceError -> context.getString(R.string.study_session_voice_answer_service_error_message)
    VoiceAnswerSilenceSkip -> context.getString(R.string.study_session_voice_answer_skip_message)
    VoiceAnswerSilencePause -> context.getString(R.string.study_session_voice_answer_skip_pause_message)
    VoiceAnswerMicPermissionRevoked -> context.getString(R.string.study_session_voice_answer_mic_permission_revoked_message)
    VoiceAnswerCaptureUnavailable -> context.getString(R.string.study_session_voice_answer_capture_unavailable_message)
}

@Composable
private fun AttemptRatingButtons(onAttemptRating: (FlashcardAttemptRating) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(CoreUiR.string.common_rating_prompt_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        FlashcardsRatingButtonRow(onRatingSelect = onAttemptRating)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun RatedStudySessionManualPreview() {
    RatedStudySessionContent(
        state = RatedStudySessionScreenState(
            categoryName = "Android",
            subcategoryNameById = mapOf("compose" to "Compose"),
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
        voiceBarsLevels = remember { MutableStateFlow(FlashcardsVoiceCaptureIndicatorDefaults.restLevels) },
        snackbarHostState = remember { SnackbarHostState() },
        onShowAnswer = {},
        onAttemptRating = {},
        onVoicePlayPause = {},
        onVoiceNext = {},
        onVoicePrevious = {},
        onDialogEvent = {},
    )
}
