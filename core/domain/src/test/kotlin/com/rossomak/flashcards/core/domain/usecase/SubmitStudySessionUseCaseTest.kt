package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.InFlight
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.NotDelivered
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Rejected
import com.rossomak.flashcards.core.domain.model.SessionDeliveryStatus.Scored
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionScore
import com.rossomak.flashcards.core.domain.model.SessionScoreCounts
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.LocalPreview
import com.rossomak.flashcards.core.domain.model.SessionSubmissionResult.ServerScored
import com.rossomak.flashcards.core.domain.model.SubcategoryProgress
import com.rossomak.flashcards.core.domain.model.XpBreakdown
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.FakeSessionSubmissionRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitStudySessionUseCaseTest {

    private val sessionSubmissionRepository = FakeSessionSubmissionRepository()
    private val cardProgressRepository = FakeCardProgressRepository()
    private val scoringStateRepository = FakeScoringStateRepository()
    private val xpConfigRepository = FakeXpConfigRepository()

    private fun createUseCase(): SubmitStudySessionUseCase = SubmitStudySessionUseCase(
        cardProgressRepository,
        scoringStateRepository,
        GetXpConfigUseCase(xpConfigRepository),
        sessionSubmissionRepository,
    )

    private fun ratedSessionResult(
        subcategoryId: String = "sub-1",
        cardResults: List<FlashcardResult.Rated>,
    ): SessionResult.Rated = SessionResult.Rated(
        id = "session-1",
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf(subcategoryId),
        subcategoryNames = listOf("Subcategory"),
        cardResults = cardResults,
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
    )

    private fun fastSessionResult(
        subcategoryId: String = "sub-1",
        cardResults: List<FlashcardResult.Fast>,
    ): SessionResult.Fast = SessionResult.Fast(
        id = "session-1",
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = listOf(subcategoryId),
        subcategoryNames = listOf("Subcategory"),
        cardResults = cardResults,
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
    )

    private fun ratedEntry(
        cardId: String = "card-1",
        subcategoryId: String = "sub-1",
        state: FlashcardStudyProgressState,
    ): FlashcardResult.Rated = FlashcardResult.Rated(
        cardId = cardId,
        subcategoryId = subcategoryId,
        state = state,
        attemptsUsed = 1,
        wasPreviouslyMastered = false,
    )

    private fun fastEntry(cardId: String = "card-1", subcategoryId: String = "sub-1"): FlashcardResult.Fast =
        FlashcardResult.Fast(cardId = cardId, subcategoryId = subcategoryId, state = FlashcardStudyProgressState.Seen)

    private suspend fun SubmitStudySessionUseCase.invokeAndCapturePreview(sessionResult: SessionResult): Result<SessionScore> =
        invoke(sessionResult).map { result -> (result as LocalPreview).score }

    @Test
    fun `hands the exact session result to the submission repository`() = runTest {
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        createUseCase().invoke(session)

        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a Scored status within the budget returns the server's score`() = runTest {
        sessionSubmissionRepository.deliveryStatusToReturn = flow {
            emit(InFlight)
            delay(2.seconds)
            emit(Scored(SERVER_SCORE))
        }
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)

        result shouldBe Result.success(ServerScored(SERVER_SCORE))
        currentTime shouldBe 2.seconds.inWholeMilliseconds
    }

    @Test
    fun `NotDelivered returns the local preview immediately`() = runTest {
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, NotDelivered)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)

        result.getOrThrow().shouldBeInstanceOf<LocalPreview>()
        currentTime shouldBe 0L
    }

    @Test
    fun `Rejected returns the local preview immediately`() = runTest {
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Rejected)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)

        result.getOrThrow().shouldBeInstanceOf<LocalPreview>()
        currentTime shouldBe 0L
    }

    @Test
    fun `no final status within the budget returns the local preview exactly when the budget runs out`() = runTest {
        sessionSubmissionRepository.deliveryStatusToReturn = flow {
            emit(InFlight)
            awaitCancellation()
        }
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)

        result.getOrThrow().shouldBeInstanceOf<LocalPreview>()
        currentTime shouldBe SubmitStudySessionUseCase.SERVER_RESULT_BUDGET.inWholeMilliseconds
    }

    @Test
    fun `a Scored status arriving after the budget never replaces the local preview`() = runTest {
        sessionSubmissionRepository.deliveryStatusToReturn = flow {
            emit(InFlight)
            delay(SubmitStudySessionUseCase.SERVER_RESULT_BUDGET + 1.seconds)
            emit(Scored(SERVER_SCORE))
        }
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)
        advanceUntilIdle()

        result.getOrThrow().shouldBeInstanceOf<LocalPreview>()
    }

    @Test
    fun `the preview baseline is read before the session is submitted`() = runTest {
        var cardProgressReadsAtSubmit: List<String>? = null
        var scoringStateReadsAtSubmit: Int? = null
        sessionSubmissionRepository.onSubmit = {
            cardProgressReadsAtSubmit = cardProgressRepository.requestedSubcategoryIds.toList()
            scoringStateReadsAtSubmit = scoringStateRepository.getScoringStateCallCount
        }
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        createUseCase().invoke(session)

        cardProgressReadsAtSubmit shouldBe listOf("sub-1")
        scoringStateReadsAtSubmit shouldBe 1
    }

    @Test
    fun `a failed baseline read does not fail a server-scored result`() = runTest {
        scoringStateRepository.resultToReturn = Result.failure(IllegalStateException("firestore down"))
        sessionSubmissionRepository.deliveryStatusToReturn = flowOf(InFlight, Scored(SERVER_SCORE))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)

        result shouldBe Result.success(ServerScored(SERVER_SCORE))
    }

    @Test
    fun `a card with no prior entry counts as new, a card with one does not`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgress(subcategoryId = "sub-1", categoryId = "cat-1", cards = mapOf("card-2" to priorEntry())),
        )
        val session = ratedSessionResult(
            cardResults = listOf(
                ratedEntry(cardId = "card-1", state = FlashcardStudyProgressState.Mastered),
                ratedEntry(cardId = "card-2", state = FlashcardStudyProgressState.Partial),
            ),
        )

        val preview = createUseCase().invokeAndCapturePreview(session)

        preview.getOrThrow().counts?.newCardsStudied shouldBe 1
    }

    @Test
    fun `new cards are counted per Subcategory across the whole session`() = runTest {
        val session = SessionResult.Rated(
            id = "session-1",
            startedAt = Instant.parse("2026-09-08T10:00:00Z"),
            durationSeconds = 60,
            abandoned = false,
            categoryId = "cat-1",
            categoryName = "Category",
            subcategoryIds = listOf("sub-1", "sub-2"),
            subcategoryNames = listOf("Subcategory 1", "Subcategory 2"),
            cardResults = listOf(
                ratedEntry(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Mastered),
                ratedEntry(cardId = "card-2", subcategoryId = "sub-2", state = FlashcardStudyProgressState.Failed),
            ),
            studyDate = "2026-09-08",
            dailyGoalMinutes = 20,
            studyDateUtcOffsetMinutes = 0,
        )

        val preview = createUseCase().invokeAndCapturePreview(session)

        preview.getOrThrow().counts?.newCardsStudied shouldBe 2
    }

    @Test
    fun `a Fast card with no prior entry counts toward new-cards-studied exactly like a Rated one`() = runTest {
        val session = fastSessionResult(cardResults = listOf(fastEntry(cardId = "card-1")))

        val preview = createUseCase().invokeAndCapturePreview(session)

        preview.getOrThrow().counts?.newCardsStudied shouldBe 1
    }

    @Test
    fun `a failed prior-progress read fails the fallback preview but the session is still submitted`() = runTest {
        val error = IllegalStateException("firestore down")
        cardProgressRepository.resultToReturn = Result.failure(error)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session)

        preview.isFailure shouldBe true
        preview.exceptionOrNull() shouldBe error
        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a failed scoring-state read fails the fallback preview but the session is still submitted`() = runTest {
        val error = IllegalStateException("firestore down")
        scoringStateRepository.resultToReturn = Result.failure(error)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session)

        preview.isFailure shouldBe true
        preview.exceptionOrNull() shouldBe error
        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a missing scoring-state document starts the preview calculation from defaults`() = runTest {
        scoringStateRepository.resultToReturn = Result.success(null)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session).getOrThrow()

        preview.level shouldBe ScoringState.STARTING_LEVEL
        preview.xpIntoCurrentLevel shouldBe preview.breakdown.xpTotal.toLong()
    }

    @Test
    fun `the preview carries the calculated xp breakdown starting from the prior scoring state`() = runTest {
        val priorXpIntoLevel = 40L
        scoringStateRepository.resultToReturn = Result.success(ScoringState(xp = priorXpIntoLevel, xpIntoCurrentLevel = priorXpIntoLevel))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session).getOrThrow()

        preview.xpIntoCurrentLevel shouldBe priorXpIntoLevel + preview.breakdown.xpTotal
    }

    @Test
    fun `the preview is scored with the cached xp configuration`() = runTest {
        xpConfigRepository.resultToReturn = Result.success(CUSTOM_CONFIG)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session).getOrThrow()

        preview.breakdown.mastered shouldBe CUSTOM_CONFIG.cardMastered
        preview.rates?.cardMastered shouldBe CUSTOM_CONFIG.cardMastered
    }

    @Test
    fun `the preview falls back to the default xp configuration when the cached one is unreadable`() = runTest {
        xpConfigRepository.resultToReturn = Result.failure(IllegalStateException("unreadable"))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session).getOrThrow()

        preview.breakdown.mastered shouldBe XpConfig().cardMastered
    }

    @Test
    fun `a card Mastered in the prior Card Progress scores as defended, whatever the session's own flag says`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgress(subcategoryId = SUBCATEGORY_ID, categoryId = CATEGORY_ID, cards = mapOf(CARD_ID to priorEntry(FlashcardStudyProgressState.Mastered))),
        )
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(cardId = CARD_ID, state = FlashcardStudyProgressState.Mastered)))

        val preview = createUseCase().invokeAndCapturePreview(session).getOrThrow()

        preview.counts?.defended shouldBe 1
        preview.counts?.newlyMastered shouldBe 0
        preview.breakdown.masteryDefenseBonus shouldBe XpConfig().masteryDefended
    }

    private fun priorEntry(state: FlashcardStudyProgressState = FlashcardStudyProgressState.Partial): CardProgressEntry = CardProgressEntry(
        state = state,
        firstStudiedAt = Instant.parse("2026-01-01T00:00:00Z"),
        masteredAt = null,
    )

    private companion object {
        const val CATEGORY_ID = "cat-1"
        const val SUBCATEGORY_ID = "sub-1"
        const val CARD_ID = "card-1"
        val CUSTOM_CONFIG = XpConfig(cardMastered = 321)
        val SERVER_SCORE = SessionScore(
            breakdown = XpBreakdown(newCards = 10, mastered = 100, streakBonus = 250),
            level = 2,
            xpIntoCurrentLevel = 40,
            xpForNextLevel = 6000,
            levelsCrossed = listOf(2),
            counts = SessionScoreCounts(newCardsStudied = 1, newlyMastered = 1, partial = 0, defended = 0, demastered = 0),
            rates = null,
        )
    }
}
