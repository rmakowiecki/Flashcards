package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.SessionScoreCountsDto
import com.rossomak.flashcards.core.data.model.SessionScoreDto
import com.rossomak.flashcards.core.data.model.SessionScoreRatesDto
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionScoreRates
import com.rossomak.flashcards.core.domain.model.XpBreakdown

fun SessionScore.toDto(): SessionScoreDto = SessionScoreDto(
    newCards = breakdown.newCards,
    mastered = breakdown.mastered,
    partial = breakdown.partial,
    masteryDefenseBonus = breakdown.masteryDefenseBonus,
    demastered = breakdown.demastered,
    timeStudied = breakdown.timeStudied,
    sessionCompletionBonus = breakdown.sessionCompletionBonus,
    dailyGoalBonus = breakdown.dailyGoalBonus,
    streakBonus = breakdown.streakBonus,
    level = level,
    xpIntoCurrentLevel = xpIntoCurrentLevel,
    xpForNextLevel = xpForNextLevel,
    levelsCrossed = levelsCrossed,
    levelBefore = levelBefore,
    xpIntoCurrentLevelBefore = xpIntoCurrentLevelBefore,
    xpForNextLevelBefore = xpForNextLevelBefore,
    currentStreak = currentStreak,
    counts = SessionScoreCountsDto(
        newCardsStudied = counts.newCardsStudied,
        newlyMastered = counts.newlyMastered,
        partial = counts.partial,
        defended = counts.defended,
        demastered = counts.demastered,
    ),
    rates = SessionScoreRatesDto(
        newCardStudied = rates.newCardStudied,
        cardMastered = rates.cardMastered,
        cardPartial = rates.cardPartial,
        masteryDefended = rates.masteryDefended,
        cardDemastered = rates.cardDemastered,
        minuteStudied = rates.minuteStudied,
        sessionCompleted = rates.sessionCompleted,
    ),
)

fun SessionScoreDto.toDomain(): SessionScore = SessionScore(
    breakdown = XpBreakdown(
        newCards = newCards,
        mastered = mastered,
        partial = partial,
        masteryDefenseBonus = masteryDefenseBonus,
        demastered = demastered,
        timeStudied = timeStudied,
        sessionCompletionBonus = sessionCompletionBonus,
        dailyGoalBonus = dailyGoalBonus,
        streakBonus = streakBonus,
    ),
    level = level,
    xpIntoCurrentLevel = xpIntoCurrentLevel,
    xpForNextLevel = xpForNextLevel,
    levelsCrossed = levelsCrossed,
    levelBefore = levelBefore,
    xpIntoCurrentLevelBefore = xpIntoCurrentLevelBefore,
    xpForNextLevelBefore = xpForNextLevelBefore,
    currentStreak = currentStreak,
    counts = SessionScoreCounts(
        newCardsStudied = counts.newCardsStudied,
        newlyMastered = counts.newlyMastered,
        partial = counts.partial,
        defended = counts.defended,
        demastered = counts.demastered,
    ),
    rates = SessionScoreRates(
        newCardStudied = rates.newCardStudied,
        cardMastered = rates.cardMastered,
        cardPartial = rates.cardPartial,
        masteryDefended = rates.masteryDefended,
        cardDemastered = rates.cardDemastered,
        minuteStudied = rates.minuteStudied,
        sessionCompleted = rates.sessionCompleted,
    ),
)
