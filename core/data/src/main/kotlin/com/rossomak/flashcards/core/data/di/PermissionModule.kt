package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.permission.DefaultPermissionChecker
import com.rossomak.flashcards.core.data.permission.DefaultPermissionRepository
import com.rossomak.flashcards.core.data.permission.PermissionChecker
import com.rossomak.flashcards.core.domain.repository.PermissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {

    @Binds
    @Singleton
    abstract fun bindPermissionRepository(
        impl: DefaultPermissionRepository,
    ): PermissionRepository

    @Binds
    abstract fun bindPermissionChecker(
        impl: DefaultPermissionChecker,
    ): PermissionChecker
}
