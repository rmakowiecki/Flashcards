package com.rossomak.flashcards.core.domain.scoring

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Mastered
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Partial
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Seen
import com.rossomak.flashcards.core.domain.model.SessionResult
import java.time.Instant

/**
 * One card's Card Progress entry to write after a session. Says which stamps to set, never when:
 * the caller picks the time.
 */
data class CardProgressUpdate(
    val state: FlashcardStudyProgressState,
    val stampFirstStudied: Boolean,
    val stampMastered: Boolean,
)

/** How one Subcategory's Studied and Mastered counts in the progress summary change after a session. */
data class SubcategoryProgressDelta(
    val masteredDelta: Int,
    val studiedDelta: Int,
)

/**
 * Everything one session changes in a User's Card Progress, derived from the Card Progress before it.
 *
 * @property cardUpdatesBySubcategory Subcategory id to card id to the entry to write. A card with
 * nothing to write is absent.
 * @property summaryDeltas only the Subcategories whose counts change.
 * @property newCardsStudied cards with no Card Progress record before the session.
 * @property previouslyMasteredByCardId for each Rated card, whether the prior Card Progress had it
 * Mastered. This replaces the session's own `wasPreviouslyMastered` flag, as the server does. Empty
 * for a Fast session.
 */
data class CardProgressMergeResult(
    val cardUpdatesBySubcategory: Map<String, Map<String, CardProgressUpdate>>,
    val summaryDeltas: Map<String, SubcategoryProgressDelta>,
    val newCardsStudied: Int,
    val previouslyMasteredByCardId: Map<String, Boolean>,
)

/**
 * Merges [sessionResult]'s card results into the Card Progress before it, with the same rules the
 * `submitStudySession` Cloud Function applies when it records the session:
 * - Fast writes `Seen` only when the card has no record yet;
 * - Rated with no record writes the card's Terminal State;
 * - Rated with a record: `Mastered` masters, `Partial` is mastery-neutral on a Mastered card, and
 *   `Failed` de-masters.
 *
 * [priorStatesBySubcategory] maps Subcategory id to card id to the card's prior state; a missing
 * Subcategory or card has no record. Both implementations run the shared cases in
 * `testdata/card-progress-merge/`, so a rule changed on one side only fails a test.
 */
fun mergeSessionIntoCardProgress(
    priorStatesBySubcategory: Map<String, Map<String, FlashcardStudyProgressState>>,
    sessionResult: SessionResult,
): CardProgressMergeResult {
    var newCardsStudied = 0
    val cardUpdatesBySubcategory = mutableMapOf<String, Map<String, CardProgressUpdate>>()
    val summaryDeltas = mutableMapOf<String, SubcategoryProgressDelta>()

    sessionResult.cardResults.groupBy(FlashcardResult::subcategoryId).forEach { (subcategoryId, entries) ->
        val priorStates = priorStatesBySubcategory[subcategoryId].orEmpty()
        val cardUpdates = mutableMapOf<String, CardProgressUpdate>()
        var masteredDelta = 0
        var studiedDelta = 0

        entries.forEach { entry ->
            val priorState = priorStates[entry.cardId]
            val wasMastered = priorState == Mastered
            if (priorState == null) {
                newCardsStudied++
                studiedDelta++
            }
            masteredDelta += resolveMasteredDelta(entry.state, wasMastered)
            resolveUpdate(entry, priorExists = priorState != null, wasMastered = wasMastered)?.let { update -> cardUpdates[entry.cardId] = update }
        }

        if (cardUpdates.isNotEmpty()) cardUpdatesBySubcategory[subcategoryId] = cardUpdates
        if (masteredDelta != 0 || studiedDelta != 0) summaryDeltas[subcategoryId] = SubcategoryProgressDelta(masteredDelta, studiedDelta)
    }

    val previouslyMasteredByCardId = sessionResult.cardResults
        .filterIsInstance<FlashcardResult.Rated>()
        .associate { entry -> entry.cardId to (priorStatesBySubcategory[entry.subcategoryId]?.get(entry.cardId) == Mastered) }

    return CardProgressMergeResult(
        cardUpdatesBySubcategory = cardUpdatesBySubcategory,
        summaryDeltas = summaryDeltas,
        newCardsStudied = newCardsStudied,
        previouslyMasteredByCardId = previouslyMasteredByCardId,
    )
}

/**
 * The entry [prior] becomes after this update, with any stamp set to [stampedAt]. A stamp the update
 * does not set keeps its prior value, as the server's merge write does.
 */
fun CardProgressUpdate.applyTo(prior: CardProgressEntry?, stampedAt: Instant): CardProgressEntry = CardProgressEntry(
    state = state,
    firstStudiedAt = if (stampFirstStudied || prior == null) stampedAt else prior.firstStudiedAt,
    masteredAt = if (stampMastered) stampedAt else prior?.masteredAt,
)

/** Fast's state is always `Seen`, hence always `0`. */
private fun resolveMasteredDelta(state: FlashcardStudyProgressState, wasMastered: Boolean): Int = when (state) {
    Mastered -> if (wasMastered) 0 else 1
    Failed -> if (wasMastered) -1 else 0
    Partial, Seen -> 0
}

/** `null` means "write nothing". */
private fun resolveUpdate(entry: FlashcardResult, priorExists: Boolean, wasMastered: Boolean): CardProgressUpdate? = when {
    entry is FlashcardResult.Fast -> if (priorExists) null else CardProgressUpdate(Seen, stampFirstStudied = true, stampMastered = false)
    !priorExists -> CardProgressUpdate(entry.state, stampFirstStudied = true, stampMastered = entry.state == Mastered)
    else -> when (entry.state) {
        Mastered -> if (wasMastered) null else CardProgressUpdate(Mastered, stampFirstStudied = false, stampMastered = true)
        Partial -> if (wasMastered) null else CardProgressUpdate(Partial, stampFirstStudied = false, stampMastered = false)
        Failed -> CardProgressUpdate(Failed, stampFirstStudied = false, stampMastered = false)
        Seen -> error("A Rated card result is never Seen")
    }
}
