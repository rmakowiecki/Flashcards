package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFlashcards
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseTaxonomy
import com.rossomak.flashcards.feature.study.fast.FastStudySessionActions
import com.rossomak.flashcards.feature.study.fast.FastStudySessionContent
import com.rossomak.flashcards.feature.study.fast.FastStudySessionScreenState

/** A Compose session on its second card. */
private val baseState = FastStudySessionScreenState(
    categoryName = ShowcaseTaxonomy.android.name,
    subcategoryNameById = mapOf(ShowcaseTaxonomy.compose.id to ShowcaseTaxonomy.compose.name),
    flashcards = ShowcaseFlashcards.compose,
    currentCardIndex = 1,
)

/** Tapping through, the answer shown, so the panel offers Next. */
private val tapThroughState = baseState.copy(isAnswerRevealed = true)

/** Read-aloud playing the question. The showcase speaks nothing: no voice engine is bound. */
private val readAloudState = baseState.copy(
    isReadAloudMode = true,
    isVoiceActive = true,
    isVoicePlaying = true,
)

private val noOpActions = FastStudySessionActions(
    onShowAnswer = {},
    onNextCard = {},
    onVoicePlayPause = {},
    onVoiceNext = {},
    onVoicePrevious = {},
    onDialogEvent = {},
)

@Composable
internal fun FastStudySessionShowcase(isReadAloud: Boolean) {
    FastStudySessionContent(
        state = if (isReadAloud) readAloudState else tapThroughState,
        snackbarHostState = remember { SnackbarHostState() },
        actions = noOpActions,
    )
}
