package com.rossomak.flashcards.core.data.repository

import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDomain
import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails
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
 * **Applied sessions.** The cached `user-stats` lists the latest sessions the server applied to it. A
 * Pending Session listed there is already in the cached server state, so it adds to no aggregate: no
 * scoring state, Streak, Daily Goal seconds or Studied/Mastered delta. Its Card Progress is still
 * replayed, since that merge is idempotent and covers a Card Progress cache older than `user-stats`.
 *
 * **Excluded session.** The Session Summary's baseline reads name their own session as excluded, so a
 * re-submission after process death never scores the session over itself. The excluded session is left
 * out of the queue before replaying, and a scoring state that already lists it as applied fails the read.
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
     * [observePendingSessions] without the sessions that have no Flashcard Result: the server rejects
     * those, so they never become a recorded session and are never shown as one.
     */
    fun observeDeliverablePendingSessions(): Flow<List<SessionResult>> = observePendingSessions()
        .map { sessions -> sessions.filter { session -> session.cardResults.isNotEmpty() } }
        .distinctUntilChanged()

    /**
     * The Card Progress of [subcategoryId] with the Pending Sessions that touch it replayed on top.
     *
     * With no such session, this is the plain remote read, failure included. Otherwise a failed remote
     * read projects over no record instead of failing, so the Pending Session results still show.
     *
     * The queue is read before the remote document, so a session delivered in between is replayed over
     * state that already includes it (harmless) rather than missing from both. If the signed-in User
     * changes between the two reads, the queue and the remote document may belong to different Users,
     * so the read fails rather than projecting one User's sessions over the other's progress.
     */
    suspend fun projectCardProgress(subcategoryId: String, excludedSessionId: String? = null): Result<SubcategoryProgressDetails?> {
        val uidAtStart = signedInUid()
        val pendingSessions = pendingSessions(excludedSessionId).filter { session -> session.touches(subcategoryId) }
        val remoteProgress = readRemoteProgress(subcategoryId)
        if (pendingSessions.isEmpty()) return remoteProgress
        if (signedInUid() != uidAtStart) {
            return Result.failure(IllegalStateException("Signed-in User changed while reading Card Progress for $subcategoryId"))
        }

        val baseline = remoteProgress.orEmptyBaseline(subcategoryId)
        return Result.success(replay(mapOf(subcategoryId to baseline), pendingSessions).progressBySubcategory[subcategoryId])
    }

    /**
     * How [pendingSessions] change the Studied and Mastered counts of each Subcategory they touch, or
     * `null` when they are stale by the time the baselines are read.
     *
     * [pendingSessions] is a snapshot taken earlier, possibly under a User who has since signed out.
     * The baselines are read for whoever is signed in now, so the queue is read again afterwards: a
     * different list means the User (or the queue) changed, and a projection mixing the two would be
     * wrong. Another User's list never equals a non-empty one, since entries are owned by uid and ids
     * are unique. The caller drops a `null` result; the queue change that made it stale re-emits.
     *
     * A session the cached scoring state lists as applied adds no delta. If that state is unreadable, no
     * session counts as applied: a double count is better than dropping real deltas on a guess.
     */
    suspend fun projectSummaryDeltas(pendingSessions: List<SessionResult>): Map<String, SubcategoryProgressDelta>? = coroutineScope {
        if (pendingSessions.isEmpty()) return@coroutineScope emptyMap()
        val progressBaselines = async { readProgressBaselines(pendingSessions) }
        val remoteScoringState = async { readRemoteScoringState() }
        val baselines = progressBaselines.await()
        val appliedSessionIds = remoteScoringState.await()
            .onFailure { exception -> logw(exception) { "Scoring state unreadable, treating no pending session as applied" } }
            .getOrNull()
            .appliedSessionIds()
        if (pendingSessions() != pendingSessions) return@coroutineScope null
        replay(baselines, pendingSessions, appliedSessionIds = appliedSessionIds).summaryDeltas
    }

    /**
     * The User's scoring state with every Pending Session but [excludedSessionId] replayed on top.
     *
     * With no Pending Session, this is the plain remote read: `null` for an account with no document
     * yet. Otherwise a missing document replays from [ScoringState]'s defaults. A failed scoring-state
     * read always fails: a guessed low starting state would show a misleading number. So does a cached
     * state that already lists [excludedSessionId] as applied, since it cannot be subtracted.
     *
     * If the signed-in User changes while the reads are in flight, the queue and the remote documents
     * may belong to different Users, so the read fails rather than projecting one User's sessions over
     * the other's scoring state.
     */
    suspend fun projectScoringState(excludedSessionId: String? = null): Result<ScoringState?> = coroutineScope {
        val uidAtStart = signedInUid()
        val pendingSessions = pendingSessions(excludedSessionId)
        val remoteScoringState = async { readRemoteScoringState().failingIfApplied(excludedSessionId) }
        if (pendingSessions.isEmpty()) return@coroutineScope remoteScoringState.await().map { dto -> dto?.toDomain() }

        val progressBaselines = async { readProgressBaselines(pendingSessions) }
        val config = async { xpConfigRepository.getXpConfig().getOrDefault(XpConfig()) }
        val scoringStateDto = remoteScoringState.await().getOrElse { exception -> return@coroutineScope Result.failure(exception) }
        val baselines = progressBaselines.await()
        val xpConfig = config.await()
        if (signedInUid() != uidAtStart) {
            return@coroutineScope Result.failure(IllegalStateException("Signed-in User changed while reading the scoring state"))
        }
        val scoringState = scoringStateDto?.toDomain() ?: ScoringState()
        Result.success(replay(baselines, pendingSessions, scoringState, xpConfig, scoringStateDto.appliedSessionIds()).scoringState)
    }

    /**
     * The preview `xpTotal` of each of [pendingSessions] (a snapshot of [observeDeliverablePendingSessions]),
     * keyed by session id. Replayed oldest first, so later Streak and Daily Goal awards build on earlier ones.
     * `null` when the queue changed meanwhile; that change re-emits. An unreadable scoring state replays
     * from [ScoringState]'s defaults: the values are approximate until the server's replace them.
     *
     * A session the cached scoring state lists as applied still gets an entry, scored against the state
     * the sessions before it left without advancing it, so a Recents row never lacks a total while the
     * recents listener lags behind; the server's entry replaces the row moments later.
     */
    suspend fun projectSessionXpTotals(pendingSessions: List<SessionResult>): Map<String, Int>? = coroutineScope {
        if (pendingSessions.isEmpty()) return@coroutineScope emptyMap()
        val progressBaselines = async { readProgressBaselines(pendingSessions) }
        val remoteScoringState = async { readRemoteScoringState() }
        val config = async { xpConfigRepository.getXpConfig().getOrDefault(XpConfig()) }
        val baselines = progressBaselines.await()
        val scoringStateDto = remoteScoringState.await()
            .onFailure { exception -> logw(exception) { "Scoring state unreadable, replaying pending session XP from defaults" } }
            .getOrNull()
        val xpConfig = config.await()
        if (observeDeliverablePendingSessions().first() != pendingSessions) return@coroutineScope null
        val scoringState = scoringStateDto?.toDomain() ?: ScoringState()
        replay(baselines, pendingSessions, scoringState, xpConfig, scoringStateDto.appliedSessionIds()).xpTotalBySessionId
    }

    private suspend fun pendingSessions(excludedSessionId: String? = null): List<SessionResult> =
        observePendingSessions().first().filterNot { session -> session.id == excludedSessionId }

    private fun signedInUid(): String? = authRepository.getCurrentUser()?.uid

    /**
     * Replays [pendingSessions] over [baselineBySubcategory], the cached server Card Progress of each
     * Subcategory they touch (`null` for one with no document), and over [scoringState]. A Subcategory
     * missing from [baselineBySubcategory] replays over no record at all. The Card Progress results
     * never depend on [scoringState], [config] or [appliedSessionIds], so a caller after those alone may
     * leave them at their defaults.
     *
     * A session in [appliedSessionIds] merges its Card Progress and gets its own `xpTotal`, but adds no
     * summary delta and does not advance the scoring state.
     *
     * Replayed entries are stamped with their session's start. The server stamps at delivery instead;
     * no current reader looks at the stamps closely enough to tell the difference.
     */
    private fun replay(
        baselineBySubcategory: Map<String, SubcategoryProgressDetails?>,
        pendingSessions: List<SessionResult>,
        scoringState: ScoringState = ScoringState(),
        config: XpConfig = XpConfig(),
        appliedSessionIds: Set<String> = emptySet(),
    ): Replay {
        val progressBySubcategory = baselineBySubcategory.mapNotNull { (subcategoryId, progress) -> progress?.let { subcategoryId to it } }.toMap().toMutableMap()
        val summaryDeltas = mutableMapOf<String, SubcategoryProgressDelta>()
        val xpTotalBySessionId = mutableMapOf<String, Int>()
        var projectedScoringState = scoringState

        pendingSessions.forEach { session ->
            val priorCardStatesBySubcategory = session.touchedSubcategoryIds().associateWith { subcategoryId ->
                progressBySubcategory[subcategoryId]?.cards.orEmpty().mapValues { (_, entry) -> entry.state }
            }
            val scoring = scoreSession(priorCardStatesBySubcategory, projectedScoringState, session, config)

            scoring.cardProgressMerge.cardUpdatesBySubcategory.forEach { (subcategoryId, cardUpdates) ->
                val prior = progressBySubcategory[subcategoryId]
                val updatedCards = cardUpdates.mapValues { (cardId, update) -> update.applyTo(prior?.cards?.get(cardId), session.startedAt) }
                progressBySubcategory[subcategoryId] = SubcategoryProgressDetails(
                    subcategoryId = subcategoryId,
                    categoryId = prior?.categoryId ?: session.categoryId,
                    cards = prior?.cards.orEmpty() + updatedCards,
                )
            }
            xpTotalBySessionId[session.id] = scoring.score.breakdown.xpTotal
            if (session.id in appliedSessionIds) return@forEach

            scoring.cardProgressMerge.summaryDeltas.forEach { (subcategoryId, delta) ->
                summaryDeltas[subcategoryId] = summaryDeltas[subcategoryId]?.plus(delta) ?: delta
            }
            projectedScoringState = scoring.newScoringState
        }

        return Replay(
            progressBySubcategory = progressBySubcategory,
            summaryDeltas = summaryDeltas,
            scoringState = projectedScoringState,
            xpTotalBySessionId = xpTotalBySessionId,
        )
    }

    /** The cached server Card Progress of every Subcategory [pendingSessions] touch, `null` where unreadable or absent. */
    private suspend fun readProgressBaselines(pendingSessions: List<SessionResult>): Map<String, SubcategoryProgressDetails?> = coroutineScope {
        pendingSessions.flatMap { session -> session.touchedSubcategoryIds() }.toSet().map { subcategoryId ->
            async { subcategoryId to readRemoteProgress(subcategoryId).orEmptyBaseline(subcategoryId) }
        }.awaitAll().toMap()
    }

    /** A failed read projects over no record instead of failing, so the Pending Session results still show. */
    private fun Result<SubcategoryProgressDetails?>.orEmptyBaseline(subcategoryId: String): SubcategoryProgressDetails? = getOrElse { exception ->
        logw(exception) { "Card Progress for $subcategoryId unreadable, projecting pending sessions over an empty baseline" }
        null
    }

    private suspend fun readRemoteProgress(subcategoryId: String): Result<SubcategoryProgressDetails?> =
        runCatchingFirestore { cardProgressRemoteDataSource.getProgress(subcategoryId)?.toDomain(subcategoryId) }

    private suspend fun readRemoteScoringState(): Result<ScoringStateDto?> = runCatchingFirestore { scoringStateRemoteDataSource.getScoringState() }

    /** Fails when the cached scoring state already lists [excludedSessionId] as applied: a baseline cannot subtract it. */
    private fun Result<ScoringStateDto?>.failingIfApplied(excludedSessionId: String?): Result<ScoringStateDto?> {
        if (excludedSessionId == null || excludedSessionId !in getOrNull().appliedSessionIds()) return this
        return Result.failure(IllegalStateException("Session $excludedSessionId is already applied to the cached scoring state"))
    }

    private fun ScoringStateDto?.appliedSessionIds(): Set<String> = this?.appliedSessionIds.orEmpty().toSet()

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
     * baseline document or a replayed result, the accumulated Studied/Mastered count changes, the
     * projected scoring state, and each replayed session's own `xpTotal`.
     */
    private data class Replay(
        val progressBySubcategory: Map<String, SubcategoryProgressDetails>,
        val summaryDeltas: Map<String, SubcategoryProgressDelta>,
        val scoringState: ScoringState,
        val xpTotalBySessionId: Map<String, Int>,
    )
}
