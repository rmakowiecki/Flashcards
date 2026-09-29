package com.rossomak.flashcards.core.data.source

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.rossomak.flashcards.core.domain.model.CurationAction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCurationRemoteDataSourceTest {

    private val firestore: FirebaseFirestore = mockk()
    private val firebaseAuth: FirebaseAuth = mockk()
    private val document: DocumentReference = mockk()

    init {
        every { firebaseAuth.currentUser?.uid } returns USER_ID
        val collection: CollectionReference = mockk()
        every { firestore.collection("users/$USER_ID/curationRequests") } returns collection
        every { collection.document(CARD_ID) } returns document
    }

    private fun createDataSource() = FirestoreCurationRemoteDataSource(firestore, firebaseAuth)

    @Test
    fun `a write the server never acknowledges counts as done after the offline timeout`() = runTest {
        every { document.set(any(), any<SetOptions>()) } returns pendingTask()

        createDataSource().upsertCurationActions(CARD_ID, SUBCATEGORY_ID, setOf(CurationAction.Delete))

        currentTime shouldBe OFFLINE_WRITE_TIMEOUT.inWholeMilliseconds
    }

    @Test
    fun `a write that fails before the timeout still throws`() = runTest {
        val failure = IllegalStateException("permission denied")
        every { document.set(any(), any<SetOptions>()) } returns failedTask(failure)

        val thrown = shouldThrow<IllegalStateException> {
            createDataSource().upsertCurationActions(CARD_ID, SUBCATEGORY_ID, setOf(CurationAction.Delete))
        }

        thrown.message shouldBe failure.message
    }

    private fun pendingTask(): Task<Void> = mockk(relaxed = true) {
        every { isComplete } returns false
    }

    private fun failedTask(exception: Exception): Task<Void> = mockk {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { this@mockk.exception } returns exception
    }

    private companion object {
        const val USER_ID = "user-1"
        const val CARD_ID = "card-1"
        const val SUBCATEGORY_ID = "android-compose"
        val OFFLINE_WRITE_TIMEOUT = 5.seconds
    }
}
