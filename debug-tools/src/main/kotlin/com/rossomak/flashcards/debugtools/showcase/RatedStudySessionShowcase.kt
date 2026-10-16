@file:Suppress("MagicNumber")

package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFlashcards
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionContent
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionScreenState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The queue with [headId] moved to its head, which is the card on screen. */
private fun queueStartingAt(headId: String): List<Flashcard> =
    ShowcaseFlashcards.coroutines.sortedByDescending { it.id == headId }

/** A Coroutines session with three of its Flashcards done and the default three Attempts per card. */
private val baseState = RatedStudySessionScreenState(
    categoryName = ShowcaseTaxonomy.kotlin.name,
    subcategoryNameById = mapOf(ShowcaseTaxonomy.coroutines.id to ShowcaseTaxonomy.coroutines.name),
    completedCount = 3,
    distinctCardCount = ShowcaseFlashcards.coroutines.size,
)

private val questionState = baseState.copy(
    flashcards = queueStartingAt("coroutines-structured-concurrency"),
    currentCardRatings = listOf(FlashcardAttemptRating.Failed),
)

private val answerRevealedState = baseState.copy(
    flashcards = queueStartingAt("coroutines-cancellation-cooperative"),
    isAnswerRevealed = true,
)

/** Listening for a spoken answer, the microphone open. */
private val voiceAnsweringState = baseState.copy(
    flashcards = queueStartingAt("coroutines-exception-handler"),
    isVoiceAnsweringSession = true,
    isVoiceActive = true,
    isVoicePlaying = true,
    voiceAnswerPhase = VoiceAnswerPhase.Listening,
    isVoiceMicrophoneOpen = true,
    availableTransportCommands = setOf(TransportCommandType.Pause, TransportCommandType.Next, TransportCommandType.Previous),
)

/** A fixed mid-sentence input level, so the capture indicator has something to draw. */
private val speakingVoiceBars: StateFlow<ImmutableList<Float>> =
    MutableStateFlow(persistentListOf(0.35f, 0.6f, 0.85f, 0.55f, 0.7f, 0.4f, 0.65f))

private val silentVoiceBars: StateFlow<ImmutableList<Float>> = MutableStateFlow(persistentListOf())

@Composable
internal fun RatedStudySessionShowcase(showcaseState: RatedShowcaseState) {
    val state = when (showcaseState) {
        RatedShowcaseState.Question -> questionState
        RatedShowcaseState.AnswerRevealed -> answerRevealedState
        RatedShowcaseState.VoiceAnswering -> voiceAnsweringState
    }
    RatedStudySessionContent(
        state = state,
        voiceBarsLevels = if (showcaseState == RatedShowcaseState.VoiceAnswering) speakingVoiceBars else silentVoiceBars,
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
