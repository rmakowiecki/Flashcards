package com.rossomak.flashcards.core.data.mapper

import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.domain.model.ScoringState

fun ScoringStateDto.toDomain(): ScoringState = ScoringState(
    xp = xp,
    level = level,
    xpIntoCurrentLevel = xpIntoCurrentLevel,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    lastStudyDate = lastStudyDate,
    goalMetDate = goalMetDate,
    studiedSecondsOnLastStudyDate = studiedSecondsOnLastStudyDate,
)
