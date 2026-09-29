package com.rossomak.flashcards.core.domain.model

/**
 * One session's itemised XP award list — one field per source, matching the
 * `sessions/{sessionId}` document's own persisted field names 1:1, plus [xpTotal] as their sum, never
 * stored separately so it cannot disagree with them.
 *
 * Every field defaults to zero rather than being absent for a Study Mode that structurally cannot
 * earn it. This differs from [FlashcardResult]'s mastery fields, genuinely undefined for a
 * [SessionResult.Fast] card: an award this session simply did not produce is honestly zero, whichever
 * mode ran, so a Fast session's document carries `mastered: 0` rather than omitting the field.
 *
 * @param newCards [XpConfig.newCardStudied] × cards studied for the first time ever. Both modes.
 * @param mastered [XpConfig.cardMastered] × cards ending Mastered this session for the first time —
 * excludes a defended card, which earns [masteryDefenseBonus] instead, not in addition. Rated only.
 * @param partial [XpConfig.cardPartial] × cards ending Partial this session, regardless of prior
 * mastery — a defended-turned-Partial card is mastery-neutral, not penalised, but still earns this.
 * Rated only.
 * @param masteryDefenseBonus [XpConfig.masteryDefended] × cards ending Mastered that were already
 * Mastered — replaces [mastered] for that card, a smaller reward for defending rather than a bonus
 * stacked on top of a fresh mastery. Rated only; structurally zero until Mastery Defense can
 * ever re-select an already-mastered card.
 * @param demastered [XpConfig.cardDemastered] (negative) × cards ending Failed that were previously
 * Mastered. Rated only; same dependency as [masteryDefenseBonus].
 * @param timeStudied [XpConfig.minuteStudied] × whole minutes studied. Both modes.
 * @param sessionCompletionBonus [XpConfig.sessionCompleted], flat, only for a session that finished
 * its deck rather than being abandoned. Both modes.
 * @param dailyGoalBonus [XpConfig.dailyGoalMet] flat, at most once per calendar day. Both modes.
 * @param streakBonus `currentStreak x XpConfig.streakPerDay`, capped at [XpConfig.streakMaxPerDay].
 * Both modes.
 */
data class XpBreakdown(
    val newCards: Int = 0,
    val mastered: Int = 0,
    val partial: Int = 0,
    val masteryDefenseBonus: Int = 0,
    val demastered: Int = 0,
    val timeStudied: Int = 0,
    val sessionCompletionBonus: Int = 0,
    val dailyGoalBonus: Int = 0,
    val streakBonus: Int = 0,
) {
    val xpTotal: Int
        get() = newCards + mastered + partial + masteryDefenseBonus + demastered +
            timeStudied + sessionCompletionBonus + dailyGoalBonus + streakBonus
}
