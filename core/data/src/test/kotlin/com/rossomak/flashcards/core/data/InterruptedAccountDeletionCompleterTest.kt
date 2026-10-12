package com.rossomak.flashcards.core.data

import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.repository.DeletedAccountQueuePurger
import com.rossomak.flashcards.core.data.source.AccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakeDeadLetteredSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterruptedAccountDeletionCompleterTest {

    private val accountDeletionMarkerLocalDataSource: AccountDeletionMarkerLocalDataSource = mockk(relaxUnitFun = true) {
        every { read() } returns DELETED_UID
    }
    private val authRemoteDataSource: AuthRemoteDataSource = mockk {
        every { getCurrentUser() } returns userWith(DELETED_UID)
        every { signOut() } just runs
    }
    private val pendingSessions = FakePendingSessionSubmissionLocalDataSource()
    private val deadLetters = FakeDeadLetteredSessionSubmissionLocalDataSource()

    private fun TestScope.complete() {
        InterruptedAccountDeletionCompleter(
            applicationScope = backgroundScope,
            accountDeletionMarkerLocalDataSource = accountDeletionMarkerLocalDataSource,
            authRemoteDataSource = authRemoteDataSource,
            deletedAccountQueuePurger = DeletedAccountQueuePurger(pendingSessions, deadLetters),
        ).complete()
    }

    @Test
    fun `no marker changes nothing`() = runTest(UnconfinedTestDispatcher()) {
        every { accountDeletionMarkerLocalDataSource.read() } returns null

        complete()

        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.read() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
        verify(exactly = 0) { accountDeletionMarkerLocalDataSource.clear() }
    }

    @Test
    fun `the marked User still signed in is signed out, their queue removed and the marker cleared`() = runTest(UnconfinedTestDispatcher()) {
        val otherPending = pendingSubmission("session-2", OTHER_UID)
        pendingSessions.seed(pendingSubmission("session-1", DELETED_UID))
        pendingSessions.seed(otherPending)

        complete()

        pendingSessions.listAll() shouldBe listOf(otherPending)
        verify(exactly = 1) { authRemoteDataSource.signOut() }
        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.clear() }
    }

    @Test
    fun `another signed-in User stays signed in while the marked queue is still removed`() = runTest(UnconfinedTestDispatcher()) {
        every { authRemoteDataSource.getCurrentUser() } returns userWith(OTHER_UID)
        pendingSessions.seed(pendingSubmission("session-1", DELETED_UID))

        complete()

        pendingSessions.listAll() shouldBe emptyList()
        verify(exactly = 1) { authRemoteDataSource.getCurrentUser() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.clear() }
    }

    private fun pendingSubmission(sessionId: String, uid: String): PendingSessionSubmissionDto = PendingSessionSubmissionDto(
        id = sessionId,
        uid = uid,
        mode = "Fast",
        startedAtEpochMillis = 0L,
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf("sub-1"),
        subcategoryNames = listOf("Subcategory"),
        sourceType = "SingleSubcategory",
        cardResults = emptyList(),
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
    )

    private fun userWith(uid: String) = AuthUser(uid = uid, email = null, displayName = null, photoUrl = null)

    private companion object {
        const val DELETED_UID = "uid-deleted"
        const val OTHER_UID = "uid-other"
    }
}
