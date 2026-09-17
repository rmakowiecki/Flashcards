package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.ProgressSummaryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressSummaryDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.ProgressSummaryRemoteDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCardProgressRepositoryTest {

    private val remoteDataSource: CardProgressRemoteDataSource = mockk()
    private val progressSummaryRemoteDataSource: ProgressSummaryRemoteDataSource = mockk()

    private fun createRepository(): DefaultCardProgressRepository =
        DefaultCardProgressRepository(remoteDataSource, progressSummaryRemoteDataSource)

    @Test
    fun `getProgress maps the dto to domain keyed by the requested subcategory id`() = runTest {
        val subcategoryId = "sub-1"
        val dto = SubcategoryProgressDto(
            categoryId = "cat-1",
            cards = mapOf(
                "card-1" to CardProgressEntryDto(
                    state = FlashcardStudyProgressState.Mastered.name,
                    firstStudiedAt = Timestamp(Date(1_000L)),
                ),
            ),
        )
        coEvery { remoteDataSource.getProgress(subcategoryId) } returns dto

        val result = createRepository().getProgress(subcategoryId)

        result.isSuccess shouldBe true
        val progress = result.getOrThrow()
        progress?.subcategoryId shouldBe subcategoryId
        progress?.categoryId shouldBe "cat-1"
        progress?.cards?.keys shouldBe setOf("card-1")
        coVerify(exactly = 1) { remoteDataSource.getProgress(subcategoryId) }
    }

    @Test
    fun `getProgress returns success with null for an absent document`() = runTest {
        val subcategoryId = "sub-1"
        coEvery { remoteDataSource.getProgress(subcategoryId) } returns null

        val result = createRepository().getProgress(subcategoryId)

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe null
        coVerify(exactly = 1) { remoteDataSource.getProgress(subcategoryId) }
    }

    @Test
    fun `getProgress wraps a data source failure in a failure result`() = runTest {
        val subcategoryId = "sub-1"
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getProgress(subcategoryId) } throws error

        val result = createRepository().getProgress(subcategoryId)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getProgress(subcategoryId) }
    }

    @Test
    fun `getProgress rethrows cancellation instead of wrapping it`() = runTest {
        val subcategoryId = "sub-1"
        coEvery { remoteDataSource.getProgress(subcategoryId) } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().getProgress(subcategoryId) }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getProgress(subcategoryId) }
    }

    @Test
    fun `observeProgressSummary maps the dto to domain keyed by subcategory id`() = runTest {
        val subcategoryId = "sub-1"
        val masteredCount = 3
        val studiedCount = 5
        val dto = ProgressSummaryDto(
            subcategories = mapOf(subcategoryId to SubcategoryProgressSummaryDto(masteredCount = masteredCount, studiedCount = studiedCount)),
        )
        every { progressSummaryRemoteDataSource.observeSummary() } returns flowOf(dto)

        createRepository().observeProgressSummary().test {
            val subcategory = awaitItem()?.subcategories?.getValue(subcategoryId)
            subcategory?.masteredCount shouldBe masteredCount
            subcategory?.studiedCount shouldBe studiedCount
            awaitComplete()
        }
    }

    @Test
    fun `observeProgressSummary emits null for an absent document`() = runTest {
        every { progressSummaryRemoteDataSource.observeSummary() } returns flowOf(null)

        createRepository().observeProgressSummary().test {
            awaitItem() shouldBe null
            awaitComplete()
        }
    }

    @Test
    fun `observeProgressSummary completes silently on permission denied without retrying`() = runTest {
        val attempts = AtomicInteger(0)
        every { progressSummaryRemoteDataSource.observeSummary() } returns flow {
            attempts.incrementAndGet()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        createRepository().observeProgressSummary().test {
            awaitComplete()
        }
        attempts.get() shouldBe 1
    }

    @Test
    fun `observeProgressSummary does not retry a non-transient Firestore failure and propagates it`() = runTest {
        val attempts = AtomicInteger(0)
        val error = FirebaseFirestoreException("bad query", FirebaseFirestoreException.Code.INVALID_ARGUMENT)
        every { progressSummaryRemoteDataSource.observeSummary() } returns flow {
            attempts.incrementAndGet()
            throw error
        }

        createRepository().observeProgressSummary().test {
            awaitError() shouldBe error
        }
        attempts.get() shouldBe 1
    }

    @Test
    fun `observeProgressSummary does not retry a non-Firestore failure and propagates it`() = runTest {
        val attempts = AtomicInteger(0)
        val error = IllegalStateException("No authenticated user")
        every { progressSummaryRemoteDataSource.observeSummary() } returns flow {
            attempts.incrementAndGet()
            throw error
        }

        createRepository().observeProgressSummary().test {
            awaitError() shouldBe error
        }
        attempts.get() shouldBe 1
    }

    @Test
    fun `observeProgressSummary retries and recovers after a non-permission listener failure`() = runTest {
        val subcategoryId = "sub-1"
        val attempts = AtomicInteger(0)
        every { progressSummaryRemoteDataSource.observeSummary() } returns flow {
            if (attempts.getAndIncrement() == 0) {
                throw FirebaseFirestoreException("listener dropped", FirebaseFirestoreException.Code.UNAVAILABLE)
            } else {
                emit(ProgressSummaryDto(subcategories = mapOf(subcategoryId to SubcategoryProgressSummaryDto(masteredCount = 1, studiedCount = 2))))
            }
        }

        createRepository().observeProgressSummary().test {
            val subcategory = awaitItem()?.subcategories?.getValue(subcategoryId)
            subcategory?.masteredCount shouldBe 1
            subcategory?.studiedCount shouldBe 2
            awaitComplete()
        }
        attempts.get() shouldBe 2
    }
}
