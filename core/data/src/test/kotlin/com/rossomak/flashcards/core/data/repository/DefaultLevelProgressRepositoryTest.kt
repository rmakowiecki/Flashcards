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
import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLevelProgressRepositoryTest {

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
    ) = DefaultLevelProgressRepository(scoringStateRemoteDataSource, pendingSessionProjector, xpConfigRepository)

    @Test
    fun `an absent document with nothing pending emits the starting state`() = runTest {
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(null)

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe LevelProgress(level = 1, xpIntoCurrentLevel = 0, xpForNextLevel = STARTING_LEVEL_THRESHOLD)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(any()) }
    }

    @Test
    fun `a document emits its Level and XP`() = runTest {
        val level = 3
        val xpIntoCurrentLevel = 1200L
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(ScoringStateDto(xp = 20_000, level = level, xpIntoCurrentLevel = xpIntoCurrentLevel))

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe LevelProgress(level = level, xpIntoCurrentLevel = xpIntoCurrentLevel, xpForNextLevel = 16_000)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `a queue change re-emits with the pending session replayed, and its removal re-emits the remote state`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)

            queue(fastSession(SESSION_ONE_ID, CARD_ID))
            awaitItem() shouldBe levelOneProgress(REMOTE_XP + SESSION_XP)

            pendingSessionQueue.remove(SESSION_ONE_ID)
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a remote change re-emits with the queue replayed on top`() = runTest {
        val updatedRemoteXp = 400L
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        val remoteScoringState = MutableStateFlow<ScoringStateDto?>(REMOTE_SCORING_STATE)
        every { scoringStateRemoteDataSource.observeScoringState() } returns remoteScoringState
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP + SESSION_XP)

            remoteScoringState.value = ScoringStateDto(xp = updatedRemoteXp, xpIntoCurrentLevel = updatedRemoteXp)
            awaitItem() shouldBe levelOneProgress(updatedRemoteXp + SESSION_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 2) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a replayed session crossing the Level threshold emits the next Level`() = runTest {
        val remoteXp = 900L
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(ScoringStateDto(xp = remoteXp, xpIntoCurrentLevel = remoteXp))
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe LevelProgress(level = 2, xpIntoCurrentLevel = remoteXp + SESSION_XP - STARTING_LEVEL_THRESHOLD, xpForNextLevel = 6000)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a pending session with no Flashcard Result is left out`() = runTest {
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        queue(fastSession(SESSION_ONE_ID))

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(any()) }
    }

    @Test
    fun `a pending session that leaves the queue during its reads is not counted`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            pendingSessionQueue.remove(SESSION_ONE_ID)
            null
        }
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `the first User's pending session is never replayed once another User signs in during its reads`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            authRepository.userToReturn = authUser(OTHER_USER_ID)
            null
        }
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeLevelProgress().test {
            // The other User's queue is empty, so only the remote state remains.
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a stale projection is dropped and the next one emits`() = runTest {
        val pendingSessionProjector: PendingSessionProjector = mockk()
        val queuedSession = fastSession(SESSION_ONE_ID, CARD_ID)
        val deliverablePendingSessions = MutableStateFlow(listOf(queuedSession))
        val staleProjectionStarted = CompletableDeferred<Unit>()
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(REMOTE_SCORING_STATE)
        every { pendingSessionProjector.observeDeliverablePendingSessions() } returns deliverablePendingSessions
        coEvery { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE.toDomain(), listOf(queuedSession), CONFIG) } coAnswers {
            staleProjectionStarted.complete(Unit)
            null
        }
        coEvery { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE.toDomain(), emptyList(), CONFIG) } returns REMOTE_SCORING_STATE.toDomain()

        createRepository(pendingSessionProjector).observeLevelProgress().test {
            staleProjectionStarted.await()
            deliverablePendingSessions.value = emptyList()

            awaitItem() shouldBe levelOneProgress(REMOTE_XP)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        verify(exactly = 1) { pendingSessionProjector.observeDeliverablePendingSessions() }
        coVerify(exactly = 1) { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE.toDomain(), listOf(queuedSession), CONFIG) }
        coVerify(exactly = 1) { pendingSessionProjector.projectScoringStateOver(REMOTE_SCORING_STATE.toDomain(), emptyList(), CONFIG) }
    }

    @Test
    fun `a queue change that moves neither Level nor XP does not re-emit`() = runTest {
        val updatedRemoteXp = 400L
        val queueProjectionRead = CompletableDeferred<Unit>()
        xpConfigRepository.resultToReturn = Result.success(ZERO_AWARD_CONFIG)
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            queueProjectionRead.complete(Unit)
            null
        }
        val remoteScoringState = MutableStateFlow<ScoringStateDto?>(REMOTE_SCORING_STATE)
        every { scoringStateRemoteDataSource.observeScoringState() } returns remoteScoringState

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)

            queue(fastSession(SESSION_ONE_ID, CARD_ID))
            // Change the remote state only once the queue's projection has read its baselines, so the
            // remote change cannot cancel that projection before it reaches the de-duplication.
            queueProjectionRead.await()
            remoteScoringState.value = ScoringStateDto(xp = updatedRemoteXp, xpIntoCurrentLevel = updatedRemoteXp)

            // The queued session earns nothing, so the next value is the remote change's.
            awaitItem() shouldBe levelOneProgress(updatedRemoteXp)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 2) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `each emission reads the configuration once and uses it for both the replay and the threshold`() = runTest {
        val config = CONFIG.copy(sessionCompleted = 7, levelCurveBase = 2000.0)
        xpConfigRepository.resultToReturn = Result.success(config)
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        every { scoringStateRemoteDataSource.observeScoringState() } returns MutableStateFlow(null)
        queue(fastSession(SESSION_ONE_ID, CARD_ID))

        createRepository().observeLevelProgress().test {
            // 1 new card × 10 + 1 minute × 10 + 7 completion + a Streak of 1 × 20, against a Level 1 threshold of 2000.
            awaitItem() shouldBe LevelProgress(level = 1, xpIntoCurrentLevel = 47, xpForNextLevel = config.levelThreshold(1))
        }
        xpConfigRepository.getXpConfigCallCount shouldBe 1
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `permission denied completes the flow without retrying`() = runTest {
        val attempts = AtomicInteger(0)
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            attempts.incrementAndGet()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        createRepository().observeLevelProgress().test {
            awaitComplete()
        }
        attempts.get() shouldBe 1
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `permission denied after a value completes the flow with no further emission`() = runTest {
        val signOut = CompletableDeferred<Unit>()
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            emit(REMOTE_SCORING_STATE)
            signOut.await()
            throw FirebaseFirestoreException("sign-out", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)

            signOut.complete(Unit)
            awaitComplete()
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `remote completion cancels a projection still in flight instead of emitting it`() = runTest {
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
        val repository = DefaultLevelProgressRepository(
            scoringStateRemoteDataSource,
            PendingSessionProjector(authRepository, pendingSessionQueue, cardProgressRemoteDataSource, scoringStateRemoteDataSource, blockingXpConfigRepository),
            blockingXpConfigRepository,
        )

        repository.observeLevelProgress().test {
            configRead.await()
            signOut.complete(Unit)

            awaitComplete()
            releaseConfigRead.complete(Unit)
        }
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
        coVerify(exactly = 1) { blockingXpConfigRepository.getXpConfig() }
    }

    @Test
    fun `a non-transient failure propagates without retrying`() = runTest {
        val attempts = AtomicInteger(0)
        val error = FirebaseFirestoreException("bad query", FirebaseFirestoreException.Code.INVALID_ARGUMENT)
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            attempts.incrementAndGet()
            throw error
        }

        createRepository().observeLevelProgress().test {
            awaitError() shouldBe error
        }
        attempts.get() shouldBe 1
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    @Test
    fun `a transient failure is retried and recovers`() = runTest {
        val attempts = AtomicInteger(0)
        every { scoringStateRemoteDataSource.observeScoringState() } returns flow {
            if (attempts.getAndIncrement() == 0) {
                throw FirebaseFirestoreException("listener dropped", FirebaseFirestoreException.Code.UNAVAILABLE)
            }
            emit(REMOTE_SCORING_STATE)
        }

        createRepository().observeLevelProgress().test {
            awaitItem() shouldBe levelOneProgress(REMOTE_XP)
            awaitComplete()
        }
        attempts.get() shouldBe 2
        verify(exactly = 1) { scoringStateRemoteDataSource.observeScoringState() }
    }

    private fun levelOneProgress(xpIntoCurrentLevel: Long) = LevelProgress(level = 1, xpIntoCurrentLevel = xpIntoCurrentLevel, xpForNextLevel = STARTING_LEVEL_THRESHOLD)

    private fun queue(sessionResult: SessionResult, uid: String = USER_ID) {
        pendingSessionQueue.seed(sessionResult.toDto(uid))
    }

    private fun fastSession(id: String, vararg cardIds: String) = SessionResult.Fast(
        id = id,
        startedAt = SESSION_ONE_START,
        durationSeconds = 60,
        abandoned = false,
        categoryId = CATEGORY_ID,
        categoryName = "Category",
        subcategoryIds = listOf(SUBCATEGORY_ID),
        subcategoryNames = listOf("Subcategory"),
        sourceType = SingleSubcategory,
        cardResults = cardIds.map { cardId -> FlashcardResult.Fast(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = Seen) },
        studyDate = "2026-09-06",
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
        val SESSION_ONE_START: Instant = Instant.parse("2026-09-06T10:00:00Z")

        // Level 1's threshold under the bundled Level curve: the "0 / 1,000 XP" a new account shows.
        const val STARTING_LEVEL_THRESHOLD = 1000L
        const val REMOTE_XP = 300L
        val REMOTE_SCORING_STATE = ScoringStateDto(xp = REMOTE_XP, xpIntoCurrentLevel = REMOTE_XP)

        // One Fast session with one new card: 1 new card × 10 + 1 minute × 10 + 500 completion + a Streak of 1 × 20.
        const val SESSION_XP = 540L

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
            minuteStudied = 0,
        )
    }
}
