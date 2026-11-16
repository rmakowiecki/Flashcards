package com.rossomak.flashcards.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colors of a Rating: the Failed, Partial and Correct sets every component that shows one reads,
 * the self-rating buttons and the attempt indicator alike, so a rating looks the same wherever it is.
 *
 * Deliberately separate from [SemanticColors]: those encode a status (a warning banner, an error
 * state), these encode a Rating, and the two may drift apart, so a Failed red need not equal a
 * warning red. Both themes use opaque containers, so a rating reads the same on a card, a sheet or a
 * dialog. Dark adds a ring in the content color, which light does not need.
 */
@Immutable
data class RatingColors(
    val failed: RatingColorSet,
    val partial: RatingColorSet,
    val correct: RatingColorSet,
)

/** One Rating's colors; [border] is null where the theme draws no ring. */
@Immutable
data class RatingColorSet(val container: Color, val content: Color, val border: Color?)

/** Opacity of the dark ring: the content color, faint. */
private const val DARK_RATING_BORDER_ALPHA = 0.40f

private val failedContainerLight = Color(0xFFF6D9DA)
private val failedContentLight = Color(0xFFC94F4F)
private val partialContainerLight = Color(0xFFF6E8C8)
private val partialContentLight = Color(0xFF9F7122)
private val correctContainerLight = Color(0xFFD3EBD6)
private val correctContentLight = Color(0xFF38874E)

private val failedContainerDark = Color(0xFF3A1F20)
private val failedContentDark = Color(0xFFE88C8C)
private val partialContainerDark = Color(0xFF3A2F16)
private val partialContentDark = Color(0xFFE8B84B)
private val correctContainerDark = Color(0xFF1E3524)
private val correctContentDark = Color(0xFF7BD98A)

val lightRatingColors = RatingColors(
    failed = RatingColorSet(container = failedContainerLight, content = failedContentLight, border = null),
    partial = RatingColorSet(container = partialContainerLight, content = partialContentLight, border = null),
    correct = RatingColorSet(container = correctContainerLight, content = correctContentLight, border = null),
)

private fun darkRatingColorSet(container: Color, content: Color) = RatingColorSet(
    container = container,
    content = content,
    border = content.copy(alpha = DARK_RATING_BORDER_ALPHA),
)

val darkRatingColors = RatingColors(
    failed = darkRatingColorSet(container = failedContainerDark, content = failedContentDark),
    partial = darkRatingColorSet(container = partialContainerDark, content = partialContentDark),
    correct = darkRatingColorSet(container = correctContainerDark, content = correctContentDark),
)

val LocalRatingColors = staticCompositionLocalOf { lightRatingColors }

val MaterialTheme.ratingColors: RatingColors
    @Composable
    @ReadOnlyComposable
    get() = LocalRatingColors.current
