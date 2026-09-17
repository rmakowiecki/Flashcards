package com.rossomak.flashcards.core.data.source

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSummaryRemoteDataSourceTest {

    private val firestore: FirebaseFirestore = mockk()
    private val firebaseAuth: FirebaseAuth = mockk()

    private fun createDataSource(): ProgressSummaryRemoteDataSource = ProgressSummaryRemoteDataSource(firestore, firebaseAuth)

    @Test
    fun `observeSummary completes silently without touching Firestore when no user is authenticated`() = runTest {
        every { firebaseAuth.currentUser } returns null

        createDataSource().observeSummary().test {
            awaitComplete()
        }
        verify(exactly = 0) { firestore.document(any()) }
    }
}
