package com.rossomak.flashcards.feature.onboarding

import com.rossomak.flashcards.core.domain.model.DailyGoal
import com.rossomak.flashcards.core.domain.model.StudyMode
import com.rossomak.flashcards.core.domain.model.VoiceDemoState
import com.rossomak.flashcards.feature.onboarding.model.FavoriteSubcategoryOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Every decision the user makes across the flow, held until the final step commits them in one go.
 * The defaults here are the same values the flow writes when the user skips, so a skipped run and
 * an untouched full run produce identical state — which is why the recap on the final step needs
 * no "was this skipped?" branch.
 */
data class OnboardingScreenState(
    val userName: String? = null,
    val defaultStudyMode: StudyMode = StudyMode.Rated,
    val dailyGoalMinutes: Int = DailyGoal.DEFAULT_MINUTES,
    val favoriteSubcategoryOptions: ImmutableList<FavoriteSubcategoryOption> = persistentListOf(),
    val isFavoriteSubcategoriesLoading: Boolean = false,
    val favoriteSubcategoriesLoaded: Boolean = false,
    val favoriteSubcategoriesLoadingFailed: Boolean = false,
    val selectedFavoriteSubcategoriesIds: ImmutableSet<String> = persistentSetOf(),
    val isCommitting: Boolean = false,
    val voiceDemoState: VoiceDemoState = VoiceDemoState.Idle,
) {
    val canDecrementDailyGoal: Boolean
        get() = dailyGoalMinutes > DailyGoal.MIN_MINUTES

    val canIncrementDailyGoal: Boolean
        get() = dailyGoalMinutes < DailyGoal.MAX_MINUTES
}
