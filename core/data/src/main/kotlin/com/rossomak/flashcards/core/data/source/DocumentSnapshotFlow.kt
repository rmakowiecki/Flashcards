package com.rossomak.flashcards.core.data.source

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Follows this document with a snapshot listener, closing with the listener's error. Snapshots are
 * delivered off the main thread so [mapSnapshot] never costs a UI frame, one at a time: Firestore submits
 * every snapshot to the executor separately, so a parallel one could finish mapping an older snapshot
 * last and leave it as the latest value.
 *
 * @param skipSnapshot drops a snapshot without emitting anything for it.
 */
internal fun <T> DocumentReference.snapshotFlow(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
    skipSnapshot: (DocumentSnapshot?) -> Boolean = { false },
    mapSnapshot: (DocumentSnapshot?) -> T,
): Flow<T> = callbackFlow {
    val executor = Dispatchers.Default.limitedParallelism(1).asExecutor()
    val registration = addSnapshotListener(executor, metadataChanges) { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (skipSnapshot(snapshot)) return@addSnapshotListener
        trySend(mapSnapshot(snapshot))
    }
    awaitClose { registration.remove() }
}
