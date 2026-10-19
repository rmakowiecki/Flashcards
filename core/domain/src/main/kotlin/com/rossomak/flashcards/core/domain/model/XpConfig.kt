package com.rossomak.flashcards.core.domain.model

/**
 * Every tunable scoring number XP and leveling needs, in one place — no point award, no
 * penalty, and no level-curve parameter is ever a constant in domain logic; the calculation that
 * eventually consumes this reads every value from here instead
 * ([ADR-0047](../../../../../../../docs/adr/0047-xp-values-behind-a-config-repository.md)).
 *
 * **The values normally come from the server.** A server-owned configuration document is the
 * source of truth: the `submitStudySession` Cloud Function scores every session with it, and
 * [com.rossomak.flashcards.core.domain.repository.XpConfigRepository] serves the last copy the client
 * fetched. The default constructor values are a fallback only, used until a first fetch succeeds.
 * They stay identical to the shared default configuration file in `testdata/xp-scoring/` at the repo
 * root, and to the Cloud Functions' own bundled default; a test on each side enforces that.
 *
 * **The client scores with its cached copy.** The Session Summary's fallback preview and the
 * projection of Pending Sessions both read [com.rossomak.flashcards.core.domain.repository.XpConfigRepository]
 * when they score, never a copy captured at session start. The server scores each session with the
 * document current when it is delivered, so a rate changed in between makes the preview differ from
 * the recorded award until the server's result is read back.
 *
 * @param newCardStudied per card seeing a card for the first time ever. Both Study Modes.
 * @param cardMastered per card ending Mastered. Rated only — Fast has no mastery concept.
 * @param cardPartial per card ending Partial. Rated only.
 * @param masteryDefended per card that keeps a previously-mastered card Mastered again. Rated only,
 * and structurally unreachable until Mastery Defense selection exists.
 * @param cardDemastered per card that loses a previously-mastered card's mastery — negative. Rated
 * only, same dependency as [masteryDefended].
 * @param sessionCompleted flat, once, only for a session that finishes its deck rather than being
 * abandoned. Both modes.
 * @param dailyGoalMet flat, once per calendar day the daily study-minutes goal is met. Both modes.
 * @param streakPerDay per consecutive study day, before [streakMaxPerDay] caps it. Both modes.
 * @param streakMaxPerDay the ceiling [streakPerDay] × streak-length is clamped to.
 * @param levelBaseCost the XP that completes the starting level; every later level costs the previous
 * one's cost plus its tier's step ([levelThreshold]).
 * @param levelCostSteps the step added per level, one entry per tier of [levelsPerCostStep] levels,
 * starting from the first tier. The last entry applies to every later tier too, so it is the cap on how
 * much more a level costs than the one before it. Never empty, every entry positive.
 * @param levelsPerCostStep how many levels share one entry of [levelCostSteps]. Positive.
 * @throws IllegalArgumentException when [levelCostSteps] is empty or holds a non-positive step, or
 * [levelsPerCostStep] is not positive: [levelThreshold] cannot be computed for such a curve.
 */
data class XpConfig(
    val newCardStudied: Int = 10,
    val cardMastered: Int = 100,
    val cardPartial: Int = 25,
    val masteryDefended: Int = 50,
    val cardDemastered: Int = -CARD_DEMASTERED_MAGNITUDE,
    val sessionCompleted: Int = 100,
    val dailyGoalMet: Int = 500,
    val streakPerDay: Int = 250,
    val streakMaxPerDay: Int = 1000,
    val levelBaseCost: Int = 1000,
    val levelCostSteps: List<Int> = DEFAULT_LEVEL_COST_STEPS,
    val levelsPerCostStep: Int = 5,
) {
    init {
        require(levelCostSteps.isNotEmpty()) { "levelCostSteps must be a nonempty list, got $levelCostSteps" }
        require(levelCostSteps.all { step -> step > 0 }) { "levelCostSteps must hold positive integers, got $levelCostSteps" }
        require(levelsPerCostStep > 0) { "levelsPerCostStep must be positive, got $levelsPerCostStep" }
    }

    private companion object {
        // Named rather than inlined as a bare -100 default above — detekt's MagicNumber check does
        // not see through the unary minus on a raw literal default value the way it does a plain one.
        const val CARD_DEMASTERED_MAGNITUDE = 100

        val DEFAULT_LEVEL_COST_STEPS = listOf(250, 500, 750, 1000, 1500, 2000)
    }
}

/**
 * The points needed to complete [level] alone, not a running total across levels, and advance to the
 * next one. The starting level costs
 * [XpConfig.levelBaseCost], and every later level costs the previous level's cost plus the step of its
 * tier: level `n` belongs to tier `(n - 1) / levelsPerCostStep`, a tier past the end of
 * [XpConfig.levelCostSteps] reuses the last step, so consecutive levels always cost more than the one
 * before. A free function on [XpConfig] rather than a member, so
 * [com.rossomak.flashcards.core.domain.scoring.calculateSessionXp]'s level-up loop and the Session
 * Summary's own progress-within-level display read the exact same rule.
 */
fun XpConfig.levelThreshold(level: Int): Long {
    var cost = levelBaseCost.toLong()
    for (reachedLevel in ScoringState.STARTING_LEVEL + 1..level) {
        val tier = minOf((reachedLevel - 1) / levelsPerCostStep, levelCostSteps.lastIndex)
        cost += levelCostSteps[tier]
    }
    return cost
}
