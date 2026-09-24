package com.rossomak.flashcards.core.data.di

import javax.inject.Qualifier

/**
 * `user_preferences` is the app's only `DataStore<Preferences>` — [UserPreferencesModule] backs
 * both [UserPreferencesRepository][com.rossomak.flashcards.core.domain.repository.UserPreferencesRepository]
 * and [StudySessionPreferencesRepository][com.rossomak.flashcards.core.domain.repository.StudySessionPreferencesRepository]
 * off the one file, voice settings and the voice answering info flag included. The qualifier is kept for
 * explicitness at every injection point even with a single store, so a second
 * `@Provides DataStore<Preferences>` reads as a mistake rather than a second store to route to.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferencesDataStore
