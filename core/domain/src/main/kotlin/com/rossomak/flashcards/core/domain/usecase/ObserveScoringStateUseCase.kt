package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.ObservedScoringState
import com.rossomak.flashcards.core.domain.model.ScoringState
import com.rossomak.flashcards.core.domain.model.Streak
import com.rossomak.flashcards.core.domain.model.XpConfig
import com.rossomak.flashcards.core.domain.model.toLevelProgress
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.TodayProvider
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Follows [ScoringStateRepository.observeScoringState] and reads each value against today: the Level
 * position on the configured curve, the Streak as of today and today's studied seconds. Every screen
 * that draws a Level card, a Streak or the Daily Goal collects it. It keeps the repository's contract:
 * nothing until the server answers for an unknown account, completion on sign-out, failures propagated.
 *
 * Today and the XP configuration are read on each upstream value only, so nothing re-emits at midnight;
 * the next scoring state change catches up. The configuration is read separately from the
 * repository's replay, so a configuration refresh between the two reads can briefly measure a value
 * against a newer Level curve than its replay used. Accepted, as the configuration rarely changes.
 */
class ObserveScoringStateUseCase @Inject constructor(
    private val scoringStateRepository: ScoringStateRepository,
    private val xpConfigRepository: XpConfigRepository,
    private val todayProvider: TodayProvider,
) : NoParamUseCase<Flow<ObservedScoringState>> {

    override suspend operator fun invoke(): Flow<ObservedScoringState> = scoringStateRepository.observeScoringState().map { scoringState ->
        val config = xpConfigRepository.getXpConfig().getOrDefault(XpConfig())
        observe(scoringState, config, todayProvider.today())
    }

    /**
     * A last study day after today (the device moved to an earlier timezone) keeps the Streak, not at
     * risk, but its seconds belong to that other day.
     */
    private fun observe(scoringState: ScoringState, config: XpConfig, today: LocalDate): ObservedScoringState {
        val levelProgress = scoringState.toLevelProgress(config)
        if (scoringState.lastStudyDate.isEmpty()) {
            return ObservedScoringState(scoringState = scoringState, levelProgress = levelProgress, streak = null, todayStudiedSeconds = 0)
        }
        val daysSinceLastStudy = ChronoUnit.DAYS.between(LocalDate.parse(scoringState.lastStudyDate), today)
        val streakDays = if (daysSinceLastStudy >= 2) 0 else scoringState.currentStreak
        return ObservedScoringState(
            scoringState = scoringState,
            levelProgress = levelProgress,
            streak = Streak(days = streakDays, best = scoringState.bestStreak, isAtRisk = daysSinceLastStudy == 1L),
            todayStudiedSeconds = if (daysSinceLastStudy == 0L) scoringState.studiedSecondsOnLastStudyDate else 0,
        )
    }
}
