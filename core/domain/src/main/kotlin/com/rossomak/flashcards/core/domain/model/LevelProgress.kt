package com.rossomak.flashcards.core.domain.model

/**
 * Where the User stands on the Level curve: their [level], the points earned since reaching it, and
 * the points that complete it. What a Level card draws.
 *
 * @param xpForNextLevel the total points needed to complete [level] ([XpConfig.levelThreshold]).
 */
data class LevelProgress(
    val level: Int,
    val xpIntoCurrentLevel: Long,
    val xpForNextLevel: Long,
) {
    /**
     * The fill of the current Level, from 0 to 1: 0 when [xpForNextLevel] is not positive, and never
     * above 1. Derived from the constructor values rather than one of them, so it can never disagree
     * with them and stays out of `equals`.
     */
    val progress: Float
        get() = if (xpForNextLevel <= 0) 0f else (xpIntoCurrentLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f)
}

/**
 * This scoring state's [LevelProgress], measured against [config]'s Level curve. `ScoringState()`
 * maps to the starting state: Level 1 with no points into it.
 */
fun ScoringState.toLevelProgress(config: XpConfig): LevelProgress = LevelProgress(
    level = level,
    xpIntoCurrentLevel = xpIntoCurrentLevel,
    xpForNextLevel = config.levelThreshold(level),
)
