package com.rossomak.flashcards.di

import com.rossomak.flashcards.crashreporting.CrashReporter
import com.rossomak.flashcards.crashreporting.DefaultCrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface CrashReportingModule {

    @Binds
    fun bindCrashReporter(defaultCrashReporter: DefaultCrashReporter): CrashReporter
}
