package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.PremiumRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class FetchPremiumUseCase @Inject constructor(
    private val premiumRepository: PremiumRepository,
) : NoParamUseCase<Result<Boolean>> {

    override suspend fun invoke(): Result<Boolean> = premiumRepository.fetchPremium()
}
