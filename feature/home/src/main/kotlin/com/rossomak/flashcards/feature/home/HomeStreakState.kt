package com.rossomak.flashcards.feature.home

/** [Empty]: the User never studied. [Failed] is never read as "none". */
sealed interface HomeStreakState {
    data object Loading : HomeStreakState
    data object Empty : HomeStreakState
    data object Failed : HomeStreakState

    /**
     * [studiedMinutes] and [goalPercent] read as shown; [isStudiedUnderAMinute] shows the minutes as under one;
     * [isGoalMet] means today's Daily Goal XP is earned.
     */
    data class Content(
        val days: Int,
        val bestDays: Int,
        val isAtRisk: Boolean,
        val studiedMinutes: Int,
        val goalMinutes: Int,
        val goalPercent: Int,
        val isGoalMet: Boolean,
        val isStudiedUnderAMinute: Boolean = false,
    ) : HomeStreakState
}
