package com.rossomak.flashcards.di

import com.rossomak.flashcards.BuildConfig
import com.rossomak.flashcards.core.domain.model.AppVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The installed build's version, read from the one place that can see it: no other module can reach
 * this app's `BuildConfig`. `BuildConfig.VERSION_NAME` already carries the debug and profiling
 * suffix.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppVersionModule {

    @Provides
    fun provideAppVersion(): AppVersion = AppVersion(
        name = BuildConfig.VERSION_NAME,
        code = BuildConfig.VERSION_CODE.toLong(),
    )
}
