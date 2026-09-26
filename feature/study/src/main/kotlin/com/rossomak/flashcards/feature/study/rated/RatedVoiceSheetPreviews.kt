package com.rossomak.flashcards.feature.study.rated

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.ui.composables.voice.FlashcardsVoiceCaptureIndicatorDefaults
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

private val PreviewVoiceBarsLevels: ImmutableList<Float> = persistentListOf(0.9f, 0.7f, 0.5f, 0.3f, 0.15f)

@Composable
private fun RatedStudySessionVoicePreview(state: RatedStudySessionScreenState, voiceBarsLevels: ImmutableList<Float> = FlashcardsVoiceCaptureIndicatorDefaults.restLevels) {
    RatedStudySessionContent(
        state = state.copy(
            categoryName = "Android",
            subcategoryNameById = mapOf("compose" to "Compose"),
            isVoiceMode = true,
            isVoiceActive = true,
            isVoiceAnswerEnabled = true,
        ),
        voiceBarsLevels = remember { MutableStateFlow(voiceBarsLevels) },
        snackbarHostState = remember { SnackbarHostState() },
        onShowAnswer = {},
        onAttemptRating = {},
        onVoicePlayPause = {},
        onVoiceNext = {},
        onVoicePrevious = {},
        onDialogEvent = {},
    )
}

@Preview
@Composable
private fun RatedStudySessionVoiceTransportPreview() {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(
            isVoicePlaying = true,
            voiceAnswerPhase = VoiceAnswerPhase.WaitingForQuestion,
        ),
    )
}

@Preview
@Composable
private fun RatedStudySessionVoiceTransportPausedPreview() {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(
            isVoicePlaying = false,
            voiceAnswerPhase = VoiceAnswerPhase.Idle,
            isVoiceAnswerPaused = true,
        ),
    )
}

@Preview
@Composable
private fun RatedStudySessionVoiceListeningPreview() {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(
            isVoicePlaying = true,
            voiceAnswerPhase = VoiceAnswerPhase.SpeechDetected,
        ),
        voiceBarsLevels = PreviewVoiceBarsLevels,
    )
}

private const val PREVIEW_SHORT_TRANSCRIPT = "remember keeps state across recompositions only."
private const val PREVIEW_LONG_TRANSCRIPT = "remember keeps a value across recompositions, but it is lost when the activity is " +
    "recreated, for example on rotation. rememberSaveable stores the value in the saved instance state bundle, so it " +
    "survives configuration changes and even process death, as long as the type can be saved into a Bundle or has a " +
    "custom Saver. That is why text field input usually goes into rememberSaveable."
private const val PREVIEW_SHORT_RATIONALE = "You named the key difference."
private const val PREVIEW_LONG_RATIONALE = "You explained that remember only survives recomposition and that " +
    "rememberSaveable also survives configuration changes, which is the core of the answer. You did not mention " +
    "process death or that rememberSaveable needs a Bundle-compatible type or a custom Saver, so the answer is not " +
    "complete. Mentioning where the value is stored would also have made the distinction clearer."
private const val PREVIEW_CORRECT_PERCENT = 90
private const val PREVIEW_PARTIAL_PERCENT = 60
private const val PREVIEW_FAILED_PERCENT = 20

@Composable
private fun RatedStudySessionVoiceGradedPreview(gradePercent: Int, rationale: String) {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(
            voiceAnswerPhase = VoiceAnswerPhase.SpeakingNotice,
            lastVoiceAnswerGrade = VoiceAnswerGrade(sanitizedTranscript = PREVIEW_SHORT_TRANSCRIPT, gradePercent = gradePercent, feedback = rationale),
        ),
    )
}

@Preview
@Composable
private fun RatedStudySessionVoicePendingPreview() {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(voiceAnswerPhase = VoiceAnswerPhase.Grading),
    )
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradingShortPreview() {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(
            voiceAnswerPhase = VoiceAnswerPhase.Grading,
            voiceAnswerSanitizedTranscript = PREVIEW_SHORT_TRANSCRIPT,
        ),
    )
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradingLongPreview() {
    RatedStudySessionVoicePreview(
        state = RatedStudySessionScreenState(
            voiceAnswerPhase = VoiceAnswerPhase.Grading,
            voiceAnswerSanitizedTranscript = PREVIEW_LONG_TRANSCRIPT,
        ),
    )
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradedCorrectPreview() {
    RatedStudySessionVoiceGradedPreview(gradePercent = PREVIEW_CORRECT_PERCENT, rationale = PREVIEW_SHORT_RATIONALE)
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradedPartialPreview() {
    RatedStudySessionVoiceGradedPreview(gradePercent = PREVIEW_PARTIAL_PERCENT, rationale = PREVIEW_SHORT_RATIONALE)
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradedFailedPreview() {
    RatedStudySessionVoiceGradedPreview(gradePercent = PREVIEW_FAILED_PERCENT, rationale = PREVIEW_SHORT_RATIONALE)
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradedCorrectLongPreview() {
    RatedStudySessionVoiceGradedPreview(gradePercent = PREVIEW_CORRECT_PERCENT, rationale = PREVIEW_LONG_RATIONALE)
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradedPartialLongPreview() {
    RatedStudySessionVoiceGradedPreview(gradePercent = PREVIEW_PARTIAL_PERCENT, rationale = PREVIEW_LONG_RATIONALE)
}

@Preview
@Composable
private fun RatedStudySessionVoiceGradedFailedLongPreview() {
    RatedStudySessionVoiceGradedPreview(gradePercent = PREVIEW_FAILED_PERCENT, rationale = PREVIEW_LONG_RATIONALE)
}
