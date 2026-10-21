package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.time.ElapsedRealtimeTimeSource
import com.rossomak.flashcards.core.domain.repository.TodayProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.TimeSource

/**
 * The wall clock, today's local calendar day and the time source for `core:domain`, which never reads
 * time on its own. Today is read in the device's timezone at each call, never cached. The time source
 * keeps counting while the device sleeps, because the auto-resume window of an audio interruption
 * spans time a locked phone spends asleep. Unit tests construct their subject directly with a fixed
 * clock and a test time source.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    fun provideTodayProvider(): TodayProvider = TodayProvider { LocalDate.now(ZoneId.systemDefault()) }

    @Provides
    fun provideTimeSource(): TimeSource.WithComparableMarks = ElapsedRealtimeTimeSource()
}
