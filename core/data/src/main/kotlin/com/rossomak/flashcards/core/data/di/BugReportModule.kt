package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.repository.DefaultBugReportRepository
import com.rossomak.flashcards.core.data.source.BugReportRemoteDataSource
import com.rossomak.flashcards.core.data.source.FirebaseBugReportRemoteDataSource
import com.rossomak.flashcards.core.domain.repository.BugReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BugReportModule {

    @Binds
    @Singleton
    abstract fun bindBugReportRepository(defaultBugReportRepository: DefaultBugReportRepository): BugReportRepository

    @Binds
    @Singleton
    abstract fun bindBugReportRemoteDataSource(firebaseBugReportRemoteDataSource: FirebaseBugReportRemoteDataSource): BugReportRemoteDataSource
}
