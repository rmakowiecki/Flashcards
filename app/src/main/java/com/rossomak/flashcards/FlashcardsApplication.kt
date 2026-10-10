package com.rossomak.flashcards

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rossomak.flashcards.core.data.InterruptedAccountDeletionCompleter
import com.rossomak.flashcards.core.data.SignedInWorkRunner
import com.rossomak.flashcards.core.data.activity.CurrentActivityHolder
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
 * [SignedInWorkRunner.start] runs once per process start: it observes the auth state for the
 * process's lifetime and runs its work (the XP configuration refresh and a drain of that User's
 * pending sessions) whenever a user becomes signed in, the session Firebase restores at app start
 * included. That drain is also the recovery path for a session a previous process queued locally but
 * never got to deliver, so no separate app-start drain is needed.
 *
 * [InterruptedAccountDeletionCompleter.complete] runs first, so a User whose deletion a process death
 * interrupted is signed out before that drain is scheduled and before the start screen is chosen.
 *
 * [CurrentActivityHolder] is registered here, before anything else can start an Activity, because
 * GitHub sign-in runs inside the data layer and needs the resumed Activity to open its Custom Tab.
 * Registering it from the Application means it sees every Activity of the process from the first.
 */
@HiltAndroidApp
class FlashcardsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var interruptedAccountDeletionCompleter: InterruptedAccountDeletionCompleter

    @Inject
    lateinit var signedInWorkRunner: SignedInWorkRunner

    @Inject
    lateinit var currentActivityHolder: CurrentActivityHolder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOGGING_ENABLED) {
            Timber.plant(Timber.DebugTree())
        }
        registerActivityLifecycleCallbacks(currentActivityHolder)
        interruptedAccountDeletionCompleter.complete()
        signedInWorkRunner.start()
    }
}
