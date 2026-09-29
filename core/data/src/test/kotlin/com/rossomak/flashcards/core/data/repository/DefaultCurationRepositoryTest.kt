package com.rossomak.flashcards.core.data.repository

import com.google.firebase.Timestamp
import com.rossomak.flashcards.core.data.model.CurationActionEntryDto
import com.rossomak.flashcards.core.data.model.CurationRequestDto
import com.rossomak.flashcards.core.data.source.CurationRemoteDataSource
import com.rossomak.flashcards.core.data.source.CurationWriteResult
import com.rossomak.flashcards.core.domain.model.CurationAction
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCurationRepositoryTest {

    private val remoteDataSource: CurationRemoteDataSource = mockk()

    private fun createRepository(): DefaultCurationRepository =
        DefaultCurationRepository(remoteDataSource)

    @Test
    fun `getCurationRequests maps dtos to domain keyed by card id`() = runTest {
        val cardId = "card-1"
        val cardIds = listOf(cardId)
        val flaggedAt = Timestamp(Date(1_000L))
        val dto = CurationRequestDto(
            subcategoryId = "sub-1",
            actions = mapOf(CurationAction.Delete.name to CurationActionEntryDto(flaggedAt = flaggedAt)),
        )
        coEvery { remoteDataSource.getCurationRequests(cardIds) } returns mapOf(cardId to dto)

        val result = createRepository().getCurationRequests(cardIds)

        result.isSuccess shouldBe true
        val request = result.getOrThrow().getValue(cardId)
        request.cardId shouldBe cardId
        request.subcategoryId shouldBe dto.subcategoryId
        request.actions.keys shouldBe setOf(CurationAction.Delete)
        coVerify(exactly = 1) { remoteDataSource.getCurationRequests(cardIds) }
    }

    @Test
    fun `getCurationRequests wraps data source failure in failure result`() = runTest {
        val cardIds = listOf("card-1")
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getCurationRequests(cardIds) } throws error

        val result = createRepository().getCurationRequests(cardIds)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getCurationRequests(cardIds) }
    }

    @Test
    fun `getCurationRequests rethrows cancellation instead of wrapping it`() = runTest {
        val cardIds = listOf("card-1")
        coEvery { remoteDataSource.getCurationRequests(cardIds) } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().getCurationRequests(cardIds) }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getCurationRequests(cardIds) }
    }

    @Test
    fun `upsertCurationActions lazily fetches known actions once before its first write`() = runTest {
        val cardId = "card-1"
        val subcategoryId = "sub-1"
        val actions = setOf(CurationAction.DifficultyTooHard, CurationAction.WrongTags)
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } returns emptyMap()
        coEvery { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) } returns CurationWriteResult.Confirmed

        val result = createRepository().upsertCurationActions(cardId, subcategoryId, actions)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getCurationRequests(listOf(cardId)) }
        coVerify(exactly = 1) { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) }
    }

    @Test
    fun `upsertCurationActions wraps data source failure in failure result`() = runTest {
        val cardId = "card-1"
        val subcategoryId = "sub-1"
        val actions = setOf(CurationAction.DifficultyTooHard)
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } returns emptyMap()
        coEvery { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) } throws error

        val result = createRepository().upsertCurationActions(cardId, subcategoryId, actions)

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) }
    }

    @Test
    fun `upsertCurationActions wraps a failed lazy fetch in failure result without writing`() = runTest {
        val cardId = "card-1"
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } throws error

        val result = createRepository().upsertCurationActions(cardId, "sub-1", setOf(CurationAction.WrongTags))

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 0) { remoteDataSource.upsertCurationActions(any(), any(), any()) }
    }

    @Test
    fun `upsertCurationActions skips data source call when resubmitting the same set already written`() = runTest {
        val cardId = "card-1"
        val subcategoryId = "sub-1"
        val actions = setOf(CurationAction.DifficultyTooHard, CurationAction.WrongTags)
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } returns emptyMap()
        coEvery { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) } returns CurationWriteResult.Confirmed
        val repository = createRepository()
        repository.upsertCurationActions(cardId, subcategoryId, actions)

        val result = repository.upsertCurationActions(cardId, subcategoryId, actions)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getCurationRequests(listOf(cardId)) }
        coVerify(exactly = 1) { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) }
    }

    @Test
    fun `upsertCurationActions skips data source write when requested set is already flagged remotely`() = runTest {
        val cardId = "card-1"
        val flaggedAt = Timestamp(Date(1_000L))
        val dto = CurationRequestDto(
            subcategoryId = "sub-1",
            actions = mapOf(CurationAction.WrongTags.name to CurationActionEntryDto(flaggedAt = flaggedAt)),
        )
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } returns mapOf(cardId to dto)

        val result = createRepository().upsertCurationActions(cardId, "sub-1", setOf(CurationAction.WrongTags))

        result.isSuccess shouldBe true
        coVerify(exactly = 0) { remoteDataSource.upsertCurationActions(any(), any(), any()) }
    }

    @Test
    fun `upsertCurationActions still writes when a new action joins an already known set`() = runTest {
        val cardId = "card-1"
        val subcategoryId = "sub-1"
        val known = setOf(CurationAction.WrongTags)
        val requested = setOf(CurationAction.WrongTags, CurationAction.NeedsCodeExample)
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } returns emptyMap()
        coEvery { remoteDataSource.upsertCurationActions(cardId, subcategoryId, known) } returns CurationWriteResult.Confirmed
        coEvery { remoteDataSource.upsertCurationActions(cardId, subcategoryId, requested) } returns CurationWriteResult.Confirmed
        val repository = createRepository()
        repository.upsertCurationActions(cardId, subcategoryId, known)

        val result = repository.upsertCurationActions(cardId, subcategoryId, requested)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getCurationRequests(listOf(cardId)) }
        coVerify(exactly = 1) { remoteDataSource.upsertCurationActions(cardId, subcategoryId, requested) }
    }

    @Test
    fun `upsertCurationActions writes again when the previous write was only queued`() = runTest {
        val cardId = "card-1"
        val subcategoryId = "sub-1"
        val actions = setOf(CurationAction.WrongTags)
        coEvery { remoteDataSource.getCurationRequests(listOf(cardId)) } returns emptyMap()
        coEvery { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) } returns CurationWriteResult.Queued
        val repository = createRepository()
        repository.upsertCurationActions(cardId, subcategoryId, actions)

        val result = repository.upsertCurationActions(cardId, subcategoryId, actions)

        result.isSuccess shouldBe true
        coVerify(exactly = 2) { remoteDataSource.upsertCurationActions(cardId, subcategoryId, actions) }
    }
}
