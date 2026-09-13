package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsInfoBanner
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownRow
import com.rossomak.flashcards.core.ui.composables.banners.FlashcardsXpBreakdownTone
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsGradientTopBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentSize
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle
import com.rossomak.flashcards.core.ui.composables.level.FlashcardsLevelCard
import com.rossomak.flashcards.core.ui.composables.rememberFlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A Rated or Fast Study Session's mandatory egress — natural end or premature exit alike. Its own
 * payoff moment: the XP breakdown pours into a running total, then into the account's level card,
 * before the screen settles into a results panel. [onNavigateBack] is this screen's one action, and
 * system back from it does the same thing — both pop straight to the tab the user started from,
 * since `NavGraph.kt` already replaced everything between them on the back stack when it navigated
 * here.
 */
@Composable
fun StudySessionSummaryScreen(
    modifier: Modifier = Modifier,
    viewModel: StudySessionSummaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val saveFailedMessage = stringResource(R.string.study_session_summary_save_failed_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            StudySessionSummaryMessage.SaveFailed -> saveFailedMessage
        }
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }

    StudySessionSummaryContent(
        modifier = modifier,
        state = state,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Drives the animated pour, presentation-only sequencing that has no business living in the
 * ViewModel's `StateFlow` (which stays "what happened," not "what the screen is currently
 * animating"). [Ring] only ever occurs for a Rated result. [LevelCard] and [Panel] no longer share
 * a Crossfade slot with [Ring]/[XpPour] — the level card is its own top-level block once either
 * phase is reached (see [StudySessionSummaryContent]'s layout), so [Phase] stays a plain enum with
 * no collapsing indirection.
 */
private enum class Phase {
    Ring,
    XpPour,
    LevelCard,
    Panel,
}

/** The two mutually-exclusive states the Ring/XpPour [Crossfade] switches between. */
private enum class RingPourPhase {
    Ring,
    XpPour,
}

private const val RING_PHASE_DURATION_MS = 1_800L
private const val XP_ROW_STAGGER_DELAY_MS = 380L
private const val XP_ROW_COUNT_DURATION_MS = 700
private const val XP_ROW_SETTLE_DELAY_MS = 800L
private const val LEVEL_CARD_ENTRANCE_DELAY_MS = 600L
private const val LEVEL_CARD_FILL_SETTLE_DELAY_MS = 1_400L
private const val PERCENT_MULTIPLIER = 100
private const val SECONDS_PER_MINUTE = 60

// Entrance-transition durations, all slower than Compose's ~300ms defaults so the payoff sequence
// reads as deliberate rather than snappy — see each AnimatedVisibility call site below.
private const val HEADER_ENTER_DURATION_MS = 700
private const val LEVEL_CARD_ENTER_DURATION_MS = 900
private const val XP_ROW_ENTER_DURATION_MS = 550
private const val BOTTOM_SHEET_ENTER_DURATION_MS = 700

// Hero-sized ring for the Ring phase's mastery moment — well past FlashcardsCircularProgressRing's
// Normal/Small tiers (56dp/40dp), so drawn locally rather than stretching that shared component's
// size axis for one caller (see MasteryRing's own doc).
private val MASTERY_RING_DIAMETER: Dp = 200.dp
private val MASTERY_RING_STROKE: Dp = 14.dp
private const val MASTERY_RING_TRACK_ALPHA = 0.3f
private const val MASTERY_RING_START_ANGLE = -90f
private const val MASTERY_RING_SWEEP_ANGLE = 360f

private const val BADGE_ROW_ENTRANCE_DELAY_MS = 450L

@Suppress("LongMethod")
@Composable
fun StudySessionSummaryContent(
    modifier: Modifier = Modifier,
    state: StudySessionSummaryScreenState,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // DEBUG-ONLY, remove ASAP: bumped by the top bar's retry icon to force every `remember` below
    // back to its initial value, replaying the whole entrance sequence from scratch.
    var replayKey by remember { mutableIntStateOf(0) }

    // rememberSaveable, not remember: survives config changes (e.g. rotation) so an in-progress
    // or already-settled sequence resumes where it left off instead of replaying from Phase.Ring.
    // Ring only exists for a Rated result — Fast has no mastery concept to play first (see Phase's
    // own doc) — captured once: state.mode never changes for the lifetime of this screen.
    var phase by rememberSaveable(replayKey) { mutableStateOf(if (state.mode == StudyMode.Rated) Phase.Ring else Phase.XpPour) }
    var xpRevealedCount by rememberSaveable(replayKey) { mutableIntStateOf(0) }
    var levelBarFilled by rememberSaveable(replayKey) { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase != Phase.Ring) return@LaunchedEffect
        delay(RING_PHASE_DURATION_MS)
        phase = Phase.XpPour
    }
    // Keyed on state.xpLines too: it starts empty and is populated once, asynchronously, by the
    // optimistic preview resolving — this restarts the reveal against the real list if that
    // resolves while this phase is already active, rather than pouring an empty list.
    LaunchedEffect(phase, state.xpLines) {
        if (phase != Phase.XpPour) return@LaunchedEffect
        if (state.xpLines.isEmpty()) {
            phase = Phase.LevelCard
            return@LaunchedEffect
        }
        state.xpLines.indices.forEach { index ->
            xpRevealedCount = index + 1
            delay(XP_ROW_STAGGER_DELAY_MS)
        }
        delay(XP_ROW_SETTLE_DELAY_MS)
        phase = Phase.LevelCard
    }
    LaunchedEffect(phase) {
        if (phase != Phase.LevelCard) return@LaunchedEffect
        delay(LEVEL_CARD_ENTRANCE_DELAY_MS)
        levelBarFilled = true
        // Nothing to fill: advance immediately instead of waiting out a fill animation that
        // would never visibly move.
        delay(if (state.xpIntoCurrentLevel == 0L) 0L else LEVEL_CARD_FILL_SETTLE_DELAY_MS)
        phase = Phase.Panel
    }

    // Cancels every LaunchedEffect above keyed on `phase` (Compose cancels the previous coroutine
    // whenever a LaunchedEffect's key changes) — jumping phase straight to Panel is enough on its
    // own; xpRevealedCount/levelBarFilled are forced to their final values here since the effects
    // that would otherwise reach them may never have run (e.g. skipping mid-Ring).
    val onSkip = {
        xpRevealedCount = state.xpLines.size
        levelBarFilled = true
        phase = Phase.Panel
    }

    // Fires once on first composition — the header's own entrance, independent of `phase`.
    // headerAppeared is saved across config changes; MutableTransitionState itself isn't
    // Saveable, so it's seeded from that flag instead — once already true, current/target start
    // equal and no enter transition replays.
    var headerAppeared by rememberSaveable { mutableStateOf(false) }
    val headerVisibleState = remember { MutableTransitionState(headerAppeared) }
    LaunchedEffect(Unit) {
        headerVisibleState.targetState = true
        headerAppeared = true
    }

    val targetProgress = if (state.xpForNextLevel <= 0L) {
        0f
    } else {
        (state.xpIntoCurrentLevel.toFloat() / state.xpForNextLevel.toFloat()).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.brandColors.screenGradient),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
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
                    actions = {
                        // DEBUG-ONLY, remove ASAP: replays the whole entrance sequence from scratch.
                        IconButton(onClick = { replayKey++ }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Replay animations (debug)",
                            )
                        }
                        // The Skip slot's visibility, not its layout space, changes — nothing else
                        // in this bar shifts as the sequence advances.
                        if (phase != Phase.Panel) {
                            FlashcardsTextButton(
                                text = stringResource(R.string.study_session_summary_skip_button),
                                onClick = onSkip,
                                size = FlashcardsComponentSize.Small,
                                style = FlashcardsComponentStyle.OnGradient,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = MaterialTheme.spacing.normal)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Generous top-level rhythm between the level card / header / payoff blocks,
                // matching the design's airy resting-state layout rather than a tight list.
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xlarge),
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                // The level card is its own top-level block, ahead of the header in Column order —
                // it slides down from above the screen once Ring/XpPour finish, pushing the
                // "Great work!" section below it down rather than crossfading in its place (order
                // deliberately flipped from the header-first layout the earlier phases use).
                AnimatedVisibility(
                    visible = phase == Phase.LevelCard || phase == Phase.Panel,
                    enter = fadeIn(tween(LEVEL_CARD_ENTER_DURATION_MS)) +
                        slideInVertically(animationSpec = tween(LEVEL_CARD_ENTER_DURATION_MS), initialOffsetY = { -it }),
                    // See XpPourPhaseContent's exit comment: the default exit's shrinkOut() would
                    // silently clip this card during its own enter too. Phase only moves forward,
                    // so exit never plays.
                    exit = ExitTransition.None,
                ) {
                    FlashcardsLevelCard(
                        level = state.level,
                        xpIntoCurrentLevel = state.xpIntoCurrentLevel,
                        xpForNextLevel = state.xpForNextLevel,
                        progress = if (levelBarFilled) targetProgress else 0f,
                        modifier = Modifier.fillMaxWidth(),
                        style = FlashcardsComponentStyle.OnGradient,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
                ) {
                    AnimatedVisibility(
                        visibleState = headerVisibleState,
                        enter = fadeIn(tween(HEADER_ENTER_DURATION_MS)) +
                            slideInVertically(animationSpec = tween(HEADER_ENTER_DURATION_MS), initialOffsetY = { it / 4 }),
                        // See XpPourPhaseContent's exit comment.
                        exit = ExitTransition.None,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
                        ) {
                            SummaryHeadline(state = state)
                            // Duplicates the duration/cards-studied badge shown once the panel
                            // settles, so it steps aside for that resting-state row rather than
                            // sitting alongside it (see the design's final layout).
                            if (phase != Phase.Panel) {
                                SummaryMetaLine(state = state)
                            }
                        }
                    }
                    if (phase == Phase.Panel) {
                        SummaryXpTotalText(state = state)
                        // Metadata badges never animate in — they pop in with their block, per
                        // the no-badge-animation rule (unlike the level card and header above
                        // them). No fillMaxWidth: the row stays wrap-content so this column's own
                        // CenterHorizontally centers the whole group, matching the design.
                        SummaryStatsBadgeRow(state = state)
                    }
                }
                if (phase == Phase.Ring || phase == Phase.XpPour) {
                    val ringPourPhase = if (phase == Phase.Ring) RingPourPhase.Ring else RingPourPhase.XpPour
                    Crossfade(targetState = ringPourPhase, label = "SummaryPhase") { targetPhase ->
                        when (targetPhase) {
                            RingPourPhase.Ring -> RingPhaseContent(state = state, modifier = Modifier.fillMaxWidth())
                            RingPourPhase.XpPour -> XpPourPhaseContent(
                                xpLines = state.xpLines,
                                revealedCount = xpRevealedCount,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.xlarge))
            }
        }
        // A plain, unaligned sibling of Scaffold, never Modifier.align(Alignment.BottomCenter) —
        // see FlashcardsBottomSheet's own doc for why that double-offsets the sheet (ADR-0043).
        AnimatedVisibility(
            visible = phase == Phase.Panel,
            enter = fadeIn(tween(BOTTOM_SHEET_ENTER_DURATION_MS)) +
                slideInVertically(animationSpec = tween(BOTTOM_SHEET_ENTER_DURATION_MS), initialOffsetY = { it }),
            // See XpPourPhaseContent's exit comment.
            exit = ExitTransition.None,
        ) {
            FlashcardsBottomSheet(
                state = rememberFlashcardsBottomSheetState(dismissible = false),
                onDismissRequest = {},
            ) {
                FlashcardsInfoBanner(
                    text = stringResource(R.string.study_session_summary_tip_message),
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
            }
        }
    }
}

@Composable
private fun SummaryHeadline(state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            if (state.abandoned) {
                R.string.study_session_summary_headline_abandoned_label
            } else {
                R.string.study_session_summary_headline_label
            },
        ),
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.brandColors.onGradientContent,
    )
}

/**
 * The resting-state payoff figure — the same total the XP-pour phase counts up to, shown static
 * (no count-up of its own) once [Phase.Panel] settles, since the pour already played that beat.
 */
@Composable
private fun SummaryXpTotalText(state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    val total = state.xpLines.sumOf { it.amount }
    Text(
        text = stringResource(R.string.study_session_summary_xp_running_total_label, total),
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.brandColors.onGradientContent,
    )
}

/** Duration + cards-studied, combined via a template placeholder rather than concatenated (ADR-0023). */
@Composable
private fun SummaryMetaLine(state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    val durationText = stringResource(
        R.string.study_session_summary_duration_label,
        state.durationSeconds / SECONDS_PER_MINUTE,
        state.durationSeconds % SECONDS_PER_MINUTE,
    )
    val studiedText = pluralStringResource(
        R.plurals.study_session_summary_cards_studied_label,
        state.studiedCount,
        state.studiedCount,
    )
    Text(
        text = stringResource(R.string.study_session_summary_meta_label, durationText, studiedText),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.brandColors.onGradientContent,
    )
}

/**
 * Rated-only: plays the mastery figures first, per "a Rated session plays its mastery figures
 * first... before the pour begins." [MasteryRing] animates its own sweep; [percent] is a
 * separately-driven count-up synced to roughly the same duration, since the ring's own animated
 * progress value isn't readable from outside the component. The mastered/partial/failed row enters
 * on its own beat, slightly after the ring, via [FlashcardsMetadataBadge] rather than plain text.
 */
@Composable
private fun RingPhaseContent(modifier: Modifier = Modifier, state: StudySessionSummaryScreenState) {
    val masteredFraction = if (state.studiedCount <= 0) {
        0f
    } else {
        state.masteredCount.toFloat() / state.studiedCount
    }
    val percent by animateIntAsState(
        targetValue = (masteredFraction * PERCENT_MULTIPLIER).toInt(),
        label = "MasteredPercent",
    )
    // Delayed appearance, no entrance animation — metadata badges never animate in.
    var statsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BADGE_ROW_ENTRANCE_DELAY_MS)
        statsVisible = true
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.normal),
    ) {
        MasteryRing(progress = masteredFraction) {
            Text(
                text = stringResource(CoreUiR.string.common_mastery_progress_label, percent),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.brandColors.onGradientContent,
            )
            Text(
                text = stringResource(R.string.study_session_summary_mastered_caption_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.brandColors.onGradientContent,
            )
        }
        if (statsVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                FlashcardsMetadataBadge(
                    label = stringResource(R.string.study_session_summary_mastered_count_label, state.masteredCount),
                    icon = Icons.Filled.WorkspacePremium,
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsMetadataBadge(
                    label = stringResource(R.string.study_session_summary_partial_count_label, state.partialCount),
                    icon = Icons.Filled.Star,
                    style = FlashcardsComponentStyle.OnGradient,
                )
                FlashcardsMetadataBadge(
                    label = stringResource(R.string.study_session_summary_failed_count_label, state.failedCount),
                    icon = Icons.Filled.Close,
                    style = FlashcardsComponentStyle.OnGradient,
                )
            }
        }
    }
}

/**
 * The Ring phase's hero mastery ring — a plain track + progress arc (no baked-in text, same shape
 * as [com.rossomak.flashcards.core.ui.composables.progress.FlashcardsCircularProgressRing]) but
 * sized well past that shared component's Normal/Small tiers. Kept local rather than adding a
 * third size tier there: this hero diameter has exactly one caller, and `FlashcardsComponentSize`
 * is a shared axis every other `Flashcards*` component switches on (ADR-0034) — widening it for one
 * screen's one-off hero moment would ripple into every unrelated `when` over that enum.
 */
@Composable
private fun MasteryRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(RING_PHASE_DURATION_MS.toInt()),
        label = "MasteryRingProgress",
    )
    val fillColor = MaterialTheme.brandColors.onGradientContent
    val trackColor = fillColor.copy(alpha = MASTERY_RING_TRACK_ALPHA)

    Box(
        modifier = modifier.size(MASTERY_RING_DIAMETER),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidthPx = MASTERY_RING_STROKE.toPx()
            val arcDiameter = size.minDimension - strokeWidthPx
            val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
            val arcSize = Size(arcDiameter, arcDiameter)

            drawArc(
                color = trackColor,
                startAngle = MASTERY_RING_START_ANGLE,
                sweepAngle = MASTERY_RING_SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt),
            )
            // Guarded as FlashcardsCircularProgressRing's own draw is: a zero-sweep stroked arc
            // with a round cap still renders a full-diameter dot, not nothing.
            if (animatedProgress > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = MASTERY_RING_START_ANGLE,
                    sweepAngle = MASTERY_RING_SWEEP_ANGLE * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * Each line enters one at a time, staggered by [revealedCount], via a plain fade-in + translate-up
 * ([AnimatedXpBreakdownRow]'s [AnimatedVisibility] below) — no per-row countdown or other reveal
 * effect on the row's own value, which shows its final amount immediately. [runningTotal] above the
 * rows is the one animated figure, counting up as each row enters.
 */
@Composable
private fun XpPourPhaseContent(
    modifier: Modifier = Modifier,
    xpLines: List<XpBreakdownLine>,
    revealedCount: Int,
) {
    val totalSoFar = xpLines.take(revealedCount).sumOf { it.amount }
    val runningTotal by animateIntAsState(
        targetValue = totalSoFar,
        animationSpec = tween(XP_ROW_COUNT_DURATION_MS),
        label = "XpRunningTotal",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = stringResource(R.string.study_session_summary_xp_running_total_label, runningTotal),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.brandColors.onGradientContent,
        )
        xpLines.forEachIndexed { index, line ->
            AnimatedVisibility(
                visible = index < revealedCount,
                enter = fadeIn(tween(XP_ROW_ENTER_DURATION_MS)) +
                    slideInVertically(animationSpec = tween(XP_ROW_ENTER_DURATION_MS), initialOffsetY = { it / 2 }),
                // Explicit no-op exit: the default exit is fadeOut() + shrinkOut(), and shrinkOut's
                // size-change spec flips AnimatedVisibility's internal clip flag on for the whole
                // component (enter included), clipping the row to its animating bounds instead of
                // showing the full card while it fades and slides. Rows only ever go visible, never
                // back, so no exit ever plays — this just kills the phantom shrink/clip.
                exit = ExitTransition.None,
            ) {
                AnimatedXpBreakdownRow(line = line, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AnimatedXpBreakdownRow(line: XpBreakdownLine, modifier: Modifier = Modifier) {
    FlashcardsXpBreakdownRow(
        label = stringResource(xpBreakdownRowLabelRes(line.source)),
        value = stringResource(R.string.study_session_summary_xp_row_value, line.count, line.rate, line.amount),
        icon = xpBreakdownRowIcon(line.source),
        modifier = modifier,
        tone = if (line.isLoss) FlashcardsXpBreakdownTone.Loss else FlashcardsXpBreakdownTone.Gain,
    )
}

private fun xpBreakdownRowLabelRes(source: XpAwardSource): Int = when (source) {
    XpAwardSource.NewCards -> R.string.study_session_summary_xp_row_new_cards_label
    XpAwardSource.Mastered -> R.string.study_session_summary_xp_row_mastered_label
    XpAwardSource.Partial -> R.string.study_session_summary_xp_row_partial_label
    XpAwardSource.MasteryDefended -> R.string.study_session_summary_xp_row_mastery_defended_label
    XpAwardSource.MasteryLost -> R.string.study_session_summary_xp_row_mastery_lost_label
    XpAwardSource.TimeStudied -> R.string.study_session_summary_xp_row_time_studied_label
    XpAwardSource.SessionCompleted -> R.string.study_session_summary_xp_row_session_completed_label
}

private fun xpBreakdownRowIcon(source: XpAwardSource): ImageVector = when (source) {
    XpAwardSource.NewCards -> Icons.Filled.Add
    XpAwardSource.Mastered -> Icons.Filled.WorkspacePremium
    XpAwardSource.Partial -> Icons.Filled.Star
    XpAwardSource.MasteryDefended -> Icons.Filled.Shield
    XpAwardSource.MasteryLost -> Icons.AutoMirrored.Filled.TrendingDown
    XpAwardSource.TimeStudied -> Icons.Filled.Schedule
    XpAwardSource.SessionCompleted -> Icons.Filled.CheckCircle
}

/**
 * The resting-state stat row — duration always, then either mastered+failed (Rated) or cards
 * studied (Fast, which never produces mastered/partial/failed outcomes). Mirrors the Ring phase's
 * own badge row rather than a plain text line, per the design's steady-state pill treatment.
 */
@Composable
private fun SummaryStatsBadgeRow(state: StudySessionSummaryScreenState, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        FlashcardsMetadataBadge(
            label = stringResource(R.string.study_session_summary_duration_badge_label, state.durationSeconds / SECONDS_PER_MINUTE),
            icon = Icons.Filled.Schedule,
            style = FlashcardsComponentStyle.OnGradient,
        )
        if (state.mode == StudyMode.Rated) {
            FlashcardsMetadataBadge(
                label = stringResource(R.string.study_session_summary_mastered_count_label, state.masteredCount),
                icon = Icons.Filled.WorkspacePremium,
                style = FlashcardsComponentStyle.OnGradient,
            )
            FlashcardsMetadataBadge(
                label = stringResource(R.string.study_session_summary_failed_count_label, state.failedCount),
                icon = Icons.Filled.Close,
                style = FlashcardsComponentStyle.OnGradient,
            )
        } else {
            FlashcardsMetadataBadge(
                label = pluralStringResource(
                    R.plurals.study_session_summary_cards_badge_label,
                    state.studiedCount,
                    state.studiedCount,
                ),
                icon = Icons.Filled.Style,
                style = FlashcardsComponentStyle.OnGradient,
            )
        }
    }
}
