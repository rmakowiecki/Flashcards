package com.rossomak.flashcards.feature.study.chrome

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.core.ui.composables.FlashcardsDifficultyBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorBadge
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Emphasized
import com.rossomak.flashcards.core.ui.composables.FlashcardsIndicatorEmphasis.Neutral
import com.rossomak.flashcards.core.ui.composables.FlashcardsScrollFadeHeight
import com.rossomak.flashcards.core.ui.composables.SyntaxCodeBlock
import com.rossomak.flashcards.core.ui.composables.flashcardsScrollFade
import com.rossomak.flashcards.core.ui.composables.withInlineCode
import com.rossomak.flashcards.core.ui.theme.FlashcardsMotion
import com.rossomak.flashcards.core.ui.theme.cornerRadius
import com.rossomak.flashcards.core.ui.theme.spacing
import com.rossomak.flashcards.feature.study.R
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.takeWhile

private val ANSWER_SLIDE_DISTANCE = 12.dp

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
 *
 * Revealing runs one transition on [isAnswerRevealed], reversed on hide: the question shrinks to
 * the answer's size while the answer grows in below it, sliding down as it fades in. The card's
 * height moves in one direction between its two settled heights; the caller only bounds it.
 */
@Composable
fun FlashcardCard(
    modifier: Modifier = Modifier,
    card: Flashcard,
    isAnswerRevealed: Boolean,
    attemptSlots: List<FlashcardsAttemptSlotState> = emptyList(),
    onExtendedContextClick: (String) -> Unit,
) {
    val motion = LocalStudySessionCardMotion.current
    val syntaxEngine = remember { SyntaxTokenizer() }
    val textMeasurer = rememberTextMeasurer()
    val shape = RoundedCornerShape(MaterialTheme.cornerRadius.card)
    val scrollState = key(card.id) { rememberScrollState() }
    val probe = key(card.id) { remember { CardLayoutProbe() } }
    val revealTransition = key(card.id) { updateTransition(targetState = isAnswerRevealed, label = "FlashcardReveal") }
    val revealFraction by revealTransition.animateFloat(
        transitionSpec = { motion.revealSpec },
        label = "FlashcardRevealFraction",
    ) { revealed -> if (revealed) 1f else 0f }
    val answerAlpha by revealTransition.animateAnswerAlpha()
    val isAnswerComposed = revealTransition.currentState || revealTransition.targetState
    val answerSlideDistancePx = with(LocalDensity.current) { ANSWER_SLIDE_DISTANCE.toPx() }
    val questionText = card.question.withInlineCode()
    val (unrevealedQuestionStyle, revealedQuestionStyle) = questionStyles(card)
    RevealScrollEffect(
        cardId = card.id,
        isAnswerRevealed = isAnswerRevealed,
        revealTransition = revealTransition,
        revealFraction = { revealFraction },
        scrollState = scrollState,
        probe = probe,
    )

    Surface(
        modifier = modifier.fillMaxWidth().clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.normal)
                .recordsViewportMaxHeight(probe)
                .flashcardsScrollFade(
                    scrollState = scrollState,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                )
                .verticalScroll(scrollState)
                .settledHeight(
                    isRevealing = { revealTransition.isRunning && isAnswerComposed },
                    fraction = { revealFraction },
                    probe = probe,
                    measureQuestion = { width, style -> textMeasurer.measure(questionText, style, constraints = Constraints(maxWidth = width)).size.height },
                    questionStyles = unrevealedQuestionStyle to revealedQuestionStyle,
                ),
        ) {
            FlashcardQuestionSection(
                card = card,
                questionText = questionText,
                questionStyle = lerp(start = unrevealedQuestionStyle, stop = revealedQuestionStyle, fraction = revealFraction),
                syntaxEngine = syntaxEngine,
                isAnswerRevealed = isAnswerRevealed,
                attemptSlots = attemptSlots,
                probe = probe,
            )
            if (isAnswerComposed) {
                FlashcardAnswerSection(
                    modifier = Modifier
                        .onPlaced { coordinates -> probe.answerSectionTop = coordinates.positionInParent().y }
                        .revealedHeight(fraction = revealFraction, probe = probe)
                        .graphicsLayer {
                            alpha = answerAlpha
                            translationY = -(1f - revealFraction) * answerSlideDistancePx
                        },
                    card = card,
                    syntaxEngine = syntaxEngine,
                    probe = probe,
                    onExtendedContextClick = onExtendedContextClick,
                )
            }
        }
    }
}

/** The question's unrevealed and revealed styles. A question with a code block never starts large. */
@Composable
private fun questionStyles(card: Flashcard): Pair<TextStyle, TextStyle> {
    val revealedStyle = MaterialTheme.typography.bodyMediumEmphasized
    return (if (card.questionCode.isNullOrEmpty()) MaterialTheme.typography.headlineSmallEmphasized else revealedStyle) to revealedStyle
}

/** The answer fades in a beat after the reveal starts, and out quickly when it is hidden again. */
@Composable
private fun Transition<Boolean>.animateAnswerAlpha(): State<Float> = animateFloat(
    transitionSpec = {
        if (false isTransitioningTo true) {
            tween(durationMillis = FlashcardsMotion.DURATION_MEDIUM_MS, delayMillis = FlashcardsMotion.DURATION_SHORT_MS)
        } else {
            tween(durationMillis = FlashcardsMotion.DURATION_SHORT_MS)
        }
    },
    label = "FlashcardAnswerAlpha",
) { revealed -> if (revealed) 1f else 0f }

/** Card part sizes and positions, in pixels from the content top. Written during layout, so plain fields, not state. */
private class CardLayoutProbe {
    var viewportMaxHeight = 0
    var questionHeight = 0
    var revealedQuestionHeight = 0
    var answerFullHeight = 0
    var answerShownHeight = 0
    var answerSectionTop = 0f
    var answerBadgeTop = 0f
    var answerTextTop = 0f
    var answerFirstLineBottom = 0f

    /** Where the answer section starts once the question has settled at its revealed size. */
    val settledAnswerSectionTop: Float get() = answerSectionTop - questionHeight + revealedQuestionHeight
}

/** Records the viewport's maximum height, which the reveal scroll aims within. */
private fun Modifier.recordsViewportMaxHeight(probe: CardLayoutProbe): Modifier = layout { measurable, constraints ->
    probe.viewportMaxHeight = constraints.maxHeight
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * While a reveal runs, reports a straight blend between the two settled heights, each raised to the
 * card's minimum, so the card moves in one direction although the question shrinks as the answer grows.
 */
private fun Modifier.settledHeight(
    isRevealing: () -> Boolean,
    fraction: () -> Float,
    probe: CardLayoutProbe,
    measureQuestion: (width: Int, style: TextStyle) -> Int,
    questionStyles: Pair<TextStyle, TextStyle>,
): Modifier = layout { measurable, constraints ->
    // The card's minimum height applies only to the reported height; measuring under it would pad the content.
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val (unrevealedQuestionStyle, revealedQuestionStyle) = questionStyles
    val unrevealedQuestionHeight = measureQuestion(placeable.width, unrevealedQuestionStyle)
    probe.revealedQuestionHeight = measureQuestion(placeable.width, revealedQuestionStyle)
    val height = if (isRevealing()) {
        val rest = placeable.height - probe.questionHeight - probe.answerShownHeight
        val hiddenHeight = (rest + unrevealedQuestionHeight).coerceAtLeast(constraints.minHeight)
        val revealedHeight = (rest + probe.revealedQuestionHeight + probe.answerFullHeight).coerceAtLeast(constraints.minHeight)
        (hiddenHeight + (revealedHeight - hiddenHeight) * fraction()).roundToInt()
    } else {
        placeable.height.coerceAtLeast(constraints.minHeight)
    }
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/**
 * Scrolls on the reveal's curve. Revealing scrolls just enough to show the ANSWER badge and the
 * answer's first line, only when the badge is fully out of sight. Hiding scrolls back to the top.
 */
@Composable
private fun RevealScrollEffect(
    cardId: String,
    isAnswerRevealed: Boolean,
    revealTransition: Transition<Boolean>,
    revealFraction: () -> Float,
    scrollState: ScrollState,
    probe: CardLayoutProbe,
) {
    val fadeHeightPx = with(LocalDensity.current) { FlashcardsScrollFadeHeight.toPx() }
    val currentRevealFraction by rememberUpdatedState(revealFraction)
    LaunchedEffect(cardId, isAnswerRevealed) {
        // The answer section first lays out a frame after the reveal starts.
        withFrameNanos { }
        if (isAnswerRevealed) {
            while (probe.answerFullHeight == 0) withFrameNanos { }
        }
        val startScroll = scrollState.value
        val startFraction = currentRevealFraction()
        val targetScroll = if (isAnswerRevealed) {
            val visibleHeight = probe.viewportMaxHeight - fadeHeightPx
            val badgeTop = probe.settledAnswerSectionTop + probe.answerBadgeTop
            if (badgeTop < startScroll + visibleHeight) return@LaunchedEffect
            (probe.settledAnswerSectionTop + probe.answerTextTop + probe.answerFirstLineBottom - visibleHeight).roundToInt()
        } else {
            0
        }
        if (targetScroll == startScroll) return@LaunchedEffect
        snapshotFlow { currentRevealFraction() to revealTransition.isRunning }
            .takeWhile { (_, isRunning) -> isRunning }
            .collect { (fraction) ->
                val progress = if (isAnswerRevealed) {
                    if (startFraction >= 1f) 1f else (fraction - startFraction) / (1f - startFraction)
                } else {
                    if (startFraction <= 0f) 1f else (startFraction - fraction) / startFraction
                }
                val scroll = startScroll + (targetScroll - startScroll) * progress.coerceIn(0f, 1f)
                scrollState.scrollTo(scroll.roundToInt())
            }
        scrollState.scrollTo(targetScroll)
    }
}

/** Lays the content out at full height but reports only [fraction] of it, without clipping. */
private fun Modifier.revealedHeight(fraction: Float, probe: CardLayoutProbe): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val shownHeight = (placeable.height * fraction).roundToInt()
    probe.answerFullHeight = placeable.height
    probe.answerShownHeight = shownHeight
    layout(placeable.width, shownHeight) { placeable.place(0, 0) }
}

@Composable
private fun FlashcardQuestionSection(
    card: Flashcard,
    questionText: AnnotatedString,
    questionStyle: TextStyle,
    syntaxEngine: SyntaxTokenizer,
    isAnswerRevealed: Boolean,
    attemptSlots: List<FlashcardsAttemptSlotState>,
    probe: CardLayoutProbe,
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
        text = questionText,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            probe.questionHeight = placeable.height
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
        style = questionStyle,
        color = MaterialTheme.colorScheme.onSurface,
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
    modifier: Modifier,
    card: Flashcard,
    syntaxEngine: SyntaxTokenizer,
    probe: CardLayoutProbe,
    onExtendedContextClick: (String) -> Unit,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.normal))
        val extendedContext = card.extendedContext
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPlaced { coordinates -> probe.answerBadgeTop = coordinates.positionInParent().y },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlashcardsIndicatorBadge(
                label = stringResource(R.string.study_session_answer_label),
                emphasis = Emphasized,
            )
            if (!extendedContext.isNullOrBlank()) {
                Spacer(modifier = Modifier.weight(1f))
                LearnMoreLink(onClick = { onExtendedContextClick(extendedContext) })
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xsmall))
        Text(
            text = card.answer.withInlineCode(),
            modifier = Modifier.onPlaced { coordinates -> probe.answerTextTop = coordinates.positionInParent().y },
            style = MaterialTheme.typography.bodyMedium,
            onTextLayout = { layout -> probe.answerFirstLineBottom = if (layout.lineCount > 0) layout.getLineBottom(0) else 0f },
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

/**
 * A bare icon+text tap target for the auxiliary "Learn more" action — deliberately not a
 * [FlashcardsTextButton], which floors at a fixed height that overpowers the compact,
 * intrinsic-height [FlashcardsIndicatorBadge] it sits beside in the same row.
 */
@Composable
private fun LearnMoreLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(MaterialTheme.cornerRadius.full))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xxsmall))
        Text(
            text = stringResource(R.string.study_session_learn_more_button),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
