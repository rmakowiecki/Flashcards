@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.AnswerRevealed
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.Question
import com.rossomak.flashcards.debugtools.showcase.RatedShowcaseState.VoiceAnswering
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

private val silentVoiceBars: StateFlow<ImmutableList<Float>> = MutableStateFlow(persistentListOf())

/** A Functions session three cards in. The card on screen is the first one that suits [showcaseState]. */
private fun ShowcaseFixtures.ratedState(showcaseState: RatedShowcaseState): RatedStudySessionScreenState {
    val isOnScreen: (Flashcard) -> Boolean = when (showcaseState) {
        Question -> { card -> card.questionCode != null }
        AnswerRevealed -> { card -> card.answerCode != null && card.questionCode == null }
        VoiceAnswering -> { card -> card.questionCode == null && card.answerCode == null }
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
