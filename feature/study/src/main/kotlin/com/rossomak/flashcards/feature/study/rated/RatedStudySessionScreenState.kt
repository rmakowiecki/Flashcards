package com.rossomak.flashcards.feature.study.rated

import com.rossomak.flashcards.core.domain.model.Flashcard
import com.rossomak.flashcards.core.domain.model.FlashcardAttemptRating
import com.rossomak.flashcards.core.domain.model.StudySessionConfig
import com.rossomak.flashcards.core.domain.model.TransportCommandType
import com.rossomak.flashcards.core.domain.model.VoiceAnswerGrade
import com.rossomak.flashcards.core.domain.model.VoiceAnswerPhase
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptIndicator
import com.rossomak.flashcards.core.ui.composables.FlashcardsAttemptSlotState
import com.rossomak.flashcards.feature.study.chrome.StudySessionDialog

/**
 * Everything a Rated Study Session screen renders. No Study Mode field — the type itself is the
 * mode
 * ([ADR-0045](../../../../../../../../docs/adr/0045-separate-fast-and-rated-session-screens.md)).
 * The presented card is always the queue's head, the first of [flashcards].
 *
 * Deliberately duplicates the shape of `FastStudySessionScreenState` rather than sharing a base
 * type with it: this screen carries a completed-out-of-distinct counter, the current card's Rating
 * history and the Voice Answering round, none of which Fast has, and a shared base would need a
 * `when` on mode to stay useful — exactly the branching this split exists to remove.
 */
data class RatedStudySessionScreenState(
    val categoryName: String = "",
    // Keyed by Flashcard.subcategoryId — resolves the current card's own subcategory name for the
    // header title (studySessionCardTitle), since a composite session's cards can each belong to
    // a different one.
    val subcategoryNameById: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val flashcards: List<Flashcard> = emptyList(),
    val isAnswerRevealed: Boolean = false,
    // Routed at session start (RatedStudySessionRoute.voiceAnsweringEnabled) — known synchronously,
    // unlike isVoiceActive below, which only flips once the voice engine finishes binding. The
    // sheet must never show the manual-mode perspective for a voice session, even for the moment
    // between entry and that bind completing, so it branches on this flag instead of isVoiceActive.
    val isVoiceAnsweringSession: Boolean = false,
    val isVoiceActive: Boolean = false,
    val isVoicePlaying: Boolean = false,
    val voiceAnswerPhase: VoiceAnswerPhase = VoiceAnswerPhase.Idle,
    // The listening window's microphone records; until then the window is still being prepared.
    val isVoiceMicrophoneOpen: Boolean = false,
    val voiceAnswerSanitizedTranscript: String? = null,
    // Continuous display state, not a one-shot event: mirrors the current round's grade for as
    // long as VoiceAnswerPhase.SpeakingNotice is reading it aloud, rendered in the bottom sheet
    // (RatedVoiceSheetMode.Graded) rather than as a snackbar. One-shot voice-answering failures go
    // through RatedStudySessionMessage instead — see RatedStudySessionViewModel.messages.
    val lastVoiceAnswerGrade: VoiceAnswerGrade? = null,
    // A short notice is being spoken: outlives the pause a short notice can trigger, so the sheet
    // stays on its status disc until the notice has actually finished.
    val isVoiceShortNoticeSpeaking: Boolean = false,
    val activeDialog: StudySessionDialog? = null,
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
    // Voice answering is paused (repeated silences or grading failures, a capture failure) or the
    // whole session is, after a voice engine failure: playback and the microphone are stopped and
    // only the resume affordance is live. Distinct from the transient Listening/SpeechDetected/
    // Grading/SpeakingNotice disable windows — those stay load-bearing and unchanged by this flag.
    val isVoiceAnswerPaused: Boolean = false,
    // A voice engine could not start. The session keeps its voice sheet, never falling back to the
    // manual one, and the play control stays enabled to resume.
    val isVoiceEngineUnavailable: Boolean = false,
    // The voice round is paused mid-way: while grading, after the grading feedback, or held at the
    // auto-advance point. The sheet shows the paused transport row, whatever the phase.
    val isVoiceRoundPaused: Boolean = false,
    // What the transport row may offer: the same set the notification and a headset get.
    val availableTransportCommands: Set<TransportCommandType> = emptySet(),
) {
    val currentCard: Flashcard? get() = flashcards.firstOrNull()

    val voiceSheetMode: RatedVoiceSheetMode
        get() = voiceSheetModeOf(
            voiceAnswerPhase = voiceAnswerPhase,
            isMicrophoneOpen = isVoiceMicrophoneOpen,
            isVoiceAnswerPaused = isVoiceAnswerPaused || isVoiceRoundPaused,
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
