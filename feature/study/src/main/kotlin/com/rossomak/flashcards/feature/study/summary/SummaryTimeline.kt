package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Waits between [SummaryPhase] steps; each element brings its own transition.
 *
 * | Step | Starts |
 * |---|---|
 * | [SummaryPhase.Counting] | A tile every [TILE_INTERVAL]; the total counts to it over [COUNT_DURATION] |
 * | [SummaryPhase.Revealing] | [LIST_PAUSE] after the last tile, with confetti when the total is positive |
 * | [SummaryPhase.Pouring] | [POUR_START] later; the Level card's own pour, nothing waits for it |
 * | [SummaryPhase.Settled] | [SHEET_START] later; the sheet rises over [SHEET_RISE] |
 */
internal val TILE_INTERVAL: Duration = 720.milliseconds
internal val COUNT_DURATION: Duration = 600.milliseconds
internal val LIST_PAUSE: Duration = 1_400.milliseconds
internal val POUR_START: Duration = 680.milliseconds
internal val SHEET_START: Duration = 240.milliseconds
internal val SHEET_RISE: Duration = 640.milliseconds

/** Cubic ease-out, the curve of the counting total. */
internal val CountEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

internal fun Duration.toMillisInt(): Int = inWholeMilliseconds.toInt()
