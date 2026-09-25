package com.rossomak.flashcards.feature.study.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.ui.R as CoreUiR
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheet
import com.rossomak.flashcards.core.ui.composables.FlashcardsBottomSheetState
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyRangePill
import com.rossomak.flashcards.core.ui.composables.dialogs.FlashcardFilters
import com.rossomak.flashcards.core.ui.composables.dialogs.label
import com.rossomak.flashcards.core.ui.composables.dialogs.partialRatingCardRequeueingLabel
import com.rossomak.flashcards.core.ui.composables.dialogs.readAloudLabel
import com.rossomak.flashcards.core.ui.composables.dialogs.speechRateLabel
import com.rossomak.flashcards.core.ui.composables.dialogs.voiceAnsweringLabel
import com.rossomak.flashcards.core.ui.composables.lists.FlashcardsSettingRow
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.FastSessionReadAloud
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.Filters
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.QuickSessionSubcategoryCountRange
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionMaxCardAttempts
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionPartialRatingCardRequeueing
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.RatedSessionVoiceAnswering
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardCount
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionCardsSortingOrder
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionMode
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.SessionVoiceSettings

/**
 * The settings sheet itself: [FlashcardsBottomSheet] docked over the ready screen, hidden until
 * the top bar's settings button opens it. [sheetState] is owned by the caller — this
 * composable only renders what's inside.
 *
 * Every row renders at once — no manual pagination or lazy list here — and
 * [FlashcardsBottomSheet]'s own default `maxHeightFraction` caps and internally scrolls the whole
 * thing once a Rated quick session's full row set (mode, voice answering, attempts, voice settings,
 * length, subcategories, filters, sort — up to eight rows) runs taller than that cap, e.g. in landscape or
 * at a large font scale. See [FlashcardsBottomSheet]'s own doc for how that cap and scroll actually
 * work, and why [FlashcardsBottomSheetState] only ever enables hidden/expanded, never a partial peek.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionSettingsSheet(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    sheetState: FlashcardsBottomSheetState,
    onDismissRequest: () -> Unit,
    onDialogEvent: (PreviewDialogEvent) -> Unit,
) {
    FlashcardsBottomSheet(
        state = sheetState,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        SessionSettingRows(state = state, onDialogEvent = onDialogEvent)
    }
}

/**
 * One row per adjustable setting, each opening its own dialog (ADR-0030), through the shared
 * [FlashcardsSettingRow].
 *
 * Voice answering, attempts and requeueing Partials are Rated-only: Fast mode has no rating step for any
 * of them to drive (ADR-0025). Read-aloud is the Fast-only counterpart — voice *output* plus
 * hands-free advance, where voice answering is voice *input*. Each is not offered outside its mode
 * rather than being offered and ignored, so the two branches are exclusive. The Voice row itself
 * only joins them when something will actually speak: Fast with read-aloud on, or voice answering
 * on — Fast alone has nothing to configure yet, since read-aloud might still be off.
 */
@Composable
private fun SessionSettingRows(
    modifier: Modifier = Modifier,
    state: PreviewStudySessionScreenState,
    onDialogEvent: (PreviewDialogEvent) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        FlashcardsSettingRow(
            label = stringResource(R.string.preview_session_mode_label),
            valueText = state.config.mode.label(),
            onClick = { onDialogEvent(Open(SessionMode(draftState = state.config.mode))) },
        )
        if (state.config.mode == StudyMode.Rated) {
            FlashcardsSettingRow(
                label = stringResource(R.string.preview_session_voice_answering_label),
                valueText = voiceAnsweringLabel(state.config.voiceAnsweringEnabled),
                onClick = {
                    onDialogEvent(
                        Open(RatedSessionVoiceAnswering(draftState = state.config.voiceAnsweringEnabled))
                    )
                },
            )
            FlashcardsSettingRow(
                label = stringResource(R.string.preview_session_attempts_label),
                valueText = pluralStringResource(
                    CoreUiR.plurals.rated_attempts_label,
                    state.config.ratedAttempts,
                    state.config.ratedAttempts,
                ),
                onClick = { onDialogEvent(Open(RatedSessionMaxCardAttempts(draftState = state.config.ratedAttempts))) },
            )
            FlashcardsSettingRow(
                label = stringResource(R.string.preview_session_partial_rating_card_requeueing_label),
                valueText = partialRatingCardRequeueingLabel(state.config.partialRatingCardRequeueingEnabled),
                onClick = {
                    onDialogEvent(
                        Open(RatedSessionPartialRatingCardRequeueing(draftState = state.config.partialRatingCardRequeueingEnabled))
                    )
                },
            )
        } else {
            FlashcardsSettingRow(
                label = stringResource(R.string.preview_session_read_aloud_label),
                valueText = readAloudLabel(state.config.readAloudEnabled),
                onClick = { onDialogEvent(Open(FastSessionReadAloud(draftState = state.config.readAloudEnabled))) },
            )
        }
        val fastModeSpeaksAloud = state.config.mode == StudyMode.Fast && state.config.readAloudEnabled
        if (fastModeSpeaksAloud || state.config.voiceAnsweringEnabled) {
            FlashcardsSettingRow(
                label = stringResource(R.string.preview_session_voice_settings_label),
                valueText = voicePlaybackSummary(state),
                onClick = { onDialogEvent(Open(SessionVoiceSettings())) },
            )
        }
        FlashcardsSettingRow(
            label = stringResource(R.string.preview_session_length_label),
            valueText = pluralStringResource(
                CoreUiR.plurals.session_length_cards_label,
                state.config.length,
                state.config.length,
            ),
            onClick = { onDialogEvent(Open(SessionCardCount(draftState = state.config.length))) },
        )
        if (state.isQuickSession) {
            SubcategoryCountRangeSettingRow(state = state, onDialogEvent = onDialogEvent)
        }
        FiltersSettingRow(state = state, onDialogEvent = onDialogEvent)
        FlashcardsSettingRow(
            label = stringResource(R.string.preview_session_sort_label),
            valueText = state.config.sortOrder.label(),
            onClick = { onDialogEvent(Open(SessionCardsSortingOrder(draftState = state.config.sortOrder))) },
        )
    }
}

/**
 * Lifted out of [SessionSettingRows] purely to keep that function under detekt's `LongMethod` — a
 * single-Subcategory or Custom session has nothing to sample and never shows this row (ADR-0040).
 */
@Composable
private fun SubcategoryCountRangeSettingRow(
    state: PreviewStudySessionScreenState,
    onDialogEvent: (PreviewDialogEvent) -> Unit,
) {
    FlashcardsSettingRow(
        label = stringResource(R.string.preview_session_subcategory_count_range_label),
        valueText = stringResource(
            CoreUiR.string.subcategory_count_range_value_label,
            state.config.subcategoryCountRange.first,
            state.config.subcategoryCountRange.last,
        ),
        onClick = { onDialogEvent(Open(QuickSessionSubcategoryCountRange(draftState = state.config.subcategoryCountRange))) },
    )
}

/** Lifted out of [SessionSettingRows] purely to keep that function under detekt's `LongMethod`. */
@Composable
private fun FiltersSettingRow(
    state: PreviewStudySessionScreenState,
    onDialogEvent: (PreviewDialogEvent) -> Unit,
) {
    FlashcardsSettingRow(
        label = stringResource(R.string.preview_session_filters_label),
        value = { FiltersSettingValue(state = state) },
        onClick = {
            onDialogEvent(
                Open(
                    Filters(
                        draftState = FlashcardFilters(
                            selectedTags = state.config.tagIds,
                            difficultyRange = state.config.difficultyRange,
                        ),
                        availableTags = state.availableTags,
                    )
                )
            )
        },
    )
}

/** Name and rate, or the rate alone when no voice label is stored. */
@Composable
private fun voicePlaybackSummary(state: PreviewStudySessionScreenState): String {
    val rateLabel = speechRateLabel(state.config.voiceSettings.speechRate)
    val voiceName = state.config.voiceSettings.voiceLabel?.label() ?: return rateLabel
    return stringResource(R.string.preview_session_setting_value_separator_label, voiceName, rateLabel)
}

/**
 * The Filters row's value slot: a tag summary beside [FlashcardsDifficultyRangePill], rather than
 * the plain text every other row uses. Difficulty always renders; the tag summary only
 * joins it when [PreviewStudySessionScreenState.availableTags] is non-empty — multi-subcategory
 * sessions filter by difficulty only (ADR-0030) and offer no tag vocabulary to summarize.
 *
 * `config.tagIds` is materialized to every available tag by default (mirroring SubcategoryDetails,
 * ADR-0038): a *proper* subset of [PreviewStudySessionScreenState.availableTags] is a narrowed
 * selection and gets a count; matching it exactly means every tag is enabled, which reads as "All
 * tags" rather than nothing — a filter row silently going blank once nothing was excluded looked
 * like the row itself had disappeared.
 */
@Composable
private fun FiltersSettingValue(state: PreviewStudySessionScreenState) {
    val config = state.config
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        if (state.availableTags.isNotEmpty()) {
            Text(
                text = if (config.tagIds == state.availableTags.toSet()) {
                    stringResource(R.string.preview_session_filters_all_tags_value_label)
                } else {
                    pluralStringResource(
                        R.plurals.preview_session_filters_tags_value_label,
                        config.tagIds.size,
                        config.tagIds.size,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlashcardsDifficultyRangePill(
            lowLevel = config.difficultyRange.first,
            highLevel = config.difficultyRange.last,
        )
    }
}
