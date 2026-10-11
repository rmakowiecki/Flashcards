package com.rossomak.flashcards.core.data.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Activity currently in the foreground, for the one data-layer call that must start an Activity: Firebase's
 * provider sign-in, which opens a Custom Tab. Held weakly and cleared when that Activity pauses, so it never leaks
 * one and never hands out an Activity the User has left. Read on the main thread, before any suspension.
 */
@Singleton
class CurrentActivityHolder @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var resumedActivity: WeakReference<Activity>? = null

    val currentActivity: Activity? get() = resumedActivity?.get()

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity === activity) resumedActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
