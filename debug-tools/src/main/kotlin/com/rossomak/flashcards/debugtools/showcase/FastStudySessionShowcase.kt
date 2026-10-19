package com.rossomak.flashcards.debugtools.showcase

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rossomak.flashcards.debugtools.showcase.fixtures.LocalShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.ShowcaseFixtures
import com.rossomak.flashcards.debugtools.showcase.fixtures.android
import com.rossomak.flashcards.debugtools.showcase.fixtures.compose
import com.rossomak.flashcards.feature.study.fast.FastStudySessionActions
import com.rossomak.flashcards.feature.study.fast.FastStudySessionContent
import com.rossomak.flashcards.feature.study.fast.FastStudySessionScreenState

private val noOpActions = FastStudySessionActions(
    onShowAnswer = {},
    onNextCard = {},
    onVoicePlayPause = {},
    onVoiceNext = {},
    onVoicePrevious = {},
    onDialogEvent = {},
)

/** A Compose session on its second card: answer shown when tapping through, question playing when read aloud. */
private fun ShowcaseFixtures.fastState(isReadAloud: Boolean) = FastStudySessionScreenState(
    categoryName = android.name,
    subcategoryNameById = mapOf(compose.id to compose.name),
    flashcards = flashcardsOf(compose.id),
    currentCardIndex = 1,
    isAnswerRevealed = !isReadAloud,
    isReadAloudMode = isReadAloud,
    isVoiceActive = isReadAloud,
    isVoicePlaying = isReadAloud,
)

@Composable
internal fun FastStudySessionShowcase(isReadAloud: Boolean) {
    val fixtures = LocalShowcaseFixtures.current
    FastStudySessionContent(
        state = remember(fixtures, isReadAloud) { fixtures.fastState(isReadAloud) },
        snackbarHostState = remember { SnackbarHostState() },
        actions = noOpActions,
    )
}
