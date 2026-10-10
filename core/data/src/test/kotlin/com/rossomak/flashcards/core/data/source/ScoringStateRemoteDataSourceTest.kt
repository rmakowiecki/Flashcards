package com.rossomak.flashcards.core.data.source

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.rossomak.flashcards.core.data.model.ScoringStateDto
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
class ScoringStateRemoteDataSourceTest {

    private val firestore: FirebaseFirestore = mockk()
    private val firebaseAuth: FirebaseAuth = mockk()
    private val document: DocumentReference = mockk()

    private fun createDataSource(): ScoringStateRemoteDataSource = ScoringStateRemoteDataSource(firestore, firebaseAuth)

    @Test
    fun `observeScoringState completes silently without touching Firestore when no user is authenticated`() = runTest {
        every { firebaseAuth.currentUser } returns null

        createDataSource().observeScoringState().test {
            awaitComplete()
        }

        verify(exactly = 1) { firebaseAuth.currentUser }
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `a snapshot maps every field into the DTO`() = runTest {
        val listenerSlot = listenToScoringStateDocument()

        createDataSource().observeScoringState().test {
            listenerSlot.captured.onEvent(existingSnapshot(SCORING_STATE, isFromCache = false), null)

            awaitItem() shouldBe SCORING_STATE
            cancelAndIgnoreRemainingEvents()
        }

        verifyListenerRegistered()
    }

    @Test
    fun `a missing document read from the cache is dropped and the server's confirmation emits null`() = runTest {
        val listenerSlot = listenToScoringStateDocument()

        createDataSource().observeScoringState().test {
            listenerSlot.captured.onEvent(missingSnapshot(isFromCache = true), null)
            expectNoEvents()

            listenerSlot.captured.onEvent(missingSnapshot(isFromCache = false), null)
            awaitItem() shouldBe null
            cancelAndIgnoreRemainingEvents()
        }

        verifyListenerRegistered()
    }

    @Test
    fun `a cached document emits at once, and the server confirming the same values does not re-emit`() = runTest {
        val listenerSlot = listenToScoringStateDocument()

        createDataSource().observeScoringState().test {
            listenerSlot.captured.onEvent(existingSnapshot(SCORING_STATE, isFromCache = true), null)
            awaitItem() shouldBe SCORING_STATE

            listenerSlot.captured.onEvent(existingSnapshot(SCORING_STATE, isFromCache = false), null)
            expectNoEvents()

            val updated = SCORING_STATE.copy(xp = 2600, xpIntoCurrentLevel = 1600)
            listenerSlot.captured.onEvent(existingSnapshot(updated, isFromCache = false), null)
            awaitItem() shouldBe updated
            cancelAndIgnoreRemainingEvents()
        }

        verifyListenerRegistered()
    }

    @Test
    fun `a listener failure ends the flow with that failure`() = runTest {
        val listenerSlot = listenToScoringStateDocument()
        val error = FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        createDataSource().observeScoringState().test {
            listenerSlot.captured.onEvent(null, error)

            awaitError() shouldBe error
        }

        verifyListenerRegistered()
    }

    private fun verifyListenerRegistered() {
        verify(exactly = 1) { firebaseAuth.currentUser }
        verify(exactly = 1) { firestore.collection(COLLECTION_PATH) }
        verify(exactly = 1) { document.addSnapshotListener(any<Executor>(), MetadataChanges.INCLUDE, any<EventListener<DocumentSnapshot>>()) }
    }

    private fun listenToScoringStateDocument(): CapturingSlot<EventListener<DocumentSnapshot>> {
        val listenerSlot = slot<EventListener<DocumentSnapshot>>()
        val user: FirebaseUser = mockk { every { uid } returns USER_ID }
        val collection: CollectionReference = mockk { every { document("user-stats") } returns document }
        every { document.addSnapshotListener(any<Executor>(), any<MetadataChanges>(), capture(listenerSlot)) } returns mockk<ListenerRegistration>(relaxed = true)
        every { firebaseAuth.currentUser } returns user
        every { firestore.collection(COLLECTION_PATH) } returns collection
        return listenerSlot
    }

    private fun missingSnapshot(isFromCache: Boolean): DocumentSnapshot = mockk {
        every { exists() } returns false
        every { metadata.isFromCache } returns isFromCache
    }

    private fun existingSnapshot(scoringState: ScoringStateDto, isFromCache: Boolean): DocumentSnapshot = mockk {
        every { exists() } returns true
        every { metadata.isFromCache } returns isFromCache
        every { getLong("xp") } returns scoringState.xp
        every { getLong("level") } returns scoringState.level.toLong()
        every { getLong("xpIntoCurrentLevel") } returns scoringState.xpIntoCurrentLevel
        every { getLong("currentStreak") } returns scoringState.currentStreak.toLong()
        every { getLong("bestStreak") } returns scoringState.bestStreak.toLong()
        every { getString("lastStudyDate") } returns scoringState.lastStudyDate
        every { getString("goalMetDate") } returns scoringState.goalMetDate
        every { getLong("studiedSecondsOnLastStudyDate") } returns scoringState.studiedSecondsOnLastStudyDate
    }

    private companion object {
        const val USER_ID = "user-1"
        const val COLLECTION_PATH = "users/$USER_ID/progress"
        val SCORING_STATE = ScoringStateDto(
            xp = 2500,
            level = 2,
            xpIntoCurrentLevel = 1500,
            currentStreak = 3,
            bestStreak = 5,
            lastStudyDate = "2026-09-06",
            goalMetDate = "2026-09-05",
            studiedSecondsOnLastStudyDate = 900,
        )
    }
}
