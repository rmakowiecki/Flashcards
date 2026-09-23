package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.permission.DefaultPermissionChecker
import com.rossomak.flashcards.core.data.permission.DefaultPermissionGateway
import com.rossomak.flashcards.core.data.permission.PermissionChecker
import com.rossomak.flashcards.core.domain.repository.PermissionGateway
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
    abstract fun bindPermissionGateway(
        impl: DefaultPermissionGateway,
    ): PermissionGateway

    @Binds
    abstract fun bindPermissionChecker(
        impl: DefaultPermissionChecker,
    ): PermissionChecker
}
