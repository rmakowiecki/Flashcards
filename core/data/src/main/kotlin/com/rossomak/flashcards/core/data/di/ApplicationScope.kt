package com.rossomak.flashcards.core.data.di

import javax.inject.Qualifier

/**
 * A [kotlinx.coroutines.CoroutineScope] that lives as long as the process, for fire-and-forget work
 * no screen owns. Provided by [ApplicationScopeModule].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
