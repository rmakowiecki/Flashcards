package com.rossomak.flashcards.presentation.debug

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rossomak.flashcards.core.ui.dialog.DialogEvent.Open
import com.rossomak.flashcards.feature.study.chrome.LocalStudySessionCardMotion
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.CurrentCardExtendedContext
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ExitSession
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.ReportCurrentCardProblem
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog.SessionVoiceSettings
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialogEvent
import com.rossomak.flashcards.feature.study.fast.FastStudySessionActions
import com.rossomak.flashcards.feature.study.fast.FastStudySessionContent
import com.rossomak.flashcards.feature.study.fast.FastStudySessionScreenState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private const val SIMULATED_LOADING_MS = 1500
private const val AUTO_PLAY_STEP_MS = 2500

/** The session screen, and so the bottom sheet, the harness renders. */
internal enum class CardAnimationsDebugSheet(val label: String) {
    FastReadAloud("Fast read-aloud"),
    FastTapThrough("Fast tap-through"),
    RatedManual("Rated manual"),
}

/** Process-lifetime settings, so a tuned setup survives reopening the harness. */
internal object CardAnimationsDebugSettings {
    var motion by mutableStateOf(CardAnimationsDebugMotion())
    var sheet by mutableStateOf(CardAnimationsDebugSheet.FastReadAloud)
}

/** Real session content over the debug deck; the header's flag icon opens live motion settings. */
@Composable
fun CardAnimationsDebugScreen(onNavigateBack: () -> Unit) {
    var isSettingsOpen by remember { mutableStateOf(false) }
    var reloadRequest by remember { mutableIntStateOf(0) }
    var jumpRequest by remember { mutableStateOf<Int?>(null) }
    var currentCardIndex by remember { mutableIntStateOf(0) }
    val sheet = CardAnimationsDebugSettings.sheet
    val cardMotion = remember(CardAnimationsDebugSettings.motion) { CardAnimationsDebugSettings.motion.toCardMotion() }

    BackHandler { onNavigateBack() }

    val harness = CardAnimationsDebugHarness(
        reloadRequest = reloadRequest,
        jumpRequest = jumpRequest,
        onCurrentCardIndexChange = { currentCardIndex = it },
        onJumpHandled = { jumpRequest = null },
        onSettingsOpen = { isSettingsOpen = true },
        onNavigateBack = onNavigateBack,
    )

    CompositionLocalProvider(LocalStudySessionCardMotion provides cardMotion) {
        when (sheet) {
            CardAnimationsDebugSheet.FastReadAloud -> FastHarness(harness = harness, isReadAloud = true)
            CardAnimationsDebugSheet.FastTapThrough -> FastHarness(harness = harness, isReadAloud = false)
            CardAnimationsDebugSheet.RatedManual -> RatedManualHarness(harness = harness)
        }
    }

    if (isSettingsOpen) {
        CardAnimationsDebugSettingsSheet(
            motion = CardAnimationsDebugSettings.motion,
            sheet = sheet,
            cardLabels = debugCards.map { debugCardLabels.getValue(it.id) },
            currentCardIndex = currentCardIndex,
            onMotionChange = { CardAnimationsDebugSettings.motion = it },
            onSheetChange = { CardAnimationsDebugSettings.sheet = it },
            onReload = {
                isSettingsOpen = false
                reloadRequest++
            },
            onJump = { index ->
                isSettingsOpen = false
                jumpRequest = index
            },
            onDismiss = { isSettingsOpen = false },
        )
    }
}

/** What every harness variant shares with the screen around it. */
internal class CardAnimationsDebugHarness(
    val reloadRequest: Int,
    val jumpRequest: Int?,
    val onCurrentCardIndexChange: (Int) -> Unit,
    val onJumpHandled: () -> Unit,
    val onSettingsOpen: () -> Unit,
    val onNavigateBack: () -> Unit,
) {
    /** Exit leaves; report and voice settings open the settings sheet. */
    fun onDialogEvent(event: StudySessionDialogEvent, onActiveDialogChange: (StudySessionDialog?) -> Unit) {
        if (event is Open) {
            when (val dialog = event.dialog) {
                ExitSession -> onNavigateBack()
                is ReportCurrentCardProblem, is SessionVoiceSettings -> onSettingsOpen()
                is CurrentCardExtendedContext -> onActiveDialogChange(dialog)
            }
        } else {
            onActiveDialogChange(null)
        }
    }
}

/** Real Fast content. Read-aloud previous hides a revealed answer first, like the real card restart. */
@Composable
private fun FastHarness(harness: CardAnimationsDebugHarness, isReadAloud: Boolean) {
    var state by remember(isReadAloud) {
        mutableStateOf(
            FastStudySessionScreenState(
                categoryName = "Debug",
                subcategoryNameById = mapOf(DEBUG_SUBCATEGORY_ID to "Card animations"),
                flashcards = debugCards,
                isReadAloudMode = isReadAloud,
                isVoiceActive = isReadAloud,
            ),
        )
    }

    fun reveal() {
        state = state.copy(isAnswerRevealed = true)
    }

    fun goTo(index: Int) {
        state = state.copy(currentCardIndex = index.coerceIn(0, state.flashcards.lastIndex), isAnswerRevealed = false)
    }

    fun next() {
        if (state.isAnswerRevealed) goTo(state.currentCardIndex + 1) else reveal()
    }

    fun previous() {
        if (state.isAnswerRevealed) state = state.copy(isAnswerRevealed = false) else goTo(state.currentCardIndex - 1)
    }

    LaunchedEffect(state.currentCardIndex) { harness.onCurrentCardIndexChange(state.currentCardIndex) }

    LaunchedEffect(harness.jumpRequest) {
        val index = harness.jumpRequest ?: return@LaunchedEffect
        goTo(index)
        harness.onJumpHandled()
    }

    LaunchedEffect(harness.reloadRequest) {
        if (harness.reloadRequest == 0) return@LaunchedEffect
        state = state.copy(isLoading = true, currentCardIndex = 0, isAnswerRevealed = false, isVoicePlaying = false)
        delay(SIMULATED_LOADING_MS.milliseconds)
        state = state.copy(isLoading = false)
    }

    // Stands in for hands-free reading: reveal, then advance, one step per tick while playing.
    LaunchedEffect(state.isReadAloudMode, state.isVoicePlaying, state.currentCardIndex, state.isAnswerRevealed) {
        if (!state.isReadAloudMode || !state.isVoicePlaying) return@LaunchedEffect
        delay(AUTO_PLAY_STEP_MS.milliseconds)
        if (state.isAnswerRevealed && state.currentCardIndex == state.flashcards.lastIndex) {
            state = state.copy(isVoicePlaying = false)
        } else {
            next()
        }
    }

    FastStudySessionContent(
        state = state,
        snackbarHostState = remember { SnackbarHostState() },
        actions = FastStudySessionActions(
            onShowAnswer = ::reveal,
            onNextCard = { goTo(state.currentCardIndex + 1) },
            onVoicePlayPause = { state = state.copy(isVoicePlaying = !state.isVoicePlaying) },
            onVoiceNext = ::next,
            onVoicePrevious = ::previous,
            onDialogEvent = { event -> harness.onDialogEvent(event) { state = state.copy(activeDialog = it) } },
        ),
    )
}
