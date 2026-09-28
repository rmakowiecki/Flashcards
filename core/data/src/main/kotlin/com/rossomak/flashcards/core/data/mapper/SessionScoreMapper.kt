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
    counts = counts?.let { sessionCounts ->
        SessionScoreCountsDto(
            newCardsStudied = sessionCounts.newCardsStudied,
            newlyMastered = sessionCounts.newlyMastered,
            partial = sessionCounts.partial,
            defended = sessionCounts.defended,
            demastered = sessionCounts.demastered,
        )
    },
    rates = rates?.let { sessionRates ->
        SessionScoreRatesDto(
            newCardStudied = sessionRates.newCardStudied,
            cardMastered = sessionRates.cardMastered,
            cardPartial = sessionRates.cardPartial,
            masteryDefended = sessionRates.masteryDefended,
            cardDemastered = sessionRates.cardDemastered,
            minuteStudied = sessionRates.minuteStudied,
            sessionCompleted = sessionRates.sessionCompleted,
        )
    },
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
    counts = counts?.let { countsDto ->
        SessionScoreCounts(
            newCardsStudied = countsDto.newCardsStudied,
            newlyMastered = countsDto.newlyMastered,
            partial = countsDto.partial,
            defended = countsDto.defended,
            demastered = countsDto.demastered,
        )
    },
    rates = rates?.let { ratesDto ->
        SessionScoreRates(
            newCardStudied = ratesDto.newCardStudied,
            cardMastered = ratesDto.cardMastered,
            cardPartial = ratesDto.cardPartial,
            masteryDefended = ratesDto.masteryDefended,
            cardDemastered = ratesDto.cardDemastered,
            minuteStudied = ratesDto.minuteStudied,
            sessionCompleted = ratesDto.sessionCompleted,
        )
    },
)
