package com.rossomak.flashcards.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Drops every call unless the destination's own back-stack entry is on top, so a tap on a screen
 * still visible through its exit transition can't navigate twice. See ADR-0067.
 *
 * The guard runs once per call: a multi-call sequence needs its own method, like [navigateStacked].
 */
class EntryNavigator @PublishedApi internal constructor(
    private val navController: NavController,
    private val isOnTop: () -> Boolean,
) {

    fun navigate(route: Any, builder: NavOptionsBuilder.() -> Unit = {}) {
        if (isOnTop()) navController.navigate(route, builder)
    }

    fun popBackStack() {
        if (isOnTop()) navController.popBackStack()
    }

    /** Navigates to [base], then pushes [route] above it, so back from [route] returns to [base]. */
    fun navigateStacked(base: Any, baseBuilder: NavOptionsBuilder.() -> Unit, route: Any) {
        if (!isOnTop()) return
        navController.navigate(base, baseBuilder)
        navController.navigate(route)
    }
}
