package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Emphasized
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Neutral
import com.rossomak.flashcards.core.ui.composables.SyntaxCodeBlock
import com.rossomak.flashcards.core.ui.composables.buttons.FlashcardsTextButton
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.composables.withInlineCode
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R

/**
 * The white rounded card at the center of every Study Session screen
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)):
 * a QUESTION pill + difficulty badge, the question and its code blocks, and — once revealed — an
 * ANSWER pill, the answer and its code blocks, an optional attempt-history row and an optional
 * "Learn more" button into Extended Context.
 *
 * [attemptSlots] is empty for Fast (which has no concept of Attempts) and non-empty for Rated —
 * the row it renders is skipped entirely rather than shown empty, so this stays a single
 * component both Study Modes share rather than forking on a mode flag.
 */
@Composable
fun FlashcardCard(
    modifier: Modifier = Modifier,
    card: Flashcard,
    isAnswerRevealed: Boolean,
    attemptSlots: List<FlashcardsAttemptSlotState> = emptyList(),
    onExtendedContextClick: (String) -> Unit,
) {
    val syntaxEngine = remember { SyntaxTokenizer() }
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.card)
    val scrollState = key(card.id) { rememberScrollState() }

    Surface(
        modifier = modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.normal)
                .flashcardsScrollFade(
                    scrollState = scrollState,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                )
                .verticalScroll(scrollState),
        ) {
            FlashcardQuestionSection(
                card = card,
                syntaxEngine = syntaxEngine,
                isAnswerRevealed = isAnswerRevealed,
                attemptSlots = attemptSlots,
            )
            AnimatedVisibility(
                visible = isAnswerRevealed,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                FlashcardAnswerSection(
                    card = card,
                    syntaxEngine = syntaxEngine,
                    onExtendedContextClick = onExtendedContextClick,
                )
            }
        }
    }
}

@Composable
private fun FlashcardQuestionSection(
    card: Flashcard,
    syntaxEngine: SyntaxTokenizer,
    isAnswerRevealed: Boolean,
    attemptSlots: List<FlashcardsAttemptSlotState>,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIndicatorBadge(
                label = stringResource(R.string.study_session_question_label),
                emphasis = if (isAnswerRevealed) Neutral else Emphasized,
            )
            Spacer(modifier = Modifier.weight(1f))
            FlashcardsDifficultyBadge(level = card.difficulty)
        }
        if (attemptSlots.isNotEmpty()) {
            FlashcardsAttemptIndicator(
                slots = attemptSlots,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
    Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
    Text(
        text = card.question.withInlineCode(),
        style = MaterialTheme.typography.bodyLarge,
    )
    card.questionCode?.forEach { codeBlock ->
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
        SyntaxCodeBlock(
            code = codeBlock.code,
            language = codeBlock.language,
            engine = syntaxEngine,
        )
    }
}

@Composable
private fun FlashcardAnswerSection(
    card: Flashcard,
    syntaxEngine: SyntaxTokenizer,
    onExtendedContextClick: (String) -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        val extendedContext = card.extendedContext
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIndicatorBadge(
                label = stringResource(R.string.study_session_answer_label),
                emphasis = Emphasized,
            )
            if (!extendedContext.isNullOrBlank()) {
                Spacer(modifier = Modifier.weight(1f))
                FlashcardsTextButton(
                    text = stringResource(R.string.study_session_learn_more_button),
                    onClick = { onExtendedContextClick(extendedContext) },
                    icon = Icons.Default.Info,
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
        Text(
            text = card.answer.withInlineCode(),
            style = MaterialTheme.typography.bodyMedium,
        )
        card.answerCode?.forEach { codeBlock ->
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
            SyntaxCodeBlock(
                code = codeBlock.code,
                language = codeBlock.language,
                engine = syntaxEngine,
            )
        }
    }
}
