package com.rossomak.flashcards.crashreporting

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCrashReporter @Inject constructor() : CrashReporter {

    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
}
