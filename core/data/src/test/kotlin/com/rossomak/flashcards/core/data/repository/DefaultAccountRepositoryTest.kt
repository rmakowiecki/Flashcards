package com.rossomak.flashcards.core.data.repository

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
import com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED
import com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE
import com.rossomak.flashcards.core.data.model.DeadLetteredSessionSubmissionDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionDto
import com.rossomak.flashcards.core.data.source.AccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.AccountDeletionRemoteDataSource
import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakeDeadLetteredSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason.NoConnection
import com.rossomak.flashcards.core.domain.model.AccountDeletionFailureReason.ServiceError
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult.Deleted
import com.rossomak.flashcards.core.domain.model.AccountDeletionResult.Failed
import com.rossomak.flashcards.core.domain.model.AuthUser
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAccountRepositoryTest {

    private val accountDeletionRemoteDataSource: AccountDeletionRemoteDataSource = mockk()
    private val authRemoteDataSource: AuthRemoteDataSource = mockk {
        every { getCurrentUser() } returns deletedUser
        every { signOut() } just runs
    }
    private val accountDeletionMarkerLocalDataSource: AccountDeletionMarkerLocalDataSource = mockk(relaxUnitFun = true) {
        every { write(any()) } returns true
    }
    private val pendingSessions = FakePendingSessionSubmissionLocalDataSource()
    private val deadLetters = FakeDeadLetteredSessionSubmissionLocalDataSource()
    private var isInternetAvailable = true

    private fun createRepository(pendingSessionSubmissionLocalDataSource: PendingSessionSubmissionLocalDataSource = pendingSessions) = DefaultAccountRepository(
        accountDeletionRemoteDataSource = accountDeletionRemoteDataSource,
        authRemoteDataSource = authRemoteDataSource,
        accountDeletionMarkerLocalDataSource = accountDeletionMarkerLocalDataSource,
        deletedAccountQueuePurger = DeletedAccountQueuePurger(pendingSessionSubmissionLocalDataSource, deadLetters),
        networkAvailabilityGateway = { isInternetAvailable },
    )

    @Test
    fun `offline fails with NoConnection without calling the server`() = runTest {
        isInternetAvailable = false

        createRepository().deleteAccount() shouldBe Failed(NoConnection)

        coVerify(exactly = 0) { accountDeletionRemoteDataSource.deleteAccount() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
        verify(exactly = 0) { accountDeletionMarkerLocalDataSource.write(any()) }
    }

    @Test
    fun `nobody signed in fails with ServiceError without calling the server`() = runTest {
        every { authRemoteDataSource.getCurrentUser() } returns null

        createRepository().deleteAccount() shouldBe Failed(ServiceError)

        verify(exactly = 1) { authRemoteDataSource.getCurrentUser() }
        coVerify(exactly = 0) { accountDeletionRemoteDataSource.deleteAccount() }
    }

    @Test
    fun `a successful deletion marks the call, removes only the deleted User's queue, signs out and clears the mark`() = runTest {
        val otherPending = pendingSubmission("session-2", OTHER_UID)
        val otherDeadLetter = deadLettered("session-4", OTHER_UID)
        pendingSessions.seed(pendingSubmission("session-1", DELETED_UID))
        pendingSessions.seed(otherPending)
        deadLetters.append(deadLettered("session-3", DELETED_UID))
        deadLetters.append(otherDeadLetter)
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.success(Unit)

        createRepository().deleteAccount() shouldBe Deleted

        pendingSessions.listAll() shouldBe listOf(otherPending)
        deadLetters.listAll() shouldBe listOf(otherDeadLetter)
        verifyOrder {
            accountDeletionMarkerLocalDataSource.write(DELETED_UID)
            authRemoteDataSource.signOut()
            accountDeletionMarkerLocalDataSource.clear()
        }
        coVerify(exactly = 1) { accountDeletionRemoteDataSource.deleteAccount() }
        verify(exactly = 1) { authRemoteDataSource.signOut() }
    }

    @Test
    fun `a queue that cannot be purged still removes the dead letters and signs out`() = runTest {
        val unreadableQueue: PendingSessionSubmissionLocalDataSource = mockk {
            coEvery { removeAllForUser(DELETED_UID) } throws IOException("unreadable")
        }
        deadLetters.append(deadLettered("session-3", DELETED_UID))
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.success(Unit)

        createRepository(pendingSessionSubmissionLocalDataSource = unreadableQueue).deleteAccount() shouldBe Deleted

        deadLetters.listAll() shouldBe emptyList()
        coVerify(exactly = 1) { unreadableQueue.removeAllForUser(DELETED_UID) }
        verify(exactly = 1) { authRemoteDataSource.signOut() }
        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.clear() }
    }

    @Test
    fun `a server error fails with ServiceError, clears the mark and changes nothing else on the device`() = runTest {
        val pending = pendingSubmission("session-1", DELETED_UID)
        pendingSessions.seed(pending)
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.failure(RuntimeException("INTERNAL"))

        createRepository().deleteAccount() shouldBe Failed(ServiceError)

        pendingSessions.listAll() shouldBe listOf(pending)
        verifyOrder {
            accountDeletionMarkerLocalDataSource.write(DELETED_UID)
            accountDeletionMarkerLocalDataSource.clear()
        }
        coVerify(exactly = 1) { accountDeletionRemoteDataSource.deleteAccount() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
    }

    @Test
    fun `a cancelled call keeps the mark, since the server may still delete the account`() = runTest {
        val callStarted = CompletableDeferred<Unit>()
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } coAnswers {
            callStarted.complete(Unit)
            awaitCancellation()
        }

        val deletion = launch { createRepository().deleteAccount() }
        callStarted.await()
        deletion.cancelAndJoin()

        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.write(DELETED_UID) }
        verify(exactly = 0) { accountDeletionMarkerLocalDataSource.clear() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
    }

    @Test
    fun `a connection failure fails with NoConnection, keeps the mark and does not sign out`() = runTest {
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.failure(RuntimeException("INTERNAL", IOException("connection reset")))

        createRepository().deleteAccount() shouldBe Failed(NoConnection)

        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.write(DELETED_UID) }
        verify(exactly = 0) { accountDeletionMarkerLocalDataSource.clear() }
        coVerify(exactly = 1) { accountDeletionRemoteDataSource.deleteAccount() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
    }

    @Test
    fun `a server timeout fails with ServiceError and keeps the mark, since the server may have deleted the account`() = runTest {
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.failure(functionsException(DEADLINE_EXCEEDED))

        createRepository().deleteAccount() shouldBe Failed(ServiceError)

        verify(exactly = 0) { accountDeletionMarkerLocalDataSource.clear() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
    }

    @Test
    fun `an unavailable server keeps the mark`() = runTest {
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.failure(functionsException(UNAVAILABLE))

        createRepository().deleteAccount() shouldBe Failed(ServiceError)

        verify(exactly = 0) { accountDeletionMarkerLocalDataSource.clear() }
    }

    @Test
    fun `a definite server rejection clears the mark`() = runTest {
        coEvery { accountDeletionRemoteDataSource.deleteAccount() } returns Result.failure(functionsException(UNAUTHENTICATED))

        createRepository().deleteAccount() shouldBe Failed(ServiceError)

        verify(exactly = 1) { accountDeletionMarkerLocalDataSource.clear() }
    }

    @Test
    fun `a marker that cannot be saved fails with ServiceError without calling the server`() = runTest {
        every { accountDeletionMarkerLocalDataSource.write(DELETED_UID) } returns false

        createRepository().deleteAccount() shouldBe Failed(ServiceError)

        coVerify(exactly = 0) { accountDeletionRemoteDataSource.deleteAccount() }
        verify(exactly = 0) { authRemoteDataSource.signOut() }
    }

    // A strict mock: the classifier walks `cause`, so both properties must be stubbed.
    private fun functionsException(code: FirebaseFunctionsException.Code): FirebaseFunctionsException {
        val exception: FirebaseFunctionsException = mockk()
        every { exception.code } returns code
        every { exception.cause } returns null
        return exception
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

    private fun deadLettered(sessionId: String, uid: String): DeadLetteredSessionSubmissionDto = DeadLetteredSessionSubmissionDto(
        entry = pendingSubmission(sessionId, uid),
        failureCode = "INVALID_ARGUMENT",
        failureMessage = "rejected",
        deadLetteredAtEpochMillis = 0L,
    )

    private companion object {
        const val DELETED_UID = "uid-deleted"
        const val OTHER_UID = "uid-other"
        val deletedUser = AuthUser(uid = DELETED_UID, email = null, displayName = null, photoUrl = null)
    }
}
