package com.rossomak.flashcards.feature.study.summary

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Every timing of the Summary's payoff, in one place. Times in the second table are measured from the
 * moment the XP tile list starts to fade ("list fade").
 *
 * | Step | Timing |
 * |---|---|
 * | Frame 0 | The total reads "0 XP", no tiles, Skip visible |
 * | First tile | [FIRST_TILE_DELAY] after frame 0, only when there are lines |
 * | Tiles | One every [TILE_INTERVAL]. A tile slides in over [TILE_ENTER]; [TILE_COUNT_DELAY] later its amount joins the running total over [TILE_COUNT_DURATION], eased by [CountEasing]. A loss counts down |
 * | After the last tile | [LIST_PAUSE], then confetti when the total is positive, and the tile list fades over [LIST_FADE] |
 *
 * | Step | Starts after the list fade starts | Lasts |
 * |---|---|---|
 * | Level card drops in | 0 | [CARD_ENTER] |
 * | Level card switches to the after position, and pours it | [POUR_START] | The card's own pour, at most 2.5 s |
 * | Sheet rises | [SHEET_START] | [SHEET_RISE] |
 * | App bar title and close fade in | [SHEET_START] | [APP_BAR_FADE] |
 *
 * The sheet and app bar overlap the end of the card entrance and the pour on purpose. The pour is the
 * Level card's own animation: nothing here waits for it to end.
 */
internal val FIRST_TILE_DELAY: Duration = 560.milliseconds
internal val TILE_INTERVAL: Duration = 720.milliseconds
internal val TILE_ENTER: Duration = 360.milliseconds
internal val TILE_COUNT_DELAY: Duration = 220.milliseconds
internal val TILE_COUNT_DURATION: Duration = 400.milliseconds
internal val LIST_PAUSE: Duration = 520.milliseconds
internal val LIST_FADE: Duration = 420.milliseconds
internal val CARD_ENTER: Duration = 420.milliseconds
internal val POUR_START: Duration = 680.milliseconds
internal val SHEET_START: Duration = 920.milliseconds
internal val SHEET_RISE: Duration = 640.milliseconds
internal val APP_BAR_FADE: Duration = 640.milliseconds

/** The tile list switches to its compact variant above this many lines. */
internal const val COMPACT_TILE_THRESHOLD = 6

/** The offset a tile and the Level card slide in from, in dp. */
internal const val TILE_SLIDE_DP = 24
internal const val CARD_SLIDE_DP = 64

/** Cubic ease-out, the curve of every number that counts. */
internal val CountEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** The curve of every slide: a tile, the Level card, the total gliding down and the sheet. */
internal val SlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun Duration.toMillisInt(): Int = inWholeMilliseconds.toInt()
