package com.rossomak.flashcards.core.data.source

import app.cash.turbine.test
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreUserFavoritesRemoteDataSourceTest {

    private val firestore: FirebaseFirestore = mockk()
    private val firebaseAuth: FirebaseAuth = mockk()

    private fun createDataSource(): FirestoreUserFavoritesRemoteDataSource = FirestoreUserFavoritesRemoteDataSource(firestore, firebaseAuth)

    @Test
    fun `observeFavorites completes silently without touching Firestore when no user is authenticated`() = runTest {
        every { firebaseAuth.currentUser } returns null

        createDataSource().observeFavorites().test {
            awaitComplete()
        }
        verify(exactly = 0) { firestore.document(any()) }
    }

    @Test
    fun `a favorite whose server timestamp is still pending is read through its estimate and kept`() = runTest {
        val listenerSlot = slot<EventListener<DocumentSnapshot>>()
        val user: FirebaseUser = mockk { every { uid } returns USER_ID }
        val document: DocumentReference = mockk {
            every { addSnapshotListener(any<Executor>(), any<MetadataChanges>(), capture(listenerSlot)) } returns mockk<ListenerRegistration>(relaxed = true)
        }
        every { firebaseAuth.currentUser } returns user
        every { firestore.document("users/$USER_ID/favorites/state") } returns document
        val estimatedTimestamp = Timestamp(ESTIMATED_SECONDS, 0)
        val snapshot: DocumentSnapshot = mockk {
            every { get("categories", ServerTimestampBehavior.ESTIMATE) } returns mapOf(CATEGORY_ID to estimatedTimestamp)
            every { get("subcategories", ServerTimestampBehavior.ESTIMATE) } returns null
        }

        createDataSource().observeFavorites().test {
            listenerSlot.captured.onEvent(snapshot, null)

            val favorites = awaitItem()
            favorites.categories shouldBe mapOf(CATEGORY_ID to estimatedTimestamp)
            favorites.subcategories shouldBe emptyMap()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unfavoriting merges a null tombstone instead of deleting the key`() = runTest {
        val written = slot<Map<String, Any?>>()
        val document = documentCapturing(written)

        createDataSource().setCategoryFavorite(CATEGORY_ID, isFavorite = false)

        written.captured shouldBe mapOf("categories" to mapOf(CATEGORY_ID to null))
        verify { document.set(any(), SetOptions.merge()) }
    }

    @Test
    fun `favoriting merges a server timestamp for every id in one write`() = runTest {
        val written = slot<Map<String, Any?>>()
        val document = documentCapturing(written)

        createDataSource().setSubcategoriesFavorite(setOf("compose", "coroutines"), isFavorite = true)

        written.captured shouldBe mapOf(
            "subcategories" to mapOf(
                "compose" to FieldValue.serverTimestamp(),
                "coroutines" to FieldValue.serverTimestamp(),
            ),
        )
        verify(exactly = 1) { document.set(any(), SetOptions.merge()) }
    }

    private fun documentCapturing(written: io.mockk.CapturingSlot<Map<String, Any?>>): DocumentReference {
        val user: FirebaseUser = mockk { every { uid } returns USER_ID }
        val writeTask: Task<Void> = mockk(relaxed = true)
        val document: DocumentReference = mockk {
            every { set(capture(written), any<SetOptions>()) } returns writeTask
        }
        every { firebaseAuth.currentUser } returns user
        every { firestore.document("users/$USER_ID/favorites/state") } returns document
        return document
    }

    private companion object {
        const val USER_ID = "user-1"
        const val CATEGORY_ID = "android"
        const val ESTIMATED_SECONDS = 1_790_000_000L
    }
}
