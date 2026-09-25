package com.rossomak.flashcards.feature.settings

import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.FlashcardSortOrder
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.VoiceLabel

/**
 * Everything the Settings rows render.
 *
 * [dailyGoalMinutes] and the study-session rows below it are backed by
 * `UserPreferencesRepository`/`StudySessionPreferencesRepository`: the ViewModel collects both
 * continuously and folds every emission straight in, so a row can never disagree with disk. The
 * non-null constant defaults here are only the pre-emission placeholder — DataStore already has
 * the real value in memory by the time this screen is reached (`SplashViewModel` reads the same
 * store on every cold start).
 *
 * [speechRate], [voiceId] and [voiceLabel] mirror `StudySessionPreferences.voiceSettings`.
 */
data class SettingsScreenState(
    val dailyGoalMinutes: Int = DailyGoal.DEFAULT_MINUTES,
    val sessionLength: Int = StudySessionConfig.DEFAULT_LENGTH,
    val ratedAttempts: Int = StudySessionConfig.DEFAULT_RATED_ATTEMPTS,
    val partialRatingCardRequeueingEnabled: Boolean = true,
    val defaultStudyMode: StudyMode = StudyMode.Rated,
    val sortOrder: FlashcardSortOrder = FlashcardSortOrder.Default,
    val subcategoryCountRange: IntRange = StudySessionConfig.DEFAULT_SUBCATEGORY_COUNT_RANGE,
    val voiceAnsweringEnabled: Boolean = false,
    val readAloudEnabled: Boolean = false,
    val speechRate: Float = 1f,
    val voiceId: String? = null,
    /** Null when no voice is saved or its label is still being resolved. */
    val voiceLabel: VoiceLabel? = null,
    val isSigningOut: Boolean = false,
    val activeDialog: SettingsDialog? = null,
)
