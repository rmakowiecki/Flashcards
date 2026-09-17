package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.VoiceOption
import com.rossomak.flashcards.core.domain.repository.VoiceOptionsRepository
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class GetAvailableVoicesUseCase @Inject constructor(
    private val repository: VoiceOptionsRepository,
) : NoParamUseCase<List<VoiceOption>> {
    override suspend operator fun invoke(): List<VoiceOption> = repository.getAvailableVoices()
}
