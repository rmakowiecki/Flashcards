package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

/**
 * Thin wrapper around [CardProgressRepository.getProgressSummary], mirroring
 * [GetSubcategoryProgressUseCase]'s shape. Category Details fans this out once per screen open to
 * draw every topic's ring and subtitle from the one summary document, rather than reading each
 * topic's packed progress document individually.
 */
class GetProgressSummaryUseCase @Inject constructor(
    private val repository: CardProgressRepository,
) : NoParamUseCase<Result<ProgressSummary?>> {

    override suspend operator fun invoke(): Result<ProgressSummary?> = repository.getProgressSummary()
}
