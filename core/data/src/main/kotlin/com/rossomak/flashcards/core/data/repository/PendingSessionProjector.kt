package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDomain
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.scoring.SubcategoryProgressDelta
import com.rossomak.flashcards.core.domain.scoring.applyTo
import com.rossomak.flashcards.core.domain.scoring.mergeSessionIntoCardProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Replays the signed-in User's Pending Sessions (queued, not yet delivered to `submitStudySession`)
 * on top of cached server state, so the repositories that read that state include them. Only the data
 * layer knows about this projection; the domain and presentation layers read the repositories as
 * before.
 *
 * Pending Sessions are the queue entries owned by the signed-in User, oldest session start first.
 * Other Users' entries are ignored, so signing in as another User drops the first User's projection.
 * An entry that fails to map is skipped and logged; the delivery worker dead-letters it separately.
 *
 * The replay merges one whole session at a time, through the same rules the server applies
 * ([mergeSessionIntoCardProgress]), each against the state the sessions before it left.
 */
@Singleton
class PendingSessionProjector @Inject constructor(
    private val authRepository: AuthRepository,
    private val pendingSessionSubmissionLocalDataSource: PendingSessionSubmissionLocalDataSource,
) {

    /** The signed-in User's Pending Sessions, oldest first; empty while signed out. Re-emits on every queue or User change. */
    fun observePendingSessions(): Flow<List<SessionResult>> = combine(
        authRepository.observeAuthUser().map { user -> user?.uid }.distinctUntilChanged(),
        pendingSessionSubmissionLocalDataSource.observeAll(),
    ) { uid, entries -> uid?.let { ownedPendingSessions(it, entries) }.orEmpty() }
        .distinctUntilChanged()

    suspend fun pendingSessions(): List<SessionResult> = observePendingSessions().first()

    /**
     * Replays [pendingSessions] over [baselineBySubcategory], the cached server Card Progress of each
     * Subcategory they touch (`null` for one with no document). A Subcategory missing from
     * [baselineBySubcategory] replays over no record at all.
     *
     * Replayed entries are stamped with their session's start. The server stamps at delivery instead;
     * no current reader looks at the stamps closely enough to tell the difference.
     */
    fun replay(baselineBySubcategory: Map<String, SubcategoryProgress?>, pendingSessions: List<SessionResult>): CardProgressReplay {
        val progressBySubcategory = baselineBySubcategory.mapNotNull { (subcategoryId, progress) -> progress?.let { subcategoryId to it } }.toMap().toMutableMap()
        val summaryDeltas = mutableMapOf<String, SubcategoryProgressDelta>()

        pendingSessions.forEach { session ->
            val touchedSubcategoryIds = session.cardResults.map(FlashcardResult::subcategoryId).toSet()
            val priorStatesBySubcategory = touchedSubcategoryIds.associateWith { subcategoryId ->
                progressBySubcategory[subcategoryId]?.cards.orEmpty().mapValues { (_, entry) -> entry.state }
            }
            val mergeResult = mergeSessionIntoCardProgress(priorStatesBySubcategory, session)

            mergeResult.cardUpdatesBySubcategory.forEach { (subcategoryId, cardUpdates) ->
                val prior = progressBySubcategory[subcategoryId]
                val updatedCards = cardUpdates.mapValues { (cardId, update) -> update.applyTo(prior?.cards?.get(cardId), session.startedAt) }
                progressBySubcategory[subcategoryId] = SubcategoryProgress(
                    subcategoryId = subcategoryId,
                    categoryId = prior?.categoryId ?: session.categoryId,
                    cards = prior?.cards.orEmpty() + updatedCards,
                )
            }
            mergeResult.summaryDeltas.forEach { (subcategoryId, delta) ->
                summaryDeltas[subcategoryId] = summaryDeltas[subcategoryId]?.plus(delta) ?: delta
            }
        }

        return CardProgressReplay(progressBySubcategory = progressBySubcategory, summaryDeltas = summaryDeltas)
    }

    private fun ownedPendingSessions(uid: String, entries: List<PendingSessionSubmissionDto>): List<SessionResult> = entries
        .filter { entry -> entry.uid == uid }
        .mapNotNull { entry ->
            runCatching { entry.toDomain() }
                .onFailure { exception -> logw(exception) { "Not projecting malformed pending session ${entry.id}" } }
                .getOrNull()
        }
        .sortedBy(SessionResult::startedAt)

    private operator fun SubcategoryProgressDelta.plus(other: SubcategoryProgressDelta) = SubcategoryProgressDelta(
        masteredDelta = masteredDelta + other.masteredDelta,
        studiedDelta = studiedDelta + other.studiedDelta,
    )
}

/**
 * What replaying Pending Sessions produced: the projected Card Progress of every Subcategory with a
 * baseline document or a replayed result, and the accumulated Studied/Mastered count changes.
 */
data class CardProgressReplay(
    val progressBySubcategory: Map<String, SubcategoryProgress>,
    val summaryDeltas: Map<String, SubcategoryProgressDelta>,
)
