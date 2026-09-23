package com.rossomak.flashcards.feature.study.fast

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsButtonIconPosition
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledIconButton
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
import com.rossomak.flashcards.feature.study.chrome.studySessionCardTitle
import com.rossomak.flashcards.feature.study.fast.FastStudySessionMessage.CurationReportFailed
import com.rossomak.flashcards.feature.study.fast.FastStudySessionMessage.VoicePlaybackUnavailable
import kotlinx.coroutines.launch

private val SHEET_PEEK_HEIGHT_READ_ALOUD: Dp = 175.dp
private val SHEET_PEEK_HEIGHT_DEFAULT: Dp = 150.dp

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

    val snackbarHostState = remember { SnackbarHostState() }

    val voicePlaybackUnavailableMessage = stringResource(R.string.study_session_voice_playback_unavailable_message)
    val openTtsSettingsAction = stringResource(R.string.study_session_open_tts_settings_button)
    val curationReportFailedMessage = stringResource(R.string.fast_study_session_report_failure_message)

    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        when (message) {
            VoicePlaybackUnavailable -> snackbarScope.launch {
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
            }

            CurationReportFailed -> snackbarScope.launch {
                snackbarHostState.showSnackbar(message = curationReportFailedMessage, duration = SnackbarDuration.Short)
            }
        }
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
            sheetPeekHeight = if (state.isReadAloudMode) SHEET_PEEK_HEIGHT_READ_ALOUD else SHEET_PEEK_HEIGHT_DEFAULT,
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
                    progressLabel = if (state.flashcards.isNotEmpty()) stringResource(R.string.fast_study_session_progress_label) else null,
                    completedCount = if (state.flashcards.isNotEmpty()) state.currentCardIndex + 1 else null,
                    totalCount = if (state.flashcards.isNotEmpty()) state.flashcards.size else null,
                    progressFraction = if (state.flashcards.isNotEmpty()) (state.currentCardIndex + 1) / state.flashcards.size.toFloat() else null,
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (state.isReadAloudMode) SHEET_PEEK_HEIGHT_READ_ALOUD else SHEET_PEEK_HEIGHT_DEFAULT)
            .padding(horizontal = MaterialTheme.spacing.normal)
            .padding(top = MaterialTheme.spacing.normal, bottom = MaterialTheme.spacing.medium)
    ) {
        if (state.isReadAloudMode) {
            FastVoiceTransportControls(
                state = state,
                onShowAnswer = onShowAnswer,
                onVoicePlayPause = onVoicePlayPause,
                onVoiceNext = onVoiceNext,
                onVoicePrevious = onVoicePrevious,
                onVoiceSettingsCogClick = onVoiceSettingsCogClick,
            )
        } else if (!state.isAnswerRevealed) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.study_session_show_answer_caption_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
            FlashcardsFilledButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.study_session_show_answer_button),
                onClick = onShowAnswer,
                icon = Icons.Default.Flip,
            )
        } else {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.fast_study_session_next_caption_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
            FlashcardsFilledButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.fast_study_session_next_button),
                onClick = onNextCard,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                iconPosition = FlashcardsButtonIconPosition.Trailing,
            )
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
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onVoiceSettingsCogClick,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.study_session_voice_settings_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .padding(top = MaterialTheme.spacing.normal)
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onVoicePrevious,
                enabled = state.isVoiceActive,
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.study_session_previous_card_cd),
                )
            }
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.normal))
            FlashcardsFilledIconButton(
                icon = if (state.isVoicePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (state.isVoicePlaying) R.string.study_session_voice_pause_cd else R.string.study_session_voice_play_cd
                ),
                onClick = onVoicePlayPause,
                enabled = state.isVoiceActive,
            )
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.normal))
            IconButton(
                onClick = if (state.isAnswerRevealed) onVoiceNext else onShowAnswer,
                enabled = state.isVoiceActive,
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(
                        if (state.isAnswerRevealed) R.string.study_session_next_flashcard_cd else R.string.study_session_show_answer_cd
                    ),
                )
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
            categoryName = "Android",
            subcategoryNameById = mapOf("compose" to "Compose"),
            flashcards = emptyList(),
            isReadAloudMode = true,
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
