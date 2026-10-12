package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.repository.DefaultInstallationInfoRepository
import com.rossomak.flashcards.core.domain.repository.InstallationInfoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The host app must also provide an `AppVersion`, which [DefaultInstallationInfoRepository] needs. */
@Module
@InstallIn(SingletonComponent::class)
abstract class InstallationInfoModule {

    @Binds
    abstract fun bindInstallationInfoRepository(impl: DefaultInstallationInfoRepository): InstallationInfoRepository
}
