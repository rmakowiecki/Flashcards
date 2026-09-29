package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold

/**
 * What one session earned, and the account's [newScoringState] after applying it.
 *
 * @property levelsCrossed every Level reached along the way, ascending; empty when none was.
 */
data class SessionXpCalculation(
    val breakdown: XpBreakdown,
    val newScoringState: ScoringState,
    val levelsCrossed: List<Int>,
)

/**
 * The XP calculation, mirroring the `submitStudySession` Cloud Function's `computeSessionXp`
 * (`functions/src/lib/xpScoring.ts`). Pure: every rate comes from [config] and no scoring number is a
 * literal here. Both implementations run the shared cases in `testdata/xp-scoring/`, so a rule changed
 * on one side only fails a test.
 *
 * Takes each Rated card's [FlashcardResult.Rated.wasPreviouslyMastered] as given. [scoreSession] has
 * already replaced it with the flag the Card Progress merge derived, as the server does before calling
 * its own copy.
 *
 * The Streak and Daily Goal awards ([XpBreakdown.streakBonus], [XpBreakdown.dailyGoalBonus]) are not
 * computed on the client yet: both stay zero, and the Streak fields of [scoringState] carry over
 * unchanged.
 *
 * @param newCardsStudied cards with no Card Progress record before the session.
 * @param scoringState the account's state before this session.
 */
fun calculateSessionXp(session: SessionResult, newCardsStudied: Int, scoringState: ScoringState, config: XpConfig): SessionXpCalculation {
    val breakdown = calculateBreakdown(session, newCardsStudied, config)
    val (newScoringState, levelsCrossed) = applyDelta(scoringState, breakdown.xpTotal, config)
    return SessionXpCalculation(breakdown = breakdown, newScoringState = newScoringState, levelsCrossed = levelsCrossed)
}

private const val SECONDS_PER_MINUTE = 60

private fun calculateBreakdown(session: SessionResult, newCardsStudied: Int, config: XpConfig): XpBreakdown {
    val cardAwards = if (session is SessionResult.Rated) calculateRatedCardAwards(session.cardResults, config) else RatedCardAwards()
    return XpBreakdown(
        newCards = newCardsStudied * config.newCardStudied,
        mastered = cardAwards.mastered,
        partial = cardAwards.partial,
        masteryDefenseBonus = cardAwards.masteryDefenseBonus,
        demastered = cardAwards.demastered,
        timeStudied = (session.durationSeconds / SECONDS_PER_MINUTE) * config.minuteStudied,
        sessionCompletionBonus = if (session.abandoned) 0 else config.sessionCompleted,
    )
}

/** [FlashcardResult.Rated]'s share of a breakdown: every award only a Rated session can produce. */
private data class RatedCardAwards(
    val mastered: Int = 0,
    val partial: Int = 0,
    val masteryDefenseBonus: Int = 0,
    val demastered: Int = 0,
)

/**
 * A card ending Mastered that was already Mastered earns [XpConfig.masteryDefended] **instead of**
 * [XpConfig.cardMastered], not both: defending is its own, smaller reward.
 */
private fun calculateRatedCardAwards(cardResults: List<FlashcardResult.Rated>, config: XpConfig): RatedCardAwards {
    var mastered = 0
    var partial = 0
    var masteryDefenseBonus = 0
    var demastered = 0
    cardResults.forEach { entry ->
        when (entry.state) {
            FlashcardStudyProgressState.Mastered -> {
                if (entry.wasPreviouslyMastered) masteryDefenseBonus += config.masteryDefended else mastered += config.cardMastered
            }
            FlashcardStudyProgressState.Partial -> partial += config.cardPartial
            FlashcardStudyProgressState.Failed -> if (entry.wasPreviouslyMastered) demastered += config.cardDemastered
            FlashcardStudyProgressState.Seen -> error("A Rated card result can never resolve to Seen")
        }
    }
    return RatedCardAwards(mastered = mastered, partial = partial, masteryDefenseBonus = masteryDefenseBonus, demastered = demastered)
}

/**
 * A positive [delta] climbs [ScoringState.level] one [XpConfig.levelThreshold] at a time, reporting
 * each crossing in ascending order; the leftover past a threshold carries into the next level. A
 * negative delta is applied as `max(delta, -xpIntoCurrentLevel)`: both [ScoringState.xp] and
 * [ScoringState.xpIntoCurrentLevel] move by that applied amount, so neither the points into the level
 * nor the level itself ever drops below where it stood.
 */
private fun applyDelta(state: ScoringState, delta: Int, config: XpConfig): Pair<ScoringState, List<Int>> {
    val appliedDelta = if (delta < 0) maxOf(delta.toLong(), -state.xpIntoCurrentLevel) else delta.toLong()

    var xpIntoCurrentLevel = state.xpIntoCurrentLevel + appliedDelta
    var level = state.level
    val levelsCrossed = mutableListOf<Int>()
    while (xpIntoCurrentLevel >= config.levelThreshold(level)) {
        xpIntoCurrentLevel -= config.levelThreshold(level)
        level += 1
        levelsCrossed += level
    }

    return state.copy(xp = state.xp + appliedDelta, level = level, xpIntoCurrentLevel = xpIntoCurrentLevel) to levelsCrossed
}
