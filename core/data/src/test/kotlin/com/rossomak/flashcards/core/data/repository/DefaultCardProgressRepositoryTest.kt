package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.model.ProgressSummaryDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressSummaryDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ProgressSummaryRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Failed
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Mastered
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Seen
import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressSummary
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCardProgressRepositoryTest {

    private val remoteDataSource: CardProgressRemoteDataSource = mockk()
    private val progressSummaryRemoteDataSource: ProgressSummaryRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = authUser(USER_ID) }
    private val pendingSessionQueue = FakePendingSessionSubmissionLocalDataSource()

    private fun createRepository(): DefaultCardProgressRepository = DefaultCardProgressRepository(
        remoteDataSource,
        progressSummaryRemoteDataSource,
        PendingSessionProjector(authRepository, pendingSessionQueue),
    )

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

    @Test
    fun `getProgress reads a card Mastered in a pending session as Mastered, stamped with the session start`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))

        val progress = createRepository().getProgress(SUBCATEGORY_ID).getOrThrow()

        progress?.categoryId shouldBe CATEGORY_ID
        progress?.cards shouldBe mapOf(CARD_ID to CardProgressEntry(Mastered, firstStudiedAt = SESSION_ONE_START, masteredAt = SESSION_ONE_START))
    }

    @Test
    fun `getProgress reads a card de-mastered in a later pending session as Failed`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))
        queue(ratedSession("session-2", SESSION_TWO_START, CARD_ID to Failed))

        val progress = createRepository().getProgress(SUBCATEGORY_ID).getOrThrow()

        progress?.cards?.getValue(CARD_ID)?.state shouldBe Failed
    }

    @Test
    fun `getProgress replays pending sessions oldest first, whatever their queue order`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-2", SESSION_TWO_START, CARD_ID to Failed))
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))

        val progress = createRepository().getProgress(SUBCATEGORY_ID).getOrThrow()

        progress?.cards?.getValue(CARD_ID)?.state shouldBe Failed
    }

    @Test
    fun `getProgress adds Seen from a Fast pending session only for cards with no record`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns remoteProgress(CARD_ID to Mastered)
        queue(fastSession("session-1", SESSION_ONE_START, CARD_ID, OTHER_CARD_ID))

        val progress = createRepository().getProgress(SUBCATEGORY_ID).getOrThrow()

        progress?.cards?.mapValues { (_, entry) -> entry.state } shouldBe mapOf(CARD_ID to Mastered, OTHER_CARD_ID to Seen)
    }

    @Test
    fun `getProgress ignores another User's pending sessions`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered), uid = OTHER_USER_ID)

        createRepository().getProgress(SUBCATEGORY_ID).getOrThrow() shouldBe null
    }

    @Test
    fun `getProgress drops the first User's projection once another User signs in`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))
        val repository = createRepository()

        authRepository.userToReturn = authUser(OTHER_USER_ID)

        repository.getProgress(SUBCATEGORY_ID).getOrThrow() shouldBe null
    }

    @Test
    fun `getProgress skips a malformed pending session and still projects the others`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        val malformed = ratedSession("session-1", SESSION_ONE_START, OTHER_CARD_ID to Mastered).toDto(USER_ID)
        pendingSessionQueue.seed(malformed.copy(cardResults = malformed.cardResults.map { it.copy(attemptsUsed = null) }))
        queue(ratedSession("session-2", SESSION_TWO_START, CARD_ID to Mastered))

        val progress = createRepository().getProgress(SUBCATEGORY_ID).getOrThrow()

        progress?.cards?.keys shouldBe setOf(CARD_ID)
    }

    @Test
    fun `getProgress stops replaying a pending session once it leaves the queue`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))
        val repository = createRepository()

        pendingSessionQueue.remove("session-1")

        repository.getProgress(SUBCATEGORY_ID).getOrThrow() shouldBe null
    }

    @Test
    fun `getProgress projects over an empty baseline when the remote read fails`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } throws IllegalStateException("offline, not cached")
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))

        val result = createRepository().getProgress(SUBCATEGORY_ID)

        result.getOrThrow()?.cards?.getValue(CARD_ID)?.state shouldBe Mastered
    }

    @Test
    fun `getProgress keeps the remote document's other cards and stamps under a pending session`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns remoteProgress(CARD_ID to Mastered)
        queue(ratedSession("session-1", SESSION_ONE_START, OTHER_CARD_ID to Failed))

        val cards = createRepository().getProgress(SUBCATEGORY_ID).getOrThrow()?.cards

        cards?.getValue(CARD_ID) shouldBe CardProgressEntry(Mastered, firstStudiedAt = REMOTE_STAMP, masteredAt = null)
        cards?.getValue(OTHER_CARD_ID)?.state shouldBe Failed
    }

    @Test
    fun `observeProgressSummary adds the pending sessions' deltas and re-emits when the queue changes`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns remoteProgress(CARD_ID to Mastered)
        every { progressSummaryRemoteDataSource.observeSummary() } returns MutableStateFlow(
            ProgressSummaryDto(subcategories = mapOf(SUBCATEGORY_ID to SubcategoryProgressSummaryDto(masteredCount = 1, studiedCount = 1))),
        )

        createRepository().observeProgressSummary().test {
            awaitItem() shouldBe summaryOf(masteredCount = 1, studiedCount = 1)

            queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Failed, OTHER_CARD_ID to Mastered))
            awaitItem() shouldBe summaryOf(masteredCount = 1, studiedCount = 2)

            pendingSessionQueue.remove("session-1")
            awaitItem() shouldBe summaryOf(masteredCount = 1, studiedCount = 1)
        }
    }

    @Test
    fun `observeProgressSummary projects a first summary from pending sessions alone`() = runTest {
        coEvery { remoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { progressSummaryRemoteDataSource.observeSummary() } returns MutableStateFlow(null)
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))
        queue(ratedSession("session-2", SESSION_TWO_START, CARD_ID to Mastered, OTHER_CARD_ID to Failed))

        createRepository().observeProgressSummary().test {
            awaitItem() shouldBe summaryOf(masteredCount = 1, studiedCount = 2)
        }
    }

    @Test
    fun `observeProgressSummary ignores another User's pending sessions`() = runTest {
        every { progressSummaryRemoteDataSource.observeSummary() } returns MutableStateFlow(null)
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered), uid = OTHER_USER_ID)

        createRepository().observeProgressSummary().test {
            awaitItem() shouldBe null
        }
    }

    private fun queue(sessionResult: SessionResult, uid: String = USER_ID) {
        pendingSessionQueue.seed(sessionResult.toDto(uid))
    }

    private fun remoteProgress(vararg cards: Pair<String, FlashcardStudyProgressState>) = SubcategoryProgressDto(
        categoryId = CATEGORY_ID,
        cards = cards.associate { (cardId, state) -> cardId to CardProgressEntryDto(state = state.name, firstStudiedAt = Timestamp(Date.from(REMOTE_STAMP))) },
    )

    private fun summaryOf(masteredCount: Int, studiedCount: Int) =
        ProgressSummary(subcategories = mapOf(SUBCATEGORY_ID to SubcategoryProgressSummary(masteredCount = masteredCount, studiedCount = studiedCount)))

    private fun ratedSession(id: String, startedAt: Instant, vararg cardStates: Pair<String, FlashcardStudyProgressState>) = SessionResult.Rated(
        id = id,
        startedAt = startedAt,
        durationSeconds = 60,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        cardResults = cardStates.map { (cardId, state) ->
            FlashcardResult.Rated(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = state, attemptsUsed = 1, wasPreviouslyMastered = false)
        },
        studyDate = "2026-09-06",
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = 20,
    )

    private fun fastSession(id: String, startedAt: Instant, vararg cardIds: String) = SessionResult.Fast(
        id = id,
        startedAt = startedAt,
        durationSeconds = 60,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        cardResults = cardIds.map { cardId -> FlashcardResult.Fast(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = Seen) },
        studyDate = "2026-09-06",
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = 20,
    )

    private fun authUser(uid: String) = AuthUser(uid = uid, email = null, displayName = null, photoUrl = null)

    private companion object {
        const val USER_ID = "user-1"
        const val OTHER_USER_ID = "user-2"
        const val CATEGORY_ID = "cat-1"
        const val SUBCATEGORY_ID = "sub-1"
        const val CARD_ID = "card-1"
        const val OTHER_CARD_ID = "card-2"
        val REMOTE_STAMP: Instant = Instant.parse("2026-09-01T10:00:00Z")
        val SESSION_ONE_START: Instant = Instant.parse("2026-09-06T10:00:00Z")
        val SESSION_TWO_START: Instant = Instant.parse("2026-09-06T11:00:00Z")
    }
}
