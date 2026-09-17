package com.rossomak.flashcards.core.domain.model

import com.rossomak.flashcards.core.domain.annotation.ArchConventionExempt
import kotlin.random.Random

/**
 * A Rated Study Session's queue and per-card grading — an immutable snapshot. Entirely plain data,
 * no behavior of its own: [rate] and [requeueAfterSilence] are top-level pure functions that take a
 * snapshot in and return the next one, so the Rated ViewModel holds a `var` and reassigns it,
 * mapping each new snapshot into screen state.
 *
 * [queue] is reordered in place rather than being a fixed list with an advancing index: the current
 * card is always the head, and a non-terminal [rate] removes it from the head and reinserts it
 * further down — the queue's size never changes on a reinsertion, only its order. A terminal
 * rating instead removes the card for good, shrinking it
 * ([ADR-0046](../../../../../../../docs/adr/0046-failed-and-partial-re-insertion-placement.md)). A
 * card whose Attempts are exhausted — or whose Rating already resolves it — leaves the queue for
 * good, resolved to a [FlashcardTerminalRating]
 * ([ADR-0044](../../../../../../../docs/adr/0044-three-valued-terminal-state.md)). [isComplete]
 * becomes `true` exactly when every distinct card has reached a Terminal State, which coincides
 * with the queue emptying.
 *
 * Build the first snapshot with [seed] rather than this primary constructor directly — [seed] is
 * what turns a routed card list into one [RatedSessionCardRecord] per card. The primary
 * constructor's shape exists so [rate]/[requeueAfterSilence] can build the next snapshot with
 * [copy].
 *
 * @param queue the cards still due an Attempt, current card first.
 * @param terminalStates every distinct card that has left the queue, by id, paired with the record
 * it resolved from — [ResolvedRatedCard] keeps [RatedSessionCardRecord.attemptsUsed] and
 * [RatedSessionCardRecord.wasPreviouslyMastered] readable for [sealRatedCardResults] after the record
 * itself has left [queue].
 * @param distinctCardCount fixed at [seed] time — a re-insertion never changes how many distinct
 * cards there are, so this is carried on every [copy] rather than re-derived from [queue]'s length.
 * @param attemptsLimit how many Attempts a card gets before Attempts-exhausted resolution applies.
 * A limit of 1 is a legitimate strict setting, not a broken state: every Rating is then immediately
 * terminal and nothing is ever re-inserted.
 * @param partialRatingCardRequeueingEnabled the Preview screen's confirmed choice. `true` (the
 * default) lets a Partial Rating re-insert as normal; `false` resolves it to Terminal Partial on the
 * spot, never re-inserting.
 * @param random injected so tests can assert a full queue sequence with a fixed seed. Production
 * passes nothing — there is no session seed anywhere in the app, and none is to be added. Its own
 * internal state still advances on every draw, but it's session-scoped configuration like
 * [attemptsLimit], not itself a Rating input, so it stays a field rather than a parameter every
 * reducer call has to thread through.
 */
data class RatedSessionState(
    val queue: List<RatedSessionCardRecord>,
    val terminalStates: Map<String, ResolvedRatedCard> = emptyMap(),
    val distinctCardCount: Int = queue.size,
    val attemptsLimit: Int,
    val partialRatingCardRequeueingEnabled: Boolean = true,
    val random: Random = Random.Default,
) {
    /** How many distinct cards have resolved [FlashcardTerminalRating.Mastered] so far. */
    val masteredCount: Int get() = terminalStates.values.count { it.terminalState == FlashcardTerminalRating.Mastered }

    /** `true` exactly when every distinct card has reached a Terminal State. */
    val isComplete: Boolean get() = queue.isEmpty()

    /** The card at the head of the queue — the one due for its next Attempt. `null` once complete. */
    val currentCard: Flashcard? get() = queue.firstOrNull()?.card

    /** The queue's cards in current order, current card first. */
    val remainingCards: List<Flashcard> get() = queue.map { it.card }

    /**
     * The current (head) card's own Rating history, in order — `FlashcardsAttemptIndicator`'s
     * source. Empty once [isComplete], since there is no head left.
     */
    val currentCardRatings: List<FlashcardAttemptRating> get() = queue.firstOrNull()?.ratings ?: emptyList()

    companion object {
        /**
         * Seeds the first snapshot: one [RatedSessionCardRecord] per [cards], each stamped
         * previously-mastered from [previouslyMasteredCardIds] — the ViewModel's session-start
         * progress read, never trusted any further than
         * this: the flag rides along on the record for [sealRatedCardResults] to carry into
         * [FlashcardResult.Rated.wasPreviouslyMastered], display and Mastery Defense only.
         */
        // TODO: take an injected Random from the caller instead of defaulting here, then drop
        // this exemption (see ADR-0020 randomizing-logic convention).
        @ArchConventionExempt()
        fun seed(
            cards: List<Flashcard>,
            attemptsLimit: Int,
            partialRatingCardRequeueingEnabled: Boolean = true,
            random: Random = Random.Default,
            previouslyMasteredCardIds: Set<String> = emptySet(),
        ): RatedSessionState = RatedSessionState(
            queue = cards.map { card ->
                RatedSessionCardRecord(card = card, wasPreviouslyMastered = card.id in previouslyMasteredCardIds)
            },
            distinctCardCount = cards.size,
            attemptsLimit = attemptsLimit,
            partialRatingCardRequeueingEnabled = partialRatingCardRequeueingEnabled,
            random = random,
        )
    }
}

/**
 * A card that has left [RatedSessionState.queue] for good, paired with the [RatedSessionCardRecord]
 * it resolved from — the record itself is discarded from the queue once terminal, so this is what
 * [sealRatedCardResults] reads [RatedSessionCardRecord.attemptsUsed] and
 * [RatedSessionCardRecord.wasPreviouslyMastered] from afterward.
 */
data class ResolvedRatedCard(val record: RatedSessionCardRecord, val terminalState: FlashcardTerminalRating)

/**
 * One [rate] call's result: the next snapshot, alongside the [FlashcardTerminalRating] the rated card
 * resolved to, or `null` when it was re-inserted rather than finished.
 */
data class RatedSessionAttemptRatingResult(val state: RatedSessionState, val terminal: FlashcardTerminalRating?)

/** Applies [rating] to [state]'s current (head) card, returning the next snapshot and its result. */
fun rate(state: RatedSessionState, rating: FlashcardAttemptRating): RatedSessionAttemptRatingResult {
    val record = state.queue.first()
    val remainingQueue = state.queue.drop(1)
    val ratedRecord = record.copy(ratings = record.ratings + rating)

    val terminal = resolveFlashcardTerminalRating(state, ratedRecord, rating)
    if (terminal == null) {
        val nextQueue = reinsert(state, remainingQueue, ratedRecord, rating)
        return RatedSessionAttemptRatingResult(state = state.copy(queue = nextQueue), terminal = null)
    }
    val resolved = ResolvedRatedCard(record = ratedRecord, terminalState = terminal)
    val nextFlashcardTerminalRatings = state.terminalStates + (ratedRecord.card.id to resolved)
    val nextState = state.copy(queue = remainingQueue, terminalStates = nextFlashcardTerminalRatings)
    return RatedSessionAttemptRatingResult(state = nextState, terminal = terminal)
}

/**
 * A silence timeout on [state]'s current (head) card: no Attempt, no Rating — the record comes back
 * unchanged, using the Failed gap range. A card nobody answered still needs asking, and Failed's gap
 * is the shortest one available. Never
 * terminal — an un-rated card cannot exhaust its Attempts.
 */
fun requeueAfterSilence(state: RatedSessionState): RatedSessionState {
    val record = state.queue.first()
    val remainingQueue = state.queue.drop(1)
    val nextQueue = reinsertAt(
        random = state.random,
        remainingQueue = remainingQueue,
        record = record,
        minGap = StudySessionConfig.FAILED_REQUEUE_MIN_GAP,
        maxGap = StudySessionConfig.FAILED_REQUEUE_MAX_GAP,
    )
    return state.copy(queue = nextQueue)
}

/** Resolution order per ADR-0044: Correct, then an immediately-terminal Partial, then Attempts-exhausted. */
private fun resolveFlashcardTerminalRating(
    state: RatedSessionState,
    record: RatedSessionCardRecord,
    rating: FlashcardAttemptRating,
): FlashcardTerminalRating? = when {
    rating == FlashcardAttemptRating.Correct -> FlashcardTerminalRating.Mastered
    rating == FlashcardAttemptRating.PartiallyCorrect && !state.partialRatingCardRequeueingEnabled -> FlashcardTerminalRating.Partial
    record.attemptsUsed >= state.attemptsLimit ->
        if (record.bestRating == FlashcardAttemptRating.PartiallyCorrect) FlashcardTerminalRating.Partial else FlashcardTerminalRating.Failed
    else -> null
}

/** Picks the gap range for [rating] (ADR-0046) and delegates to [reinsertAt]. */
private fun reinsert(
    state: RatedSessionState,
    remainingQueue: List<RatedSessionCardRecord>,
    record: RatedSessionCardRecord,
    rating: FlashcardAttemptRating,
): List<RatedSessionCardRecord> {
    val (minGap, maxGap) = when (rating) {
        FlashcardAttemptRating.Failed ->
            StudySessionConfig.FAILED_REQUEUE_MIN_GAP to StudySessionConfig.FAILED_REQUEUE_MAX_GAP
        FlashcardAttemptRating.PartiallyCorrect ->
            StudySessionConfig.PARTIAL_REQUEUE_MIN_GAP to StudySessionConfig.PARTIAL_REQUEUE_MAX_GAP
        FlashcardAttemptRating.Correct ->
            error("Correct never re-inserts — it resolves Mastered immediately in resolveFlashcardTerminalRating")
    }
    return reinsertAt(random = state.random, remainingQueue = remainingQueue, record = record, minGap = minGap, maxGap = maxGap)
}

/** Draws a bounded random gap within [minGap]..[maxGap] and inserts, clamped to the end of the remaining queue. */
private fun reinsertAt(
    random: Random,
    remainingQueue: List<RatedSessionCardRecord>,
    record: RatedSessionCardRecord,
    minGap: Int,
    maxGap: Int,
): List<RatedSessionCardRecord> {
    val gap = random.nextInt(minGap, maxGap + 1)
    return remainingQueue.toMutableList().apply { add(gap.coerceAtMost(size), record) }
}

/**
 * Seals [state] into [SessionResult]'s per-card [SessionResult.cardResults] — one [FlashcardResult]
 * per card with at least one completed Attempt, Rated's definition of Studied. A card never reached,
 * and a card that received only a silence timeout (zero Attempts either way), contributes nothing.
 *
 * A card already resolved to a [FlashcardTerminalRating] ([RatedSessionState.terminalStates]) carries that
 * outcome straight through. A card still mid re-insertion when [abandoned] is `true` — waiting for
 * its next draw, not yet terminal — is force-resolved from its best-rating-so-far via
 * [toAbandonedFlashcardTerminalRating]: the same [ADR-0044](../../../../../../../docs/adr/0044-three-valued-terminal-state.md)
 * table [resolveFlashcardTerminalRating] uses for natural resolution, just not through that function — there
 * is no just-submitted rating at abandon time, and the remaining queue's Attempts may be well under
 * its limit, so neither of [resolveFlashcardTerminalRating]'s inputs apply.
 */
fun sealRatedCardResults(state: RatedSessionState, abandoned: Boolean): List<FlashcardResult.Rated> {
    val resolvedEntries = state.terminalStates.values.map { resolved ->
        resolved.record.toFlashcardResult(resolved.terminalState.toFlashcardStudyProgressState())
    }
    if (!abandoned) return resolvedEntries

    val forcedEntries = state.queue
        .filter { it.attemptsUsed > 0 }
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
