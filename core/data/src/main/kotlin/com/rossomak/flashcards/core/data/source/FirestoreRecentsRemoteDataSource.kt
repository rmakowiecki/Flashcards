package com.rossomak.flashcards.core.data.source

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.rossomak.flashcards.core.common.logw
import com.rossomak.flashcards.core.data.model.RecentSessionEntryDto
import com.rossomak.flashcards.core.data.model.RecentsStateDto
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FirestoreRecentsRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) : RecentsRemoteDataSource {

    private fun document(uid: String) = firestore.document(DOCUMENT_PATH_TEMPLATE.format(uid))

    /**
     * One listener on `recents/state`; a missing document reads as an empty [RecentsStateDto]. Signed
     * out, it completes without a listener, like [FirestoreUserFavoritesRemoteDataSource.observeFavorites].
     */
    override fun observeRecents(): Flow<RecentsStateDto> = flow {
        val uid = firebaseAuth.currentUser?.uid ?: return@flow
        emitAll(observeAuthenticatedRecents(uid))
    }

    private fun observeAuthenticatedRecents(uid: String): Flow<RecentsStateDto> = callbackFlow {
        // Delivered off the main thread so the snapshot-to-DTO mapping never costs a UI frame, one
        // snapshot at a time: Firestore submits every snapshot to the executor separately, so a
        // parallel one could finish mapping an older snapshot last and leave it as the latest value.
        val registration = document(uid).addSnapshotListener(Dispatchers.Default.limitedParallelism(1).asExecutor()) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot.toRecentsStateDto())
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot?.toRecentsStateDto(): RecentsStateDto {
        val entries = this?.get(FIELD_ENTRIES) as? Map<*, *> ?: return RecentsStateDto()
        return RecentsStateDto(
            entries = entries.mapNotNull { (key, value) ->
                runCatching { parseEntry(value) }
                    .onFailure { exception -> logw(exception) { "Skipping malformed recents entry $key" } }
                    .getOrNull()
            },
        )
    }

    /**
     * Throws [IllegalArgumentException] when a required field is missing or has the wrong type, or the
     * Subcategory ids and names differ in length. Firestore returns every integer as a `Long`.
     */
    private fun parseEntry(value: Any?): RecentSessionEntryDto {
        val fields = requireNotNull(value as? Map<*, *>) { "Non-object entry" }
        val subcategoryIds = requireStringList(fields, FIELD_SUBCATEGORY_IDS)
        val subcategoryNames = requireStringList(fields, FIELD_SUBCATEGORY_NAMES)
        require(subcategoryIds.size == subcategoryNames.size) { "$FIELD_SUBCATEGORY_IDS and $FIELD_SUBCATEGORY_NAMES differ in length" }
        return RecentSessionEntryDto(
            sessionId = requireString(fields, FIELD_SESSION_ID),
            startTimestamp = requireNotNull(fields[FIELD_START_TIMESTAMP] as? Timestamp) { "Missing or non-timestamp $FIELD_START_TIMESTAMP" },
            durationSeconds = requireInt(fields, FIELD_DURATION_SECONDS),
            studyMode = requireString(fields, FIELD_STUDY_MODE),
            voiceAnswering = fields[FIELD_VOICE_ANSWERING] as? Boolean,
            readAloud = fields[FIELD_READ_ALOUD] as? Boolean,
            sourceType = requireString(fields, FIELD_SOURCE_TYPE),
            categoryId = requireString(fields, FIELD_CATEGORY_ID),
            categoryName = requireString(fields, FIELD_CATEGORY_NAME),
            subcategoryIds = subcategoryIds,
            subcategoryNames = subcategoryNames,
            cardCount = requireInt(fields, FIELD_CARD_COUNT),
            xpTotal = requireInt(fields, FIELD_XP_TOTAL),
        )
    }

    private fun requireString(fields: Map<*, *>, name: String): String = requireNotNull(fields[name] as? String) { "Missing or non-string $name" }

    private fun requireStringList(fields: Map<*, *>, name: String): List<String> =
        requireNotNull(fields[name] as? List<*>) { "Missing or non-list $name" }.map { element ->
            requireNotNull(element as? String) { "Non-string entry in $name" }
        }

    private fun requireInt(fields: Map<*, *>, name: String): Int = requireNotNull((fields[name] as? Number)?.toInt()) { "Missing or non-numeric $name" }

    private companion object {
        const val DOCUMENT_PATH_TEMPLATE = "users/%s/recents/state"
        const val FIELD_ENTRIES = "entries"
        const val FIELD_SESSION_ID = "sessionId"
        const val FIELD_START_TIMESTAMP = "startTimestamp"
        const val FIELD_DURATION_SECONDS = "durationSeconds"
        const val FIELD_STUDY_MODE = "studyMode"
        const val FIELD_VOICE_ANSWERING = "voiceAnswering"
        const val FIELD_READ_ALOUD = "readAloud"
        const val FIELD_SOURCE_TYPE = "sourceType"
        const val FIELD_CATEGORY_ID = "categoryId"
        const val FIELD_CATEGORY_NAME = "categoryName"
        const val FIELD_SUBCATEGORY_IDS = "subcategoryIds"
        const val FIELD_SUBCATEGORY_NAMES = "subcategoryNames"
        const val FIELD_CARD_COUNT = "cardCount"
        const val FIELD_XP_TOTAL = "xpTotal"
    }
}
