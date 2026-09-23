package com.rossomak.flashcards.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rossomak.flashcards.core.data.repository.DefaultStudySessionPreferencesRepository
import com.rossomak.flashcards.core.data.repository.DefaultUserPreferencesRepository
import com.rossomak.flashcards.core.data.source.DataStoreStudySessionPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.DataStoreUserPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.StudySessionPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.UserPreferencesLocalDataSource
import com.rossomak.flashcards.core.domain.repository.StudySessionPreferencesRepository
import com.rossomak.flashcards.core.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * One file, two data sources, two repositories, backed by the single [UserPreferencesDataStore]
 * below — the app's only `DataStore<Preferences>`, voice settings and the voice answering info flag included. One
 * file also means one thing for debug to clear.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserPreferencesModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: DefaultUserPreferencesRepository,
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesLocalDataSource(
        impl: DataStoreUserPreferencesLocalDataSource,
    ): UserPreferencesLocalDataSource

    @Binds
    @Singleton
    abstract fun bindStudySessionPreferencesRepository(
        impl: DefaultStudySessionPreferencesRepository,
    ): StudySessionPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindStudySessionPreferencesLocalDataSource(
        impl: DataStoreStudySessionPreferencesLocalDataSource,
    ): StudySessionPreferencesLocalDataSource

    companion object {

        @Provides
        @Singleton
        @UserPreferencesDataStore
        fun provideUserPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.userPreferencesDataStore
    }
}
