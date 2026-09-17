package com.rossomak.flashcards.core.data.di

import com.rossomak.flashcards.core.data.repository.DefaultAppShortcutsRepository
import com.rossomak.flashcards.core.data.repository.DefaultAuthRepository
import com.rossomak.flashcards.core.data.repository.DefaultCardProgressRepository
import com.rossomak.flashcards.core.data.repository.DefaultCurationRepository
import com.rossomak.flashcards.core.data.repository.DefaultFlashcardRepository
import com.rossomak.flashcards.core.data.repository.DefaultOnboardingSubcategoriesRepository
import com.rossomak.flashcards.core.data.repository.DefaultScoringStateRepository
import com.rossomak.flashcards.core.data.repository.DefaultSessionSubmissionRepository
import com.rossomak.flashcards.core.data.repository.DefaultUserFavoritesRepository
import com.rossomak.flashcards.core.data.repository.DefaultXpConfigRepository
import com.rossomak.flashcards.core.data.source.AuthRemoteDataSource
import com.rossomak.flashcards.core.data.source.CurationRemoteDataSource
import com.rossomak.flashcards.core.data.source.FilePendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.FirebaseAuthRemoteDataSource
import com.rossomak.flashcards.core.data.source.FirebaseSessionSubmissionRemoteDataSource
import com.rossomak.flashcards.core.data.source.FirestoreCurationRemoteDataSource
import com.rossomak.flashcards.core.data.source.FirestoreUserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.data.source.PendingSessionSubmissionLocalDataSource
import com.rossomak.flashcards.core.data.source.SessionSubmissionRemoteDataSource
import com.rossomak.flashcards.core.data.source.UserFavoritesRemoteDataSource
import com.rossomak.flashcards.core.domain.repository.AppShortcutsRepository
import com.rossomak.flashcards.core.domain.repository.AuthRepository
import com.rossomak.flashcards.core.domain.repository.CardProgressRepository
import com.rossomak.flashcards.core.domain.repository.CurationRepository
import com.rossomak.flashcards.core.domain.repository.FlashcardRepository
import com.rossomak.flashcards.core.domain.repository.OnboardingSubcategoriesRepository
import com.rossomak.flashcards.core.domain.repository.ScoringStateRepository
import com.rossomak.flashcards.core.domain.repository.SessionSubmissionRepository
import com.rossomak.flashcards.core.domain.repository.UserFavoritesRepository
import com.rossomak.flashcards.core.domain.repository.XpConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppShortcutsRepository(defaultAppShortcutsRepository: DefaultAppShortcutsRepository): AppShortcutsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(defaultAuthRepository: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(impl: FirebaseAuthRemoteDataSource): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindFlashcardRepository(defaultFlashcardRepository: DefaultFlashcardRepository): FlashcardRepository

    @Binds
    @Singleton
    abstract fun bindCurationRepository(defaultCurationRepository: DefaultCurationRepository): CurationRepository

    @Binds
    @Singleton
    abstract fun bindCurationRemoteDataSource(impl: FirestoreCurationRemoteDataSource): CurationRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(defaultFavoritesRepository: DefaultUserFavoritesRepository): UserFavoritesRepository

    @Binds
    @Singleton
    abstract fun bindUserFavoritesRemoteDataSource(impl: FirestoreUserFavoritesRemoteDataSource): UserFavoritesRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSessionSubmissionRepository(defaultSessionSubmissionRepository: DefaultSessionSubmissionRepository): SessionSubmissionRepository

    @Binds
    @Singleton
    abstract fun bindSessionSubmissionRemoteDataSource(firebaseSessionSubmissionRemoteDataSource: FirebaseSessionSubmissionRemoteDataSource): SessionSubmissionRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindPendingSessionSubmissionLocalDataSource(
        filePendingSessionSubmissionLocalDataSource: FilePendingSessionSubmissionLocalDataSource,
    ): PendingSessionSubmissionLocalDataSource

    @Binds
    @Singleton
    abstract fun bindCardProgressRepository(defaultCardProgressRepository: DefaultCardProgressRepository): CardProgressRepository

    @Binds
    @Singleton
    abstract fun bindXpConfigRepository(defaultXpConfigRepository: DefaultXpConfigRepository): XpConfigRepository

    @Binds
    @Singleton
    abstract fun bindScoringStateRepository(defaultScoringStateRepository: DefaultScoringStateRepository): ScoringStateRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingSubcategoriesRepository(
        defaultOnboardingSubcategoriesRepository: DefaultOnboardingSubcategoriesRepository,
    ): OnboardingSubcategoriesRepository
}
