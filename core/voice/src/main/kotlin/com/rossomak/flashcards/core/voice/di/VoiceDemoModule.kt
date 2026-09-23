package com.rossomak.flashcards.core.voice.di

import com.rossomak.flashcards.core.domain.repository.VoiceDemoGateway
import com.rossomak.flashcards.core.voice.data.DefaultVoiceDemoGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class VoiceDemoModule {

    @Binds
    @ViewModelScoped
    abstract fun bindVoiceDemoGateway(impl: DefaultVoiceDemoGateway): VoiceDemoGateway
}
