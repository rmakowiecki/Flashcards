package com.rossomak.flashcards.core.data.source

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.rossomak.flashcards.core.data.model.UserFavoritesDto
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreUserFavoritesRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) : UserFavoritesRemoteDataSource {

    private val uid: String
        get() = requireNotNull(firebaseAuth.currentUser?.uid) { "No authenticated user" }

    private fun document() = firestore.document(DOCUMENT_PATH_TEMPLATE.format(uid))

    /**
     * One listener on the single `favorites/state` document backs both category and subcategory
     * favorites — no favorites doc yet (nobody has favorited anything) reads back as an empty
     * [UserFavoritesDto], not an error.
     */
    override fun observeFavorites(): Flow<UserFavoritesDto> = callbackFlow {
        val registration = document().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot.toFavoritesDto())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setCategoryFavorite(categoryId: String, isFavorite: Boolean) {
        writeEntries(FIELD_CATEGORIES, setOf(categoryId), isFavorite)
    }

    override suspend fun setSubcategoryFavorite(subcategoryId: String, isFavorite: Boolean) {
        writeEntries(FIELD_SUBCATEGORIES, setOf(subcategoryId), isFavorite)
    }

    override suspend fun setSubcategoriesFavorite(subcategoryIds: Set<String>, isFavorite: Boolean) {
        if (subcategoryIds.isEmpty()) return
        writeEntries(FIELD_SUBCATEGORIES, subcategoryIds, isFavorite)
    }

    /**
     * `set(merge)` with a genuine nested [Map] value — not a dotted-path string key, which
     * `set(merge)` (unlike `update()`) writes as one literal field name rather than a field path —
     * merges into the existing `categories`/`subcategories` submap key-by-key, leaving every other
     * entry and the sibling map untouched. Creates `favorites/state` on a user's very first favorite
     * with no existence check needed first, and one call here is one write regardless of how many
     * ids are in [ids].
     */
    private suspend fun writeEntries(field: String, ids: Set<String>, isFavorite: Boolean) {
        val value: Any = if (isFavorite) FieldValue.serverTimestamp() else FieldValue.delete()
        val update = mapOf(field to ids.associateWith { value })
        awaitWithOfflineTimeout(document().set(update, SetOptions.merge()))
    }

    /**
     * Firestore's offline persistence applies a write to the local cache immediately but leaves the
     * returned Task pending until the server acks (docs/design/onboarding-flow.md:122-123) — an
     * unbounded `await()` would hang an offline caller forever. Timing out treats the
     * already-applied local write as success; a real failure (e.g. permission-denied) still throws
     * before the timeout elapses and propagates normally.
     */
    private suspend fun awaitWithOfflineTimeout(task: Task<Void>) {
        withTimeoutOrNull(OFFLINE_WRITE_TIMEOUT_MS) { task.await() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot?.toFavoritesDto(): UserFavoritesDto {
        if (this == null) return UserFavoritesDto()
        return UserFavoritesDto(
            categories = parseTimestampMap(FIELD_CATEGORIES),
            subcategories = parseTimestampMap(FIELD_SUBCATEGORIES),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.parseTimestampMap(field: String): Map<String, Timestamp> {
        val raw = get(field) as? Map<String, Any> ?: return emptyMap()
        return raw.mapNotNull { (key, value) -> (value as? Timestamp)?.let { key to it } }.toMap()
    }

    private companion object {
        const val DOCUMENT_PATH_TEMPLATE = "users/%s/favorites/state"
        const val FIELD_CATEGORIES = "categories"
        const val FIELD_SUBCATEGORIES = "subcategories"
        const val OFFLINE_WRITE_TIMEOUT_MS = 5_000L
    }
}
