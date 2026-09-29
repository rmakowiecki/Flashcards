package com.rossomak.flashcards.core.domain.model

/**
 * A Rated Study Session's rules state: an immutable snapshot that `RatedSessionReducer` takes in and
 * returns the next one of. Entirely plain data, no behavior of its own.
 *
 * [queue] is reordered in place rather than being a fixed list with an advancing index: the current
 * card is always the head, and a non-terminal Rating removes it from the head and reinserts it
 * further down — the queue's size never changes on a reinsertion, only its order. A terminal
 * rating instead removes the card for good, shrinking it
 * ([ADR-0046](../../../../../../../docs/adr/0046-failed-and-partial-re-insertion-placement.md)). A
 * card whose Attempts are exhausted — or whose Rating already resolves it — leaves the queue for
 * good, resolved to a [FlashcardTerminalRating]
 * ([ADR-0044](../../../../../../../docs/adr/0044-three-valued-terminal-state.md)). [isComplete]
 * becomes `true` exactly when every distinct card has reached a Terminal State, which coincides
 * with the queue emptying.
 *
 * A Rating or requeue is recorded at once — the head carries the new Rating, and a resolved card
 * joins [terminalStates] — but the head only moves at a queue sync, as [pendingMove] says. For a
 * voice round the sync waits until the notice about the head has finished, so the card being talked
 * about stays the current card until then.
 *
 * @param queue the cards still due an Attempt, current card first.
 * @param terminalStates every distinct card that has left the queue, by id, paired with the record
 * it resolved from — [ResolvedRatedCard] keeps [RatedSessionCardRecord.attemptsUsed] and
 * [RatedSessionCardRecord.wasPreviouslyMastered] readable for [sealRatedCardResults] after the record
 * itself has left [queue].
 * @param distinctCardCount fixed at seed time — a re-insertion never changes how many distinct
 * cards there are, so this is carried on every copy rather than re-derived from [queue]'s length.
 * @param attemptsLimit how many Attempts a card gets before Attempts-exhausted resolution applies.
 * A limit of 1 is a legitimate strict setting, not a broken state: every Rating is then immediately
 * terminal and nothing is ever re-inserted.
 * @param partialRatingCardRequeueingEnabled the Preview screen's confirmed choice. `true` (the
 * default) lets a Partial Rating re-insert as normal; `false` resolves it to Terminal Partial on the
 * spot, never re-inserting.
 * @param isAnswerRevealed the current card's answer is showing. A queue sync hides it again.
 * @param isVoiceAnsweringSession fixed for the whole session: a session never changes its delivery
 * mode once it has started.
 * @param consecutiveSilenceCount silence timeouts in a row. A graded answer or a resume resets it.
 * @param consecutiveGradingFailureCount grading failures in a row, counted independently of
 * silences. A graded answer or a resume resets it.
 * @param round the current Voice Answering round.
 * @param speakingNotices the notices still being spoken, oldest first. A notice outlives any pause.
 * @param pendingMove how the head moves at the next queue sync; `null` while it stays.
 * @param voiceAnswerPauseReason why voice answering is paused; `null` while it is not.
 * @param pauseReason why the whole session is paused; `null` while it is not.
 * @param isPausedAtAdvancePoint the notice and its tail finished while playback was paused: the
 * next play reads the next question instead of re-reading the answered one.
 * @param isPlaying the voice player's playing state, as last reported.
 * @param isAdvanceHoldRequested while set, the session stops at the auto-advance point instead of
 * moving on.
 * @param isHeldAtAdvancePoint the notice and its tail finished with a hold requested: the session
 * stopped on the answered card, with its queue sync still pending. Not a user pause: releasing the
 * hold moves on and plays.
 * @param isPausedTemporarily playback is paused by a temporary pause, which plays again when it
 * ends; a user pause or play in between replaces it.
 * @param isPausedWhileGrading the user paused while the answer was being graded. Grading goes on;
 * a grade that arrives meanwhile is recorded without speaking its feedback.
 * @param isPausedAfterFeedback the user paused the grading feedback, or it arrived while paused: the
 * session waits on the graded card, before the auto-advance point. Play reads the feedback again
 * from the start; next moves on without playing.
 */
data class RatedSessionState(
    val queue: List<RatedSessionCardRecord>,
    val terminalStates: Map<String, ResolvedRatedCard> = emptyMap(),
    val distinctCardCount: Int = queue.size,
    val attemptsLimit: Int,
    val partialRatingCardRequeueingEnabled: Boolean = true,
    val isAnswerRevealed: Boolean = false,
    val isVoiceAnsweringSession: Boolean = false,
    val consecutiveSilenceCount: Int = 0,
    val consecutiveGradingFailureCount: Int = 0,
    val round: VoiceAnswerRound = VoiceAnswerRound(),
    val speakingNotices: List<SpokenNotice> = emptyList(),
    val pendingMove: QueueMove? = null,
    val voiceAnswerPauseReason: VoiceAnswerPauseReason? = null,
    val pauseReason: SessionPauseReason? = null,
    val isPausedAtAdvancePoint: Boolean = false,
    val isPlaying: Boolean = false,
    val isAdvanceHoldRequested: Boolean = false,
    val isHeldAtAdvancePoint: Boolean = false,
    val isPausedTemporarily: Boolean = false,
    val isPausedWhileGrading: Boolean = false,
    val isPausedAfterFeedback: Boolean = false,
) {
    /** How many distinct cards have resolved [FlashcardTerminalRating.Mastered] so far. */
    val masteredCount: Int get() = terminalStates.values.count { it.terminalState == FlashcardTerminalRating.Mastered }

    /** How many distinct cards have reached any [FlashcardTerminalRating] so far, any grade. */
    val completedCount: Int get() = terminalStates.size

    /**
     * `true` once every distinct card has reached a Terminal State and the last one has left the
     * queue at its sync.
     */
    val isComplete: Boolean get() = queue.isEmpty()

    /** A recorded Rating or requeue is waiting for a queue sync to move the head. */
    val isSyncPending: Boolean get() = pendingMove != null

    /** The card at the head of the queue — the one due for its next Attempt. `null` once complete. */
    val currentCard: Flashcard? get() = queue.firstOrNull()?.card

    /** The queue's cards in current order, current card first. */
    val remainingCards: List<Flashcard> get() = queue.map { it.card }

    /**
     * The current (head) card's own Rating history, in order — `FlashcardsAttemptIndicator`'s
     * source. Empty once [isComplete], since there is no head left.
     */
    val currentCardRatings: List<FlashcardAttemptRating> get() = queue.firstOrNull()?.ratings ?: emptyList()

    /** Voice answering is on for this session and nothing has paused it. */
    val isVoiceAnsweringActive: Boolean
        get() = isVoiceAnsweringSession && voiceAnswerPauseReason == null && pauseReason == null

    /**
     * The grading feedback is being read, or its tail is running: the round has a grade, and
     * nothing has paused or held it.
     */
    val isFeedbackPlaying: Boolean
        get() = isVoiceAnsweringActive &&
            round.phase == VoiceAnswerPhase.SpeakingNotice &&
            round.grade != null &&
            !isPausedAfterFeedback &&
            !isHeldAtAdvancePoint

    /** A notice other than the grading feedback is still being spoken. */
    val isShortNoticeSpeaking: Boolean get() = speakingNotices.any { it.isShort }
}

/** How the head of a [RatedSessionState.queue] moves at the next queue sync. */
sealed interface QueueMove {
    /** The head goes back into the queue [gap] places down, clamped to the queue's end. */
    data class Requeue(val gap: Int) : QueueMove

    /** The head has reached a Terminal State and leaves the queue for good. */
    data object Remove : QueueMove
}

/**
 * One Voice Answering round: the card it listens for, what was heard and how it was graded.
 *
 * @param cardId the card the listening window opened for; `null` before it opens.
 * @param isMicrophoneOpen the microphone is recording for this round's listening window; until
 * then the window is still being prepared.
 * @param hasUtterance an answer was captured and handed to grading.
 * @param transcript the sanitized transcript, as soon as it streams in, ahead of [grade].
 * @param gradingFailure why this round's grading failed; `null` unless it did.
 */
data class VoiceAnswerRound(
    val phase: VoiceAnswerPhase = VoiceAnswerPhase.Idle,
    val cardId: String? = null,
    val isMicrophoneOpen: Boolean = false,
    val hasUtterance: Boolean = false,
    val transcript: String? = null,
    val grade: VoiceAnswerGrade? = null,
    val gradingFailure: GradingFailureReason? = null,
)

/** Why voice answering is paused, while the rest of the session still runs. */
enum class VoiceAnswerPauseReason {
    /** Consecutive silence timeouts reached the threshold. */
    Silence,

    /** Consecutive grading failures reached the threshold. */
    GradingFailures,

    /** The microphone stopped working. */
    CaptureFailed,
}

/** Why a whole Study Session is paused. */
enum class SessionPauseReason {
    /**
     * A text-to-speech engine could not start. The session keeps its delivery mode and waits for
     * a resume, which restarts the voice stack.
     */
    VoiceEngineUnavailable,
}

/**
 * A card that has left [RatedSessionState.queue] for good, paired with the [RatedSessionCardRecord]
 * it resolved from — the record itself is discarded from the queue once terminal, so this is what
 * [sealRatedCardResults] reads [RatedSessionCardRecord.attemptsUsed] and
 * [RatedSessionCardRecord.wasPreviouslyMastered] from afterward.
 */
data class ResolvedRatedCard(val record: RatedSessionCardRecord, val terminalState: FlashcardTerminalRating)

/**
 * Seals [state] into [SessionResult]'s per-card [SessionResult.cardResults] — one [FlashcardResult]
 * per card with at least one completed Attempt, Rated's definition of Studied. A card never reached,
 * and a card that received only a silence timeout (zero Attempts either way), contributes nothing.
 *
 * A card already resolved to a [FlashcardTerminalRating] ([RatedSessionState.terminalStates]) carries that
 * outcome straight through. A card still mid re-insertion when [abandoned] is `true` — waiting for
 * its next draw, not yet terminal — is force-resolved from its best-rating-so-far via
 * [toAbandonedFlashcardTerminalRating]: the same [ADR-0044](../../../../../../../docs/adr/0044-three-valued-terminal-state.md)
 * table natural resolution uses, just not through the same function — there is no just-submitted
 * rating at abandon time, and the remaining queue's Attempts may be well under its limit. A head
 * already resolved but still waiting for its sync is counted once, from [RatedSessionState.terminalStates].
 */
fun sealRatedCardResults(state: RatedSessionState, abandoned: Boolean): List<FlashcardResult.Rated> {
    val resolvedEntries = state.terminalStates.values.map { resolved ->
        resolved.record.toFlashcardResult(resolved.terminalState.toFlashcardStudyProgressState())
    }
    if (!abandoned) return resolvedEntries

    val forcedEntries = state.queue
        .filter { it.attemptsUsed > 0 && it.card.id !in state.terminalStates }
        .map { record ->
            val bestRating = requireNotNull(record.bestRating) {
                "a record with attemptsUsed > 0 always has a bestRating"
            }
            record.toFlashcardResult(bestRating.toAbandonedFlashcardTerminalRating().toFlashcardStudyProgressState())
        }
    return resolvedEntries + forcedEntries
}

/**
 * [ADR-0044](../../../../../../../docs/adr/0044-three-valued-terminal-state.md)'s table, applied to
 * a best-rating-so-far at abandon time rather than a just-submitted rating — see [sealRatedCardResults].
 */
fun FlashcardAttemptRating.toAbandonedFlashcardTerminalRating(): FlashcardTerminalRating = when (this) {
    FlashcardAttemptRating.Correct -> FlashcardTerminalRating.Mastered
    FlashcardAttemptRating.PartiallyCorrect -> FlashcardTerminalRating.Partial
    FlashcardAttemptRating.Failed -> FlashcardTerminalRating.Failed
}

private fun RatedSessionCardRecord.toFlashcardResult(state: FlashcardStudyProgressState): FlashcardResult.Rated =
    FlashcardResult.Rated(
        cardId = card.id,
        subcategoryId = card.subcategoryId,
        state = state,
        attemptsUsed = attemptsUsed,
        wasPreviouslyMastered = wasPreviouslyMastered,
    )
