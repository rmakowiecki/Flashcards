package com.rossomak.flashcards.core.domain.model

import kotlin.math.ceil
import kotlin.math.pow

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
 * @param minuteStudied per minute of session time. Both modes.
 * @param levelCurveBase the level curve's `base` in `ceil(base × level^exponent / 1000) × 1000` —
 * a tuning value, not yet chosen for real (the intended shape: early levels reachable in one or
 * two good sessions, the middle range demanding multi-day effort, the high levels long-term).
 * @param levelCurveExponent the curve's `exponent`, same formula, same tuning status as
 * [levelCurveBase].
 */
data class XpConfig(
    val newCardStudied: Int = 10,
    val cardMastered: Int = 100,
    val cardPartial: Int = 25,
    val masteryDefended: Int = 50,
    val cardDemastered: Int = -CARD_DEMASTERED_MAGNITUDE,
    val sessionCompleted: Int = 500,
    val dailyGoalMet: Int = 1000,
    val streakPerDay: Int = 250,
    val streakMaxPerDay: Int = 2500,
    val minuteStudied: Int = 10,
    val levelCurveBase: Double = 1000.0,
    val levelCurveExponent: Double = 2.5,
) {
    private companion object {
        // Named rather than inlined as a bare -80 default above — detekt's MagicNumber check does
        // not see through the unary minus on a raw literal default value the way it does a plain one.
        const val CARD_DEMASTERED_MAGNITUDE = 80
    }
}

/**
 * The total points needed to complete [level] and advance to the next one, per the curve
 * shape: `ceil(base × level^exponent / 1000) × 1000`. A free function on [XpConfig] rather than a
 * member, so [com.rossomak.flashcards.core.domain.scoring.calculateSessionXp]'s level-up loop
 * and the Session Summary's own progress-within-level display read the exact same formula — the one
 * place a tuning change to the curve's shape, not just its parameters, would need to happen.
 */
fun XpConfig.levelThreshold(level: Int): Long {
    val rounded = ceil(levelCurveBase * level.toDouble().pow(levelCurveExponent) / LEVEL_THRESHOLD_ROUNDING_UNIT)
    return rounded.toLong() * LEVEL_THRESHOLD_ROUNDING_UNIT
}

private const val LEVEL_THRESHOLD_ROUNDING_UNIT = 1000L
