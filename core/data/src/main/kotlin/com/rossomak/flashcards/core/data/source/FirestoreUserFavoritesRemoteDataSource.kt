package com.rossomak.flashcards.core.data.source

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.rossomak.flashcards.core.common.loge
import com.rossomak.flashcards.core.data.model.UserFavoritesDto
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreUserFavoritesRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
) : UserFavoritesRemoteDataSource {

    private val uid: String
        get() = requireNotNull(firebaseAuth.currentUser?.uid) { "No authenticated user" }

    private fun document(uid: String) = firestore.document(DOCUMENT_PATH_TEMPLATE.format(uid))

    /**
     * One listener on the single `favorites/state` document backs both category and subcategory
     * favorites — no favorites doc yet (nobody has favorited anything) reads back as an empty
     * [UserFavoritesDto], not an error.
     *
     * No authenticated user (e.g. collection starting right after sign-out) completes silently
     * instead of registering a listener — mirrors the PERMISSION_DENIED-during-sign-out teardown
     * path, rather than crashing on the [uid] getter's `requireNotNull`. Captured once here rather
     * than reread later, so a sign-out racing the flow's launch can't throw the `requireNotNull`
     * out of the [snapshotFlow] builder.
     */
    override fun observeFavorites(): Flow<UserFavoritesDto> = flow {
        val uid = firebaseAuth.currentUser?.uid ?: return@flow
        emitAll(observeAuthenticatedFavorites(uid))
    }

    private fun observeAuthenticatedFavorites(uid: String): Flow<UserFavoritesDto> = document(uid).snapshotFlow { snapshot -> snapshot.toFavoritesDto() }

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
     *
     * Unfavoriting writes `null` instead of [FieldValue.delete]. The SDK's local view of pending
     * writes loses an earlier pending nested-key delete as soon as a later write to the same
     * document is composed, so an offline unfavorite could silently come back (even in the same
     * session). A `null` value is an ordinary value to it, so the view stays right; the read side
     * keeps only [Timestamp] values, so a `null` entry is simply not a favorite.
     */
    private suspend fun writeEntries(field: String, ids: Set<String>, isFavorite: Boolean) {
        val value: Any? = if (isFavorite) FieldValue.serverTimestamp() else null
        val update = mapOf(field to ids.associateWith { value })
        val writeTask = document(uid).set(update, SetOptions.merge())
        // Outlives the offline timeout below, so a write the server rejects after the caller has
        // already stopped waiting is still reported rather than silently rolled back.
        writeTask.addOnFailureListener { exception ->
            loge(exception) { "Favorite write rejected: field=$field ids=$ids isFavorite=$isFavorite" }
        }
        awaitWithOfflineTimeout(writeTask)
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

    /**
     * Read with [ServerTimestampBehavior.ESTIMATE]: a favorite whose write the server has not
     * acknowledged yet (always, while offline) holds a pending server timestamp, which the default
     * behavior reads as `null` and this parse would drop. The estimate is the SDK's local-clock
     * value, so the entry is visible at once and the server's value replaces it on acknowledgement.
     */
    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.parseTimestampMap(field: String): Map<String, Timestamp> {
        val raw = get(field, ServerTimestampBehavior.ESTIMATE) as? Map<String, Any> ?: return emptyMap()
        return raw.mapNotNull { (key, value) -> (value as? Timestamp)?.let { key to it } }.toMap()
    }

    private companion object {
        const val DOCUMENT_PATH_TEMPLATE = "users/%s/favorites/state"
        const val FIELD_CATEGORIES = "categories"
        const val FIELD_SUBCATEGORIES = "subcategories"
        const val OFFLINE_WRITE_TIMEOUT_MS = 5_000L
    }
}
