package com.rossomak.flashcards.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Replaces `composable<T>`: content navigates only through an [EntryNavigator] bound to its own entry.
 * Compared by [NavBackStackEntry.id], which survives process death.
 */
inline fun <reified T : Any> NavGraphBuilder.guardedNavComposable(
    navController: NavController,
    noinline enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    noinline exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    noinline content: @Composable AnimatedContentScope.(navigator: EntryNavigator) -> Unit,
) {
    composable<T>(enterTransition = enterTransition, exitTransition = exitTransition) { entry ->
        val navigator = remember(navController, entry.id) {
            EntryNavigator(navController) { navController.currentBackStackEntry?.id == entry.id }
        }
        content(navigator)
    }
}
