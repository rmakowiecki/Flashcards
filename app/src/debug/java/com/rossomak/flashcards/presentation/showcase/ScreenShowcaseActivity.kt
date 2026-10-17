package com.rossomak.flashcards.presentation.showcase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rossomak.flashcards.core.ui.theme.FlashcardsTheme
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntry
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseEntryContent
import com.rossomak.flashcards.debugtools.showcase.ScreenShowcaseListScreen
import com.rossomak.flashcards.ui.navigation.guardedNavComposable
import kotlinx.serialization.Serializable

/** The showcase list, its start destination. */
@Serializable object ScreenShowcaseList

/** One showcase entry, full screen. [entryName] is a [ScreenShowcaseEntry] name. */
@Serializable data class ScreenShowcaseEntryRoute(val entryName: String)

/**
 * The debug screen showcase: every listed production screen, full screen, in a hand-built state.
 *
 * Its own activity rather than routes in the debug tab's graph, because that graph sits inside Main's
 * scaffold, which always draws the bottom bar. Declared only in the debug and profiling manifests, and
 * it injects nothing, so no Hilt graph, repository, network or sign-in is involved.
 */
class ScreenShowcaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashcardsTheme(dynamicColor = false) {
                ScreenShowcaseNavHost(onExit = ::finish)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ScreenShowcaseActivity::class.java)
    }
}

@Composable
private fun ScreenShowcaseNavHost(onExit: () -> Unit) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ScreenShowcaseList,
        modifier = Modifier.fillMaxSize(),
    ) {
        guardedNavComposable<ScreenShowcaseList>(navController) { navigator ->
            ScreenShowcaseListScreen(
                onNavigateBack = onExit,
                onNavigateToEntry = { entry -> navigator.navigate(ScreenShowcaseEntryRoute(entry.name)) },
            )
        }
        guardedNavComposable<ScreenShowcaseEntryRoute>(navController) { navigator ->
            // NavHost provides each destination's own back-stack entry as its ViewModelStoreOwner. Reading
            // the route from it, not from the controller's current entry, stays right during transitions.
            val backStackEntry = LocalViewModelStoreOwner.current as NavBackStackEntry
            val entry = remember(backStackEntry) { ScreenShowcaseEntry.valueOf(backStackEntry.toRoute<ScreenShowcaseEntryRoute>().entryName) }
            ScreenShowcaseEntryContent(entry = entry, onNavigateBack = { navigator.popBackStack() })
        }
    }
}
