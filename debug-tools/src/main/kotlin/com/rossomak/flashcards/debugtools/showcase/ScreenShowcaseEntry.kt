package com.rossomak.flashcards.debugtools.showcase

import androidx.annotation.StringRes
import com.rossomak.flashcards.debugtools.R

/** The sections of the showcase list, in the order a User meets the screens. */
enum class ScreenShowcaseGroup(@param:StringRes val titleRes: Int) {
    Onboarding(R.string.screen_showcase_onboarding_title),
    Home(R.string.screen_showcase_home_title),
    Browse(R.string.screen_showcase_browse_title),
    Session(R.string.screen_showcase_session_title),
}

/**
 * One row of the showcase list: one production screen in one hand-built state. Its [name] is the route
 * argument, so renaming an entry only changes which row a restored back stack reopens.
 */
enum class ScreenShowcaseEntry(val group: ScreenShowcaseGroup, @param:StringRes val titleRes: Int) {
    Onboarding(ScreenShowcaseGroup.Onboarding, R.string.screen_showcase_onboarding_label),
    Home(ScreenShowcaseGroup.Home, R.string.screen_showcase_home_label),
    Browse(ScreenShowcaseGroup.Browse, R.string.screen_showcase_browse_label),
    CategoryDetails(ScreenShowcaseGroup.Browse, R.string.screen_showcase_category_details_label),
    CategoryDetailsSelectionMode(ScreenShowcaseGroup.Browse, R.string.screen_showcase_category_details_selection_mode_label),
    SubcategoryDetails(ScreenShowcaseGroup.Browse, R.string.screen_showcase_subcategory_details_label),
    PreviewStudySession(ScreenShowcaseGroup.Session, R.string.screen_showcase_preview_label),
    RatedQuestion(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_question_label),
    RatedAnswerRevealed(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_answer_revealed_label),
    RatedVoiceAnswering(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_voice_answering_label),
    FastTapThrough(ScreenShowcaseGroup.Session, R.string.screen_showcase_fast_tap_through_label),
    FastReadAloud(ScreenShowcaseGroup.Session, R.string.screen_showcase_fast_read_aloud_label),
    SessionSummary(ScreenShowcaseGroup.Session, R.string.screen_showcase_summary_label),
}
