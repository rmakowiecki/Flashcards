package com.rossomak.flashcards.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

/**
 * Fixed 10-step difficulty ramp (1 easiest – 10 hardest), green to red. Deliberately identical in
 * both themes — like [com.rossomak.flashcards.core.designsystem.theme.BrandColors], it encodes card difficulty rather than a themed surface, so
 * it never flips light/dark.
 */
object DifficultyColors {
    /** Number of difficulty levels the ramp covers. */
    const val LEVELS = 10

    private val ramp = listOf(
        Color(0xFF1F9730),
        Color(0xFF81B420),
        Color(0xFFB7C30E),
        Color(0xFFE2CE00),
        Color(0xFFFCC70D),
        Color(0xFFF59D1C),
        Color(0xFFEE7F1E),
        Color(0xFFE45C1D),
        Color(0xFFDF481C),
        Color(0xFFD92A1B),
    )

    /** Foreground color on every ramp stop — all ten are saturated enough for white contrast. */
    val onRamp: Color = Color.White

    /** Ramp color for a 1-[LEVELS] difficulty [level]. */
    fun colorFor(level: Int): Color {
        require(level in 1..LEVELS) { "Difficulty level must be in 1..$LEVELS, was $level" }
        return ramp[level - 1]
    }
}

val MaterialTheme.difficultyColors: DifficultyColors
    get() = DifficultyColors
