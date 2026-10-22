package com.rossomak.flashcards.feature.home

/** [Empty]: the User never studied. [Failed] is never read as "none". */
sealed interface HomeStreakState {
    data object Loading : HomeStreakState
    data object Empty : HomeStreakState
    data object Failed : HomeStreakState

    data class Content(val days: Int, val bestDays: Int, val isAtRisk: Boolean, val studiedMinutes: Int, val goalMinutes: Int) : HomeStreakState {
        val progress: Float
            get() = if (goalMinutes > 0) (studiedMinutes.toFloat() / goalMinutes).coerceAtMost(1f) else 0f
    }
}
