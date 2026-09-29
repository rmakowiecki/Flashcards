package com.rossomak.flashcards.core.data.model

import com.rossomak.flashcards.core.domain.model.ScoringState

data class ScoringStateDto(
    val xp: Long = 0,
    val level: Int = ScoringState.STARTING_LEVEL,
    val xpIntoCurrentLevel: Long = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastStudyDate: String = "",
    val goalMetDate: String = "",
    val studiedSecondsOnLastStudyDate: Long = 0,
)
