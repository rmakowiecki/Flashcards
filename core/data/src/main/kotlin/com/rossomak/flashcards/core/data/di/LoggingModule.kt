package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.logging.DefaultDomainLogger
import com.rossomak.flashcards.core.domain.logging.DomainLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {

    @Binds
    abstract fun bindDomainLogger(logger: DefaultDomainLogger): DomainLogger
}
