package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.time.ElapsedRealtimeTimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlin.time.TimeSource

/**
 * The wall clock and the time source for `core:domain`, which never reads time on its own. The time
 * source keeps counting while the device sleeps, because the auto-resume window of an audio
 * interruption spans time a locked phone spends asleep. Unit tests construct their subject directly
 * with a fixed clock and a test time source.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    fun provideTimeSource(): TimeSource.WithComparableMarks = ElapsedRealtimeTimeSource()
}
