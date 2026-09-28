package com.rossomak.flashcards.core.data.source

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDto
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Reads `users/{uid}/progress/details/subcategories/{subcategoryId}` (ADR-0016). `details` is a fixed
 * anchor document with no fields of its own — it exists only to host the real `subcategories`
 * subcollection, since Firestore cannot nest a collection directly inside another collection; the
 * sibling singletons `progress/summary` and `progress/user-stats` stay one hop shallower so
 * `progress` itself holds only per-User singleton documents.
 *
 * Read-only: the server-authoritative `submitStudySession` Cloud Function is the sole
 * writer of this collection now — this client never composes a write for it.
 *
 * [Source.SERVER] skips the local cache and, on success, refreshes it:
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] reads that way after each
 * delivery, so later default-source reads see the server's new state.
 */
class CardProgressRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) {

    private val uid: String
        get() = requireNotNull(firebaseAuth.currentUser?.uid) { "No authenticated user" }

    private fun collection() = firestore
        .collection(PROGRESS_COLLECTION_PATH_TEMPLATE.format(uid))
        .document(DETAILS_DOCUMENT_ID)
        .collection(SUBCATEGORIES_COLLECTION_ID)

    suspend fun getProgress(subcategoryId: String, source: Source = Source.DEFAULT): SubcategoryProgressDto? {
        val document = collection().document(subcategoryId).get(source).await()
        if (!document.exists()) return null

        val categoryId = document.getString(FIELD_CATEGORY_ID) ?: return null

        @Suppress("UNCHECKED_CAST")
        val cardsRaw = document.get(FIELD_CARDS) as? Map<String, Any> ?: emptyMap()
        val cards = cardsRaw.mapNotNull { (cardId, value) ->
            @Suppress("UNCHECKED_CAST")
            val entryMap = value as? Map<String, Any> ?: return@mapNotNull null
            cardId to CardProgressEntryDto(
                state = entryMap[FIELD_STATE] as? String ?: "",
                firstStudiedAt = entryMap[FIELD_FIRST_STUDIED_AT] as? Timestamp,
                masteredAt = entryMap[FIELD_MASTERED_AT] as? Timestamp,
            )
        }.toMap()

        return SubcategoryProgressDto(categoryId = categoryId, cards = cards)
    }

    private companion object {
        const val PROGRESS_COLLECTION_PATH_TEMPLATE = "users/%s/progress"
        const val DETAILS_DOCUMENT_ID = "details"
        const val SUBCATEGORIES_COLLECTION_ID = "subcategories"
        const val FIELD_CATEGORY_ID = "categoryId"
        const val FIELD_CARDS = "cards"
        const val FIELD_STATE = "state"
        const val FIELD_FIRST_STUDIED_AT = "firstStudiedAt"
        const val FIELD_MASTERED_AT = "masteredAt"
    }
}
