package com.rossomak.flashcards.core.data.repository

import com.google.firebase.Timestamp
import com.rossomak.flashcards.core.data.mapper.toDomain
import com.rossomak.flashcards.core.data.model.CardProgressEntryDto
import com.rossomak.flashcards.core.data.model.PendingSessionSubmissionMapper.toDto
import com.rossomak.flashcards.core.data.model.ScoringStateDto
import com.rossomak.flashcards.core.data.model.SubcategoryProgressDetailsDto
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
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeAuthRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import com.rossomak.flashcards.core.domain.scoring.SubcategoryProgressDelta
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSessionProjectorTest {

    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource = mockk { coEvery { getScoringState(any()) } returns null }
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val authRepository = FakeAuthRepository().apply { userToReturn = authUser(USER_ID) }
    private val pendingSessionQueue = FakePendingSessionSubmissionLocalDataSource()
    private val xpConfigRepository = FakeXpConfigRepository().apply { resultToReturn = Result.success(CONFIG) }

    private fun createProjector() = PendingSessionProjector(authRepository, pendingSessionQueue, cardProgressRemoteDataSource, scoringStateRemoteDataSource, xpConfigRepository)

    @Test
    fun `two pending sessions yield the XP and Level of applying both in order, a card Mastered in the first defended in the second`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))
        queue(ratedSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID to Mastered))

        val state = createProjector().projectScoringState().getOrThrow()

        // Session 1: 1 new card × 10 + 1 mastered × 100 + 1 minute × 10 + 500 completion + a Streak of
        // 1 × 20 = 640. Session 2, the same day: card-1 is neither new nor freshly Mastered, so 1
        // defended × 50 + 10 + 500 = 560. 1200 crosses Level 1's threshold of 1000.
        state shouldBe ScoringState(
            xp = 1200,
            level = 2,
            xpIntoCurrentLevel = 200,
            currentStreak = 1,
            bestStreak = 1,
            lastStudyDate = STUDY_DATE,
            studiedSecondsOnLastStudyDate = 120,
        )
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a card studied in the first pending session is not new in the second`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID))

        val state = createProjector().projectScoringState().getOrThrow()

        // (1 new × 10 + 10 + 500 + a Streak of 1 × 20) + (0 new + 10 + 500).
        state?.xp shouldBe 1050L
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `pending sessions replay over the remote scoring state and Card Progress, advancing the Streak fields`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns ScoringStateDto(
            xp = 300,
            level = 1,
            xpIntoCurrentLevel = 300,
            currentStreak = 4,
            bestStreak = 7,
            lastStudyDate = PREVIOUS_STUDY_DATE,
            goalMetDate = PREVIOUS_STUDY_DATE,
            studiedSecondsOnLastStudyDate = 1800,
        )
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns remoteProgress(CARD_ID to Mastered)
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))

        val state = createProjector().projectScoringState().getOrThrow()

        // 300 + 1 defended × 50 + 10 + 500 + a Streak of 5 × 20. The previous day's seconds do not
        // count toward today's goal.
        state shouldBe ScoringState(
            xp = 960,
            level = 1,
            xpIntoCurrentLevel = 960,
            currentStreak = 5,
            bestStreak = 7,
            lastStudyDate = STUDY_DATE,
            goalMetDate = PREVIOUS_STUDY_DATE,
            studiedSecondsOnLastStudyDate = 60,
        )
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `two pending sessions on consecutive days advance the projected Streak by two`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns ScoringStateDto(currentStreak = 1, bestStreak = 1, lastStudyDate = PREVIOUS_STUDY_DATE)
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        queue(fastSession(SESSION_TWO_ID, NEXT_DAY_START, CARD_ID).copy(studyDate = NEXT_STUDY_DATE))

        val state = createProjector().projectScoringState().getOrThrow()

        state?.currentStreak shouldBe 3
        state?.bestStreak shouldBe 3
        state?.lastStudyDate shouldBe NEXT_STUDY_DATE
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `two short pending sessions on the same day reach the Daily Goal together, on the second`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID).copy(dailyGoalMinutes = 2))

        createProjector().projectScoringState().getOrThrow()?.goalMetDate shouldBe ""

        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID).copy(dailyGoalMinutes = 2))

        val state = createProjector().projectScoringState().getOrThrow()
        state?.goalMetDate shouldBe STUDY_DATE
        state?.studiedSecondsOnLastStudyDate shouldBe 120L
        coVerify(exactly = 2) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 2) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a pending session from yesterday is judged against yesterday, not today`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns ScoringStateDto(currentStreak = 1, bestStreak = 1, lastStudyDate = PREVIOUS_STUDY_DATE)
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, PREVIOUS_DAY_START, CARD_ID).copy(studyDate = PREVIOUS_STUDY_DATE, dailyGoalMinutes = 1))
        queue(fastSession(SESSION_TWO_ID, SESSION_ONE_START, CARD_ID).copy(dailyGoalMinutes = 2))

        val state = createProjector().projectScoringState().getOrThrow()

        // Yesterday's session keeps the Streak and meets yesterday's 1-minute goal; today's advances the
        // Streak once and, with only its own minute, misses today's 2-minute goal.
        state?.currentStreak shouldBe 2
        state?.goalMetDate shouldBe PREVIOUS_STUDY_DATE
        state?.lastStudyDate shouldBe STUDY_DATE
        state?.studiedSecondsOnLastStudyDate shouldBe 60L
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `pending sessions are scored with the cached xp configuration`() = runTest {
        xpConfigRepository.resultToReturn = Result.success(CONFIG.copy(sessionCompleted = 7))
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))

        createProjector().projectScoringState().getOrThrow()?.xp shouldBe 47L
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `another User's pending sessions are ignored`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered), uid = OTHER_USER_ID)

        createProjector().projectScoringState().getOrThrow() shouldBe null
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(any()) }
    }

    @Test
    fun `projectScoringState fails instead of mixing Users when the signed-in User changes during the reads`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } coAnswers {
            authRepository.userToReturn = authUser(OTHER_USER_ID)
            null
        }
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))

        createProjector().projectScoringState().isFailure shouldBe true
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a failed scoring-state read fails even with pending sessions`() = runTest {
        val error = IllegalStateException("offline, not cached")
        coEvery { scoringStateRemoteDataSource.getScoringState() } throws error
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))

        createProjector().projectScoringState().exceptionOrNull() shouldBe error
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
    }

    @Test
    fun `a missing scoring-state document with pending sessions projects from the starting state`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))

        createProjector().projectScoringState().getOrThrow() shouldBe ScoringState(
            xp = 540,
            level = 1,
            xpIntoCurrentLevel = 540,
            currentStreak = 1,
            bestStreak = 1,
            lastStudyDate = STUDY_DATE,
            studiedSecondsOnLastStudyDate = 60,
        )
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `a failed Card Progress read projects the scoring state over no record`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } throws IllegalStateException("offline, not cached")
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))

        createProjector().projectScoringState().getOrThrow()?.xp shouldBe 640L
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSummaryDeltas drops a snapshot taken under a User who has since signed out`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))
        val projector = createProjector()
        val staleSnapshot = projector.observePendingSessions().first()

        authRepository.userToReturn = authUser(OTHER_USER_ID)

        projector.projectSummaryDeltas(staleSnapshot) shouldBe null
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSummaryDeltas drops a snapshot once a pending session leaves the queue during the baseline read`() = runTest {
        val projector = createProjector()
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            pendingSessionQueue.remove(SESSION_ONE_ID)
            null
        }
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))
        val snapshot = projector.observePendingSessions().first()

        projector.projectSummaryDeltas(snapshot) shouldBe null
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSessionXpTotals of no sessions is empty without reading anything`() = runTest {
        createProjector().projectSessionXpTotals(emptyList()) shouldBe emptyMap()

        coVerify(exactly = 0) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(any()) }
    }

    @Test
    fun `projectSessionXpTotals of one pending session is its scored xpTotal`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))
        val projector = createProjector()

        val xpTotals = projector.projectSessionXpTotals(projector.observePendingSessions().first())

        // 1 new card × 10 + 1 mastered × 100 + 1 minute × 10 + 500 completion + a Streak of 1 × 20.
        xpTotals shouldBe mapOf(SESSION_ONE_ID to 640)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSessionXpTotals replays two pending sessions in order, the second reaching the Daily Goal`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID).copy(dailyGoalMinutes = 2))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID).copy(dailyGoalMinutes = 2))
        val projector = createProjector()

        val xpTotals = projector.projectSessionXpTotals(projector.observePendingSessions().first())

        // Session 1: 1 new × 10 + 10 + 500 + a Streak of 1 × 20. Session 2: card-1 is no longer new, no
        // second Streak award the same day, and the two minutes together meet the 2-minute goal: 10 + 500 + 1000.
        xpTotals shouldBe mapOf(SESSION_ONE_ID to 540, SESSION_TWO_ID to 1510)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSessionXpTotals replays from the starting state when the scoring state is unreadable`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } throws IllegalStateException("offline, not cached")
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        val projector = createProjector()

        projector.projectSessionXpTotals(projector.observePendingSessions().first()) shouldBe mapOf(SESSION_ONE_ID to 540)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSessionXpTotals drops a snapshot once a pending session leaves the queue during the reads`() = runTest {
        val projector = createProjector()
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } coAnswers {
            pendingSessionQueue.remove(SESSION_ONE_ID)
            null
        }
        queue(ratedSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID to Mastered))
        val snapshot = projector.observePendingSessions().first()

        projector.projectSessionXpTotals(snapshot) shouldBe null
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `projectSessionXpTotals is not stale because of a zero-card pending session left out of its argument`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, CARD_ID))
        val projector = createProjector()
        val deliverable = projector.observeDeliverablePendingSessions().first()

        projector.projectSessionXpTotals(deliverable) shouldBe mapOf(SESSION_TWO_ID to 540)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState() }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) }
    }

    @Test
    fun `three sessions queued offline chain their scoring states, the last projecting the XP of applying all three`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, OTHER_CARD_ID))
        queue(fastSession(SESSION_THREE_ID, SESSION_THREE_START, THIRD_CARD_ID))
        val projector = createProjector()

        val state = projector.projectScoringState().getOrThrow()
        val xpTotals = projector.projectSessionXpTotals(projector.observePendingSessions().first())

        // Each: 1 new × 10 + 10 + 500, the first also a Streak of 1 × 20. 1580 crosses Level 1's 1000.
        xpTotals shouldBe mapOf(SESSION_ONE_ID to 540, SESSION_TWO_ID to 520, SESSION_THREE_ID to 520)
        state shouldBe ScoringState(
            xp = 1580,
            level = 2,
            xpIntoCurrentLevel = 580,
            currentStreak = 1,
            bestStreak = 1,
            lastStudyDate = STUDY_DATE,
            studiedSecondsOnLastStudyDate = 180,
        )
    }

    @Test
    fun `a queued session listed as applied adds to no aggregate, but its Card Progress still shows`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns SCORING_STATE_AFTER_SESSION_ONE.copy(appliedSessionIds = listOf(SESSION_ONE_ID))
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, OTHER_CARD_ID))
        val projector = createProjector()

        val state = projector.projectScoringState().getOrThrow()
        val summaryDeltas = projector.projectSummaryDeltas(projector.observePendingSessions().first())
        val progress = projector.projectCardProgress(SUBCATEGORY_ID).getOrThrow()

        state shouldBe SCORING_STATE_AFTER_SESSIONS_ONE_AND_TWO
        summaryDeltas shouldBe mapOf(SUBCATEGORY_ID to SubcategoryProgressDelta(masteredDelta = 0, studiedDelta = 1))
        progress?.cards?.keys shouldBe setOf(CARD_ID, OTHER_CARD_ID)
    }

    @Test
    fun `a session delivered and removed from the queue with the cache refreshed is still counted exactly once`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns SCORING_STATE_AFTER_SESSION_ONE.copy(appliedSessionIds = listOf(SESSION_ONE_ID))
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns remoteProgress(CARD_ID to Seen)
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, OTHER_CARD_ID))

        createProjector().projectScoringState().getOrThrow() shouldBe SCORING_STATE_AFTER_SESSIONS_ONE_AND_TWO
    }

    @Test
    fun `projectSessionXpTotals scores an applied session without advancing the later sessions' chain`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns SCORING_STATE_AFTER_SESSION_ONE.copy(appliedSessionIds = listOf(SESSION_ONE_ID))
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID).copy(dailyGoalMinutes = 2))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, OTHER_CARD_ID).copy(dailyGoalMinutes = 2))
        val projector = createProjector()

        val xpTotals = projector.projectSessionXpTotals(projector.observePendingSessions().first())

        // Both are scored against the cached state's 60 seconds, so each meets the 2-minute goal: 10 + 10
        // + 500 + 1000. Had the applied session advanced the chain, only it would have met the goal.
        xpTotals shouldBe mapOf(SESSION_ONE_ID to 1520, SESSION_TWO_ID to 1520)
    }

    @Test
    fun `an excluded session is left out of the scoring state and Card Progress`() = runTest {
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        queue(fastSession(SESSION_TWO_ID, SESSION_TWO_START, OTHER_CARD_ID))
        val projector = createProjector()

        val state = projector.projectScoringState(excludedSessionId = SESSION_TWO_ID).getOrThrow()
        val progress = projector.projectCardProgress(SUBCATEGORY_ID, excludedSessionId = SESSION_TWO_ID).getOrThrow()

        state shouldBe SCORING_STATE_AFTER_SESSION_ONE.toDomain()
        progress?.cards?.keys shouldBe setOf(CARD_ID)
    }

    @Test
    fun `an excluded session the cached scoring state lists as applied fails the scoring-state read`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } returns SCORING_STATE_AFTER_SESSION_ONE.copy(appliedSessionIds = listOf(SESSION_ONE_ID))
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        val projector = createProjector()

        projector.projectScoringState(excludedSessionId = SESSION_ONE_ID).isFailure shouldBe true
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        projector.projectScoringState(excludedSessionId = SESSION_ONE_ID).isFailure shouldBe true
    }

    @Test
    fun `projectSummaryDeltas treats no session as applied when the scoring state is unreadable`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState() } throws IllegalStateException("offline, not cached")
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ID) } returns null
        queue(fastSession(SESSION_ONE_ID, SESSION_ONE_START, CARD_ID))
        val projector = createProjector()

        projector.projectSummaryDeltas(projector.observePendingSessions().first()) shouldBe
            mapOf(SUBCATEGORY_ID to SubcategoryProgressDelta(masteredDelta = 0, studiedDelta = 1))
    }

    private fun queue(sessionResult: SessionResult, uid: String = USER_ID) {
        pendingSessionQueue.seed(sessionResult.toDto(uid))
    }

    private fun remoteProgress(vararg cards: Pair<String, FlashcardStudyProgressState>) = SubcategoryProgressDetailsDto(
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
        sourceType = SingleSubcategory,
        cardResults = cardStates.map { (cardId, state) ->
            FlashcardResult.Rated(cardId = cardId, subcategoryId = SUBCATEGORY_ID, state = state, attemptsUsed = 1, wasPreviouslyMastered = false)
        },
        studyDate = STUDY_DATE,
        studyDateUtcOffsetMinutes = 0,
        dailyGoalMinutes = 20,
        voiceAnsweringEnabled = false,
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
        const val SESSION_TWO_ID = "session-2"
        const val SESSION_THREE_ID = "session-3"
        const val OTHER_CARD_ID = "card-2"
        const val THIRD_CARD_ID = "card-3"
        const val PREVIOUS_STUDY_DATE = "2026-09-05"
        const val STUDY_DATE = "2026-09-06"
        const val NEXT_STUDY_DATE = "2026-09-07"
        val PREVIOUS_DAY_START: Instant = Instant.parse("2026-09-05T10:00:00Z")
        val SESSION_ONE_START: Instant = Instant.parse("2026-09-06T10:00:00Z")
        val SESSION_TWO_START: Instant = Instant.parse("2026-09-06T11:00:00Z")
        val SESSION_THREE_START: Instant = Instant.parse("2026-09-06T12:00:00Z")
        val NEXT_DAY_START: Instant = Instant.parse("2026-09-07T10:00:00Z")

        // The bundled defaults, spelled out so the arithmetic in each test reads against known rates,
        // except a small Streak rate, so a Streak award stays inside Level 1.
        // The cached state once the server applied a Fast session one studying one new card: 10 + 10 +
        // 500 + a Streak of 1 × 20.
        val SCORING_STATE_AFTER_SESSION_ONE = ScoringStateDto(
            xp = 540,
            xpIntoCurrentLevel = 540,
            currentStreak = 1,
            bestStreak = 1,
            lastStudyDate = STUDY_DATE,
            studiedSecondsOnLastStudyDate = 60,
        )

        // Session two adds 1 new × 10 + 10 + 500 over it, crossing Level 1's 1000.
        val SCORING_STATE_AFTER_SESSIONS_ONE_AND_TWO = ScoringState(
            xp = 1060,
            level = 2,
            xpIntoCurrentLevel = 60,
            currentStreak = 1,
            bestStreak = 1,
            lastStudyDate = STUDY_DATE,
            studiedSecondsOnLastStudyDate = 120,
        )

        val CONFIG = XpConfig(
            newCardStudied = 10,
            cardMastered = 100,
            masteryDefended = 50,
            minuteStudied = 10,
            sessionCompleted = 500,
            streakPerDay = 20,
            dailyGoalMet = 1000,
            levelCurveBase = 1000.0,
            levelCurveExponent = 2.5,
        )
    }
}
