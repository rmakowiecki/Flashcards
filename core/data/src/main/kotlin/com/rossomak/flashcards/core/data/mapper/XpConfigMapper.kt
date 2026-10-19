package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.XpConfigDto
import com.rossomak.flashcards.core.domain.model.XpConfig

/**
 * Validates with the same rules `submitStudySession` applies to the same document: every field
 * present and finite, every award a whole number, the de-mastery penalty at most zero, the level
 * curve's base cost and tier length positive whole numbers, and its step list nonempty with every step
 * a positive whole number (a zero or negative step makes a level no more expensive than the one before
 * it, and a tier length of zero divides by zero). Every value is an `Int`, which keeps every threshold
 * within a `Long` for any level a User could reach.
 *
 * @throws IllegalArgumentException naming the first invalid field.
 */
fun XpConfigDto.toDomain(): XpConfig {
    val config = XpConfig(
        newCardStudied = newCardStudied.requireAward("newCardStudied"),
        cardMastered = cardMastered.requireAward("cardMastered"),
        cardPartial = cardPartial.requireAward("cardPartial"),
        masteryDefended = masteryDefended.requireAward("masteryDefended"),
        cardDemastered = cardDemastered.requireAward("cardDemastered"),
        sessionCompleted = sessionCompleted.requireAward("sessionCompleted"),
        dailyGoalMet = dailyGoalMet.requireAward("dailyGoalMet"),
        streakPerDay = streakPerDay.requireAward("streakPerDay"),
        streakMaxPerDay = streakMaxPerDay.requireAward("streakMaxPerDay"),
        levelBaseCost = levelBaseCost.requireAward("levelBaseCost"),
        levelCostSteps = requireSteps(levelCostSteps),
        levelsPerCostStep = levelsPerCostStep.requireAward("levelsPerCostStep"),
    )
    require(config.cardDemastered <= 0) { "cardDemastered must be zero or negative, got ${config.cardDemastered}" }
    require(config.levelBaseCost > 0) { "levelBaseCost must be positive, got ${config.levelBaseCost}" }
    require(config.levelsPerCostStep > 0) { "levelsPerCostStep must be positive, got ${config.levelsPerCostStep}" }
    return config
}

private fun requireSteps(steps: List<Number?>?): List<Int> {
    require(!steps.isNullOrEmpty()) { "levelCostSteps must be a nonempty list, got $steps" }
    return steps.map { step ->
        val value = step.requireAward("levelCostSteps")
        require(value > 0) { "levelCostSteps must hold positive integers, got $value" }
        value
    }
}

private fun Number?.requireFinite(field: String): Double {
    val value = this?.toDouble()
    require(value != null && value.isFinite()) { "$field must be a finite number, got $this" }
    return value
}

private fun Number?.requireAward(field: String): Int {
    val value = requireFinite(field)
    require(value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
        "$field must be an integer, got $this"
    }
    return value.toInt()
}
