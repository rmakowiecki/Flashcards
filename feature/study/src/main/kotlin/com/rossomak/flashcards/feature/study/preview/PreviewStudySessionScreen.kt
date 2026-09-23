package com.rossomak.flashcards.feature.study.preview

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsEmptyState
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconCircle
import com.rossomak.flashcards.core.ui.composables.FlashcardsMetadataBadge
import com.rossomak.flashcards.core.ui.composables.bars.FlashcardsGradientTopBar
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsFilledButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsOutlinedButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalButton
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTonalIconButton
import com.rossomak.flashcards.core.ui.composables.common.FlashcardsComponentStyle.OnGradient
import com.rossomak.flashcards.core.ui.composables.dialogs.label
import com.rossomak.flashcards.core.ui.composables.rememberFlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.FastStudySessionRoute
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.RatedStudySessionRoute
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardCount
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionMode
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PreviewStudySessionScreen(
    modifier: Modifier = Modifier,
    viewModel: PreviewStudySessionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToFastStudySession: (FastStudySessionRoute) -> Unit,
    onNavigateToRatedStudySession: (RatedStudySessionRoute) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            is PreviewStudySessionDestination.FastStudySession ->
                onNavigateToFastStudySession(destination.route)

            is PreviewStudySessionDestination.RatedStudySession ->
                onNavigateToRatedStudySession(destination.route)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val micPermissionStillDeniedText = stringResource(CoreUiR.string.common_mic_permission_still_denied_message)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        when (message) {
            PreviewStudySessionMessage.MicPermissionStillDenied -> snackbarScope.launch {
                snackbarHostState.showSnackbar(message = micPermissionStillDeniedText, duration = SnackbarDuration.Short)
            }
        }
    }

    PreviewStudySessionContent(
        modifier = modifier,
        state = state,
        onNavigateBack = onNavigateBack,
        onRetry = viewModel::onRetry,
        onReshuffleSubcategories = viewModel::onReshuffleSubcategories,
        onDialogEvent = viewModel::onDialogEvent,
        onResetFilters = viewModel::onResetFilters,
        onOpenAppSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        onSwitchToManualAnswering = viewModel::onSwitchToManualAnswering,
        onStartSession = viewModel::onStartSession,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The screen's brand identity: [MaterialTheme.brandColors.screenGradient] painted full-bleed behind
 * a transparent [Scaffold], with [FlashcardsGradientTopBar] bleeding it up behind the status bar for
 * free. Deliberately the brand's fixed gradient, not category-tinted — per-category colour arrives
 * with the unbuilt category icon-and-colour feature, and no route change is needed here to prepare
 * for it.
 *
 * Settings live behind a sheet hidden until asked for. Its open/closed value
 * ([settingsSheetOpen]) is screen-local view state, owned flat in this function rather than in
 * [ReadyContent] or a separate hoisted controller — see this function's own `@Suppress` for why a
 * prior extraction was dropped — because [SessionSettingsSheet] renders as a **plain, unaligned
 * sibling of [Scaffold]** in the outer [Box] below, not nested inside [ReadyContent] or Scaffold's
 * content slot at all. See that [Box]'s own comment for why. A settings badge opens the
 * sheet *and* the dialog for the value it names — but staggered, not together: the sheet slides up
 * first, the dialog fades in a beat later, since the reveal is how a user discovers the sheet
 * exists at all. `onOpenSettingsDialog` below sets the sheet open immediately and only delays the
 * dialog's own open event, via [pendingBadgeDialog] + its own `LaunchedEffect` — a plain `remember`ed
 * `Job` was tried first and rejected: nothing cancelled it on dismiss or a rapid second badge tap,
 * so a sheet swiped away mid-delay could still pop its dialog open afterward on a closed sheet.
 * Keying a `LaunchedEffect` on [pendingBadgeDialog] instead gets cancellation for free — Compose
 * cancels the previous coroutine whenever the key changes (a new badge tap) or the composable leaves
 * composition, and setting it back to `null` on dismiss/toggle-close changes the key immediately,
 * cancelling any in-flight delay the same way. [initiallySettingsSheetOpen] exists solely so a
 * `@Preview` can render the sheet-open state; every real caller leaves it at its default.
 */
@OptIn(ExperimentalMaterial3Api::class)
// This function's job is to own every top-level piece of screen state exactly once (the settings
// sheet's open value, its FlashcardsBottomSheetState, and the badge-dialog stagger) so nothing
// downstream duplicates it — splitting that ownership into a separate hoisted controller class was
// tried and dropped: it added an indirection layer (a class plus its own remember-function) for a
// detekt threshold alone, with no state actually shared outside this function.
@Suppress("LongMethod", "LongParameterList")
@Composable
fun PreviewStudySessionContent(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onReshuffleSubcategories: () -> Unit,
    onDialogEvent: (PreviewDialogEvent) -> Unit,
    onResetFilters: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSwitchToManualAnswering: () -> Unit,
    onStartSession: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    initiallySettingsSheetOpen: Boolean = false,
) {
    PreviewDialogHost(
        activeDialog = state.activeDialog,
        onDialogEvent = onDialogEvent,
    )

    var settingsSheetOpen by rememberSaveable { mutableStateOf(initiallySettingsSheetOpen) }
    val settingsSheetState = rememberFlashcardsBottomSheetState(initiallyExpanded = initiallySettingsSheetOpen)
    LaunchedEffect(settingsSheetOpen) {
        if (settingsSheetOpen) settingsSheetState.sheetState.show() else settingsSheetState.sheetState.hide()
    }

    // The badge-tap-to-dialog stagger — see this function's own doc for why a
    // LaunchedEffect keyed on this, not a manually-tracked Job, is what makes it cancellable.
    var pendingBadgeDialog by remember { mutableStateOf<PreviewDialog?>(null) }
    LaunchedEffect(pendingBadgeDialog) {
        val dialog = pendingBadgeDialog ?: return@LaunchedEffect
        delay(BADGE_DIALOG_STAGGER_DELAY_MS.milliseconds)
        onDialogEvent(Open(dialog))
        pendingBadgeDialog = null
    }
    val onDismissSettingsSheet = {
        settingsSheetOpen = false
        pendingBadgeDialog = null
    }
    val onToggleSettings = {
        settingsSheetOpen = !settingsSheetOpen
        if (!settingsSheetOpen) pendingBadgeDialog = null
    }
    val onOpenSettingsDialog: (PreviewDialog) -> Unit = { dialog ->
        settingsSheetOpen = true
        pendingBadgeDialog = dialog
    }

    // A plain, unaligned sibling of Scaffold — deliberately *not* docked inside its content slot,
    // unlike an earlier version of this screen. Scaffold's default contentWindowInsets reserve the
    // bottom safe-drawing (gesture nav) inset into innerPadding, shrinking whatever sits inside its
    // content lambda short of the true screen bottom by that inset's height. FlashcardsBottomSheet
    // wants the opposite: it already handles the bottom system-bar inset internally (see its own
    // doc) and expects to own the real screen bottom itself. Nesting it inside Scaffold's
    // inset-shrunk content double-counted that inset — the sheet's hidden position landed short of
    // the true bottom (a gesture-bar-height sliver stayed visible, "peeking") and its expanded
    // position gapped, showing this screen's gradient background beneath the sheet. Docking the
    // sheet out here instead, outside Scaffold entirely, lets it reach the true screen bottom
    // uncontested. [ReadyContent] (below, inside Scaffold's own content) still wants that inset —
    // its Start button must stay clear of the gesture bar — so Scaffold itself is untouched.
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
                    title = screenTitle(
                        state = state,
                        separator = stringResource(CoreUiR.string.common_middle_dot_separator),
                        quickTitle = stringResource(R.string.preview_session_quick_title),
                        customTitle = stringResource(R.string.preview_session_custom_title),
                    ),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.preview_session_close_cd),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.brandColors.onGradientContent)
                }

                state.error != null -> ErrorContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    error = state.error,
                    onRetry = onRetry,
                )

                else -> ReadyContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    state = state,
                    settingsSheetOpen = settingsSheetOpen,
                    onOpenSettings = { settingsSheetOpen = true },
                    onToggleSettings = onToggleSettings,
                    onOpenSettingsDialog = onOpenSettingsDialog,
                    onReshuffleSubcategories = onReshuffleSubcategories,
                    onResetFilters = onResetFilters,
                    onOpenAppSettings = onOpenAppSettings,
                    onSwitchToManualAnswering = onSwitchToManualAnswering,
                    onStartSession = onStartSession,
                )
            }
        }
        // Gated to error only, deliberately NOT to state.isLoading: a settings edit re-triggers
        // selectCards(), which flips isLoading true for the reselect and false again once it lands
        // (see PreviewStudySessionViewModel.selectCards's own doc) — a sheet already open at that
        // point must ride through untouched, or it unmounts and remounts on every edit, snapping
        // shut and sliding back open as a visible flicker. Loading is surfaced elsewhere (the
        // Scaffold content below); the sheet's own open/closed value (settingsSheetOpen) has no
        // dependency on it. state.config is always populated (a real default, never null) even
        // before the first load lands, so nothing here is meaningless during that window either —
        // and the sheet cannot be open yet at that point regardless, since ReadyContent's own
        // settings toggle is what's absent until the first load lands.
        if (state.error == null) {
            SessionSettingsSheet(
                state = state,
                sheetState = settingsSheetState,
                onDismissRequest = onDismissSettingsSheet,
                onDialogEvent = onDialogEvent,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.brandColors.onGradientContent,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        FlashcardsOutlinedButton(
            text = stringResource(CoreUiR.string.common_retry_button),
            onClick = onRetry,
            style = OnGradient,
        )
    }
}

/**
 * The ready-state body: the hero (play circle, title, scope sentence and badges — or, once the
 * pool is empty or the microphone a voice-answering session needs is permanently denied,
 * [FlashcardsEmptyState] in its place), top-anchored, with [HeroActions] pinned to
 * the screen's true bottom edge below a flexible [Spacer] — the same bottom edge
 * [SessionSettingsSheet][com.rossomak.flashcards.feature.study.preview.SessionSettingsSheet] docks
 * up from (see [PreviewStudySessionContent]'s doc), so an expanded sheet — comfortably taller than
 * this row on any real device, being every setting row stacked — fully covers it rather than
 * leaving it floating above the sheet's top edge with daylight in between. [HeroActions] stays put
 * whether the pool is empty or not, so Reshuffle topics remains reachable even from the empty
 * state — a fresh sample can turn an empty result into a match. Otherwise plain top-down [Column]
 * flow — no adaptive collapsing, no measuring against the sheet's actual height; see
 * [PreviewStudySessionContent]'s doc for why that was dropped.
 */
@Suppress("LongParameterList")
@Composable
private fun ReadyContent(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    settingsSheetOpen: Boolean,
    onOpenSettings: () -> Unit,
    onToggleSettings: () -> Unit,
    onOpenSettingsDialog: (PreviewDialog) -> Unit,
    onReshuffleSubcategories: () -> Unit,
    onResetFilters: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSwitchToManualAnswering: () -> Unit,
    onStartSession: () -> Unit,
) {
    val isEmpty = state.selectedCardCount == 0

    Column(
        modifier = modifier.padding(
            start = MaterialTheme.spacing.medium,
            end = MaterialTheme.spacing.medium,
            top = MaterialTheme.spacing.xxlarge,
            bottom = MaterialTheme.spacing.medium,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            isEmpty -> EmptyHeroBody(onResetFilters = onResetFilters)
            state.isMicPermissionRejected -> MicPermissionRejectedHeroBody(
                onOpenAppSettings = onOpenAppSettings,
                onSwitchToManualAnswering = onSwitchToManualAnswering,
            )
            else -> {
                HeroTop()
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
                ScopeHeroBody(state = state, onOpenSettings = onOpenSettings, onOpenSettingsDialog = onOpenSettingsDialog)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        HeroActions(
            state = state,
            settingsSheetOpen = settingsSheetOpen,
            onToggleSettings = onToggleSettings,
            onReshuffleSubcategories = onReshuffleSubcategories,
            onStartSession = onStartSession,
        )
    }
}

@Composable
private fun HeroTop(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FlashcardsIconCircle(
            icon = Icons.Default.PlayArrow,
            // Decorative: "Ready to start?" right below already names what this circle means.
            contentDescription = null,
            style = OnGradient,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        Text(
            // titleMedium, not a headline style: matches FlashcardsEmptyState's own title size —
            // this hero is that empty state's populated counterpart in the very same layout slot
            // (see ReadyContent), so the two should read as the same weight of "state title".
            text = stringResource(R.string.preview_session_ready_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.brandColors.onGradientContent,
        )
    }
}

/**
 * The scope sentence and its read-only badges — minutes, cards, then subcategories for a multi-subcategory
 * or Quick session (today's code emitted subcategories before cards; the design calls for this order) —
 * plus [SettingsBadgeRow] on its own line beneath them. This half of the hero always survives
 * [AdaptiveHero]'s adaptation.
 */
@Composable
private fun ScopeHeroBody(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    onOpenSettings: () -> Unit,
    onOpenSettingsDialog: (PreviewDialog) -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            // bodyMedium, matching FlashcardsEmptyState's own supportingText size (see HeroTop).
            text = scopeDescription(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.brandColors.onGradientContent,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
        ) {
            FlashcardsMetadataBadge(
                label = stringResource(R.string.preview_session_estimated_minutes_badge_label, state.estimatedMinutes),
                icon = Icons.Default.Schedule,
                style = OnGradient,
            )
            FlashcardsMetadataBadge(
                label = pluralStringResource(
                    CoreUiR.plurals.session_length_cards_label,
                    state.selectedCardCount,
                    state.selectedCardCount,
                ),
                icon = Icons.Default.Style,
                style = OnGradient,
                onClick = { onOpenSettingsDialog(SessionCardCount(draftState = state.config.length)) },
            )
            if (!state.isSingleSubcategory) {
                FlashcardsMetadataBadge(
                    label = pluralStringResource(
                        R.plurals.preview_session_topics_badge_label,
                        state.subcategoryCount,
                        state.subcategoryCount,
                    ),
                    icon = Icons.AutoMirrored.Filled.List,
                    style = OnGradient,
                    onClick = {
                        // Quick's subcategory count is the SubcategoryCountRange setting; Custom's
                        // subcategories are hand-picked outside this screen, so no dialog matches
                        // them — the badge falls back to just revealing the sheet (ticket per grill).
                        if (state.isQuickSession) {
                            onOpenSettingsDialog(QuickSessionSubcategoryCountRange(draftState = state.config.subcategoryCountRange))
                        } else {
                            onOpenSettings()
                        }
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
        SettingsBadgeRow(state = state, onOpenSettingsDialog = onOpenSettingsDialog)
    }
}

/**
 * Mode and interaction, tappable — a new user's only on-screen evidence that either is a choice at
 * all, now that the settings sheet defaults to hidden. Kept off the read-only scope
 * badges' own row: a tappable pill sitting among read-only ones is poor affordance and worse
 * accessibility. Each [FlashcardsMetadataBadge] gets a non-null `onClick`, which is what
 * makes it announce itself as a button rather than static text — the scope badges above pass none.
 *
 * The second badge always names a behaviour, never its absence — "Manual", not "Off" — a deliberate
 * reversal of the usual rule against badging negatives.
 */
@Composable
private fun SettingsBadgeRow(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    onOpenSettingsDialog: (PreviewDialog) -> Unit,
) {
    val isRated = state.config.mode == StudyMode.Rated
    val interactionEnabled = if (isRated) state.config.voiceAnsweringEnabled else state.config.readAloudEnabled

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xsmall),
    ) {
        FlashcardsMetadataBadge(
            label = state.config.mode.label(),
            icon = if (isRated) Icons.Default.Star else Icons.Default.Bolt,
            style = OnGradient,
            onClick = { onOpenSettingsDialog(SessionMode(draftState = state.config.mode)) },
        )
        val interactionBadge = interactionBadgeContent(isRated = isRated, enabled = interactionEnabled)
        FlashcardsMetadataBadge(
            label = interactionBadge.label,
            icon = interactionBadge.icon,
            style = OnGradient,
            onClick = {
                onOpenSettingsDialog(
                    if (isRated) {
                        RatedSessionVoiceAnswering(draftState = state.config.voiceAnsweringEnabled)
                    } else {
                        FastSessionReadAloud(draftState = state.config.readAloudEnabled)
                    },
                )
            },
        )
    }
}

/** [SettingsBadgeRow]'s second badge: label plus a leading icon, together since both are picked by the same three-way classification. */
private data class InteractionBadgeContent(val label: String, val icon: ImageVector)

/**
 * "Voice" (Rated) or "Auto" (Fast) when [enabled], "Manual" either way when not — never "Off". The
 * icon mirrors what each value already carries in
 * [VoiceAnsweringDialog][com.rossomak.flashcards.core.ui.composables.dialogs.VoiceAnsweringDialog]/
 * [ReadAloudDialog][com.rossomak.flashcards.core.ui.composables.dialogs.ReadAloudDialog].
 */
@Composable
private fun interactionBadgeContent(isRated: Boolean, enabled: Boolean): InteractionBadgeContent = when {
    !enabled -> InteractionBadgeContent(
        label = stringResource(R.string.preview_session_interaction_manual_label),
        icon = Icons.Default.TouchApp,
    )

    isRated -> InteractionBadgeContent(
        label = stringResource(R.string.preview_session_interaction_voice_label),
        icon = Icons.Default.Mic,
    )

    else -> InteractionBadgeContent(
        label = stringResource(R.string.preview_session_interaction_auto_label),
        icon = Icons.AutoMirrored.Filled.VolumeUp,
    )
}

/**
 * The nothing-matches state: [FlashcardsEmptyState] replaces the *whole* hero above it (no
 * play circle, no title, no scope sentence, no badges — [AdaptiveHero] never even composes
 * [HeroTop] when [ReadyContent] finds the pool empty), with a single Reset filters action that
 * restores what the screen was originally handed. Settings remains reachable from an empty pool via
 * [HeroActions]' own settings toggle, which stays put either way below this body (with Start
 * session visible but disabled) so it's never out of reach. Reshuffle is deliberately absent too —
 * [HeroActions] itself keeps it off in the empty state.
 */
@Composable
private fun EmptyHeroBody(modifier: Modifier = Modifier, onResetFilters: () -> Unit) {
    FlashcardsEmptyState(
        modifier = modifier,
        icon = Icons.Default.SearchOff,
        title = stringResource(R.string.preview_session_empty_state_title),
        supportingText = stringResource(R.string.preview_session_empty_state_message),
        style = OnGradient,
        button = {
            FlashcardsFilledButton(
                text = stringResource(R.string.preview_session_empty_state_reset_button),
                onClick = onResetFilters,
                icon = Icons.Default.Refresh,
                style = OnGradient,
            )
        },
    )
}

/**
 * Takes the same hero slot as [EmptyHeroBody], which wins when both apply — with no cards there is
 * no session to fix the microphone for. Switch to manual turns voice answering off for this session
 * only.
 */
@Composable
private fun MicPermissionRejectedHeroBody(
    modifier: Modifier = Modifier,
    onOpenAppSettings: () -> Unit,
    onSwitchToManualAnswering: () -> Unit,
) {
    FlashcardsEmptyState(
        modifier = modifier,
        icon = Icons.Default.MicOff,
        title = stringResource(R.string.preview_session_mic_permission_empty_state_title),
        supportingText = stringResource(R.string.preview_session_mic_permission_empty_state_message),
        style = OnGradient,
        button = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                FlashcardsFilledButton(
                    text = stringResource(R.string.preview_session_mic_permission_open_settings_button),
                    onClick = onOpenAppSettings,
                    style = OnGradient,
                )
                FlashcardsOutlinedButton(
                    text = stringResource(R.string.preview_session_mic_permission_switch_to_manual_button),
                    onClick = onSwitchToManualAnswering,
                    style = OnGradient,
                )
            }
        },
    )
}

/**
 * **Start session** plus the unlabelled settings toggle, with **Reshuffle topics** for Quick
 * sessions only — except when nothing matches: reshuffling there is offered nowhere,
 * not just left off the empty state's own two actions, since a stale sample and a fresh one look
 * identical until reshuffled. Custom never offers it, single- or multi-subcategory alike: its
 * subcategories are hand-picked by the user, not sampled, so there is nothing to reshuffle
 * ([PreviewStudySessionScreenState.canReshuffleSubcategories]). Reshuffle stays enabled independent
 * of [PreviewStudySessionScreenState.canStart] otherwise: a fresh sample can turn an empty result
 * into a match, which is exactly when reshuffling is needed. Start's label never changes with the
 * sheet's open/closed value — the primary action's text must not shift under the user — and it stays
 * visible but disabled whenever nothing is selected, rather than disappearing.
 *
 * Two shapes, picked by the same condition that gates Reshuffle itself: when it's offered, Start
 * gets its own full-width row on top (the settings toggle isn't reachable in that row anyway once
 * Reshuffle joins it — two weighted buttons plus an icon overflow a phone's width), with the
 * toggle sharing Reshuffle's row beneath. Everywhere else (single-subcategory, Custom, or an empty
 * pool) the toggle stays paired with Start, as before.
 */
@Composable
private fun HeroActions(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    settingsSheetOpen: Boolean,
    onToggleSettings: () -> Unit,
    onReshuffleSubcategories: () -> Unit,
    onStartSession: () -> Unit,
) {
    val showReshuffle = state.canReshuffleSubcategories && state.selectedCardCount > 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (showReshuffle) {
            StartSessionButton(
                state = state,
                onStartSession = onStartSession,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsToggleButton(settingsSheetOpen = settingsSheetOpen, onToggleSettings = onToggleSettings)
                FlashcardsTonalButton(
                    text = stringResource(R.string.preview_session_reshuffle_button),
                    onClick = onReshuffleSubcategories,
                    enabled = !state.isLoading,
                    icon = Icons.Default.Shuffle,
                    style = OnGradient,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsToggleButton(settingsSheetOpen = settingsSheetOpen, onToggleSettings = onToggleSettings)
                StartSessionButton(
                    state = state,
                    onStartSession = onStartSession,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StartSessionButton(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    onStartSession: () -> Unit,
) {
    FlashcardsFilledButton(
        text = stringResource(CoreUiR.string.common_start_session_button),
        onClick = onStartSession,
        enabled = state.canStart,
        icon = Icons.Default.PlayArrow,
        style = OnGradient,
        modifier = modifier,
    )
}

@Composable
private fun SettingsToggleButton(settingsSheetOpen: Boolean, onToggleSettings: () -> Unit) {
    FlashcardsTonalIconButton(
        icon = Icons.Default.Tune,
        contentDescription = stringResource(if (settingsSheetOpen) R.string.preview_session_close_settings_cd else R.string.preview_session_open_settings_cd),
        onClick = onToggleSettings,
        style = OnGradient,
    )
}

/**
 * How long a settings badge tap waits after opening the sheet before opening its dialog — long enough that the sheet's own slide-up
 * reads as a distinct event before the dialog (and its background blur) covers it*/
private const val BADGE_DIALOG_STAGGER_DELAY_MS = 250L

private fun screenTitle(
    state: PreviewStudySessionScreenState,
    separator: String,
    quickTitle: String,
    customTitle: String,
): String = when {
    state.isQuickSession -> "${state.categoryName}$separator$quickTitle"
    state.isSingleSubcategory -> "${state.categoryName}$separator${state.subcategoryNames.first()}"
    else -> "${state.categoryName}$separator$customTitle"
}

@Composable
private fun scopeDescription(state: PreviewStudySessionScreenState): AnnotatedString {
    val cardsText = pluralStringResource(
        CoreUiR.plurals.session_length_cards_label,
        state.selectedCardCount,
        state.selectedCardCount,
    )
    val otherSubcategoriesText = (state.subcategoryNames.size - SUBCATEGORY_LIST_VISIBLE_COUNT)
        .takeIf { state.subcategoryNames.size > SUBCATEGORY_LIST_TRUNCATION_THRESHOLD }
        ?.let { otherCount ->
            pluralStringResource(R.plurals.preview_session_scope_other_subcategories_count, otherCount, otherCount)
        }
    val listSeparator = stringResource(CoreUiR.string.common_list_separator)
    val listTwoItemConjunction = stringResource(CoreUiR.string.common_list_two_item_conjunction)
    val listFinalConjunction = stringResource(CoreUiR.string.common_list_final_conjunction)
    return buildAnnotatedString {
        fun appendBold(text: String) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
        }

        when {
            state.isQuickSession -> {
                appendBold(cardsText)
                append(stringResource(R.string.preview_session_scope_quick_session_message))
                appendSubcategoryList(
                    state.subcategoryNames,
                    ::appendBold,
                    otherSubcategoriesText,
                    listSeparator,
                    listTwoItemConjunction,
                    listFinalConjunction,
                )
            }

            state.isSingleSubcategory -> {
                appendBold(cardsText)
                append(stringResource(R.string.preview_session_scope_single_subcategory_prefix_message))
                appendBold(state.subcategoryNames.first())
                append(stringResource(R.string.preview_session_scope_single_subcategory_suffix_message))
            }

            else -> {
                appendBold(cardsText)
                append(stringResource(R.string.preview_session_scope_multi_subcategory_message))
                appendSubcategoryList(
                    state.subcategoryNames,
                    ::appendBold,
                    otherSubcategoriesText,
                    listSeparator,
                    listTwoItemConjunction,
                    listFinalConjunction,
                )
            }
        }
    }
}

/**
 * Names past this count stop being useful to read at a glance and collapse into a count instead.
 * Pinned to [StudySessionConfig.MAX_SUBCATEGORY_COUNT] — Quick's subcategory-count cap — so a
 * Quick session, whose subcategories the user did not choose, making naming them the whole point of a
 * preview, never truncates. Custom sessions, unbounded, still collapse once they cross it.
 */
private const val SUBCATEGORY_LIST_TRUNCATION_THRESHOLD = StudySessionConfig.MAX_SUBCATEGORY_COUNT
private const val SUBCATEGORY_LIST_VISIBLE_COUNT = 3

/**
 * Lists every Subcategory name in full when [otherSubcategoriesText] is `null` (fits within
 * [SUBCATEGORY_LIST_TRUNCATION_THRESHOLD]); otherwise the first [SUBCATEGORY_LIST_VISIBLE_COUNT]
 * plus that pre-resolved "and N other topics" summary of the rest.
 */
private fun AnnotatedString.Builder.appendSubcategoryList(
    names: List<String>,
    appendBold: (String) -> Unit,
    otherSubcategoriesText: String?,
    listSeparator: String,
    listTwoItemConjunction: String,
    listFinalConjunction: String,
) {
    if (otherSubcategoriesText == null) {
        appendOxfordList(names, appendBold, listSeparator, listTwoItemConjunction, listFinalConjunction)
        return
    }
    names.take(SUBCATEGORY_LIST_VISIBLE_COUNT).forEachIndexed { index, name ->
        if (index > 0) append(listSeparator)
        appendBold(name)
    }
    append(otherSubcategoriesText)
}

// Joins names as "A and B" (2 items) or "A, B, and C" (3+ items), bolding each name.
private fun AnnotatedString.Builder.appendOxfordList(
    names: List<String>,
    appendBold: (String) -> Unit,
    listSeparator: String,
    listTwoItemConjunction: String,
    listFinalConjunction: String,
) {
    names.forEachIndexed { index, name ->
        when (index) {
            0 -> Unit
            names.lastIndex -> append(if (names.size > 2) listFinalConjunction else listTwoItemConjunction)
            else -> append(listSeparator)
        }
        appendBold(name)
    }
}
