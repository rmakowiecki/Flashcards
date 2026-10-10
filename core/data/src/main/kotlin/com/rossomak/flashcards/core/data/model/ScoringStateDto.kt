package com.rossomak.flashcards.core.data.model

import com.rossomak.flashcards.core.domain.model.ScoringState

/**
 * `progress/user-stats` as read. [appliedSessionIds] are the latest sessions the server applied to it,
 * oldest first; it stays out of the domain [ScoringState] and tells the Pending Session projection which
 * queued sessions the document already includes.
 */
data class ScoringStateDto(
    val xp: Long = 0,
    val level: Int = ScoringState.STARTING_LEVEL,
    val xpIntoCurrentLevel: Long = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastStudyDate: String = "",
    val goalMetDate: String = "",
    val studiedSecondsOnLastStudyDate: Long = 0,
    val appliedSessionIds: List<String> = emptyList(),
)
