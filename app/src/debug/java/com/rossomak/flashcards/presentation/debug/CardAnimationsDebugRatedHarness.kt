package com.rossomak.flashcards.presentation.debug

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.session.MANUAL_RATING_HOLD
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionContent
import com.rossomak.flashcards.feature.study.rated.RatedStudySessionScreenState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val SIMULATED_LOADING_MS = 1500
private val FAILED_REQUEUE_DISTANCE = 2..4
private val PARTIAL_REQUEUE_DISTANCE = 5..9

/** Real Rated manual content over a simulated queue with real re-insertion. An empty queue or a jump restarts the deck. */
@Composable
internal fun RatedManualHarness(harness: CardAnimationsDebugHarness) {
    var state by remember { mutableStateOf(ratedStateFrom(debugCards)) }
    var ratingsByCardId by remember { mutableStateOf(emptyMap<String, List<FlashcardAttemptRating>>()) }
    val voiceBarsLevels = remember { MutableStateFlow<ImmutableList<Float>>(persistentListOf()) }
    val holdScope = rememberCoroutineScope()
    var isHoldingRating by remember { mutableStateOf(false) }

    fun restartFrom(index: Int) {
        ratingsByCardId = emptyMap()
        state = ratedStateFrom(debugCards.drop(index) + debugCards.take(index)).copy(activeDialog = state.activeDialog)
    }

    fun moveOn(card: Flashcard, rating: FlashcardAttemptRating, ratings: List<FlashcardAttemptRating>) {
        // A jump or reload during the hold already replaced the deck.
        if (state.currentCard?.id != card.id) return
        val isFinished = rating == FlashcardAttemptRating.Correct || ratings.size >= state.attemptsLimit
        val queue = state.flashcards.drop(1).toMutableList()
        if (!isFinished) {
            val distance = if (rating == FlashcardAttemptRating.Failed) FAILED_REQUEUE_DISTANCE.random() else PARTIAL_REQUEUE_DISTANCE.random()
            queue.add((distance - 1).coerceAtMost(queue.size), card)
        }
        if (queue.isEmpty()) {
            restartFrom(0)
            return
        }
        state = state.copy(
            flashcards = queue,
            isAnswerRevealed = false,
            completedCount = state.completedCount + if (isFinished) 1 else 0,
            currentCardRatings = ratingsByCardId[queue.first().id].orEmpty(),
        )
    }

    // Mirrors the session: the Rating shows on the card for the hold, then the queue moves on.
    fun rate(rating: FlashcardAttemptRating) {
        val card = state.currentCard ?: return
        if (isHoldingRating) return
        val ratings = ratingsByCardId[card.id].orEmpty() + rating
        ratingsByCardId = ratingsByCardId + (card.id to ratings)
        state = state.copy(currentCardRatings = ratings)
        isHoldingRating = true
        holdScope.launch {
            delay(MANUAL_RATING_HOLD)
            isHoldingRating = false
            moveOn(card, rating, ratings)
        }
    }

    LaunchedEffect(state.currentCard) {
        val index = debugCards.indexOfFirst { it.id == state.currentCard?.id }
        if (index >= 0) harness.onCurrentCardIndexChange(index)
    }

    LaunchedEffect(harness.jumpRequest) {
        val index = harness.jumpRequest ?: return@LaunchedEffect
        restartFrom(index)
        harness.onJumpHandled()
    }

    LaunchedEffect(harness.reloadRequest) {
        if (harness.reloadRequest == 0) return@LaunchedEffect
        restartFrom(0)
        state = state.copy(isLoading = true)
        delay(SIMULATED_LOADING_MS.milliseconds)
        state = state.copy(isLoading = false)
    }

    RatedStudySessionContent(
        state = state,
        voiceBarsLevels = voiceBarsLevels,
        snackbarHostState = remember { SnackbarHostState() },
        onShowAnswer = { state = state.copy(isAnswerRevealed = true) },
        onAttemptRating = ::rate,
        onVoicePlayPause = {},
        onVoiceNext = {},
        onVoicePrevious = {},
        onVoiceFeedbackSkip = {},
        onDialogEvent = { event -> harness.onDialogEvent(event) { state = state.copy(activeDialog = it) } },
    )
}

private fun ratedStateFrom(queue: List<Flashcard>) = RatedStudySessionScreenState(
    categoryName = "Debug",
    subcategoryNameById = mapOf(DEBUG_SUBCATEGORY_ID to "Card animations"),
    flashcards = queue,
    distinctCardCount = queue.size,
)
