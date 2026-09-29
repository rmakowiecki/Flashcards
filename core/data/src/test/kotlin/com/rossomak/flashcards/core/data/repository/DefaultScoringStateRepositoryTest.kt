package com.rossomak.flashcards.core.data.repository

import com.google.firebase.Timestamp
import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDto
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.FakePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.AuthUser
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Mastered
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState.Seen
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultScoringStateRepositoryTest {

    private val remoteDataSource: ScoringStateRemoteDataSource = mockk()
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = authUser(USER_ID) }
    private val pendingSessionQueue = FakePendingSessionSubmissionLocalDataSource()
    private val xpConfigRepository = FakeXpConfigRepository().apply { resultToReturn = Result.success(CONFIG) }

    private fun createRepository(): DefaultScoringStateRepository = DefaultScoringStateRepository(
        PendingSessionProjector(authRepository, pendingSessionQueue, cardProgressRemoteDataSource, remoteDataSource, xpConfigRepository),
    )

    @Test
    fun `getScoringState maps the dto to domain`() = runTest {
        val xp = 620L
        val level = 2
        val xpIntoCurrentLevel = 100L
        val currentStreak = 3
        val bestStreak = 5
        val lastStudyDate = "2026-09-06"
        val goalMetDate = "2026-09-06"
        val dto = ScoringStateDto(
            xp = xp,
            level = level,
            xpIntoCurrentLevel = xpIntoCurrentLevel,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            lastStudyDate = lastStudyDate,
            goalMetDate = goalMetDate,
        )
        coEvery { remoteDataSource.getScoringState() } returns dto

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
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState returns success with null for an absent document`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null

        val result = createRepository().getScoringState()

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe null
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState wraps a data source failure in a failure result`() = runTest {
        val error = IllegalStateException("firestore down")
        coEvery { remoteDataSource.getScoringState() } throws error

        val result = createRepository().getScoringState()

        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `getScoringState rethrows cancellation instead of wrapping it`() = runTest {
        coEvery { remoteDataSource.getScoringState() } throws CancellationException("cancelled")

        val thrown = runCatching { createRepository().getScoringState() }.exceptionOrNull()

        (thrown is CancellationException) shouldBe true
        coVerify(exactly = 1) { remoteDataSource.getScoringState() }
    }

    @Test
    fun `two pending sessions yield the XP and Level of applying both in order, a card Mastered in the first defended in the second`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))
        queue(ratedSession("session-2", SESSION_TWO_START, CARD_ID to Mastered))

        val state = createRepository().getScoringState().getOrThrow()

        // Session 1: 1 new card × 10 + 1 mastered × 100 + 1 minute × 10 + 500 completion = 620.
        // Session 2: card-1 is neither new nor freshly Mastered, so 1 defended × 50 + 10 + 500 = 560.
        // 1180 crosses Level 1's threshold of 1000.
        state shouldBe ScoringState(xp = 1180, level = 2, xpIntoCurrentLevel = 180)
    }

    @Test
    fun `a card studied in the first pending session is not new in the second`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession("session-1", SESSION_ONE_START, CARD_ID))
        queue(fastSession("session-2", SESSION_TWO_START, CARD_ID))

        val state = createRepository().getScoringState().getOrThrow()

        // (1 new × 10 + 10 + 500) + (0 new + 10 + 500).
        state?.xp shouldBe 1030L
    }

    @Test
    fun `pending sessions replay over the remote scoring state and Card Progress, carrying the Streak fields over`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns ScoringStateDto(
            xp = 300,
            level = 1,
            xpIntoCurrentLevel = 300,
            currentStreak = 4,
            bestStreak = 7,
            lastStudyDate = "2026-09-05",
            goalMetDate = "2026-09-05",
        )
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns remoteProgress(CARD_ID to Mastered)
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))

        val state = createRepository().getScoringState().getOrThrow()

        // 300 + 1 defended × 50 + 10 + 500.
        state shouldBe ScoringState(
            xp = 860,
            level = 1,
            xpIntoCurrentLevel = 860,
            currentStreak = 4,
            bestStreak = 7,
            lastStudyDate = "2026-09-05",
            goalMetDate = "2026-09-05",
        )
    }

    @Test
    fun `pending sessions are scored with the cached xp configuration`() = runTest {
        xpConfigRepository.resultToReturn = Result.success(CONFIG.copy(sessionCompleted = 7))
        coEvery { remoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession("session-1", SESSION_ONE_START, CARD_ID))

        createRepository().getScoringState().getOrThrow()?.xp shouldBe 27L
    }

    @Test
    fun `another User's pending sessions are ignored`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered), uid = OTHER_USER_ID)

        createRepository().getScoringState().getOrThrow() shouldBe null
    }

    @Test
    fun `a failed scoring-state read fails even with pending sessions`() = runTest {
        val error = IllegalStateException("offline, not cached")
        coEvery { remoteDataSource.getScoringState() } throws error
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))

        createRepository().getScoringState().exceptionOrNull() shouldBe error
    }

    @Test
    fun `a missing scoring-state document with pending sessions projects from the starting state`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession("session-1", SESSION_ONE_START, CARD_ID))

        createRepository().getScoringState().getOrThrow() shouldBe ScoringState(xp = 520, level = 1, xpIntoCurrentLevel = 520)
    }

    @Test
    fun `a failed Card Progress read projects the scoring state over no record`() = runTest {
        coEvery { remoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } throws IllegalStateException("offline, not cached")
        queue(ratedSession("session-1", SESSION_ONE_START, CARD_ID to Mastered))

        createRepository().getScoringState().getOrThrow()?.xp shouldBe 620L
    }

    private fun queue(sessionResult: SessionResult, uid: String = USER_ID) {
        pendingSessionQueue.seed(sessionResult.toDto(uid))
    }

    private fun remoteProgress(vararg cards: Pair<String, FlashcardStudyProgressState>) = SubcategoryProgressDto(
        categoryId = CATEGORY_ID,
        cards = cards.associate { (cardId, state) -> cardId to CardProgressEntryDto(state = state.name, firstStudiedAt = Timestamp(Date.from(SESSION_ONE_START))) },
    )

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
        val SESSION_ONE_START: Instant = Instant.parse("2026-09-06T10:00:00Z")
        val SESSION_TWO_START: Instant = Instant.parse("2026-09-06T11:00:00Z")

        // The bundled defaults, spelled out so the arithmetic in each test reads against known rates.
        val CONFIG = XpConfig(
            newCardStudied = 10,
            cardMastered = 100,
            masteryDefended = 50,
            minuteStudied = 10,
            sessionCompleted = 500,
            levelCurveBase = 1000.0,
            levelCurveExponent = 2.5,
        )
    }
}
