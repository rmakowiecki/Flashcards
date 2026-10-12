package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.theme.brandColors
import com.rossomak.flashcards.core.ui.theme.spacing

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
    Box(modifier = modifier.fillMaxSize()) {
        when {
            // No cards only before the first Running snapshot, or while a failed load returns to Preview.
            isLoading || flashcards.isEmpty() -> CenteredBox(innerPadding) {
                CircularProgressIndicator(color = MaterialTheme.brandColors.onGradientContent)
            }
            else -> BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(MaterialTheme.spacing.normal),
            ) {
                FlashcardCard(
                    modifier = Modifier.heightIn(max = maxHeight),
                    card = flashcards[currentCardIndex],
                    isAnswerRevealed = isAnswerRevealed,
                    attemptSlots = attemptSlots,
                    onExtendedContextClick = onExtendedContextClick,
                )
            }
        }
    }
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
