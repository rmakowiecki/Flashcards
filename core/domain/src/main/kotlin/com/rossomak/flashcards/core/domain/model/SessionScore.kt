package com.rossomak.flashcards.core.domain.model

import kotlinx.serialization.Serializable

/**
 * One session's XP award, line by line, and the account's Level position right before and right after
 * applying it.
 * Comes from the server-authoritative `submitStudySession` Cloud Function for a delivered session, or
 * from the client's own [com.rossomak.flashcards.core.domain.scoring.scoreSession] for the Session
 * Summary's fallback preview; both use the same rules, so the Summary renders either the same way.
 *
 * @param breakdown every award line, [XpBreakdown.dailyGoalBonus] and [XpBreakdown.streakBonus] included.
 * @param level the account's Level after this session.
 * @param xpIntoCurrentLevel points into [level] after this session.
 * @param xpForNextLevel points [level] needs in total before the next Level.
 * @param levelsCrossed every Level reached by this session, ascending; empty when none was.
 * @param levelBefore the account's Level right before this session.
 * @param xpIntoCurrentLevelBefore points into [levelBefore] right before this session.
 * @param xpForNextLevelBefore points [levelBefore] needs in total before the next Level.
 * @param currentStreak the account's Streak right after this session.
 * @param counts how many cards or events each multiplied line counts.
 * @param rates the per-line rates the session was scored with.
 */
@Serializable
data class SessionScore(
    val breakdown: XpBreakdown,
    val level: Int,
    val xpIntoCurrentLevel: Long,
    val xpForNextLevel: Long,
    val levelsCrossed: List<Int>,
    val levelBefore: Int,
    val xpIntoCurrentLevelBefore: Long,
    val xpForNextLevelBefore: Long,
    val currentStreak: Int,
    val counts: SessionScoreCounts,
    val rates: SessionScoreRates,
)

/**
 * The count behind each multiplied [XpBreakdown] line: `line = count × rate`. [newCardsStudied]
 * applies to both Study Modes. The other four are Rated only and `null` for a Fast session, which has
 * no Terminal States to count.
 *
 * @param newlyMastered cards ending Mastered that were not Mastered before, behind [XpBreakdown.mastered].
 * @param partial cards ending Partial, behind [XpBreakdown.partial].
 * @param defended cards ending Mastered that were already Mastered, behind [XpBreakdown.masteryDefenseBonus].
 * @param demastered cards ending Failed that were Mastered before, behind [XpBreakdown.demastered].
 */
@Serializable
data class SessionScoreCounts(
    val newCardsStudied: Int,
    val newlyMastered: Int?,
    val partial: Int?,
    val defended: Int?,
    val demastered: Int?,
)

/**
 * The [XpConfig] rates behind each multiplied [XpBreakdown] line, as the server scored the session.
 * The Streak and Daily Goal awards are not multiplied per item, so they have no rate here.
 */
@Serializable
data class SessionScoreRates(
    val newCardStudied: Int,
    val cardMastered: Int,
    val cardPartial: Int,
    val masteryDefended: Int,
    val cardDemastered: Int,
    val minuteStudied: Int,
    val sessionCompleted: Int,
)
