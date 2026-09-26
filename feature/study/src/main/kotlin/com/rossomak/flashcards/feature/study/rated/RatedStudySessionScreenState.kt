package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog
import com.rossomak.flashcards.feature.study.voice.VoiceAnswerPhase
import com.rossomak.flashcards.feature.study.voice.VoicePlaybackState

/**
 * Everything a Rated Study Session screen renders. No Study Mode field — the type itself is the
 * mode
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)),
 * and no voice-auto-start-pending flag — that is Fast's; Rated never auto-starts playback, only
 * voice answering switches the gateway on.
 *
 * Deliberately duplicates the shape of `FastStudySessionScreenState` rather than sharing a base
 * type with it: this screen carries a mastered-out-of-distinct counter and a per-card Rating ledger
 * that Fast has no concept of, and a shared
 * base would need a `when` on mode to stay useful — exactly the branching this split exists to
 * remove.
 */
data class RatedStudySessionScreenState(
    val categoryName: String = "",
    // Keyed by Flashcard.subcategoryId — resolves the current card's own subcategory name for the
    // header title (studySessionCardTitle), since a composite session's cards can each belong to
    // a different one.
    val subcategoryNameById: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val flashcards: List<Flashcard> = emptyList(),
    val currentCardIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val error: String? = null,
    // Routed at session start (RatedStudySessionRoute.voiceAnsweringEnabled) — known synchronously,
    // unlike isVoiceActive below, which only flips once the voice engine finishes binding. The
    // sheet must never show the manual-mode perspective for a voice session, even for the moment
    // between entry and that bind completing, so it branches on this flag instead of isVoiceActive.
    val isVoiceMode: Boolean = false,
    val isVoiceActive: Boolean = false,
    val isVoicePlaying: Boolean = false,
    val speechRate: Float = VoicePlaybackState.DEFAULT_SPEECH_RATE,
    val isVoiceAnswerEnabled: Boolean = false,
    val voiceAnswerPhase: VoiceAnswerPhase = VoiceAnswerPhase.Idle,
    val voiceAnswerSanitizedTranscript: String? = null,
    // Continuous display state, not a one-shot event: mirrors the current round's grade for as
    // long as VoiceAnswerPhase.SpeakingNotice is reading it aloud, rendered in the bottom sheet
    // (RatedVoiceSheetMode.Graded) rather than as a snackbar. One-shot voice-answering failures go
    // through RatedStudySessionMessage instead — see RatedStudySessionViewModel.messages.
    val lastVoiceAnswerGrade: VoiceAnswerGrade? = null,
    // Mirrors VoiceAnswerState.isShortNoticeSpeaking: outlives the pause a short notice can trigger,
    // so the sheet stays on its status disc until the notice has actually finished.
    val isVoiceShortNoticeSpeaking: Boolean = false,
    // This round's grading failed: its notice keeps the card answer revealed, which grading already did.
    val isVoiceAnswerGradingFailed: Boolean = false,
    val activeDialog: StudySessionDialog? = null,
    // Mirrors RatedSessionState.masteredCount. No longer the header's counter (see completedCount
    // below) — kept for the Session Summary screen's own mastered tally.
    val masteredCount: Int = 0,
    // Mirrors RatedSessionState.completedCount; the "completed" half of the top bar's counter —
    // every distinct card that has reached a Terminal State so far, any grade.
    val completedCount: Int = 0,
    // Mirrors RatedSessionState.distinctCardCount — the counter's "of" half. Fixed at session
    // start; unlike completedCount, a re-queue never moves it.
    val distinctCardCount: Int = 0,
    // Mirrors RatedSessionState.currentCardRatings — the current (head) card's own Rating history,
    // source for the Attempt indicator's slots below.
    val currentCardRatings: List<FlashcardAttemptRating> = emptyList(),
    // The routed Attempts limit (RatedStudySessionRoute.ratedAttempts): the Attempt indicator's
    // total slot count, independent of how many attempts this card has used so far.
    val attemptsLimit: Int = StudySessionConfig.DEFAULT_RATED_ATTEMPTS,
    // Three consecutive silence timeouts:
    // playback and the microphone are stopped and only the resume affordance is live. Distinct
    // from the transient Listening/SpeechDetected/Grading/SpeakingNotice disable windows below —
    // those stay load-bearing and unchanged by this flag.
    val isVoiceAnswerPaused: Boolean = false,
) {
    val currentCard: Flashcard? get() = flashcards.getOrNull(currentCardIndex)

    val voiceSheetMode: RatedVoiceSheetMode
        get() = voiceSheetModeOf(
            isVoiceAnswerEnabled = isVoiceAnswerEnabled,
            voiceAnswerPhase = voiceAnswerPhase,
            isVoiceAnswerPaused = isVoiceAnswerPaused,
            isShortNoticeSpeaking = isVoiceShortNoticeSpeaking,
            sanitizedTranscript = voiceAnswerSanitizedTranscript,
            lastGrade = lastVoiceAnswerGrade,
        )

    /**
     * The current card's Rating history mapped to [FlashcardsAttemptIndicator] slots: one filled
     * slot per past Attempt in order, one [FlashcardsAttemptSlotState.Current] slot, and the rest
     * [FlashcardsAttemptSlotState.Future] — always [attemptsLimit] slots in total.
     */
    val attemptSlots: List<FlashcardsAttemptSlotState>
        get() {
            val pastSlots = currentCardRatings.map { it.toAttemptSlotState() }
            val remainingSlots = (attemptsLimit - pastSlots.size).coerceAtLeast(0)
            if (remainingSlots == 0) return pastSlots
            val futureSlots = List(remainingSlots - 1) { FlashcardsAttemptSlotState.Future }
            return pastSlots + FlashcardsAttemptSlotState.Current + futureSlots
        }
}

private fun FlashcardAttemptRating.toAttemptSlotState(): FlashcardsAttemptSlotState = when (this) {
    FlashcardAttemptRating.Failed -> FlashcardsAttemptSlotState.Failed
    FlashcardAttemptRating.PartiallyCorrect -> FlashcardsAttemptSlotState.Partial
    FlashcardAttemptRating.Correct -> FlashcardsAttemptSlotState.Correct
}
