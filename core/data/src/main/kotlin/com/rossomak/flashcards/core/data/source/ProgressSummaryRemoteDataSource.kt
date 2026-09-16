package com.rossomak.flashcards.core.data.source

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.rossomak.flashcards.core.data.model.ProgressSummaryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressSummaryDto
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observes the User's per-Subcategory progress-summary singleton, `users/{uid}/progress/summary`
 * (ADR-0016). Read-only: the server-authoritative `submitStudySession` Cloud Function is
 * the sole writer of this document now — this client never composes an increment for it.
 */
class ProgressSummaryRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) {

    private val uid: String
        get() = requireNotNull(firebaseAuth.currentUser?.uid) { "No authenticated user" }

    private fun document() = firestore.document(DOCUMENT_PATH_TEMPLATE.format(uid))

    /**
     * A missing document (nobody has finished a session yet) reads back as a `null` emission, not
     * an error — mirrors [getSummary]'s old contract for an absent document.
     */
    fun observeSummary(): Flow<ProgressSummaryDto?> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
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
