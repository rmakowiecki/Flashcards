package com.rossomak.flashcards.core.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
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
 * Baselines are read with Firestore's default source, and a failed Card Progress read projects over no
 * record. The Session Summary's score reads them from the cache only ([Source.CACHE]), so it never waits
 * for the server: a Card Progress document the cache never held is no record, and any other failure
 * fails the read.
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
     * With no such session, this is the plain remote read. Otherwise a failed default read projects over
     * no record, so the Pending Session results still show, while a failed [cacheOnly] read fails.
     *
     * The queue is read first, so a session delivered in between is replayed over state that already
     * includes it (harmless). A User switch between the two reads fails the read.
     */
    suspend fun projectCardProgress(subcategoryId: String, excludedSessionId: String? = null, cacheOnly: Boolean = false): Result<SubcategoryProgressDetails?> {
        val uidAtStart = signedInUid()
        val pendingSessions = pendingSessions(excludedSessionId).filter { session -> subcategoryId in session.touchedSubcategoryIds() }
        val remoteProgress = readRemoteProgress(subcategoryId, firestoreSource(cacheOnly))
        if (pendingSessions.isEmpty()) return remoteProgress
        if (signedInUid() != uidAtStart) {
            return Result.failure(IllegalStateException("Signed-in User changed while reading Card Progress for $subcategoryId"))
        }

        val baseline = if (cacheOnly) remoteProgress.getOrElse { exception -> return Result.failure(exception) } else remoteProgress.orEmptyBaseline(subcategoryId)
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
        val remoteScoringState = async { readRemoteScoringState(Source.DEFAULT) }
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
     * yet. Otherwise a missing document replays from [ScoringState]'s defaults. The read fails when the
     * scoring state is unreadable (a guessed low state would mislead), already lists [excludedSessionId]
     * as applied, or changes User mid-read. A queue that merely changes meanwhile does not fail it. A
     * [cacheOnly] read also fails on a scoring state the cache never held.
     */
    suspend fun projectScoringState(excludedSessionId: String? = null, cacheOnly: Boolean = false): Result<ScoringState?> = coroutineScope {
        val uidAtStart = signedInUid()
        val pendingSessions = pendingSessions(excludedSessionId)
        val remoteScoringState = async { readRemoteScoringState(firestoreSource(cacheOnly)).failingIfApplied(excludedSessionId) }
        if (pendingSessions.isEmpty()) return@coroutineScope remoteScoringState.await().map { dto -> dto?.toDomain() }

        val progressBaselines = async {
            if (cacheOnly) readTouchedProgress(pendingSessions, Source.CACHE).allOrFailure() else Result.success(readProgressBaselines(pendingSessions))
        }
        val config = async { xpConfigRepository.getXpConfig().getOrDefault(XpConfig()) }
        val scoringStateDto = remoteScoringState.await().getOrElse { exception -> return@coroutineScope Result.failure(exception) }
        val baselines = progressBaselines.await().getOrElse { exception -> return@coroutineScope Result.failure(exception) }
        val xpConfig = config.await()
        if (signedInUid() != uidAtStart) {
            return@coroutineScope Result.failure(IllegalStateException("Signed-in User changed while reading the scoring state"))
        }
        val scoringState = scoringStateDto?.toDomain() ?: ScoringState()
        Result.success(replay(baselines, pendingSessions, scoringState, xpConfig, scoringStateDto.appliedSessionIds()).scoringState)
    }

    /**
     * [remoteScoringState] with [pendingSessions] (a snapshot of [observePendingSessions]) replayed on
     * top and scored with [config], or `null` when [pendingSessions] is stale by the time the Card
     * Progress baselines are read. A missing remote document (`null`) replays from [ScoringState]'s
     * defaults. A session [remoteScoringState] lists as applied is already in it, so it does not advance
     * the result. With no session, this returns the remote state, or the defaults, without reading anything.
     *
     * The queue is read again after the baselines and compared with [pendingSessions]: a different list
     * means the queue changed meanwhile. The same check covers a User switch, so there is no separate uid
     * check: after sign-out the list is empty, and another User's list never equals a non-empty one, since
     * entries are owned by uid and ids are unique. The caller drops a `null` result; the queue change that
     * made it stale re-emits.
     */
    suspend fun projectScoringStateOver(remoteScoringState: ScoringStateDto?, pendingSessions: List<SessionResult>, config: XpConfig): ScoringState? {
        val scoringState = remoteScoringState?.toDomain() ?: ScoringState()
        if (pendingSessions.isEmpty()) return scoringState
        val baselines = readProgressBaselines(pendingSessions)
        if (observePendingSessions().first() != pendingSessions) return null
        return replay(baselines, pendingSessions, scoringState, config, remoteScoringState.appliedSessionIds()).scoringState
    }

    /**
     * The preview `xpTotal` of each of [pendingSessions] (a snapshot of [observePendingSessions]),
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
        val remoteScoringState = async { readRemoteScoringState(Source.DEFAULT) }
        val config = async { xpConfigRepository.getXpConfig().getOrDefault(XpConfig()) }
        val baselines = progressBaselines.await()
        val scoringStateDto = remoteScoringState.await()
            .onFailure { exception -> logw(exception) { "Scoring state unreadable, replaying pending session XP from defaults" } }
            .getOrNull()
        val xpConfig = config.await()
        if (observePendingSessions().first() != pendingSessions) return@coroutineScope null
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

    /** The server Card Progress of every Subcategory [pendingSessions] touch, `null` where unreadable or absent. */
    private suspend fun readProgressBaselines(pendingSessions: List<SessionResult>): Map<String, SubcategoryProgressDetails?> =
        readTouchedProgress(pendingSessions, Source.DEFAULT).mapValues { (subcategoryId, read) -> read.orEmptyBaseline(subcategoryId) }

    private suspend fun readTouchedProgress(pendingSessions: List<SessionResult>, source: Source): Map<String, Result<SubcategoryProgressDetails?>> = coroutineScope {
        pendingSessions.flatMap { session -> session.touchedSubcategoryIds() }.toSet()
            .map { subcategoryId -> async { subcategoryId to readRemoteProgress(subcategoryId, source) } }
            .awaitAll()
            .toMap()
    }

    /** A failed read projects over no record instead of failing, so the Pending Session results still show. */
    private fun Result<SubcategoryProgressDetails?>.orEmptyBaseline(subcategoryId: String): SubcategoryProgressDetails? = getOrElse { exception ->
        logw(exception) { "Card Progress for $subcategoryId unreadable, projecting pending sessions over an empty baseline" }
        null
    }

    private suspend fun readRemoteProgress(subcategoryId: String, source: Source): Result<SubcategoryProgressDetails?> =
        runCatchingFirestore { cardProgressRemoteDataSource.getProgress(subcategoryId, source)?.toDomain(subcategoryId) }.cacheMissAsNoDocument(source)

    private suspend fun readRemoteScoringState(source: Source): Result<ScoringStateDto?> =
        runCatchingFirestore { scoringStateRemoteDataSource.getScoringState(source) }

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

private fun firestoreSource(cacheOnly: Boolean): Source = if (cacheOnly) Source.CACHE else Source.DEFAULT

/** A [Source.CACHE] read of a document the cache never held fails with `UNAVAILABLE`; this reads it as no document. */
private fun <T> Result<T?>.cacheMissAsNoDocument(source: Source): Result<T?> {
    val exception = exceptionOrNull()
    val isCacheMiss = source == Source.CACHE && exception is FirebaseFirestoreException && exception.code == FirebaseFirestoreException.Code.UNAVAILABLE
    return if (isCacheMiss) Result.success(null) else this
}

private fun <K, V> Map<K, Result<V>>.allOrFailure(): Result<Map<K, V>> =
    Result.success(mapValues { (_, read) -> read.getOrElse { exception -> return Result.failure(exception) } })
