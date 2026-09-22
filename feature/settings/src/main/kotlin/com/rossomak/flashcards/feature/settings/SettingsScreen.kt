package com.rossomak.flashcards.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsIconTile
import com.rossomak.flashcards.core.ui.composables.FlashcardsOverlineLabel
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.dialogs.label
import com.rossomak.flashcards.core.ui.composables.dialogs.partialRatingCardRequeueingLabel
import com.rossomak.flashcards.core.ui.composables.dialogs.readAloudLabel
import com.rossomak.flashcards.core.ui.composables.dialogs.speechRateLabel
import com.rossomak.flashcards.core.ui.composables.dialogs.voiceAnsweringLabel
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsChevron
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroup
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsListGroupItem
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.navigation.observeAsEvents
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.settings.SettingsDialog.DailyStudyGoal
import com.rossomak.flashcards.feature.settings.SettingsDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.settings.SettingsDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionMaxCardAttempts
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionPartialRatingCardRequeueing
import com.rossomak.flashcards.feature.settings.SettingsDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardCount
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionMode
import com.rossomak.flashcards.feature.settings.SettingsDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.settings.SettingsDialog.SignOut
import com.rossomak.flashcards.feature.settings.SettingsMessage.SaveFailed
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    observeAsEvents(viewModel.events) { destination ->
        when (destination) {
            SettingsDestination.Login -> onNavigateToLogin()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val saveFailedMessage = stringResource(R.string.settings_save_failed_error)
    val snackbarScope = rememberCoroutineScope()
    observeAsEvents(viewModel.messages) { message ->
        val text = when (message) {
            SaveFailed -> saveFailedMessage
        }
        snackbarScope.launch {
            snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        }
    }

    SettingsContent(
        modifier = modifier,
        state = state,
        snackbarHostState = snackbarHostState,
        onDialogEvent = viewModel::onDialogEvent,
    )
}

/**
 * Every row is the same shape — a committed value, a chevron, and a dialog that edits it — so the
 * screen is two [FlashcardsListGroup]s of [FlashcardsListGroupItem.Row]s and nothing else. Sign out
 * is the one action, and it lives in the app bar rather than as a row at the bottom of a list of
 * preferences, where it would read as one more setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsScreenState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onDialogEvent: (SettingsDialogEvent) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    SettingsDialogHost(
        activeDialog = state.activeDialog,
        onDialogEvent = onDialogEvent,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                actions = {
                    SignOutAction(
                        isSigningOut = state.isSigningOut,
                        onClick = { onDialogEvent(Open(SignOut)) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.spacing.normal,
                    vertical = MaterialTheme.spacing.small,
                ),
        ) {
            FlashcardsOverlineLabel(text = stringResource(R.string.settings_study_sessions_label))
            FlashcardsListGroup(items = studySessionRows(state, onDialogEvent))

            FlashcardsOverlineLabel(
                text = stringResource(R.string.settings_voice_label),
                modifier = Modifier.padding(top = MaterialTheme.spacing.normal),
            )
            FlashcardsListGroup(items = voiceRows(state, onDialogEvent))
        }
    }
}

/**
 * Swaps the label for a spinner while sign-out is in flight rather than only disabling it: the
 * request can outlive a slow network round-trip, and a greyed-out label does not say why.
 */
@Composable
private fun SignOutAction(isSigningOut: Boolean, onClick: () -> Unit) {
    if (isSigningOut) {
        val description = stringResource(R.string.settings_signing_out_cd)
        CircularProgressIndicator(
            modifier = Modifier
                .padding(end = MaterialTheme.spacing.normal)
                .size(MaterialTheme.sizes.inlineProgress)
                .semantics { contentDescription = description },
            strokeWidth = MaterialTheme.sizes.inlineProgressStroke,
        )
    } else {
        FlashcardsTextButton(
            text = stringResource(R.string.settings_sign_out_button),
            onClick = onClick,
            contentColor = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun studySessionRows(
    state: SettingsScreenState,
    onDialogEvent: (SettingsDialogEvent) -> Unit,
): List<FlashcardsListGroupItem> = listOf(
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_daily_goal_label),
        onClick = { onDialogEvent(Open(DailyStudyGoal(draftState = state.dailyGoalMinutes))) },
        secondaryText = stringResource(R.string.settings_daily_goal_summary_label, state.dailyGoalMinutes),
        leading = { FlashcardsIconTile(icon = Icons.Default.EmojiEvents, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_session_length_label),
        onClick = { onDialogEvent(Open(SessionCardCount(draftState = state.sessionLength))) },
        secondaryText = pluralStringResource(
            CoreUiR.plurals.session_length_cards_label,
            state.sessionLength,
            state.sessionLength,
        ),
        leading = { FlashcardsIconTile(icon = Icons.Default.Numbers, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_rated_attempts_label),
        onClick = { onDialogEvent(Open(RatedSessionMaxCardAttempts(draftState = state.ratedAttempts))) },
        secondaryText = pluralStringResource(
            CoreUiR.plurals.rated_attempts_label,
            state.ratedAttempts,
            state.ratedAttempts,
        ),
        leading = { FlashcardsIconTile(icon = Icons.Default.Replay, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_partial_rating_card_requeueing_label),
        onClick = {
            onDialogEvent(Open(RatedSessionPartialRatingCardRequeueing(draftState = state.partialRatingCardRequeueingEnabled)))
        },
        secondaryText = partialRatingCardRequeueingLabel(state.partialRatingCardRequeueingEnabled),
        leading = { FlashcardsIconTile(icon = Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_study_mode_label),
        onClick = { onDialogEvent(Open(SessionMode(draftState = state.defaultStudyMode))) },
        secondaryText = state.defaultStudyMode.label(),
        leading = { FlashcardsIconTile(icon = Icons.Default.SwapHoriz, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_sort_order_label),
        onClick = { onDialogEvent(Open(SessionCardsSortingOrder(draftState = state.sortOrder))) },
        secondaryText = state.sortOrder.label(),
        leading = { FlashcardsIconTile(icon = Icons.Default.SortByAlpha, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_subcategory_count_range_label),
        onClick = { onDialogEvent(Open(QuickSessionSubcategoryCountRange(draftState = state.subcategoryCountRange))) },
        secondaryText = stringResource(
            CoreUiR.string.subcategory_count_range_value_label,
            state.subcategoryCountRange.first,
            state.subcategoryCountRange.last,
        ),
        leading = { FlashcardsIconTile(icon = Icons.Default.Layers, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
)

/**
 * Both toggles stay live whatever the default study mode is. They are *defaults*, and the mode a
 * session actually runs in is still chosen per session on the Preview screen — so neither is dead
 * just because the other mode is currently the default.
 */
@Composable
private fun voiceRows(
    state: SettingsScreenState,
    onDialogEvent: (SettingsDialogEvent) -> Unit,
): List<FlashcardsListGroupItem> = listOf(
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_voice_answering_label),
        onClick = { onDialogEvent(Open(RatedSessionVoiceAnswering(draftState = state.voiceAnsweringEnabled))) },
        secondaryText = voiceAnsweringLabel(state.voiceAnsweringEnabled),
        leading = { FlashcardsIconTile(icon = Icons.Default.GraphicEq, contentDescription = null) },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_read_aloud_label),
        onClick = { onDialogEvent(Open(FastSessionReadAloud(draftState = state.readAloudEnabled))) },
        secondaryText = readAloudLabel(state.readAloudEnabled),
        leading = {
            FlashcardsIconTile(icon = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
        },
        trailing = { FlashcardsChevron() },
    ),
    FlashcardsListGroupItem.Row(
        title = stringResource(R.string.settings_voice_playback_label),
        onClick = { onDialogEvent(Open(SessionVoiceSettings())) },
        secondaryText = voicePlaybackSummary(state),
        leading = {
            FlashcardsIconTile(icon = Icons.Default.RecordVoiceOver, contentDescription = null)
        },
        trailing = { FlashcardsChevron() },
    ),
)

/**
 * Rate and name, or the rate alone while the platform voice list has not arrived — an unresolved
 * id is not worth showing, and the rate is true either way.
 */
@Composable
private fun voicePlaybackSummary(state: SettingsScreenState): String {
    val rateLabel = speechRateLabel(state.speechRate)
    val voiceName = state.selectedVoice?.label() ?: return rateLabel
    return stringResource(R.string.settings_voice_playback_summary_label, rateLabel, voiceName)
}

@PreviewLightDark
@Composable
private fun SettingsContentPreview() {
    FlashcardsTheme {
        SettingsContent(
            state = SettingsScreenState(),
            onDialogEvent = {},
        )
    }
}
