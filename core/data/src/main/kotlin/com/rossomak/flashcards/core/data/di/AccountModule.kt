package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.repository.DefaultAccountRepository
import com.rossomak.flashcards.core.data.source.AccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.AccountDeletionRemoteDataSource
import com.rossomak.flashcards.core.data.source.FileAccountDeletionMarkerLocalDataSource
import com.rossomak.flashcards.core.data.source.FirebaseAccountDeletionRemoteDataSource
import com.rossomak.flashcards.core.domain.repository.AccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(defaultAccountRepository: DefaultAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindAccountDeletionRemoteDataSource(firebaseAccountDeletionRemoteDataSource: FirebaseAccountDeletionRemoteDataSource): AccountDeletionRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAccountDeletionMarkerLocalDataSource(fileAccountDeletionMarkerLocalDataSource: FileAccountDeletionMarkerLocalDataSource): AccountDeletionMarkerLocalDataSource
}
