package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.VoiceDemoGateway
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class StartVoiceDemoUseCase @Inject constructor(
    private val voiceDemoGateway: VoiceDemoGateway,
) : NoParamUseCase<Unit> {

    override suspend operator fun invoke() {
        voiceDemoGateway.start()
    }
}
