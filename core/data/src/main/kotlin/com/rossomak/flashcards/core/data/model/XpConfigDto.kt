package com.rossomak.flashcards.core.data.model

/**
 * The `config/xp` document's fields exactly as Firestore returned them: a whole number arrives as a
 * [Long], a fractional one as a [Double], and a missing or non-numeric field as `null`. The step list
 * arrives as a list whose non-numeric entries are `null`, and a missing or non-list field as `null`.
 * Validated only when mapped to the domain.
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
    val levelBaseCost: Number?,
    val levelCostSteps: List<Number?>?,
    val levelsPerCostStep: Number?,
)
