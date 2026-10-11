package com.rossomak.flashcards.feature.study.summary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.animation.rememberAnimationsEnabled
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsGradientTopBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
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
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryMessage.SaveFailed
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryMessage.XpUnavailable
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Loading
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable
import kotlinx.coroutines.launch

private const val SECONDS_PER_MINUTE = 60

/** The spacers around the middle group while the sequence counts: a weight must be positive, so almost nothing. */
private const val COLLAPSED_SPACER_WEIGHT = 0.001f

/** How the Level card, headline and pills come in at the reveal. */
private val RevealEnter = fadeIn() + expandVertically()

/**
 * A Rated or Fast Study Session's mandatory egress, natural end or premature exit alike. Both modes
 * end on the same screen: the account's Level card, a headline, the XP total with its breakdown, the session's stat pills
 * and a sheet with a tip, the way home and a way to study the same thing again. [onNavigateBack] is how the screen
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
    val saveFailedMessage = stringResource(R.string.study_session_summary_save_failed_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            XpUnavailable -> xpUnavailableMessage
            SaveFailed -> saveFailedMessage
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
 * The Summary: its animation sequence, and the settled state every other path lands on.
 *
 * While the score loads, only a spinner shows on the gradient: no app bar, and nothing to tap. When it
 * resolves during this composition and animations are on, the sequence plays once (see [SummarySequence]).
 * A restored or rotated screen, an [Unavailable] score and a system with animations off all show the
 * settled state directly. Settled, the app bar, a scrolling stack (the Level card, headline, XP total
 * and pills) and the bottom sheet are all in place. An [Unavailable] score shows the same screen without
 * the total or its breakdown button, and its Level card follows the live Level stream like Home's card,
 * with the same skeleton while it loads or when it is unavailable.
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
    val animationsEnabled = rememberAnimationsEnabled()
    // Saved, so a rotation or a restore mid-sequence settles instead of replaying it.
    var sequenceStarted by rememberSaveable { mutableStateOf(false) }
    // A score already there when the screen first composes was restored, not awaited: it never plays.
    val scoredOnEntry = remember { state.scoreStatus == Scored }
    val isScored = state.scoreStatus == Scored
    val sequence = remember(isScored) {
        val shouldPlay = isScored && !scoredOnEntry && !sequenceStarted && animationsEnabled
        if (shouldPlay) state.toSequence(settled = false) else state.toSequence(settled = true)
    }

    LaunchedEffect(sequence) {
        if (!sequence.isSettled) {
            sequenceStarted = true
            sequence.play()
        }
    }
    BackHandler(enabled = !sequence.isSettled) { sequence.skip() }

    SummaryLayout(
        modifier = modifier,
        state = state,
        sequence = sequence,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onStudyAgainClick = onStudyAgainClick,
        onDialogEvent = onDialogEvent,
    )
}

private fun StudySessionSummaryScreenState.toSequence(settled: Boolean): SummarySequence {
    val build = if (settled) SummarySequence::settled else SummarySequence::unplayed
    return build(xpLines, xpTotal)
}

/**
 * [StudySessionSummaryContent] with the sequence handed in, so a preview can draw any step of it.
 *
 * The sheet is a sibling of the Scaffold rather than part of its content, so the scrolling stack and
 * the snackbar both pad themselves by the sheet's measured height to stay clear of it.
 */
@Composable
internal fun SummaryLayout(
    modifier: Modifier = Modifier,
    state: StudySessionSummaryScreenState,
    sequence: SummarySequence,
    snackbarHostState: SnackbarHostState,
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

    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val sheetHeight = with(LocalDensity.current) { sheetHeightPx.toDp() }
    // Off screen by its own height until the sequence settles, then it rises. Read in draw only.
    val sheetOffset = animateFloatAsState(if (sequence.isSettled) 0f else 1f, tween(SHEET_RISE.toMillisInt()), label = "sheetOffset")
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
            topBar = {
                if (!isLoading) {
                    SummaryTopBar(sequence = sequence, onNavigateBack = onNavigateBack)
                }
            },
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
                    sequence = sequence,
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
                isRaised = sequence.isSettled,
                onNavigateBack = onNavigateBack,
                onStudyAgainClick = onStudyAgainClick,
                modifier = Modifier
                    .onSizeChanged { size -> sheetHeightPx = size.height }
                    .graphicsLayer {
                        translationY = sheetOffset.value * sheetHeightPx
                        // Hidden for the one frame before it is measured, when the offset is still unknown.
                        alpha = if (sheetHeightPx == 0 && !sequence.isSettled) 0f else 1f
                    },
            )
        }
        SummaryConfetti(origin = sequence.confettiOrigin)
        if (state.scoreStatus == Scored) {
            SummaryAnnouncement(sequence = sequence, levelsCrossed = state.levelsCrossed)
        }
    }
}

/**
 * Once the Summary has settled, announces the total and, if a Level was crossed, the new Level. A
 * polite live region on its own node, so the counting total never announces itself. Zero-sized and
 * out of the way: it exists only to be spoken, and the actions are reached by ordinary traversal.
 */
@Composable
private fun SummaryAnnouncement(sequence: SummarySequence, levelsCrossed: List<Int>) {
    val locale = currentLocale()
    val totalText = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(sequence.xpTotal, locale))
    val levelUpText = levelsCrossed.lastOrNull()?.let { level ->
        stringResource(R.string.study_session_summary_level_up_announcement, level)
    }
    val announcement = listOfNotNull(totalText, levelUpText).joinToString(separator = ". ")
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(sequence.isSettled) {
        if (sequence.isSettled) spoken = announcement
    }
    Box(
        modifier = Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
    )
}

/**
 * The app bar is always laid out, so the screen never jumps when it is revealed, but until the sequence
 * settles only Skip is there: the title is invisible and the close button is not even composed, so
 * neither can be focused or tapped.
 */
@Composable
private fun SummaryTopBar(sequence: SummarySequence, onNavigateBack: () -> Unit) {
    // Read in draw only, so the fade never recomposes the bar.
    val alpha = animateFloatAsState(if (sequence.isSettled) 1f else 0f, tween(SHEET_RISE.toMillisInt()), label = "appBarAlpha")
    FlashcardsGradientTopBar(
        title = stringResource(R.string.study_session_summary_title_label),
        titleAlpha = { alpha.value },
        navigationIcon = {
            if (sequence.isSettled) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.study_session_summary_close_cd),
                    )
                }
            }
        },
        actions = {
            if (!sequence.isSettled) {
                FlashcardsTextButton(
                    text = stringResource(R.string.study_session_summary_skip_button),
                    onClick = sequence::skip,
                    size = FlashcardsComponentSize.Small,
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
        },
    )
}

/**
 * The stack: the Level card at the top, then the headline, total and pills midway between the card and
 * the sheet, scrolling when they do not fit. While the sequence counts, only the total is in, at the top,
 * with the tiles growing under it. The reveal brings the card and headline in, which pushes the total
 * down, and grows the two spacers around the middle group from nothing, which glides it to the middle.
 * Nothing is measured but the sheet.
 */
@Composable
private fun SummaryBody(
    state: StudySessionSummaryScreenState,
    sequence: SummarySequence,
    contentPadding: PaddingValues,
    sheetHeight: Dp,
    onDialogEvent: (StudySessionSummaryDialogEvent) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val isScored = state.scoreStatus == Scored
    val isRevealed = sequence.phase >= SummaryPhase.Revealing
    val spacerWeight by animateFloatAsState(if (isRevealed) 1f else COLLAPSED_SPACER_WEIGHT, label = "spacerWeight")
    val topPadding = spacing.small
    // The sheet draws under the navigation bar, so its measured height already covers that inset: it
    // replaces the Scaffold's own bottom padding rather than adding to it.
    val bottomPadding = sheetHeight + spacing.normal

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = sequence.isSettled)
                .padding(horizontal = spacing.normal)
                .padding(top = topPadding, bottom = bottomPadding)
                // At least the visible height, so the spacers have room to share; taller content scrolls.
                .heightIn(min = (maxHeight - topPadding - bottomPadding).coerceAtLeast(0.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isScored) {
                AnimatedVisibility(visible = isRevealed, enter = RevealEnter) {
                    SummaryLevelCard(sequence = sequence, state = state, modifier = Modifier.padding(bottom = spacing.medium))
                }
            }
            if (state.scoreStatus == Unavailable) {
                FlashcardsLevelCard(
                    state = state.levelCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.medium),
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
            Spacer(Modifier.weight(spacerWeight))
            AnimatedVisibility(visible = isRevealed, enter = RevealEnter) {
                SummaryHeadline(headline = state.headline, modifier = Modifier.padding(bottom = spacing.medium))
            }
            if (isScored) {
                SummaryXpTotal(sequence = sequence, onShowBreakdown = { onDialogEvent(Open(XpBreakdown)) })
                AnimatedVisibility(
                    visible = sequence.phase == SummaryPhase.Counting,
                    // Takes what the column has left, and scrolls itself inside it.
                    modifier = Modifier.weight(1f, fill = false),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SummaryXpTiles(
                        lines = sequence.lines,
                        visibleCount = sequence.visibleLineCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.medium),
                    )
                }
            }
            AnimatedVisibility(visible = isRevealed, enter = RevealEnter) {
                SummaryPills(state = state, modifier = Modifier.padding(top = spacing.medium))
            }
            Spacer(Modifier.weight(spacerWeight))
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

/**
 * The session's XP total, counting while the tiles reveal, with the button that opens its breakdown once
 * the Summary has settled. The button's slot is always laid out, and an empty slot of the same width
 * mirrors it, so the total sits on the screen's centreline whether or not the button is there. The total
 * text is a single node, read as one figure, and deliberately not a live region: it changes every frame
 * while counting. The settled figure is announced by [SummaryAnnouncement] instead.
 */
@Composable
private fun SummaryXpTotal(
    sequence: SummarySequence,
    onShowBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val shownTotal by animateIntAsState(
        targetValue = sequence.runningTotal,
        animationSpec = tween(COUNT_DURATION.toMillisInt(), easing = CountEasing),
        label = "shownTotal",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.minimumInteractiveComponentSize())
        Text(
            text = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(shownTotal, locale)),
            // Only the coordinates object is kept; the confetti reads its position when it bursts.
            modifier = Modifier.onPlaced { coordinates -> sequence.totalTextCoordinates = coordinates },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.brandColors.onGradientContent,
        )
        Box(modifier = Modifier.minimumInteractiveComponentSize()) {
            if (sequence.isSettled) {
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
    }
}

/**
 * The account's Level card on a scored Summary: the position before the session until the sequence
 * pours, then the position after, which the card pours on its own. The card stays out of
 * accessibility until the sequence settles, so it is read out once, with its final figures.
 */
@Composable
private fun SummaryLevelCard(sequence: SummarySequence, state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    // A skip gives the card a new identity, whose first composition shows the after position without a pour.
    key(sequence.wasSkipped) {
        FlashcardsLevelCard(
            state = if (sequence.showsLevelAfter) state.levelCard else state.levelCardBefore,
            modifier = modifier
                .fillMaxWidth()
                .then(if (sequence.isSettled) Modifier else Modifier.clearAndSetSemantics {}),
            style = FlashcardsComponentStyle.OnGradient,
        )
    }
}

@Composable
private fun SummarySheet(
    mode: StudyMode,
    isRaised: Boolean,
    onNavigateBack: () -> Unit,
    onStudyAgainClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlashcardsBottomSheet(
        state = rememberFlashcardsBottomSheetState(dismissible = false),
        onDismissRequest = {},
        // Off screen until it rises: out of accessibility too, so its actions come after the announcement.
        modifier = modifier.then(if (isRaised) Modifier else Modifier.clearAndSetSemantics {}),
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
