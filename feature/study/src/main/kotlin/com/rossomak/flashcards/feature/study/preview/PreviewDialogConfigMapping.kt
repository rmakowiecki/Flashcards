package com.rossomak.flashcards.feature.study.preview

import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.StudySessionPreference
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.DefaultStudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.PartialRatingCardRequeueingEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.RatedAttempts
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.ReadAloudEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SessionLength
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SortOrder
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.SubcategoryCountRange as SubcategoryCountRangePreference
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoiceAnsweringEnabled
import com.rossomak.flashcards.core.domain.model.StudySessionPreference.VoicePlayback
import com.rossomak.flashcards.core.ui.voice.toVoiceSettings
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
import com.rossomak.flashcards.feature.study.preview.PreviewDialog.VoiceAnsweringInfo

/**
 * Pure [StudySessionConfig]/[PreviewDialog] mapping helpers pulled out of
 * [PreviewStudySessionViewModel] — they touch no view-model state, so keeping them here instead
 * of as private class members keeps that class under detekt's `TooManyFunctions` threshold.
 */

/**
 * Lifted out of `onDialogConfirm` purely to keep that function under detekt's
 * `CyclomaticComplexMethod` threshold — every branch is still a total `copy()` of the config
 * with the confirmed dialog's draftState folded in.
 */
internal fun StudySessionConfig.foldInDialog(dialog: PreviewDialog): StudySessionConfig = when (dialog) {
    is SessionMode -> withMode(dialog.draftState)
    is RatedSessionVoiceAnswering -> copy(voiceAnsweringEnabled = dialog.draftState)
    is RatedSessionMaxCardAttempts -> copy(ratedAttempts = dialog.draftState)
    is FastSessionReadAloud -> copy(readAloudEnabled = dialog.draftState)
    is RatedSessionPartialRatingCardRequeueing -> copy(partialRatingCardRequeueingEnabled = dialog.draftState)
    is SessionCardCount -> copy(length = dialog.draftState)
    is SessionCardsSortingOrder -> copy(sortOrder = dialog.draftState)
    is QuickSessionSubcategoryCountRange -> copy(subcategoryCountRange = dialog.draftState)
    is SessionVoiceSettings -> copy(voiceSettings = dialog.draftState.toVoiceSettings())
    is Filters -> copy(
        tagIds = dialog.draftState.selectedTags,
        difficultyRange = dialog.draftState.difficultyRange,
    )
    VoiceAnsweringInfo -> this
}

/**
 * `voiceAnsweringEnabled` is Rated-only (ADR-0025) — reset it switching away from Rated, not
 * just gate its *display* at the read sites, since the stale value would otherwise also leak
 * into `onStartSession`'s `RatedStudySessionRoute` payload unchanged. Its own function purely
 * to keep `onDialogConfirm`'s cyclomatic complexity under detekt's threshold.
 */
internal fun StudySessionConfig.withMode(mode: StudyMode): StudySessionConfig = copy(
    mode = mode,
    voiceAnsweringEnabled = voiceAnsweringEnabled && mode == StudyMode.Rated,
)

/**
 * `null` when the dialog didn't check "keep as my default" — or, for [Filters], can never
 * check it at all: tags belong to one subcategory and cannot carry to another (ADR-0030).
 */
internal fun PreviewDialog.toStudySessionPreferenceIfKept(): StudySessionPreference? = when (this) {
    is SessionMode -> DefaultStudyMode(draftState).takeIf { keepAsDefault }
    is RatedSessionVoiceAnswering -> VoiceAnsweringEnabled(draftState).takeIf { keepAsDefault }
    is RatedSessionMaxCardAttempts -> RatedAttempts(draftState).takeIf { keepAsDefault }
    is FastSessionReadAloud -> ReadAloudEnabled(draftState).takeIf { keepAsDefault }
    is RatedSessionPartialRatingCardRequeueing -> PartialRatingCardRequeueingEnabled(draftState).takeIf { keepAsDefault }
    is SessionCardCount -> SessionLength(draftState).takeIf { keepAsDefault }
    is SessionCardsSortingOrder -> SortOrder(draftState).takeIf { keepAsDefault }
    is QuickSessionSubcategoryCountRange -> SubcategoryCountRangePreference(draftState).takeIf { keepAsDefault }
    is SessionVoiceSettings -> VoicePlayback(draftState.toVoiceSettings()).takeIf { keepAsDefault }
    is Filters, VoiceAnsweringInfo -> null
}

/**
 * Tags belong to one subcategory, so a multi-Subcategory pool has no coherent tag vocabulary
 * to filter by at all — asserted here rather than relied on staying empty by omission
 * elsewhere (ADR-0030).
 */
internal fun StudySessionConfig.forSelection(isSingleSubcategory: Boolean): StudySessionConfig =
    if (isSingleSubcategory) this else copy(tagIds = emptySet())
