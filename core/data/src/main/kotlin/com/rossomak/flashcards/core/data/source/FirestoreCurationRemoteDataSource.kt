package com.rossomak.flashcards.core.data.source

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.rossomak.flashcards.core.data.model.CurationActionEntryDto
import com.rossomak.flashcards.core.data.model.CurationRequestDto
import com.rossomak.flashcards.core.domain.model.CurationAction
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

private const val WHEREIN_BATCH_SIZE = 30

class FirestoreCurationRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) : CurationRemoteDataSource {

    private val uid: String
        get() = requireNotNull(firebaseAuth.currentUser?.uid) { "No authenticated user" }

    private fun collection() = firestore.collection(COLLECTION_PATH_TEMPLATE.format(uid))

    override suspend fun getCurationRequests(cardIds: List<String>): Map<String, CurationRequestDto> {
        if (cardIds.isEmpty()) return emptyMap()
        val userCollection = collection()
        return cardIds
            .chunked(WHEREIN_BATCH_SIZE)
            .flatMap { chunk ->
                userCollection
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        val subcategoryId = document.getString(FIELD_SUBCATEGORY_ID) ?: return@mapNotNull null

                        @Suppress("UNCHECKED_CAST")
                        val actionsRaw = document.get(FIELD_ACTIONS) as? Map<String, Any> ?: emptyMap()
                        val actions = actionsRaw.mapNotNull { (key, value) ->
                            @Suppress("UNCHECKED_CAST")
                            val entryMap = value as? Map<String, Any> ?: return@mapNotNull null
                            val flaggedAt = entryMap[FIELD_FLAGGED_AT] as? Timestamp
                            key to CurationActionEntryDto(flaggedAt = flaggedAt)
                        }.toMap()
                        document.id to CurationRequestDto(subcategoryId = subcategoryId, actions = actions)
                    }
            }
            .toMap()
    }

    /**
     * One `set(merge)` for the whole submission. `set(merge)` does not treat dotted keys as nested
     * field paths the way `update()` does — a key like `"actions.Delete"` would be written as a
     * literal top-level field — so the `actions` submap is built as a real nested [Map] instead;
     * Firestore merges nested maps key-by-key, leaving untouched actions on the document intact. A
     * difficulty action additionally deletes its opposite's key, keeping the pair mutually
     * exclusive in storage as well as in the draft.
     */
    override suspend fun upsertCurationActions(cardId: String, subcategoryId: String, actions: Set<CurationAction>) {
        if (actions.isEmpty()) return // unreachable via SubmitCurationReportUseCase's additive design (ADR-0017)
        val actionUpdates = mutableMapOf<String, Any>()
        actions.forEach { action ->
            actionUpdates[action.name] = mapOf(FIELD_FLAGGED_AT to FieldValue.serverTimestamp())
        }
        actions.forEach { action ->
            val opposite = action.difficultyOpposite() ?: return@forEach
            if (opposite !in actions) actionUpdates[opposite.name] = FieldValue.delete()
        }
        val updates = mapOf(FIELD_SUBCATEGORY_ID to subcategoryId, FIELD_ACTIONS to actionUpdates)
        collection().document(cardId).set(updates, SetOptions.merge()).await()
    }

    private companion object {
        const val COLLECTION_PATH_TEMPLATE = "users/%s/curationRequests"
        const val FIELD_SUBCATEGORY_ID = "subcategoryId"
        const val FIELD_ACTIONS = "actions"
        const val FIELD_FLAGGED_AT = "flaggedAt"
    }
}
