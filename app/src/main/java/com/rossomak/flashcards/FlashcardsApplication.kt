package com.rossomak.flashcards

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rossomak.flashcards.core.common.logd
import com.rossomak.flashcards.core.data.SessionSubmissionDrainScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

/**
 * [Configuration.Provider] wires WorkManager to Hilt's [HiltWorkerFactory]: the default,
 * reflection-based factory can only build a `Worker` with a no-arg constructor, and
 * [com.rossomak.flashcards.core.data.worker.SessionSubmissionDeliveryWorker] has none — its real
 * dependencies are constructor-injected. This replaces WorkManager's own `androidx.startup`
 * auto-initializer, which `AndroidManifest.xml` removes for exactly this reason (its default factory
 * would otherwise try, and fail, to build that worker on the app's first WorkManager access) — this
 * `Configuration.Provider` implementation and that manifest removal must always land together.
 *
 * [scheduleDrain] runs unconditionally on every app start: the sole recovery mechanism for a session
 * a previous process queued locally but never got to drain — no separate "check for
 * leftover records" path exists or is needed, since [SessionSubmissionDrainScheduler]'s own
 * `enqueueUniqueWork(..., KEEP, ...)` call is itself a safe no-op to issue redundantly.
 */
@HiltAndroidApp
class FlashcardsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var sessionSubmissionDrainScheduler: SessionSubmissionDrainScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOGGING_ENABLED) {
            Timber.plant(Timber.DebugTree())
        }
        logd { "App start: scheduling session submission drain for recovery" }
        sessionSubmissionDrainScheduler.scheduleDrain()
    }
}
