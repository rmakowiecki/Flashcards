package com.rossomak.flashcards.core.domain.model

/**
 * What the server-authoritative `submitStudySession` Cloud Function computed for one delivered
 * session: the XP it awarded, line by line, and the account's Level position right after applying it.
 * Unlike [SessionXpResult], the client's own local preview, this is the award actually recorded, so the
 * Session Summary shows it as-is whenever it arrives in time.
 *
 * @param breakdown every award line, [XpBreakdown.dailyGoalBonus] and [XpBreakdown.streakBonus] included.
 * @param level the account's Level after this session.
 * @param xpIntoCurrentLevel points into [level] after this session.
 * @param xpForNextLevel points [level] needs in total before the next Level.
 * @param levelsCrossed every Level reached by this session, ascending; empty when none was.
 * @param counts how many cards or events each multiplied line counts; `null` only when the function
 * answered without them.
 * @param rates the per-line rates the function scored with; `null` when it answered from a session
 * recorded before it stored them.
 */
data class SessionScore(
    val breakdown: XpBreakdown,
    val level: Int,
    val xpIntoCurrentLevel: Long,
    val xpForNextLevel: Long,
    val levelsCrossed: List<Int>,
    val counts: SessionScoreCounts?,
    val rates: SessionScoreRates?,
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
data class SessionScoreRates(
    val newCardStudied: Int,
    val cardMastered: Int,
    val cardPartial: Int,
    val masteryDefended: Int,
    val cardDemastered: Int,
    val minuteStudied: Int,
    val sessionCompleted: Int,
)
