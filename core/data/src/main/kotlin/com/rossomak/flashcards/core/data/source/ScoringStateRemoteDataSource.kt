package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.domain.model.ScoringState.Companion.STARTING_LEVEL
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Reads the User's account-wide scoring-state singleton, `users/{uid}/progress/user-stats`.
 * Read-only: the server-authoritative `submitStudySession` Cloud Function
 * is the sole writer of this document now — it recomputes and overwrites the whole
 * [com.rossomak.flashcards.core.domain.model.ScoringState] itself, so this client never composes a
 * write for it. [com.rossomak.flashcards.core.data.repository.PendingSessionProjector] reads it as the
 * baseline it replays Pending Sessions over.
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

    suspend fun getScoringState(source: Source = Source.DEFAULT): ScoringStateDto? {
        val document = firestore.collection(COLLECTION_PATH_TEMPLATE.format(uid)).document(DOCUMENT_ID).get(source).await()
        if (!document.exists()) return null

        return ScoringStateDto(
            xp = document.getLong(FIELD_XP) ?: 0,
            level = (document.getLong(FIELD_LEVEL) ?: STARTING_LEVEL.toLong()).toInt(),
            xpIntoCurrentLevel = document.getLong(FIELD_XP_INTO_CURRENT_LEVEL) ?: 0,
            currentStreak = (document.getLong(FIELD_CURRENT_STREAK) ?: 0).toInt(),
            bestStreak = (document.getLong(FIELD_BEST_STREAK) ?: 0).toInt(),
            lastStudyDate = document.getString(FIELD_LAST_STUDY_DATE) ?: "",
            goalMetDate = document.getString(FIELD_GOAL_MET_DATE) ?: "",
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
    }
}
