package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.model.ObservedScoringState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.Streak
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.levelThreshold
import com.rossomak.flashcards.core.domain.repository.FakeScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.FakeTodayProvider
import com.rossomak.flashcards.core.domain.repository.FakeXpConfigRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveScoringStateUseCaseTest {

    private val scoringStateRepository = FakeScoringStateRepository()
    private val xpConfigRepository = FakeXpConfigRepository()
    private val todayProvider = FakeTodayProvider(TODAY)

    private val useCase = ObserveScoringStateUseCase(scoringStateRepository, xpConfigRepository, todayProvider)

    @Test
    fun `measures the Level against the configured curve`() = runTest {
        val config = XpConfig(levelBaseCost = 2000)
        xpConfigRepository.resultToReturn = Result.success(config)
        scoringStateRepository.emit(ScoringState(xp = 2400, level = 2, xpIntoCurrentLevel = 400))

        useCase().first().levelProgress shouldBe LevelProgress(level = 2, xpIntoCurrentLevel = 400, xpForNextLevel = config.levelThreshold(2))
    }

    @Test
    fun `measures the Level against the default curve when the configuration read fails`() = runTest {
        xpConfigRepository.resultToReturn = Result.failure(IllegalStateException("config down"))
        scoringStateRepository.emit(ScoringState(xp = 400, xpIntoCurrentLevel = 400))

        useCase().first().levelProgress shouldBe LevelProgress(level = 1, xpIntoCurrentLevel = 400, xpForNextLevel = XpConfig().levelThreshold(1))
    }

    @Test
    fun `a User who never studied has no Streak and no seconds today`() = runTest {
        val scoringState = ScoringState()
        scoringStateRepository.emit(scoringState)

        useCase().first() shouldBe ObservedScoringState(
            scoringState = scoringState,
            levelProgress = LevelProgress(level = 1, xpIntoCurrentLevel = 0, xpForNextLevel = XpConfig().levelThreshold(1)),
            streak = null,
            todayStudiedSeconds = 0,
        )
    }

    @Test
    fun `a last study day of today keeps the Streak, not at risk, with its seconds`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY))

        with(useCase().first()) {
            streak shouldBe Streak(days = CURRENT_STREAK, best = BEST_STREAK, isAtRisk = false)
            todayStudiedSeconds shouldBe STUDIED_SECONDS
        }
    }

    @Test
    fun `a last study day of yesterday keeps the Streak at risk, with no seconds today`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY.minusDays(1)))

        with(useCase().first()) {
            streak shouldBe Streak(days = CURRENT_STREAK, best = BEST_STREAK, isAtRisk = true)
            todayStudiedSeconds shouldBe 0
        }
    }

    @Test
    fun `a last study day two or more days ago reads the Streak as 0 and keeps Best`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY.minusDays(2)))

        with(useCase().first()) {
            streak shouldBe Streak(days = 0, best = BEST_STREAK, isAtRisk = false)
            todayStudiedSeconds shouldBe 0
        }
    }

    @Test
    fun `a last study day after today keeps the Streak, not at risk, with no seconds today`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY.plusDays(1)))

        with(useCase().first()) {
            streak shouldBe Streak(days = CURRENT_STREAK, best = BEST_STREAK, isAtRisk = false)
            todayStudiedSeconds shouldBe 0
        }
    }

    @Test
    fun `a Daily Goal met today reads as met`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY).copy(goalMetDate = TODAY.toString()))

        useCase().first().isDailyGoalMetToday shouldBe true
    }

    @Test
    fun `a Daily Goal met on an earlier day does not read as met today`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY).copy(goalMetDate = TODAY.minusDays(1).toString()))

        useCase().first().isDailyGoalMetToday shouldBe false
    }

    @Test
    fun `a Daily Goal never met does not read as met`() = runTest {
        scoringStateRepository.emit(studiedOn(TODAY))

        useCase().first().isDailyGoalMetToday shouldBe false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reads today again on each scoring state change`() = runTest {
        val scoringState = studiedOn(TODAY)
        scoringStateRepository.emit(scoringState)
        val observed = mutableListOf<ObservedScoringState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase().toList(observed) }

        todayProvider.today = TODAY.plusDays(1)
        scoringStateRepository.emit(scoringState.copy(xp = scoringState.xp + 1))

        observed.map { it.streak?.isAtRisk to it.todayStudiedSeconds } shouldBe listOf(false to STUDIED_SECONDS, true to 0L)
    }

    @Test
    fun `passes the raw scoring state through`() = runTest {
        val scoringState = studiedOn(TODAY)
        scoringStateRepository.emit(scoringState)

        useCase().first().scoringState shouldBe scoringState
    }

    private fun studiedOn(date: LocalDate) = ScoringState(
        xp = 500,
        xpIntoCurrentLevel = 500,
        currentStreak = CURRENT_STREAK,
        bestStreak = BEST_STREAK,
        lastStudyDate = date.toString(),
        studiedSecondsOnLastStudyDate = STUDIED_SECONDS,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 6)
        const val CURRENT_STREAK = 8
        const val BEST_STREAK = 14
        const val STUDIED_SECONDS = 840L
    }
}
