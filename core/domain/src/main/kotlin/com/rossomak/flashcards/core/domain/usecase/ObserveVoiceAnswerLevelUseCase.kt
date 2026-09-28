package com.rossomak.flashcards.core.domain.usecase

import com.rossomak.flashcards.core.domain.repository.VoiceCaptureGateway
import com.rossomak.flashcards.core.domain.usecase.base.NoParamUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The microphone input level while a Voice Answering round listens, 0 otherwise. */
class ObserveVoiceAnswerLevelUseCase @Inject constructor(
    private val voiceCaptureGateway: VoiceCaptureGateway,
) : NoParamUseCase<Flow<Float>> {

    override suspend operator fun invoke(): Flow<Float> = voiceCaptureGateway.rawVoiceLevel
}
