package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Animation timing tokens for design-system components. Components must read durations and
 * easing from here rather than hardcoding raw millisecond values, so motion stays uniform
 * across the app.
 */
object FlashcardsMotion {
    /** Quick state feedback: selection tint, chevron rotation. */
    const val DURATION_SHORT_MS: Int = 150

    /** Standard content transitions: row expand/collapse, size changes. */
    const val DURATION_MEDIUM_MS: Int = 250

    /** Deliberate, larger transitions. */
    const val DURATION_LONG_MS: Int = 400

    /** Loaded content fading in and sliding up into place. */
    const val REVEAL_DURATION_MS: Int = 500

    /** How long a load runs before its loading indicator appears; faster loads never show one. */
    const val LOADING_INDICATOR_DELAY_MS: Int = 250

    /** Shortest time a loading indicator stays on screen once shown, so it never flashes. */
    const val LOADING_INDICATOR_MIN_VISIBLE_MS: Int = 400

    /** Default easing for enter/exit and size animations. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** Emphasized easing for expand/reveal interactions. */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized easing for something arriving: fast at first, then a long, soft settle. */
    val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
