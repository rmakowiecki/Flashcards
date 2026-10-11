package com.rossomak.flashcards.feature.study.summary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
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

/** A weight must be positive, so this stands in for a collapsed spacer. */
private const val COLLAPSED_SPACER_WEIGHT = 0.001f

private val COUNTING_TOTAL_DROP = 32.dp

private val COUNTING_TILES_GAP = 64.dp

/** Roughly the Level card's bottom edge below the total; fixed, since the card is not laid out at the burst. */
private val CONFETTI_DROP = 48.dp

/** Unclipped, so an element moves in whole rather than being uncovered edge first. */
private val RevealEnter = fadeIn() + expandVertically(expandFrom = Alignment.Top, clip = false) + slideInVertically { height -> -height / 4 }

/** Explicit no-op: the default shrink exit makes [AnimatedVisibility] clip during the enter too. */
private val RevealExit = ExitTransition.None

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
 * Plays the sequence once when the score resolves during this composition with animations on. A restore,
 * a rotation, an [Unavailable] score or disabled animations show the settled state directly.
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
    // Saved, so a rotation mid-sequence settles instead of replaying.
    var sequenceStarted by rememberSaveable { mutableStateOf(false) }
    // A score present on first composition was restored: it never plays.
    val scoredOnEntry = remember { state.scoreStatus == Scored }
    val isScored = state.scoreStatus == Scored
    val sequence = remember(isScored) {
        val shouldPlay = isScored && animationsEnabled && !scoredOnEntry && !sequenceStarted
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

/** Takes the sequence so previews can draw any step. The stack and snackbar pad by the sheet's measured height. */
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
            // Created only once scored, so it starts from the unplayed sequence, not the loading screen's settled one.
            val sheetOffset = animateFloatAsState(if (sequence.isSettled) 0f else 1f, tween(SHEET_RISE.toMillisInt()), label = "sheetOffset")
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
        val confettiDropPx = with(LocalDensity.current) { CONFETTI_DROP.toPx() }
        SummaryConfetti(origin = sequence.confettiOrigin?.let { origin -> origin + Offset(0f, confettiDropPx) })
        if (state.scoreStatus == Scored) {
            SummaryAnnouncement(sequence = sequence, levelsCrossed = state.levelsCrossed)
        }
    }
}

/** Announces the total and any new Level once settled, from its own node so the counting total stays silent. */
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

/** Always laid out, so its reveal never shifts the screen; only Skip is interactive until settled. */
@Composable
private fun SummaryTopBar(sequence: SummarySequence, onNavigateBack: () -> Unit) {
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
 * Two weighted spacers centre the headline, total and pills between the Level card and the sheet. While
 * counting, the top spacer is collapsed and the tiles sit on their own layer, so their exit never moves the total.
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
    val topSpacerWeight by animateFloatAsState(if (isRevealed) 1f else COLLAPSED_SPACER_WEIGHT, label = "topSpacerWeight")
    val totalDrop by animateDpAsState(if (isRevealed) 0.dp else COUNTING_TOTAL_DROP, label = "totalDrop")
    val topPadding = spacing.small
    // The sheet's measured height already covers the navigation bar, so it replaces the Scaffold's bottom padding.
    val bottomPadding = sheetHeight + spacing.normal
    val scrollState = rememberScrollState()
    // A scroll clips to its viewport and would cut the Level card's slide-in, so it starts once settled.
    val stackScroll = if (sequence.isSettled) {
        Modifier.verticalScroll(scrollState)
    } else {
        Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
    }

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
                .then(stackScroll)
                .padding(horizontal = spacing.normal)
                .padding(top = topPadding, bottom = bottomPadding)
                // At least the visible height, so the spacers have room to share; taller content scrolls.
                .heightIn(min = (maxHeight - topPadding - bottomPadding).coerceAtLeast(0.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isScored) {
                AnimatedVisibility(visible = isRevealed, enter = RevealEnter, exit = RevealExit) {
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
            Spacer(Modifier.height(totalDrop))
            Spacer(Modifier.weight(topSpacerWeight))
            AnimatedVisibility(visible = isRevealed, enter = RevealEnter, exit = RevealExit) {
                SummaryHeadline(headline = state.headline, modifier = Modifier.padding(bottom = spacing.medium))
            }
            if (isScored) {
                SummaryXpTotal(sequence = sequence, onShowBreakdown = { onDialogEvent(Open(XpBreakdown)) })
            }
            AnimatedVisibility(visible = isRevealed, enter = RevealEnter, exit = RevealExit) {
                SummaryPills(state = state, modifier = Modifier.padding(top = spacing.medium))
            }
            Spacer(Modifier.weight(1f))
        }
        if (isScored) {
            AnimatedVisibility(visible = sequence.phase == SummaryPhase.Counting, enter = EnterTransition.None, exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.normal)
                        .padding(top = topPadding + COUNTING_TOTAL_DROP, bottom = contentPadding.calculateBottomPadding() + spacing.normal),
                ) {
                    // An invisible copy of the total's row, so the tiles start under the total without measuring it.
                    SummaryXpTotalSlot(text = "", modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                    SummaryXpTiles(
                        lines = sequence.lines,
                        visibleCount = sequence.visibleLineCount,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(top = COUNTING_TILES_GAP),
                    )
                }
            }
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
 * A mirrored empty slot keeps the total centred whether or not the info button shows. Not a live region,
 * since it changes every frame while counting; [SummaryAnnouncement] speaks the final figure.
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
        SummaryXpTotalSlot(
            text = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(shownTotal, locale)),
            modifier = Modifier.onPlaced { coordinates -> sequence.totalTextCoordinates = coordinates },
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

/** As tall as the row's touch targets, so the tiles layer's invisible copy lines up with the real total. */
@Composable
private fun SummaryXpTotalSlot(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.brandColors.onGradientContent,
    )
}

/** Hidden from accessibility until settled, so it is read once, with final figures. */
@Composable
private fun SummaryLevelCard(sequence: SummarySequence, state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    // A skip gives the card a new identity, so it shows the after position without a pour.
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
