package com.rossomak.flashcards.feature.study.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsGradientTopBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCard
import com.rossomak.flashcards.core.ui.composables.rememberFlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.format.currentLocale
import com.rossomak.flashcards.core.ui.format.formatSignedXp
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryDestination.StudyAgain
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryDialog.XpBreakdown
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.GreatWork
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.NiceEffort
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryHeadline.PerfectRun
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryMessage.XpUnavailable
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Loading
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable
import java.util.Locale
import kotlinx.coroutines.launch

private const val SECONDS_PER_MINUTE = 60

/**
 * A Rated or Fast Study Session's mandatory egress, natural end or premature exit alike. Both modes
 * end on the same screen: a headline, the session's stat pills, the XP total with its breakdown, the
 * account's Level card and a sheet with a tip, the way home and a way to study the same thing again. [onNavigateBack] is how the screen
 * ends, from the close icon and the sheet's button alike, and system back does the same thing, since
 * `NavGraph.kt` already replaced everything between here and the tab the user started from.
 */
@Composable
fun StudySessionSummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: StudySessionSummaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPreviewStudySession: (
        categoryId: String,
        categoryName: String,
        sourceType: SessionSourceType,
        subcategoryIds: List<String>,
        subcategoryNames: List<String>,
        studyMode: StudyMode,
        voiceAnsweringEnabled: Boolean?,
        readAloudEnabled: Boolean?,
    ) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val xpUnavailableMessage = stringResource(R.string.study_session_summary_xp_unavailable_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            XpUnavailable -> xpUnavailableMessage
        }
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is StudyAgain -> with(destination.replay) {
                onNavigateToPreviewStudySession(
                    categoryId,
                    categoryName,
                    sourceType,
                    subcategoryIds,
                    subcategoryNames,
                    studyMode,
                    voiceAnsweringEnabled,
                    readAloudEnabled,
                )
            }
        }
    }

    StudySessionSummaryContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onStudyAgainClick = viewModel::onStudyAgainClick,
        onDialogEvent = viewModel::onDialogEvent,
    )
}

/**
 * The Summary in its final, settled state, which is the frame every other state lands on.
 *
 * While the score loads, only a spinner shows on the gradient: no app bar, and nothing to tap. Once
 * it resolves, the app bar, a scrolling stack (headline, pills, and for a [Scored] session the XP
 * total and Level card) and the bottom sheet appear. An [Unavailable] score shows the same screen
 * without the total, its breakdown button or the Level card.
 */
@Composable
fun StudySessionSummaryContent(
    modifier: Modifier = Modifier,
    state: StudySessionSummaryScreenState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit,
    onStudyAgainClick: () -> Unit,
    onDialogEvent: (StudySessionSummaryDialogEvent) -> Unit,
) {
    StudySessionSummaryDialogHost(
        activeDialog = state.activeDialog,
        xpLines = state.xpLines,
        xpTotal = state.xpTotal,
        onDialogEvent = onDialogEvent,
    )

    // The sheet is a sibling of the Scaffold rather than part of its content, so the scrolling
    // stack and the snackbar both pad themselves by the sheet's measured height to stay clear of it.
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val sheetHeight = with(LocalDensity.current) { sheetHeightPx.toDp() }
    val isLoading = state.scoreStatus == Loading

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = sheetHeight)) },
            topBar = { if (!isLoading) SummaryTopBar(onNavigateBack = onNavigateBack) },
        ) { innerPadding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.brandColors.onGradientContent)
                }
            } else {
                SummaryBody(
                    state = state,
                    contentPadding = innerPadding,
                    sheetHeight = sheetHeight,
                    onDialogEvent = onDialogEvent,
                )
            }
        }
        if (!isLoading) {
            // A plain, unaligned sibling of Scaffold, never Modifier.align(Alignment.BottomCenter):
            // see FlashcardsBottomSheet's own doc for why that double-offsets the sheet (ADR-0043).
            SummarySheet(
                mode = state.mode,
                onNavigateBack = onNavigateBack,
                onStudyAgainClick = onStudyAgainClick,
                modifier = Modifier.onSizeChanged { size -> sheetHeightPx = size.height },
            )
        }
    }
}

@Composable
private fun SummaryTopBar(onNavigateBack: () -> Unit) {
    FlashcardsGradientTopBar(
        title = stringResource(R.string.study_session_summary_title_label),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.study_session_summary_close_cd),
                )
            }
        },
    )
}

@Composable
private fun SummaryBody(
    state: StudySessionSummaryScreenState,
    contentPadding: PaddingValues,
    sheetHeight: Dp,
    onDialogEvent: (StudySessionSummaryDialogEvent) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val locale = currentLocale()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.normal)
            // The sheet draws under the navigation bar, so its measured height already covers that
            // inset: it replaces the Scaffold's own bottom padding rather than adding to it.
            .padding(top = MaterialTheme.spacing.small, bottom = sheetHeight + MaterialTheme.spacing.normal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        SummaryHeadline(headline = state.headline)
        SummaryPills(state = state)
        if (state.scoreStatus == Scored) {
            SummaryXpTotal(
                xpTotal = state.xpTotal,
                locale = locale,
                onShowBreakdown = { onDialogEvent(Open(XpBreakdown)) },
            )
            FlashcardsLevelCard(
                level = state.level,
                xpIntoCurrentLevel = state.xpIntoCurrentLevel,
                xpForNextLevel = state.xpForNextLevel,
                progress = levelProgress(state.xpIntoCurrentLevel, state.xpForNextLevel),
                photoUrl = state.photoUrl,
                displayName = state.displayName,
                modifier = Modifier.fillMaxWidth(),
                style = FlashcardsComponentStyle.OnGradient,
            )
        }
    }
}

@Composable
private fun SummaryHeadline(headline: StudySessionSummaryHeadline, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (headline) {
                GreatWork -> R.string.study_session_summary_headline_label
                PerfectRun -> R.string.study_session_summary_headline_perfect_label
                NiceEffort -> R.string.study_session_summary_headline_effort_label
            },
        ),
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.brandColors.onGradientContent,
        textAlign = TextAlign.Center,
    )
}

/**
 * Duration always, then the cards studied, then (Rated only) how many ended Mastered. TalkBack reads
 * the row as one sentence rather than a pill at a time.
 */
@Composable
private fun SummaryPills(state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    val durationLabel = if (state.durationSeconds < SECONDS_PER_MINUTE) {
        stringResource(R.string.study_session_summary_duration_under_minute_badge_label)
    } else {
        stringResource(R.string.study_session_summary_duration_badge_label, state.durationSeconds / SECONDS_PER_MINUTE)
    }
    val cardsLabel = pluralStringResource(R.plurals.study_session_summary_cards_badge_label, state.studiedCount, state.studiedCount)
    val masteredLabel = stringResource(R.string.study_session_summary_mastered_count_label, state.masteredCount)
    val spokenLabel = listOfNotNull(durationLabel, cardsLabel, masteredLabel.takeIf { state.mode == StudyMode.Rated }).joinToString()

    FlowRow(
        modifier = modifier.clearAndSetSemantics { contentDescription = spokenLabel },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        FlashcardsMetadataBadge(label = durationLabel, icon = Icons.Filled.Schedule, style = FlashcardsComponentStyle.OnGradient)
        FlashcardsMetadataBadge(label = cardsLabel, icon = Icons.Filled.Style, style = FlashcardsComponentStyle.OnGradient)
        if (state.mode == StudyMode.Rated) {
            FlashcardsMetadataBadge(label = masteredLabel, icon = Icons.Filled.WorkspacePremium, style = FlashcardsComponentStyle.OnGradient)
        }
    }
}

/** The session's XP total with the button that opens its breakdown. The total text is a single node, read as one figure. */
@Composable
private fun SummaryXpTotal(
    xpTotal: Int,
    locale: Locale,
    onShowBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(xpTotal, locale)),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.brandColors.onGradientContent,
        )
        IconButton(
            onClick = onShowBreakdown,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.brandColors.onGradientContent),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.study_session_summary_xp_breakdown_cd),
            )
        }
    }
}

@Composable
private fun SummarySheet(
    mode: StudyMode,
    onNavigateBack: () -> Unit,
    onStudyAgainClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlashcardsBottomSheet(
        state = rememberFlashcardsBottomSheetState(dismissible = false),
        onDismissRequest = {},
        modifier = modifier,
    ) {
        FlashcardsInfoBanner(
            text = stringResource(
                when (mode) {
                    StudyMode.Rated -> R.string.study_session_summary_rated_tip_message
                    StudyMode.Fast -> R.string.study_session_summary_fast_tip_message
                },
            ),
            icon = Icons.Default.Lightbulb,
            modifier = Modifier.fillMaxWidth(),
        )
        FlashcardsFilledButton(
            text = stringResource(R.string.study_session_summary_back_button),
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.normal),
        )
        FlashcardsTonalButton(
            text = stringResource(R.string.study_session_summary_study_again_button),
            onClick = onStudyAgainClick,
            icon = Icons.Filled.Replay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.small),
        )
    }
}

private fun levelProgress(xpIntoCurrentLevel: Long, xpForNextLevel: Long): Float =
    if (xpForNextLevel <= 0L) 0f else (xpIntoCurrentLevel.toFloat() / xpForNextLevel.toFloat()).coerceIn(0f, 1f)
