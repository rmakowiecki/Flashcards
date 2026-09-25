package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.rossomak.flashcards.core.data.model.ProgressSummaryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressSummaryDto
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Observes the User's per-Subcategory progress-summary singleton, `users/{uid}/progress/summary`
 * (ADR-0016). Read-only: the server-authoritative `submitStudySession` Cloud Function is
 * the sole writer of this document now — this client never composes an increment for it.
 */
class ProgressSummaryRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) {

    private fun document(uid: String) = firestore.document(DOCUMENT_PATH_TEMPLATE.format(uid))

    /**
     * A missing document (nobody has finished a session yet) reads back as a `null` emission, not
     * an error — mirrors [getSummary]'s old contract for an absent document.
     *
     * No authenticated user (e.g. collection starting right after sign-out) completes silently
     * instead of registering a listener — mirrors the PERMISSION_DENIED-during-sign-out teardown
     * path. Captured once here rather than reread later, so a sign-out racing the flow's launch
     * can't throw partway into the `callbackFlow` builder.
     */
    fun observeSummary(): Flow<ProgressSummaryDto?> = flow {
        val uid = firebaseAuth.currentUser?.uid ?: return@flow
        emitAll(observeAuthenticatedSummary(uid))
    }

    private fun observeAuthenticatedSummary(uid: String): Flow<ProgressSummaryDto?> = callbackFlow {
        // Delivered off the main thread so the snapshot-to-DTO mapping never costs a UI frame, one
        // snapshot at a time: Firestore submits every snapshot to the executor separately, so a
        // parallel one could finish mapping an older snapshot last and leave it as the latest value.
        val registration = document(uid).addSnapshotListener(Dispatchers.Default.limitedParallelism(1).asExecutor()) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot.toSummaryDto())
        }
        awaitClose { registration.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot?.toSummaryDto(): ProgressSummaryDto? {
        if (this == null || !exists()) return null

        val subcategoriesRaw = get(FIELD_SUBCATEGORIES) as? Map<String, Any> ?: emptyMap()
        val subcategories = subcategoriesRaw.mapNotNull { (subcategoryId, value) ->
            val entryMap = value as? Map<String, Any> ?: return@mapNotNull null
            subcategoryId to SubcategoryProgressSummaryDto(
                masteredCount = (entryMap[FIELD_MASTERED_COUNT] as? Number)?.toInt() ?: 0,
                studiedCount = (entryMap[FIELD_STUDIED_COUNT] as? Number)?.toInt() ?: 0,
            )
        }.toMap()

        return ProgressSummaryDto(subcategories = subcategories)
    }

    private companion object {
        const val DOCUMENT_PATH_TEMPLATE = "users/%s/progress/summary"
        const val FIELD_SUBCATEGORIES = "subcategories"
        const val FIELD_MASTERED_COUNT = "masteredCount"
        const val FIELD_STUDIED_COUNT = "studiedCount"
    }
}
