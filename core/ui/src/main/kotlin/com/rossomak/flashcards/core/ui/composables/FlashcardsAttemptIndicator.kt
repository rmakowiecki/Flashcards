package com.rossomak.flashcards.core.ui.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.rossomak.flashcards.core.ui.R
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState.Correct
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState.Current
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState.Failed
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState.Future
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState.Partial
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.semanticColors
import com.rossomak.flashcards.core.ui.theme.sizes
import com.rossomak.flashcards.core.ui.theme.spacing

private val ATTEMPT_SLOT_SIZE = 20.dp
private val ATTEMPT_ICON_SIZE = 12.dp

/** How far a slot swells when a rating lands in it. */
private const val ATTEMPT_SLOT_PULSE_SCALE = 1.25f

/**
 * Read-only history of a card's attempts within a session: one small square per attempt, filled
 * with the same red/amber/green a [FlashcardsAttemptSlotState.Failed]/[Partial]/[Correct] rating
 * uses, an unfilled square with a heavier primary-colored border for the [FlashcardsAttemptSlotState.Current]
 * attempt, and an unfilled square with a hairline border for each [FlashcardsAttemptSlotState.Future]
 * one. Purely presentational — [slots] is fully caller-driven; this does not know a card's max
 * attempt count or track state itself.
 *
 * A slot that turns rated while shown pulses once; slots already rated on first show never pulse.
 *
 * Not interactive: the whole row collapses into a single merged content description rather than
 * exposing one semantics node per slot, since there is nothing here for a screen reader user to
 * act on individually.
 */
@Composable
fun FlashcardsAttemptIndicator(
    slots: List<FlashcardsAttemptSlotState>,
    modifier: Modifier = Modifier,
) {
    val description = slots.mapIndexed { index, slot -> slot.contentDescription(attemptNumber = index + 1) }
        .joinToString(separator = " ")

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxsmall),
    ) {
        slots.forEach { slot -> AttemptSlot(slot) }
    }
}

@Composable
private fun AttemptSlot(state: FlashcardsAttemptSlotState) {
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.xsmall)
    val semanticColors = MaterialTheme.semanticColors

    val (containerColor, contentColor) = when (state) {
        Failed -> semanticColors.negativeContainer to semanticColors.onNegativeContainer
        Partial -> semanticColors.neutralContainer to semanticColors.onNeutralContainer
        Correct -> semanticColors.positiveContainer to semanticColors.onPositiveContainer
        Current, Future -> Color.Transparent to Color.Transparent
    }
    val borderColor = when (state) {
        Current -> MaterialTheme.colorScheme.primary
        Future -> MaterialTheme.colorScheme.outlineVariant
        else -> null
    }
    val borderWidth = if (state == Current) MaterialTheme.sizes.tagChipBorder else MaterialTheme.sizes.hairline
    val pulseScale = remember { Animatable(1f) }
    var previousState by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        val isNewRating = !previousState.isRated() && state.isRated()
        previousState = state
        if (isNewRating) {
            val pulseSpec = tween<Float>(durationMillis = FlashcardsMotion.DURATION_SHORT_MS, easing = FlashcardsMotion.StandardEasing)
            pulseScale.animateTo(ATTEMPT_SLOT_PULSE_SCALE, pulseSpec)
            pulseScale.animateTo(1f, pulseSpec)
        }
    }

    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .size(ATTEMPT_SLOT_SIZE),
        shape = shape,
        color = containerColor,
        border = borderColor?.let { BorderStroke(width = borderWidth, color = it) },
    ) {
        val icon = state.icon()
        if (icon != null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    // Decorative: the row exposes one merged content description for the whole history.
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.xxsmall)
                        .size(ATTEMPT_ICON_SIZE),
                )
            }
        }
    }
}

private fun FlashcardsAttemptSlotState.isRated(): Boolean = this == Failed || this == Partial || this == Correct

private fun FlashcardsAttemptSlotState.icon(): ImageVector? = when (this) {
    Failed -> Icons.Filled.Close
    Partial -> Icons.Filled.Remove
    Correct -> Icons.Filled.Check
    Current, Future -> null
}

@Composable
private fun FlashcardsAttemptSlotState.contentDescription(attemptNumber: Int): String = when (this) {
    Failed -> stringResource(R.string.common_attempt_indicator_failed_cd, attemptNumber)
    Partial -> stringResource(R.string.common_attempt_indicator_partial_cd, attemptNumber)
    Correct -> stringResource(R.string.common_attempt_indicator_correct_cd, attemptNumber)
    Current -> stringResource(R.string.common_attempt_indicator_current_cd, attemptNumber)
    Future -> stringResource(R.string.common_attempt_indicator_future_cd, attemptNumber)
}

@ShowkaseComposable(name = "Attempt indicator", group = "Chips & Badges")
@PreviewLightDark
@Composable
private fun FlashcardsAttemptIndicatorShowcase() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
                FlashcardsAttemptIndicator(
                    slots = listOf(
                        Failed,
                        Partial,
                        Current,
                        Future,
                    ),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FlashcardsAttemptIndicatorAllGradedPreview() {
    FlashcardsTheme {
        Surface {
            Box(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
                FlashcardsAttemptIndicator(
                    slots = listOf(
                        Failed,
                        Partial,
                        Correct,
                        Correct,
                    ),
                )
            }
        }
    }
}
