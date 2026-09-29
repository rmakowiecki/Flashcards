package com.rossomak.flashcards.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlin.time.TimeSource

/**
 * The wall clock and the monotonic time source for `core:domain`, which never reads time on its
 * own. Unit tests construct their subject directly with a fixed clock and a test time source.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    fun provideTimeSource(): TimeSource.WithComparableMarks = TimeSource.Monotonic
}
