package com.rossomak.flashcards.debugtools.showcase

import androidx.annotation.StringRes
import com.rossomak.flashcards.debugtools.R

/** List sections, in the order a User meets the screens. */
enum class ScreenShowcaseGroup(@param:StringRes val titleRes: Int) {
    Onboarding(R.string.screen_showcase_onboarding_title),
    Home(R.string.screen_showcase_home_title),
    Browse(R.string.screen_showcase_browse_title),
    Preview(R.string.screen_showcase_preview_title),
    Session(R.string.screen_showcase_session_title),
    Summary(R.string.screen_showcase_summary_title),
}

/** One production screen in one sample state. Its [name] is the route argument. */
enum class ScreenShowcaseEntry(val group: ScreenShowcaseGroup, @param:StringRes val titleRes: Int) {
    Onboarding(ScreenShowcaseGroup.Onboarding, R.string.screen_showcase_onboarding_label),
    Home(ScreenShowcaseGroup.Home, R.string.screen_showcase_home_label),
    Browse(ScreenShowcaseGroup.Browse, R.string.screen_showcase_browse_label),
    CategoryDetails(ScreenShowcaseGroup.Browse, R.string.screen_showcase_category_details_label),
    CategoryDetailsSelectionMode(ScreenShowcaseGroup.Browse, R.string.screen_showcase_category_details_selection_mode_label),
    SubcategoryDetails(ScreenShowcaseGroup.Browse, R.string.screen_showcase_subcategory_details_label),
    SubcategoryDetailsFilters(ScreenShowcaseGroup.Browse, R.string.screen_showcase_subcategory_details_filters_label),
    PreviewStudySession(ScreenShowcaseGroup.Preview, R.string.screen_showcase_preview_label),
    PreviewStudySessionSettings(ScreenShowcaseGroup.Preview, R.string.screen_showcase_preview_settings_label),
    RatedQuestion(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_question_label),
    RatedAnswerRevealed(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_answer_revealed_label),
    RatedVoiceAnswering(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_voice_answering_label),
    RatedVoiceGraded(ScreenShowcaseGroup.Session, R.string.screen_showcase_rated_voice_graded_label),
    FastTapThrough(ScreenShowcaseGroup.Session, R.string.screen_showcase_fast_tap_through_label),
    FastReadAloud(ScreenShowcaseGroup.Session, R.string.screen_showcase_fast_read_aloud_label),
    SessionSummary(ScreenShowcaseGroup.Summary, R.string.screen_showcase_summary_label),
    SessionSummaryXpBreakdown(ScreenShowcaseGroup.Summary, R.string.screen_showcase_summary_xp_breakdown_label),
    SessionSummaryConfetti(ScreenShowcaseGroup.Summary, R.string.screen_showcase_summary_confetti_label),
}
