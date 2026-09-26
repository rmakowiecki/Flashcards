package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.VoiceDemoGateway
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveVoiceDemoInputLevelsUseCase @Inject constructor(
    private val voiceDemoGateway: VoiceDemoGateway,
) : NoParamUseCase<Flow<List<Float>>> {

    override suspend operator fun invoke(): Flow<List<Float>> = voiceDemoGateway.inputLevels
}
