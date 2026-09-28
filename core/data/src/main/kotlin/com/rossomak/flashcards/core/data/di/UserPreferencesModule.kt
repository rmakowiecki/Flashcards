package com.rossomak.flashcards.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rossomak.flashcards.core.data.repository.DefaultStudySessionPreferencesRepository
import com.rossomak.flashcards.core.data.repository.DefaultUserPreferencesRepository
import com.rossomak.flashcards.core.data.source.DataStoreStudySessionPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.DataStoreUserPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.DataStoreXpConfigLocalDataSource
import com.rossomak.flashcards.core.data.source.StudySessionPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.UserPreferencesLocalDataSource
import com.rossomak.flashcards.core.data.source.XpConfigLocalDataSource
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
 * One file, three data sources, backed by the single [UserPreferencesDataStore] below — the app's
 * only `DataStore<Preferences>`, voice settings, the voice answering info flag and the last fetched
 * XP configuration included. One file also means one thing for debug to clear; clearing the XP
 * configuration only means the bundled defaults apply until the next fetch.
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

    @Binds
    @Singleton
    abstract fun bindXpConfigLocalDataSource(
        impl: DataStoreXpConfigLocalDataSource,
    ): XpConfigLocalDataSource

    companion object {

        @Provides
        @Singleton
        @UserPreferencesDataStore
        fun provideUserPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.userPreferencesDataStore
    }
}
