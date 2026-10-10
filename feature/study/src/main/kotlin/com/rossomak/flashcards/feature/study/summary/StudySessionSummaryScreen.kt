package com.rossomak.flashcards.feature.study.summary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryMessage.XpUnavailable
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Loading
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Scored
import com.rossomak.flashcards.feature.study.summary.StudySessionSummaryScoreStatus.Unavailable
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

private const val SECONDS_PER_MINUTE = 60

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
 * The Summary: its payoff sequence, and the settled state every other path lands on.
 *
 * While the score loads, only a spinner shows on the gradient: no app bar, and nothing to tap. When it
 * resolves during this composition and animations are on, the payoff plays once (see [SummaryPayoff]).
 * A restored or rotated screen, an [Unavailable] score and a system with animations off all show the
 * settled state directly. Settled, the app bar, a scrolling stack (for a [Scored]
 * session the Level card, headline, XP total and pills; otherwise headline and pills) and the bottom sheet are all in place. An [Unavailable] score
 * shows the same screen without the total, its breakdown button or the Level card.
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
    val payoff = remember(isScored) {
        val shouldPlay = isScored && !scoredOnEntry && !sequenceStarted && animationsEnabled
        if (shouldPlay) state.toPayoff(settled = false) else state.toPayoff(settled = true)
    }

    LaunchedEffect(payoff) {
        if (!payoff.isSettled) {
            sequenceStarted = true
            payoff.play()
        }
    }
    BackHandler(enabled = payoff.isSkippable) { payoff.skip() }

    SummaryLayout(
        modifier = modifier,
        state = state,
        payoff = payoff,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onStudyAgainClick = onStudyAgainClick,
        onDialogEvent = onDialogEvent,
    )
}

private fun StudySessionSummaryScreenState.toPayoff(settled: Boolean): SummaryPayoff {
    val build = if (settled) SummaryPayoff::settled else SummaryPayoff::unplayed
    return build(xpLines, xpTotal, positionBefore(), positionAfter(), levelsCrossed)
}

/**
 * [StudySessionSummaryContent] with the payoff handed in, so a preview can draw any frame of it.
 *
 * The sheet is a sibling of the Scaffold rather than part of its content, so the scrolling stack and
 * the snackbar both pad themselves by the sheet's measured height to stay clear of it.
 */
@Composable
internal fun SummaryLayout(
    modifier: Modifier = Modifier,
    state: StudySessionSummaryScreenState,
    payoff: SummaryPayoff,
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
            .background(MaterialTheme.brandColors.screenGradient)
            // A tap anywhere that nothing else claims jumps to the end. Detected rather than made
            // clickable, so the root carries no click action for TalkBack to announce.
            .pointerInput(payoff.isSkippable) {
                if (payoff.isSkippable) detectTapGestures { payoff.skip() }
            },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = sheetHeight)) },
            topBar = {
                if (!isLoading) {
                    SummaryTopBar(payoff = payoff, onNavigateBack = onNavigateBack)
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
                    payoff = payoff,
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
                isRaised = payoff.isSheetRaised,
                onNavigateBack = onNavigateBack,
                onStudyAgainClick = onStudyAgainClick,
                modifier = Modifier
                    .onSizeChanged { size -> sheetHeightPx = size.height }
                    .graphicsLayer {
                        // Off screen until the rise, and hidden for the one frame before it is measured.
                        translationY = (1f - payoff.sheetProgress.value) * sheetHeightPx
                        alpha = if (sheetHeightPx == 0 && !payoff.isSettled) 0f else 1f
                    },
            )
        }
        SummaryConfetti(origin = payoff.confettiOrigin)
        if (state.scoreStatus == Scored) {
            SummaryAnnouncement(payoff = payoff, levelsCrossed = state.levelsCrossed)
        }
    }
}

/**
 * Once the Summary has settled, announces the total and, if a Level was crossed, the new Level. A
 * polite live region on its own node, so the counting total never announces itself. Zero-sized and
 * out of the way: it exists only to be spoken, and the actions are reached by ordinary traversal.
 */
@Composable
private fun SummaryAnnouncement(payoff: SummaryPayoff, levelsCrossed: List<Int>) {
    val locale = currentLocale()
    val totalText = stringResource(R.string.study_session_summary_xp_row_amount_label, formatSignedXp(payoff.xpTotal, locale))
    val levelUpText = levelsCrossed.lastOrNull()?.let { level ->
        stringResource(R.string.study_session_summary_level_up_announcement, level)
    }
    val announcement = listOfNotNull(totalText, levelUpText).joinToString(separator = ". ")
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(payoff.isSettled) {
        if (payoff.isSettled) spoken = announcement
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
 * The app bar is always laid out, so the screen never jumps when it is revealed, but until the sheet
 * rises only Skip is there: the title is invisible and the close button is not even composed, so
 * neither can be focused or tapped.
 */
@Composable
private fun SummaryTopBar(payoff: SummaryPayoff, onNavigateBack: () -> Unit) {
    val progress = payoff.appBarProgress.value
    FlashcardsGradientTopBar(
        title = stringResource(R.string.study_session_summary_title_label),
        titleAlpha = progress,
        navigationIcon = {
            if (progress > 0f) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.alpha(progress)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.study_session_summary_close_cd),
                    )
                }
            }
        },
        actions = {
            if (payoff.isSkippable) {
                FlashcardsTextButton(
                    text = stringResource(R.string.study_session_summary_skip_button),
                    onClick = payoff::skip,
                    size = FlashcardsComponentSize.Small,
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
        },
    )
}

/**
 * The settled stack (Level card, headline, total, pills), with the payoff laid over it. The card, headline
 * and pills are in the stack from the start but fade in as the card drops in. The total starts at the top
 * of the stack, above where it settles, and is pushed down to its place as the card arrives; the tiles fill
 * the space it leaves below it. Nothing is positioned by coordinates: the glide is the measured height of
 * what sits above the total.
 */
@Composable
private fun SummaryBody(
    state: StudySessionSummaryScreenState,
    payoff: SummaryPayoff,
    contentPadding: PaddingValues,
    sheetHeight: Dp,
    onDialogEvent: (StudySessionSummaryDialogEvent) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    var cardHeightPx by remember { mutableIntStateOf(0) }
    var headlineHeightPx by remember { mutableIntStateOf(0) }
    var totalHeightPx by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { spacing.medium.toPx() }
    val totalTopHeightPx = { (cardHeightPx + headlineHeightPx + 2 * gapPx).coerceAtLeast(0f) }
    val isScored = state.scoreStatus == Scored
    val revealModifier = Modifier.revealWith(payoff.isRevealed) { payoff.cardProgress.value }

    Box(
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
                .verticalScroll(rememberScrollState(), enabled = payoff.isSettled)
                .padding(horizontal = spacing.normal)
                // The sheet draws under the navigation bar, so its measured height already covers that
                // inset: it replaces the Scaffold's own bottom padding rather than adding to it.
                .padding(top = spacing.small, bottom = sheetHeight + spacing.normal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            if (isScored) {
                SummaryLevelCard(
                    payoff = payoff,
                    state = state,
                    modifier = revealModifier
                        .onSizeChanged { size -> cardHeightPx = size.height }
                        .graphicsLayer {
                            val enter = payoff.cardProgress.value
                            translationY = -(1f - enter) * CARD_SLIDE_DP.dp.toPx()
                        },
                )
            }
            SummaryHeadline(
                headline = state.headline,
                modifier = revealModifier.onSizeChanged { size -> headlineHeightPx = size.height },
            )
            if (isScored) {
                SummaryXpTotal(
                    payoff = payoff,
                    modifier = Modifier
                        .onSizeChanged { size -> totalHeightPx = size.height }
                        .graphicsLayer {
                            val glide = SlideEasing.transform(payoff.cardProgress.value)
                            translationY = -totalTopHeightPx() * (1f - glide)
                            // Until what sits above it is measured the glide distance is unknown, and the
                            // total would show for a frame at its settled place.
                            alpha = if (payoff.isSettled || (cardHeightPx > 0 && headlineHeightPx > 0)) 1f else 0f
                        },
                    onShowBreakdown = { onDialogEvent(Open(XpBreakdown)) },
                )
            }
            SummaryPills(state = state, modifier = revealModifier)
        }
        if (isScored && payoff.isListShown) {
            // Under the total's floating spot, over the part of the stack that has not appeared yet.
            SummaryXpTiles(
                lines = payoff.lines,
                visibleCount = payoff.visibleLineCount,
                alpha = { payoff.listAlpha.value },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.normal)
                    .padding(
                        top = spacing.small + with(LocalDensity.current) { totalHeightPx.toDp() } + spacing.medium,
                        bottom = spacing.normal,
                    ),
            )
        }
    }
}

/** Fades with [progress] and, until [isRevealed], stays out of accessibility too. */
private fun Modifier.revealWith(isRevealed: Boolean, progress: () -> Float): Modifier =
    graphicsLayer { alpha = progress() }.then(if (isRevealed) Modifier else Modifier.clearAndSetSemantics {})

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
 * the Summary has settled. The button's slot is always laid out, so the total never shifts when it
 * arrives. The total text is a single node, read as one figure, and deliberately not a live region: it
 * changes every frame while counting. The settled figure is announced by [SummaryAnnouncement] instead.
 */
@Composable
private fun SummaryXpTotal(
    payoff: SummaryPayoff,
    onShowBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.study_session_summary_xp_row_amount_label,
                formatSignedXp(payoff.runningTotal.value.roundToInt(), locale),
            ),
            modifier = Modifier.onGloballyPositioned { coordinates -> payoff.totalTextCoordinates = coordinates },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.brandColors.onGradientContent,
        )
        Box(modifier = Modifier.minimumInteractiveComponentSize()) {
            if (payoff.isSettled) {
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
 * The account's Level card, driven by the pour: the Level number, the bar and the "x / y XP" text all
 * follow [payoff]. Reads its animated values here, in its own scope, so a frame of the pour recomposes
 * this card and nothing around it.
 */
@Composable
private fun SummaryLevelCard(
    payoff: SummaryPayoff,
    state: StudySessionSummaryScreenState,
    modifier: Modifier = Modifier,
) {
    FlashcardsLevelCard(
        level = payoff.pourLevel,
        xpIntoCurrentLevel = payoff.pourXpIntoLevel.value.roundToLong(),
        xpForNextLevel = payoff.pourXpForNextLevel,
        progress = payoff.pourFraction.value,
        photoUrl = state.photoUrl,
        displayName = state.displayName,
        modifier = modifier.fillMaxWidth(),
        style = FlashcardsComponentStyle.OnGradient,
        showXpCount = payoff.showsXpCount,
        animateProgress = false,
        levelScale = { payoff.levelPulse.value },
    )
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
