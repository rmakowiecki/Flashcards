package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.repository.DefaultVoiceAnswerGradingRepository
import com.rossomak.flashcards.core.data.source.FirebaseVoiceGradingRemoteDataSource
import com.rossomak.flashcards.core.data.source.VoiceGradingRemoteDataSource
import com.rossomak.flashcards.core.domain.repository.VoiceAnswerGradingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceGradingModule {

    @Binds
    @Singleton
    abstract fun bindVoiceGradingRemoteDataSource(impl: FirebaseVoiceGradingRemoteDataSource): VoiceGradingRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindVoiceAnswerGradingRepository(
        impl: DefaultVoiceAnswerGradingRepository,
    ): VoiceAnswerGradingRepository
}
