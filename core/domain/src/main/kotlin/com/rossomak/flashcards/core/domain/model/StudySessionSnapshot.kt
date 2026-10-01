package com.rossomak.flashcards.core.domain.model

/**
 * Everything a Rated Study Session's screen reads, as its coordinator last published it. Only a
 * [Running] session has cards, counts or transport state.
 */
sealed interface RatedSessionStateSnapshot {

    /** The session's cards are still loading. */
    data object Loading : RatedSessionStateSnapshot

    /** The session's cards could not be loaded. */
    data object LoadFailed : RatedSessionStateSnapshot

    /**
     * A loaded session. Plain data: every rule behind these values lives on the reducer state, and
     * the coordinator copies the results in.
     *
     * @param cards the queue, current card first. The current card moves only at a queue sync, so a
     * spoken notice about it keeps it on screen until the notice finishes.
     * @param currentCardRatings the current card's attempt markers; a voice grade shows at once.
     * @param playback the voice player's transport state.
     * @param round the current Voice Answering round.
     * @param isShortNoticeSpeaking a notice other than [SpokenNotice.Feedback] is still being spoken.
     * @param isHeldAtAdvancePoint a requested hold stopped the session at the auto-advance point, on
     * the card it just finished.
     * @param isPausedWhileGrading the user paused while the answer was being graded.
     * @param isPausedAfterFeedback the session is paused on the graded card; play reads the
     * feedback again.
     * @param availableTransportCommands what the in-app row, the notification and a headset may
     * offer now. Empty while a call rings or runs, and once the session is complete.
     */
    data class Running(
        val cards: List<Flashcard> = emptyList(),
        val completedCount: Int = 0,
        val distinctCardCount: Int = 0,
        val currentCardRatings: List<FlashcardAttemptRating> = emptyList(),
        val isAnswerRevealed: Boolean = false,
        val playback: VoicePlaybackState = VoicePlaybackState(),
        val round: VoiceAnswerRound = VoiceAnswerRound(),
        val isShortNoticeSpeaking: Boolean = false,
        val voiceAnswerPauseReason: VoiceAnswerPauseReason? = null,
        val isHeldAtAdvancePoint: Boolean = false,
        val pauseReason: SessionPauseReason? = null,
        val isPausedWhileGrading: Boolean = false,
        val isPausedAfterFeedback: Boolean = false,
        val availableTransportCommands: Set<TransportCommandType> = emptySet(),
    ) : RatedSessionStateSnapshot
}

/**
 * Everything a Fast Study Session's screen reads, as its coordinator last published it. Only a
 * [Running] session has cards or transport state.
 */
sealed interface FastSessionStateSnapshot {

    /** The session's cards are still loading. */
    data object Loading : FastSessionStateSnapshot

    /** The session's cards could not be loaded. */
    data object LoadFailed : FastSessionStateSnapshot

    /**
     * A loaded session. Plain data, like [RatedSessionStateSnapshot.Running].
     *
     * @param currentIndex the presented card's index in [cards].
     * @param playback the voice player's transport state.
     * @param pauseReason who paused the session; `null` while nothing did.
     * @param isHeldAtAdvancePoint a requested hold stopped read-aloud at the auto-advance point, on
     * the card it just finished.
     * @param availableTransportCommands what the in-app row, the notification and a headset may
     * offer now. Empty while a call rings or runs.
     */
    data class Running(
        val cards: List<Flashcard> = emptyList(),
        val currentIndex: Int = 0,
        val isAnswerRevealed: Boolean = false,
        val playback: VoicePlaybackState = VoicePlaybackState(),
        val pauseReason: FastPauseReason? = null,
        val isHeldAtAdvancePoint: Boolean = false,
        val availableTransportCommands: Set<TransportCommandType> = TransportCommandType.entries.toSet(),
    ) : FastSessionStateSnapshot
}
