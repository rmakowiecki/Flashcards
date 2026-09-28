package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.XpConfigDto
import com.rossomak.flashcards.core.domain.model.XpConfig

/**
 * Validates with the same rules `submitStudySession` applies to the same document: every field
 * present and finite, every award a whole number, the de-mastery penalty at most zero, the level
 * curve's base positive (a base of zero or less makes every level threshold zero), and its exponent
 * zero or positive (a negative exponent makes each level cheaper than the one before it).
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
        minuteStudied = minuteStudied.requireAward("minuteStudied"),
        levelCurveBase = levelCurveBase.requireFinite("levelCurveBase"),
        levelCurveExponent = levelCurveExponent.requireFinite("levelCurveExponent"),
    )
    require(config.cardDemastered <= 0) { "cardDemastered must be zero or negative, got ${config.cardDemastered}" }
    require(config.levelCurveBase > 0) { "levelCurveBase must be positive, got ${config.levelCurveBase}" }
    require(config.levelCurveExponent >= 0) { "levelCurveExponent must be zero or positive, got ${config.levelCurveExponent}" }
    return config
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
