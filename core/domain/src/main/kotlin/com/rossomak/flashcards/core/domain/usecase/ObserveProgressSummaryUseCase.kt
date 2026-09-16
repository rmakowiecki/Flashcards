package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.ProgressSummary
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper around [CardProgressRepository.observeProgressSummary], mirroring
 * [GetSubcategoryProgressUseCase]'s shape. Category Details and Browse each collect this once per
 * screen open to draw every subcategory's ring and subtitle from the one summary document, rather
 * than reading each subcategory's packed progress document individually.
 */
class ObserveProgressSummaryUseCase @Inject constructor(
    private val repository: CardProgressRepository,
) : NoParamUseCase<Flow<ProgressSummary?>> {

    override suspend operator fun invoke(): Flow<ProgressSummary?> = repository.observeProgressSummary()
}
