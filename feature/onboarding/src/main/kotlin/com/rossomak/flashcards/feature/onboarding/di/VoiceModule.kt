package com.rossomak.flashcards.feature.onboarding.di

import com.rossomak.flashcards.feature.onboarding.voice.OnboardingVoiceDemoGateway
import com.rossomak.flashcards.feature.onboarding.voice.VoiceDemoGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class VoiceModule {

    @Binds
    @ViewModelScoped
    abstract fun bindVoiceDemoGateway(impl: OnboardingVoiceDemoGateway): VoiceDemoGateway
}
