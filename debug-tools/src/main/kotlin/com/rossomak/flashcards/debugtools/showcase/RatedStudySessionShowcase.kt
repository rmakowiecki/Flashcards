@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.AnswerRevealed
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.Question
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.VoiceAnswering
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.VoiceGraded
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.functions
import com.rossomak.flashcards.debugtools.showcase.fixtures.kotlin
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionContent
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionScreenState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val speakingVoiceBars: StateFlow<ImmutableList<Float>> = MutableStateFlow(persistentListOf(0.35f, 0.6f, 0.85f, 0.55f, 0.7f, 0.4f, 0.65f))

/** The `apply` card: a short answer that reads well spoken. */
private const val VOICE_GRADED_CARD_ID = "2026-07-05T12-31-10-08c84b"

/** Made up: filler and paraphrase that still earn Correct. */
private val voiceGrade = VoiceAnswerGrade(
    sanitizedTranscript = "So apply gives you back the object itself, not whatever the lambda returns. " +
        "You'd use it to configure something, like set a few properties and then keep using that same object.",
    gradePercent = 92,
    feedback = "Spot on: apply returns its receiver, and configuring an object is exactly its canonical use.",
)

private val silentVoiceBars: StateFlow<ImmutableList<Float>> = MutableStateFlow(persistentListOf())

/** Functions, three cards in. The card on screen is the first that suits [showcaseState]. */
private fun ShowcaseFixtures.ratedState(showcaseState: RatedShowcaseState): RatedStudySessionScreenState {
    val isOnScreen: (Flashcard) -> Boolean = when (showcaseState) {
        Question -> { card -> card.questionCode != null }
        AnswerRevealed -> { card -> card.answerCode != null && card.questionCode == null && card.extendedContext != null }
        VoiceAnswering -> { card -> card.questionCode == null && card.answerCode == null }
        VoiceGraded -> { card -> card.id == VOICE_GRADED_CARD_ID }
    }
    val base = RatedStudySessionScreenState(
        categoryName = kotlin.name,
        subcategoryNameById = mapOf(functions.id to functions.name),
        flashcards = flashcardsOf(functions.id).sortedByDescending(isOnScreen),
        completedCount = 3,
        distinctCardCount = 20,
    )
    return when (showcaseState) {
        Question -> base.copy(currentCardRatings = listOf(FlashcardAttemptRating.Failed))
        AnswerRevealed -> base.copy(isAnswerRevealed = true)
        VoiceAnswering -> base.copy(
            isVoiceAnsweringSession = true,
            isVoiceActive = true,
            isVoicePlaying = true,
            voiceAnswerPhase = VoiceAnswerPhase.Listening,
            isVoiceMicrophoneOpen = true,
            availableTransportCommands = setOf(TransportCommandType.Pause, TransportCommandType.Next, TransportCommandType.Previous),
        )

        // Second Attempt after a Failed one; the Correct marker shows during the feedback, as in the app.
        VoiceGraded -> base.copy(
            currentCardRatings = listOf(FlashcardAttemptRating.Failed, FlashcardAttemptRating.Correct),
            isAnswerRevealed = true,
            isVoiceAnsweringSession = true,
            isVoiceActive = true,
            isVoicePlaying = true,
            voiceAnswerPhase = VoiceAnswerPhase.SpeakingNotice,
            voiceAnswerSanitizedTranscript = voiceGrade.sanitizedTranscript,
            lastVoiceAnswerGrade = voiceGrade,
            availableTransportCommands = setOf(TransportCommandType.Pause, TransportCommandType.Next, TransportCommandType.Previous),
        )
    }
}

@Composable
internal fun RatedStudySessionShowcase(showcaseState: RatedShowcaseState) {
    val fixtures = LocalShowcaseFixtures.current
    RatedStudySessionContent(
        state = remember(fixtures, showcaseState) { fixtures.ratedState(showcaseState) },
        voiceBarsLevels = if (showcaseState == VoiceAnswering) speakingVoiceBars else silentVoiceBars,
        snackbarHostState = remember { SnackbarHostState() },
        onShowAnswer = {},
        onAttemptRating = {},
        onVoicePlayPause = {},
        onVoiceNext = {},
        onVoicePrevious = {},
        onVoiceFeedbackSkip = {},
        onDialogEvent = {},
    )
}
