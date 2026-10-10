package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.domain.model.ScoringState.Companion.STARTING_LEVEL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Reads the User's account-wide scoring-state singleton, `users/{uid}/progress/user-stats`.
 * Read-only: the server-authoritative `submitStudySession` Cloud Function
 * is the sole writer of this document now — it recomputes and overwrites the whole
 * [com.rossomak.flashcards.core.domain.model.ScoringState] itself, so this client never composes a
 * write for it. [com.rossomak.flashcards.core.data.repository.PendingSessionProjector] reads it as the
 * baseline it replays Pending Sessions over, and its `studiedSecondsOnLastStudyDate` as the seconds
 * already studied that day when it judges a Pending Session's Daily Goal.
 *
 * [Source.SERVER] skips the local cache and, on success, refreshes it:
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] reads that way after each
 * delivery, so later default-source reads see the server's new state.
 */
class ScoringStateRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) {

    private val uid: String
        get() = requireNotNull(firebaseAuth.currentUser?.uid) { "No authenticated user" }

    private fun document(uid: String) = firestore.collection(COLLECTION_PATH_TEMPLATE.format(uid)).document(DOCUMENT_ID)

    suspend fun getScoringState(source: Source = Source.DEFAULT): ScoringStateDto? = document(uid).get(source).await().toScoringStateDto()

    /**
     * Follows the document live. A missing document reads back as a `null` emission once the server
     * confirms it is missing. A missing document read from the cache only means it is not cached
     * (offline on a new device, or after a reinstall), so that snapshot is dropped rather than read as
     * a brand-new account: nothing is emitted until the server answers. The server's answer may change
     * only the snapshot's sync state, which a listener raises only with [MetadataChanges.INCLUDE].
     * Snapshots that change nothing but that state are not re-emitted.
     *
     * No authenticated user (e.g. collection starting right after sign-out) completes silently instead
     * of registering a listener, as [ProgressSummaryRemoteDataSource.observeSummary] does.
     */
    fun observeScoringState(): Flow<ScoringStateDto?> = flow {
        val uid = firebaseAuth.currentUser?.uid ?: return@flow
        emitAll(observeAuthenticatedScoringState(uid))
    }.distinctUntilChanged()

    private fun observeAuthenticatedScoringState(uid: String): Flow<ScoringStateDto?> = callbackFlow {
        // Delivered off the main thread one snapshot at a time, as ProgressSummaryRemoteDataSource does:
        // a parallel executor could finish mapping an older snapshot last.
        val executor = Dispatchers.Default.limitedParallelism(1).asExecutor()
        val registration = document(uid).addSnapshotListener(executor, MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || (!snapshot.exists() && snapshot.metadata.isFromCache)) return@addSnapshotListener
            trySend(snapshot.toScoringStateDto())
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toScoringStateDto(): ScoringStateDto? {
        if (!exists()) return null

        return ScoringStateDto(
            xp = getLong(FIELD_XP) ?: 0,
            level = (getLong(FIELD_LEVEL) ?: STARTING_LEVEL.toLong()).toInt(),
            xpIntoCurrentLevel = getLong(FIELD_XP_INTO_CURRENT_LEVEL) ?: 0,
            currentStreak = (getLong(FIELD_CURRENT_STREAK) ?: 0).toInt(),
            bestStreak = (getLong(FIELD_BEST_STREAK) ?: 0).toInt(),
            lastStudyDate = getString(FIELD_LAST_STUDY_DATE) ?: "",
            goalMetDate = getString(FIELD_GOAL_MET_DATE) ?: "",
            studiedSecondsOnLastStudyDate = getLong(FIELD_STUDIED_SECONDS_ON_LAST_STUDY_DATE) ?: 0,
        )
    }

    private companion object {
        const val COLLECTION_PATH_TEMPLATE = "users/%s/progress"
        const val DOCUMENT_ID = "user-stats"
        const val FIELD_XP = "xp"
        const val FIELD_LEVEL = "level"
        const val FIELD_XP_INTO_CURRENT_LEVEL = "xpIntoCurrentLevel"
        const val FIELD_CURRENT_STREAK = "currentStreak"
        const val FIELD_BEST_STREAK = "bestStreak"
        const val FIELD_LAST_STUDY_DATE = "lastStudyDate"
        const val FIELD_GOAL_MET_DATE = "goalMetDate"
        const val FIELD_STUDIED_SECONDS_ON_LAST_STUDY_DATE = "studiedSecondsOnLastStudyDate"
    }
}
