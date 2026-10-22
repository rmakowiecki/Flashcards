package com.rossomak.flashcards.core.data.repository

import app.cash.turbine.test
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Seen
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultScoringStateRepositoryTest {

    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource = mockk()
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = authUser(USER_ID) }
    private val pendingSessionQueue = FakePendingSessionSubmissionLocalDataSource()
    private val xpConfigRepository = FakeXpConfigRepository().apply { resultToReturn = Result.success(CONFIG) }

    private fun createRepository(
        pendingSessionProjector: PendingSessionProjector = PendingSessionProjector(
            authRepository,
            pendingSessionQueue,
            cardProgressRemoteDataSource,
            scoringStateRemoteDataSource,
            xpConfigRepository,
        ),
    ) = DefaultScoringStateRepository(scoringStateRemoteDataSource, pendingSessionProjector, xpConfigRepository)

    @Test
    fun `getScoringState maps the dto to domain`() = runTest {
        val xp = 620L
        val level = 2
        val xpIntoCurrentLevel = 100L
        val currentStreak = 3
        val bestStreak = 5
        val lastStudyDate = "2026-09-06"
        val goalMetDate = "2026-09-06"
        val studiedSecondsOnLastStudyDate = 1500L
        val dto = ScoringStateDto(
            xp = xp,
            level = level,
            xpIntoCurrentLevel = xpIntoCurrentLevel,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            lastStudyDate = lastStudyDate,
            goalMetDate = goalMetDate,
            studiedSecondsOnLastStudyDate = studiedSecondsOnLastStudyDate,
        )
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns dto

        val result = createRepository().getScoringState()

        result.isSuccess shouldBe true
        val state = result.getOrThrow()
        state?.xp shouldBe xp
        state?.level shouldBe level
        state?.xpIntoCurrentLevel shouldBe xpIntoCurrentLevel
        state?.currentStreak shouldBe currentStreak
        state?.bestStreak shouldBe bestStreak
        state?.lastStudyDate shouldBe lastStudyDate
        state?.goalMetDate shouldBe goalMetDate
        state?.studiedSecondsOnLastStudyDate shouldBe studiedSecondsOnLastStudyDate
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState returns success with null for an absent document`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null

        val result = createRepository().getScoringState()

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe null
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState wraps a data source failure in a failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { scoringStateRemoteDataSource.getScoringState() } throws error

        val result = createRepository().getScoringState()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().getScoringState() }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
    }

    @Test
    fun `observeScoringState emits the starting state for an absent document with nothing pending`() = runTest {
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(null)

        createRepository().observeScoringState().test {
            awaitItem() shouldBe ScoringState()
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(any()) }
    }

    @Test
    fun `observeScoringState emits the document's whole scoring state`() = runTest {
        val dto = ScoringStateDto(
            xp = 20_000,
            level = 3,
            xpIntoCurrentLevel = 1200,
            currentStreak = 4,
            bestStreak = 9,
            lastStudyDate = STUDY_DATE,
            goalMetDate = STUDY_DATE,
            studiedSecondsOnLastStudyDate = 1500,
        )
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(dto)

        createRepository().observeScoringState().test {
            awaitItem() shouldBe dto.toDomain()
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `observeScoringState re-emits on a queue change with the pending session replayed, and on its removal with the remote state`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)

        createRepository().observeScoringState().test {
            awaitItem() shouldBe remoteState(REMOTE_XP)

            queue(fastSession(SESSION_ONE_ID, CARD_ID))
            awaitItem() shouldBe stateWithOneSession(REMOTE_XP + SESSION_XP)

            pendingSessionQueue.remove(SESSION_ONE_ID)
            awaitItem() shouldBe remoteState(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState re-emits on a remote change with the queue replayed on top`() = runTest {
        val updatedRemoteXp = 400L
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        val remoteScoringState = MutableStateFlow<ScoringStateDto?>(REMOTE_SCORING_STATE)
        every { scoringStateRemoteDataSource.observeScoringState() } returns remoteScoringState
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeScoringState().test {
            awaitItem() shouldBe stateWithOneSession(REMOTE_XP + SESSION_XP)

            remoteScoringState.value = ScoringStateDto(xp = updatedRemoteXp, xpIntoCurrentLevel = updatedRemoteXp)
            awaitItem() shouldBe stateWithOneSession(updatedRemoteXp + SESSION_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 2) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState emits the next Level when a replayed session crosses the threshold`() = runTest {
        val remoteXp = 900L
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(ScoringStateDto(xp = remoteXp, xpIntoCurrentLevel = remoteXp))
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeScoringState().test {
            awaitItem() shouldBe stateWithOneSession(remoteXp + SESSION_XP).copy(level = 2, xpIntoCurrentLevel = remoteXp + SESSION_XP - STARTING_LEVEL_THRESHOLD)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState counts a pending session the document already lists as applied once`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        val remoteScoringState = MutableStateFlow<ScoringStateDto?>(REMOTE_SCORING_STATE)
        every { scoringStateRemoteDataSource.observeScoringState() } returns remoteScoringState
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeScoringState().test {
            awaitItem() shouldBe stateWithOneSession(REMOTE_XP + SESSION_XP)

            val deliveredXp = REMOTE_XP + SESSION_XP
            remoteScoringState.value = ScoringStateDto(
                xp = deliveredXp,
                xpIntoCurrentLevel = deliveredXp,
                currentStreak = 1,
                bestStreak = 1,
                lastStudyDate = STUDY_DATE,
                studiedSecondsOnLastStudyDate = SESSION_DURATION_SECONDS.toLong(),
                appliedSessionIds = listOf(SESSION_ONE_ID),
            )
            expectNoEvents()
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `observeScoringState does not count a pending session that leaves the queue during its reads`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            pendingSessionQueue.remove(SESSION_ONE_ID)
            null
        }
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeScoringState().test {
            awaitItem() shouldBe remoteState(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState never replays the first User's pending session once another User signs in during its reads`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            authRepository.userToReturn = authUser(OTHER_USER_ID)
            null
        }
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeScoringState().test {
            // The other User's queue is empty, so only the remote state remains.
            awaitItem() shouldBe remoteState(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState drops a stale projection and emits the next one`() = runTest {
        val pendingSessionProjector: PendingSessionProjector = mockk()
        val queuedSession = fastSession(SESSION_ONE_ID, CARD_ID)
        val pendingSessions = MutableStateFlow(listOf(queuedSession))
        val staleProjectionStarted = CompletableDeferred<Unit>()
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        every { pendingSessionProjector.observePendingSessions() } returns pendingSessions
        coEvery { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE, listOf(queuedSession), CONFIG) } coAnswers {
            staleProjectionStarted.complete(Unit)
            null
        }
        coEvery { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE, emptyList(), CONFIG) } returns REMOTE_SCORING_STATE.toDomain()

        createRepository(pendingSessionProjector).observeScoringState().test {
            staleProjectionStarted.await()
            pendingSessions.value = emptyList()

            awaitItem() shouldBe remoteState(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        verify(exactly = 1) { pendingSessionProjector.observePendingSessions() }
        coVerify(exactly = 1) { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE, listOf(queuedSession), CONFIG) }
        coVerify(exactly = 1) { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE, emptyList(), CONFIG) }
    }

    @Test
    fun `observeScoringState does not re-emit a queue change that leaves the scoring state equal`() = runTest {
        val updatedRemoteXp = 400L
        val queueProjectionRead = CompletableDeferred<Unit>()
        xpConfigRepository.resultToReturn = Result.success(ZERO_AWARD_CONFIG)
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            queueProjectionRead.complete(Unit)
            null
        }
        val studiedTodayDto = ScoringStateDto(xp = REMOTE_XP, xpIntoCurrentLevel = REMOTE_XP, currentStreak = 1, bestStreak = 1, lastStudyDate = STUDY_DATE)
        val remoteScoringState = MutableStateFlow<ScoringStateDto?>(studiedTodayDto)
        every { scoringStateRemoteDataSource.observeScoringState() } returns remoteScoringState

        createRepository().observeScoringState().test {
            awaitItem() shouldBe studiedTodayDto.toDomain()

            // Earns nothing, adds no seconds and falls on the day already recorded.
            queue(fastSession(SESSION_ONE_ID, CARD_ID, durationSeconds = 0))
            // Change the remote state only once the queue's projection has read its baselines, so the
            // remote change cannot cancel that projection before it reaches the de-duplication.
            queueProjectionRead.await()
            val updatedDto = studiedTodayDto.copy(xp = updatedRemoteXp, xpIntoCurrentLevel = updatedRemoteXp)
            remoteScoringState.value = updatedDto

            // The queued session changes nothing, so the next value is the remote change's.
            awaitItem() shouldBe updatedDto.toDomain()
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 2) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState reads the configuration once per projection for its replay`() = runTest {
        val config = CONFIG.copy(sessionCompleted = 7, levelBaseCost = 2000)
        xpConfigRepository.resultToReturn = Result.success(config)
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(null)
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeScoringState().test {
            // 1 new card × 10 + 7 completion + a Streak of 1 × 20, inside a Level 1 threshold of 2000.
            awaitItem() shouldBe stateWithOneSession(37)
        }
        xpConfigRepository.getXpConfigCallCount shouldBe 1
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `observeScoringState completes without retrying on permission denied`() = runTest {
        val attempts = AtomicInteger(0)
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            attempts.incrementAndGet()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        createRepository().observeScoringState().test {
            awaitComplete()
        }
        attempts.get() shouldBe 1
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `observeScoringState completes with no further emission on permission denied after a value`() = runTest {
        val signOut = CompletableDeferred<Unit>()
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            emit(REMOTE_SCORING_STATE)
            signOut.await()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        createRepository().observeScoringState().test {
            awaitItem() shouldBe remoteState(REMOTE_XP)

            signOut.complete(Unit)
            awaitComplete()
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `observeScoringState cancels a projection still in flight on remote completion instead of emitting it`() = runTest {
        val configRead = CompletableDeferred<Unit>()
        val releaseConfigRead = CompletableDeferred<Unit>()
        val signOut = CompletableDeferred<Unit>()
        val blockingXpConfigRepository: XpConfigRepository = mockk()
        coEvery { blockingXpConfigRepository.getXpConfig() } coAnswers {
            configRead.complete(Unit)
            releaseConfigRead.await()
            Result.success(CONFIG)
        }
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            emit(REMOTE_SCORING_STATE)
            signOut.await()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
        val repository = DefaultScoringStateRepository(
            scoringStateRemoteDataSource,
            PendingSessionProjector(authRepository, pendingSessionQueue, cardProgressRemoteDataSource, scoringStateRemoteDataSource, blockingXpConfigRepository),
            blockingXpConfigRepository,
        )

        repository.observeScoringState().test {
            configRead.await()
            signOut.complete(Unit)

            awaitComplete()
            releaseConfigRead.complete(Unit)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { blockingXpConfigRepository.getXpConfig() }
    }

    @Test
    fun `observeScoringState propagates a non-transient failure without retrying`() = runTest {
        val attempts = AtomicInteger(0)
        val error = FirebaseFirestoreException("bad query", FirebaseFirestoreException.Code.INVALID_ARGUMENT)
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            attempts.incrementAndGet()
            throw error
        }

        createRepository().observeScoringState().test {
            awaitError() shouldBe error
        }
        attempts.get() shouldBe 1
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `observeScoringState retries a transient failure and recovers`() = runTest {
        val attempts = AtomicInteger(0)
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            if (attempts.getAndIncrement() == 0) {
                throw FirebaseFirestoreException("listener dropped", FirebaseFirestoreException.Code.UNAVAILABLE)
            }
            emit(REMOTE_SCORING_STATE)
        }

        createRepository().observeScoringState().test {
            awaitItem() shouldBe remoteState(REMOTE_XP)
            awaitComplete()
        }
        attempts.get() shouldBe 2
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    private fun remoteState(xp: Long) = ScoringState(xp = xp, xpIntoCurrentLevel = xp)

    /** A Level 1 state after the one queued session: its first Streak day and its seconds on [STUDY_DATE]. */
    private fun stateWithOneSession(xp: Long) = ScoringState(
        xp = xp,
        xpIntoCurrentLevel = xp,
        currentStreak = 1,
        bestStreak = 1,
        lastStudyDate = STUDY_DATE,
        studiedSecondsOnLastStudyDate = SESSION_DURATION_SECONDS.toLong(),
    )

    private fun queue(sessionResult: SessionResult, uid: String = USER_ID) {
        pendingSessionQueue.seed(sessionResult.toDto(uid))
    }

    private fun fastSession(id: String, vararg cardIds: String, durationSeconds: Int = SESSION_DURATION_SECONDS) = SessionResult.Fast(
        id = id,
        startedAt = SESSION_ONE_START,
        durationSeconds = durationSeconds,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        sourceType = SingleSubcategory,
        cardResults = cardIds.map { cardId -> FlashcardResult.Fast(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = Seen) },
        studyDate = STUDY_DATE,
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = 20,
        readAloudEnabled = false,
    )

    private fun authUser(uid: String) = AuthUser(uid = uid, email = null, displayName = null, photoUrl = null)

    private companion object {
        const val USER_ID = "user-1"
        const val OTHER_USER_ID = "user-2"
        const val CATEGORY_ID = "cat-1"
        const val SUBCATEGORY_ID = "sub-1"
        const val CARD_ID = "card-1"
        const val SESSION_ONE_ID = "session-1"
        const val STUDY_DATE = "2026-09-06"
        const val SESSION_DURATION_SECONDS = 60
        val SESSION_ONE_START: Instant = Instant.parse("2026-09-06T10:00:00Z")

        // Level 1's threshold under the bundled Level curve.
        const val STARTING_LEVEL_THRESHOLD = 1000L
        const val REMOTE_XP = 300L
        val REMOTE_SCORING_STATE = ScoringStateDto(xp = REMOTE_XP, xpIntoCurrentLevel = REMOTE_XP)

        // One Fast session with one new card: 1 new card × 10 + 100 completion + a Streak of 1 × 20.
        const val SESSION_XP = 130L

        // The bundled defaults, except a small Streak rate, so a Streak award stays inside Level 1.
        val CONFIG = XpConfig(streakPerDay = 20)

        // Every award zero, so a replayed session moves neither Level nor XP.
        val ZERO_AWARD_CONFIG = XpConfig(
            newCardStudied = 0,
            cardMastered = 0,
            cardPartial = 0,
            masteryDefended = 0,
            cardDemastered = 0,
            sessionCompleted = 0,
            dailyGoalMet = 0,
            streakPerDay = 0,
            streakMaxPerDay = 0,
        )
    }
}
