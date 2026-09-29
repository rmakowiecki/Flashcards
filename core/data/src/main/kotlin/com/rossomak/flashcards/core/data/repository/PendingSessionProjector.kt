package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDomain
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import com.rossomak.flashcards.core.domain.scoring.SubcategoryProgressDelta
import com.rossomak.flashcards.core.domain.scoring.applyTo
import com.rossomak.flashcards.core.domain.scoring.scoreSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * The replay scores one whole session at a time with [scoreSession], the same rules the server
 * applies, each against the Card Progress and scoring state the sessions before it left. It scores
 * with the cached XP configuration, as the server scores with its current one at delivery.
 *
 * Baselines are read from the Firestore cache or the server (default source). A Card Progress read
 * that fails (typically offline with no cached copy) projects over no record; see
 * [DefaultCardProgressRepository] for that limitation.
 */
@Singleton
class PendingSessionProjector @Inject constructor(
    private val authRepository: AuthRepository,
    private val pendingSessionSubmissionLocalDataSource: PendingSessionSubmissionLocalDataSource,
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource,
    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource,
    private val xpConfigRepository: XpConfigRepository,
) {

    /** The signed-in User's Pending Sessions, oldest first; empty while signed out. Re-emits on every queue or User change. */
    fun observePendingSessions(): Flow<List<SessionResult>> = combine(
        authRepository.observeAuthUser().map { user -> user?.uid }.distinctUntilChanged(),
        pendingSessionSubmissionLocalDataSource.observeAll(),
    ) { uid, entries -> uid?.let { ownedPendingSessions(it, entries) }.orEmpty() }
        .distinctUntilChanged()

    /**
     * The Card Progress of [subcategoryId] with the Pending Sessions that touch it replayed on top.
     *
     * With no such session, this is the plain remote read, failure included. Otherwise a failed remote
     * read projects over no record instead of failing, so the Pending Session results still show.
     *
     * The queue is read before the remote document, so a session delivered in between is replayed over
     * state that already includes it (harmless) rather than missing from both.
     */
    suspend fun projectCardProgress(subcategoryId: String): Result<SubcategoryProgress?> {
        val pendingSessions = pendingSessions().filter { session -> session.touches(subcategoryId) }
        val remoteProgress = readRemoteProgress(subcategoryId)
        if (pendingSessions.isEmpty()) return remoteProgress

        val baseline = remoteProgress.getOrElse { exception ->
            logw(exception) { "Card Progress for $subcategoryId unreadable, projecting pending sessions over an empty baseline" }
            null
        }
        return Result.success(replay(mapOf(subcategoryId to baseline), pendingSessions).progressBySubcategory[subcategoryId])
    }

    /** How [pendingSessions] change the Studied and Mastered counts of each Subcategory they touch. */
    suspend fun projectSummaryDeltas(pendingSessions: List<SessionResult>): Map<String, SubcategoryProgressDelta> {
        if (pendingSessions.isEmpty()) return emptyMap()
        return replay(readProgressBaselines(pendingSessions), pendingSessions).summaryDeltas
    }

    /**
     * The User's scoring state with every Pending Session replayed on top.
     *
     * With no Pending Session, this is the plain remote read: `null` for an account with no document
     * yet. Otherwise a missing document replays from [ScoringState]'s defaults. A failed scoring-state
     * read always fails: a guessed low starting state would show a misleading number.
     */
    suspend fun projectScoringState(): Result<ScoringState?> = coroutineScope {
        val pendingSessions = pendingSessions()
        val remoteScoringState = async { readRemoteScoringState() }
        if (pendingSessions.isEmpty()) return@coroutineScope remoteScoringState.await()

        val progressBaselines = async { readProgressBaselines(pendingSessions) }
        val config = async { xpConfigRepository.getXpConfig().getOrDefault(XpConfig()) }
        val scoringState = remoteScoringState.await().getOrElse { exception -> return@coroutineScope Result.failure(exception) } ?: ScoringState()
        Result.success(replay(progressBaselines.await(), pendingSessions, scoringState, config.await()).scoringState)
    }

    private suspend fun pendingSessions(): List<SessionResult> = observePendingSessions().first()

    /**
     * Replays [pendingSessions] over [baselineBySubcategory], the cached server Card Progress of each
     * Subcategory they touch (`null` for one with no document), and over [scoringState]. A Subcategory
     * missing from [baselineBySubcategory] replays over no record at all. The Card Progress results
     * never depend on [scoringState] or [config], so a caller after those alone may leave both at
     * their defaults.
     *
     * Replayed entries are stamped with their session's start. The server stamps at delivery instead;
     * no current reader looks at the stamps closely enough to tell the difference.
     */
    private fun replay(
        baselineBySubcategory: Map<String, SubcategoryProgress?>,
        pendingSessions: List<SessionResult>,
        scoringState: ScoringState = ScoringState(),
        config: XpConfig = XpConfig(),
    ): Replay {
        val progressBySubcategory = baselineBySubcategory.mapNotNull { (subcategoryId, progress) -> progress?.let { subcategoryId to it } }.toMap().toMutableMap()
        val summaryDeltas = mutableMapOf<String, SubcategoryProgressDelta>()
        var projectedScoringState = scoringState

        pendingSessions.forEach { session ->
            val priorCardStatesBySubcategory = session.touchedSubcategoryIds().associateWith { subcategoryId ->
                progressBySubcategory[subcategoryId]?.cards.orEmpty().mapValues { (_, entry) -> entry.state }
            }
            val scoring = scoreSession(priorCardStatesBySubcategory, projectedScoringState, session, config)

            scoring.cardProgressMerge.cardUpdatesBySubcategory.forEach { (subcategoryId, cardUpdates) ->
                val prior = progressBySubcategory[subcategoryId]
                val updatedCards = cardUpdates.mapValues { (cardId, update) -> update.applyTo(prior?.cards?.get(cardId), session.startedAt) }
                progressBySubcategory[subcategoryId] = SubcategoryProgress(
                    subcategoryId = subcategoryId,
                    categoryId = prior?.categoryId ?: session.categoryId,
                    cards = prior?.cards.orEmpty() + updatedCards,
                )
            }
            scoring.cardProgressMerge.summaryDeltas.forEach { (subcategoryId, delta) ->
                summaryDeltas[subcategoryId] = summaryDeltas[subcategoryId]?.plus(delta) ?: delta
            }
            projectedScoringState = scoring.newScoringState
        }

        return Replay(progressBySubcategory = progressBySubcategory, summaryDeltas = summaryDeltas, scoringState = projectedScoringState)
    }

    /** The cached server Card Progress of every Subcategory [pendingSessions] touch, `null` where unreadable or absent. */
    private suspend fun readProgressBaselines(pendingSessions: List<SessionResult>): Map<String, SubcategoryProgress?> = coroutineScope {
        pendingSessions.flatMap { session -> session.touchedSubcategoryIds() }.toSet().map { subcategoryId ->
            async {
                subcategoryId to readRemoteProgress(subcategoryId).getOrElse { exception ->
                    logw(exception) { "Card Progress for $subcategoryId unreadable, projecting pending sessions over an empty baseline" }
                    null
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun readRemoteProgress(subcategoryId: String): Result<SubcategoryProgress?> =
        runCatchingFirestoreWrite { cardProgressRemoteDataSource.getProgress(subcategoryId)?.toDomain(subcategoryId) }

    private suspend fun readRemoteScoringState(): Result<ScoringState?> =
        runCatchingFirestoreWrite { scoringStateRemoteDataSource.getScoringState()?.toDomain() }

    private fun ownedPendingSessions(uid: String, entries: List<PendingSessionSubmissionDto>): List<SessionResult> = entries
        .filter { entry -> entry.uid == uid }
        .mapNotNull { entry ->
            runCatching { entry.toDomain() }
                .onFailure { exception -> logw(exception) { "Not projecting malformed pending session ${entry.id}" } }
                .getOrNull()
        }
        .sortedBy(SessionResult::startedAt)

    private fun SessionResult.touchedSubcategoryIds(): Set<String> = cardResults.map(FlashcardResult::subcategoryId).toSet()

    private fun SessionResult.touches(subcategoryId: String): Boolean = cardResults.any { entry -> entry.subcategoryId == subcategoryId }

    private operator fun SubcategoryProgressDelta.plus(other: SubcategoryProgressDelta) = SubcategoryProgressDelta(
        masteredDelta = masteredDelta + other.masteredDelta,
        studiedDelta = studiedDelta + other.studiedDelta,
    )

    /**
     * What replaying Pending Sessions produced: the projected Card Progress of every Subcategory with a
     * baseline document or a replayed result, the accumulated Studied/Mastered count changes, and the
     * projected scoring state.
     */
    private data class Replay(
        val progressBySubcategory: Map<String, SubcategoryProgress>,
        val summaryDeltas: Map<String, SubcategoryProgressDelta>,
        val scoringState: ScoringState,
    )
}
