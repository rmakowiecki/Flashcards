package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.CardProgressEntry
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.SessionNotSavedException
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType.SingleSubcategory
import com.rossomak.flashcards.core.domain.model.SubcategoryProgressDetails
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.repository.FakeCardProgressRepository
import com.rossomak.flashcards.core.domain.repository.FakeScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.FakeSessionSubmissionRepository
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
        sourceType = SingleSubcategory,
        cardResults = cardResults,
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        voiceAnsweringEnabled = false,
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
        sourceType = SingleSubcategory,
        cardResults = cardResults,
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        readAloudEnabled = false,
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

    @Test
    fun `hands the exact session result to the submission repository`() = runTest {
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        createUseCase().invoke(session)

        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a session that could not be queued fails with SessionNotSavedException instead of a score`() = runTest {
        sessionSubmissionRepository.submissionResultToReturn = Result.failure(IllegalStateException("queue file unwritable"))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val result = createUseCase().invoke(session)

        result.exceptionOrNull().shouldBeInstanceOf<SessionNotSavedException>()
    }

    @Test
    fun `the score baseline is read before the session is queued`() = runTest {
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
    fun `a re-submission whose session is already queued scores the same as the first submission`() = runTest {
        scoringStateRepository.resultToReturn = Result.success(ScoringState(xp = 40, xpIntoCurrentLevel = 40))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))
        val first = createUseCase().invoke(session).getOrThrow()
        scoringStateRepository.resultsIncludingQueuedSession = mapOf(
            session.id to Result.success(ScoringState(xp = 40L + first.breakdown.xpTotal, xpIntoCurrentLevel = 40L + first.breakdown.xpTotal)),
        )
        cardProgressRepository.progressIncludingQueuedSession = mapOf(
            session.id to SubcategoryProgressDetails(subcategoryId = "sub-1", categoryId = "cat-1", cards = mapOf("card-1" to priorEntry(FlashcardStudyProgressState.Mastered))),
        )

        val resubmitted = createUseCase().invoke(session).getOrThrow()

        resubmitted shouldBe first
        scoringStateRepository.excludedSessionIds shouldBe listOf(session.id, session.id)
        cardProgressRepository.excludedSessionIds shouldBe listOf(session.id, session.id)
    }

    @Test
    fun `a session already applied in the cached scoring state fails the score`() = runTest {
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))
        scoringStateRepository.appliedSessionIds = setOf(session.id)

        val result = createUseCase().invoke(session)

        result.isFailure shouldBe true
        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a card with no prior entry counts as new, a card with one does not`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgressDetails(subcategoryId = "sub-1", categoryId = "cat-1", cards = mapOf("card-2" to priorEntry())),
        )
        val session = ratedSessionResult(
            cardResults = listOf(
                ratedEntry(cardId = "card-1", state = FlashcardStudyProgressState.Mastered),
                ratedEntry(cardId = "card-2", state = FlashcardStudyProgressState.Partial),
            ),
        )

        val score = createUseCase().invoke(session)

        score.getOrThrow().counts.newCardsStudied shouldBe 1
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
            sourceType = SingleSubcategory,
            cardResults = listOf(
                ratedEntry(cardId = "card-1", subcategoryId = "sub-1", state = FlashcardStudyProgressState.Mastered),
                ratedEntry(cardId = "card-2", subcategoryId = "sub-2", state = FlashcardStudyProgressState.Failed),
            ),
            studyDate = "2026-09-08",
            dailyGoalMinutes = 20,
            studyDateUtcOffsetMinutes = 0,
            voiceAnsweringEnabled = false,
        )

        val score = createUseCase().invoke(session)

        score.getOrThrow().counts.newCardsStudied shouldBe 2
    }

    @Test
    fun `a Fast card with no prior entry counts toward new-cards-studied exactly like a Rated one`() = runTest {
        val session = fastSessionResult(cardResults = listOf(fastEntry(cardId = "card-1")))

        val score = createUseCase().invoke(session)

        score.getOrThrow().counts.newCardsStudied shouldBe 1
    }

    @Test
    fun `a failed prior-progress read fails the score but the session is still queued`() = runTest {
        val error = IllegalStateException("firestore down")
        cardProgressRepository.resultToReturn = Result.failure(error)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session)

        score.isFailure shouldBe true
        score.exceptionOrNull() shouldBe error
        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a failed scoring-state read fails the score but the session is still queued`() = runTest {
        val error = IllegalStateException("firestore down")
        scoringStateRepository.resultToReturn = Result.failure(error)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session)

        score.isFailure shouldBe true
        score.exceptionOrNull() shouldBe error
        sessionSubmissionRepository.submittedSessionResults shouldBe listOf(session)
    }

    @Test
    fun `a missing scoring-state document starts the score from defaults`() = runTest {
        scoringStateRepository.resultToReturn = Result.success(null)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session).getOrThrow()

        score.level shouldBe ScoringState.STARTING_LEVEL
        score.xpIntoCurrentLevel shouldBe score.breakdown.xpTotal.toLong()
    }

    @Test
    fun `the score carries the calculated xp breakdown starting from the prior scoring state`() = runTest {
        val priorXpIntoLevel = 40L
        scoringStateRepository.resultToReturn = Result.success(ScoringState(xp = priorXpIntoLevel, xpIntoCurrentLevel = priorXpIntoLevel))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session).getOrThrow()

        score.xpIntoCurrentLevel shouldBe priorXpIntoLevel + score.breakdown.xpTotal
    }

    @Test
    fun `the session is scored with the cached xp configuration`() = runTest {
        xpConfigRepository.resultToReturn = Result.success(CUSTOM_CONFIG)
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session).getOrThrow()

        score.breakdown.mastered shouldBe CUSTOM_CONFIG.cardMastered
        score.rates.cardMastered shouldBe CUSTOM_CONFIG.cardMastered
    }

    @Test
    fun `the score falls back to the default xp configuration when the cached one is unreadable`() = runTest {
        xpConfigRepository.resultToReturn = Result.failure(IllegalStateException("unreadable"))
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session).getOrThrow()

        score.breakdown.mastered shouldBe XpConfig().cardMastered
    }

    @Test
    fun `a card Mastered in the prior Card Progress scores as defended, whatever the session's own flag says`() = runTest {
        cardProgressRepository.seed(
            SubcategoryProgressDetails(subcategoryId = SUBCATEGORY_ID, categoryId = CATEGORY_ID, cards = mapOf(CARD_ID to priorEntry(FlashcardStudyProgressState.Mastered))),
        )
        val session = ratedSessionResult(cardResults = listOf(ratedEntry(cardId = CARD_ID, state = FlashcardStudyProgressState.Mastered)))

        val score = createUseCase().invoke(session).getOrThrow()

        score.counts.defended shouldBe 1
        score.counts.newlyMastered shouldBe 0
        score.breakdown.masteryDefenseBonus shouldBe XpConfig().masteryDefended
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
    }
}
