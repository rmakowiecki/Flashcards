package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePremiumUseCase @Inject constructor(
    private val premiumRepository: PremiumRepository,
) : NoParamUseCase<Flow<Boolean?>> {

    override suspend fun invoke(): Flow<Boolean?> = premiumRepository.observePremium()
}
