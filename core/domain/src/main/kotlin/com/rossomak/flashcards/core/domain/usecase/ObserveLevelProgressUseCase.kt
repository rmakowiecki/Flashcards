package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.LevelProgress
import com.rossomak.flashcards.core.domain.repository.LevelProgressRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [LevelProgressRepository.observeLevelProgress], mirroring
 * [ObserveProgressSummaryUseCase]'s shape. Every screen that draws a Level card collects it.
 */
class ObserveLevelProgressUseCase @Inject constructor(
    private val repository: LevelProgressRepository,
) : NoParamUseCase<Flow<LevelProgress>> {

    override suspend operator fun invoke(): Flow<LevelProgress> = repository.observeLevelProgress()
}
