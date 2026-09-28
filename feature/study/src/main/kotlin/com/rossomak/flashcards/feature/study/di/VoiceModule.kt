package com.rossomak.flashcards.feature.study.di

import com.rossomak.flashcards.core.domain.repository.StudyVoicePlaybackGateway
import com.rossomak.flashcards.core.domain.repository.VoiceCaptureGateway
import com.rossomak.flashcards.feature.study.voice.data.StudySessionVoiceGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Both study voice seams resolve to the one [StudySessionVoiceGateway] a ViewModel gets: the class
 * itself is `@ViewModelScoped`, so these bindings stay unscoped. Scoping each binding instead would
 * make two separate instances.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class VoiceModule {

    @Binds
    abstract fun bindStudyVoicePlaybackGateway(gateway: StudySessionVoiceGateway): StudyVoicePlaybackGateway

    @Binds
    abstract fun bindVoiceCaptureGateway(gateway: StudySessionVoiceGateway): VoiceCaptureGateway
}
