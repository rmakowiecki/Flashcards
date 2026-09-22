package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R

/**
 * The non-sheet body shared by every Study Session screen
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)):
 * the loading spinner, the load-error text, the empty-deck message, and — once cards exist —
 * [FlashcardCard] for the current card.
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
    error: String?,
    flashcards: List<Flashcard>,
    currentCardIndex: Int,
    isAnswerRevealed: Boolean,
    innerPadding: PaddingValues,
    attemptSlots: List<FlashcardsAttemptSlotState> = emptyList(),
    onExtendedContextClick: (String) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> CenteredBox(innerPadding) { CircularProgressIndicator() }
            error != null -> CenteredBox(innerPadding) { Text(text = error) }
            flashcards.isEmpty() -> CenteredBox(innerPadding) {
                Text(text = stringResource(R.string.study_session_no_cards_message))
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
