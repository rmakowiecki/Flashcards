package com.rossomak.flashcards.core.ui.composables.rating

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.ratingColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

/**
 * A single [FlashcardAttemptRating] as a circular icon button with an optional caption.
 *
 * Passing `null` for [onClick] renders the same visual as a non-interactive display — the read-only
 * form Voice Answering uses to show the grade it assigned, where the value is an outcome rather
 * than a choice. Interactive and read-only share one component (and one palette) so a grade badge
 * can never drift from the button the user would have tapped for the same rating.
 */
@Composable
fun FlashcardsRatingButton(
    rating: FlashcardAttemptRating,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val label = stringResource(rating.labelRes)
    val containerColor = MaterialTheme.ratingColors.containerColorFor(rating)
    val contentColor = MaterialTheme.ratingColors.contentColorFor(rating)
    val buttonSize = Modifier.size(MaterialTheme.sizes.ratingButton)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        if (onClick != null) {
            FilledIconButton(
                onClick = onClick,
                modifier = buttonSize,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            ) {
                Icon(imageVector = rating.icon, contentDescription = label)
            }
        } else {
            Surface(
                modifier = buttonSize,
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = rating.icon, contentDescription = label)
                }
            }
        }
        if (showLabel) {
            // The icon above already carries [label] as its content description; clearing the
            // caption's semantics keeps TalkBack from announcing the same word twice.
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The three [FlashcardAttemptRating] options side by side — the self-rating control of a Rated session.
 *
 * [onRatingSelect] is nullable for the same reason [FlashcardsRatingButton.onClick] is: onboarding
 * shows this row purely to teach the mechanic, with nothing to tap.
 */
@Composable
fun FlashcardsRatingButtonRow(
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    onRatingSelect: ((FlashcardAttemptRating) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        FlashcardAttemptRating.entries.forEach { rating ->
            FlashcardsRatingButton(
                rating = rating,
                showLabel = showLabels,
                onClick = onRatingSelect?.let { select -> { select(rating) } },
            )
        }
    }
}

private val FlashcardAttemptRating.icon: ImageVector
    get() = when (this) {
        FlashcardAttemptRating.Failed -> Icons.Default.Close
        FlashcardAttemptRating.PartiallyCorrect -> Icons.Default.Remove
        FlashcardAttemptRating.Correct -> Icons.Default.Check
    }

/** The Rating's display name, shared by the rating buttons and any text that names a Rating. */
@get:StringRes
val FlashcardAttemptRating.labelRes: Int
    get() = when (this) {
        FlashcardAttemptRating.Failed -> R.string.common_rating_failed_label
        FlashcardAttemptRating.PartiallyCorrect -> R.string.common_rating_partially_correct_label
        FlashcardAttemptRating.Correct -> R.string.common_rating_correct_label
    }

@ShowkaseComposable(name = "Rating buttons", group = "Rating")
@Composable
fun FlashcardsRatingButtonRowShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsRatingButtonRow(onRatingSelect = {})
        }
    }
}

@ShowkaseComposable(name = "Rating buttons (read-only)", group = "Rating")
@Composable
fun FlashcardsRatingButtonRowReadOnlyShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsRatingButtonRow()
        }
    }
}

@ShowkaseComposable(name = "Rating grade badge", group = "Rating")
@Composable
fun FlashcardsRatingButtonBadgeShowcase() {
    FlashcardsTheme {
        Surface {
            FlashcardsRatingButton(rating = FlashcardAttemptRating.Correct, showLabel = false)
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsRatingButtonRowPreview() {
    FlashcardsRatingButtonRowShowcase()
}
