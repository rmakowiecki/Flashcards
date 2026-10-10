package com.rossomak.flashcards.core.data.worker

import com.google.firebase.firestore.Source
import com.rossomak.flashcards.core.data.source.CardProgressRemoteDataSource
import com.rossomak.flashcards.core.data.source.ScoringStateRemoteDataSource
import com.rossomak.flashcards.core.domain.model.FlashcardResult
import com.rossomak.flashcards.core.domain.model.FlashcardStudyProgressState
import com.rossomak.flashcards.core.domain.model.SessionResult
import com.rossomak.flashcards.core.domain.model.SessionSourceType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SessionServerStateRefresherTest {

    private val scoringStateRemoteDataSource: ScoringStateRemoteDataSource = mockk()
    private val cardProgressRemoteDataSource: CardProgressRemoteDataSource = mockk()
    private val refresher = SessionServerStateRefresher(scoringStateRemoteDataSource, cardProgressRemoteDataSource)

    @Before
    fun setUp() {
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } returns null
        coEvery { cardProgressRemoteDataSource.getProgress(any(), any()) } returns null
    }

    private fun sessionResult(subcategoryIds: List<String>, touchedSubcategoryIds: List<String>): SessionResult = SessionResult.Fast(
        id = "session-1",
        startedAt = Instant.parse("2026-09-08T10:00:00Z"),
        durationSeconds = 60,
        abandoned = false,
        categoryId = "cat-1",
        categoryName = "Category",
        subcategoryIds = subcategoryIds,
        subcategoryNames = subcategoryIds.map { "Name of $it" },
        sourceType = SessionSourceType.Custom,
        cardResults = touchedSubcategoryIds.mapIndexed { index, subcategoryId ->
            FlashcardResult.Fast(cardId = "card-$index", subcategoryId = subcategoryId, state = FlashcardStudyProgressState.Seen)
        },
        studyDate = "2026-09-08",
        dailyGoalMinutes = 20,
        studyDateUtcOffsetMinutes = 0,
        readAloudEnabled = false,
    )

    @Test
    fun `every read succeeding, documents missing included, refreshes successfully`() = runTest {
        val result = refresher.refresh(sessionResult(subcategoryIds = listOf(SUBCATEGORY_ONE), touchedSubcategoryIds = listOf(SUBCATEGORY_ONE)))

        result shouldBe Result.success(Unit)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ONE, Source.SERVER) }
    }

    @Test
    fun `only the Subcategories the Flashcard Results touch are read, each once`() = runTest {
        val subcategoryIds = listOf(SUBCATEGORY_ONE, SUBCATEGORY_TWO, SUBCATEGORY_THREE)

        refresher.refresh(sessionResult(subcategoryIds = subcategoryIds, touchedSubcategoryIds = listOf(SUBCATEGORY_ONE, SUBCATEGORY_THREE, SUBCATEGORY_ONE)))

        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ONE, Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_THREE, Source.SERVER) }
        coVerify(exactly = 0) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_TWO, any()) }
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
    }

    @Test
    fun `a failed scoring-state read fails the refresh after every Card Progress read is attempted`() = runTest {
        val failure = IllegalStateException("offline")
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } throws failure

        val result = refresher.refresh(sessionResult(subcategoryIds = listOf(SUBCATEGORY_ONE, SUBCATEGORY_TWO), touchedSubcategoryIds = listOf(SUBCATEGORY_ONE, SUBCATEGORY_TWO)))

        result shouldBe Result.failure(failure)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ONE, Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_TWO, Source.SERVER) }
    }

    @Test
    fun `a failed Card Progress read fails the refresh after every other read is attempted`() = runTest {
        val failure = IllegalStateException("offline")
        coEvery { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ONE, any()) } throws failure

        val result = refresher.refresh(sessionResult(subcategoryIds = listOf(SUBCATEGORY_ONE, SUBCATEGORY_TWO), touchedSubcategoryIds = listOf(SUBCATEGORY_ONE, SUBCATEGORY_TWO)))

        result shouldBe Result.failure(failure)
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_ONE, Source.SERVER) }
        coVerify(exactly = 1) { cardProgressRemoteDataSource.getProgress(SUBCATEGORY_TWO, Source.SERVER) }
    }

    @Test
    fun `a cancellation is rethrown, not reported as a failed read`() = runTest {
        coEvery { scoringStateRemoteDataSource.getScoringState(any()) } throws CancellationException("cancelled")

        val thrown = runCatching { refresher.refresh(sessionResult(subcategoryIds = listOf(SUBCATEGORY_ONE), touchedSubcategoryIds = listOf(SUBCATEGORY_ONE))) }.exceptionOrNull()

        thrown.shouldBeInstanceOf<CancellationException>()
        coVerify(exactly = 1) { scoringStateRemoteDataSource.getScoringState(Source.SERVER) }
    }

    private companion object {
        const val SUBCATEGORY_ONE = "sub-1"
        const val SUBCATEGORY_TWO = "sub-2"
        const val SUBCATEGORY_THREE = "sub-3"
    }
}
