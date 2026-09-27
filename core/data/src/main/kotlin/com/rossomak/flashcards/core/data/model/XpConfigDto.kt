package com.rossomak.flashcards.core.data.model

/**
 * The `config/xp` document's fields exactly as Firestore returned them: a whole number arrives as a
 * [Long], a fractional one as a [Double], and a missing or non-numeric field as `null`. Validated
 * only when mapped to the domain.
 */
data class XpConfigDto(
    val newCardStudied: Number?,
    val cardMastered: Number?,
    val cardPartial: Number?,
    val masteryDefended: Number?,
    val cardDemastered: Number?,
    val sessionCompleted: Number?,
    val dailyGoalMet: Number?,
    val streakPerDay: Number?,
    val streakMaxPerDay: Number?,
    val minuteStudied: Number?,
    val levelCurveBase: Number?,
    val levelCurveExponent: Number?,
)
