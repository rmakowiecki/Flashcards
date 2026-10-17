package com.rossomak.flashcards.core.data.source

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class FirestorePremiumRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PremiumRemoteDataSource {

    private fun document(uid: String) =
        firestore.collection(USERS_COLLECTION).document(uid).collection(ENTITLEMENT_COLLECTION).document(PREMIUM_DOCUMENT)

    /**
     * Metadata changes are included so the server's answer arrives even when it matches the cache. A
     * cached snapshot of a missing record is skipped: with no record cached yet, offline would otherwise
     * read as not Premium before the server has said so.
     */
    override fun observePremium(uid: String): Flow<Boolean> = document(uid).snapshotFlow(
        metadataChanges = MetadataChanges.INCLUDE,
        skipSnapshot = { snapshot -> snapshot == null || (snapshot.metadata.isFromCache && !snapshot.exists()) },
        mapSnapshot = { snapshot -> snapshot.isPremium() },
    )

    override suspend fun fetchPremium(uid: String): Boolean = document(uid).get(Source.SERVER).await().isPremium()

    private fun DocumentSnapshot?.isPremium(): Boolean = this?.getBoolean(FIELD_IS_PREMIUM) == true

    private companion object {
        const val USERS_COLLECTION = "users"
        const val ENTITLEMENT_COLLECTION = "entitlement"
        const val PREMIUM_DOCUMENT = "premium"
        const val FIELD_IS_PREMIUM = "isPremium"
    }
}
