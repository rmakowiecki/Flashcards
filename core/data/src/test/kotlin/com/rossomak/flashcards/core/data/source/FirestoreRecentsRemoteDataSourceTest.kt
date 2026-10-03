package com.rossomak.flashcards.core.data.source

import app.cash.turbine.test
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rossomak.flashcards.core.data.model.RecentSessionEntryDto
import com.rossomak.flashcards.core.data.model.RecentsStateDto
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRecentsRemoteDataSourceTest {

    private val firestore: FirebaseFirestore = mockk()
    private val firebaseAuth: FirebaseAuth = mockk()

    private fun createDataSource(): FirestoreRecentsRemoteDataSource = FirestoreRecentsRemoteDataSource(firestore, firebaseAuth)

    @Test
    fun `observeRecents completes silently without touching Firestore when no user is authenticated`() = runTest {
        every { firebaseAuth.currentUser } returns null

        createDataSource().observeRecents().test {
            awaitComplete()
        }

        verify(exactly = 0) { firestore.document(any()) }
    }

    @Test
    fun `a Rated and a Fast entry parse into their DTOs`() = runTest {
        val listenerSlot = listenToRecentsDocument()
        val snapshot = snapshotWithEntries(
            mapOf(
                RATED_SESSION_ID to ratedEntryFields(),
                FAST_SESSION_ID to ratedEntryFields(FAST_SESSION_ID) - "voiceAnswering" + mapOf("studyMode" to FAST_MODE, "readAloud" to true, "sourceType" to QUICK_SOURCE),
            ),
        )

        createDataSource().observeRecents().test {
            listenerSlot.captured.onEvent(snapshot, null)

            awaitItem().entries shouldBe listOf(
                ratedEntryDto(),
                ratedEntryDto(FAST_SESSION_ID).copy(studyMode = FAST_MODE, voiceAnswering = null, readAloud = true, sourceType = QUICK_SOURCE),
            )
            cancelAndIgnoreRemainingEvents()
        }

        verifyListenerRegistered()
    }

    @Test
    fun `an entry missing a required field or holding the wrong type is skipped and the rest still emit`() = runTest {
        val listenerSlot = listenToRecentsDocument()
        val snapshot = snapshotWithEntries(
            mapOf(
                RATED_SESSION_ID to ratedEntryFields(),
                "missing-category" to ratedEntryFields("missing-category") - "categoryId",
                "string-card-count" to ratedEntryFields("string-card-count") + mapOf("cardCount" to "3"),
            ),
        )

        createDataSource().observeRecents().test {
            listenerSlot.captured.onEvent(snapshot, null)

            awaitItem().entries shouldBe listOf(ratedEntryDto())
            cancelAndIgnoreRemainingEvents()
        }

        verifyListenerRegistered()
    }

    @Test
    fun `a missing document emits no entries`() = runTest {
        val listenerSlot = listenToRecentsDocument()
        val snapshot = snapshotWithEntries(null)

        createDataSource().observeRecents().test {
            listenerSlot.captured.onEvent(snapshot, null)

            awaitItem() shouldBe RecentsStateDto()
            cancelAndIgnoreRemainingEvents()
        }

        verifyListenerRegistered()
    }

    private val document: DocumentReference = mockk()

    private fun verifyListenerRegistered() {
        verify(exactly = 1) { firestore.document("users/$USER_ID/recents/state") }
        verify(exactly = 1) { document.addSnapshotListener(any<Executor>(), any()) }
    }

    private fun listenToRecentsDocument(): CapturingSlot<EventListener<DocumentSnapshot>> {
        val listenerSlot = slot<EventListener<DocumentSnapshot>>()
        val user: FirebaseUser = mockk { every { uid } returns USER_ID }
        every { document.addSnapshotListener(any<Executor>(), capture(listenerSlot)) } returns mockk<ListenerRegistration>(relaxed = true)
        every { firebaseAuth.currentUser } returns user
        every { firestore.document("users/$USER_ID/recents/state") } returns document
        return listenerSlot
    }

    private fun snapshotWithEntries(entries: Map<String, Map<String, Any?>>?): DocumentSnapshot {
        val snapshot: DocumentSnapshot = mockk()
        every { snapshot.get("entries") } returns entries
        return snapshot
    }

    // Integers arrive from Firestore as Long, so the fixture holds them as Long.
    private fun ratedEntryFields(sessionId: String = RATED_SESSION_ID): Map<String, Any?> = mapOf(
        "sessionId" to sessionId,
        "startTimestamp" to START_TIMESTAMP,
        "durationSeconds" to DURATION_SECONDS.toLong(),
        "studyMode" to "Rated",
        "voiceAnswering" to true,
        "sourceType" to "SingleSubcategory",
        "categoryId" to CATEGORY_ID,
        "subcategoryIds" to listOf(SUBCATEGORY_ID),
        "cardCount" to CARD_COUNT.toLong(),
        "xpTotal" to XP_TOTAL.toLong(),
    )

    private fun ratedEntryDto(sessionId: String = RATED_SESSION_ID) = RecentSessionEntryDto(
        sessionId = sessionId,
        startTimestamp = START_TIMESTAMP,
        durationSeconds = DURATION_SECONDS,
        studyMode = "Rated",
        voiceAnswering = true,
        readAloud = null,
        sourceType = "SingleSubcategory",
        categoryId = CATEGORY_ID,
        subcategoryIds = listOf(SUBCATEGORY_ID),
        cardCount = CARD_COUNT,
        xpTotal = XP_TOTAL,
    )

    private companion object {
        const val USER_ID = "user-1"
        const val RATED_SESSION_ID = "session-rated"
        const val FAST_SESSION_ID = "session-fast"
        const val FAST_MODE = "Fast"
        const val QUICK_SOURCE = "Quick"
        const val CATEGORY_ID = "android"
        const val SUBCATEGORY_ID = "compose"
        const val DURATION_SECONDS = 300
        const val CARD_COUNT = 12
        const val XP_TOTAL = -40
        val START_TIMESTAMP = Timestamp(1_790_000_000L, 0)
    }
}
