package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.theme.AppSpacing
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing

private const val CARD_MIN_HEIGHT_FRACTION = 0.55f
private val CARD_MIN_HEIGHT_FLOOR = 240.dp
private val CARD_MIN_HEIGHT_CEILING = 360.dp

/**
 * The non-sheet body shared by every Study Session screen
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)):
 * the loading spinner and, once cards exist, [FlashcardCard] for the current card. A session that
 * loads no cards never shows here: its ViewModel returns to Preview instead.
 *
 * Takes the current card plus the values needed to pick it, not the screen state that owns them —
 * the two Study Modes are diverging into two different state types and this body must not force
 * either into a shared supertype just to be fed. [attemptSlots] rides straight through to
 * [FlashcardCard] — empty for Fast, populated for Rated.
 *
 * The card grows down to just above the sheet, then up into its top padding, then scrolls. Swaps
 * are keyed on index and id, so a Rated card returning straight to the queue's head stays put.
 */
@Composable
fun StudySessionBody(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    flashcards: List<Flashcard>,
    currentCardIndex: Int,
    isAnswerRevealed: Boolean,
    innerPadding: PaddingValues,
    attemptSlots: List<FlashcardsAttemptSlotState> = emptyList(),
    onExtendedContextClick: (String) -> Unit,
) {
    // No cards only before the first Running snapshot, or while a failed load returns to Preview.
    val presentedCard = if (isLoading || flashcards.isEmpty()) {
        null
    } else {
        PresentedCard(
            index = currentCardIndex,
            card = flashcards[currentCardIndex],
            isAnswerRevealed = isAnswerRevealed,
            attemptSlots = attemptSlots,
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (presentedCard == null) {
            CenteredBox(innerPadding) {
                CircularProgressIndicator(color = MaterialTheme.brandColors.onGradientContent)
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MaterialTheme.spacing.normal),
        ) {
            val motion = LocalStudySessionCardMotion.current
            val geometry = remember(maxHeight) { studySessionCardGeometry(bodyHeight = maxHeight) }
            AnimatedContent(
                targetState = presentedCard,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { motion.swapTransform((targetState?.index ?: 0) < (initialState?.index ?: 0)) },
                contentKey = { presented -> presented?.let { it.index to it.card.id } },
                label = "StudySessionCardSwap",
            ) { presented ->
                if (presented != null) {
                    FlashcardCard(
                        modifier = Modifier
                            .placedByGeometry(geometry)
                            .heightIn(min = geometry.minHeight, max = geometry.maxHeight),
                        card = presented.card,
                        isAnswerRevealed = presented.isAnswerRevealed,
                        attemptSlots = presented.attemptSlots,
                        onExtendedContextClick = onExtendedContextClick,
                    )
                }
            }
        }
    }
}

/** Everything one card renders with, so a leaving card keeps rendering its own last state. */
private data class PresentedCard(
    val index: Int,
    val card: Flashcard,
    val isAnswerRevealed: Boolean,
    val attemptSlots: List<FlashcardsAttemptSlotState>,
)

/** [minHeight] never exceeds the room below the default top; content taller than [maxHeight] scrolls. */
private data class StudySessionCardGeometry(
    val defaultTopPadding: Dp,
    val minTopPadding: Dp,
    val bottomLimit: Dp,
    val minHeight: Dp,
    val maxHeight: Dp,
)

/** The card sits [AppSpacing.xxlarge] below the header by default, rising to at most [AppSpacing.normal] below it. */
private fun studySessionCardGeometry(bodyHeight: Dp): StudySessionCardGeometry {
    val spacing = MaterialTheme.spacing
    val bottomLimit = (bodyHeight - spacing.medium).coerceAtLeast(0.dp)
    val defaultTopPadding = spacing.xxlarge.coerceAtMost((bottomLimit - spacing.normal).coerceAtLeast(0.dp))
    val roomBelowDefaultTop = bottomLimit - defaultTopPadding
    val minHeight = (roomBelowDefaultTop * CARD_MIN_HEIGHT_FRACTION)
        .coerceIn(CARD_MIN_HEIGHT_FLOOR, CARD_MIN_HEIGHT_CEILING)
        .coerceAtMost(roomBelowDefaultTop)
    return StudySessionCardGeometry(
        defaultTopPadding = defaultTopPadding,
        minTopPadding = spacing.normal.coerceAtMost(defaultTopPadding),
        bottomLimit = bottomLimit,
        minHeight = minHeight,
        maxHeight = (bottomLimit - spacing.normal).coerceAtLeast(minHeight),
    )
}

/** Places the card at the default top, raised by whatever it overflows, never above the minimum top. */
private fun Modifier.placedByGeometry(geometry: StudySessionCardGeometry): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val overflow = placeable.height - (geometry.bottomLimit - geometry.defaultTopPadding).roundToPx()
    val top = (geometry.defaultTopPadding.roundToPx() - overflow.coerceAtLeast(0))
        .coerceAtLeast(geometry.minTopPadding.roundToPx())
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top) }
}

@Composable
private fun CenteredBox(
    innerPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
