package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.model.VoiceDemoRecordingResult
import com.rossomak.flashcards.core.domain.repository.VoiceDemoGateway
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject

class FinishVoiceDemoRecordingUseCase @Inject constructor(
    private val voiceDemoGateway: VoiceDemoGateway,
) : NoParamUseCase<VoiceDemoRecordingResult> {

    override suspend operator fun invoke(): VoiceDemoRecordingResult = voiceDemoGateway.finishRecording()
}
